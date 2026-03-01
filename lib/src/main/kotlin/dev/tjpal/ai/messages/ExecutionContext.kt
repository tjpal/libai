package dev.tjpal.ai.messages

interface ExecutionContext : AutoCloseable {
    val id: String

    override fun close() {}
}

object DefaultExecutionContext : ExecutionContext {
    override val id: String = "default-execution-context"
}
