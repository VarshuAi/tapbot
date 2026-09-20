package com.tapbot.core.network

import com.tapbot.core.model.BotCredentialSpec
import com.tapbot.core.model.BotMetadata
import com.tapbot.core.model.BotPackageInfo
import com.tapbot.core.model.BotVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Production CatalogApi implementation connecting to the Cloudflare Workers / D1 API.
 * Dispatches requests to /api/v1/bots and /api/v1/bots/{id}.
 */
class CloudflareCatalogApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient = OkHttpClient(),
    private val fallback: CatalogApi? = MockCatalogApi()
) : CatalogApi {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    override suspend fun getBots(): Result<List<BotMetadata>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${baseUrl.trimEnd('/')}/api/v1/bots"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Cloudflare API error: ${response.code} ${response.message}")
                    }
                    val bodyString = response.body?.string() ?: throw IOException("Empty response body from catalog API")
                    val wrapper = json.decodeFromString<ApiEnvelope<List<CloudflareBotDto>>>(bodyString)
                    if (!wrapper.success || wrapper.data == null) {
                        throw IOException(wrapper.error?.message ?: "Unknown API catalog failure")
                    }
                    wrapper.data.map { it.toDomainModel() }
                }
            } catch (e: Exception) {
                // If network fails (e.g. offline dev or unconfigured domain) and fallback is provided, use fallback
                if (fallback != null) {
                    fallback.getBots().getOrThrow()
                } else {
                    throw e
                }
            }
        }
    }

    override suspend fun getBotDetails(botId: String): Result<BotMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${baseUrl.trimEnd('/')}/api/v1/bots/$botId"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (response.code == 404) {
                        throw NoSuchElementException("Bot not found on Cloudflare Catalog: $botId")
                    }
                    if (!response.isSuccessful) {
                        throw IOException("Cloudflare API error: ${response.code} ${response.message}")
                    }
                    val bodyString = response.body?.string() ?: throw IOException("Empty response body")
                    val wrapper = json.decodeFromString<ApiEnvelope<CloudflareBotDto>>(bodyString)
                    if (!wrapper.success || wrapper.data == null) {
                        throw IOException(wrapper.error?.message ?: "Failed to retrieve bot metadata")
                    }
                    wrapper.data.toDomainModel()
                }
            } catch (e: Exception) {
                if (fallback != null) {
                    fallback.getBotDetails(botId).getOrThrow()
                } else {
                    throw e
                }
            }
        }
    }

    override suspend fun getBotVersions(botId: String): Result<List<BotVersion>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${baseUrl.trimEnd('/')}/api/v1/bots/$botId/versions"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Cloudflare API error: ${response.code} ${response.message}")
                    }
                    val bodyString = response.body?.string() ?: throw IOException("Empty response body")
                    val wrapper = json.decodeFromString<ApiEnvelope<List<CloudflareVersionDto>>>(bodyString)
                    if (!wrapper.success || wrapper.data == null) {
                        throw IOException(wrapper.error?.message ?: "Failed to retrieve bot versions")
                    }
                    wrapper.data.map { it.toDomainModel(botId) }
                }
            } catch (e: Exception) {
                if (fallback != null) {
                    fallback.getBotVersions(botId).getOrThrow()
                } else {
                    throw e
                }
            }
        }
    }

    override suspend fun getLatestVersion(botId: String): Result<BotVersion?> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${baseUrl.trimEnd('/')}/api/v1/bots/$botId/versions/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (response.code == 404) return@runCatching null
                    if (!response.isSuccessful) {
                        throw IOException("Cloudflare API error: ${response.code} ${response.message}")
                    }
                    val bodyString = response.body?.string() ?: throw IOException("Empty response body")
                    val wrapper = json.decodeFromString<ApiEnvelope<CloudflareVersionDto>>(bodyString)
                    if (!wrapper.success || wrapper.data == null) {
                        null
                    } else {
                        wrapper.data.toDomainModel(botId)
                    }
                }
            } catch (e: Exception) {
                if (fallback != null) {
                    fallback.getLatestVersion(botId).getOrThrow()
                } else {
                    throw e
                }
            }
        }
    }

    companion object {
        // Default local development URL for Android Emulator (10.0.2.2 maps to host 127.0.0.1:8787)
        const val DEFAULT_LOCAL_URL = "http://10.0.2.2:8787"
        const val DEFAULT_BASE_URL = "https://tapbot-catalog.workers.dev"
    }
}
