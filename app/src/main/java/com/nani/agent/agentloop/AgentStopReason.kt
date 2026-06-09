package com.nani.agent.agentloop

enum class AgentStopReason {
    None,
    GoalDone,
    UserStopped,
    Paused,
    PolicyBlocked,
    NeedsInternetConfirmation,
    NeedsFinalSubmitConfirmation,
    MaxStepsReached,
    Timeout,
    Error
}
