package com.nani.agent.ai

class ApiAiProvider(
    private val settings: AiSettings
) : AiProvider {
    override suspend fun generatePlan(userCommand: String): AiPlan {
        val actionType = AiAction.AskClarifyingQuestion
        val riskLevel = AiRiskLevel.Low
        val proposedJson = """
            {
              "actionType": "${actionType.wireName}",
              "requiresConfirmation": false,
              "riskLevel": "${riskLevel.wireName}",
              "provider": "api",
              "baseUrlConfigured": ${settings.apiBaseUrl.isNotBlank()},
              "modelName": "${escapeJson(settings.modelName)}",
              "networkCallImplemented": false
            }
        """.trimIndent()

        return AiPlan(
            actionType = actionType,
            explanation = "API provider configured but network call not implemented yet.",
            requiresConfirmation = false,
            riskLevel = riskLevel,
            proposedJson = proposedJson
        )
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
