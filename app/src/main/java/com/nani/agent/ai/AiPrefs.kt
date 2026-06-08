package com.nani.agent.ai

import android.content.Context

object AiPrefs {
    const val PROVIDER_LOCAL_GEMMA_4_E2B = "local_gemma_4_e2b"
    const val PROVIDER_OPENAI_COMPATIBLE_API = "openai_compatible_api"
    const val PROVIDER_CUSTOM_API = "custom_api"
    const val DEFAULT_LOCAL_MODEL_NAME = "Gemma 4 E2B"

    private const val PREFS_NAME = "nani_ai_settings"
    private const val KEY_PROVIDER_TYPE = "providerType"
    private const val KEY_API_BASE_URL = "apiBaseUrl"
    private const val KEY_MODEL_NAME = "modelName"
    private const val KEY_API_KEY = "apiKey"

    fun load(context: Context): AiSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AiSettings(
            providerType = prefs.getString(KEY_PROVIDER_TYPE, PROVIDER_LOCAL_GEMMA_4_E2B)
                ?: PROVIDER_LOCAL_GEMMA_4_E2B,
            apiBaseUrl = prefs.getString(KEY_API_BASE_URL, "").orEmpty(),
            modelName = prefs.getString(KEY_MODEL_NAME, DEFAULT_LOCAL_MODEL_NAME)
                ?: DEFAULT_LOCAL_MODEL_NAME,
            apiKey = prefs.getString(KEY_API_KEY, "").orEmpty()
        )
    }

    fun save(context: Context, settings: AiSettings) {
        // TODO: Move API key storage to Android Keystore before production use.
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROVIDER_TYPE, settings.providerType)
            .putString(KEY_API_BASE_URL, settings.apiBaseUrl)
            .putString(KEY_MODEL_NAME, settings.modelName.ifBlank { DEFAULT_LOCAL_MODEL_NAME })
            .putString(KEY_API_KEY, settings.apiKey)
            .apply()
    }
}

data class AiSettings(
    val providerType: String,
    val apiBaseUrl: String,
    val modelName: String,
    val apiKey: String
)
