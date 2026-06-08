package com.nani.agent

object SecurityPolicy {
    private val deniedPackageFragments = listOf(
        "settings",
        "permissioncontroller",
        "packageinstaller"
    )

    private val allowedPackageNames = setOf(
        "com.nani.agent",
        "com.android.systemui",
        "com.sec.android.app.launcher",
        "com.google.android.apps.nexuslauncher"
    )

    fun decisionFor(packageName: String): SecurityDecision {
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
