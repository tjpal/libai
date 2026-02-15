package dev.tjpal.ai.cli

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.fasterxml.jackson.annotation.JsonTypeName
import dev.tjpal.ai.tools.Tool
import dev.tjpal.ai.tools.ToolExecutionContext

const val HELLO_WORLD_DEFINITION_NAME = "hello_world"

@JsonTypeName(HELLO_WORLD_DEFINITION_NAME)
@JsonClassDescription("Generate a hello-style greeting for the provided name.")
class HelloWorldTool : Tool {
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
