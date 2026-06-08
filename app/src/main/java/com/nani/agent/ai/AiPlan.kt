package com.nani.agent.ai

data class AiPlan(
    val actionType: AiAction,
    val explanation: String,
    val requiresConfirmation: Boolean,
    val requiresInternetConfirmation: Boolean = false,
    val riskLevel: AiRiskLevel,
    val proposedJson: String
)

enum class AiAction(val wireName: String) {
    ReadFiles("read_files"),
    WriteFiles("write_files"),
    EditFiles("edit_files"),
    OrganizeFiles("organize_files"),
    UseApp("use_app"),
    UseBrowser("use_browser"),
    AskConfirmation("ask_confirmation"),
    AskClarifyingQuestion("ask_clarifying_question"),
    Blocked("blocked");

    companion object {
        fun fromWireName(value: String): AiAction? {
            if (value == "list_files") return ReadFiles
            if (value == "summarize_folder") return ReadFiles
            if (value == "create_folders") return WriteFiles
            if (value == "suggest_copy_files") return OrganizeFiles
            if (value == "suggest_move_files") return OrganizeFiles
            return entries.firstOrNull { it.wireName == value }
        }
    }
}

enum class AiRiskLevel(val wireName: String) {
    Low("low"),
    Medium("medium"),
    High("high");

    companion object {
        fun fromWireName(value: String): AiRiskLevel? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}
