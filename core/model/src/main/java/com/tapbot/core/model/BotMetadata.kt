package com.tapbot.core.model

import kotlinx.serialization.Serializable

/**
 * Metadata for a bot published in the catalog.
 * Curated and served from Cloudflare Workers/D1/R2.
 */
@Serializable
data class BotMetadata(
    val id: String,
    val name: String,
    val summary: String,
    val description: String,
    val author: String,
    val version: String,
    val iconUrl: String,
    val category: String,
    val tags: List<String> = emptyList(),
    val requiredCredentials: List<BotCredentialSpec> = listOf(BotCredentialSpec.TELEGRAM_BOT_TOKEN),
    val packageInfo: BotPackageInfo,
    val permissionsRequired: List<String> = emptyList(),
    val repositoryUrl: String? = null,
    val releaseNotes: String? = null,
    val minimumAppVersion: Int = 1,
    val minimumRuntimeVersion: String = "1.0.0",
    val updatedAt: String? = null,
    val isFeatured: Boolean = false
)
