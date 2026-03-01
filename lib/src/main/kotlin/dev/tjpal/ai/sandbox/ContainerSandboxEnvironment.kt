package dev.tjpal.ai.sandbox

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.StreamType
import com.github.dockerjava.core.command.PullImageResultCallback
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory

class ContainerSandboxEnvironment private constructor(
    override val id: String,
    private val dockerClient: DockerClient,
    private val containerId: String
) : SandboxEnvironment {
    private val logger = LoggerFactory.getLogger(ContainerSandboxEnvironment::class.java)
    private val isShutdown = AtomicBoolean(false)

    override fun runCommand(command: String, timeout: Duration?): SandboxCommandResult {
        if (isShutdown.get()) {
            throw IllegalStateException("Sandbox environment $id is already shut down.")
        }

        val exec = dockerClient.execCreateCmd(containerId)
            .withAttachStdout(true)
            .withAttachStderr(true)
            .withCmd("sh", "-lc", command)
            .exec()

        val callback = CommandResultCallback()
        dockerClient.execStartCmd(exec.id).exec(callback)

        if (timeout == null) {
            callback.awaitCompletion()
        } else {
            val completed = callback.awaitCompletion(timeout.toMillis(), TimeUnit.MILLISECONDS)
            if (!completed) {
                throw TimeoutException("Command timed out after ${timeout.toMillis()} ms in environment $id.")
            }
        }

        val exitCode = dockerClient.inspectExecCmd(exec.id).exec().exitCodeLong?.toInt() ?: -1

        return SandboxCommandResult(
            exitCode = exitCode,
            stdout = callback.stdout,
            stderr = callback.stderr
        )
    }

    override fun shutdown() {
        if (!isShutdown.compareAndSet(false, true)) {
            return
        }

        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec()
        } catch(e: Exception) {
            logger.warn("Failed to remove sandbox container id={} env={}: {}", containerId, id, e.message)
        }
    }

    private class CommandResultCallback : ResultCallback.Adapter<Frame>() {
        private val stdoutBuilder = StringBuilder()
        private val stderrBuilder = StringBuilder()

        override fun onNext(frame: Frame) {
            val payload = String(frame.payload ?: ByteArray(0), StandardCharsets.UTF_8)
            when (frame.streamType) {
                StreamType.STDERR -> stderrBuilder.append(payload)
                else -> stdoutBuilder.append(payload)
            }
        }

        val stdout: String
            get() = stdoutBuilder.toString()

        val stderr: String
            get() = stderrBuilder.toString()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ContainerSandboxEnvironment::class.java)
        private const val KEEP_ALIVE_COMMAND = "while true; do sleep 3600; done"

        fun create(
            dockerClient: DockerClient,
            config: ContainerSandboxConfig = ContainerSandboxConfig()
        ): ContainerSandboxEnvironment {
            if (config.pullImageOnCreate) {
                dockerClient.pullImageCmd(config.imageRepository)
                    .withTag(config.imageTag)
                    .exec(PullImageResultCallback())
                    .awaitCompletion()
            }

            val environmentId = "sandbox-${UUID.randomUUID()}"
            val container = createContainer(dockerClient, config.imageName, environmentId)

            dockerClient.startContainerCmd(container.id).exec()
            logger.info("Started sandbox environment={} container={}", environmentId, container.id)

            return ContainerSandboxEnvironment(
                id = environmentId,
                dockerClient = dockerClient,
                containerId = container.id
            )
        }

        private fun createContainer(
            dockerClient: DockerClient,
            imageName: String,
            environmentId: String
        ): CreateContainerResponse {
            return dockerClient.createContainerCmd(imageName)
                .withName(environmentId)
                .withCmd("sh", "-lc", KEEP_ALIVE_COMMAND)
                .withTty(false)
                .exec()
        }
    }
}
