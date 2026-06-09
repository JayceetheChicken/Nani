package com.nani.agent.uiagent

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

class AccessibilityTreeReader(
    private val service: AccessibilityService
) {
    fun readCurrentScreen(maxNodes: Int = 80): ScreenSnapshot? {
        val root = service.rootInActiveWindow ?: return null
        val packageName = root.packageName?.toString().orEmpty()
        val appLabel = packageName.takeIf { it.isNotBlank() }?.let { labelForPackage(service, it) }
        val nodes = mutableListOf<UiNodeInfo>()
        collect(root, nodes, maxNodes)
        return ScreenSnapshot(
            foregroundPackage = packageName,
            appLabel = appLabel,
            windowTitle = root.window?.title?.toString(),
            nodes = nodes
        )
    }

    fun findNode(snapshot: ScreenSnapshot?, action: UiAction): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val target = action.target
        val wantedText = action.text ?: target?.textOrHint
        val wantedId = target?.nodeId
        val wantedViewId = target?.viewIdResourceName
        var currentIndex = 0
        return findDepthFirst(root) { node ->
            val nodeIndex = currentIndex++
            (wantedId != null && snapshot?.nodes?.getOrNull(wantedId)?.id == nodeIndex) ||
                (!wantedViewId.isNullOrBlank() && node.viewIdResourceName == wantedViewId) ||
                (!wantedText.isNullOrBlank() && node.matchesText(wantedText))
        }
    }

    private fun collect(node: AccessibilityNodeInfo, nodes: MutableList<UiNodeInfo>, maxNodes: Int) {
        if (nodes.size >= maxNodes) return
        val rect = Rect()
        node.getBoundsInScreen(rect)
        nodes += UiNodeInfo(
            id = nodes.size,
            text = sanitizeNodeText(node.text?.toString(), node.isPassword),
            contentDescription = sanitizeNodeText(node.contentDescription?.toString(), node.isPassword),
            viewIdResourceName = node.viewIdResourceName?.take(80),
            className = node.className?.toString()?.take(80),
            clickable = node.isClickable,
            editable = node.isEditable,
            scrollable = node.isScrollable,
            enabled = node.isEnabled,
            password = node.isPassword,
            boundsInScreen = "${rect.left},${rect.top},${rect.right},${rect.bottom}"
        )
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { collect(it, nodes, maxNodes) }
            if (nodes.size >= maxNodes) return
        }
    }

    private fun findDepthFirst(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            findDepthFirst(child, predicate)?.let { return it }
        }
        return null
    }

    private fun labelForPackage(context: Context, packageName: String): String? {
        return runCatching {
            val packageManager = context.packageManager
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrNull()
    }
}

private fun AccessibilityNodeInfo.matchesText(value: String): Boolean {
    val normalized = value.lowercase()
    return text?.toString()?.lowercase()?.contains(normalized) == true ||
        hintText?.toString()?.lowercase()?.contains(normalized) == true ||
        contentDescription?.toString()?.lowercase()?.contains(normalized) == true
}

private fun sanitizeNodeText(value: String?, password: Boolean): String? {
    if (value.isNullOrBlank()) return null
    if (password) return "••••"
    return value.replace(Regex("\\s+"), " ").take(120)
}
