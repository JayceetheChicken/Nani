package com.nani.agent.policy

object NetworkPolicy {
    private val internetOperations = setOf(
        "open_url",
        "web_search",
        "use_browser",
        "send_network_request",
        "upload_file",
        "share_file",
        "send_message",
        "send_email",
        "work_on_webpage",
        "fill_form",
        "submit_form",
        "click_button_by_text"
    )

    fun operationRequiresInternetConfirmation(operation: String): Boolean {
        val normalized = operation.lowercase()
        return normalized in internetOperations || internetOperations.any { normalized.contains(it) }
    }
}
