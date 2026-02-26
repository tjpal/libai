package dev.tjpal.ai

import dev.tjpal.ai.messages.RequestResponseChain

interface LLM {
    fun createResponseRequestChain(): RequestResponseChain
    fun synthesizeSpeech(text: String, outputFilePath: String)
    fun transcriptAudio(filePath: String): String
    fun runResponseGarbageCollection()
}
