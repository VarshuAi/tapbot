package com.tapbot.core.model

import kotlinx.serialization.Serializable

/**
 * Represents a published or available version of a bot in the catalog.
 */
@Serializable
data class BotVersion(
    val id: String,
    val botId: String,
    val version: String,
    val packageUrl: String,
    val sha256: String,
    val packageSize: Long = 0L,
    val releaseNotes: String? = null,
    val minimumAppVersion: Int = 1,
    val minimumRuntimeVersion: String = "1.0.0",
    val isPublished: Boolean = true,
    val publishedAt: String = ""
)
