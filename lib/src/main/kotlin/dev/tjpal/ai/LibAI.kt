package dev.tjpal.ai

import dev.tjpal.ai.di.AIComponent
import dev.tjpal.ai.di.DaggerAIComponent
import dev.tjpal.ai.di.LLMProvider
import dev.tjpal.ai.openai.OpenAIConfig
import java.security.InvalidParameterException

class LibAI(private val config: OpenAIConfig) {
    private val component: AIComponent by lazy {
        DaggerAIComponent.builder()
            .config(config)
            .build()
    }

    private val llmsByProvider: Map<String, LLM> by lazy { component.llms() }

    fun llm(provider: LLMProvider = LLMProvider.OPENAI): LLM {
        return llm(provider.providerName)
    }

    fun llm(providerName: String): LLM {
        return llmsByProvider[providerName]
            ?: throw InvalidParameterException("No LLM is registered for provider '$providerName'. Available providers: ${llmsByProvider.keys.sorted()}")
    }
}
