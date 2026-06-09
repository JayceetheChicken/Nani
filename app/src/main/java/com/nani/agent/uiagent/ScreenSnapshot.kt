package com.nani.agent.uiagent

data class ScreenSnapshot(
    val foregroundPackage: String,
    val appLabel: String?,
    val windowTitle: String?,
    val nodes: List<UiNodeInfo>,
    val capturedAtMillis: Long = System.currentTimeMillis()
) {
    val summary: String
        get() = buildString {
            appendLine("App: ${appLabel ?: foregroundPackage.ifBlank { "unknown" }}")
            if (!windowTitle.isNullOrBlank()) appendLine("Window: $windowTitle")
            appendLine("Visible nodes: ${nodes.size}")
            usefulNodes().take(30).forEach { appendLine(it.compactLine()) }
        }.trim()

    private fun usefulNodes(): List<UiNodeInfo> {
        return nodes.sortedWith(
            compareBy<UiNodeInfo> { nodePriority(it) }
                .thenBy { it.id }
        )
    }

    private fun nodePriority(node: UiNodeInfo): Int {
        return when {
            node.editable -> 0
            node.clickable -> 1
            node.scrollable -> 2
            !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank() -> 3
            else -> 4
        }
    }
}
