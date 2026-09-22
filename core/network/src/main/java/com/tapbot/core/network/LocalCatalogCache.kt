package com.tapbot.core.network

import com.tapbot.core.model.BotMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists and retrieves catalog data locally to enable offline store browsing.
 */
class LocalCatalogCache(
    private val cacheFile: File? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private var inMemoryBots: List<BotMetadata>? = null
    private var inMemoryCategories: List<String>? = null

    init {
        loadFromDisk()
    }

    @Synchronized
    fun saveCatalog(bots: List<BotMetadata>, categories: List<String>) {
        inMemoryBots = bots
        inMemoryCategories = categories

        if (cacheFile != null) {
            try {
                cacheFile.parentFile?.mkdirs()
                val payload = CachedCatalogPayload(bots = bots, categories = categories)
                cacheFile.writeText(json.encodeToString(CachedCatalogPayload.serializer(), payload))
            } catch (e: Exception) {
                // Ignore disk cache write errors
            }
        }
    }

    @Synchronized
    fun getCachedBots(): List<BotMetadata>? = inMemoryBots

    @Synchronized
    fun getCachedCategories(): List<String>? = inMemoryCategories

    @Synchronized
    fun getCachedBot(botId: String): BotMetadata? {
        return inMemoryBots?.find { it.id == botId || it.name.equals(botId, ignoreCase = true) }
    }

    @Synchronized
    fun updateBot(bot: BotMetadata) {
        val current = inMemoryBots?.toMutableList() ?: mutableListOf()
        val index = current.indexOfFirst { it.id == bot.id }
        if (index >= 0) {
            current[index] = bot
        } else {
            current.add(bot)
        }
        inMemoryBots = current
        if (cacheFile != null) {
            try {
                cacheFile.parentFile?.mkdirs()
                val payload = CachedCatalogPayload(bots = current, categories = inMemoryCategories ?: emptyList())
                cacheFile.writeText(json.encodeToString(CachedCatalogPayload.serializer(), payload))
            } catch (_: Exception) {
                // Ignore disk cache write errors
            }
        }
    }

    @Synchronized
    fun clearCache() {
        inMemoryBots = null
        inMemoryCategories = null
        if (cacheFile != null && cacheFile.exists()) {
            cacheFile.delete()
        }
    }

    @Synchronized
    fun hasCache(): Boolean = !inMemoryBots.isNullOrEmpty()

    private fun loadFromDisk() {
        if (cacheFile != null && cacheFile.exists() && cacheFile.length() > 0) {
            try {
                val text = cacheFile.readText()
                val payload = json.decodeFromString(CachedCatalogPayload.serializer(), text)
                inMemoryBots = payload.bots
                inMemoryCategories = payload.categories
            } catch (_: Exception) {
                // Ignore corrupted disk cache
            }
        }
    }

    @Serializable
    private data class CachedCatalogPayload(
        val bots: List<BotMetadata>,
        val categories: List<String>
    )
}
