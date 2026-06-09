package com.nani.agent.memory

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object MemoryStore {
    private const val FILE_NAME = "nani_memory.json"
    private const val MAX_CONTENT_CHARS = 500
    private val sensitiveMarkers = listOf(
        "api key",
        "apikey",
        "api_key",
        "password",
        "passwort",
        "pin",
        "2fa",
        "tan",
        "captcha",
        "credit card",
        "kreditkarte",
        "iban",
        "health",
        "gesundheit"
    )

    fun addMemory(context: Context, content: String): MemoryItem {
        val cleanContent = sanitizeContent(content)
        require(cleanContent.isNotBlank()) { "Memory is empty." }
        require(!containsSensitiveData(cleanContent)) { "Sensitive memory content is not allowed." }

        val now = System.currentTimeMillis()
        val memory = MemoryItem(
            id = UUID.randomUUID().toString(),
            content = cleanContent,
            createdAtMillis = now,
            updatedAtMillis = now,
            enabled = true
        )
        save(context, listMemories(context) + memory)
        return memory
    }

    fun listMemories(context: Context): List<MemoryItem> {
        val file = memoryFile(context)
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText(Charsets.UTF_8))
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    add(
                        MemoryItem(
                            id = json.optString("id"),
                            content = json.optString("content"),
                            createdAtMillis = json.optLong("createdAtMillis"),
                            updatedAtMillis = json.optLong("updatedAtMillis"),
                            enabled = json.optBoolean("enabled", true)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun getEnabledMemories(context: Context): List<MemoryItem> {
        return listMemories(context).filter { it.enabled }
    }

    fun disableMemory(context: Context, id: String) {
        val now = System.currentTimeMillis()
        save(
            context = context,
            memories = listMemories(context).map {
                if (it.id == id) it.copy(enabled = false, updatedAtMillis = now) else it
            }
        )
    }

    fun buildMemoryContext(context: Context, maxItems: Int = 8): String {
        val enabled = getEnabledMemories(context).takeLast(maxItems)
        if (enabled.isEmpty()) return "No enabled local memories."
        return buildString {
            appendLine("Enabled local memories:")
            enabled.forEachIndexed { index, memory ->
                appendLine("${index + 1}. ${memory.content.take(MAX_CONTENT_CHARS)}")
            }
        }.trim()
    }

    private fun save(context: Context, memories: List<MemoryItem>) {
        val array = JSONArray()
        memories.forEach { memory ->
            array.put(
                JSONObject()
                    .put("id", memory.id)
                    .put("content", memory.content)
                    .put("createdAtMillis", memory.createdAtMillis)
                    .put("updatedAtMillis", memory.updatedAtMillis)
                    .put("enabled", memory.enabled)
            )
        }
        memoryFile(context).writeText(array.toString(2), Charsets.UTF_8)
    }

    private fun sanitizeContent(content: String): String {
        return content.replace(Regex("\\s+"), " ").trim().take(MAX_CONTENT_CHARS)
    }

    private fun containsSensitiveData(content: String): Boolean {
        val normalized = content.lowercase()
        return sensitiveMarkers.any { normalized.contains(it) }
    }

    private fun memoryFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }
}
