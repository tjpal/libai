package dev.tjpal.ai

import dev.tjpal.ai.audio.AudioRecorder
import dev.tjpal.ai.di.AIComponent
import dev.tjpal.ai.di.DaggerAIComponent
import dev.tjpal.ai.di.LLMProvider
import dev.tjpal.ai.openai.OpenAIConfig
import dev.tjpal.ai.openai.NoOpOpenAINativeToolRuntime
import dev.tjpal.ai.openai.OpenAINativeToolRuntime
import dev.tjpal.ai.tools.NoArgToolInstantiator
import dev.tjpal.ai.tools.ToolInstantiator
import java.security.InvalidParameterException

class LibAI(
    private val config: OpenAIConfig,
    private val toolInstantiator: ToolInstantiator = NoArgToolInstantiator(),
    // Native support for OpenAI tools. Will be consolidated later after other providers are integrated into a unified approach
    private val openAINativeToolRuntime: OpenAINativeToolRuntime = NoOpOpenAINativeToolRuntime
) {
    private val component: AIComponent by lazy {
        DaggerAIComponent.builder()
            .config(config)
            .toolInstantiator(toolInstantiator)
            .openAINativeToolRuntime(openAINativeToolRuntime)
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

    fun audioRecorder(): AudioRecorder {
        return AudioRecorder()
    }
}
