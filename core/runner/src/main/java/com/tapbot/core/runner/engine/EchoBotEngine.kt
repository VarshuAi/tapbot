package com.tapbot.core.runner.engine

import com.tapbot.core.model.LogLevel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Built-in reference implementation of [BotEngine].
 * Validates the bot token, runs an HTTPS long-polling loop,
 * and replies back with diagnostics for every incoming message.
 */
class EchoBotEngine(
    override val botId: String = "bot_echo"
) : BotEngine {

    private var executionContext: BotExecutionContext? = null
    private var pollingJob: Job? = null
    private var lastUpdateId: Long? = null

    override suspend fun initialize(context: BotExecutionContext) {
        this.executionContext = context
    }

    override suspend fun start() {
        val ctx = executionContext ?: throw IllegalStateException("BotEngine not initialized")
        val token = ctx.credentials["bot_token"] ?: run {
            ctx.log(LogLevel.ERROR, TAG, "Missing required bot_token credential!")
            return
        }

        ctx.log(LogLevel.INFO, TAG, "Starting Echo Bot runner loop on Android device...")

        // Validate token
        val meResult = ctx.telegramApi.getMe(token)
        if (meResult.isFailure) {
            ctx.log(LogLevel.ERROR, TAG, "Telegram authentication failed: ${meResult.exceptionOrNull()?.message}")
            return
        }

        val botUser = meResult.getOrNull()
        ctx.log(LogLevel.INFO, TAG, "Authenticated as @${botUser?.username} (${botUser?.firstName})")
        ctx.log(LogLevel.INFO, TAG, "Entering Telegram getUpdates long-polling loop...")

        pollingJob = ctx.scope.launch {
            while (isActive) {
                try {
                    val offset = lastUpdateId?.plus(1)
                    val result = ctx.telegramApi.getUpdates(
                        token = token,
                        offset = offset,
                        timeoutSeconds = 25
                    )

                    if (result.isSuccess) {
                        val updates = result.getOrNull().orEmpty()
                        for (update in updates) {
                            lastUpdateId = update.updateId
                            val message = update.message
                            if (message != null) {
                                val sender = message.from?.username ?: message.from?.firstName ?: "User"
                                val text = message.text ?: ""
                                ctx.log(LogLevel.INFO, TAG, "Received message from @$sender: $text")

                                val replyText = "🤖 Echo Bot (Running locally on Android)\n\n" +
                                        "Message: $text\n" +
                                        "From: @$sender\n" +
                                        "Chat ID: ${message.chat.id}\n" +
                                        "Status: Healthy & Active"

                                ctx.telegramApi.sendMessage(
                                    token = token,
                                    chatId = message.chat.id,
                                    text = replyText
                                )
                                ctx.log(LogLevel.DEBUG, TAG, "Sent echo reply to chat ${message.chat.id}")
                            }
                        }
                    } else {
                        val err = result.exceptionOrNull()?.message
                        ctx.log(LogLevel.WARN, TAG, "Polling poll returned error: $err. Retrying in 5s...")
                        delay(5000)
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        ctx.log(LogLevel.ERROR, TAG, "Polling exception: ${e.message}")
                        delay(5000)
                    }
                }
            }
        }
    }

    override suspend fun stop() {
        val ctx = executionContext
        ctx?.log(LogLevel.INFO, TAG, "Stopping Echo Bot runner...")
        pollingJob?.cancel()
        pollingJob = null
        ctx?.log(LogLevel.INFO, TAG, "Echo Bot runner stopped.")
    }

    companion object {
        private const val TAG = "EchoBotEngine"
    }
}
