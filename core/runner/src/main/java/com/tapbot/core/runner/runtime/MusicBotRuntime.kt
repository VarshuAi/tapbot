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
 * On-device runtime for Music Bot.
 * Manages playlist queue, tracks, playback simulation, and interactive commands:
 * /play, /skip, /queue, /volume, /status
 */
class MusicBotRuntime(
    override val botId: String = "bot_music"
) : BotRuntime {

    private val _state = MutableStateFlow<BotRuntimeState>(BotRuntimeState.Stopped)
    override val state: StateFlow<BotRuntimeState> = _state.asStateFlow()

    private var context: RuntimeContext? = null
    private var pollJob: Job? = null
    private var lastUpdateId: Long? = null

    // Music Player Internal State
    private val playlist = mutableListOf("Lofi Beats - Chill Study", "Synthwave Drive", "Acoustic Morning")
    private var currentTrackIndex = 0
    private var volume = 80
    private var isPlaying = true

    override suspend fun initialize(context: RuntimeContext) {
        this.context = context
    }

    override suspend fun start() {
        val ctx = context ?: throw IllegalStateException("Runtime not initialized")
        val token = ctx.token

        if (token.isBlank()) {
            val err = "Cannot start Music Bot: Empty Telegram Bot Token."
            ctx.log(LogLevel.ERROR, TAG, err)
            _state.value = BotRuntimeState.Error(err)
            return
        }

        _state.value = BotRuntimeState.Starting
        ctx.log(LogLevel.INFO, TAG, "Starting Music Bot runtime on Android...")

        val meResult = ctx.telegramApi.getMe(token)
        if (meResult.isFailure) {
            val err = "Telegram authentication failed: ${meResult.exceptionOrNull()?.message}"
            ctx.log(LogLevel.ERROR, TAG, err)
            _state.value = BotRuntimeState.Error(err)
            return
        }

        val botUser = meResult.getOrThrow()
        val username = botUser.username ?: "MusicBot"
        ctx.log(LogLevel.INFO, TAG, "Music Bot authenticated as @$username")

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
            ctx.log(LogLevel.INFO, TAG, "Music Bot polling active.")

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

                            val reply = handleCommand(text)
                            ctx.telegramApi.sendMessage(token, chatId, reply)
                            ctx.log(LogLevel.INFO, TAG, "Handled music command '$text' -> reply sent.")
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
                } catch (e: Exception) {
                    ctx.log(LogLevel.WARN, TAG, "Music Bot polling transient error: ${e.message}")
                    delay(3000)
                }
            }
        }
    }

    private fun handleCommand(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.startsWith("/start") -> {
                "🎵 *Welcome to Music Bot!* 🎧\n\nCommands:\n" +
                        "• `/play <track>` - Queue and play track\n" +
                        "• `/skip` - Next track\n" +
                        "• `/queue` - View playlist\n" +
                        "• `/volume <0-100>` - Set volume\n" +
                        "• `/status` - Current playback info"
            }
            lower.startsWith("/play") -> {
                val query = text.removePrefix("/play").trim()
                if (query.isNotBlank()) {
                    playlist.add(query)
                    "▶️ Queued and playing: *$query* (Position #${playlist.size})"
                } else {
                    "▶️ Resumed: *${playlist.getOrNull(currentTrackIndex) ?: "No track"}*"
                }
            }
            lower.startsWith("/skip") -> {
                if (playlist.isNotEmpty()) {
                    currentTrackIndex = (currentTrackIndex + 1) % playlist.size
                    "⏭️ Skipped! Now playing: *${playlist[currentTrackIndex]}*"
                } else {
                    "Queue is empty."
                }
            }
            lower.startsWith("/queue") -> {
                val list = playlist.mapIndexed { i, t ->
                    if (i == currentTrackIndex) "▶️ **$t** (Now Playing)" else "  ${i + 1}. $t"
                }.joinToString("\n")
                "🎶 **Current Queue**:\n$list"
            }
            lower.startsWith("/volume") -> {
                val arg = text.removePrefix("/volume").trim().toIntOrNull()
                if (arg != null && arg in 0..100) {
                    volume = arg
                    "🔊 Volume set to $volume%"
                } else {
                    "Current volume: $volume%. Use `/volume <0-100>`"
                }
            }
            lower.startsWith("/status") -> {
                "🎧 **Status**: ${if (isPlaying) "Playing" else "Paused"}\n" +
                        "Track: ${playlist.getOrNull(currentTrackIndex) ?: "None"}\n" +
                        "Volume: $volume%\n" +
                        "Total in queue: ${playlist.size}"
            }
            else -> "🎵 Music Bot received: '$text'. Send `/start` for playback commands."
        }
    }

    override suspend fun stop() {
        pollJob?.cancel()
        pollJob = null
        _state.value = BotRuntimeState.Stopped
    }

    companion object {
        private const val TAG = "MusicBotRuntime"
    }
}
