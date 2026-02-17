package dev.tjpal.ai.sandbox

import java.time.Duration

interface SandboxEnvironment : AutoCloseable {
    val id: String

    fun runCommand(command: String, timeout: Duration? = null): SandboxCommandResult

    fun shutdown()

    override fun close() {
        shutdown()
    }
}
