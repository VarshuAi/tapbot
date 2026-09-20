package com.tapbot.core.network

import com.tapbot.core.model.BotCredentialSpec
import com.tapbot.core.model.BotMetadata
import com.tapbot.core.model.BotPackageInfo
import com.tapbot.core.model.BotVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Contract for fetching raw catalog metadata from the Cloudflare Workers / D1 API.
 */
interface RemoteBotDataSource {
    suspend fun getPublishedBots(category: String? = null, search: String? = null): Result<List<BotMetadata>>
    suspend fun getBotDetails(botId: String): Result<BotMetadata>
    suspend fun getCategories(): Result<List<String>>
    suspend fun getBotVersions(botId: String): Result<List<BotVersion>>
    suspend fun getLatestVersion(botId: String): Result<BotVersion?>
}

/**
 * Production implementation of RemoteBotDataSource backed by Cloudflare Workers.
 */
class CloudflareRemoteBotDataSource(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient = OkHttpClient()
) : RemoteBotDataSource {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    override suspend fun getPublishedBots(category: String?, search: String?): Result<List<BotMetadata>> =
        withContext(Dispatchers.IO) {
            runCatching {
                var url = "${baseUrl.trimEnd('/')}/api/v1/bots"
                val queryParams = mutableListOf<String>()
                if (!category.isNullOrBlank() && !category.equals("All", ignoreCase = true)) {
                    queryParams.add("category=${category.lowercase()}")
                }
                if (!search.isNullOrBlank()) {
                    queryParams.add("search=${search.trim()}")
                }
                if (queryParams.isNotEmpty()) {
                    url += "?" + queryParams.joinToString("&")
                }

                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: ${response.message}")
                    }
                    val bodyString = response.body?.string() ?: throw IOException("Empty response body")
                    val wrapper = json.decodeFromString<ApiEnvelope<List<CloudflareBotDto>>>(bodyString)
                    if (!wrapper.success || wrapper.data == null) {
                        throw IOException(wrapper.error?.message ?: "Failed to fetch published bots")
                    }
                    wrapper.data.map { it.toDomainModel() }
                }
            }
        }

    override suspend fun getBotDetails(botId: String): Result<BotMetadata> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "${baseUrl.trimEnd('/')}/api/v1/bots/$botId"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.code == 404) {
                        throw NoSuchElementException("Bot not found: $botId")
                    }
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: ${response.message}")
                    }
                    val bodyString = response.body?.string() ?: throw IOException("Empty response body")
                    val wrapper = json.decodeFromString<ApiEnvelope<CloudflareBotDto>>(bodyString)
                    if (!wrapper.success || wrapper.data == null) {
                        throw IOException(wrapper.error?.message ?: "Failed to retrieve bot $botId")
                    }
                    wrapper.data.toDomainModel()
                }
            }
        }

    override suspend fun getCategories(): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "${baseUrl.trimEnd('/')}/api/v1/categories"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: ${response.message}")
                    }
                    val bodyString = response.body?.string() ?: throw IOException("Empty response body")
                    val wrapper = json.decodeFromString<ApiEnvelope<List<CloudflareCategoryDto>>>(bodyString)
                    if (!wrapper.success || wrapper.data == null) {
                        throw IOException(wrapper.error?.message ?: "Failed to fetch categories")
                    }
                    val list = mutableListOf("All")
                    list.addAll(wrapper.data.map { it.name })
                    list
                }
            }
        }

    override suspend fun getBotVersions(botId: String): Result<List<BotVersion>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "${baseUrl.trimEnd('/')}/api/v1/bots/$botId/versions"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: ${response.message}")
                    }
                    val bodyString = response.body?.string() ?: throw IOException("Empty response body")
                    val wrapper = json.decodeFromString<ApiEnvelope<List<CloudflareVersionDto>>>(bodyString)
                    if (!wrapper.success || wrapper.data == null) {
                        throw IOException(wrapper.error?.message ?: "Failed to fetch versions for $botId")
                    }
                    wrapper.data.map { it.toDomainModel(botId) }
                }
            }
        }

    override suspend fun getLatestVersion(botId: String): Result<BotVersion?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "${baseUrl.trimEnd('/')}/api/v1/bots/$botId/versions/latest"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.code == 404) return@runCatching null
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: ${response.message}")
                    }
                    val bodyString = response.body?.string() ?: throw IOException("Empty response body")
                    val wrapper = json.decodeFromString<ApiEnvelope<CloudflareVersionDto>>(bodyString)
                    if (!wrapper.success || wrapper.data == null) {
                        null
                    } else {
                        wrapper.data.toDomainModel(botId)
                    }
                }
            }
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://tapbot-catalog.workers.dev"
    }
}
