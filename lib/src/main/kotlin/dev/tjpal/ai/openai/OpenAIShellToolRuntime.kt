package dev.tjpal.ai.openai

import com.openai.models.responses.FunctionShellTool
import com.openai.models.responses.LocalEnvironment
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseFunctionShellCallOutputContent
import com.openai.models.responses.ResponseInputItem
import com.openai.models.responses.ResponseOutputItem
import dev.tjpal.ai.messages.ExecutionContext
import dev.tjpal.ai.messages.Request
import dev.tjpal.ai.sandbox.CommandExecutionContext
import dev.tjpal.ai.sandbox.CommandExecutionResult
import dev.tjpal.ai.sandbox.LocalMachineCommandExecutionContext
import dev.tjpal.ai.tools.ToolDefinition
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory

data class OpenAIShellCommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val timedOut: Boolean = false
)

fun interface OpenAIShellCommandExecutor {
    fun execute(command: String, timeoutMs: Long?): OpenAIShellCommandResult
}

class OpenAIShellToolRuntime(
    private val defaultCommandExecutor: OpenAIShellCommandExecutor
) : OpenAINativeToolRuntime {
    private val logger = LoggerFactory.getLogger(OpenAIShellToolRuntime::class.java)

    constructor(
        workingDirectory: File = File("."),
        defaultTimeoutMs: Long = DEFAULT_TIMEOUT_MS
    ) : this(
        LocalMachineExecutor(
            workingDirectory = workingDirectory,
            defaultTimeoutMs = defaultTimeoutMs
        )
    )

    override fun addTool(builder: ResponseCreateParams.Builder, definition: ToolDefinition.Native) {
        require(definition.type == SHELL_TOOL_TYPE) {
            "OpenAIShellToolRuntime only supports native tool type '$SHELL_TOOL_TYPE', got '${definition.type}'."
        }

        builder.addTool(
            FunctionShellTool.builder()
                .environment(LocalEnvironment.builder().build())
                .build()
        )
    }

    override fun handleOutputItem(
        outputItem: ResponseOutputItem,
        request: Request,
        executionContext: ExecutionContext
    ): ResponseInputItem? {
        if (!outputItem.isShellCall()) {
            return null
        }

        val shellCall = outputItem.asShellCall()
        logger.info(
            "Model requested native shell call callId={} commandCount={}",
            shellCall.callId(),
            shellCall.action().commands().size
        )
        val environment = shellCall.environment().orElse(null)
        require(environment == null || environment.isLocal()) {
            "Shell call '${shellCall.callId()}' requested non-local environment '${environment?.toString()}'. " +
                "Only local shell execution is supported."
        }

        val outputBuilder = ResponseInputItem.ShellCallOutput.builder()
            .callId(shellCall.callId())
            .status(ResponseInputItem.ShellCallOutput.Status.COMPLETED)

        shellCall.action().maxOutputLength().ifPresent { outputBuilder.maxOutputLength(it) }

        val commandExecutor = resolveCommandExecutor(executionContext)

        shellCall.action().commands().forEach { command ->
            logger.info("Invoking native shell command callId={} command={}", shellCall.callId(), command)
            val result = commandExecutor.execute(command, shellCall.action().timeoutMs().orElse(null))
            logger.info(
                "Native shell command finished callId={} exitCode={} timedOut={} stdoutPreview={} stderrPreview={}",
                shellCall.callId(),
                result.exitCode,
                result.timedOut,
                result.stdout.take(200),
                result.stderr.take(200)
            )
            val contentBuilder = ResponseFunctionShellCallOutputContent.builder()
                .stdout(result.stdout)
                .stderr(result.stderr)

            if (result.timedOut) {
                contentBuilder.outcomeTimeout()
            } else {
                contentBuilder.exitOutcome(result.exitCode.toLong())
            }

            outputBuilder.addOutput(contentBuilder.build())
        }

        logger.info("Native shell call completed callId={}", shellCall.callId())
        return ResponseInputItem.ofShellCallOutput(outputBuilder.build())
    }

    private fun resolveCommandExecutor(executionContext: ExecutionContext): OpenAIShellCommandExecutor {
        if (executionContext is CommandExecutionContext) {
            return OpenAIShellCommandExecutor { command, timeoutMs ->
                executionContext.executeCommand(command, timeoutMs).toOpenAIShellCommandResult()
            }
        }

        return defaultCommandExecutor
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
        const val SHELL_TOOL_TYPE = "shell"
    }
}

class LocalMachineExecutor(
    private val workingDirectory: File = File("."),
    private val defaultTimeoutMs: Long = OpenAIShellToolRuntime.DEFAULT_TIMEOUT_MS
) : OpenAIShellCommandExecutor {
    override fun execute(command: String, timeoutMs: Long?): OpenAIShellCommandResult {
        val process = ProcessBuilder("sh", "-c", command)
            .directory(workingDirectory)
            .redirectErrorStream(false)
            .start()

        val stdout = AtomicReference("")
        val stderr = AtomicReference("")

        val stdoutReader = Thread {
            process.inputStream.bufferedReader().use {
                stdout.set(it.readText())
            }
        }
        val stderrReader = Thread {
            process.errorStream.bufferedReader().use {
                stderr.set(it.readText())
            }
        }

        stdoutReader.start()
        stderrReader.start()

        val effectiveTimeoutMs = timeoutMs ?: defaultTimeoutMs
        val finished = process.waitFor(effectiveTimeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            process.waitFor()
        }

        stdoutReader.join()
        stderrReader.join()

        return if (!finished) {
            OpenAIShellCommandResult(
                stdout = stdout.get(),
                stderr = stderr.get(),
                exitCode = 124,
                timedOut = true
            )
        } else {
            OpenAIShellCommandResult(
                stdout = stdout.get(),
                stderr = stderr.get(),
                exitCode = process.exitValue(),
                timedOut = false
            )
        }
    }
}

@Deprecated("Use LocalMachineExecutor")
typealias LocalOpenAIShellCommandExecutor = LocalMachineExecutor

@Deprecated("Use LocalMachineCommandExecutionContext")
typealias LocalOpenAIShellExecutionContext = LocalMachineCommandExecutionContext

private fun CommandExecutionResult.toOpenAIShellCommandResult(): OpenAIShellCommandResult {
    return OpenAIShellCommandResult(
        stdout = stdout,
        stderr = stderr,
        exitCode = exitCode,
        timedOut = timedOut
    )
}
