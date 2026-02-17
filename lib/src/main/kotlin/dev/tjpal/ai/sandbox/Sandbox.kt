package dev.tjpal.ai.sandbox

import com.github.dockerjava.api.DockerClient
import dev.tjpal.ai.di.LibrarySingleton
import javax.inject.Inject
import java.util.concurrent.ConcurrentHashMap

@LibrarySingleton
class Sandbox @Inject constructor(
    private val dockerClient: DockerClient
) : AutoCloseable {
    private val environmentsById = ConcurrentHashMap<String, SandboxEnvironment>()

    fun createContainerEnvironment(config: ContainerSandboxConfig = ContainerSandboxConfig()): SandboxEnvironment {
        val environment = ContainerSandboxEnvironment.create(dockerClient, config)
        environmentsById[environment.id] = environment

        return environment
    }

    fun shutdown(environmentId: String): Boolean {
        val environment = environmentsById.remove(environmentId) ?: return false
        environment.shutdown()

        return true
    }

    fun shutdownAll() {
        val environmentIds = environmentsById.keys.toList()

        environmentIds.forEach { id ->
            shutdown(id)
        }
    }

    override fun close() {
        shutdownAll()
        dockerClient.close()
    }
}
