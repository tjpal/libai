package dev.tjpal.ai.tools

import kotlin.reflect.KClass

sealed interface ToolDefinition {
    data class Function(val toolClass: KClass<out Tool>) : ToolDefinition

    data class Native(val type: String) : ToolDefinition {
        init {
            require(type.isNotBlank()) { "Native tool type must not be blank" }
        }
    }
}
