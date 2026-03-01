package dev.tjpal.ai.openai

import com.openai.client.OpenAIClient
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseInputItem
import com.openai.models.responses.ResponseOutputItem
import dev.tjpal.ai.messages.ExecutionContext
import dev.tjpal.ai.messages.Request
import dev.tjpal.ai.messages.RequestResponseChain
import dev.tjpal.ai.messages.Response
import dev.tjpal.ai.tools.ToolDefinition
import dev.tjpal.ai.tools.ToolRegistry
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory

class OpenAIRequestResponseChain(
    private val client: OpenAIClient,
    private val toolRegistry: ToolRegistry,
    private val gcStore: ResponsesGarbageCollector,
    private val nativeToolRuntime: OpenAINativeToolRuntime
) : RequestResponseChain() {
    private val logger = LoggerFactory.getLogger(OpenAIRequestResponseChain::class.java)
    private var conversationId: String? = null
    private var responseIDs = mutableListOf<String>()
    private var messages = mutableListOf<PersistedMessage>()

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class PersistedMessage(
        val role: String,
        val content: String
    )

    @Serializable
    private data class PersistedChainState(
        val version: Int = 1,
        val conversationId: String? = null,
        val responseIds: List<String> = emptyList(),
        val messages: List<PersistedMessage> = emptyList()
    )

    override fun persist(): String {
        val state = PersistedChainState(
            conversationId = conversationId,
            responseIds = responseIDs.toList(),
            messages = messages.toList()
        )

        return json.encodeToString(state)
    }

    override fun load(serializedState: String) {
        val state = try {
            json.decodeFromString<PersistedChainState>(serializedState)
        } catch (e: Exception) {
            throw IllegalArgumentException("Could not deserialize request-response chain state: ${e.message}", e)
        }

        if (state.version != 1) {
            throw IllegalArgumentException("Unsupported request-response chain state version: ${state.version}")
        }

        conversationId = state.conversationId
        responseIDs = state.responseIds.toMutableList()
        messages = state.messages.toMutableList()

        logger.debug(
            "Loaded request-response chain state conversationId={} responseCount={} messageCount={}",
            conversationId,
            responseIDs.size,
            messages.size
        )
    }

    override fun createResponse(request: Request, executionContext: ExecutionContext): Response {
        val conversationID = ensureConversationId()
        messages.add(PersistedMessage(role = "user", content = request.input))

        val initialUserMessage = initialUserMessage(request)

        var itemsToSend: List<ResponseInputItem> = listOf(initialUserMessage)
        var lastApiResponse: com.openai.models.responses.Response? = null

        while (true) {
            try {
                lastApiResponse = buildAndSend(itemsToSend, request, conversationID)
            } catch (e: Exception) {
                val msg = "Failed to create response for conversation $conversationID: ${e.message}"
                logger.error(msg, e)
                throw IllegalStateException(msg, e)
            }

            val currentResponse = requireNotNull(lastApiResponse)
            val responseId = currentResponse.id()
            responseIDs.add(responseId)

            // Create an entry in the garbage collection store. The client code is not required to delete responses/conversations.
            // Still, we would like to clear the backend entries at OpenAI.
            gcStore.append(GarbageCollectionEntry(System.currentTimeMillis(), responseId, conversationID))

            logger.debug("Received response id={} conversation={}", currentResponse.id(), conversationID)

            val toolOutputItems = processToolCalls(currentResponse.output(), request, executionContext)

            if (toolOutputItems.isEmpty()) {
                logger.debug("Final response reached id={}", currentResponse.id())
                break
            }

            itemsToSend = toolOutputItems
        }

        val finalMessage = extractMessage(requireNotNull(lastApiResponse))
        logger.debug("Final message extracted: {}", finalMessage.take(200))
        messages.add(PersistedMessage(role = "assistant", content = finalMessage))

        return Response(message = finalMessage)
    }

    override fun delete() {
        val conversationID = conversationId ?: run {
            logger.debug("OpenAIRequestResponseChain: No active conversation to delete")
            responseIDs.clear()
            messages.clear()
            return
        }

        logger.debug("OpenAIRequestResponseChain: Deleting responses for conversation {}", conversationID)

        val successfullyDeletedResponses = mutableSetOf<String>()

        responseIDs.forEach { responseID ->
            logger.info("OpenAIRequestResponseChain: Deleting response {}", responseID)

            try {
                client.responses().delete(responseID)
                successfullyDeletedResponses.add(responseID)
            } catch (e: Exception) {
                logger.error("Failed to delete response {}", responseID, e)
            }
        }

        gcStore.removeEntries(successfullyDeletedResponses, conversationID)

        if(successfullyDeletedResponses.size != responseIDs.size) {
            // In case the deletion of a response failed, there is no point in deleting the conversation.
            // Stop here. The garbage collector will take care of the pending entries next time it runs.
            return
        }

        try {
            logger.info("OpenAIRequestResponseChain: Deleting conversation {}", conversationID)
            client.conversations().delete(conversationID)
            logger.debug("Conversation {} deleted successfully", conversationID)
        } catch (e: Exception) {
            logger.error("Failed to delete conversation {}", conversationID, e)
        }

        this.conversationId = null
        responseIDs.clear()
        messages.clear()
    }

    private fun ensureConversationId(): String {
        val existingConversationId = conversationId
        if (existingConversationId != null) {
            return existingConversationId
        }

        val createdConversationId = client.conversations().create().id()
        conversationId = createdConversationId
        logger.debug("OpenAI conversation created id={}", createdConversationId)

        return createdConversationId
    }

    private fun initialUserMessage(request: Request): ResponseInputItem {
        return ResponseInputItem.ofMessage(
            ResponseInputItem.Message.builder()
                .addInputTextContent(request.input)
                .role(ResponseInputItem.Message.Role.USER)
                .build()
        )
    }

    private fun buildAndSend(
        items: List<ResponseInputItem>,
        request: Request,
        conversationID: String
    ): com.openai.models.responses.Response {
        val builder = ResponseCreateParams.builder()
            .input(ResponseCreateParams.Input.ofResponse(items))
            .instructions(request.instructions)
            .model(request.model)
            .conversation(conversationID)

        request.temperature?.let {
            logger.debug("Setting temperature to: {}", it)
            builder.temperature(it)
        }

        resolvedToolDefinitions(request).forEach { definition ->
            when (definition) {
                is ToolDefinition.Function -> {
                    val definitionName = toolRegistry.registerToolClass(definition.toolClass)
                    logger.debug(
                        "Adding function tool to response builder: class={} definition={}",
                        definition.toolClass.qualifiedName,
                        definitionName
                    )
                    builder.addTool(definition.toolClass.java)
                }
                is ToolDefinition.Native -> {
                    logger.debug("Adding native tool to response builder: type={}", definition.type)
                    nativeToolRuntime.addTool(builder, definition)
                }
            }
        }

        request.responseType?.let {
            logger.debug("Setting response type to: {}", it.java)
            builder.text(it.java)
        }

        request.topP?.let {
            logger.debug("Setting topP to: {}", it)
            builder.topP(it)
        }

        logger.debug("Creating OpenAI response for conversation={}", conversationID)

        return client.responses().create(builder.build())
    }

    private fun processToolCalls(
        outputItems: List<ResponseOutputItem>,
        request: Request,
        executionContext: ExecutionContext
    ): List<ResponseInputItem> {
        val nextItems = mutableListOf<ResponseInputItem>()

        for (item in outputItems) {
            if (item.isFunctionCall()) {
                val functionCall = item.asFunctionCall()
                val functionName = functionCall.name()
                logger.info("Model requested function call name={} callId={}", functionName, functionCall.callId())

                val parsedArguments: JsonElement? = try {
                    val rawArgs = functionCall.arguments()
                    json.parseToJsonElement(rawArgs)
                } catch (e: Exception) {
                    logger.error("Failed to parse function call arguments for {}: {}", functionName, e.message)
                    null
                }

                val staticParams: JsonElement? = resolveStaticParameters(functionName, request)

                val toolOutput: String = try {
                    logger.info(
                        "Invoking tool {} with arguments={} staticParams={}",
                        functionName,
                        parsedArguments,
                        staticParams
                    )
                    toolRegistry.invokeTool(functionName, parsedArguments, staticParams)
                } catch (e: Exception) {
                    val msg = "Tool invocation failed for $functionName: ${e.message}"
                    logger.error(msg, e)
                    throw IllegalStateException(msg, e)
                }

                logger.info(
                    "Tool {} executed successfully callId={} outputPreview={}",
                    functionName,
                    functionCall.callId(),
                    toolOutput.take(200)
                )

                nextItems.add(
                    ResponseInputItem.ofFunctionCallOutput(
                        ResponseInputItem.FunctionCallOutput.builder()
                            .callId(functionCall.callId())
                            .output(toolOutput)
                            .build()
                    )
                )

                continue
            }

            val nativeOutput = nativeToolRuntime.handleOutputItem(item, request, executionContext)
            if (nativeOutput != null) {
                nextItems.add(nativeOutput)
            }
        }

        return nextItems
    }

    private fun resolveStaticParameters(definitionName: String, request: Request): JsonElement? {
        request.toolStaticParametersByDefinitionName?.get(definitionName)?.let { return it }

        val byClass = request.toolStaticParametersByClass ?: return null
        for ((toolClass, staticParams) in byClass) {
            if (toolRegistry.getDefinitionName(toolClass) == definitionName) {
                return staticParams
            }
        }

        return null
    }

    private fun resolvedToolDefinitions(request: Request): List<ToolDefinition> {
        if (request.toolDefinitions.isEmpty()) {
            return request.tools.map { ToolDefinition.Function(it) }
        }

        val merged = request.toolDefinitions + request.tools.map { ToolDefinition.Function(it) }
        return merged.distinctBy { definition ->
            when (definition) {
                is ToolDefinition.Function -> {
                    val toolId = definition.toolClass.qualifiedName ?: definition.toolClass.toString()
                    "function:$toolId"
                }
                is ToolDefinition.Native -> "native:${definition.type}"
            }
        }
    }

    private fun extractMessage(response: com.openai.models.responses.Response): String {
        if (response.output().isEmpty()) {
            return ""
        }

        val stringBuilder = StringBuilder()

        response.output().forEach { outItem ->
            if (outItem.message().isPresent) {
                val message = outItem.message().get()

                message.content().forEach { content ->
                    val outputText = content.outputText()
                    if (outputText.isPresent) {
                        stringBuilder.append(outputText.get().text())
                        stringBuilder.append('\n')
                    }
                }
            }
        }

        return stringBuilder.toString().trimEnd()
    }
}
