package dev.tjpal.ai.openai

import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseInputItem
import com.openai.models.responses.ResponseOutputItem
import dev.tjpal.ai.messages.Request
import dev.tjpal.ai.tools.ToolDefinition

interface OpenAINativeToolRuntime {
    fun addTool(builder: ResponseCreateParams.Builder, definition: ToolDefinition.Native)
    fun handleOutputItem(outputItem: ResponseOutputItem, request: Request): ResponseInputItem?
}

object NoOpOpenAINativeToolRuntime : OpenAINativeToolRuntime {
    override fun addTool(builder: ResponseCreateParams.Builder, definition: ToolDefinition.Native) {
        throw IllegalStateException(
            "Native tool '${definition.type}' requested, but no OpenAINativeToolRuntime was configured. " +
                "Provide a custom runtime via LibAI(..., openAINativeToolRuntime = ...)."
        )
    }

    override fun handleOutputItem(outputItem: ResponseOutputItem, request: Request): ResponseInputItem? = null
}
