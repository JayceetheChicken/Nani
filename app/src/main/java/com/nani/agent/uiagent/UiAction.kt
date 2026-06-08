package com.nani.agent.uiagent

data class UiAction(
    val op: UiActionType,
    val rawOp: String,
    val target: UiActionTarget? = null,
    val text: String? = null,
    val url: String? = null,
    val reason: String? = null,
    val requiresFinalSubmitConfirmation: Boolean = false
)

data class UiActionTarget(
    val nodeId: Int? = null,
    val textOrHint: String? = null,
    val label: String? = null,
    val viewIdResourceName: String? = null
)

enum class UiActionType(val wireName: String, val usesInternet: Boolean = false, val finalSubmit: Boolean = false) {
    ReadScreen("read_screen"),
    TapNode("tap_node"),
    SetText("set_text"),
    AppendText("append_text"),
    Scroll("scroll"),
    PressBack("press_back"),
    PressHome("press_home"),
    OpenApp("open_app"),
    WaitForScreen("wait_for_screen"),
    FindNode("find_node"),
    SelectOption("select_option"),
    OpenUrl("open_url", usesInternet = true),
    FillForm("fill_form"),
    SetFieldByLabel("set_field_by_label"),
    SetFieldByHint("set_field_by_hint"),
    SetFieldByNodeId("set_field_by_node_id"),
    ClickButtonByText("click_button_by_text"),
    SubmitForm("submit_form", usesInternet = true, finalSubmit = true),
    Unsupported("unsupported");

    companion object {
        fun fromWireName(value: String): UiActionType? = entries.firstOrNull { it.wireName == value }
    }
}
