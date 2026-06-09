package com.nani.agent.uiagent

import com.nani.agent.LogStore
import com.nani.agent.agent.NaniAccessibilityService
import com.nani.agent.saf.ActionExecutionResult
import kotlinx.coroutines.delay

class UiAgentController {
    fun readScreen(): ScreenSnapshot? {
        return NaniAccessibilityService.activeService()?.let { service ->
            AccessibilityTreeReader(service).readCurrentScreen()?.also { snapshot ->
                ScreenStateStore.update(snapshot)
                LogStore.appendForegroundPackage(service, snapshot.foregroundPackage)
            }
        }
    }

    suspend fun waitForUiSettledAndReadScreen(timeoutMs: Long = 3_000): ScreenSnapshot? {
        val service = NaniAccessibilityService.activeService() ?: return null
        val reader = AccessibilityTreeReader(service)
        val deadline = System.currentTimeMillis() + timeoutMs
        var latest: ScreenSnapshot? = null
        var stableReads = 0
        var lastSignature: String? = null

        while (System.currentTimeMillis() < deadline) {
            val snapshot = reader.readCurrentScreen()
            if (snapshot != null) {
                ScreenStateStore.update(snapshot)
                LogStore.appendForegroundPackage(service, snapshot.foregroundPackage)
                latest = snapshot
                val signature = "${snapshot.foregroundPackage}:${snapshot.windowTitle}:${snapshot.nodes.size}"
                stableReads = if (signature == lastSignature) stableReads + 1 else 1
                lastSignature = signature
                if (stableReads >= 2) return snapshot
            }
            delay(300)
        }
        return latest
    }

    suspend fun execute(
        actions: List<UiAction>,
        internetConfirmed: Boolean,
        finalSubmitConfirmed: Boolean
    ): ActionExecutionResult {
        val service = NaniAccessibilityService.activeService()
            ?: return ActionExecutionResult(
                successes = emptyList(),
                failures = listOf("Accessibility service is not active."),
                warnings = emptyList()
            )
        return UiActionExecutor(service).execute(actions, internetConfirmed, finalSubmitConfirmed)
    }
}
