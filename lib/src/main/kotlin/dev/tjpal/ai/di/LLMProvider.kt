package dev.tjpal.ai.di

enum class LLMProvider(val providerName: String) {
    OPENAI("openai");

    companion object {
        const val OPENAI_PROVIDER_NAME = "openai"
    }
}