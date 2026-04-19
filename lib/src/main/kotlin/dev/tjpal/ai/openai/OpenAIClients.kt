package dev.tjpal.ai.openai

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import org.slf4j.LoggerFactory

internal object OpenAIClients {
    private val logger = LoggerFactory.getLogger(OpenAIClients::class.java)

    fun buildClientFromCredentialPath(credentialPath: String): OpenAIClient {
        try {
            val key = Files.readString(Paths.get(credentialPath)).trim { it <= ' ' }
            return OpenAIOkHttpClient.builder().apiKey(key).build()
        } catch (e: IOException) {
            logger.error("Failed to read OpenAI API key from {}", credentialPath, e)
            throw IllegalStateException("Failed to read OpenAI API key from $credentialPath", e)
        }
    }
}
