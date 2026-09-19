package com.tapbot.core.model

import kotlinx.serialization.Serializable

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

/**
 * Individual log event produced by a running Telegram bot or the runner engine.
 */
@Serializable
data class BotLogEntry(
    val id: Long = System.nanoTime(),
    val botId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.INFO,
    val tag: String = "BotRunner",
    val message: String
)
