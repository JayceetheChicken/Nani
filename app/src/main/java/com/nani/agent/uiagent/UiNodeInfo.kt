package com.nani.agent.uiagent

data class UiNodeInfo(
    val id: Int,
    val text: String?,
    val contentDescription: String?,
    val viewIdResourceName: String?,
    val className: String?,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val enabled: Boolean,
    val password: Boolean,
    val boundsInScreen: String
) {
    fun compactLine(): String {
        val label = text ?: contentDescription ?: viewIdResourceName ?: className ?: "node"
        val traits = buildList {
            if (clickable) add("click")
            if (editable) add("edit")
            if (scrollable) add("scroll")
            if (password) add("password")
        }.joinToString(",")
        return "#$id ${label.take(60)} ${if (traits.isBlank()) "" else "[$traits]"}".trim()
    }
}
