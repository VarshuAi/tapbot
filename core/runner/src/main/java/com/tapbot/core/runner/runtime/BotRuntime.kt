package com.tapbot.core.runner.runtime

import com.tapbot.core.model.LogLevel
import com.tapbot.core.network.TelegramApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Context provided to a [BotRuntime] for isolated execution.
 */
data class RuntimeContext(
    val botId: String,
    val token: String,
    val telegramApi: TelegramApiClient,
    val log: (level: LogLevel, tag: String, message: String) -> Unit,
    val scope: CoroutineScope,
    val isNetworkAvailable: () -> Boolean = { true },
    val networkState: StateFlow<Boolean>? = null
)

/**
 * Standard contract for executing a Telegram bot on Android.
 * In Phase 2, this is implemented as an in-process native ART coroutine runtime.
 */
interface BotRuntime {
    val botId: String
    val state: StateFlow<BotRuntimeState>

    suspend fun initialize(context: RuntimeContext)
    suspend fun start()
    suspend fun stop()
}
