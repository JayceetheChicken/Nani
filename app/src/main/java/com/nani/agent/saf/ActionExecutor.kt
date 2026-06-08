package com.nani.agent.saf

import android.content.Context
import com.nani.agent.executor.AgentExecutionController
import com.nani.agent.plan.ExecutablePlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ActionExecutor(
    private val context: Context
) {
    suspend fun execute(plan: ExecutablePlan): ActionExecutionResult = withContext(Dispatchers.IO) {
        AgentExecutionController(context).execute(plan, internetConfirmed = false)
    }
}
