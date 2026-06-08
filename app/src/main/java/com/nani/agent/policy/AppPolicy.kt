package com.nani.agent.policy

object AppPolicy {
    private val blockedFragments = listOf(
        "settings",
        "permissioncontroller",
        "packageinstaller",
        "installer",
        "playstore"
    )

    private val sensitiveFragments = listOf(
        "bank",
        "payment",
        "wallet",
        "password",
        "authenticator",
        "2fa",
        "paypal"
    )

    private val browserFragments = listOf(
        "browser",
        "chrome",
        "firefox",
        "edge",
        "samsung.android.app.sbrowser"
    )

    private val sendFragments = listOf(
        "messaging",
        "messages",
        "whatsapp",
        "telegram",
        "mail",
        "gmail",
        "outlook"
    )

    fun decisionForPackage(packageName: String): AppPolicyDecision {
        val normalized = packageName.lowercase()
        return when {
            blockedFragments.any { normalized.contains(it) } -> AppPolicyDecision.Blocked
            sensitiveFragments.any { normalized.contains(it) } -> AppPolicyDecision.Blocked
            browserFragments.any { normalized.contains(it) } -> AppPolicyDecision.RequiresInternetConfirmation
            sendFragments.any { normalized.contains(it) } -> AppPolicyDecision.RequiresSendConfirmation
            else -> AppPolicyDecision.Allowed
        }
    }
}

enum class AppPolicyDecision {
    Allowed,
    RequiresInternetConfirmation,
    RequiresSendConfirmation,
    Blocked
}
