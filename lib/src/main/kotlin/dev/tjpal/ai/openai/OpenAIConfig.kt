package dev.tjpal.ai.openai

import kotlinx.serialization.Serializable

@Serializable
data class OpenAIConfig(
    val openAIGarbageCollectorPath: String,
    val openAICredentialPath: String
)
