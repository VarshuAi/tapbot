package com.tapbot.core.runner.engine

import android.content.Context
import com.tapbot.core.model.LogLevel
import com.tapbot.core.network.TelegramApiClient
import kotlinx.coroutines.CoroutineScope

/**
 * Execution context supplied to a running Telegram bot.
 * Provides access to on-device networking, secure credentials, and real-time logging.
 */
data class BotExecutionContext(
    val botId: String,
    val credentials: Map<String, String>,
    val telegramApi: TelegramApiClient,
    val log: (level: LogLevel, tag: String, message: String) -> Unit,
    val scope: CoroutineScope,
    val context: Context
)

/**
 * Pluggable contract for on-device Telegram Bot runtimes.
 * Supports built-in bots, dynamic DEX plugins, and future scripting engines.
 */
interface BotEngine {
    val botId: String
    suspend fun initialize(context: BotExecutionContext)
    suspend fun start()
    suspend fun stop()
}
