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
            nodes.take(12).forEach { appendLine(it.compactLine()) }
        }.trim()
}
