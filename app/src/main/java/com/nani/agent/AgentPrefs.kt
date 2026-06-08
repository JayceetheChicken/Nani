package com.nani.agent

import android.content.Context

object AgentPrefs {
    private const val PREFS_NAME = "nani_agent_state"
    private const val KEY_RUNNING = "running"

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
}
