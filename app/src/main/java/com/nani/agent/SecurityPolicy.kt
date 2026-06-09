package com.nani.agent

import com.nani.agent.policy.AppPolicy
import com.nani.agent.policy.AppPolicyDecision

object SecurityPolicy {
    private val deniedPackageFragments = listOf(
        "settings",
        "com.android.settings",
        "android.settings",
        "permissioncontroller",
        "packageinstaller",
        "installer",
        "vending",
        "playstore",
        "knox",
        "security",
        "account"
    )

    private val allowedPackageNames = setOf(
        "com.nani.agent",
        "com.android.systemui",
        "com.sec.android.app.launcher",
        "com.google.android.apps.nexuslauncher"
    )

    fun decisionFor(packageName: String): SecurityDecision {
        if (AppPolicy.decisionForPackage(packageName) == AppPolicyDecision.Blocked) {
            return SecurityDecision.Block
        }
        val normalized = packageName.lowercase()
        if (deniedPackageFragments.any { normalized.contains(it) }) {
            return SecurityDecision.Block
        }
        if (packageName in allowedPackageNames) {
            return SecurityDecision.Allow
        }
        return SecurityDecision.ObserveOnly
    }
}

enum class SecurityDecision {
    Allow,
    ObserveOnly,
    Block
}
