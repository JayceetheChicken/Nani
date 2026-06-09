package com.nani.agent.plan

import com.nani.agent.ai.AiAction
import com.nani.agent.ai.AiRiskLevel
import com.nani.agent.policy.NetworkPolicy

object PlanValidator {
    const val MAX_OPERATIONS = 1000
    const val PREVIEW_OPERATION_LIMIT = 50
    const val EXECUTION_CHUNK_SIZE = 25
    private val secretMarkers = listOf("apikey", "api_key", "password", "token", "secret", "c:\\", "/system", "/data")
    private val forbiddenOperations = setOf(
        "delete_file",
        "delete",
        "remove",
        "trash",
        "move_file",
        "wipe_folder",
        "wipe",
        "clear_folder",
        "clear",
        "format_storage",
        "upload_file",
        "download_file",
        "share_file",
        "open_settings",
        "change_permission",
        "grant_permission",
        "revoke_permission",
        "install_app",
        "uninstall_app",
        "factory_reset",
        "root_action",
        "device_admin_action",
        "overlay_action",
        "accessibility_click",
        "accessibility_gesture",
        "work_on_webpage",
        "permission",
        "settings"
    )
    private val uiOperations = setOf(
        PlanOperationType.ReadScreen,
        PlanOperationType.TapNode,
        PlanOperationType.SetText,
        PlanOperationType.AppendText,
        PlanOperationType.Scroll,
        PlanOperationType.ScrollForward,
        PlanOperationType.ScrollBackward,
        PlanOperationType.PressBack,
        PlanOperationType.PressHome,
        PlanOperationType.OpenApp,
        PlanOperationType.WaitForScreen,
        PlanOperationType.Wait,
        PlanOperationType.FindNode,
        PlanOperationType.SelectOption,
        PlanOperationType.FillForm,
        PlanOperationType.SetFieldByLabel,
        PlanOperationType.SetFieldByHint,
        PlanOperationType.SetFieldByNodeId,
        PlanOperationType.ClickButtonByText,
        PlanOperationType.SubmitForm,
        PlanOperationType.OpenUrl,
        PlanOperationType.UseApp
    )
    private val fileOperations = setOf(
        PlanOperationType.ListFiles,
        PlanOperationType.ReadFile,
        PlanOperationType.SummarizeFile,
        PlanOperationType.SearchFiles,
        PlanOperationType.BatchGroupFiles,
        PlanOperationType.ClassifyFiles,
        PlanOperationType.CreateFolder,
        PlanOperationType.CreateFile,
        PlanOperationType.EditTextFile,
        PlanOperationType.AppendTextFile,
        PlanOperationType.CopyFile,
        PlanOperationType.RenameFile,
        PlanOperationType.SummarizeFolder
    )

    fun validate(
        plan: ExecutablePlan,
        hasWorkFolder: Boolean,
        internetConfirmed: Boolean = false
    ): PlanValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        when (plan.actionType) {
            AiAction.ReadFiles,
            AiAction.AgentStep,
            AiAction.WriteFiles,
            AiAction.EditFiles,
            AiAction.OrganizeFiles,
            AiAction.ReadScreen,
            AiAction.UseApp,
            AiAction.UseBrowser,
            AiAction.FillForm,
            AiAction.AskConfirmation -> Unit
            AiAction.AskClarifyingQuestion -> {
                errors += "Clarifying-question plans cannot be executed."
            }
            AiAction.Blocked -> {
                errors += "Blocked plans cannot be executed."
            }
        }
        if (plan.riskLevel == AiRiskLevel.High) {
            errors += "High-risk plans cannot be executed."
        }
        if (plan.operations.size > MAX_OPERATIONS) {
            errors += "Plan has too many operations. MVP limit is $MAX_OPERATIONS."
        }
        if (plan.operations.isEmpty()) {
            if (!plan.done) errors += "Plan has no executable operations."
        }

        val hasWritingOperations = plan.operations.any { it.op.writes }
        val hasInternetOperations = plan.requiresInternetConfirmation ||
            plan.operations.any { it.op.usesInternet || NetworkPolicy.operationRequiresInternetConfirmation(it.rawOp) }
        val requiresWorkFolder = plan.operations.any { it.op in fileOperations }
        val requiresFinalSubmitConfirmation = plan.operations.any {
            it.requiresFinalSubmitConfirmation || it.op == PlanOperationType.SubmitForm || isFinalSubmitText(it.text)
        }

        if (hasWritingOperations && !plan.requiresConfirmation) {
            errors += "Writing operations require explicit confirmation."
        }
        if (hasInternetOperations && !plan.requiresInternetConfirmation) {
            errors += "Internet operations must set requiresInternetConfirmation=true."
        }
        if (hasInternetOperations && !internetConfirmed) {
            errors += "Internet actions require separate user confirmation."
        }
        if (requiresWorkFolder && !hasWorkFolder) {
            errors += "Select a work folder before executing this plan."
        }
        if (requiresFinalSubmitConfirmation && plan.actionType != AiAction.AskConfirmation) {
            errors += "Final submit/send actions must be returned as ask_confirmation plans."
        }

        plan.operations.forEachIndexed { index, operation ->
            validateOperation(index, operation, errors)
        }

        if (plan.operations.any { it.op == PlanOperationType.CopyFile }) {
            warnings += "Copy limit is 20 MB per file when size can be checked."
        }
        if (plan.operations.size > 20) {
            warnings += "This plan affects many operations and should be reviewed carefully."
        }

        return PlanValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            requiresWorkFolder = requiresWorkFolder,
            hasWritingOperations = hasWritingOperations,
            requiresInternetConfirmation = hasInternetOperations,
            requiresFinalSubmitConfirmation = requiresFinalSubmitConfirmation
        )
    }

    private fun validateOperation(index: Int, operation: PlanOperation, errors: MutableList<String>) {
        if (isForbiddenOperation(operation.rawOp)) {
            errors += "Operation '${operation.rawOp}' is forbidden."
            return
        }

        when (operation.op) {
            PlanOperationType.Unsupported -> {
                val opName = operation.rawOp.ifBlank { "unknown" }
                if (isForbiddenOperation(opName)) {
                    errors += "Operation '${operation.rawOp}' is forbidden."
                } else {
                    errors += "Operation '${operation.rawOp}' is not supported."
                }
            }
            PlanOperationType.ListFiles,
            PlanOperationType.SummarizeFolder,
            PlanOperationType.SearchFiles,
            PlanOperationType.ClassifyFiles -> Unit
            PlanOperationType.BatchGroupFiles -> {
                validateBatchGroupFiles(index, operation, errors)
            }
            PlanOperationType.ReadFile,
            PlanOperationType.SummarizeFile -> {
                validatePath("operation ${index + 1} path", operation.path, errors)
            }
            PlanOperationType.CreateFolder -> {
                validatePath("operation ${index + 1} path", operation.path, errors)
            }
            PlanOperationType.CreateFile,
            PlanOperationType.EditTextFile,
            PlanOperationType.AppendTextFile -> {
                validatePath("operation ${index + 1} path", operation.path, errors)
                validateContent("operation ${index + 1} content", operation.content, errors)
            }
            PlanOperationType.CopyFile -> {
                validatePath("operation ${index + 1} source", operation.from, errors)
                validatePath("operation ${index + 1} target", operation.to, errors)
            }
            PlanOperationType.RenameFile -> {
                validatePath("operation ${index + 1} source", operation.from, errors)
                validatePath("operation ${index + 1} target", operation.to, errors)
            }
            PlanOperationType.OpenUrl -> {
                if (operation.url.isNullOrBlank()) {
                    errors += "operation ${index + 1} URL is missing."
                }
            }
            PlanOperationType.UseApp,
            PlanOperationType.ReadScreen,
            PlanOperationType.Scroll,
            PlanOperationType.ScrollForward,
            PlanOperationType.ScrollBackward,
            PlanOperationType.PressBack,
            PlanOperationType.PressHome,
            PlanOperationType.OpenApp,
            PlanOperationType.WaitForScreen,
            PlanOperationType.Wait,
            PlanOperationType.FindNode,
            PlanOperationType.TapNode,
            PlanOperationType.ClickButtonByText,
            PlanOperationType.SelectOption -> Unit
            PlanOperationType.SetText,
            PlanOperationType.AppendText,
            PlanOperationType.FillForm,
            PlanOperationType.SetFieldByLabel,
            PlanOperationType.SetFieldByHint,
            PlanOperationType.SetFieldByNodeId -> {
                validateContent("operation ${index + 1} text", operation.text ?: operation.content, errors)
                validateSensitiveField(operation, errors)
            }
            PlanOperationType.SubmitForm -> {
                if (!operation.requiresFinalSubmitConfirmation) {
                    errors += "Submit operations must set requiresFinalSubmitConfirmation=true."
                }
            }
        }
    }

    private fun isForbiddenOperation(opName: String): Boolean {
        val normalized = opName.lowercase()
        return normalized in forbiddenOperations || forbiddenOperations.any { normalized.contains(it) }
    }

    private fun validatePath(label: String, path: String?, errors: MutableList<String>) {
        if (path.isNullOrBlank()) {
            errors += "$label is missing."
            return
        }
        val normalized = path.replace("\\", "/").lowercase()
        if (normalized.startsWith("/") || normalized.contains(":")) {
            errors += "$label must be a relative path."
        }
        if (normalized.split("/").any { it == ".." }) {
            errors += "$label must not contain '..'."
        }
        if (secretMarkers.any { normalized.contains(it) }) {
            errors += "$label appears to contain sensitive or system data."
        }
    }

    private fun validateContent(label: String, content: String?, errors: MutableList<String>) {
        val normalized = content.orEmpty().lowercase()
        if (secretMarkers.any { normalized.contains(it) }) {
            errors += "$label appears to contain sensitive data."
        }
    }

    private fun validateBatchGroupFiles(index: Int, operation: PlanOperation, errors: MutableList<String>) {
        val groupSize = operation.groupSize ?: return
        if (groupSize !in 1..MAX_OPERATIONS) {
            errors += "operation ${index + 1} groupSize must be between 1 and $MAX_OPERATIONS."
        }
        val mode = operation.mode?.lowercase()
        if (mode != null && mode != "copy") {
            errors += "operation ${index + 1} batch_group_files only supports copy mode in this MVP."
        }
        val prefix = operation.targetFolderPrefix
        if (prefix != null) {
            val normalizedPrefix = prefix.replace("\\", "/").lowercase()
            if (normalizedPrefix.isBlank() ||
                normalizedPrefix.contains("/") ||
                normalizedPrefix.contains(":") ||
                normalizedPrefix == "." ||
                normalizedPrefix == ".."
            ) {
                errors += "operation ${index + 1} targetFolderPrefix must be a simple folder-name prefix."
            }
        }
        val sortBy = operation.sortBy?.lowercase()
        if (sortBy != null && sortBy !in setOf("name", "date")) {
            errors += "operation ${index + 1} sortBy must be name or date."
        }
    }

    private fun validateSensitiveField(operation: PlanOperation, errors: MutableList<String>) {
        val haystack = listOfNotNull(
            operation.text,
            operation.label,
            operation.targetTextOrHint,
            operation.targetViewIdResourceName
        ).joinToString(" ").lowercase()
        val sensitiveMarkers = listOf("password", "passwort", "pin", "tan", "2fa", "captcha", "kreditkarte", "iban")
        if (sensitiveMarkers.any { haystack.contains(it) }) {
            errors += "Sensitive form fields cannot be filled automatically."
        }
    }

    private fun isFinalSubmitText(text: String?): Boolean {
        val normalized = text.orEmpty().lowercase()
        return listOf("absenden", "submit", "send", "post", "kaufen", "bestellen", "buchen", "pay").any {
            normalized.contains(it)
        }
    }
}
