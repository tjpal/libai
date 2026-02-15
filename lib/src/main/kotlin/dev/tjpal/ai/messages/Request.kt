package dev.tjpal.ai.messages

import dev.tjpal.ai.tools.Tool
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KClass

data class Request(
    val input: String,
    val instructions: String,
    val model: String = "gpt-5-mini",
    val responseType: KClass<*>? = null,
    val tools: List<KClass<out Tool>> = emptyList(),
    val temperature: Double = 1.0,
    val topP: Double? = null,
    val toolStaticParametersByClass: Map<KClass<out Tool>, JsonElement>? = null
)
