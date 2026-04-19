package dev.tjpal.ai.openai

import com.openai.models.audio.AudioModel
import com.openai.models.audio.speech.SpeechCreateParams
import com.openai.models.audio.speech.SpeechModel
import com.openai.models.audio.transcriptions.TranscriptionCreateParams
import dev.tjpal.ai.LLM
import dev.tjpal.ai.messages.RequestResponseChain
import dev.tjpal.ai.di.LibrarySingleton
import dev.tjpal.ai.tools.ToolRegistry
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import kotlin.io.path.Path
import org.slf4j.LoggerFactory

@LibrarySingleton
class OpenAILLM @Inject constructor(
    private val config: OpenAIConfig,
    private val toolRegistry: ToolRegistry,
    private val openAINativeToolRuntime: OpenAINativeToolRuntime
) : LLM {
    private val logger = LoggerFactory.getLogger(OpenAILLM::class.java)
    private val client = OpenAIClients.buildClientFromCredentialPath(config.openAICredentialPath)
    private val garbageCollectionStore = ResponsesGarbageCollector(Path(config.openAIGarbageCollectorPath))

    override fun createResponseRequestChain(): RequestResponseChain {
        logger.debug("OpenAI RequestResponseChain created")
        return OpenAIRequestResponseChain(client, toolRegistry, garbageCollectionStore, openAINativeToolRuntime)
    }

    override fun synthesizeSpeech(text: String, outputFilePath: String) {
        require(text.isNotBlank()) { "Input text must not be blank." }

        val outputPath = Paths.get(outputFilePath)
        outputPath.parent?.let { Files.createDirectories(it) }
        logger.info("Synthesizing speech to file={}", outputFilePath)

        val params = SpeechCreateParams.builder()
            .model(SpeechModel.GPT_4O_MINI_TTS)
            .voice("alloy")
            .input(text)
            .responseFormat(SpeechCreateParams.ResponseFormat.MP3)
            .speed(1.0)
            .build()

        client.audio()
            .withRawResponse()
            .speech()
            .create(params)
            .body()
            .use { audio ->
                Files.copy(audio, outputPath, StandardCopyOption.REPLACE_EXISTING)
            }
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
            model(AudioModel.GPT_4O_MINI_TRANSCRIBE).
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
