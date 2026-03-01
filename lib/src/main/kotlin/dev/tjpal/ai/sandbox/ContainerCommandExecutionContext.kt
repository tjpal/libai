package dev.tjpal.ai.sandbox

import java.time.Duration
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ContainerCommandExecutionContext private constructor(
    override val id: String,
    private val sandbox: Sandbox,
    private val environment: SandboxEnvironment
) : CommandExecutionContext {
    private val closed = AtomicBoolean(false)

    override fun executeCommand(command: String, timeoutMs: Long?): CommandExecutionResult {
        if (closed.get()) {
            throw IllegalStateException("Execution context '$id' is already closed.")
        }

        return try {
            val result = environment.runCommand(command, timeoutMs?.let(Duration::ofMillis))
            CommandExecutionResult(
                stdout = result.stdout,
                stderr = result.stderr,
                exitCode = result.exitCode,
                timedOut = false
            )
        } catch (_: TimeoutException) {
            CommandExecutionResult(
                stdout = "",
                stderr = "timed out",
                exitCode = 124,
                timedOut = true
            )
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }

        sandbox.shutdown(environment.id)
    }

    companion object {
        private val sequence = AtomicInteger(1)

        fun create(
            sandbox: Sandbox,
            config: ContainerSandboxConfig = ContainerSandboxConfig()
        ): ContainerCommandExecutionContext {
            val environment = sandbox.createContainerEnvironment(config)
            val contextId = "container-command-context-${sequence.getAndIncrement()}"
            return ContainerCommandExecutionContext(contextId, sandbox, environment)
        }
    }
}

@Deprecated("Use ContainerCommandExecutionContext")
typealias ContainerOpenAIShellExecutionContext = ContainerCommandExecutionContext
