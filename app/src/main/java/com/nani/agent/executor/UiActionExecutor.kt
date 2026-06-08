package com.nani.agent.executor

import com.nani.agent.saf.ActionExecutionResult

class UiActionExecutor {
    fun refuseForThisBuild(): ActionExecutionResult {
        return ActionExecutionResult(
            successes = emptyList(),
            failures = listOf("UI/App actions are not executable in this build. No blind clicks are performed."),
            warnings = emptyList()
        )
    }
}
