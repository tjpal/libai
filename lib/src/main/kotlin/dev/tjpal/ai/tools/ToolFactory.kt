package dev.tjpal.ai.tools

import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KClass

interface ToolFactory {
    /**
     * Used to pass the class definition to OpenAI API so it can extract the data from the annotations.
     */
    val toolClass: KClass<out Tool>

    /**
     * Create a Tool instance.

     * @param arguments JSON provided by the model function call (may be null or empty JSON string parsed to JsonElement)
     * @param nodeParameters Node-level parameters associated with the tool node (nullable)
     */
    fun create(arguments: JsonElement? = null, nodeParameters: JsonElement? = null): Tool
}
