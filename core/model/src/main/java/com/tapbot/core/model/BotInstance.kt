package com.tapbot.core.model

import kotlinx.serialization.Serializable

/**
 * Lifecycle and execution state for a locally installed bot instance.
 */
@Serializable
sealed class BotInstanceStatus {
    @Serializable
    object Stopped : BotInstanceStatus()

    @Serializable
    object Starting : BotInstanceStatus()

    @Serializable
    data class Running(
        val pollCount: Long = 0L,
        val messageCount: Long = 0L,
        val lastActivityAt: Long = System.currentTimeMillis()
    ) : BotInstanceStatus()

    @Serializable
    object Stopping : BotInstanceStatus()

    @Serializable
    data class Crashed(
        val reason: String,
        val timestamp: Long = System.currentTimeMillis(),
        val crashCount: Int = 1
    ) : BotInstanceStatus()

    val isRunning: Boolean get() = this is Running
    val isStopped: Boolean get() = this is Stopped
    val isCrashed: Boolean get() = this is Crashed
}

/**
 * Historical and diagnostic crash information for an installed bot.
 */
@Serializable
data class CrashState(
    val crashCount: Int = 0,
    val lastCrashReason: String? = null,
    val lastCrashTimestamp: Long? = null
)

/**
 * Represents an independently installed and managed bot instance on the Android device.
 *
 * Each installation has its own:
 * - Unique installation ID
 * - Bot ID and catalog metadata
 * - Installed version
 * - Configuration map
 * - Credentials in hardware Keystore
 * - Isolated runtime state & lifecycle
 * - Log stream
 * - Start time & uptime tracking
 * - Crash state & recovery metrics
 */
@Serializable
data class BotInstance(
    val installationId: String,
    val botId: String,
    val name: String,
    val version: String,
    val status: BotInstanceStatus = BotInstanceStatus.Stopped,
    val configuration: Map<String, String> = emptyMap(),
    val packagePath: String? = null,
    val runtimeType: String = "native_art",
    val installedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = installedAt,
    val startedAt: Long? = null,
    val crashState: CrashState? = null
)
