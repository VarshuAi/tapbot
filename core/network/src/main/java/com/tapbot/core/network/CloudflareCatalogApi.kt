package com.tapbot.core.network

import com.tapbot.core.model.BotCredentialSpec
import com.tapbot.core.model.BotMetadata
import com.tapbot.core.model.BotPackageInfo
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

    companion object {
        // Default local development URL for Android Emulator (10.0.2.2 maps to host 127.0.0.1:8787)
        const val DEFAULT_LOCAL_URL = "http://10.0.2.2:8787"
        const val DEFAULT_BASE_URL = "https://tapbot-catalog.workers.dev"
    }
}

// -------------------------------------------------------------------------
// Cloudflare Workers API Serialization DTOs
// -------------------------------------------------------------------------

@Serializable
private data class ApiEnvelope<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiErrorDetail? = null
)

@Serializable
private data class ApiErrorDetail(
    val code: String,
    val message: String
)

@Serializable
private data class CloudflareBotDto(
    val id: String,
    val slug: String,
    val name: String,
    val description: String,
    val longDescription: String? = null,
    val iconUrl: String? = null,
    val category: String,
    val runtime: String = "native_art",
    val currentVersion: String? = null,
    val status: String = "published",
    val credentials: List<CloudflareCredentialDto> = emptyList(),
    val currentVersionInfo: CloudflareVersionDto? = null
) {
    fun toDomainModel(): BotMetadata {
        return BotMetadata(
            id = id,
            name = name,
            summary = description,
            description = longDescription ?: description,
            author = "TapBot Curated",
            version = currentVersion ?: "1.0.0",
            iconUrl = iconUrl ?: "",
            category = category.replaceFirstChar { it.uppercase() },
            tags = listOf(category, runtime),
            requiredCredentials = if (credentials.isNotEmpty()) {
                credentials.map { it.toDomainModel() }
            } else {
                listOf(BotCredentialSpec.TELEGRAM_BOT_TOKEN)
            },
            packageInfo = BotPackageInfo(
                packageUrl = currentVersionInfo?.downloadUrl ?: "",
                sha256Checksum = currentVersionInfo?.sha256 ?: "",
                runtimeType = runtime,
                packageSizeBytes = currentVersionInfo?.packageSize ?: 0L
            ),
            permissionsRequired = listOf("INTERNET", "FOREGROUND_SERVICE")
        )
    }
}

@Serializable
private data class CloudflareCredentialDto(
    val key: String,
    val displayName: String,
    val description: String? = null,
    val required: Boolean = true,
    val secret: Boolean = true,
    val inputType: String = "text"
) {
    fun toDomainModel(): BotCredentialSpec {
        return BotCredentialSpec(
            key = key,
            label = displayName,
            description = description ?: "",
            isSecret = secret,
            isRequired = required,
            placeholder = if (secret) "••••••••" else ""
        )
    }
}

@Serializable
private data class CloudflareVersionDto(
    val id: String,
    val version: String,
    val packageKey: String,
    val packageSize: Long = 0L,
    val sha256: String,
    val releaseNotes: String? = null,
    val minimumAppVersion: Int = 1,
    val publishedAt: String,
    val downloadUrl: String? = null
)
