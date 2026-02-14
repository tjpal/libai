package dev.tjpal.ai

import dev.tjpal.ai.di.LLMProvider
import dev.tjpal.ai.messages.Request
import dev.tjpal.ai.openai.OpenAIConfig
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
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
}
