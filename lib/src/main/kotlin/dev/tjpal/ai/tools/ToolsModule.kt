package dev.tjpal.ai.tools

import dagger.Module
import dagger.Provides
import dev.tjpal.ai.di.LibrarySingleton

@Module
class ToolsModule {
    @Provides
    @LibrarySingleton
    fun provideToolRegistry(toolInstantiator: ToolInstantiator): ToolRegistry = ToolRegistry(toolInstantiator)
}
