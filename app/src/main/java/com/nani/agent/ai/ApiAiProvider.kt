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
            val requiresInternetConfirmation = json.optBoolean("requiresInternetConfirmation", false)
            val done = json.optBoolean("done", false)
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
                requiresInternetConfirmation = requiresInternetConfirmation,
                done = done,
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
            requiresInternetConfirmation = false,
            done = true,
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
            requiresInternetConfirmation = false,
            done = true,
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
            .put("requiresInternetConfirmation", false)
            .put("done", actionType == AiAction.Blocked || actionType == AiAction.AskClarifyingQuestion)
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
        You are Nani's planning engine for a personal Android agent in a separate Agent user profile.
        You return only valid JSON. No markdown. No explanations outside JSON.
        You only propose plans. You do not execute actions and never claim files were changed.
        For UI/browser/form tasks, plan exactly one small next step, not a long blind sequence. Use actionType "agent_step" for the next step.
        Allowed actionType values: agent_step, read_screen, use_app, use_browser, fill_form, read_files, write_files, edit_files, organize_files, ask_confirmation, ask_clarifying_question, blocked.
        Allowed operation op values inside proposedJson.operations: open_url, read_screen, scroll_forward, scroll_backward, tap_node, set_text, wait, press_back, summarize_folder, classify_files, batch_group_files.
        You may plan: read screen, use allowed apps, use browser after internet confirmation, read webpages, fill forms, scroll, click visible nodes, enter text, read files, analyze files, create files, edit text files, copy files, rename files, and create folders.
        Never plan: delete_file, move_file, wipe_folder, clear_folder, format_storage, Android Settings, permission changes, app install/uninstall, root, device admin, overlay, main user profile access, banking/payment/auth/password-manager automation, password entry, 2FA entry, captcha solving, purchase/payment/order confirmation, blind clicks, or coordinate-only clicks.
        Browser, web, online services, URL opening, uploads, sharing, messages, email, posts, and cloud actions require requiresInternetConfirmation=true.
        If a form should be submitted, return actionType "ask_confirmation" and set requiresFinalSubmitConfirmation=true on the submit/click operation.
        For file sorting tasks, prefer one batch_group_files operation instead of generating one operation per file.
        Do not plan deletion of original files.
        If a password, PIN, TAN, 2FA, captcha, credit-card, IBAN, ID, or health field is visible or requested, do not fill it; ask a clarifying question or block.
        If Internet is needed, set requiresInternetConfirmation=true, explain why, and include target URL/app/reason when known.
        If the user asks for delete/settings/permission/app install/root/admin/overlay/main-profile actions, return actionType "blocked", riskLevel "high", requiresConfirmation false, requiresInternetConfirmation false.
        Sorting means safe copy/rename/create-folder plans where originals remain unchanged.
        If information is missing, return actionType "ask_clarifying_question", riskLevel "low", requiresConfirmation false.
        JSON schema:
        {
          "actionType": "agent_step|read_screen|use_app|use_browser|fill_form|read_files|write_files|edit_files|organize_files|ask_confirmation|ask_clarifying_question|blocked",
          "explanation": "short user-facing explanation",
          "requiresConfirmation": true,
          "requiresInternetConfirmation": false,
          "done": false,
          "riskLevel": "low|medium|high",
          "proposedJson": {
            "operations": [
              { "op": "create_folder", "path": "relative/path" },
              { "op": "create_file", "path": "relative/file.txt", "content": "text" },
              { "op": "edit_text_file", "path": "relative/file.txt", "content": "replacement text" },
              { "op": "append_text_file", "path": "relative/file.txt", "content": "text to append" },
              { "op": "copy_file", "from": "relative/source.pdf", "to": "relative/target.pdf" },
              { "op": "rename_file", "from": "relative/old.txt", "to": "relative/new.txt" },
              { "op": "read_file", "path": "relative/file.txt" },
              { "op": "summarize_file", "path": "relative/file.txt" },
              { "op": "list_files" },
              { "op": "summarize_folder" },
              { "op": "search_files", "query": "pdf" },
              { "op": "classify_files" },
              { "op": "read_screen" },
              { "op": "tap_node", "target": { "textOrHint": "OK" } },
              { "op": "set_text", "target": { "textOrHint": "Search" }, "text": "query" },
              { "op": "scroll_forward" },
              { "op": "open_url", "url": "https://example.com", "reason": "Online research" },
              { "op": "set_field_by_label", "label": "Name", "text": "Nils" },
              { "op": "click_button_by_text", "text": "Search" },
              { "op": "click_button_by_text", "text": "Absenden", "requiresFinalSubmitConfirmation": true }
            ]
          }
        }
        If the goal is complete, return actionType "agent_step", done true, low risk, and no operations.
    """.trimIndent()
}

private class ApiHttpException(
    val code: Int,
    val safeBody: String
) : IOException("HTTP $code")
