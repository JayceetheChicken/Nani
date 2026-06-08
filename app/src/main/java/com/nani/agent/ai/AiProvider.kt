package com.nani.agent.ai

interface AiProvider {
    suspend fun generatePlan(userCommand: String): AiPlan
}
