package com.nani.agent.plan

import com.nani.agent.ai.AiAction
import com.nani.agent.ai.AiRiskLevel

object PlanValidator {
    private const val MAX_OPERATIONS = 50
    private val secretMarkers = listOf("apikey", "api_key", "password", "token", "secret", "c:\\", "/system", "/data")
    private val forbiddenOperations = setOf(
        "delete_file",
        "move_file",
        "rename_file",
        "upload_file",
        "download_file",
        "open_settings",
        "grant_permission",
        "install_app",
        "uninstall_app"
    )

    fun validate(plan: ExecutablePlan, hasWorkFolder: Boolean): PlanValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (plan.actionType == AiAction.Blocked) {
            errors += "Blocked plans cannot be executed."
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
        val requiresWorkFolder = plan.operations.isNotEmpty()

        if (hasWritingOperations && !plan.requiresConfirmation) {
            errors += "Writing operations require explicit confirmation."
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

        return PlanValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            requiresWorkFolder = requiresWorkFolder,
            hasWritingOperations = hasWritingOperations
        )
    }

    private fun validateOperation(index: Int, operation: PlanOperation, errors: MutableList<String>) {
        when (operation.op) {
            PlanOperationType.Unsupported -> {
                val opName = operation.rawOp.ifBlank { "unknown" }
                if (opName in forbiddenOperations) {
                    errors += "Operation '${operation.rawOp}' is forbidden."
                } else {
                    errors += "Operation '${operation.rawOp}' is not supported."
                }
            }
            PlanOperationType.ListFiles,
            PlanOperationType.SummarizeFolder -> Unit
            PlanOperationType.CreateFolder -> {
                validatePath("operation ${index + 1} path", operation.path, errors)
            }
            PlanOperationType.CopyFile -> {
                validatePath("operation ${index + 1} source", operation.from, errors)
                validatePath("operation ${index + 1} target", operation.to, errors)
            }
        }
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
}
