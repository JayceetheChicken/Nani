package com.nani.agent.ai

object AiProviderFactory {
    fun create(settings: AiSettings): AiProvider {
        return when (settings.providerType) {
            AiPrefs.PROVIDER_OPENAI_COMPATIBLE_API,
            AiPrefs.PROVIDER_CUSTOM_API -> ApiAiProvider(settings)
            else -> LocalGemmaProvider()
        }
    }

    fun providerStatus(settings: AiSettings): String {
        return when (settings.providerType) {
            AiPrefs.PROVIDER_LOCAL_GEMMA_4_E2B -> "AI Provider: Local Gemma 4 E2B"
            else -> "AI Provider: API"
        }
    }
}
