package com.tapbot.core.model

import kotlinx.serialization.Serializable

/**
 * Information regarding the downloadable bot package payload.
 */
@Serializable
data class BotPackageInfo(
    val packageUrl: String,
    val sha256Checksum: String,
    val runtimeType: String = "built-in", // e.g. "built-in", "dex"
    val entryClass: String? = null,
    val packageSizeBytes: Long = 0L
)
