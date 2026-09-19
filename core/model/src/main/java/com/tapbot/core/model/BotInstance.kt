package com.tapbot.core.model

import kotlinx.serialization.Serializable

/**
 * Represents a locally installed bot on the user's Android device.
 */
@Serializable
data class BotInstance(
    val botId: String,
    val installedVersion: String,
    val installedAt: Long,
    val isEnabled: Boolean = false,
    val autoStartOnBoot: Boolean = false,
    val lastRunState: BotRunState = BotRunState.Stopped,
    val localPackagePath: String? = null
)
