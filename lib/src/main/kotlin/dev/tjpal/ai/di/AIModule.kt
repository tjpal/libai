package dev.tjpal.ai.di

import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import dev.tjpal.ai.LLM
import dev.tjpal.ai.embeddings.Embeddings
import dev.tjpal.ai.di.LLMProvider
import dev.tjpal.ai.openai.OpenAIEmbedding3Large
import dev.tjpal.ai.openai.OpenAIEmbedding3Small
import dev.tjpal.ai.openai.OpenAILLM

@Module
abstract class AIModule {
    @Binds
    @IntoMap
    @NodeFactoryKey(LLMProvider.Companion.OPENAI_PROVIDER_NAME)
    @LibrarySingleton
    abstract fun bindOpenAIService(impl: OpenAILLM): LLM

    @Binds
    @IntoMap
    @NodeFactoryKey("TEXT_EMBEDDING_3_LARGE")
    @LibrarySingleton
    abstract fun bindOpenAIEmbedding3Large(impl: OpenAIEmbedding3Large): Embeddings

    @Binds
    @IntoMap
    @NodeFactoryKey("TEXT_EMBEDDING_3_SMALL")
    @LibrarySingleton
    abstract fun bindOpenAIEmbedding3Small(impl: OpenAIEmbedding3Small): Embeddings
}
