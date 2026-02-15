package dev.tjpal.ai.tools

import com.fasterxml.jackson.annotation.JsonTypeName
import dev.tjpal.ai.di.LibrarySingleton
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * Registry for tool classes, allowing automatic registration and invocation by definition name.
 */
@LibrarySingleton
class ToolRegistry @Inject constructor(
    private val toolInstantiator: ToolInstantiator
) {
    private val logger = LoggerFactory.getLogger(ToolRegistry::class.java)

    private val toolsByDefinitionName: ConcurrentHashMap<String, KClass<out Tool>> = ConcurrentHashMap()

    fun registerToolClass(toolClass: KClass<out Tool>): String {
        val definitionName = getDefinitionName(toolClass)
        val existing = toolsByDefinitionName.putIfAbsent(definitionName, toolClass)

        if (existing != null && existing != toolClass) {
            throw IllegalStateException(
                "Tool definition name '$definitionName' is already registered for " +
                    "${existing.qualifiedName}. Refusing to overwrite with ${toolClass.qualifiedName}."
            )
        }

        logger.debug(
            "Registered tool class={} for definition={}",
            toolClass.qualifiedName,
            definitionName
        )
        return definitionName
    }

    /**
     * Invoke the tool by definition name.
     *
     * @param definitionName logical name of the tool definition
     * @param arguments JSON element parsed from the model's function call (nullable)
     * @param staticParameters JSON element representing static parameters for the tool (nullable)
     * @return the string output produced by the tool's execute() method
     */
    fun invokeTool(definitionName: String, arguments: JsonElement?, staticParameters: JsonElement?): String {
        val toolClass = toolsByDefinitionName[definitionName]
            ?: throw IllegalStateException("No tool class registered for definition name: $definitionName")

        try {
            val tool = toolInstantiator.instantiate(toolClass)
            val out = tool.execute(
                ToolExecutionContext(
                    definitionName = definitionName,
                    arguments = arguments,
                    staticParameters = staticParameters
                )
            )

            logger.debug("Tool executed for definition={} output={}...", definitionName, out)
            return out
        } catch (e: Exception) {
            val msg = "Tool execution failed for $definitionName: ${e.message}"
            logger.error(msg, e)
            throw IllegalStateException(msg, e)
        }
    }

    /**
     * Returns the KClass of the tool for a given definition name. Class is passed to
     * OpenAI API so it can extract the data from the annotations.
     */
    fun getToolClass(definitionName: String): KClass<out Tool>? {
        return toolsByDefinitionName[definitionName]
    }

    fun getDefinitionName(toolClass: KClass<out Tool>): String {
        val fromAnnotation = toolClass.java.getAnnotation(JsonTypeName::class.java)
            ?.value
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        if (fromAnnotation != null) {
            return fromAnnotation
        }

        return toolClass.simpleName
            ?: throw IllegalStateException("Tool class ${toolClass.qualifiedName} has no simpleName")
    }
}
