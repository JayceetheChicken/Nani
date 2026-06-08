package com.nani.agent.plan

data class PlanValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val requiresWorkFolder: Boolean,
    val hasWritingOperations: Boolean,
    val requiresInternetConfirmation: Boolean,
    val requiresFinalSubmitConfirmation: Boolean = false
) {
    val canExecute: Boolean
        get() = isValid
}
