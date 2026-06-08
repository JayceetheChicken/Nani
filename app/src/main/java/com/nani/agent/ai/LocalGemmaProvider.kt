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
                requiresInternetConfirmation = false,
                riskLevel = AiRiskLevel.High,
                operations = emptyList()
            )
            "ordner erstellen" in normalized || "erstelle ordner" in normalized -> plan(
                actionType = AiAction.WriteFiles,
                explanation = "Nani can create folders after preview and confirmation.",
                requiresConfirmation = true,
                requiresInternetConfirmation = false,
                riskLevel = AiRiskLevel.Medium,
                operations = listOf(
                    JSONObject()
                        .put("op", "create_folder")
                        .put("path", "Schule")
                )
            )
            "sortiere" in normalized || "verschiebe" in normalized -> plan(
                actionType = AiAction.OrganizeFiles,
                explanation = "Local Dummy can suggest target folders for sorting. Originals stay unchanged.",
                requiresConfirmation = true,
                requiresInternetConfirmation = false,
                riskLevel = AiRiskLevel.Medium,
                operations = listOf(
                    JSONObject()
                        .put("op", "create_folder")
                        .put("path", "Schule")
                )
            )
            "erstelle datei" in normalized || "datei erstellen" in normalized -> plan(
                actionType = AiAction.WriteFiles,
                explanation = "Nani can create a text file after preview and confirmation.",
                requiresConfirmation = true,
                requiresInternetConfirmation = false,
                riskLevel = AiRiskLevel.Medium,
                operations = listOf(
                    JSONObject()
                        .put("op", "create_file")
                        .put("path", "Notizen/nani-notiz.txt")
                        .put("content", "Neue Nani-Notiz")
                )
            )
            "umbenennen" in normalized -> plan(
                actionType = AiAction.OrganizeFiles,
                explanation = "Nani can rename files after preview and confirmation.",
                requiresConfirmation = true,
                requiresInternetConfirmation = false,
                riskLevel = AiRiskLevel.Medium,
                operations = listOf(
                    JSONObject()
                        .put("op", "rename_file")
                        .put("from", "alte-datei.txt")
                        .put("to", "neue-datei.txt")
                )
            )
            "zeige" in normalized || "liste" in normalized -> plan(
                actionType = AiAction.ReadFiles,
                explanation = "Nani can list files inside the selected work folder.",
                requiresConfirmation = false,
                requiresInternetConfirmation = false,
                riskLevel = AiRiskLevel.Low,
                operations = listOf(JSONObject().put("op", "list_files"))
            )
            "lies" in normalized || "lese" in normalized -> plan(
                actionType = AiAction.ReadFiles,
                explanation = "Nani can read a text file inside the selected work folder.",
                requiresConfirmation = false,
                requiresInternetConfirmation = false,
                riskLevel = AiRiskLevel.Low,
                operations = listOf(
                    JSONObject()
                        .put("op", "read_file")
                        .put("path", "Notizen/nani-notiz.txt")
                )
            )
            "zusammenfassen" in normalized || "summary" in normalized -> plan(
                actionType = AiAction.ReadFiles,
                explanation = "Nani can summarize the selected work folder.",
                requiresConfirmation = false,
                requiresInternetConfirmation = false,
                riskLevel = AiRiskLevel.Low,
                operations = listOf(JSONObject().put("op", "summarize_folder"))
            )
            else -> plan(
                actionType = AiAction.AskClarifyingQuestion,
                explanation = "Nani needs a clearer command before proposing a safe JSON plan.",
                requiresConfirmation = false,
                requiresInternetConfirmation = false,
                riskLevel = AiRiskLevel.Low,
                operations = emptyList()
            )
        }
    }

    private fun plan(
        actionType: AiAction,
        explanation: String,
        requiresConfirmation: Boolean,
        requiresInternetConfirmation: Boolean,
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
            requiresInternetConfirmation = requiresInternetConfirmation,
            riskLevel = riskLevel,
            proposedJson = proposedJson
        )
    }
}
