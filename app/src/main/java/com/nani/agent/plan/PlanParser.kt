package com.nani.agent.plan

import com.nani.agent.ai.AiAction
import com.nani.agent.ai.AiPlan
import org.json.JSONArray
import org.json.JSONObject

object PlanParser {
    fun parse(plan: AiPlan): ExecutablePlan {
        val operations = parseOperations(plan)
        return ExecutablePlan(
            actionType = plan.actionType,
            explanation = plan.explanation,
            requiresConfirmation = plan.requiresConfirmation,
            riskLevel = plan.riskLevel,
            operations = operations,
            proposedJson = plan.proposedJson
        )
    }

    private fun parseOperations(plan: AiPlan): List<PlanOperation> {
        val parsedOperations = runCatching {
            val json = JSONObject(plan.proposedJson)
            val operations = json.optJSONArray("operations") ?: JSONArray()
            buildList {
                for (index in 0 until operations.length()) {
                    val operation = operations.optJSONObject(index) ?: continue
                    val rawOp = operation.optString("op")
                    val op = PlanOperationType.fromWireName(rawOp) ?: PlanOperationType.Unsupported
                    add(
                        PlanOperation(
                            op = op,
                            rawOp = rawOp,
                            path = operation.optStringOrNull("path"),
                            from = operation.optStringOrNull("from"),
                            to = operation.optStringOrNull("to")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())

        if (parsedOperations.isNotEmpty()) return parsedOperations

        return when (plan.actionType) {
            AiAction.ListFiles -> listOf(PlanOperation(PlanOperationType.ListFiles, rawOp = "list_files"))
            AiAction.SummarizeFolder -> listOf(PlanOperation(PlanOperationType.SummarizeFolder, rawOp = "summarize_folder"))
            else -> emptyList()
        }
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        return if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotBlank() } else null
    }
}
