package com.tapbot.core.runner.runtime

import com.tapbot.core.model.LogLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Real implementation of [BotRuntime] for the Phase 2 Proof of Concept.
 * Executes on-device, long-polls Telegram HTTPS API, and handles:
 * - "/start" -> replies with a simple greeting
 * - "ping" -> replies "pong"
 */
class PingPongBotRuntime(
    override val botId: String = "bot_pingpong_poc"
) : BotRuntime {

    private val _state = MutableStateFlow<BotRuntimeState>(BotRuntimeState.Stopped)
    override val state: StateFlow<BotRuntimeState> = _state.asStateFlow()

    private var context: RuntimeContext? = null
    private var pollJob: Job? = null
    private var lastUpdateId: Long? = null

    override suspend fun initialize(context: RuntimeContext) {
        this.context = context
    }

    override suspend fun start() {
        val ctx = context ?: throw IllegalStateException("Runtime not initialized")
        val token = ctx.token

        if (token.isBlank()) {
            val err = "Cannot start bot: Empty Telegram Bot Token provided."
            ctx.log(LogLevel.ERROR, TAG, err)
            _state.value = BotRuntimeState.Error(err)
            return
        }

        _state.value = BotRuntimeState.Starting
        ctx.log(LogLevel.INFO, TAG, "Starting PingPongBot runtime locally on Android ART...")

        // Step 1: Authenticate with Telegram API
        val meResult = ctx.telegramApi.getMe(token)
        if (meResult.isFailure) {
            val err = "Telegram authentication failed: ${meResult.exceptionOrNull()?.message}"
            ctx.log(LogLevel.ERROR, TAG, err)
            _state.value = BotRuntimeState.Error(err)
            return
        }

        val botUser = meResult.getOrThrow()
        val username = botUser.username ?: "UnknownBot"
        ctx.log(LogLevel.INFO, TAG, "Authenticated successfully as @$username (${botUser.firstName})")

        _state.value = BotRuntimeState.Connected(username, botUser.firstName)

        // Step 2: Launch Long Polling Coroutine Loop
        val startedAt = System.currentTimeMillis()
        var pollCounter = 0L
        var messageCounter = 0L

        pollJob = ctx.scope.launch {
            _state.value = BotRuntimeState.Running(
                botUsername = username,
                startedAt = startedAt,
                pollCount = 0L,
                messageCount = 0L
            )
            ctx.log(LogLevel.INFO, TAG, "Entering getUpdates long-polling loop (timeout: 25s)...")

            while (isActive) {
                try {
                    val offset = lastUpdateId?.plus(1)
                    val updatesResult = ctx.telegramApi.getUpdates(
                        token = token,
                        offset = offset,
                        timeoutSeconds = 25
                    )

                    pollCounter++

                    if (updatesResult.isSuccess) {
                        val updates = updatesResult.getOrThrow()
                        for (update in updates) {
                            lastUpdateId = update.updateId
                            val message = update.message ?: continue
                            val sender = message.from?.username ?: message.from?.firstName ?: "User"
                            val text = message.text?.trim().orEmpty()
                            val chatId = message.chat.id

                            messageCounter++
                            ctx.log(LogLevel.INFO, TAG, "Received message from @$sender (chatId: $chatId): '$text'")

                            when {
                                text.equals("/start", ignoreCase = true) -> {
                                    val reply = "👋 Hello! I am running locally on an Android device via TapBot.\n\nSend 'ping' to test my response!"
                                    ctx.telegramApi.sendMessage(token, chatId, reply)
                                    ctx.log(LogLevel.INFO, TAG, "Replied to /start for @$sender")
                                }
                                text.equals("ping", ignoreCase = true) -> {
                                    val reply = "pong"
                                    ctx.telegramApi.sendMessage(token, chatId, reply)
                                    ctx.log(LogLevel.INFO, TAG, "Replied 'pong' to @$sender")
                                }
                                else -> {
                                    val reply = "🤖 Bot is running locally on Android!\n\nCommands:\n• /start - Welcome message\n• ping - Responds with pong"
                                    ctx.telegramApi.sendMessage(token, chatId, reply)
                                    ctx.log(LogLevel.DEBUG, TAG, "Sent command help to @$sender")
                                }
                            }

                            _state.value = BotRuntimeState.Running(
                                botUsername = username,
                                startedAt = startedAt,
                                pollCount = pollCounter,
                                messageCount = messageCounter,
                                lastActivityAt = System.currentTimeMillis()
                            )
                        }
                    } else {
                        val error = updatesResult.exceptionOrNull()
                        ctx.log(LogLevel.WARN, TAG, "getUpdates returned error: ${error?.message}. Retrying in 3s...")
                        delay(3000)
                    }
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    if (isActive) {
                        ctx.log(LogLevel.ERROR, TAG, "Exception in polling loop: ${e.message}")
                        delay(3000)
                    }
                }
            }
        }
    }

    override suspend fun stop() {
        val ctx = context
        _state.value = BotRuntimeState.Stopping
        ctx?.log(LogLevel.INFO, TAG, "Stopping runtime...")

        pollJob?.cancel()
        pollJob = null

        _state.value = BotRuntimeState.Stopped
        ctx?.log(LogLevel.INFO, TAG, "Bot runtime stopped cleanly.")
    }

    companion object {
        private const val TAG = "PingPongBotRuntime"
    }
}
