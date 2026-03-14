package dev.tjpal.ai.messages

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class RequestResponseChainAsyncTest {
    @Test
    fun createResponseAsync_await_returns_same_response() {
        val chain = TestChain(
            responseFactory = {
                Response("ok")
            }
        )

        val handle = chain.createResponseAsync(
            Request(
                input = "hi",
                instructions = "respond"
            )
        )

        val response = handle.await()
        assertEquals("ok", response.message)
        assertTrue(handle.isDone())
    }

    @Test
    fun createResponseAsync_cancel_causes_cancelled_exception() {
        val started = CountDownLatch(1)
        val chain = TestChain(
            responseFactory = {
                started.countDown()
                try {
                    while (true) {
                        Thread.sleep(10)
                    }
                } catch (_: InterruptedException) {
                    throw RequestCancelledException("Cancelled by test")
                }
                error("unreachable")
            }
        )

        val handle = chain.createResponseAsync(
            Request(
                input = "hi",
                instructions = "respond"
            )
        )

        assertTrue(started.await(1, TimeUnit.SECONDS), "Background response task did not start.")
        assertTrue(handle.cancel("test"))

        try {
            handle.await()
            fail("Expected RequestCancelledException")
        } catch (_: RequestCancelledException) {
            // expected
        }

        assertTrue(handle.isDone())
        assertFalse(handle.cancel("again"))
    }

    private class TestChain(
        private val responseFactory: () -> Response
    ) : RequestResponseChain() {
        override fun createResponse(request: Request, executionContext: ExecutionContext): Response {
            return responseFactory()
        }

        override fun persist(): String = ""

        override fun load(serializedState: String) {}

        override fun delete() {}
    }
}
