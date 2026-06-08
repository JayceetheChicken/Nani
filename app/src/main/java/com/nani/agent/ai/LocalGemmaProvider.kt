package com.nani.agent.ai

import org.json.JSONArray
import org.json.JSONObject

class LocalGemmaProvider : AiProvider {
    override suspend fun generatePlan(userCommand: String): AiPlan {
        // TODO: Replace deterministic dummy planner with real on-device Gemma 4 E2B inference.
        // This is not a real local AI model yet.
        val normalized = userCommand.lowercase()
        return when {
            "lösche" in normalized || "loesche" in normalized -> plan(
                actionType = AiAction.Blocked,
                explanation = "Deleting files is disabled.",
                requiresConfirmation = false,
                riskLevel = AiRiskLevel.High,
                operations = emptyList()
            )
            "ordner erstellen" in normalized || "erstelle ordner" in normalized -> plan(
                actionType = AiAction.CreateFolders,
                explanation = "Nani can create folders after preview and confirmation.",
                requiresConfirmation = true,
                riskLevel = AiRiskLevel.Medium,
                operations = listOf(
                    JSONObject()
                        .put("op", "create_folder")
                        .put("path", "Schule")
                )
            )
            "sortiere" in normalized || "verschiebe" in normalized -> plan(
                actionType = AiAction.SuggestCopyFiles,
                explanation = "Nani can suggest a safe copy plan. Originals stay unchanged.",
                requiresConfirmation = true,
                riskLevel = AiRiskLevel.Medium,
                operations = listOf(
                    JSONObject()
                        .put("op", "create_folder")
                        .put("path", "Schule"),
                    JSONObject()
                        .put("op", "copy_file")
                        .put("from", "Downloads/beispiel.pdf")
                        .put("to", "Schule/beispiel.pdf")
                )
            )
            "zeige" in normalized || "liste" in normalized -> plan(
                actionType = AiAction.ListFiles,
                explanation = "Nani can list files inside the selected work folder.",
                requiresConfirmation = false,
                riskLevel = AiRiskLevel.Low,
                operations = listOf(JSONObject().put("op", "list_files"))
            )
            "zusammenfassen" in normalized || "summary" in normalized -> plan(
                actionType = AiAction.SummarizeFolder,
                explanation = "Nani can summarize the selected work folder.",
                requiresConfirmation = false,
                riskLevel = AiRiskLevel.Low,
                operations = listOf(JSONObject().put("op", "summarize_folder"))
            )
            else -> plan(
                actionType = AiAction.AskClarifyingQuestion,
                explanation = "Nani needs a clearer command before proposing a safe JSON plan.",
                requiresConfirmation = false,
                riskLevel = AiRiskLevel.Low,
                operations = emptyList()
            )
        }
    }

    private fun plan(
        actionType: AiAction,
        explanation: String,
        requiresConfirmation: Boolean,
        riskLevel: AiRiskLevel,
        operations: List<JSONObject>
    ): AiPlan {
        val proposedJson = JSONObject()
            .put("root", "selected_saf_tree")
            .put("operations", JSONArray(operations))
            .toString(2)

        return AiPlan(
            actionType = actionType,
            explanation = explanation,
            requiresConfirmation = requiresConfirmation,
            riskLevel = riskLevel,
            proposedJson = proposedJson
        )
    }
}
