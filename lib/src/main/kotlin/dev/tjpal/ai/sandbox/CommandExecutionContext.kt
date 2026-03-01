package dev.tjpal.ai.sandbox

import dev.tjpal.ai.messages.ExecutionContext

data class CommandExecutionResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val timedOut: Boolean = false
)

interface CommandExecutionContext : ExecutionContext {
    fun executeCommand(command: String, timeoutMs: Long?): CommandExecutionResult
}
