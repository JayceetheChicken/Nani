package com.nani.agent.saf

import android.content.Context
import com.nani.agent.executor.AgentExecutionController
import com.nani.agent.plan.ExecutablePlan

class ActionExecutor(
    private val context: Context
) {
    suspend fun execute(plan: ExecutablePlan): ActionExecutionResult {
        return AgentExecutionController(context).execute(plan, internetConfirmed = false)
    }
}
