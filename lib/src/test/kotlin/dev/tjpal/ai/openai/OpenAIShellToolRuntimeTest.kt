package dev.tjpal.ai.openai

import com.openai.models.responses.ResponseFunctionShellToolCall
import com.openai.models.responses.ResponseInputItem
import com.openai.models.responses.ResponseLocalEnvironment
import com.openai.models.responses.ResponseOutputItem
import dev.tjpal.ai.messages.Request
import dev.tjpal.ai.tools.ToolDefinition
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class OpenAIShellToolRuntimeTest {
    @Test
    fun `addTool rejects unsupported native tool type`() {
        val runtime = OpenAIShellToolRuntime { _, _ -> OpenAIShellCommandResult("", "", 0) }

        assertFailsWith<IllegalArgumentException> {
            runtime.addTool(
                com.openai.models.responses.ResponseCreateParams.builder(),
                ToolDefinition.Native("apply_patch")
            )
        }
    }

    @Test
    fun `handleOutputItem converts shell call to shell call output`() {
        val runtime = OpenAIShellToolRuntime { command, timeoutMs ->
            when (command) {
                "echo ok" -> OpenAIShellCommandResult(stdout = "ok\n", stderr = "", exitCode = 0)
                else -> OpenAIShellCommandResult(
                    stdout = "",
                    stderr = "timed out",
                    exitCode = 124,
                    timedOut = timeoutMs == 1000L
                )
            }
        }

        val shellCall = ResponseFunctionShellToolCall.builder()
            .id("shell_call_1")
            .callId("call_123")
            .environment(ResponseLocalEnvironment.builder().build())
            .status(ResponseFunctionShellToolCall.Status.COMPLETED)
            .action(
                ResponseFunctionShellToolCall.Action.builder()
                    .commands(listOf("echo ok", "sleep 10"))
                    .maxOutputLength(4096L)
                    .timeoutMs(1000L)
                    .build()
            )
            .build()

        val inputItem = runtime.handleOutputItem(
            ResponseOutputItem.ofShellCall(shellCall),
            Request(input = "x", instructions = "y")
        )

        assertNotNull(inputItem)
        val shellOutput = inputItem.asShellCallOutput()
        assertEquals("call_123", shellOutput.callId())
        assertEquals(ResponseInputItem.ShellCallOutput.Status.COMPLETED, shellOutput.status().get())
        assertEquals(2, shellOutput.output().size)
        assertEquals("ok\n", shellOutput.output()[0].stdout())
        assertEquals("timed out", shellOutput.output()[1].stderr())
        assertEquals(true, shellOutput.output()[1].outcome().isTimeout())
    }

    @Test
    fun `handleOutputItem rejects non-local shell call environments`() {
        val runtime = OpenAIShellToolRuntime { _, _ -> OpenAIShellCommandResult("", "", 0) }

        val shellCall = ResponseFunctionShellToolCall.builder()
            .id("shell_call_2")
            .callId("call_456")
            .containerReferenceEnvironment("ctr_123")
            .status(ResponseFunctionShellToolCall.Status.COMPLETED)
            .action(
                ResponseFunctionShellToolCall.Action.builder()
                    .commands(listOf("pwd"))
                    .maxOutputLength(4_096L)
                    .timeoutMs(1_000L)
                    .build()
            )
            .build()

        assertFailsWith<IllegalArgumentException> {
            runtime.handleOutputItem(
                ResponseOutputItem.ofShellCall(shellCall),
                Request(input = "x", instructions = "y")
            )
        }
    }

    @Test
    fun `default local executor executes shell commands locally`() {
        val runtime = OpenAIShellToolRuntime(workingDirectory = File("."))

        val shellCall = ResponseFunctionShellToolCall.builder()
            .id("shell_call_3")
            .callId("call_789")
            .environment(ResponseLocalEnvironment.builder().build())
            .status(ResponseFunctionShellToolCall.Status.COMPLETED)
            .action(
                ResponseFunctionShellToolCall.Action.builder()
                    .commands(listOf("printf ok"))
                    .maxOutputLength(4_096L)
                    .timeoutMs(1_000L)
                    .build()
            )
            .build()

        val inputItem = runtime.handleOutputItem(
            ResponseOutputItem.ofShellCall(shellCall),
            Request(input = "x", instructions = "y")
        )

        assertNotNull(inputItem)
        val shellOutput = inputItem.asShellCallOutput()
        assertEquals(1, shellOutput.output().size)
        assertEquals("ok", shellOutput.output()[0].stdout())
        assertEquals(0L, shellOutput.output()[0].outcome().asExit().exitCode())
    }
}
