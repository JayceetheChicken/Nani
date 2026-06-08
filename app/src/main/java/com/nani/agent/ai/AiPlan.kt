package com.nani.agent.ai

data class AiPlan(
    val actionType: AiAction,
    val explanation: String,
    val requiresConfirmation: Boolean,
    val riskLevel: AiRiskLevel,
    val proposedJson: String
)

enum class AiAction(val wireName: String) {
    ListFiles("list_files"),
    SuggestCopyFiles("suggest_copy_files"),
    CreateFolders("create_folders"),
    SummarizeFolder("summarize_folder"),
    AskClarifyingQuestion("ask_clarifying_question"),
    Blocked("blocked");

    companion object {
        fun fromWireName(value: String): AiAction? {
            if (value == "suggest_move_files") return SuggestCopyFiles
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
