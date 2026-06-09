package com.nani.agent.memory

data class MemoryItem(
    val id: String,
    val content: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val enabled: Boolean
)
