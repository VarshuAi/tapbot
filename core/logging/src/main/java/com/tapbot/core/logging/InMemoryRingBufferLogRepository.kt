package com.tapbot.core.logging

import com.tapbot.core.model.BotLogEntry
import com.tapbot.core.model.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory ring buffer implementation of [BotLogRepository].
 * Retains up to [maxCapacityPerBot] entries per bot to keep RAM usage strictly bounded.
 */
class InMemoryRingBufferLogRepository(
    private val maxCapacityPerBot: Int = 500
) : BotLogRepository {

    private val botLogFlows = ConcurrentHashMap<String, MutableStateFlow<List<BotLogEntry>>>()
    private val lock = Any()

    override fun appendLog(botId: String, level: LogLevel, tag: String, message: String) {
        val entry = BotLogEntry(
            botId = botId,
            level = level,
            tag = tag,
            message = SecretRedactor.redact(message)
        )
        val flow = botLogFlows.computeIfAbsent(botId) { MutableStateFlow(emptyList()) }

        synchronized(lock) {
            val currentList = flow.value
            val updatedList = if (currentList.size >= maxCapacityPerBot) {
                currentList.drop(currentList.size - maxCapacityPerBot + 1) + entry
            } else {
                currentList + entry
            }
            flow.value = updatedList
        }
    }

    override fun getLogStream(botId: String): StateFlow<List<BotLogEntry>> {
        return botLogFlows.computeIfAbsent(botId) { MutableStateFlow(emptyList()) }.asStateFlow()
    }

    override fun clearLogs(botId: String) {
        synchronized(lock) {
            botLogFlows[botId]?.value = emptyList()
        }
    }
}
