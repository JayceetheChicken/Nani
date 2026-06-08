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
                val response = try {
                    postChatCompletion(endpoint, userCommand, includeResponseFormat = true)
                } catch (exception: ApiHttpException) {
                    if (settings.providerType != AiPrefs.PROVIDER_DEEPSEEK_API && exception.code == 400) {
                        postChatCompletion(endpoint, userCommand, includeResponseFormat = false)
                    } else {
                        throw exception
                    }
                }
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

    private fun postChatCompletion(
        endpoint: String,
        userCommand: String,
        includeResponseFormat: Boolean
    ): String {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
            setRequestProperty("Content-Type", "application/json")
        }

        return try {
            val requestBody = buildRequestBody(userCommand, includeResponseFormat)
            connection.outputStream.use { output ->
                output.write(requestBody.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val safeBody = connection.errorStream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    ?.take(220)
                    .orEmpty()
                throw ApiHttpException(responseCode, safeBody)
            }

            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun buildRequestBody(userCommand: String, includeResponseFormat: Boolean): JSONObject {
        val body = JSONObject()
            .put("model", settings.modelName)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userCommand))
            )
            .put("temperature", 0.1)

        if (includeResponseFormat) {
            body.put("response_format", JSONObject().put("type", "json_object"))
        }
        if (settings.providerType == AiPrefs.PROVIDER_DEEPSEEK_API) {
            body.put("thinking", JSONObject().put("type", "disabled"))
        }
        return body
    }

    private fun parseChatCompletionResponse(responseBody: String): AiPlan {
        return try {
            val choice = JSONObject(responseBody)
                .getJSONArray("choices")
                .getJSONObject(0)
            val finishReason = choice.optString("finish_reason", "")
            if (finishReason == "length") {
                return errorPlan("API response was cut off before valid JSON was complete.")
            }

            val content = choice
                .getJSONObject("message")
                .getString("content")
            parsePlanContent(extractJsonContent(content))
        } catch (exception: Exception) {
            errorPlan("API response was not valid JSON.")
        }
    }

    private fun extractJsonContent(content: String): String {
        val trimmed = content.trim()
        if (!trimmed.startsWith("```")) return trimmed

        val withoutFence = trimmed
            .removePrefix("```json")
            .removePrefix("```")
            .trim()
        return withoutFence.substringBeforeLast("```").trim()
    }

    private fun parsePlanContent(content: String): AiPlan {
        return try {
            val json = JSONObject(content)
            val action = AiAction.fromWireName(json.getString("actionType"))
                ?: return errorPlan("API response used an unknown actionType.")
            val risk = AiRiskLevel.fromWireName(json.getString("riskLevel"))
                ?: return errorPlan("API response used an unknown riskLevel.")
            val explanation = json.getString("explanation")
            val requiresConfirmation = json.getBoolean("requiresConfirmation")
            val proposedJsonValue = json.get("proposedJson")
            val proposedJson = when (proposedJsonValue) {
                is JSONObject -> proposedJsonValue.toString(2)
                is JSONArray -> JSONObject()
                    .put("root", "selected_saf_tree")
                    .put("operations", proposedJsonValue)
                    .toString(2)
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
            .put("root", "selected_saf_tree")
            .put("operations", JSONArray())
            .put("diagnostic", message.take(160))
            .put("actionType", actionType.wireName)
            .put("requiresConfirmation", false)
            .put("riskLevel", riskLevel.wireName)
            .toString(2)
    }

    private fun safeError(exception: Exception): String {
        return when (exception) {
            is ApiHttpException -> "HTTP ${exception.code}: ${exception.safeBody.take(160)}"
            is IOException -> exception.message?.take(120) ?: "network_error"
            else -> exception::class.java.simpleName
        }
    }

    private fun isDeleteRequest(userCommand: String): Boolean {
        val normalized = userCommand.lowercase()
        return "lösche" in normalized || "loesche" in normalized || "delete" in normalized
    }

    private val systemPrompt = """
        You are Nani's planning engine.
        You return only valid JSON. No markdown. No explanations outside JSON.
        You only propose plans. You do not execute actions and never claim files were changed.
        Allowed actionType values: list_files, summarize_folder, suggest_copy_files, create_folders, ask_clarifying_question, blocked.
        Legacy suggest_move_files is not allowed; sorting means safe copy plans where originals remain unchanged.
        Allowed operation op values inside proposedJson.operations: list_files, create_folder, copy_file, summarize_folder.
        Never plan delete_file, move_file, rename_file, upload_file, download_file, open_settings, grant_permission, install_app, uninstall_app, Android actions, Accessibility actions, clicks, gestures, or permission changes.
        If the user asks for dangerous actions, return actionType "blocked", riskLevel "high", requiresConfirmation false.
        If the user asks to sort files, return actionType "suggest_copy_files", riskLevel "medium", requiresConfirmation true.
        If information is missing, return actionType "ask_clarifying_question", riskLevel "low", requiresConfirmation false.
        JSON schema:
        {
          "actionType": "list_files|summarize_folder|suggest_copy_files|create_folders|ask_clarifying_question|blocked",
          "explanation": "short user-facing explanation",
          "requiresConfirmation": true,
          "riskLevel": "low|medium|high",
          "proposedJson": {
            "root": "selected_saf_tree",
            "operations": [
              { "op": "create_folder", "path": "relative/path" },
              { "op": "copy_file", "from": "relative/source.pdf", "to": "relative/target.pdf" },
              { "op": "list_files" },
              { "op": "summarize_folder" }
            ]
          }
        }
    """.trimIndent()
}

private class ApiHttpException(
    val code: Int,
    val safeBody: String
) : IOException("HTTP $code")
