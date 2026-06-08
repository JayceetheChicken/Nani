package com.nani.agent.saf

data class ActionExecutionResult(
    val successes: List<String>,
    val failures: List<String>,
    val warnings: List<String>
)
