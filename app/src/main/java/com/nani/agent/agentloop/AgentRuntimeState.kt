package com.nani.agent.agentloop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AgentRuntimeSnapshot(
    val loopState: AgentLoopState = AgentLoopState(),
    val serviceRunning: Boolean = false
)

object AgentRuntimeState {
    private val mutableState = MutableStateFlow(AgentRuntimeSnapshot())
    val state: StateFlow<AgentRuntimeSnapshot> = mutableState

    fun update(loopState: AgentLoopState, serviceRunning: Boolean) {
        mutableState.value = AgentRuntimeSnapshot(loopState, serviceRunning)
    }

    fun clear() {
        mutableState.value = AgentRuntimeSnapshot()
    }
}
