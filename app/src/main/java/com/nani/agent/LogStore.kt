package com.nani.agent

import android.content.Context
import java.io.File
import java.time.Instant

object LogStore {
    private const val LOG_FILE_NAME = "nani_foreground_log.txt"
    private const val MAX_BYTES = 64 * 1024

    fun appendForegroundPackage(context: Context, packageName: String) {
        appendLine(context, "${Instant.now()} foreground=$packageName")
    }

    fun appendSecurityAction(context: Context, packageName: String, action: String) {
        appendLine(context, "${Instant.now()} security_action=$action package=$packageName")
    }

    fun appendControlChange(context: Context, control: String, enabled: Boolean) {
        appendLine(context, "${Instant.now()} control_change=$control enabled=$enabled")
    }

    fun appendAiTest(context: Context, provider: String, actionType: String, riskLevel: String) {
        appendLine(context, "${Instant.now()} ai_test provider=$provider actionType=$actionType riskLevel=$riskLevel")
    }

    fun appendAiPlanGenerated(context: Context, provider: String, actionType: String, riskLevel: String) {
        appendLine(context, "${Instant.now()} ai_plan_generated provider=$provider actionType=$actionType riskLevel=$riskLevel")
    }

    fun appendSafAction(context: Context, operation: String, result: String) {
        appendLine(context, "${Instant.now()} saf_action operation=$operation result=$result")
    }

    fun appendUiAction(context: Context, operation: String, result: String) {
        appendLine(context, "${Instant.now()} ui_action operation=$operation result=$result")
    }

    fun appendAgentLoopStep(
        context: Context,
        stepNumber: Int,
        foregroundBefore: String,
        action: String,
        result: String,
        foregroundAfter: String,
        stopReason: String
    ) {
        appendLine(
            context,
            "${Instant.now()} agent_loop_step step=$stepNumber foreground_before=$foregroundBefore action=$action result=$result foreground_after=$foregroundAfter stop_reason=$stopReason"
        )
    }

    fun appendScreenSummary(context: Context, foregroundPackage: String, nodeCount: Int, summary: String) {
        appendLine(
            context,
            "${Instant.now()} screen_summary foreground=$foregroundPackage nodes=$nodeCount summary=${summary.lineSequence().firstOrNull().orEmpty().take(120)}"
        )
    }

    fun readRecent(context: Context, limit: Int = 80): List<String> {
        val file = logFile(context)
        if (!file.exists()) return emptyList()
        return file.readLines().takeLast(limit).asReversed()
    }

    private fun appendLine(context: Context, line: String) {
        val file = logFile(context)
        trimIfNeeded(file)
        file.appendText(line + "\n")
    }

    private fun trimIfNeeded(file: File) {
        if (!file.exists() || file.length() <= MAX_BYTES) return
        val tail = file.readLines().takeLast(300).joinToString(separator = "\n", postfix = "\n")
        file.writeText(tail)
    }

    private fun logFile(context: Context): File {
        return File(context.filesDir, LOG_FILE_NAME)
    }
}
