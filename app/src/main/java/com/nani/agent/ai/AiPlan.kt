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
    SuggestMoveFiles("suggest_move_files"),
    SummarizeFolder("summarize_folder"),
    AskClarifyingQuestion("ask_clarifying_question"),
    Blocked("blocked")
}

enum class AiRiskLevel(val wireName: String) {
    Low("low"),
    Medium("medium"),
    High("high")
}
