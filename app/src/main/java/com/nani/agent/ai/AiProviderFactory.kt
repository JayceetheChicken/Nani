package com.nani.agent.ai

object AiProviderFactory {
    fun create(settings: AiSettings): AiProvider {
        return when (settings.providerType) {
            AiPrefs.PROVIDER_DEEPSEEK_API,
            AiPrefs.PROVIDER_OPENAI_COMPATIBLE_API,
            AiPrefs.PROVIDER_CUSTOM_API -> ApiAiProvider(settings)
            else -> LocalGemmaProvider()
        }
    }

    fun providerStatus(settings: AiSettings): String {
        return when (settings.providerType) {
            AiPrefs.PROVIDER_DEEPSEEK_API -> "AI Provider: DeepSeek API"
            AiPrefs.PROVIDER_OPENAI_COMPATIBLE_API -> "AI Provider: OpenAI Compatible API"
            AiPrefs.PROVIDER_CUSTOM_API -> "AI Provider: Custom API"
            else -> "AI Provider: Local Dummy / Gemma planned"
        }
    }
}
