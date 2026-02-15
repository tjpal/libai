package dev.tjpal.ai.tools

import kotlin.reflect.KClass

interface ToolInstantiator {
    fun instantiate(toolClass: KClass<out Tool>): Tool
}

/**
 * Default tool instantiator using a no-arg constructor via reflection.
 */
class NoArgToolInstantiator : ToolInstantiator {
    override fun instantiate(toolClass: KClass<out Tool>): Tool {
        val ctor = toolClass.java.declaredConstructors.firstOrNull { it.parameterCount == 0 }
            ?: throw IllegalStateException(
                "Tool ${toolClass.qualifiedName} must expose a no-arg constructor " +
                    "or provide a custom ToolInstantiator."
            )

        try {
            ctor.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            return ctor.newInstance() as Tool
        } catch (e: Exception) {
            throw IllegalStateException("Failed to instantiate tool ${toolClass.qualifiedName}: ${e.message}", e)
        }
    }
}
