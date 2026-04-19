package dev.tjpal.ai.di

import dagger.BindsInstance
import dagger.Component
import dev.tjpal.ai.embeddings.Embeddings
import dev.tjpal.ai.di.AIModule
import dev.tjpal.ai.LLM
import dev.tjpal.ai.openai.OpenAIConfig
import dev.tjpal.ai.openai.OpenAINativeToolRuntime
import dev.tjpal.ai.sandbox.Sandbox
import dev.tjpal.ai.sandbox.SandboxModule
import dev.tjpal.ai.tools.ToolInstantiator
import dev.tjpal.ai.tools.ToolsModule

@LibrarySingleton
@Component(modules = [AIModule::class, ToolsModule::class, SandboxModule::class])
interface AIComponent {
    fun llms(): Map<String, @JvmSuppressWildcards LLM>
    fun embeddings(): Map<String, @JvmSuppressWildcards Embeddings>
    fun sandbox(): Sandbox

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun config(config: OpenAIConfig): Builder

        @BindsInstance
        fun toolInstantiator(toolInstantiator: ToolInstantiator): Builder

        @BindsInstance
        fun openAINativeToolRuntime(openAINativeToolRuntime: OpenAINativeToolRuntime): Builder

        fun build(): AIComponent
    }
}
