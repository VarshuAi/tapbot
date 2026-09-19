package com.tapbot.core.logging

import com.tapbot.core.model.BotLogEntry
import com.tapbot.core.model.LogLevel
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for managing and observing bot execution logs in real time.
 */
interface BotLogRepository {
    /**
     * Appends a new log line for a specific bot execution.
     */
    fun appendLog(botId: String, level: LogLevel, tag: String, message: String)

    /**
     * Observes real-time logs for the given bot as a StateFlow for Compose collectors.
     */
    fun getLogStream(botId: String): StateFlow<List<BotLogEntry>>

    /**
     * Clears all buffered logs for the given bot.
     */
    fun clearLogs(botId: String)
}
