package com.nani.agent.uiagent

import com.nani.agent.policy.AppPolicy
import com.nani.agent.policy.AppPolicyDecision

data class UiActionValidationResult(
    val canExecute: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val requiresFinalSubmitConfirmation: Boolean
)

object UiActionValidator {
    private val forbiddenOps = setOf(
        "open_settings",
        "change_permission",
        "install_app",
        "uninstall_app",
        "grant_permission",
        "revoke_permission",
        "approve_payment",
        "confirm_purchase",
        "enter_password",
        "enter_2fa_code",
        "solve_captcha",
        "delete_file",
        "delete",
        "factory_reset",
        "enter_pin",
        "enter_tan",
        "root_action",
        "device_admin_action",
        "overlay_action"
    )
    private val sensitiveTextMarkers = listOf(
        "password", "passwort", "pin", "tan", "2fa", "captcha", "kreditkarte",
        "credit card", "iban", "ausweis", "health", "gesundheit"
    )

    fun validate(
        snapshot: ScreenSnapshot?,
        actions: List<UiAction>,
        internetConfirmed: Boolean,
        finalSubmitConfirmed: Boolean
    ): UiActionValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val packageName = snapshot?.foregroundPackage.orEmpty()

        if (snapshot == null && actions.any { it.op != UiActionType.OpenUrl && it.op != UiActionType.PressHome }) {
            errors += "No screen snapshot is available. Enable Accessibility and refresh the screen first."
        }

        when (AppPolicy.decisionForPackage(packageName)) {
            AppPolicyDecision.Blocked -> errors += "Current app is blocked by Nani policy."
            AppPolicyDecision.RequiresInternetConfirmation -> if (!internetConfirmed) {
                errors += "Current app requires internet confirmation."
            }
            AppPolicyDecision.RequiresSendConfirmation -> warnings += "Messaging or mail apps require extra send confirmation."
            AppPolicyDecision.RequiresConfirmation,
            AppPolicyDecision.Allowed -> Unit
        }

        val needsFinalSubmit = actions.any { it.requiresFinalSubmitConfirmation || it.op.finalSubmit || isFinalSubmitText(it.text) }

        actions.forEach { action ->
            if (action.rawOp in forbiddenOps) errors += "UI operation '${action.rawOp}' is forbidden."
            if (action.op == UiActionType.Unsupported) errors += "UI operation '${action.rawOp}' is not supported."
            if (action.op.usesInternet && !internetConfirmed) errors += "Internet UI action requires confirmation."
            if ((action.op == UiActionType.SetText || action.op == UiActionType.AppendText || action.op.name.startsWith("SetField")) &&
                containsSensitiveFormMarker(action)
            ) {
                errors += "Sensitive form fields cannot be filled automatically."
            }
        }

        if (needsFinalSubmit && !finalSubmitConfirmed) {
            errors += "Final submit/send confirmation is required."
        }

        return UiActionValidationResult(
            canExecute = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            requiresFinalSubmitConfirmation = needsFinalSubmit
        )
    }

    private fun containsSensitiveFormMarker(action: UiAction): Boolean {
        val haystack = listOfNotNull(
            action.text,
            action.target?.textOrHint,
            action.target?.label,
            action.target?.viewIdResourceName
        ).joinToString(" ").lowercase()
        return sensitiveTextMarkers.any { haystack.contains(it) }
    }

    private fun isFinalSubmitText(text: String?): Boolean {
        val normalized = text.orEmpty().lowercase()
        return listOf("absenden", "send", "submit", "post", "kaufen", "bestellen", "buchen", "pay").any {
            normalized.contains(it)
        }
    }
}
