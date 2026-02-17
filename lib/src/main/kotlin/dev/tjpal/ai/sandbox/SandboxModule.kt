package dev.tjpal.ai.sandbox

import com.github.dockerjava.api.DockerClient
import dagger.Module
import dagger.Provides
import dev.tjpal.ai.di.LibrarySingleton

@Module
class SandboxModule {
    @Provides
    @LibrarySingleton
    fun provideDockerClient(): DockerClient = DockerClients.createClient()
}
