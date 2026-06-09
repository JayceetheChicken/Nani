package com.nani.agent.uiagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.nani.agent.LogStore
import com.nani.agent.saf.ActionExecutionResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class UiActionExecutor(
    private val service: AccessibilityService
) {
    suspend fun execute(actions: List<UiAction>, internetConfirmed: Boolean, finalSubmitConfirmed: Boolean): ActionExecutionResult {
        var currentSnapshot = AccessibilityTreeReader(service).readCurrentScreen()?.also(ScreenStateStore::update)
        val validation = UiActionValidator.validate(currentSnapshot, actions, internetConfirmed, finalSubmitConfirmed)
        if (!validation.canExecute) {
            return ActionExecutionResult(emptyList(), validation.errors, validation.warnings)
        }

        val reader = AccessibilityTreeReader(service)
        val successes = mutableListOf<String>()
        val failures = mutableListOf<String>()

        actions.forEach { action ->
            val result = runCatching {
                when (action.op) {
                    UiActionType.ReadScreen -> currentSnapshot?.summary ?: "No screen snapshot available."
                    UiActionType.TapNode,
                    UiActionType.ClickButtonByText,
                    UiActionType.SelectOption -> clickNode(reader, currentSnapshot, action)
                    UiActionType.SetText,
                    UiActionType.AppendText,
                    UiActionType.SetFieldByLabel,
                    UiActionType.SetFieldByHint,
                    UiActionType.SetFieldByNodeId -> setText(reader, currentSnapshot, action)
                    UiActionType.Scroll,
                    UiActionType.ScrollForward -> scrollNode(reader, currentSnapshot, action, forward = true)
                    UiActionType.ScrollBackward -> scrollNode(reader, currentSnapshot, action, forward = false)
                    UiActionType.PressBack -> {
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                        "Pressed Back"
                    }
                    UiActionType.PressHome -> {
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                        "Pressed Home"
                    }
                    UiActionType.WaitForScreen,
                    UiActionType.Wait -> {
                        delay(500)
                        "Waited briefly"
                    }
                    UiActionType.FindNode -> reader.findNode(currentSnapshot, action)?.let { "Found node for ${action.rawOp}" }
                        ?: "Node not found."
                    UiActionType.OpenUrl -> openUrl(action)
                    UiActionType.OpenApp -> openApp(action)
                    UiActionType.FillForm,
                    UiActionType.SubmitForm,
                    UiActionType.Unsupported -> error("Operation is not executable here: ${action.rawOp}")
                }
            }
            if (result.isSuccess) {
                successes += result.getOrThrow()
                currentSnapshot = if (action.op == UiActionType.OpenUrl || action.op == UiActionType.OpenApp) {
                    ScreenStateStore.latest() ?: observeAfterAction(delayMs = 0)
                } else {
                    observeAfterAction(delayMs = 500) ?: currentSnapshot
                }
                LogStore.appendUiAction(service, action.rawOp, "success")
            } else {
                failures += "${action.rawOp}: ${result.exceptionOrNull()?.message ?: "failed"}"
                LogStore.appendUiAction(service, action.rawOp, "failed")
            }
        }

        return ActionExecutionResult(successes, failures, validation.warnings)
    }

    private suspend fun openUrl(action: UiAction): String {
        val url = action.url ?: error("URL missing.")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        service.startActivity(intent)
        observeAfterAction(delayMs = 1_500)
        return "Opened URL; observation required"
    }

    private suspend fun openApp(action: UiAction): String {
        val appQuery = listOfNotNull(
            action.text,
            action.target?.textOrHint,
            action.target?.label,
            action.target?.viewIdResourceName
        ).firstOrNull { it.isNotBlank() } ?: error("App name or package is missing.")
        val packageName = resolveLaunchableAppPackage(appQuery)
            ?: error("No launchable app found for: $appQuery")
        val intent = service.packageManager.getLaunchIntentForPackage(packageName)
            ?: error("No launch intent for package: $packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        service.startActivity(intent)
        observeAfterAction(delayMs = 1_500)
        return "Opened app $packageName; observation required"
    }

    private suspend fun observeAfterAction(delayMs: Long): ScreenSnapshot? {
        if (delayMs > 0) delay(delayMs)
        return UiAgentController().waitForUiSettledAndReadScreen(timeoutMs = 2_500)
            ?: UiAgentController().readScreen()
    }

    private fun resolveLaunchableAppPackage(query: String): String? {
        val packageManager = service.packageManager
        if (packageManager.getLaunchIntentForPackage(query) != null) return query

        aliasPackagesFor(query).firstOrNull {
            packageManager.getLaunchIntentForPackage(it) != null
        }?.let { return it }

        val normalizedQuery = normalizeAppName(query)
        val launchableApps = queryLaunchableApps()
        return launchableApps.firstOrNull { (_, label) ->
            normalizeAppName(label) == normalizedQuery
        }?.first ?: launchableApps.firstOrNull { (packageName, label) ->
            normalizeAppName(packageName) == normalizedQuery ||
                normalizeAppName(label).contains(normalizedQuery)
        }?.first
    }

    private fun aliasPackagesFor(query: String): List<String> {
        return when (normalizeAppName(query)) {
            "drive",
            "google drive" -> listOf("com.google.android.apps.docs")
            "chrome",
            "google chrome" -> listOf("com.android.chrome")
            "samsung internet" -> listOf("com.sec.android.app.sbrowser")
            "files",
            "dateien" -> listOf(
                "com.sec.android.app.myfiles",
                "com.google.android.apps.nbu.files",
                "com.google.android.documentsui",
                "com.android.documentsui"
            )
            else -> emptyList()
        }
    }

    private fun queryLaunchableApps(): List<Pair<String, String>> {
        val packageManager = service.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, 0)
        }
        return resolved.mapNotNull { info ->
            val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
            val label = info.loadLabel(packageManager).toString()
            packageName to label
        }
    }

    private fun normalizeAppName(value: String): String {
        return value.trim().lowercase().replace(Regex("\\s+"), " ")
    }

    private suspend fun clickNode(reader: AccessibilityTreeReader, snapshot: ScreenSnapshot?, action: UiAction): String {
        val node = reader.findNode(snapshot, action) ?: error("Target node not found.")
        val clickableNode = generateSequence(node) { it.parent }.firstOrNull { it.isClickable && it.isEnabled }
        if (clickableNode != null) {
            clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return "Clicked visible node"
        }
        return tapNodeCenterWithGesture(node)
    }

    private suspend fun tapNodeCenterWithGesture(node: AccessibilityNodeInfo): String {
        if (!node.isVisibleToUser || !node.isEnabled) error("Target node is not visibly tappable.")
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0) error("Target node has no visible bounds.")
        val x = rect.centerX().toFloat()
        val y = rect.centerY().toFloat()
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        val dispatched = suspendCancellableCoroutine { continuation ->
            val started = service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                },
                null
            )
            if (!started && continuation.isActive) continuation.resume(false)
        }
        if (!dispatched) error("Gesture tap was cancelled.")
        return "Tapped center of visible Accessibility node"
    }

    private fun setText(reader: AccessibilityTreeReader, snapshot: ScreenSnapshot?, action: UiAction): String {
        val node = reader.findNode(snapshot, action) ?: error("Target field not found.")
        if (!node.isEditable || node.isPassword) error("Target is not a safe editable field.")
        val text = action.text.orEmpty()
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        return "Set text in visible field"
    }

    private fun scrollNode(reader: AccessibilityTreeReader, snapshot: ScreenSnapshot?, action: UiAction, forward: Boolean): String {
        val node = reader.findNode(snapshot, action)
            ?: service.rootInActiveWindow
            ?: error("No scroll target available.")
        val scrollableNode = generateSequence(node) { it.parent }.firstOrNull { it.isScrollable && it.isEnabled }
            ?: error("No scrollable node found.")
        scrollableNode.performAction(
            if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        )
        return if (forward) "Scrolled visible container forward" else "Scrolled visible container backward"
    }
}
