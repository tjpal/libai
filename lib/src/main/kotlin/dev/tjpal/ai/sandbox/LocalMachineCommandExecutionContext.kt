package dev.tjpal.ai.sandbox

import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class LocalMachineCommandExecutionContext(
    override val id: String = "local-machine-command-context",
    private val workingDirectory: File = File("."),
    private val defaultTimeoutMs: Long = 30_000L
) : CommandExecutionContext {
    override fun executeCommand(command: String, timeoutMs: Long?): CommandExecutionResult {
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
            CommandExecutionResult(
                stdout = stdout.get(),
                stderr = stderr.get(),
                exitCode = 124,
                timedOut = true
            )
        } else {
            CommandExecutionResult(
                stdout = stdout.get(),
                stderr = stderr.get(),
                exitCode = process.exitValue(),
                timedOut = false
            )
        }
    }
}
