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
            requiresInternetConfirmation = plan.requiresInternetConfirmation,
            done = plan.done,
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
                            to = operation.optStringOrNull("to"),
                            content = operation.optStringOrNull("content"),
                            query = operation.optStringOrNull("query"),
                            url = operation.optStringOrNull("url"),
                            reason = operation.optStringOrNull("reason"),
                            text = operation.optStringOrNull("text"),
                            label = operation.optStringOrNull("label"),
                            targetTextOrHint = operation.optJSONObject("target")?.optStringOrNull("textOrHint")
                                ?: operation.optStringOrNull("hint"),
                            targetViewIdResourceName = operation.optJSONObject("target")?.optStringOrNull("viewIdResourceName")
                                ?: operation.optStringOrNull("viewIdResourceName"),
                            targetNodeId = operation.optJSONObject("target")?.optIntOrNull("nodeId")
                                ?: operation.optIntOrNull("nodeId"),
                            groupSize = operation.optIntOrNull("groupSize"),
                            targetFolderPrefix = operation.optStringOrNull("targetFolderPrefix"),
                            fileTypes = operation.optStringList("fileTypes"),
                            mode = operation.optStringOrNull("mode"),
                            sortBy = operation.optStringOrNull("sortBy"),
                            requiresFinalSubmitConfirmation = operation.optBoolean("requiresFinalSubmitConfirmation", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())

        if (parsedOperations.isNotEmpty()) return parsedOperations

        return when (plan.actionType) {
            AiAction.ReadFiles -> listOf(PlanOperation(PlanOperationType.ListFiles, rawOp = "list_files"))
            else -> emptyList()
        }
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        return if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotBlank() } else null
    }

    private fun JSONObject.optIntOrNull(name: String): Int? {
        return if (has(name) && !isNull(name)) optInt(name) else null
    }

    private fun JSONObject.optStringList(name: String): List<String> {
        val array = optJSONArray(name) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }
}
