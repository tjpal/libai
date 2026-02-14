package dev.tjpal.ai.openai

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.audio.AudioModel
import com.openai.models.audio.transcriptions.TranscriptionCreateParams
import dev.tjpal.ai.LLM
import dev.tjpal.ai.messages.RequestResponseChain
import dev.tjpal.ai.di.LibrarySingleton
import dev.tjpal.ai.tools.ToolRegistry
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import javax.inject.Inject
import kotlin.io.path.Path
import org.slf4j.LoggerFactory

@LibrarySingleton
class OpenAILLM @Inject constructor(
    private val config: OpenAIConfig,
    private val toolRegistry: ToolRegistry
) : LLM {
    private val logger = LoggerFactory.getLogger(OpenAILLM::class.java)
    private val client = buildClientFromFile()
    private val garbageCollectionStore = ResponsesGarbageCollector(Path(config.openAIGarbageCollectorPath))

    private fun buildClientFromFile(): OpenAIClient {
        try {
            val key = Files.readString(Paths.get(config.openAICredentialPath)).trim { it <= ' ' }

            return OpenAIOkHttpClient.builder().apiKey(key).build()
        } catch (e: IOException) {
            logger.error("Failed to read OpenAI API key from {}", config.openAICredentialPath, e)
            throw IllegalStateException("Failed to read OpenAI API key from ${config.openAICredentialPath}", e)
        }
    }

    override fun createResponseRequestChain(): RequestResponseChain {
        logger.debug("OpenAI RequestResponseChain created")
        return OpenAIRequestResponseChain(client, toolRegistry, garbageCollectionStore)
    }

    override fun transcriptAudio(filePath: String): String {
        logger.info("Transcribing audio file={}", filePath)
        val vadConfig = TranscriptionCreateParams.ChunkingStrategy.VadConfig.builder().
            type(TranscriptionCreateParams.ChunkingStrategy.VadConfig.Type.SERVER_VAD).
            silenceDurationMs(5000).
            threshold(0.0).
            build()

        val createParams: TranscriptionCreateParams = TranscriptionCreateParams.builder().
            file(Path(filePath)).
            model(AudioModel.Companion.GPT_4O_MINI_TRANSCRIBE).
            chunkingStrategy(vadConfig).
            build()

        val transcriptionResponse = client.
            audio().
            transcriptions().
            create(createParams)

        val transcription = transcriptionResponse.asTranscription()

        val text = transcription.text()
        logger.debug("Transcription completed: ${'$'}{text.take(100)}...")
        return text
    }

    override fun runResponseGarbageCollection() {
        garbageCollectionStore.runResponseGarbageCollection(client)
    }
}
