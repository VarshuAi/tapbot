package com.tapbot.core.runner.manager

import com.tapbot.core.logging.BotLogRepository
import com.tapbot.core.model.LogLevel
import com.tapbot.core.model.TelegramUser
import com.tapbot.core.network.TelegramApiClient
import com.tapbot.core.runner.runtime.BotRuntime
import com.tapbot.core.runner.runtime.BotRuntimeState
import com.tapbot.core.runner.runtime.PingPongBotRuntime
import com.tapbot.core.runner.runtime.RuntimeContext
import com.tapbot.core.security.CredentialStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Interface coordinating the lifecycle of the active on-device Telegram Bot.
 */
interface BotInstanceManager {
    val activeState: StateFlow<BotRuntimeState>

    suspend fun validateToken(token: String): Result<TelegramUser>
    suspend fun start(): Result<Unit>
    suspend fun stop(): Result<Unit>
    suspend fun restart(): Result<Unit>
}

class DefaultBotInstanceManager(
    private val credentialStore: CredentialStore,
    private val telegramApi: TelegramApiClient,
    private val logRepository: BotLogRepository,
    private val runtimeFactory: () -> BotRuntime = { PingPongBotRuntime() }
) : BotInstanceManager {

    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mutex = Mutex()

    private var activeRuntime: BotRuntime? = null

    private val _activeState = MutableStateFlow<BotRuntimeState>(BotRuntimeState.Stopped)
    override val activeState: StateFlow<BotRuntimeState> = _activeState.asStateFlow()

    override suspend fun validateToken(token: String): Result<TelegramUser> {
        if (token.isBlank()) {
            return Result.failure(IllegalArgumentException("Token cannot be blank"))
        }
        return telegramApi.getMe(token.trim())
    }

    override suspend fun start(): Result<Unit> = mutex.withLock {
        try {
            val token = credentialStore.getToken()
            if (token.isNullOrBlank()) {
                val err = "No Telegram Bot Token saved in Keystore."
                _activeState.value = BotRuntimeState.Error(err)
                return Result.failure(IllegalStateException(err))
            }

            if (activeRuntime != null && activeRuntime?.state?.value?.isRunning == true) {
                return Result.success(Unit)
            }

            val runtime = runtimeFactory()
            activeRuntime = runtime

            // Forward state updates
            managerScope.launch {
                runtime.state.collect { st ->
                    _activeState.value = st
                }
            }

            val context = RuntimeContext(
                botId = runtime.botId,
                token = token,
                telegramApi = telegramApi,
                log = { level, tag, msg -> logRepository.appendLog(runtime.botId, level, tag, msg) },
                scope = managerScope
            )

            runtime.initialize(context)
            runtime.start()

            Result.success(Unit)
        } catch (e: Exception) {
            _activeState.value = BotRuntimeState.Error(e.message ?: "Failed to start bot")
            Result.failure(e)
        }
    }

    override suspend fun stop(): Result<Unit> = mutex.withLock {
        try {
            activeRuntime?.stop()
            activeRuntime = null
            _activeState.value = BotRuntimeState.Stopped
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun restart(): Result<Unit> {
        stop()
        return start()
    }
}
