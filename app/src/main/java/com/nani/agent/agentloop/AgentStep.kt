package com.nani.agent.agentloop

import com.nani.agent.plan.PlanOperation

data class AgentStep(
    val number: Int,
    val explanation: String,
    val operations: List<PlanOperation>,
    val done: Boolean
)
