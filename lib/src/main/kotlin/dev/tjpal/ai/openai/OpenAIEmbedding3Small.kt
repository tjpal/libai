package dev.tjpal.ai.openai

import com.openai.models.embeddings.EmbeddingModel
import dev.tjpal.ai.embeddings.Embeddings
import javax.inject.Inject

class OpenAIEmbedding3Small @Inject constructor(
    private val provider: OpenAIEmbeddingsProvider
) : Embeddings {
    override val modelId: String = "TEXT_EMBEDDING_3_SMALL"

    override fun createEmbeddings(inputs: List<String>): List<List<Float>> {
        return provider.createEmbeddings(inputs, EmbeddingModel.Companion.TEXT_EMBEDDING_3_SMALL)
    }

    override fun similarity(firstEmbeddings: List<Float>, secondEmbeddings: List<Float>): Float {
        return provider.similarity(firstEmbeddings, secondEmbeddings)
    }
}
