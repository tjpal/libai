package dev.tjpal.ai.openai

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OpenAIEmbeddingsProviderTest {
    @Test
    fun similarity_returns_dot_product() {
        val provider = createProvider()

        val similarity = provider.similarity(
            firstEmbeddings = listOf(1.5f, 2.0f, -1.0f),
            secondEmbeddings = listOf(2.0f, -3.0f, 4.0f)
        )

        assertEquals(-7.0f, similarity)
    }

    @Test
    fun similarity_requires_vectors_of_same_size() {
        val provider = createProvider()

        assertFailsWith<IllegalArgumentException> {
            provider.similarity(listOf(1.0f), listOf(1.0f, 2.0f))
        }
    }

    private fun createProvider(): OpenAIEmbeddingsProvider {
        val tempDirectory = Files.createTempDirectory("openai-embeddings-provider-test")
        val credentialPath = tempDirectory.resolve("openai.key")
        Files.writeString(credentialPath, "test-key")

        return OpenAIEmbeddingsProvider(
            OpenAIConfig(
                openAIGarbageCollectorPath = tempDirectory.resolve("gc.json").toString(),
                openAICredentialPath = credentialPath.toString()
            )
        )
    }
}
