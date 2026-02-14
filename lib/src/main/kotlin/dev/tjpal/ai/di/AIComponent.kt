package dev.tjpal.ai.di

import dagger.BindsInstance
import dagger.Component
import dev.tjpal.ai.di.AIModule
import dev.tjpal.ai.LLM
import dev.tjpal.ai.openai.OpenAIConfig
import dev.tjpal.ai.tools.ToolsModule

@LibrarySingleton
@Component(modules = [AIModule::class, ToolsModule::class])
interface AIComponent {
    fun llms(): Map<String, @JvmSuppressWildcards LLM>

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun config(config: OpenAIConfig): Builder

        fun build(): AIComponent
    }
}
