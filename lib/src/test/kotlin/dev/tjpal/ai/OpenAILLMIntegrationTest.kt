package dev.tjpal.ai

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.fasterxml.jackson.annotation.JsonTypeName
import dev.tjpal.ai.di.LLMProvider
import dev.tjpal.ai.messages.Request
import dev.tjpal.ai.openai.OpenAIConfig
import dev.tjpal.ai.tools.Tool
import dev.tjpal.ai.tools.ToolExecutionContext
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.assertContains

class OpenAILLMIntegrationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun createsResponseUsingGpt5Nano() {
        val configPath = Path(System.getProperty("user.home"), ".libai", "config.json")
        check(Files.exists(configPath)) { "Missing config file: $configPath" }

        val config = json.decodeFromString<OpenAIConfig>(Files.readString(configPath))
        val llm = LibAI(config).llm(LLMProvider.OPENAI)
        val chain = llm.createResponseRequestChain()

        try {
            val response = chain.createResponse(
                Request(
                    input = "Please explain C++ in two sentences.",
                    instructions = "Answer in exactly two sentences.",
                    model = "gpt-5-nano"
                )
            )

            assertTrue(response.message.isNotBlank(), "Expected non-empty response from OpenAI")
            assertContains(response.message, "C++")
            println("Received response: ${response.message}")
        } finally {
            chain.delete()
        }
    }

    @Test
    fun createsResponseUsingToolCall() {
        val configPath = Path(System.getProperty("user.home"), ".libai", "config.json")
        check(Files.exists(configPath)) { "Missing config file: $configPath" }

        val config = json.decodeFromString<OpenAIConfig>(Files.readString(configPath))
        val llm = LibAI(config).llm(LLMProvider.OPENAI)
        val chain = llm.createResponseRequestChain()

        try {
            val response = chain.createResponse(
                Request(
                    input = "Call the hello_world tool with name World, then give the final answer.",
                    instructions = "You must call hello_world exactly once and include its output verbatim.",
                    model = "gpt-5-nano",
                    tools = listOf(IntegrationHelloWorldTool::class),
                    toolStaticParametersByClass = mapOf(
                        IntegrationHelloWorldTool::class to buildJsonObject {
                            put("greetingPrefix", "Hello")
                        }
                    )
                )
            )

            assertTrue(response.message.isNotBlank(), "Expected non-empty response from OpenAI")
            assertContains(response.message, "Hello, World!")
            println("Received response with tool output: ${response.message}")
        } finally {
            chain.delete()
        }
    }

    @Test
    fun synthesizesAndTranscribesAudioRoundTrip() {
        val configPath = Path(System.getProperty("user.home"), ".libai", "config.json")
        check(Files.exists(configPath)) { "Missing config file: $configPath" }

        val config = json.decodeFromString<OpenAIConfig>(Files.readString(configPath))
        val llm = LibAI(config).llm(LLMProvider.OPENAI)

        val expectedText = "This is a speech round trip integration test"
        val audioFile = Files.createTempFile("libai-tts-roundtrip-", ".mp3")

        try {
            llm.synthesizeSpeech(expectedText, audioFile.toString())

            val audioSizeBytes = Files.size(audioFile)
            assertTrue(audioSizeBytes > 1024, "Expected generated audio file to be larger than 1KB")

            val transcribedText = llm.transcriptAudio(audioFile.toString())
            println("Transcribed text: $transcribedText")

            assertEquals(normalizeForComparison(expectedText), normalizeForComparison(transcribedText))
        } finally {
            Files.deleteIfExists(audioFile)
        }
    }

    @JsonTypeName("hello_world")
    @JsonClassDescription("Generate a hello-style greeting for the provided name.")
    class IntegrationHelloWorldTool : Tool {
        @get:JsonPropertyDescription("Name to greet. If omitted, defaults to World.")
        @Suppress("unused")
        val name: String? = null

        override fun execute(context: ToolExecutionContext): String {
            val nameFromArgs = context.argument<String>("name")
            val greetingPrefix = context.staticParameter<String>("greetingPrefix")
                ?.takeIf { it.isNotBlank() }
                ?: "Hello"

            val target = nameFromArgs?.trim().takeUnless { it.isNullOrEmpty() } ?: "World"
            return "$greetingPrefix, $target!"
        }
    }

    private fun normalizeForComparison(input: String): String {
        return input
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
