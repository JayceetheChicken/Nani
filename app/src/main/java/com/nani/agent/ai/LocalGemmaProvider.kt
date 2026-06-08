package com.nani.agent.ai

class LocalGemmaProvider : AiProvider {
    override suspend fun generatePlan(userCommand: String): AiPlan {
        // TODO: Replace deterministic planner with real on-device Gemma 4 E2B inference.
        // TODO: Keep this provider name bound to the planned Gemma 4 E2B local runtime.
        val normalized = userCommand.lowercase()
        return when {
            "lösche" in normalized || "loesche" in normalized -> plan(
                actionType = AiAction.Blocked,
                explanation = "Deleting files is disabled.",
                requiresConfirmation = false,
                riskLevel = AiRiskLevel.High
            )
            "sortiere" in normalized || "verschiebe" in normalized -> plan(
                actionType = AiAction.SuggestMoveFiles,
                explanation = "Nani can suggest a move plan, but no files will be changed without confirmation.",
                requiresConfirmation = true,
                riskLevel = AiRiskLevel.Medium
            )
            "zeige" in normalized || "liste" in normalized -> plan(
                actionType = AiAction.ListFiles,
                explanation = "Nani can suggest a listing plan, but file access is not implemented yet.",
                requiresConfirmation = false,
                riskLevel = AiRiskLevel.Low
            )
            else -> plan(
                actionType = AiAction.AskClarifyingQuestion,
                explanation = "Nani needs a clearer command before proposing a safe JSON plan.",
                requiresConfirmation = false,
                riskLevel = AiRiskLevel.Low
            )
        }
    }

    private fun plan(
        actionType: AiAction,
        explanation: String,
        requiresConfirmation: Boolean,
        riskLevel: AiRiskLevel
    ): AiPlan {
        val proposedJson = """
            {
              "actionType": "${actionType.wireName}",
              "requiresConfirmation": $requiresConfirmation,
              "riskLevel": "${riskLevel.wireName}",
              "executesActions": false,
              "accessesFiles": false
            }
        """.trimIndent()

        return AiPlan(
            actionType = actionType,
            explanation = explanation,
            requiresConfirmation = requiresConfirmation,
            riskLevel = riskLevel,
            proposedJson = proposedJson
        )
    }
}
