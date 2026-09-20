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
 * On-device runtime for Utility Bot.
 * Provides system diagnostics, latency checks, and test crash simulation:
 * /ping, /uptime, /sysinfo, /echo, /crash (for resilience testing)
 */
class UtilityBotRuntime(
    override val botId: String = "bot_utility"
) : BotRuntime {

    private val _state = MutableStateFlow<BotRuntimeState>(BotRuntimeState.Stopped)
    override val state: StateFlow<BotRuntimeState> = _state.asStateFlow()

    private var context: RuntimeContext? = null
    private var pollJob: Job? = null
    private var lastUpdateId: Long? = null
    private var bootTimestamp: Long = 0L

    override suspend fun initialize(context: RuntimeContext) {
        this.context = context
    }

    override suspend fun start() {
        val ctx = context ?: throw IllegalStateException("Runtime not initialized")
        val token = ctx.token

        if (token.isBlank()) {
            val err = "Cannot start Utility Bot: Empty Telegram Bot Token."
            ctx.log(LogLevel.ERROR, TAG, err)
            _state.value = BotRuntimeState.Error(err)
            return
        }

        _state.value = BotRuntimeState.Starting
        bootTimestamp = System.currentTimeMillis()
        ctx.log(LogLevel.INFO, TAG, "Starting Utility Bot runtime on Android ART...")

        val meResult = ctx.telegramApi.getMe(token)
        if (meResult.isFailure) {
            val err = "Telegram authentication failed: ${meResult.exceptionOrNull()?.message}"
            ctx.log(LogLevel.ERROR, TAG, err)
            _state.value = BotRuntimeState.Error(err)
            return
        }

        val botUser = meResult.getOrThrow()
        val username = botUser.username ?: "UtilityBot"
        ctx.log(LogLevel.INFO, TAG, "Utility Bot authenticated as @$username")

        _state.value = BotRuntimeState.Connected(username, botUser.firstName)

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
            ctx.log(LogLevel.INFO, TAG, "Utility Bot polling active.")

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
                            val text = message.text?.trim().orEmpty()
                            val chatId = message.chat.id
                            messageCounter++

                            if (text.equals("/crash", ignoreCase = true)) {
                                ctx.log(LogLevel.ERROR, TAG, "Simulated crash command triggered!")
                                throw RuntimeException("Simulated crash exception in UtilityBotRuntime")
                            }

                            val reply = handleCommand(text)
                            ctx.telegramApi.sendMessage(token, chatId, reply)
                            ctx.log(LogLevel.INFO, TAG, "Handled utility command '$text' -> reply sent.")
                        }
                    }

                    _state.value = BotRuntimeState.Running(
                        botUsername = username,
                        startedAt = startedAt,
                        pollCount = pollCounter,
                        messageCount = messageCounter,
                        lastActivityAt = System.currentTimeMillis()
                    )
                } catch (c: CancellationException) {
                    break
                } catch (e: RuntimeException) {
                    if (e.message?.contains("Simulated crash") == true) {
                        _state.value = BotRuntimeState.Error(e.message ?: "Crash")
                        throw e
                    }
                    ctx.log(LogLevel.WARN, TAG, "Utility Bot transient error: ${e.message}")
                    delay(3000)
                } catch (e: Exception) {
                    ctx.log(LogLevel.WARN, TAG, "Utility Bot polling transient error: ${e.message}")
                    delay(3000)
                }
            }
        }
    }

    private fun handleCommand(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.startsWith("/start") -> {
                "⚙️ *Welcome to Utility Bot!* 🛠️\n\nCommands:\n" +
                        "• `/ping` - Check response and roundtrip\n" +
                        "• `/uptime` - Show runner uptime\n" +
                        "• `/sysinfo` - Memory, threads, and device status\n" +
                        "• `/echo <text>` - Echo back text\n" +
                        "• `/crash` - Test resilience & crash recovery"
            }
            lower.startsWith("/ping") -> {
                "🏓 *pong* (Android ART runner alive & responsive)"
            }
            lower.startsWith("/uptime") -> {
                val uptimeSeconds = (System.currentTimeMillis() - bootTimestamp) / 1000
                "⏱️ *Uptime*: ${uptimeSeconds}s active in foreground service."
            }
            lower.startsWith("/sysinfo") -> {
                val runtime = Runtime.getRuntime()
                val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                val totalMb = runtime.totalMemory() / (1024 * 1024)
                "📊 *System Diagnostics*:\n" +
                        "• Used Heap: ${usedMb}MB / ${totalMb}MB\n" +
                        "• Active Threads: ${Thread.activeCount()}\n" +
                        "• Architecture: Native ART (Android)\n" +
                        "• Process PID: ${android.os.Process.myPid()}"
            }
            lower.startsWith("/echo") -> {
                val body = text.removePrefix("/echo").trim()
                "📢 Echo: $body"
            }
            else -> "⚙️ Utility Bot received: '$text'. Send `/start` for available commands."
        }
    }

    override suspend fun stop() {
        pollJob?.cancel()
        pollJob = null
        _state.value = BotRuntimeState.Stopped
    }

    companion object {
        private const val TAG = "UtilityBotRuntime"
    }
}
