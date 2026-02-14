package dev.tjpal.ai.openai

import com.openai.client.OpenAIClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Serializable
data class GarbageCollectionEntry(
    val timestamp: Long,
    val responseId: String,
    val conversationId: String
)

/**
 * Stores all created response and conversation IDs to a file for later garbage collection.
 * The file consists of JSON entries *per line*. It cannot be read as a whole JSON array to allow
 * appending without rewriting the entire file.
 */
class ResponsesGarbageCollector(private val file: Path) {
    private val lock = ReentrantLock()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val logger = LoggerFactory.getLogger(ResponsesGarbageCollector::class.java)

    init {
        try {
            Files.createDirectories(file.parent)
        } catch (e: IOException) {
            logger.error("Failed to create directories for GC file at ${file.parent}: ${e.message}", e)
        }
    }

    fun append(entry: GarbageCollectionEntry) {
        lock.withLock {
            try {
                BufferedWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)).use { writer ->
                    writer.write(json.encodeToString(GarbageCollectionEntry.serializer(), entry))
                    writer.newLine()
                    writer.flush()
                }
            } catch (e: IOException) {
                logger.error("Failed to write GC file at ${file.parent}: ${e.message}", e)
            }
        }
    }

    fun runResponseGarbageCollection(client: OpenAIClient) {
        logger.info("Starting OpenAI GC run")

        val entries = listEntries()
        if (entries.isEmpty()) {
            logger.debug("No GC entries found")
            return
        }

        val grouped = entries.groupBy { it.conversationId }

        for ((conversationId, convEntries) in grouped) {
            val responseIds = convEntries.map { it.responseId }
            val successfullyDeleted = mutableListOf<GarbageCollectionEntry>()

            // Try to delete responses individually
            for (entry in convEntries) {
                try {
                    client.responses().delete(entry.responseId)
                    successfullyDeleted.add(entry)
                    logger.info("Deleted response {} for conversation {}", entry, conversationId)
                } catch (e: Exception) {
                    // If some conversations cannot be deleted, they were either deleted or this will recover in the next
                    // try. For now keep them so we try again next time. In the worst case, the file needs to be cleared manually.
                    // Failed deletions are the absolute exception.
                    logger.warn("Failed to delete response {} for conversation {}: {}", entry, conversationId, e.message)
                }
            }

            // Delete the conversation ID only if all responses were deleted
            if(responseIds.size == successfullyDeleted.size) {
                try {
                    client.conversations().delete(conversationId)
                } catch (e: Exception) {
                    logger.error("Failed to delete conversation {}: {}", conversationId, e.message)
                    continue
                }
            }

            removeEntries(successfullyDeleted)
        }

        logger.info("Manual OpenAI GC run finished")
    }

    fun removeEntries(responseIds: Set<String>, conversationId: String) {
        val entriesToRemove = listEntries().filter { entry ->
            entry.conversationId == conversationId && responseIds.contains(entry.responseId)
        }

        removeEntries(entriesToRemove)
    }

    private fun listEntries(): List<GarbageCollectionEntry> {
        lock.withLock {
            if (!Files.exists(file)) return emptyList()

            return try {
                Files.newBufferedReader(file, StandardCharsets.UTF_8).use { reader ->
                    reader.lineSequence()
                        .mapNotNull { line ->
                            val trimmed = line.trim()
                            if (trimmed.isEmpty()) return@mapNotNull null

                            try {
                                json.decodeFromString(GarbageCollectionEntry.serializer(), trimmed)
                            } catch (e: Exception) {
                                logger.error("Failed to decode GC file at ${file.parent}: ${e.message}", e)
                                null
                            }
                        }
                        .toList()
                }
            } catch (e: IOException) {
                logger.error("Failed to read GC file at ${file.parent}: ${e.message}", e)
                emptyList()
            }
        }
    }

    private fun removeEntries(entriesToRemove: List<GarbageCollectionEntry>) {
        lock.withLock {
            if (!Files.exists(file)) return

            try {
                val existingEntries = listEntries()
                val updatedEntries = existingEntries.filterNot { entry ->
                    entriesToRemove.any { it.responseId == entry.responseId && it.conversationId == entry.conversationId && it.timestamp == entry.timestamp }
                }

                BufferedWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)).use { writer ->
                    updatedEntries.forEach { entry ->
                        writer.write(json.encodeToString(GarbageCollectionEntry.serializer(), entry))
                        writer.newLine()
                    }
                    writer.flush()
                }
            } catch (e: IOException) {
                logger.error("Failed to update GC file at ${file.parent}: ${e.message}", e)
            }
        }
    }

    fun clear() {
        lock.withLock {
            try {
                Files.deleteIfExists(file)
            } catch (e: IOException) {
                System.err.println("Failed to delete GC file ${file}: ${e.message}")
            }
        }
    }
}
