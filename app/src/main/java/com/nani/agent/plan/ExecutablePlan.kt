package com.nani.agent.plan

import com.nani.agent.ai.AiAction
import com.nani.agent.ai.AiRiskLevel

data class ExecutablePlan(
    val actionType: AiAction,
    val explanation: String,
    val requiresConfirmation: Boolean,
    val requiresInternetConfirmation: Boolean,
    val done: Boolean = false,
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
    val reason: String? = null,
    val text: String? = null,
    val label: String? = null,
    val targetTextOrHint: String? = null,
    val targetViewIdResourceName: String? = null,
    val targetNodeId: Int? = null,
    val requiresFinalSubmitConfirmation: Boolean = false
)

enum class PlanOperationType(val wireName: String, val writes: Boolean, val usesInternet: Boolean = false) {
    ListFiles("list_files", writes = false),
    ReadFile("read_file", writes = false),
    SummarizeFile("summarize_file", writes = false),
    SearchFiles("search_files", writes = false),
    ClassifyFiles("classify_files", writes = false),
    BatchGroupFiles("batch_group_files", writes = false),
    CreateFolder("create_folder", writes = true),
    CreateFile("create_file", writes = true),
    EditTextFile("edit_text_file", writes = true),
    AppendTextFile("append_text_file", writes = true),
    CopyFile("copy_file", writes = true),
    RenameFile("rename_file", writes = true),
    SummarizeFolder("summarize_folder", writes = false),
    OpenUrl("open_url", writes = false, usesInternet = true),
    UseApp("use_app", writes = false),
    ReadScreen("read_screen", writes = false),
    TapNode("tap_node", writes = false),
    SetText("set_text", writes = true),
    AppendText("append_text", writes = true),
    Scroll("scroll", writes = false),
    ScrollForward("scroll_forward", writes = false),
    ScrollBackward("scroll_backward", writes = false),
    PressBack("press_back", writes = false),
    PressHome("press_home", writes = false),
    OpenApp("open_app", writes = false),
    WaitForScreen("wait_for_screen", writes = false),
    Wait("wait", writes = false),
    FindNode("find_node", writes = false),
    SelectOption("select_option", writes = true),
    FillForm("fill_form", writes = true, usesInternet = true),
    SetFieldByLabel("set_field_by_label", writes = true),
    SetFieldByHint("set_field_by_hint", writes = true),
    SetFieldByNodeId("set_field_by_node_id", writes = true),
    ClickButtonByText("click_button_by_text", writes = false, usesInternet = true),
    SubmitForm("submit_form", writes = true, usesInternet = true),
    Unsupported("unsupported", writes = false);

    companion object {
        fun fromWireName(value: String): PlanOperationType? {
            val normalized = value.lowercase()
            return when (normalized) {
                "open_url" -> OpenUrl
                "read_screen" -> ReadScreen
                "scroll", "scroll_forward" -> ScrollForward
                "scroll_backward" -> ScrollBackward
                "tap", "tap_node" -> TapNode
                "type_text", "set_text" -> SetText
                "wait" -> Wait
                "press_back" -> PressBack
                "summarize", "summarize_folder" -> SummarizeFolder
                "ask_confirmation" -> Unsupported
                "batch_group_files" -> BatchGroupFiles
                else -> entries.firstOrNull { it.wireName == normalized }
            }
        }
    }
}
