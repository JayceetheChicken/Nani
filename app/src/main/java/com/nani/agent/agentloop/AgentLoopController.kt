package com.nani.agent.agentloop

import android.content.Context
import com.nani.agent.LogStore
import com.nani.agent.ai.AiAction
import com.nani.agent.ai.AiPrefs
import com.nani.agent.ai.AiProviderFactory
import com.nani.agent.executor.AgentExecutionController
import com.nani.agent.memory.MemoryStore
import com.nani.agent.plan.ExecutablePlan
import com.nani.agent.plan.PlanParser
import com.nani.agent.plan.PlanValidator
import com.nani.agent.saf.BroadStorageAccess
import com.nani.agent.saf.SafRootStore
import com.nani.agent.uiagent.UiAgentController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class AgentLoopController(
    private val context: Context
) {
    suspend fun runNextStep(
        previous: AgentLoopState,
        goal: String,
        internetConfirmed: Boolean,
        finalSubmitConfirmed: Boolean
    ): AgentLoopState = withContext(Dispatchers.IO) {
        if (goal.isBlank()) {
            return@withContext previous.copy(
                goal = goal,
                running = false,
                paused = false,
                stopped = true,
                stopReason = AgentStopReason.Error,
                message = "Please enter a goal first."
            )
        }
        if (previous.stepCount >= previous.maxSteps) {
            return@withContext previous.copy(
                running = false,
                paused = false,
                stopped = true,
                stopReason = AgentStopReason.MaxStepsReached,
                message = "Max steps reached."
            )
        }

        runCatching {
            withTimeout(STEP_TIMEOUT_MS) {
                val uiAgent = UiAgentController()
                val snapshot = uiAgent.waitForUiSettledAndReadScreen(timeoutMs = 1_500)
                    ?: uiAgent.readScreen()
                val foregroundBefore = snapshot?.foregroundPackage.orEmpty().ifBlank { "unknown" }
                val settings = AiPrefs.load(context)
                val provider = AiProviderFactory.create(settings)
                val plan = provider.generatePlan(buildStepPrompt(goal, snapshot?.summary))
                val executable = PlanParser.parse(plan).singleStep()
                val taskDone = (plan.done || executable.done) && executable.operations.isEmpty()
                val hasFileRoot = SafRootStore.getRootUri(context) != null || BroadStorageAccess.isGranted()
                val validation = PlanValidator.validate(
                    plan = executable,
                    hasWorkFolder = hasFileRoot,
                    internetConfirmed = internetConfirmed
                )
                LogStore.appendAiPlanGenerated(
                    context = context,
                    provider = providerName(settings.providerType),
                    actionType = plan.actionType.wireName,
                    riskLevel = plan.riskLevel.wireName
                )

                val step = AgentStep(
                    number = previous.stepCount + 1,
                    explanation = plan.explanation,
                    operations = executable.operations,
                    done = taskDone
                )

                when {
                    taskDone -> previous.copy(
                        goal = goal,
                        running = false,
                        paused = false,
                        stopped = true,
                        stepCount = previous.stepCount + 1,
                        currentStep = step,
                        nextPlan = plan,
                        stopReason = AgentStopReason.GoalDone,
                        message = plan.explanation
                    ).also {
                        logLoopStep(it, foregroundBefore, executable.operations.firstOrNull()?.rawOp ?: "none", plan.explanation, foregroundBefore)
                    }
                    plan.actionType == AiAction.Blocked || validation.errors.any { it.contains("forbidden", ignoreCase = true) } -> previous.copy(
                        goal = goal,
                        running = false,
                        paused = false,
                        stopped = true,
                        stepCount = previous.stepCount + 1,
                        currentStep = step,
                        nextPlan = plan,
                        stopReason = AgentStopReason.PolicyBlocked,
                        message = validation.errors.firstOrNull() ?: plan.explanation
                    ).also {
                        logLoopStep(it, foregroundBefore, executable.operations.firstOrNull()?.rawOp ?: "none", it.message ?: "blocked", foregroundBefore)
                    }
                    validation.requiresInternetConfirmation && !internetConfirmed -> previous.copy(
                        goal = goal,
                        running = false,
                        paused = true,
                        stopped = false,
                        stepCount = previous.stepCount,
                        currentStep = step,
                        nextPlan = plan,
                        stopReason = AgentStopReason.NeedsInternetConfirmation,
                        message = "Internet confirmation is required before the next step."
                    ).also {
                        logLoopStep(it, foregroundBefore, executable.operations.firstOrNull()?.rawOp ?: "none", it.message ?: "confirmation_required", foregroundBefore)
                    }
                    validation.requiresFinalSubmitConfirmation && !finalSubmitConfirmed -> previous.copy(
                        goal = goal,
                        running = false,
                        paused = true,
                        stopped = false,
                        stepCount = previous.stepCount,
                        currentStep = step,
                        nextPlan = plan,
                        stopReason = AgentStopReason.NeedsFinalSubmitConfirmation,
                        message = "Final submit confirmation is required."
                    ).also {
                        logLoopStep(it, foregroundBefore, executable.operations.firstOrNull()?.rawOp ?: "none", it.message ?: "confirmation_required", foregroundBefore)
                    }
                    !validation.canExecute -> previous.copy(
                        goal = goal,
                        running = false,
                        paused = true,
                        stopped = false,
                        stepCount = previous.stepCount,
                        currentStep = step,
                        nextPlan = plan,
                        stopReason = AgentStopReason.PolicyBlocked,
                        message = validation.errors.joinToString("; ")
                    ).also {
                        logLoopStep(it, foregroundBefore, executable.operations.firstOrNull()?.rawOp ?: "none", it.message ?: "validation_error", foregroundBefore)
                    }
                    else -> {
                        val result = AgentExecutionController(context).execute(
                            plan = executable,
                            internetConfirmed = internetConfirmed,
                            finalSubmitConfirmed = finalSubmitConfirmed
                        )
                        val afterSnapshot = uiAgent.waitForUiSettledAndReadScreen()
                            ?: uiAgent.readScreen()
                        afterSnapshot?.let {
                            LogStore.appendScreenSummary(
                                context = context,
                                foregroundPackage = it.foregroundPackage,
                                nodeCount = it.nodes.size,
                                summary = it.summary
                            )
                        }
                        val foregroundAfter = afterSnapshot?.foregroundPackage.orEmpty().ifBlank { "unknown" }
                        val actionRawOp = executable.operations.firstOrNull()?.rawOp ?: "none"
                        val resultText = result.failures.firstOrNull() ?: result.successes.firstOrNull() ?: "no_result"
                        previous.copy(
                            goal = goal,
                            running = true,
                            paused = false,
                            stopped = false,
                            stepCount = previous.stepCount + 1,
                            currentStep = step,
                            lastAction = executable.operations.firstOrNull()?.rawOp,
                            nextPlan = plan,
                            lastResult = result,
                            stopReason = AgentStopReason.None,
                            message = resultText
                        ).also {
                            logLoopStep(it, foregroundBefore, actionRawOp, resultText, foregroundAfter)
                        }
                    }
                }
            }
        }.getOrElse { exception ->
            previous.copy(
                goal = goal,
                running = false,
                paused = true,
                stopped = false,
                stopReason = if (exception is kotlinx.coroutines.TimeoutCancellationException) {
                    AgentStopReason.Timeout
                } else {
                    AgentStopReason.Error
                },
                message = exception.message ?: exception::class.java.simpleName
            ).also {
                LogStore.appendAgentLoopStep(
                    context = context,
                    stepNumber = previous.stepCount + 1,
                    foregroundBefore = "unknown",
                    action = previous.currentStep?.operations?.firstOrNull()?.rawOp ?: "unknown",
                    result = exception.message ?: exception::class.java.simpleName,
                    foregroundAfter = "unknown",
                    stopReason = it.stopReason.name
                )
            }
        }
    }

    fun stop(state: AgentLoopState): AgentLoopState {
        LogStore.appendUiAction(context, "agent_loop_stop", "success")
        return state.copy(running = false, paused = false, stopped = true, stopReason = AgentStopReason.UserStopped)
    }

    fun pause(state: AgentLoopState): AgentLoopState {
        LogStore.appendUiAction(context, "agent_loop_pause", "success")
        return state.copy(running = false, paused = true, stopped = false, stopReason = AgentStopReason.Paused)
    }

    fun discard(): AgentLoopState = AgentLoopState()

    private fun logLoopStep(
        state: AgentLoopState,
        foregroundBefore: String,
        action: String,
        result: String,
        foregroundAfter: String
    ) {
        LogStore.appendAgentLoopStep(
            context = context,
            stepNumber = state.stepCount,
            foregroundBefore = foregroundBefore,
            action = action,
            result = result,
            foregroundAfter = foregroundAfter,
            stopReason = state.stopReason.name
        )
    }

    private fun ExecutablePlan.singleStep(): ExecutablePlan {
        return copy(operations = operations.take(1))
    }

    private fun buildStepPrompt(goal: String, screenSummary: String?): String {
        return """
            Agent loop goal:
            $goal

            Plan only the next single small step as JSON. Use actionType agent_step unless confirmation, blocked, or done is required.
            If done, return done=true and no operations.
            If internet/browser/web is needed, return ask_confirmation with requiresInternetConfirmation=true unless already on a web page and the next visible step is safe.
            Allowed operation op values: open_app, open_url, read_screen, scroll_forward, scroll_backward, tap_node, set_text, wait, press_back, summarize_folder, classify_files, batch_group_files, create_folder, create_file, edit_text_file, append_text_file, copy_file, rename_file, read_file, summarize_file, list_files, search_files, click_button_by_text, set_field_by_label, set_field_by_hint, set_field_by_node_id.
            Opening an app or URL is never task completion. After open_app or open_url, continue with read_screen/wait/tap/scroll/set_text steps until the original goal is completed.
            Never generate work_on_webpage or vague unsupported operations.
            Never plan Settings, permission changes, app installs, deletion, passwords, PINs, 2FA, TAN, captcha, purchases, payments, or blind coordinate clicks.

            Current shortened screen snapshot:
            ${screenSummary ?: "No Accessibility screen snapshot available."}

            ${MemoryStore.buildMemoryContext(context)}
        """.trimIndent()
    }

    private fun providerName(providerType: String): String {
        return when (providerType) {
            AiPrefs.PROVIDER_DEEPSEEK_API -> "deepseek_api"
            AiPrefs.PROVIDER_OPENAI_COMPATIBLE_API -> "openai_compatible_api"
            AiPrefs.PROVIDER_CUSTOM_API -> "custom_api"
            AiPrefs.PROVIDER_LOCAL_DUMMY_GEMMA -> "local_dummy_gemma"
            else -> "unknown"
        }
    }

    companion object {
        private const val STEP_TIMEOUT_MS = 10_000L
    }
}
