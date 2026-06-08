package com.nani.agent.executor

import android.content.Context
import com.nani.agent.LogStore
import com.nani.agent.plan.ExecutablePlan
import com.nani.agent.plan.PlanOperationType
import com.nani.agent.plan.PlanValidator
import com.nani.agent.saf.ActionExecutionResult
import com.nani.agent.saf.BroadStorageAccess
import com.nani.agent.saf.SafRootStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AgentExecutionController(
    private val context: Context
) {
    suspend fun execute(
        plan: ExecutablePlan,
        internetConfirmed: Boolean
    ): ActionExecutionResult = withContext(Dispatchers.IO) {
        val hasFileRoot = SafRootStore.getRootUri(context) != null || BroadStorageAccess.isGranted()
        val validation = PlanValidator.validate(
            plan = plan,
            hasWorkFolder = hasFileRoot,
            internetConfirmed = internetConfirmed
        )
        if (!validation.isValid) {
            LogStore.appendSafAction(context, operation = "execute_plan", result = "blocked")
            return@withContext ActionExecutionResult(
                successes = emptyList(),
                failures = validation.errors.ifEmpty { listOf("Plan failed final executor validation.") },
                warnings = validation.warnings
            )
        }

        if (!InternetActionGate.canProceed(plan, internetConfirmed)) {
            LogStore.appendSafAction(context, operation = "internet_gate", result = "blocked")
            return@withContext ActionExecutionResult(
                successes = emptyList(),
                failures = listOf("Internet action requires explicit confirmation."),
                warnings = validation.warnings
            )
        }

        if (plan.operations.any { it.op == PlanOperationType.OpenUrl || it.op == PlanOperationType.UseApp }) {
            return@withContext UiActionExecutor().refuseForThisBuild()
        }

        val result = FileActionExecutor(context).execute(plan)
        return@withContext result.copy(warnings = validation.warnings + result.warnings)
    }
}
