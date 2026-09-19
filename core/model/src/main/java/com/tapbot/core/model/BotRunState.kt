package com.tapbot.core.model

import kotlinx.serialization.Serializable

/**
 * Execution state of a local Telegram bot on the Android device.
 */
@Serializable
sealed class BotRunState {
    @Serializable
    object Stopped : BotRunState()

    @Serializable
    object Starting : BotRunState()

    @Serializable
    data class Running(
        val startedAt: Long,
        val pollCount: Long = 0L,
        val lastActivityAt: Long = startedAt
    ) : BotRunState()

    @Serializable
    data class PausedNoNetwork(val reason: String = "Waiting for network connectivity") : BotRunState()

    @Serializable
    data class Error(val message: String, val timestamp: Long = System.currentTimeMillis()) : BotRunState()

    val isRunning: Boolean get() = this is Running
}
