package dev.tjpal.ai.di

import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import dev.tjpal.ai.LLM
import dev.tjpal.ai.di.LLMProvider
import dev.tjpal.ai.openai.OpenAILLM

@Module
abstract class AIModule {
    @Binds
    @IntoMap
    @NodeFactoryKey(LLMProvider.Companion.OPENAI_PROVIDER_NAME)
    @LibrarySingleton
    abstract fun bindOpenAIService(impl: OpenAILLM): LLM
}