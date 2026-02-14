package dev.tjpal.ai.tools

import dev.tjpal.ai.di.LibrarySingleton
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * Registry for tool factories, allowing registration and invocation of tools by definition name.
 */
@LibrarySingleton
class ToolRegistry @Inject constructor() {
    private val logger = LoggerFactory.getLogger(ToolRegistry::class.java)

    private val factories: ConcurrentHashMap<String, ToolFactory> = ConcurrentHashMap()

    fun registerFactory(definitionName: String, factory: ToolFactory) {
        factories[definitionName] = factory
        logger.debug("Registered tool factory for definition={}", definitionName)
    }

    /**
     * Invoke the tool by definition name.
     *
     * @param definitionName logical name of the tool definition
     * @param arguments JSON element parsed from the model's function call (nullable)
     * @param nodeParameters JSON element representing node-level parameters (nullable)
     * @return the string output produced by the tool's execute() method
     */
    fun invokeTool(definitionName: String, arguments: JsonElement?, nodeParameters: JsonElement?): String {
        val factory = factories[definitionName] ?: throw IllegalStateException("No tool factory registered for definition name: $definitionName")

        try {
            val tool = factory.create(arguments, nodeParameters)
            val out = tool.execute()

            logger.debug("Tool factory executed for definition={} output={}...", definitionName, out)
            return out
        } catch (e: Exception) {
            val msg = "Tool factory failed for $definitionName: ${e.message}"
            logger.error(msg, e)
            throw IllegalStateException(msg, e)
        }
    }

    /**
     * Returns the KClass of the tool for a given definition name. Class is passed to
     * OpenAI API so it can extract the data from the annotations.
     */
    fun getToolClass(definitionName: String): KClass<out Tool>? {
        val factory = factories[definitionName]
        return factory?.toolClass
    }
}
