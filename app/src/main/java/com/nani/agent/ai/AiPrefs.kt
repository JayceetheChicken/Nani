package com.nani.agent.ai

import android.content.Context

object AiPrefs {
    const val PROVIDER_DEEPSEEK_API = "deepseek_api"
    const val PROVIDER_OPENAI_COMPATIBLE_API = "openai_compatible_api"
    const val PROVIDER_CUSTOM_API = "custom_api"
    const val PROVIDER_LOCAL_DUMMY_GEMMA = "local_dummy_gemma"
    const val DEFAULT_DEEPSEEK_BASE_URL = "https://api.deepseek.com"
    const val DEFAULT_DEEPSEEK_MODEL_NAME = "deepseek-v4-flash"
    const val DEEPSEEK_PRO_MODEL_NAME = "deepseek-v4-pro"
    const val DEFAULT_LOCAL_MODEL_NAME = "Local Dummy / Gemma planned"

    private const val PREFS_NAME = "nani_ai_settings"
    private const val KEY_PROVIDER_TYPE = "providerType"
    private const val KEY_API_BASE_URL = "apiBaseUrl"
    private const val KEY_MODEL_NAME = "modelName"
    private const val KEY_API_KEY = "apiKey"

    fun load(context: Context): AiSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val providerType = normalizeProviderType(
            prefs.getString(KEY_PROVIDER_TYPE, PROVIDER_DEEPSEEK_API) ?: PROVIDER_DEEPSEEK_API
        )
        return AiSettings(
            providerType = providerType,
            apiBaseUrl = prefs.getString(KEY_API_BASE_URL, defaultBaseUrl(providerType))
                ?: defaultBaseUrl(providerType),
            modelName = prefs.getString(KEY_MODEL_NAME, defaultModelName(providerType))
                ?: defaultModelName(providerType),
            apiKey = prefs.getString(KEY_API_KEY, "").orEmpty()
        )
    }

    fun save(context: Context, settings: AiSettings) {
        // TODO: Move API key storage to Android Keystore before production use.
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROVIDER_TYPE, settings.providerType)
            .putString(KEY_API_BASE_URL, settings.apiBaseUrl)
            .putString(KEY_MODEL_NAME, settings.modelName.ifBlank { defaultModelName(settings.providerType) })
            .putString(KEY_API_KEY, settings.apiKey)
            .apply()
    }

    fun defaultBaseUrl(providerType: String): String {
        return when (providerType) {
            PROVIDER_DEEPSEEK_API -> DEFAULT_DEEPSEEK_BASE_URL
            else -> ""
        }
    }

    fun defaultModelName(providerType: String): String {
        return when (providerType) {
            PROVIDER_DEEPSEEK_API -> DEFAULT_DEEPSEEK_MODEL_NAME
            PROVIDER_LOCAL_DUMMY_GEMMA -> DEFAULT_LOCAL_MODEL_NAME
            else -> ""
        }
    }

    private fun normalizeProviderType(providerType: String): String {
        return when (providerType) {
            "local_gemma_4_e2b" -> PROVIDER_LOCAL_DUMMY_GEMMA
            PROVIDER_DEEPSEEK_API,
            PROVIDER_OPENAI_COMPATIBLE_API,
            PROVIDER_CUSTOM_API,
            PROVIDER_LOCAL_DUMMY_GEMMA -> providerType
            else -> PROVIDER_DEEPSEEK_API
        }
    }
}

data class AiSettings(
    val providerType: String,
    val apiBaseUrl: String,
    val modelName: String,
    val apiKey: String
)
