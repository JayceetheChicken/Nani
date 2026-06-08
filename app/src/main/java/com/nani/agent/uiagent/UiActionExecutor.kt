package com.nani.agent.uiagent

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import com.nani.agent.LogStore
import com.nani.agent.saf.ActionExecutionResult

class UiActionExecutor(
    private val service: AccessibilityService
) {
    fun execute(actions: List<UiAction>, internetConfirmed: Boolean, finalSubmitConfirmed: Boolean): ActionExecutionResult {
        val snapshot = AccessibilityTreeReader(service).readCurrentScreen()
        val validation = UiActionValidator.validate(snapshot, actions, internetConfirmed, finalSubmitConfirmed)
        if (!validation.canExecute) {
            return ActionExecutionResult(emptyList(), validation.errors, validation.warnings)
        }

        val reader = AccessibilityTreeReader(service)
        val successes = mutableListOf<String>()
        val failures = mutableListOf<String>()

        actions.forEach { action ->
            val result = runCatching {
                when (action.op) {
                    UiActionType.ReadScreen -> snapshot?.summary ?: "No screen snapshot available."
                    UiActionType.TapNode,
                    UiActionType.ClickButtonByText,
                    UiActionType.SelectOption -> clickNode(reader, snapshot, action)
                    UiActionType.SetText,
                    UiActionType.AppendText,
                    UiActionType.SetFieldByLabel,
                    UiActionType.SetFieldByHint,
                    UiActionType.SetFieldByNodeId -> setText(reader, snapshot, action)
                    UiActionType.Scroll -> scrollNode(reader, snapshot, action)
                    UiActionType.PressBack -> {
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                        "Pressed Back"
                    }
                    UiActionType.PressHome -> {
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                        "Pressed Home"
                    }
                    UiActionType.WaitForScreen -> "Wait requested. Long-running waits are not implemented in this MVP."
                    UiActionType.FindNode -> reader.findNode(snapshot, action)?.let { "Found node for ${action.rawOp}" }
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
                LogStore.appendUiAction(service, action.rawOp, "success")
            } else {
                failures += "${action.rawOp}: ${result.exceptionOrNull()?.message ?: "failed"}"
                LogStore.appendUiAction(service, action.rawOp, "failed")
            }
        }

        return ActionExecutionResult(successes, failures, validation.warnings)
    }

    private fun openUrl(action: UiAction): String {
        val url = action.url ?: error("URL missing.")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        service.startActivity(intent)
        return "Opened URL after internet confirmation"
    }

    private fun openApp(action: UiAction): String {
        val packageName = action.target?.viewIdResourceName ?: action.text ?: error("Package name missing.")
        val intent = service.packageManager.getLaunchIntentForPackage(packageName)
            ?: error("No launch intent for package: $packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        service.startActivity(intent)
        return "Opened allowed app"
    }

    private fun clickNode(reader: AccessibilityTreeReader, snapshot: ScreenSnapshot?, action: UiAction): String {
        val node = reader.findNode(snapshot, action) ?: error("Target node not found.")
        val clickableNode = generateSequence(node) { it.parent }.firstOrNull { it.isClickable && it.isEnabled }
            ?: error("No clickable parent found.")
        clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return "Clicked visible node"
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

    private fun scrollNode(reader: AccessibilityTreeReader, snapshot: ScreenSnapshot?, action: UiAction): String {
        val node = reader.findNode(snapshot, action)
            ?: service.rootInActiveWindow
            ?: error("No scroll target available.")
        val scrollableNode = generateSequence(node) { it.parent }.firstOrNull { it.isScrollable && it.isEnabled }
            ?: error("No scrollable node found.")
        scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        return "Scrolled visible container"
    }
}
