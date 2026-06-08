package com.nani.agent

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nani.agent.agent.NaniAccessibilityService
import com.nani.agent.ai.AiAction
import com.nani.agent.ai.AiPlan
import com.nani.agent.ai.AiPrefs
import com.nani.agent.ai.AiProviderFactory
import com.nani.agent.ai.AiRiskLevel
import com.nani.agent.ai.AiSettings
import com.nani.agent.plan.ExecutablePlan
import com.nani.agent.plan.PlanOperation
import com.nani.agent.plan.PlanParser
import com.nani.agent.plan.PlanValidationResult
import com.nani.agent.plan.PlanValidator
import com.nani.agent.saf.ActionExecutionResult
import com.nani.agent.saf.ActionExecutor
import com.nani.agent.saf.SafFileRepository
import com.nani.agent.saf.SafRootStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NaniApp()
        }
    }
}

@Composable
fun NaniApp() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            MainScreen()
        }
    }
}

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var accessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var agentEnabled by remember { mutableStateOf(AgentPrefs.isAgentEnabled(context)) }
    var guardEnabled by remember { mutableStateOf(AgentPrefs.isGuardEnabled(context)) }
    var aiSettings by remember { mutableStateOf(AiPrefs.load(context)) }
    var workFolderUri by remember { mutableStateOf(SafRootStore.getRootUri(context)) }
    var workFolderMessage by remember { mutableStateOf<String?>(null) }
    var commandText by remember { mutableStateOf("") }
    var commandMessage by remember { mutableStateOf<String?>(null) }
    var commandLoading by remember { mutableStateOf(false) }
    var aiPlan by remember { mutableStateOf<AiPlan?>(null) }
    var executablePlan by remember { mutableStateOf<ExecutablePlan?>(null) }
    var validationResult by remember { mutableStateOf<PlanValidationResult?>(null) }
    var executionResult by remember { mutableStateOf<ActionExecutionResult?>(null) }
    var planJsonVisible by remember { mutableStateOf(false) }
    var aiTestPlan by remember { mutableStateOf<AiPlan?>(null) }
    var aiTestLoading by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf(LogStore.readRecent(context, limit = 50)) }

    val openTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            SafRootStore.saveRootUri(context, uri)
            LogStore.appendSafAction(context, operation = "select_work_folder", result = "success")
            workFolderUri = uri
            workFolderMessage = "Work folder selected."
            executablePlan?.let {
                validationResult = PlanValidator.validate(it, hasWorkFolder = true)
            }
            logs = LogStore.readRecent(context, limit = 50)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            accessibilityEnabled = isAccessibilityServiceEnabled(context)
            agentEnabled = AgentPrefs.isAgentEnabled(context)
            guardEnabled = AgentPrefs.isGuardEnabled(context)
            aiSettings = AiPrefs.load(context)
            logs = LogStore.readRecent(context, limit = 50)
            delay(1_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Nani",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        StatusCard(
            accessibilityEnabled = accessibilityEnabled,
            agentEnabled = agentEnabled,
            guardEnabled = guardEnabled,
            aiProvider = AiProviderFactory.providerStatus(aiSettings),
            workFolder = shortUri(workFolderUri),
            onOpenSettings = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onToggleAgent = {
                val enabled = !AgentPrefs.isAgentEnabled(context)
                AgentPrefs.setAgentEnabled(context, enabled)
                LogStore.appendControlChange(context, control = "agent", enabled = enabled)
                agentEnabled = AgentPrefs.isAgentEnabled(context)
                logs = LogStore.readRecent(context, limit = 50)
            },
            onToggleGuard = {
                val enabled = !AgentPrefs.isGuardEnabled(context)
                AgentPrefs.setGuardEnabled(context, enabled)
                LogStore.appendControlChange(context, control = "guard", enabled = enabled)
                guardEnabled = AgentPrefs.isGuardEnabled(context)
                logs = LogStore.readRecent(context, limit = 50)
            }
        )

        if (!agentEnabled) {
            WarningCard("Nani Agent is disabled. No monitoring, logging, or blocking is active.")
        } else if (!guardEnabled) {
            WarningCard("Nani Guard is disabled. Nani is logging only and will not block protected screens.")
        }

        WorkFolderCard(
            rootUri = workFolderUri,
            message = workFolderMessage,
            onSelectFolder = { openTreeLauncher.launch(null) },
            onResetFolder = {
                workFolderUri?.let { releaseSafPermission(context, it) }
                SafRootStore.clear(context)
                LogStore.appendSafAction(context, operation = "reset_work_folder", result = "success")
                workFolderUri = null
                workFolderMessage = "Work folder reset."
                executablePlan?.let {
                    validationResult = PlanValidator.validate(it, hasWorkFolder = false)
                }
                logs = LogStore.readRecent(context, limit = 50)
            },
            onScanFolder = {
                val uri = workFolderUri
                if (uri == null) {
                    workFolderMessage = "Select a work folder first."
                } else {
                    runCatching {
                        val summary = SafFileRepository(context, uri).summarizeFolder()
                        workFolderMessage = buildString {
                            appendLine("Files: ${summary.fileCount}")
                            appendLine("Folders: ${summary.folderCount}")
                            appendLine("Extensions: ${summary.extensions.entries.joinToString { "${it.key}=${it.value}" }}")
                            if (summary.firstFiles.isNotEmpty()) {
                                appendLine("First files:")
                                append(summary.firstFiles.joinToString("\n"))
                            }
                        }
                        LogStore.appendSafAction(context, operation = "scan_work_folder", result = "success")
                    }.onFailure {
                        workFolderMessage = "Folder scan failed: ${it.message ?: it::class.java.simpleName}"
                        LogStore.appendSafAction(context, operation = "scan_work_folder", result = "failed")
                    }
                    logs = LogStore.readRecent(context, limit = 50)
                }
            }
        )

        AgentCommandCenterCard(
            command = commandText,
            message = commandMessage,
            isGenerating = commandLoading,
            onCommandChanged = {
                commandText = it
                if (commandMessage == "Please enter a command first.") {
                    commandMessage = null
                }
            },
            onGeneratePlan = {
                if (commandText.isBlank()) {
                    commandMessage = "Please enter a command first."
                    aiPlan = null
                    executablePlan = null
                    validationResult = null
                    executionResult = null
                } else {
                    coroutineScope.launch {
                        commandLoading = true
                        commandMessage = null
                        executionResult = null
                        try {
                            val settings = AiPrefs.load(context)
                            val provider = AiProviderFactory.create(settings)
                            val plan = provider.generatePlan(commandText)
                            val executable = PlanParser.parse(plan)
                            val validation = PlanValidator.validate(
                                plan = executable,
                                hasWorkFolder = workFolderUri != null
                            )
                            aiPlan = plan
                            executablePlan = executable
                            validationResult = validation
                            planJsonVisible = false
                            LogStore.appendAiPlanGenerated(
                                context = context,
                                provider = providerLogName(settings),
                                actionType = plan.actionType.wireName,
                                riskLevel = plan.riskLevel.wireName
                            )
                            logs = LogStore.readRecent(context, limit = 50)
                        } finally {
                            commandLoading = false
                        }
                    }
                }
            }
        )

        PlanPreviewCard(
            plan = aiPlan,
            executablePlan = executablePlan,
            validationResult = validationResult,
            jsonVisible = planJsonVisible,
            onToggleJson = { planJsonVisible = !planJsonVisible },
            onDiscard = {
                aiPlan = null
                executablePlan = null
                validationResult = null
                executionResult = null
                planJsonVisible = false
            },
            onExecute = {
                val executable = executablePlan ?: return@PlanPreviewCard
                val validation = PlanValidator.validate(executable, hasWorkFolder = workFolderUri != null)
                validationResult = validation
                if (validation.canExecute) {
                    executionResult = ActionExecutor(context).execute(executable)
                    logs = LogStore.readRecent(context, limit = 50)
                }
            }
        )

        ExecutionResultCard(result = executionResult)

        AiSettingsCard(
            settings = aiSettings,
            testPlan = aiTestPlan,
            isTesting = aiTestLoading,
            onSettingsChanged = { aiSettings = it },
            onSave = {
                AiPrefs.save(context, aiSettings)
                aiSettings = AiPrefs.load(context)
            },
            onTest = {
                coroutineScope.launch {
                    aiTestLoading = true
                    val savedSettings = aiSettings
                    try {
                        AiPrefs.save(context, savedSettings)
                        val provider = AiProviderFactory.create(savedSettings)
                        val plan = provider.generatePlan("Sortiere meine PDFs für Schule")
                        aiTestPlan = plan
                        LogStore.appendAiTest(
                            context = context,
                            provider = providerLogName(savedSettings),
                            actionType = plan.actionType.wireName,
                            riskLevel = plan.riskLevel.wireName
                        )
                        logs = LogStore.readRecent(context, limit = 50)
                    } finally {
                        aiTestLoading = false
                    }
                }
            }
        )

        SecurityRulesCard()

        LogsCard(logs = logs)
    }
}

@Composable
private fun StatusCard(
    accessibilityEnabled: Boolean,
    agentEnabled: Boolean,
    guardEnabled: Boolean,
    aiProvider: String,
    workFolder: String,
    onOpenSettings: () -> Unit,
    onToggleAgent: () -> Unit,
    onToggleGuard: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Status",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            StatusRow(label = "Accessibility", value = if (accessibilityEnabled) "Enabled" else "Disabled")
            StatusRow(label = "Agent", value = if (agentEnabled) "Active" else "Disabled")
            StatusRow(label = "Guard", value = if (guardEnabled) "Active" else "Disabled")
            StatusRow(label = "API Provider", value = aiProvider.removePrefix("AI Provider: "))
            StatusRow(label = "Work Folder", value = workFolder)
            Button(onClick = onToggleAgent) {
                Text(if (agentEnabled) "Deactivate Nani Agent" else "Activate Nani Agent")
            }
            Button(onClick = onToggleGuard) {
                Text(if (guardEnabled) "Deactivate Nani Guard" else "Activate Nani Guard")
            }
            Button(onClick = onOpenSettings) {
                Text("Open Accessibility Settings")
            }
        }
    }
}

@Composable
private fun WorkFolderCard(
    rootUri: Uri?,
    message: String?,
    onSelectFolder: () -> Unit,
    onResetFolder: () -> Unit,
    onScanFolder: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Arbeitsordner",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text("Aktuell: ${shortUri(rootUri)}")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = onSelectFolder) {
                    Text("Arbeitsordner auswählen")
                }
                Button(onClick = onScanFolder, enabled = rootUri != null) {
                    Text("Dateien listen")
                }
            }
            Button(onClick = onResetFolder, enabled = rootUri != null) {
                Text("Arbeitsordner zurücksetzen")
            }
            if (message != null) {
                Text(text = message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AgentCommandCenterCard(
    command: String,
    message: String?,
    isGenerating: Boolean,
    onCommandChanged: (String) -> Unit,
    onGeneratePlan: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Agent Command Center",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = command,
                onValueChange = onCommandChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Command for Nani") },
                placeholder = { Text("Sortiere meine PDFs für Schule") },
                minLines = 3,
                maxLines = 6
            )
            Button(onClick = onGeneratePlan, enabled = !isGenerating) {
                Text(if (isGenerating) "Plan wird erstellt..." else "Plan erstellen")
            }
            if (isGenerating) {
                Text("Generating safe JSON plan...")
            }
            if (message != null) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PlanPreviewCard(
    plan: AiPlan?,
    executablePlan: ExecutablePlan?,
    validationResult: PlanValidationResult?,
    jsonVisible: Boolean,
    onToggleJson: () -> Unit,
    onDiscard: () -> Unit,
    onExecute: () -> Unit
) {
    if (plan == null || executablePlan == null) return

    val canExecute = validationResult?.canExecute == true
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Plan Preview",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text("Action: ${plan.actionType.wireName}")
            Text("Risk: ${plan.riskLevel.wireName}")
            Text("Requires confirmation: ${plan.requiresConfirmation}")
            Text(plan.explanation)
            if (plan.actionType == AiAction.Blocked) {
                Text(
                    text = "This action is blocked by Nani safety rules.",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
            OperationList(executablePlan.operations)
            validationResult?.let { validation ->
                if (validation.errors.isNotEmpty()) {
                    Text("Validation errors:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    validation.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                if (validation.warnings.isNotEmpty()) {
                    Text("Warnings:", fontWeight = FontWeight.SemiBold)
                    validation.warnings.forEach { Text(it) }
                }
            }
            Button(onClick = onToggleJson) {
                Text(if (jsonVisible) "JSON ausblenden" else "JSON anzeigen")
            }
            if (jsonVisible) {
                Text(text = executablePlan.proposedJson, style = MaterialTheme.typography.bodySmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = onExecute, enabled = canExecute) {
                    Text("Ausführen")
                }
                Button(onClick = onDiscard) {
                    Text("Verwerfen")
                }
            }
            if (validationResult?.hasWritingOperations == true) {
                Text("Schreibende Aktionen werden erst durch Ausführen bestätigt. Originaldateien bleiben erhalten.")
            }
        }
    }
}

@Composable
private fun OperationList(operations: List<PlanOperation>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Geplante Operationen", fontWeight = FontWeight.SemiBold)
        if (operations.isEmpty()) {
            Text("Keine Dateioperationen geplant.")
        } else {
            operations.forEachIndexed { index, operation ->
                Text("${index + 1}. ${operationText(operation)}")
            }
        }
    }
}

@Composable
private fun ExecutionResultCard(result: ActionExecutionResult?) {
    if (result == null) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Execution Result",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (result.successes.isNotEmpty()) {
                Text("Successful operations", fontWeight = FontWeight.SemiBold)
                result.successes.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (result.failures.isNotEmpty()) {
                Text("Failed operations", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                result.failures.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            if (result.warnings.isNotEmpty()) {
                Text("Warnings", fontWeight = FontWeight.SemiBold)
                result.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun AiSettingsCard(
    settings: AiSettings,
    testPlan: AiPlan?,
    isTesting: Boolean,
    onSettingsChanged: (AiSettings) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "AI Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = AiProviderFactory.providerStatus(settings),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            ProviderOption(
                label = "DeepSeek API (Recommended)",
                selected = settings.providerType == AiPrefs.PROVIDER_DEEPSEEK_API,
                onClick = {
                    onSettingsChanged(
                        settings.copy(
                            providerType = AiPrefs.PROVIDER_DEEPSEEK_API,
                            apiBaseUrl = AiPrefs.DEFAULT_DEEPSEEK_BASE_URL,
                            modelName = AiPrefs.DEFAULT_DEEPSEEK_MODEL_NAME
                        )
                    )
                }
            )
            ProviderOption(
                label = "OpenAI Compatible API",
                selected = settings.providerType == AiPrefs.PROVIDER_OPENAI_COMPATIBLE_API,
                onClick = {
                    onSettingsChanged(
                        settings.copy(
                            providerType = AiPrefs.PROVIDER_OPENAI_COMPATIBLE_API,
                            apiBaseUrl = "",
                            modelName = ""
                        )
                    )
                }
            )
            ProviderOption(
                label = "Custom API",
                selected = settings.providerType == AiPrefs.PROVIDER_CUSTOM_API,
                onClick = {
                    onSettingsChanged(
                        settings.copy(
                            providerType = AiPrefs.PROVIDER_CUSTOM_API,
                            apiBaseUrl = "",
                            modelName = ""
                        )
                    )
                }
            )
            ProviderOption(
                label = "Local Dummy / Gemma planned",
                selected = settings.providerType == AiPrefs.PROVIDER_LOCAL_DUMMY_GEMMA,
                onClick = {
                    onSettingsChanged(
                        settings.copy(
                            providerType = AiPrefs.PROVIDER_LOCAL_DUMMY_GEMMA,
                            apiBaseUrl = "",
                            modelName = AiPrefs.DEFAULT_LOCAL_MODEL_NAME
                        )
                    )
                }
            )
            if (settings.providerType == AiPrefs.PROVIDER_DEEPSEEK_API) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(onClick = { onSettingsChanged(settings.copy(modelName = AiPrefs.DEFAULT_DEEPSEEK_MODEL_NAME)) }) {
                        Text("deepseek-v4-flash")
                    }
                    Button(onClick = { onSettingsChanged(settings.copy(modelName = AiPrefs.DEEPSEEK_PRO_MODEL_NAME)) }) {
                        Text("deepseek-v4-pro")
                    }
                }
            }
            OutlinedTextField(
                value = settings.apiBaseUrl,
                onValueChange = { onSettingsChanged(settings.copy(apiBaseUrl = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Base URL") },
                singleLine = true
            )
            OutlinedTextField(
                value = settings.modelName,
                onValueChange = { onSettingsChanged(settings.copy(modelName = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model Name") },
                singleLine = true
            )
            OutlinedTextField(
                value = settings.apiKey,
                onValueChange = { onSettingsChanged(settings.copy(apiKey = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Text("API key is stored locally for now. TODO: Move API key storage to Android Keystore before production use.")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = onSave) {
                    Text("Save")
                }
                Button(onClick = onTest, enabled = !isTesting) {
                    Text(if (isTesting) "Testing API..." else "Test API")
                }
            }
            if (isTesting) {
                Text("Testing selected provider...")
            }
            if (testPlan != null) {
                CompactPlanPreview(testPlan)
            }
        }
    }
}

@Composable
private fun CompactPlanPreview(plan: AiPlan) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Test Result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Action: ${plan.actionType.wireName}")
        Text("Risk: ${plan.riskLevel.wireName}")
        Text("Requires confirmation: ${plan.requiresConfirmation}")
        Text(plan.explanation)
        Text("Proposed JSON", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(text = plan.proposedJson, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ProviderOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(onClick = onClick) {
        Text(label)
    }
    if (selected) {
        Text(text = "Selected: $label", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun WarningCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SecurityRulesCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Security Rules",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text("No file deletion is implemented.")
            Text("No real move or rename operations are implemented.")
            Text("No device administrator, root, or overlay permissions are requested.")
            Text("Accessibility stays inactive until manually enabled in Android settings.")
            Text("When the agent is active, foreground packages are logged to app-private storage.")
            Text("When the guard is active, Settings and permission-management screens are blocked with Back, then Home.")
            Text("AI can only produce suggestions and JSON plans.")
            Text("Only SAF work-folder operations are executable: list, summarize, create folders, copy files.")
            Text("Google Drive, native inference runtime, app install/uninstall, and generic Accessibility automation are not included.")
        }
    }
}

@Composable
private fun LogsCard(logs: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Recent Logs",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (logs.isEmpty()) {
                Text("No foreground events logged yet.")
            } else {
                logs.forEach { line ->
                    Text(text = line, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, NaniAccessibilityService::class.java).flattenToString()
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabledServices)
    return splitter.any { it.equals(expected, ignoreCase = true) }
}

private fun providerLogName(settings: AiSettings): String {
    return when (settings.providerType) {
        AiPrefs.PROVIDER_DEEPSEEK_API -> "deepseek_api"
        AiPrefs.PROVIDER_OPENAI_COMPATIBLE_API -> "openai_compatible_api"
        AiPrefs.PROVIDER_CUSTOM_API -> "custom_api"
        AiPrefs.PROVIDER_LOCAL_DUMMY_GEMMA -> "local_dummy_gemma"
        else -> "unknown"
    }
}

private fun shortUri(uri: Uri?): String {
    val value = uri?.toString() ?: return "Not selected"
    return if (value.length <= 42) value else value.take(20) + "..." + value.takeLast(18)
}

private fun releaseSafPermission(context: Context, uri: Uri) {
    runCatching {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.releasePersistableUriPermission(uri, flags)
    }
}

private fun operationText(operation: PlanOperation): String {
    return when (operation.op.wireName) {
        "list_files" -> "Dateien im Arbeitsordner listen"
        "summarize_folder" -> "Arbeitsordner zusammenfassen"
        "create_folder" -> "Ordner erstellen: ${operation.path.orEmpty()}"
        "copy_file" -> "Datei kopieren: ${operation.from.orEmpty()} -> ${operation.to.orEmpty()}"
        else -> "Nicht unterstützte Operation: ${operation.rawOp}"
    }
}
