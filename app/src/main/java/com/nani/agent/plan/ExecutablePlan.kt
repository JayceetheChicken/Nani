package com.nani.agent.plan

import com.nani.agent.ai.AiAction
import com.nani.agent.ai.AiRiskLevel

data class ExecutablePlan(
    val actionType: AiAction,
    val explanation: String,
    val requiresConfirmation: Boolean,
    val riskLevel: AiRiskLevel,
    val operations: List<PlanOperation>,
    val proposedJson: String
)

data class PlanOperation(
    val op: PlanOperationType,
    val rawOp: String,
    val path: String? = null,
    val from: String? = null,
    val to: String? = null
)

enum class PlanOperationType(val wireName: String, val writes: Boolean) {
    ListFiles("list_files", writes = false),
    CreateFolder("create_folder", writes = true),
    CopyFile("copy_file", writes = true),
    SummarizeFolder("summarize_folder", writes = false),
    Unsupported("unsupported", writes = false);

    companion object {
        fun fromWireName(value: String): PlanOperationType? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}
