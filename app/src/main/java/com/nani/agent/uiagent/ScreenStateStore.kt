package com.nani.agent.uiagent

object ScreenStateStore {
    @Volatile
    private var latestSnapshot: ScreenSnapshot? = null

    fun update(snapshot: ScreenSnapshot) {
        latestSnapshot = snapshot
    }

    fun latest(): ScreenSnapshot? = latestSnapshot

    fun clear() {
        latestSnapshot = null
    }
}
