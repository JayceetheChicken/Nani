package com.nani.agent.plan

import com.nani.agent.ai.AiAction
import com.nani.agent.ai.AiRiskLevel

data class ExecutablePlan(
    val actionType: AiAction,
    val explanation: String,
    val requiresConfirmation: Boolean,
    val requiresInternetConfirmation: Boolean,
    val riskLevel: AiRiskLevel,
    val operations: List<PlanOperation>,
    val proposedJson: String
)

data class PlanOperation(
    val op: PlanOperationType,
    val rawOp: String,
    val path: String? = null,
    val from: String? = null,
    val to: String? = null,
    val content: String? = null,
    val query: String? = null,
    val url: String? = null,
    val reason: String? = null
)

enum class PlanOperationType(val wireName: String, val writes: Boolean, val usesInternet: Boolean = false) {
    ListFiles("list_files", writes = false),
    ReadFile("read_file", writes = false),
    SummarizeFile("summarize_file", writes = false),
    SearchFiles("search_files", writes = false),
    ClassifyFiles("classify_files", writes = false),
    CreateFolder("create_folder", writes = true),
    CreateFile("create_file", writes = true),
    EditTextFile("edit_text_file", writes = true),
    AppendTextFile("append_text_file", writes = true),
    CopyFile("copy_file", writes = true),
    RenameFile("rename_file", writes = true),
    SummarizeFolder("summarize_folder", writes = false),
    OpenUrl("open_url", writes = false, usesInternet = true),
    UseApp("use_app", writes = false),
    Unsupported("unsupported", writes = false);

    companion object {
        fun fromWireName(value: String): PlanOperationType? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}
