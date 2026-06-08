package com.nani.agent.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ApiAiProvider(
    private val settings: AiSettings
) : AiProvider {
    override suspend fun generatePlan(userCommand: String): AiPlan {
        return withContext(Dispatchers.IO) {
            val validationError = validateSettings()
            if (validationError != null) return@withContext errorPlan(validationError)

            try {
                val endpoint = buildChatCompletionsEndpoint(settings.apiBaseUrl)
                val response = postChatCompletion(endpoint, userCommand)
                val parsedPlan = parseChatCompletionResponse(response)
                if (isDeleteRequest(userCommand) && parsedPlan.actionType != AiAction.Blocked) {
                    blockedDeletePlan()
                } else {
                    parsedPlan
                }
            } catch (exception: Exception) {
                errorPlan("API request failed: ${safeError(exception)}")
            }
        }
    }

    private fun validateSettings(): String? {
        return when {
            settings.apiBaseUrl.isBlank() -> "API Base URL is empty."
            settings.modelName.isBlank() -> "Model name is empty."
            settings.apiKey.isBlank() -> "API key is empty."
            else -> null
        }
    }

    private fun buildChatCompletionsEndpoint(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/chat/completions")) {
            trimmed
        } else {
            "$trimmed/chat/completions"
        }
    }

    private fun postChatCompletion(endpoint: String, userCommand: String): String {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
            setRequestProperty("Content-Type", "application/json")
        }

        return try {
            val requestBody = JSONObject()
                .put("model", settings.modelName)
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", systemPrompt))
                        .put(JSONObject().put("role", "user").put("content", userCommand))
                )
                .put("temperature", 0.1)

            connection.outputStream.use { output ->
                output.write(requestBody.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("HTTP $responseCode")
            }

            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseChatCompletionResponse(responseBody: String): AiPlan {
        return try {
            val content = JSONObject(responseBody)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            parsePlanContent(content)
        } catch (exception: Exception) {
            errorPlan("API response was not valid JSON.")
        }
    }

    private fun parsePlanContent(content: String): AiPlan {
        return try {
            val json = JSONObject(content)
            val action = AiAction.fromWireName(json.getString("actionType"))
                ?: return errorPlan("API response was not valid JSON.")
            val risk = AiRiskLevel.fromWireName(json.getString("riskLevel"))
                ?: return errorPlan("API response was not valid JSON.")
            val explanation = json.getString("explanation")
            val requiresConfirmation = json.getBoolean("requiresConfirmation")
            val proposedJsonValue = json.get("proposedJson")
            val proposedJson = when (proposedJsonValue) {
                is JSONObject -> proposedJsonValue.toString(2)
                is JSONArray -> proposedJsonValue.toString(2)
                else -> proposedJsonValue.toString()
            }

            AiPlan(
                actionType = action,
                explanation = explanation,
                requiresConfirmation = requiresConfirmation,
                riskLevel = risk,
                proposedJson = proposedJson
            )
        } catch (exception: Exception) {
            errorPlan("API response was not valid JSON.")
        }
    }

    private fun blockedDeletePlan(): AiPlan {
        return AiPlan(
            actionType = AiAction.Blocked,
            explanation = "Deleting files is disabled.",
            requiresConfirmation = false,
            riskLevel = AiRiskLevel.High,
            proposedJson = safeJson(
                actionType = AiAction.Blocked,
                riskLevel = AiRiskLevel.High,
                message = "delete_disabled"
            )
        )
    }

    private fun errorPlan(explanation: String): AiPlan {
        return AiPlan(
            actionType = AiAction.AskClarifyingQuestion,
            explanation = explanation,
            requiresConfirmation = false,
            riskLevel = AiRiskLevel.Low,
            proposedJson = safeJson(
                actionType = AiAction.AskClarifyingQuestion,
                riskLevel = AiRiskLevel.Low,
                message = explanation
            )
        )
    }

    private fun safeJson(actionType: AiAction, riskLevel: AiRiskLevel, message: String): String {
        return JSONObject()
            .put("actionType", actionType.wireName)
            .put("requiresConfirmation", false)
            .put("riskLevel", riskLevel.wireName)
            .put("error", message)
            .put("executesActions", false)
            .put("accessesFiles", false)
            .toString(2)
    }

    private fun safeError(exception: Exception): String {
        return when (exception) {
            is IOException -> exception.message?.take(80) ?: "network_error"
            else -> exception::class.java.simpleName
        }
    }

    private fun isDeleteRequest(userCommand: String): Boolean {
        val normalized = userCommand.lowercase()
        return "lösche" in normalized || "loesche" in normalized || "delete" in normalized
    }

    private val systemPrompt = """
        You are Nani's planning engine.
        You must return ONLY valid JSON.
        No markdown.
        No explanations outside JSON.
        You may only propose plans.
        You must not execute actions.
        You must not claim that files were changed.
        You must never move, delete, copy, rename, upload, download, or modify files.
        You must never execute Android actions or Accessibility actions.
        Allowed actionType values: list_files, suggest_move_files, summarize_folder, ask_clarifying_question, blocked.
        The JSON must contain: actionType, explanation, requiresConfirmation, riskLevel, proposedJson.
        riskLevel must be one of: low, medium, high.
        If the user asks to delete files, return actionType "blocked", riskLevel "high", requiresConfirmation false.
        If the user asks to move/sort files, return actionType "suggest_move_files", riskLevel "medium", requiresConfirmation true.
        If unsure, return actionType "ask_clarifying_question", riskLevel "low", requiresConfirmation false.
    """.trimIndent()
}
