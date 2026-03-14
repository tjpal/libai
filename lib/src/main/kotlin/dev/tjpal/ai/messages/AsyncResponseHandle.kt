package dev.tjpal.ai.messages

import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future

interface AsyncResponseHandle {
    fun await(): Response
    fun isDone(): Boolean
    fun cancel(reason: String? = null): Boolean
}

class RequestCancelledException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

internal class FutureAsyncResponseHandle(
    private val future: Future<Response>,
    private val cancelAction: (String?) -> Boolean = { future.cancel(true) }
) : AsyncResponseHandle {
    override fun await(): Response {
        try {
            return future.get()
        } catch (e: CancellationException) {
            throw RequestCancelledException("Request was cancelled.", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RuntimeException("Interrupted while awaiting response.", e)
        } catch (e: ExecutionException) {
            val cause = e.cause
            if (cause is RequestCancelledException) {
                throw cause
            }
            throw (cause ?: e)
        }
    }

    override fun isDone(): Boolean = future.isDone

    override fun cancel(reason: String?): Boolean = cancelAction(reason)
}
