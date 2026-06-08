package com.nani.agent.agent

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.nani.agent.AgentPrefs
import com.nani.agent.LogStore
import com.nani.agent.SecurityDecision
import com.nani.agent.SecurityPolicy
import com.nani.agent.uiagent.AccessibilityTreeReader
import com.nani.agent.uiagent.ScreenStateStore
import java.lang.ref.WeakReference

class NaniAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastForegroundPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        active = WeakReference(this)
        AgentPrefs.setRunning(this, true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!AgentPrefs.isAgentEnabled(this)) return

        val packageName = event?.packageName?.toString().orEmpty()
        refreshSnapshot()
        if (packageName.isBlank() || packageName == lastForegroundPackage) return

        lastForegroundPackage = packageName
        LogStore.appendForegroundPackage(this, packageName)

        when (SecurityPolicy.decisionFor(packageName)) {
            SecurityDecision.Block -> {
                if (AgentPrefs.isGuardEnabled(this)) {
                    exitBlockedPackage(packageName)
                }
            }
            SecurityDecision.Allow,
            SecurityDecision.ObserveOnly -> Unit
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        AgentPrefs.setRunning(this, false)
        ScreenStateStore.clear()
        active = null
        super.onDestroy()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        AgentPrefs.setRunning(this, false)
        ScreenStateStore.clear()
        active = null
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

    fun refreshSnapshot() {
        AccessibilityTreeReader(this).readCurrentScreen()?.let(ScreenStateStore::update)
    }

    companion object {
        @Volatile
        private var active: WeakReference<NaniAccessibilityService>? = null

        fun activeService(): NaniAccessibilityService? = active?.get()
    }
}
