package com.nani.agent.executor

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.nani.agent.LogStore
import com.nani.agent.plan.ExecutablePlan
import com.nani.agent.plan.PlanOperation
import com.nani.agent.plan.PlanOperationType
import com.nani.agent.plan.PlanValidator
import com.nani.agent.saf.ActionExecutionResult
import com.nani.agent.saf.BroadStorageAccess
import com.nani.agent.saf.SafRootStore
import com.nani.agent.uiagent.UiAction
import com.nani.agent.uiagent.UiActionTarget
import com.nani.agent.uiagent.UiActionType
import com.nani.agent.uiagent.UiAgentController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AgentExecutionController(
    private val context: Context
) {
    suspend fun execute(
        plan: ExecutablePlan,
        internetConfirmed: Boolean,
        finalSubmitConfirmed: Boolean = false,
        shouldContinue: () -> Boolean = { true }
    ): ActionExecutionResult = withContext(Dispatchers.IO) {
        if (!shouldContinue()) {
            return@withContext ActionExecutionResult(
                successes = emptyList(),
                failures = listOf("Execution is not running."),
                warnings = emptyList()
            )
        }

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

        if (plan.operations.any { it.op in uiOperationTypes }) {
            if (plan.operations.all { it.op == PlanOperationType.OpenUrl }) {
                return@withContext openUrls(plan.operations)
            }
            return@withContext UiAgentController().execute(
                actions = plan.operations.mapNotNull { it.toUiAction() },
                internetConfirmed = internetConfirmed,
                finalSubmitConfirmed = finalSubmitConfirmed
            )
        }

        val result = FileActionExecutor(context, shouldContinue).execute(plan)
        return@withContext result.copy(warnings = validation.warnings + result.warnings)
    }

    private fun PlanOperation.toUiAction(): UiAction? {
        val type = when (op) {
            PlanOperationType.ReadScreen -> UiActionType.ReadScreen
            PlanOperationType.TapNode -> UiActionType.TapNode
            PlanOperationType.SetText -> UiActionType.SetText
            PlanOperationType.AppendText -> UiActionType.AppendText
            PlanOperationType.Scroll -> UiActionType.Scroll
            PlanOperationType.ScrollForward -> UiActionType.ScrollForward
            PlanOperationType.ScrollBackward -> UiActionType.ScrollBackward
            PlanOperationType.PressBack -> UiActionType.PressBack
            PlanOperationType.PressHome -> UiActionType.PressHome
            PlanOperationType.OpenApp,
            PlanOperationType.UseApp -> UiActionType.OpenApp
            PlanOperationType.WaitForScreen -> UiActionType.WaitForScreen
            PlanOperationType.Wait -> UiActionType.Wait
            PlanOperationType.FindNode -> UiActionType.FindNode
            PlanOperationType.SelectOption -> UiActionType.SelectOption
            PlanOperationType.OpenUrl -> UiActionType.OpenUrl
            PlanOperationType.FillForm -> UiActionType.FillForm
            PlanOperationType.SetFieldByLabel -> UiActionType.SetFieldByLabel
            PlanOperationType.SetFieldByHint -> UiActionType.SetFieldByHint
            PlanOperationType.SetFieldByNodeId -> UiActionType.SetFieldByNodeId
            PlanOperationType.ClickButtonByText -> UiActionType.ClickButtonByText
            PlanOperationType.SubmitForm -> UiActionType.SubmitForm
            else -> return null
        }
        return UiAction(
            op = type,
            rawOp = rawOp,
            target = UiActionTarget(
                nodeId = targetNodeId,
                textOrHint = targetTextOrHint ?: label ?: text,
                label = label,
                viewIdResourceName = targetViewIdResourceName
            ),
            text = text ?: content,
            url = url,
            reason = reason,
            requiresFinalSubmitConfirmation = requiresFinalSubmitConfirmation
        )
    }

    private val uiOperationTypes = setOf(
        PlanOperationType.ReadScreen,
        PlanOperationType.TapNode,
        PlanOperationType.SetText,
        PlanOperationType.AppendText,
        PlanOperationType.Scroll,
        PlanOperationType.ScrollForward,
        PlanOperationType.ScrollBackward,
        PlanOperationType.PressBack,
        PlanOperationType.PressHome,
        PlanOperationType.OpenApp,
        PlanOperationType.WaitForScreen,
        PlanOperationType.Wait,
        PlanOperationType.FindNode,
        PlanOperationType.SelectOption,
        PlanOperationType.OpenUrl,
        PlanOperationType.UseApp,
        PlanOperationType.FillForm,
        PlanOperationType.SetFieldByLabel,
        PlanOperationType.SetFieldByHint,
        PlanOperationType.SetFieldByNodeId,
        PlanOperationType.ClickButtonByText,
        PlanOperationType.SubmitForm
    )

    private fun openUrls(operations: List<PlanOperation>): ActionExecutionResult {
        val successes = mutableListOf<String>()
        val failures = mutableListOf<String>()
        operations.forEach { operation ->
            val result = runCatching {
                val url = operation.url ?: error("URL missing.")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                LogStore.appendUiAction(context, "open_url", "success")
                "Opened URL after internet confirmation"
            }
            if (result.isSuccess) {
                successes += result.getOrThrow()
            } else {
                failures += "open_url: ${result.exceptionOrNull()?.message ?: "failed"}"
                LogStore.appendUiAction(context, "open_url", "failed")
            }
        }
        return ActionExecutionResult(successes, failures, emptyList())
    }
}
