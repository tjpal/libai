package dev.tjpal.ai.tools

import dev.tjpal.ai.messages.ExecutionContext
import kotlin.test.Test
import kotlin.test.assertEquals

class ToolRegistryExecutionContextTest {
    @Test
    fun invokeTool_passesExecutionContextToTool() {
        val registry = ToolRegistry(NoArgToolInstantiator())
        val definitionName = registry.registerToolClass(ExecutionContextEchoTool::class)

        val context = object : ExecutionContext {
            override val id: String = "request-ctx-123"
        }

        val result = registry.invokeTool(
            definitionName = definitionName,
            arguments = null,
            staticParameters = null,
            executionContext = context
        )

        assertEquals("request-ctx-123", result)
    }

    class ExecutionContextEchoTool : Tool {
        override fun execute(context: ToolExecutionContext): String {
            return context.executionContext.id
        }
    }
}
