package dev.tjpal.ai.messages

abstract class RequestResponseChain {
    abstract fun createResponse(request: Request): Response
    abstract fun delete()
}