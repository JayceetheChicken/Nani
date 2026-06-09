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
import com.nani.agent.agentloop.AgentLoopController
import com.nani.agent.agentloop.AgentLoopState
import com.nani.agent.agentloop.AgentStopReason
import com.nani.agent.ai.AiAction
import com.nani.agent.ai.AiPlan
import com.nani.agent.ai.AiPrefs
import com.nani.agent.ai.AiProviderFactory
import com.nani.agent.ai.AiSettings
import com.nani.agent.executor.AgentExecutionController
import com.nani.agent.plan.ExecutablePlan
import com.nani.agent.plan.PlanOperation
import com.nani.agent.plan.PlanParser
import com.nani.agent.plan.PlanValidationResult
import com.nani.agent.plan.PlanValidator
import com.nani.agent.saf.ActionExecutionResult
import com.nani.agent.saf.BroadFileRepository
import com.nani.agent.saf.BroadStorageAccess
import com.nani.agent.saf.SafFileRepository
import com.nani.agent.saf.SafRootStore
import com.nani.agent.uiagent.ScreenSnapshot
import com.nani.agent.uiagent.ScreenStateStore
import com.nani.agent.uiagent.UiAgentController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var workFolderCount by remember { mutableStateOf(SafRootStore.getRootUris(context).size) }
    var workFolderMessage by remember { mutableStateOf<String?>(null) }
    var broadStorageGranted by remember { mutableStateOf(BroadStorageAccess.isGranted()) }
    var commandText by remember { mutableStateOf("") }
    var commandMessage by remember { mutableStateOf<String?>(null) }
    var commandLoading by remember { mutableStateOf(false) }
    var aiPlan by remember { mutableStateOf<AiPlan?>(null) }
    var executablePlan by remember { mutableStateOf<ExecutablePlan?>(null) }
    var validationResult by remember { mutableStateOf<PlanValidationResult?>(null) }
    var executionResult by remember { mutableStateOf<ActionExecutionResult?>(null) }
    var executionLoading by remember { mutableStateOf(false) }
    var internetConfirmed by remember { mutableStateOf(false) }
    var finalSubmitConfirmed by remember { mutableStateOf(false) }
    var executionPaused by remember { mutableStateOf(false) }
    var executionStopped by remember { mutableStateOf(false) }
    var planJsonVisible by remember { mutableStateOf(false) }
    var aiTestPlan by remember { mutableStateOf<AiPlan?>(null) }
    var aiTestLoading by remember { mutableStateOf(false) }
    var workFolderScanning by remember { mutableStateOf(false) }
    var screenSnapshot by remember { mutableStateOf(ScreenStateStore.latest()) }
    var screenMessage by remember { mutableStateOf<String?>(null) }
    var agentLoopState by remember { mutableStateOf(AgentLoopState()) }
    var agentLoopLoading by remember { mutableStateOf(false) }
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
            workFolderCount = SafRootStore.getRootUris(context).size
            workFolderMessage = "Work folder selected."
            executablePlan?.let {
                validationResult = PlanValidator.validate(
                    plan = it,
                    hasWorkFolder = hasFileRoot(workFolderUri, broadStorageGranted),
                    internetConfirmed = internetConfirmed
                )
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
            broadStorageGranted = BroadStorageAccess.isGranted()
            screenSnapshot = ScreenStateStore.latest()
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
        Text(
            text = "Personal Android Agent",
            style = MaterialTheme.typography.titleMedium
        )

        StatusCard(
            accessibilityEnabled = accessibilityEnabled,
            agentEnabled = agentEnabled,
            guardEnabled = guardEnabled,
            aiProvider = AiProviderFactory.providerStatus(aiSettings),
            workFolder = shortUri(workFolderUri),
            fileAccessMode = fileAccessMode(workFolderUri, broadStorageGranted),
            currentApp = screenSnapshot?.appLabel ?: screenSnapshot?.foregroundPackage ?: "Unknown",
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
            rootCount = workFolderCount,
            broadStorageGranted = broadStorageGranted,
            message = workFolderMessage,
            onSelectFolder = { openTreeLauncher.launch(null) },
            onAddFolder = { openTreeLauncher.launch(null) },
            onResetFolder = {
                SafRootStore.getRootUris(context).forEach { releaseSafPermission(context, it) }
                SafRootStore.clear(context)
                LogStore.appendSafAction(context, operation = "reset_work_folder", result = "success")
                workFolderUri = null
                workFolderCount = 0
                workFolderMessage = "Work folder reset."
                executablePlan?.let {
                    validationResult = PlanValidator.validate(
                        plan = it,
                        hasWorkFolder = hasFileRoot(workFolderUri, broadStorageGranted),
                        internetConfirmed = internetConfirmed
                    )
                }
                logs = LogStore.readRecent(context, limit = 50)
            },
            onScanFolder = {
                val uri = workFolderUri
                if (uri == null && !broadStorageGranted) {
                    workFolderMessage = "Select a work folder first."
                } else {
                    coroutineScope.launch {
                        workFolderScanning = true
                        workFolderMessage = "Scanning work folder..."
                        val scanResult = withContext(Dispatchers.IO) {
                            runCatching {
                                val summary = if (uri != null) {
                                    SafFileRepository(context, uri).summarizeFolder()
                                } else {
                                    BroadFileRepository().summarizeFolder()
                                }
                                val message = buildString {
                                    appendLine("Files: ${summary.fileCount}")
                                    appendLine("Folders: ${summary.folderCount}")
                                    appendLine("Extensions: ${summary.extensions.entries.joinToString { "${it.key}=${it.value}" }}")
                                    if (summary.firstFiles.isNotEmpty()) {
                                        appendLine("First files:")
                                        append(summary.firstFiles.joinToString("\n"))
                                    }
                                }
                                LogStore.appendSafAction(context, operation = "scan_work_folder", result = "success")
                                message
                            }
                        }
                        if (scanResult.isSuccess) {
                            workFolderMessage = scanResult.getOrNull()
                        } else {
                            val failure = scanResult.exceptionOrNull()
                            workFolderMessage = "Folder scan failed: ${failure?.message ?: failure?.javaClass?.simpleName ?: "unknown"}"
                            withContext(Dispatchers.IO) {
                                LogStore.appendSafAction(context, operation = "scan_work_folder", result = "failed")
                            }
                        }
                        logs = LogStore.readRecent(context, limit = 50)
                        workFolderScanning = false
                    }
                }
            },
            isScanning = workFolderScanning
        )

        ScreenCard(
            snapshot = screenSnapshot,
            message = screenMessage,
            onRefresh = {
                val refreshed = UiAgentController().readScreen()
                screenSnapshot = refreshed
                screenMessage = if (refreshed == null) {
                    "Accessibility is not ready or no active screen is available."
                } else {
                    LogStore.appendUiAction(context, "read_screen", "success")
                    logs = LogStore.readRecent(context, limit = 50)
                    "Screen refreshed."
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
            onStartAgent = {
                if (commandText.isBlank()) {
                    commandMessage = "Please enter a command first."
                } else {
                    coroutineScope.launch {
                        agentLoopLoading = true
                        commandMessage = null
                        agentLoopState = AgentLoopState(goal = commandText, running = true)
                        try {
                            agentLoopState = AgentLoopController(context).runNextStep(
                                previous = agentLoopState,
                                goal = commandText,
                                internetConfirmed = internetConfirmed,
                                finalSubmitConfirmed = finalSubmitConfirmed
                            )
                            screenSnapshot = ScreenStateStore.latest()
                            logs = LogStore.readRecent(context, limit = 50)
                        } finally {
                            agentLoopLoading = false
                        }
                    }
                }
            },
            onGeneratePlan = {
                if (commandText.isBlank()) {
                    commandMessage = "Please enter a command first."
                    aiPlan = null
                    executablePlan = null
                    validationResult = null
                    executionResult = null
                    internetConfirmed = false
                    finalSubmitConfirmed = false
                    executionPaused = false
                    executionStopped = false
                } else {
                    coroutineScope.launch {
                        commandLoading = true
                        commandMessage = null
                        executionResult = null
                        try {
                            internetConfirmed = false
                            finalSubmitConfirmed = false
                            executionPaused = false
                            executionStopped = false
                            val settings = AiPrefs.load(context)
                            val provider = AiProviderFactory.create(settings)
                            val plan = provider.generatePlan(commandText)
                            if (plan.actionType == AiAction.AskClarifyingQuestion) {
                                commandMessage = plan.explanation
                                aiPlan = null
                                executablePlan = null
                                validationResult = null
                                internetConfirmed = false
                                finalSubmitConfirmed = false
                                LogStore.appendAiPlanGenerated(
                                    context = context,
                                    provider = providerLogName(settings),
                                    actionType = plan.actionType.wireName,
                                    riskLevel = plan.riskLevel.wireName
                                )
                                logs = LogStore.readRecent(context, limit = 50)
                                return@launch
                            }
                            val executable = PlanParser.parse(plan)
                            val validation = PlanValidator.validate(
                                plan = executable,
                                hasWorkFolder = hasFileRoot(workFolderUri, broadStorageGranted),
                                internetConfirmed = internetConfirmed
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

        AgentLoopCard(
            state = agentLoopState,
            isLoading = agentLoopLoading,
            internetConfirmed = internetConfirmed,
            finalSubmitConfirmed = finalSubmitConfirmed,
            onAllowInternet = { internetConfirmed = true },
            onAllowFinalSubmit = { finalSubmitConfirmed = true },
            onContinue = {
                val goal = agentLoopState.goal.ifBlank { commandText }
                coroutineScope.launch {
                    agentLoopLoading = true
                    try {
                        agentLoopState = AgentLoopController(context).runNextStep(
                            previous = agentLoopState.copy(running = true, paused = false, stopped = false),
                            goal = goal,
                            internetConfirmed = internetConfirmed,
                            finalSubmitConfirmed = finalSubmitConfirmed
                        )
                        screenSnapshot = ScreenStateStore.latest()
                        logs = LogStore.readRecent(context, limit = 50)
                    } finally {
                        agentLoopLoading = false
                    }
                }
            },
            onPause = {
                agentLoopState = AgentLoopController(context).pause(agentLoopState)
                logs = LogStore.readRecent(context, limit = 50)
            },
            onStop = {
                agentLoopState = AgentLoopController(context).stop(agentLoopState)
                logs = LogStore.readRecent(context, limit = 50)
            },
            onDiscard = {
                agentLoopState = AgentLoopController(context).discard()
                internetConfirmed = false
                finalSubmitConfirmed = false
            }
        )

        PlanPreviewCard(
            plan = aiPlan,
            executablePlan = executablePlan,
            validationResult = validationResult,
            internetConfirmed = internetConfirmed,
            finalSubmitConfirmed = finalSubmitConfirmed,
            executionPaused = executionPaused,
            executionStopped = executionStopped,
            isExecuting = executionLoading,
            jsonVisible = planJsonVisible,
            onAllowInternet = {
                internetConfirmed = true
                executablePlan?.let {
                    validationResult = PlanValidator.validate(
                        plan = it,
                        hasWorkFolder = hasFileRoot(workFolderUri, broadStorageGranted),
                        internetConfirmed = true
                    )
                }
            },
            onAllowFinalSubmit = {
                finalSubmitConfirmed = true
            },
            onCancelInternet = {
                internetConfirmed = false
                executionResult = ActionExecutionResult(
                    successes = emptyList(),
                    failures = listOf("Internet action cancelled by user."),
                    warnings = emptyList()
                )
            },
            onToggleJson = { planJsonVisible = !planJsonVisible },
            onDiscard = {
                aiPlan = null
                executablePlan = null
                validationResult = null
                executionResult = null
                internetConfirmed = false
                finalSubmitConfirmed = false
                executionPaused = false
                executionStopped = false
                planJsonVisible = false
            },
            onPause = {
                executionPaused = true
                executionResult = ActionExecutionResult(
                    successes = emptyList(),
                    failures = emptyList(),
                    warnings = listOf("Execution is paused. Resume by pressing Execute again.")
                )
            },
            onStop = {
                executionStopped = true
                executionLoading = false
                executionResult = ActionExecutionResult(
                    successes = emptyList(),
                    failures = listOf("Execution stopped by user."),
                    warnings = emptyList()
                )
                LogStore.appendUiAction(context, "stop", "success")
                logs = LogStore.readRecent(context, limit = 50)
            },
            onExecute = {
                val executable = executablePlan ?: return@PlanPreviewCard
                executionPaused = false
                val validation = PlanValidator.validate(
                    plan = executable,
                    hasWorkFolder = hasFileRoot(workFolderUri, broadStorageGranted),
                    internetConfirmed = internetConfirmed
                )
                validationResult = validation
                if (validation.canExecute && !executionStopped) {
                    coroutineScope.launch {
                        executionLoading = true
                        try {
                            executionResult = AgentExecutionController(context).execute(
                                plan = executable,
                                internetConfirmed = internetConfirmed,
                                finalSubmitConfirmed = finalSubmitConfirmed
                            )
                            screenSnapshot = ScreenStateStore.latest()
                            logs = LogStore.readRecent(context, limit = 50)
                        } finally {
                            executionLoading = false
                        }
                    }
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
    fileAccessMode: String,
    currentApp: String,
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
            StatusRow(label = "File Access Mode", value = fileAccessMode)
            StatusRow(label = "Internet Gate", value = "Internet actions require confirmation")
            StatusRow(label = "Current App", value = currentApp)
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
private fun ScreenCard(
    snapshot: ScreenSnapshot?,
    message: String?,
    onRefresh: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Current Screen",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text("App: ${snapshot?.appLabel ?: snapshot?.foregroundPackage ?: "Unknown"}")
            Text("Detected UI elements: ${snapshot?.nodes?.size ?: 0}")
            snapshot?.nodes.orEmpty().take(8).forEach {
                Text(it.compactLine(), style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onRefresh) {
                Text("Bildschirm neu lesen")
            }
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun WorkFolderCard(
    rootUri: Uri?,
    rootCount: Int,
    broadStorageGranted: Boolean,
    message: String?,
    isScanning: Boolean,
    onSelectFolder: () -> Unit,
    onAddFolder: () -> Unit,
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
            Text("SAF folders selected: $rootCount")
            Text(BroadStorageAccess.statusText())
            Text("Nani darf nicht selbst in Settings gehen. Rechte müssen manuell vom Nutzer gesetzt werden.")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = onSelectFolder) {
                    Text("Arbeitsordner auswählen")
                }
                Button(onClick = onAddFolder) {
                    Text("Weitere hinzufügen")
                }
                Button(onClick = onScanFolder, enabled = (rootUri != null || broadStorageGranted) && !isScanning) {
                    Text(if (isScanning) "Scanning..." else "Dateien listen")
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
    onStartAgent: () -> Unit,
    onGeneratePlan: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Was soll Nani tun?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text("Beispiele: Öffne Chrome und suche nach ..., Fülle dieses Formular aus, Lies diese Webseite zusammen, Sortiere meine Downloads.")
            OutlinedTextField(
                value = command,
                onValueChange = onCommandChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Was soll Nani tun?") },
                placeholder = { Text("Sortiere meine PDFs für Schule") },
                minLines = 3,
                maxLines = 6
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = onStartAgent, enabled = !isGenerating) {
                    Text("Agent starten")
                }
                Button(onClick = onGeneratePlan, enabled = !isGenerating) {
                    Text(if (isGenerating) "Plan wird erstellt..." else "Plan erstellen")
                }
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
private fun AgentLoopCard(
    state: AgentLoopState,
    isLoading: Boolean,
    internetConfirmed: Boolean,
    finalSubmitConfirmed: Boolean,
    onAllowInternet: () -> Unit,
    onAllowFinalSubmit: () -> Unit,
    onContinue: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onDiscard: () -> Unit
) {
    if (state.goal.isBlank() && state.currentStep == null && !isLoading) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Agent Loop",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text("Goal: ${state.goal}")
            Text("Steps: ${state.stepCount}/${state.maxSteps}")
            Text("Status: ${loopStatusText(state, isLoading)}")
            state.currentStep?.let { step ->
                Text("Current step: ${step.explanation}")
                if (step.operations.isNotEmpty()) {
                    Text("Next action: ${step.operations.first().rawOp}")
                }
            }
            state.lastAction?.let { Text("Last action: $it") }
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            state.lastResult?.let { result ->
                result.successes.take(2).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                result.failures.take(2).forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            if (state.stopReason == AgentStopReason.NeedsInternetConfirmation && !internetConfirmed) {
                Text("Internet erlauben? Browser/Web actions can transfer data outside the device.")
                Button(onClick = onAllowInternet) {
                    Text("Internet erlauben")
                }
            }
            if (state.stopReason == AgentStopReason.NeedsFinalSubmitConfirmation && !finalSubmitConfirmed) {
                Text("Formular wirklich absenden?")
                Button(onClick = onAllowFinalSubmit) {
                    Text("Absenden erlauben")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onContinue,
                    enabled = !isLoading && !state.stopped && state.stepCount < state.maxSteps
                ) {
                    Text(if (isLoading) "Läuft..." else "Weiter")
                }
                Button(onClick = onPause, enabled = !isLoading && !state.paused && !state.stopped) {
                    Text("Pause")
                }
                Button(onClick = onStop, enabled = !state.stopped) {
                    Text("Stop")
                }
                Button(onClick = onDiscard) {
                    Text("Verwerfen")
                }
            }
        }
    }
}

@Composable
private fun PlanPreviewCard(
    plan: AiPlan?,
    executablePlan: ExecutablePlan?,
    validationResult: PlanValidationResult?,
    internetConfirmed: Boolean,
    finalSubmitConfirmed: Boolean,
    executionPaused: Boolean,
    executionStopped: Boolean,
    isExecuting: Boolean,
    jsonVisible: Boolean,
    onAllowInternet: () -> Unit,
    onAllowFinalSubmit: () -> Unit,
    onCancelInternet: () -> Unit,
    onToggleJson: () -> Unit,
    onDiscard: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
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
            Text("Requires internet confirmation: ${plan.requiresInternetConfirmation}")
            Text("Requires final submit confirmation: ${validationResult?.requiresFinalSubmitConfirmation == true}")
            Text(plan.explanation)
            if (plan.actionType == AiAction.Blocked) {
                Text(
                    text = "This action is blocked by Nani safety rules.",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
            OperationList(executablePlan.operations)
            if (validationResult?.requiresInternetConfirmation == true && !internetConfirmed) {
                InternetConfirmationCard(
                    plan = plan,
                    operations = executablePlan.operations,
                    onAllowInternet = onAllowInternet,
                    onCancelInternet = onCancelInternet
                )
            }
            if (validationResult?.requiresFinalSubmitConfirmation == true && !finalSubmitConfirmed) {
                FinalSubmitConfirmationCard(onAllowFinalSubmit = onAllowFinalSubmit)
            }
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
                Button(onClick = onExecute, enabled = canExecute && !isExecuting && !executionStopped) {
                    Text(if (isExecuting) "Ausführen..." else "Ausführen")
                }
                Button(onClick = onPause, enabled = !executionPaused && !executionStopped) {
                    Text("Pause")
                }
                Button(onClick = onStop) {
                    Text("Stop")
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
private fun InternetConfirmationCard(
    plan: AiPlan,
    operations: List<PlanOperation>,
    onAllowInternet: () -> Unit,
    onCancelInternet: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Internet Confirmation", fontWeight = FontWeight.SemiBold)
            Text("Purpose: ${plan.explanation}")
            operations.filter { it.url != null || it.reason != null }.forEach {
                Text("Target: ${it.url ?: "unknown"}")
                Text("Reason: ${it.reason ?: "not specified"}")
            }
            Text("Data may leave the device if this action is later implemented.")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onAllowInternet) {
                    Text("Internetaktion erlauben")
                }
                Button(onClick = onCancelInternet) {
                    Text("Abbrechen")
                }
            }
        }
    }
}

@Composable
private fun FinalSubmitConfirmationCard(
    onAllowFinalSubmit: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Final Submit Confirmation", fontWeight = FontWeight.SemiBold)
            Text("Formular wirklich absenden? Sending, posting, buying, booking, or mailing always needs this extra confirmation.")
            Button(onClick = onAllowFinalSubmit) {
                Text("Formular absenden erlauben")
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
            Text("Move/delete-like operations remain blocked. Rename is allowed only inside an approved file root and never overwrites.")
            Text("No device administrator, root, or overlay permissions are requested.")
            Text("Accessibility stays inactive until manually enabled in Android settings.")
            Text("When the agent is active, foreground packages are logged to app-private storage.")
            Text("When the guard is active, Settings and permission-management screens are blocked with Back, then Home.")
            Text("AI can only produce JSON plans; local validation decides what may execute.")
            Text("Allowed file actions: list, read, summarize, search, classify, create folders/files, edit text, append text, copy, and rename.")
            Text("SAF Workspace is recommended. Broad Agent Storage is optional and must be granted manually by the user.")
            Text("Browser and internet actions require separate confirmation and are not executed blindly.")
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

private fun hasFileRoot(uri: Uri?, broadStorageGranted: Boolean): Boolean {
    return uri != null || broadStorageGranted
}

private fun fileAccessMode(uri: Uri?, broadStorageGranted: Boolean): String {
    return when {
        uri != null -> "SAF Workspace"
        broadStorageGranted -> "Broad Agent Storage granted"
        else -> "Broad Agent Storage not granted"
    }
}

private fun loopStatusText(state: AgentLoopState, isLoading: Boolean): String {
    return when {
        isLoading -> "Running one visible step"
        state.stopped -> "Stopped: ${state.stopReason}"
        state.paused -> "Paused: ${state.stopReason}"
        state.running -> "Ready for next step"
        else -> "Idle"
    }
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
        "read_file" -> "Datei lesen: ${operation.path.orEmpty()}"
        "summarize_file" -> "Datei zusammenfassen: ${operation.path.orEmpty()}"
        "summarize_folder" -> "Arbeitsordner zusammenfassen"
        "search_files" -> "Dateien suchen: ${operation.query.orEmpty()}"
        "classify_files" -> "Dateien klassifizieren"
        "create_folder" -> "Ordner erstellen: ${operation.path.orEmpty()}"
        "create_file" -> "Datei erstellen: ${operation.path.orEmpty()}"
        "edit_text_file" -> "Textdatei bearbeiten: ${operation.path.orEmpty()}"
        "append_text_file" -> "Text anhängen: ${operation.path.orEmpty()}"
        "copy_file" -> "Datei kopieren: ${operation.from.orEmpty()} -> ${operation.to.orEmpty()}"
        "rename_file" -> "Datei umbenennen: ${operation.from.orEmpty()} -> ${operation.to.orEmpty()}"
        "open_url" -> "Internet/URL öffnen: ${operation.url.orEmpty()}"
        "use_app" -> "App-Aktion: ${operation.reason.orEmpty()}"
        "read_screen" -> "Aktuellen Bildschirm lesen"
        "tap_node" -> "Sichtbares UI-Element antippen: ${operation.targetTextOrHint ?: operation.text.orEmpty()}"
        "set_text" -> "Textfeld ausfüllen: ${operation.targetTextOrHint.orEmpty()}"
        "append_text" -> "Text anfügen: ${operation.targetTextOrHint.orEmpty()}"
        "scroll" -> "Sichtbaren Bereich scrollen"
        "scroll_forward" -> "Sichtbaren Bereich vorwärts scrollen"
        "scroll_backward" -> "Sichtbaren Bereich rückwärts scrollen"
        "press_back" -> "Zurück drücken"
        "press_home" -> "Home drücken"
        "open_app" -> "Erlaubte App öffnen"
        "wait_for_screen" -> "Auf Bildschirm warten"
        "wait" -> "Kurz warten"
        "find_node" -> "UI-Element suchen: ${operation.targetTextOrHint ?: operation.text.orEmpty()}"
        "select_option" -> "Option auswählen: ${operation.targetTextOrHint ?: operation.text.orEmpty()}"
        "fill_form" -> "Formular vorbereiten"
        "set_field_by_label" -> "Feld ausfüllen: ${operation.label.orEmpty()}"
        "set_field_by_hint" -> "Feld nach Hinweis ausfüllen: ${operation.targetTextOrHint.orEmpty()}"
        "set_field_by_node_id" -> "Feld nach Node-ID ausfüllen: ${operation.targetNodeId ?: "-"}"
        "click_button_by_text" -> "Button klicken: ${operation.text.orEmpty()}"
        "submit_form" -> "Formular absenden, nur nach Extra-Bestätigung"
        else -> "Nicht unterstützte Operation: ${operation.rawOp}"
    }
}
