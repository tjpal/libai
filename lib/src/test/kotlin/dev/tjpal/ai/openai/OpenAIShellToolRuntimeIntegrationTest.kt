package dev.tjpal.ai.openai

import com.github.dockerjava.core.DefaultDockerClientConfig
import dev.tjpal.ai.LibAI
import dev.tjpal.ai.di.LLMProvider
import dev.tjpal.ai.messages.Request
import dev.tjpal.ai.sandbox.ContainerCommandExecutionContext
import dev.tjpal.ai.sandbox.DockerClients
import dev.tjpal.ai.sandbox.Sandbox
import dev.tjpal.ai.tools.ToolDefinition
import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class OpenAIShellToolRuntimeIntegrationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun grepKeywordsFromTempFileUsingShellTool() {
        val configPath = Path(System.getProperty("user.home"), ".libai", "config.json")
        assumeTrue(
            Files.exists(configPath),
            "Skipping OpenAI shell tool integration test because $configPath is missing."
        )

        val config = json.decodeFromString<OpenAIConfig>(Files.readString(configPath))
        val llm = LibAI(
            config = config,
            openAINativeToolRuntime = OpenAIShellToolRuntime(workingDirectory = File("."))
        ).llm(LLMProvider.OPENAI)
        val chain = llm.createResponseRequestChain()

        val tempDir = Files.createTempDirectory("libai-shell-runtime-it-")
        val keywordsFile = tempDir.resolve("keywords.txt")
        Files.writeString(
            keywordsFile,
            """
            alpha appears here
            bravo appears here too
            charlie appears here
            alpha appears again
            """.trimIndent()
        )

        try {
            val response = chain.createResponse(
                Request(
                    input = """
                        Use the shell tool to grep in file ${keywordsFile.toAbsolutePath()} for alpha, charlie, and delta.
                        Return only unique matches as lowercase comma-separated values in alphabetical order.
                    """.trimIndent(),
                    instructions = "You must use the shell tool and respond with only comma-separated keywords.",
                    model = "gpt-5.1-codex-mini",
                    toolDefinitions = listOf(ToolDefinition.Native(OpenAIShellToolRuntime.SHELL_TOOL_TYPE))
                )
            )

            val normalized = response.message.trim().lowercase()
            val matchedKeywords = Regex("\\b(alpha|charlie|delta)\\b")
                .findAll(normalized)
                .map { it.value }
                .toSet()

            assertGrepResponse(response.message, matchedKeywords)
        } finally {
            chain.delete()
            Files.deleteIfExists(keywordsFile)
            Files.deleteIfExists(tempDir)
        }
    }

    @Test
    fun grepKeywordsFromTempFileUsingShellToolInContainer() {
        assumeTrue(
            System.getenv("LIBAI_RUN_CONTAINER_INTEGRATION_TESTS") == "true",
            "Skipping container integration test unless LIBAI_RUN_CONTAINER_INTEGRATION_TESTS=true."
        )
        val configPath = Path(System.getProperty("user.home"), ".libai", "config.json")
        assumeTrue(
            Files.exists(configPath),
            "Skipping OpenAI shell tool integration test because $configPath is missing."
        )

        val config = json.decodeFromString<OpenAIConfig>(Files.readString(configPath))
        val llm = LibAI(
            config = config,
            openAINativeToolRuntime = OpenAIShellToolRuntime(workingDirectory = File("."))
        ).llm(LLMProvider.OPENAI)
        val chain = llm.createResponseRequestChain()

        val dockerHost = resolveDockerHostForIntegrationTest()
        val dockerConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost(dockerHost)
            .build()
        val dockerClient = DockerClients.createClient(dockerConfig)
        assumeTrue(
            isDockerReachable(dockerClient),
            "Skipping container integration test because Docker is not reachable at $dockerHost."
        )
        val sandbox = Sandbox(dockerClient)
        val executionContext = ContainerCommandExecutionContext.create(sandbox)

        val tempDir = Files.createTempDirectory("libai-shell-runtime-container-it-")
        val keywordsFile = tempDir.resolve("keywords.txt")
        Files.writeString(
            keywordsFile,
            """
            alpha appears here
            bravo appears here too
            charlie appears here
            alpha appears again
            """.trimIndent()
        )

        try {
            try {
                val response = chain.createResponse(
                    Request(
                        input = """
                            Use the shell tool to grep in file ${keywordsFile.toAbsolutePath()} for alpha, charlie, and delta.
                            Return only unique matches as lowercase comma-separated values in alphabetical order.
                        """.trimIndent(),
                        instructions = "You must use the shell tool and respond with only comma-separated keywords.",
                        model = "gpt-5.1-codex-mini",
                        toolDefinitions = listOf(ToolDefinition.Native(OpenAIShellToolRuntime.SHELL_TOOL_TYPE))
                    ),
                    executionContext
                )

                val normalized = response.message.trim().lowercase()
                assumeTrue(
                    normalized != "error,file_missing",
                    "Skipping container integration test because sandbox containers cannot access host temp files."
                )
                val matchedKeywords = Regex("\\b(alpha|charlie|delta)\\b")
                    .findAll(normalized)
                    .map { it.value }
                    .toSet()

                assertGrepResponse(response.message, matchedKeywords)
            } catch (e: RuntimeException) {
                assumeTrue(
                    false,
                    "Skipping container integration test because container execution is unavailable: ${e.message}"
                )
            }
        } finally {
            executionContext.close()
            sandbox.close()
            chain.delete()
            Files.deleteIfExists(keywordsFile)
            Files.deleteIfExists(tempDir)
        }
    }

    private fun resolveDockerHostForIntegrationTest(): String {
        val explicit = System.getenv("DOCKER_HOST")?.takeIf { it.isNotBlank() }
        if (explicit != null) {
            return explicit
        }

        val isMac = System.getProperty("os.name").lowercase().contains("mac")
        assumeTrue(isMac, "Skipping container integration test because DOCKER_HOST is not configured.")

        runCommand("podman", "machine", "start")
        val inspect = runCommand(
            "podman",
            "machine",
            "inspect",
            "--format",
            "{{.ConnectionInfo.PodmanSocket.Path}}"
        )
        check(inspect.exitCode == 0) {
            "Failed to inspect Podman machine for socket path. stderr=${inspect.stderr.trim()}"
        }

        val socketPath = inspect.stdout.trim()
        check(socketPath.isNotBlank()) {
            "Podman machine did not report a socket path."
        }

        return "unix://$socketPath"
    }

    private fun assertGrepResponse(responseMessage: String, matchedKeywords: Set<String>) {
        assertTrue(responseMessage.isNotBlank(), "Expected non-empty response from OpenAI")
        assertTrue(
            matchedKeywords.containsAll(setOf("alpha", "charlie")),
            "Expected grep response to include alpha and charlie, got: $responseMessage"
        )

        if ("delta" in matchedKeywords) {
            val normalized = responseMessage.trim().lowercase()
            assertTrue(
                Regex("\\b(delta\\s+(is\\s+)?(missing|absent|not found)|" +
                    "(missing|absent|not found)\\s+delta)\\b").containsMatchIn(normalized),
                "Expected delta to be mentioned only as missing, got: $responseMessage"
            )
        }
    }

    private fun isDockerReachable(dockerClient: com.github.dockerjava.api.DockerClient): Boolean {
        return try {
            dockerClient.pingCmd().exec()
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun runCommand(vararg command: String): CommandResult {
        val process = ProcessBuilder(*command)
            .redirectErrorStream(false)
            .start()

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        return CommandResult(exitCode, stdout, stderr)
    }

    private data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    )
}
