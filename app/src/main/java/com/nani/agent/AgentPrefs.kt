package com.nani.agent

import android.content.Context

object AgentPrefs {
    private const val PREFS_NAME = "nani_agent_state"
    private const val KEY_RUNNING = "running"
    private const val KEY_AGENT_ENABLED = "agentEnabled"
    private const val KEY_GUARD_ENABLED = "guardEnabled"

    fun setRunning(context: Context, running: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RUNNING, running)
            .apply()
    }

    fun isRunning(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_RUNNING, false)
    }

    fun setAgentEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AGENT_ENABLED, enabled)
            .apply()
    }

    fun isAgentEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AGENT_ENABLED, true)
    }

    fun setGuardEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GUARD_ENABLED, enabled)
            .apply()
    }

    fun isGuardEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_GUARD_ENABLED, true)
    }
}
