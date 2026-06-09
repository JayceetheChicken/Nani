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
                val snapshot = UiAgentController().readScreen()
                val settings = AiPrefs.load(context)
                val provider = AiProviderFactory.create(settings)
                val plan = provider.generatePlan(buildStepPrompt(goal, snapshot?.summary))
                val executable = PlanParser.parse(plan).singleStep()
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
                    done = plan.done || executable.done
                )

                when {
                    plan.done || executable.done -> previous.copy(
                        goal = goal,
                        running = false,
                        paused = false,
                        stopped = true,
                        stepCount = previous.stepCount + 1,
                        currentStep = step,
                        nextPlan = plan,
                        stopReason = AgentStopReason.GoalDone,
                        message = plan.explanation
                    )
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
                    )
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
                    )
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
                    )
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
                    )
                    else -> {
                        val result = AgentExecutionController(context).execute(
                            plan = executable,
                            internetConfirmed = internetConfirmed,
                            finalSubmitConfirmed = finalSubmitConfirmed
                        )
                        UiAgentController().readScreen()
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
                            message = result.failures.firstOrNull() ?: result.successes.firstOrNull() ?: plan.explanation
                        )
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
            )
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
