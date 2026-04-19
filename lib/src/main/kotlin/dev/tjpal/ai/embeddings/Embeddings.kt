package dev.tjpal.ai.embeddings

interface Embeddings {
    val modelId: String
    fun createEmbeddings(inputs: List<String>): List<List<Float>>
    fun similarity(firstEmbeddings: List<Float>, secondEmbeddings: List<Float>): Float
}
