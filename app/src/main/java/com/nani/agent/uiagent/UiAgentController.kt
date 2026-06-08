package com.nani.agent.uiagent

import com.nani.agent.agent.NaniAccessibilityService
import com.nani.agent.saf.ActionExecutionResult

class UiAgentController {
    fun readScreen(): ScreenSnapshot? {
        return NaniAccessibilityService.activeService()?.let {
            AccessibilityTreeReader(it).readCurrentScreen()?.also(ScreenStateStore::update)
        }
    }

    fun execute(
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
