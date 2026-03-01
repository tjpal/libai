package dev.tjpal.ai.messages

abstract class RequestResponseChain {
    open fun createResponse(request: Request): Response = createResponse(request, DefaultExecutionContext)
    abstract fun createResponse(request: Request, executionContext: ExecutionContext): Response
    abstract fun persist(): String
    abstract fun load(serializedState: String)
    abstract fun delete()
}
