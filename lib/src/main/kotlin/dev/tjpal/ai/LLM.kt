package dev.tjpal.ai

import dev.tjpal.ai.messages.RequestResponseChain

interface LLM {
    fun createResponseRequestChain(): RequestResponseChain
    fun transcriptAudio(filePath: String): String
    fun runResponseGarbageCollection()
}
