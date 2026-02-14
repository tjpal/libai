package dev.tjpal.ai.cli

import dev.tjpal.ai.LibAI
import dev.tjpal.ai.messages.Request
import dev.tjpal.ai.messages.RequestResponseChain
import dev.tjpal.ai.openai.OpenAIConfig
import java.nio.file.Files
import kotlin.io.path.Path
import kotlinx.serialization.json.Json
import kotlin.system.exitProcess

private val json = Json { ignoreUnknownKeys = true }

fun main(args: Array<String>) {
    if (args.size < 3) {
        System.err.println("Usage: libaicli <provider> <model> <message>")
        exitProcess(1)
    }

    val provider = args[0]
    val model = args[1]
    val message = args.drop(2).joinToString(" ").trim()

    if (message.isEmpty()) {
        System.err.println("Error: Message must not be empty")
        exitProcess(1)
    }

    val configPath = resolveConfigPath()
    val config = try {
        loadConfig(configPath)
    } catch (e: Exception) {
        System.err.println("Error: Failed to load config from $configPath: ${e.message}")
        exitProcess(1)
    }

    var chain: RequestResponseChain? = null

    try {
        val llm = LibAI(config).llm(provider)
        chain = llm.createResponseRequestChain()

        val response = chain.createResponse(
            Request(
                input = message,
                instructions = "Answer the user message clearly and directly.",
                model = model
            )
        )

        println(response.message)
    } catch (e: Exception) {
        System.err.println("Error: ${e.message}")
        exitProcess(1)
    } finally {
        if (chain != null) {
            runCatching { chain.delete() }
                .onFailure { cleanupError ->
                    System.err.println("Error: Failed to clean up response chain: ${cleanupError.message}")
                }
        }
    }
}

private fun resolveConfigPath(): String {
    val fromEnv = System.getenv("LIBAI_CONFIG")?.trim()
    if (!fromEnv.isNullOrEmpty()) {
        return fromEnv
    }

    return Path(System.getProperty("user.home"), ".libai", "config.json").toString()
}

private fun loadConfig(configPath: String): OpenAIConfig {
    val filePath = Path(configPath)
    check(Files.exists(filePath)) { "Config file does not exist" }

    return json.decodeFromString<OpenAIConfig>(Files.readString(filePath))
}
