package dev.tjpal.ai.tools

import dev.tjpal.ai.messages.DefaultExecutionContext
import dev.tjpal.ai.messages.ExecutionContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

interface Tool {
    fun execute(context: ToolExecutionContext): String
}

data class ToolExecutionContext(
    val definitionName: String,
    val arguments: JsonElement? = null,
    val staticParameters: JsonElement? = null,
    val executionContext: ExecutionContext = DefaultExecutionContext
) {
    inline fun <reified T : Any> argument(path: String): T? = get(arguments, path, T::class)

    inline fun <reified T : Any> staticParameter(path: String): T? = get(staticParameters, path, T::class)

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(source: JsonElement?, path: String, type: kotlin.reflect.KClass<T>): T? {
        val leaf = resolvePath(source, path) ?: return null

        return when (type) {
            String::class -> (leaf as? JsonPrimitive)?.contentOrNull as T?
            Int::class -> (leaf as? JsonPrimitive)?.intOrNull as T?
            Long::class -> (leaf as? JsonPrimitive)?.longOrNull as T?
            Double::class -> (leaf as? JsonPrimitive)?.doubleOrNull as T?
            Boolean::class -> (leaf as? JsonPrimitive)?.booleanOrNull as T?
            JsonElement::class -> leaf as T
            JsonPrimitive::class -> (leaf as? JsonPrimitive) as T?
            JsonObject::class -> (leaf as? JsonObject) as T?
            else -> null
        }
    }

    private fun resolvePath(source: JsonElement?, path: String): JsonElement? {
        if (source == null) return null
        if (path.isBlank()) return source

        return path.split('.')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .fold(source) { current, segment ->
                (current as? JsonObject)?.get(segment) ?: return null
            }
    }
}
