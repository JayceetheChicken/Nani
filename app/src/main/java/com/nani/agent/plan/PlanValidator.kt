package com.nani.agent.plan

import com.nani.agent.ai.AiAction
import com.nani.agent.ai.AiRiskLevel
import com.nani.agent.policy.NetworkPolicy

object PlanValidator {
    private const val MAX_OPERATIONS = 50
    private val secretMarkers = listOf("apikey", "api_key", "password", "token", "secret", "c:\\", "/system", "/data")
    private val forbiddenOperations = setOf(
        "delete_file",
        "move_file",
        "wipe_folder",
        "clear_folder",
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
        "click",
        "gesture",
        "permission",
        "settings"
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
            AiAction.WriteFiles,
            AiAction.EditFiles,
            AiAction.OrganizeFiles,
            AiAction.UseApp,
            AiAction.UseBrowser -> Unit
            AiAction.AskConfirmation -> {
                errors += "Confirmation-request plans cannot be executed directly."
            }
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
            errors += "Plan has no executable operations."
        }

        val hasWritingOperations = plan.operations.any { it.op.writes }
        val hasInternetOperations = plan.requiresInternetConfirmation ||
            plan.operations.any { it.op.usesInternet || NetworkPolicy.operationRequiresInternetConfirmation(it.rawOp) }
        val requiresWorkFolder = plan.operations.any { it.op != PlanOperationType.OpenUrl && it.op != PlanOperationType.UseApp }

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
            requiresInternetConfirmation = hasInternetOperations
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
            PlanOperationType.UseApp -> {
                errors += "UI app actions are not executable in this build."
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
}
