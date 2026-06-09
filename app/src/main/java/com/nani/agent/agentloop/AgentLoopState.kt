package com.nani.agent.agentloop

import com.nani.agent.ai.AiPlan
import com.nani.agent.saf.ActionExecutionResult

data class AgentLoopState(
    val goal: String = "",
    val running: Boolean = false,
    val paused: Boolean = false,
    val stopped: Boolean = false,
    val stepCount: Int = 0,
    val maxSteps: Int = 20,
    val currentStep: AgentStep? = null,
    val lastAction: String? = null,
    val nextPlan: AiPlan? = null,
    val lastResult: ActionExecutionResult? = null,
    val stopReason: AgentStopReason = AgentStopReason.None,
    val message: String? = null
)
