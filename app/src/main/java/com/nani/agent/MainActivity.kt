package com.nani.agent

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.nani.agent.ai.AiSettings
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
    var accessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var agentEnabled by remember { mutableStateOf(AgentPrefs.isAgentEnabled(context)) }
    var guardEnabled by remember { mutableStateOf(AgentPrefs.isGuardEnabled(context)) }
    var aiSettings by remember { mutableStateOf(AiPrefs.load(context)) }
    var aiTestPlan by remember { mutableStateOf<AiPlan?>(null) }
    var aiTestLoading by remember { mutableStateOf(false) }
    var commandText by remember { mutableStateOf("") }
    var commandPlan by remember { mutableStateOf<AiPlan?>(null) }
    var commandMessage by remember { mutableStateOf<String?>(null) }
    var commandLoading by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf(LogStore.readRecent(context, limit = 40)) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            accessibilityEnabled = isAccessibilityServiceEnabled(context)
            agentEnabled = AgentPrefs.isAgentEnabled(context)
            guardEnabled = AgentPrefs.isGuardEnabled(context)
            logs = LogStore.readRecent(context, limit = 40)
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
            onOpenSettings = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onToggleAgent = {
                val enabled = !AgentPrefs.isAgentEnabled(context)
                AgentPrefs.setAgentEnabled(context, enabled)
                LogStore.appendControlChange(context, control = "agent", enabled = enabled)
                agentEnabled = AgentPrefs.isAgentEnabled(context)
                logs = LogStore.readRecent(context, limit = 40)
            },
            onToggleGuard = {
                val enabled = !AgentPrefs.isGuardEnabled(context)
                AgentPrefs.setGuardEnabled(context, enabled)
                LogStore.appendControlChange(context, control = "guard", enabled = enabled)
                guardEnabled = AgentPrefs.isGuardEnabled(context)
                logs = LogStore.readRecent(context, limit = 40)
            }
        )

        if (!agentEnabled) {
            WarningCard("Nani Agent is disabled. No monitoring, logging, or blocking is active.")
        } else if (!guardEnabled) {
            WarningCard("Nani Guard is disabled. Nani is logging only and will not block protected screens.")
        }

        AgentCommandCenterCard(
            command = commandText,
            plan = commandPlan,
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
                    commandPlan = null
                } else {
                    coroutineScope.launch {
                        commandLoading = true
                        commandMessage = null
                        val userCommand = commandText
                        try {
                            val currentSettings = AiPrefs.load(context)
                            val provider = AiProviderFactory.create(currentSettings)
                            val plan = provider.generatePlan(userCommand)
                            commandPlan = plan
                            LogStore.appendAiPlanGenerated(
                                context = context,
                                provider = providerLogName(currentSettings),
                                actionType = plan.actionType.wireName,
                                riskLevel = plan.riskLevel.wireName
                            )
                            logs = LogStore.readRecent(context, limit = 40)
                        } finally {
                            commandLoading = false
                        }
                    }
                }
            },
            onClearPlan = {
                commandPlan = null
                commandMessage = null
            }
        )

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
                        logs = LogStore.readRecent(context, limit = 40)
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
private fun AgentCommandCenterCard(
    command: String,
    plan: AiPlan?,
    message: String?,
    isGenerating: Boolean,
    onCommandChanged: (String) -> Unit,
    onGeneratePlan: () -> Unit,
    onClearPlan: () -> Unit
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = onGeneratePlan, enabled = !isGenerating) {
                    Text(if (isGenerating) "Generating..." else "Generate Plan")
                }
                Button(onClick = onClearPlan, enabled = !isGenerating && plan != null) {
                    Text("Clear Plan")
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
            if (plan != null) {
                PlanPreview(plan = plan, showConfirmationControls = true)
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
                label = "Local Gemma 4 E2B",
                selected = settings.providerType == AiPrefs.PROVIDER_LOCAL_GEMMA_4_E2B,
                onClick = {
                    onSettingsChanged(
                        settings.copy(
                            providerType = AiPrefs.PROVIDER_LOCAL_GEMMA_4_E2B,
                            modelName = AiPrefs.DEFAULT_LOCAL_MODEL_NAME
                        )
                    )
                }
            )
            ProviderOption(
                label = "OpenAI Compatible API",
                selected = settings.providerType == AiPrefs.PROVIDER_OPENAI_COMPATIBLE_API,
                onClick = {
                    onSettingsChanged(settings.copy(providerType = AiPrefs.PROVIDER_OPENAI_COMPATIBLE_API))
                }
            )
            ProviderOption(
                label = "Custom API",
                selected = settings.providerType == AiPrefs.PROVIDER_CUSTOM_API,
                onClick = {
                    onSettingsChanged(settings.copy(providerType = AiPrefs.PROVIDER_CUSTOM_API))
                }
            )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = onSave) {
                    Text("Save AI Settings")
                }
                Button(onClick = onTest, enabled = !isTesting) {
                    Text(if (isTesting) "Testing AI..." else "Test AI")
                }
            }
            if (isTesting) {
                Text("Testing selected AI provider...")
            }
            if (testPlan != null) {
                PlanPreview(plan = testPlan, showConfirmationControls = false)
            }
        }
    }
}

@Composable
private fun PlanPreview(plan: AiPlan, showConfirmationControls: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Plan Preview",
            style = MaterialTheme.typography.titleMedium,
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
        if (showConfirmationControls && plan.requiresConfirmation) {
            Button(onClick = {}, enabled = false) {
                Text("Confirm Action - Not implemented yet")
            }
            Text("Real file actions are not implemented yet. Nani can only generate safe JSON plans.")
        }
        Text(
            text = "Proposed JSON",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = plan.proposedJson,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall
        )
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
private fun StatusCard(
    accessibilityEnabled: Boolean,
    agentEnabled: Boolean,
    guardEnabled: Boolean,
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
                text = "Agent Status",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            StatusRow(label = "Accessibility", value = if (accessibilityEnabled) "Enabled" else "Disabled")
            StatusRow(label = "Agent", value = if (agentEnabled) "Active" else "Disabled")
            StatusRow(label = "Guard", value = if (guardEnabled) "Active" else "Disabled")
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
            Text("No device administrator, root, or overlay permissions are requested.")
            Text("Accessibility stays inactive until manually enabled in Android settings.")
            Text("When the agent is active, foreground packages are logged to app-private storage.")
            Text("When the guard is active, Settings and permission-management screens are blocked with Back, then Home.")
            Text("AI can only produce suggestions and JSON plans.")
            Text("AI never executes Android actions or file operations.")
            Text("Unknown apps are observe-only. No arbitrary app automation is implemented.")
            Text("No Google Drive API, native inference runtime, or local fallback model is included.")
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
        AiPrefs.PROVIDER_LOCAL_GEMMA_4_E2B -> "local_gemma_4_e2b"
        AiPrefs.PROVIDER_OPENAI_COMPATIBLE_API -> "openai_compatible_api"
        AiPrefs.PROVIDER_CUSTOM_API -> "custom_api"
        else -> "unknown"
    }
}
