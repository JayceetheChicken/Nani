package com.nani.agent.agent

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.nani.agent.AgentPrefs
import com.nani.agent.LogStore
import com.nani.agent.SecurityDecision
import com.nani.agent.SecurityPolicy

class NaniAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastForegroundPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        AgentPrefs.setRunning(this, true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString().orEmpty()
        if (packageName.isBlank() || packageName == lastForegroundPackage) return

        lastForegroundPackage = packageName
        LogStore.appendForegroundPackage(this, packageName)

        when (SecurityPolicy.decisionFor(packageName)) {
            SecurityDecision.Block -> exitBlockedPackage(packageName)
            SecurityDecision.Allow,
            SecurityDecision.ObserveOnly -> Unit
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        AgentPrefs.setRunning(this, false)
        super.onDestroy()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        AgentPrefs.setRunning(this, false)
        return super.onUnbind(intent)
    }

    private fun exitBlockedPackage(packageName: String) {
        LogStore.appendSecurityAction(this, packageName, "global_back")
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({
            LogStore.appendSecurityAction(this, packageName, "global_home")
            performGlobalAction(GLOBAL_ACTION_HOME)
        }, 300)
    }
}
