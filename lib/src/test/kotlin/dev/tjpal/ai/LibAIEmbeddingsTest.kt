package dev.tjpal.ai

import dev.tjpal.ai.openai.OpenAIConfig
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import java.security.InvalidParameterException

class LibAIEmbeddingsTest {
    @Test
    fun embeddings_returns_openai_models_by_stable_model_id() {
        val libAI = createLibAI()

        val embeddings = libAI.embeddings()

        assertEquals(
            setOf("TEXT_EMBEDDING_3_LARGE", "TEXT_EMBEDDING_3_SMALL"),
            embeddings.keys
        )
        assertEquals("TEXT_EMBEDDING_3_LARGE", embeddings.getValue("TEXT_EMBEDDING_3_LARGE").modelId)
        assertEquals("TEXT_EMBEDDING_3_SMALL", embeddings.getValue("TEXT_EMBEDDING_3_SMALL").modelId)
    }

    @Test
    fun embedding_returns_registered_instance_for_model_id() {
        val libAI = createLibAI()

        val allEmbeddings = libAI.embeddings()
        val selectedEmbedding = libAI.embedding("TEXT_EMBEDDING_3_LARGE")

        assertSame(allEmbeddings.getValue("TEXT_EMBEDDING_3_LARGE"), selectedEmbedding)
    }

    @Test
    fun embedding_throws_for_unknown_model_id() {
        val libAI = createLibAI()

        assertFailsWith<InvalidParameterException> {
            libAI.embedding("unknown-model")
        }
    }

    private fun createLibAI(): LibAI {
        val tempDirectory = Files.createTempDirectory("libai-embeddings-test")
        val credentialPath = tempDirectory.resolve("openai.key")
        Files.writeString(credentialPath, "test-key")

        return LibAI(
            OpenAIConfig(
                openAIGarbageCollectorPath = tempDirectory.resolve("gc.json").toString(),
                openAICredentialPath = credentialPath.toString()
            )
        )
    }
}
