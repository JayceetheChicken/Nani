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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nani.agent.agent.NaniAccessibilityService
import kotlinx.coroutines.delay

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
    var logs by remember { mutableStateOf(LogStore.readRecent(context, limit = 40)) }

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

        SecurityRulesCard()

        LogsCard(logs = logs)
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
            Text("Unknown apps are observe-only. No arbitrary app automation is implemented.")
            Text("No Google Drive API and no AI model are included.")
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
