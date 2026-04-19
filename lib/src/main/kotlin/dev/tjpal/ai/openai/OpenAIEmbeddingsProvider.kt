package dev.tjpal.ai.openai

import com.openai.models.embeddings.EmbeddingCreateParams
import com.openai.models.embeddings.EmbeddingModel
import dev.tjpal.ai.di.LibrarySingleton
import javax.inject.Inject

@LibrarySingleton
class OpenAIEmbeddingsProvider @Inject constructor(
    config: OpenAIConfig
) {
    private val client = OpenAIClients.buildClientFromCredentialPath(config.openAICredentialPath)

    fun createEmbeddings(inputs: List<String>, model: EmbeddingModel): List<List<Float>> {
        val createParams = EmbeddingCreateParams.builder()
            .inputOfArrayOfStrings(inputs)
            .model(model)
            .build()

        val response = client.embeddings().create(createParams)
        val floats = response.data().map { embedding -> embedding.embedding() }

        if (floats.size != inputs.size) {
            throw IllegalStateException("OpenAI API response did not match the number of inputs")
        }

        return floats
    }

    fun similarity(firstEmbeddings: List<Float>, secondEmbeddings: List<Float>): Float {
        if (firstEmbeddings.size != secondEmbeddings.size) {
            throw IllegalArgumentException("Vectors must be of the same size to compare")
        }

        var dotProduct = 0.0f

        for (i in firstEmbeddings.indices) {
            dotProduct += firstEmbeddings[i] * secondEmbeddings[i]
        }

        return dotProduct
    }
}
