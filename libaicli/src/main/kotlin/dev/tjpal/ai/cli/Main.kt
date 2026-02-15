package dev.tjpal.ai.cli

import dev.tjpal.ai.LibAI
import dev.tjpal.ai.messages.Request
import dev.tjpal.ai.messages.RequestResponseChain
import dev.tjpal.ai.openai.OpenAIConfig
import java.nio.file.Files
import kotlin.io.path.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.system.exitProcess

private val json = Json { ignoreUnknownKeys = true }

fun main(args: Array<String>) {
    val enableHelloTool = args.contains("--hello-tool")
    val positionalArgs = args.filterNot { it == "--hello-tool" }

    if (positionalArgs.size < 3) {
        System.err.println("Usage: libaicli <provider> <model> <message> [--hello-tool]")
        exitProcess(1)
    }

    val provider = positionalArgs[0]
    val model = positionalArgs[1]
    val message = positionalArgs.drop(2).joinToString(" ").trim()

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
        val libAI = LibAI(config)
        val llm = libAI.llm(provider)
        chain = llm.createResponseRequestChain()

        val instructions = if (enableHelloTool) {
            "You must call the hello_world tool exactly once before answering the user. " +
                "Include the tool output verbatim in the final answer."
        } else {
            "Answer the user message clearly and directly."
        }

        val response = chain.createResponse(
            Request(
                input = message,
                instructions = instructions,
                model = model,
                tools = if (enableHelloTool) listOf(HelloWorldTool::class) else emptyList(),
                toolStaticParametersByClass = if (enableHelloTool) {
                    mapOf(
                        HelloWorldTool::class to buildJsonObject {
                            put("greetingPrefix", "Hello")
                        }
                    )
                } else {
                    null
                }
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
