package com.tapbot.core.network

import com.tapbot.core.model.BotCredentialSpec
import com.tapbot.core.model.BotMetadata
import com.tapbot.core.model.BotPackageInfo
import com.tapbot.core.model.BotVersion
import kotlinx.serialization.Serializable

@Serializable
internal data class ApiEnvelope<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiErrorDetail? = null
)

@Serializable
internal data class ApiErrorDetail(
    val code: String,
    val message: String
)

@Serializable
internal data class CloudflareCategoryDto(
    val id: String,
    val name: String,
    val slug: String
)

@Serializable
internal data class CloudflareBotDto(
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
    val createdAt: String = "",
    val updatedAt: String = "",
    val credentials: List<CloudflareCredentialDto> = emptyList(),
    val currentVersionInfo: CloudflareVersionDto? = null
) {
    fun toDomainModel(): BotMetadata {
        val resolvedUpdatedAt = if (updatedAt.isNotBlank()) updatedAt else createdAt
        val isBotFeatured = category.equals("utilities", ignoreCase = true) || id.contains("ping_pong")
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
            permissionsRequired = listOf("INTERNET", "FOREGROUND_SERVICE", "NOTIFICATIONS"),
            releaseNotes = currentVersionInfo?.releaseNotes,
            minimumAppVersion = currentVersionInfo?.minimumAppVersion ?: 1,
            minimumRuntimeVersion = "1.0.0",
            updatedAt = resolvedUpdatedAt,
            isFeatured = isBotFeatured
        )
    }
}

@Serializable
internal data class CloudflareCredentialDto(
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
internal data class CloudflareVersionDto(
    val id: String,
    val version: String,
    val packageKey: String,
    val packageSize: Long = 0L,
    val sha256: String,
    val releaseNotes: String? = null,
    val minimumAppVersion: Int = 1,
    val publishedAt: String,
    val downloadUrl: String? = null
) {
    fun toDomainModel(botId: String): BotVersion {
        return BotVersion(
            id = id,
            botId = botId,
            version = version,
            packageUrl = downloadUrl ?: "",
            sha256 = sha256,
            packageSize = packageSize,
            releaseNotes = releaseNotes,
            minimumAppVersion = minimumAppVersion,
            minimumRuntimeVersion = "1.0.0",
            isPublished = true,
            publishedAt = publishedAt
        )
    }
}
