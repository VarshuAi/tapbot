package com.tapbot.core.runner.runtime

import kotlinx.serialization.Serializable

/**
 * Lifecycle and execution state machine for an on-device Bot Runtime.
 */
@Serializable
sealed class BotRuntimeState {
    @Serializable
    object Stopped : BotRuntimeState()

    @Serializable
    object Starting : BotRuntimeState()

    @Serializable
    data class Connected(
        val botUsername: String,
        val botFirstName: String
    ) : BotRuntimeState()

    @Serializable
    data class Running(
        val botUsername: String,
        val startedAt: Long = System.currentTimeMillis(),
        val pollCount: Long = 0L,
        val messageCount: Long = 0L,
        val lastActivityAt: Long = startedAt
    ) : BotRuntimeState()

    @Serializable
    object Stopping : BotRuntimeState()

    @Serializable
    data class Error(
        val message: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : BotRuntimeState()

    val isRunning: Boolean get() = this is Running
    val isConnected: Boolean get() = this is Connected || this is Running
}
