package dev.tjpal.ai.messages

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory

abstract class RequestResponseChain {
    companion object {
        private val asyncExecutor = Executors.newCachedThreadPool(
            ThreadFactory { runnable ->
                Thread(runnable, "libai-request-response-async").apply { isDaemon = true }
            }
        )
    }

    open fun createResponse(request: Request): Response = createResponse(request, DefaultExecutionContext)
    open fun createResponseAsync(
        request: Request,
        executionContext: ExecutionContext = DefaultExecutionContext
    ): AsyncResponseHandle = FutureAsyncResponseHandle(
        submitAsync {
            createResponse(request, executionContext)
        }
    )

    protected fun submitAsync(task: () -> Response): Future<Response> = asyncExecutor.submit(Callable(task))

    abstract fun createResponse(request: Request, executionContext: ExecutionContext): Response
    abstract fun persist(): String
    abstract fun load(serializedState: String)
    abstract fun delete()
}
