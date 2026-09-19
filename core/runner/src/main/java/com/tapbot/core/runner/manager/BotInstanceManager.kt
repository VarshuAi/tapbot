package com.tapbot.core.runner.manager

import android.content.Context
import android.content.Intent
import android.os.Build
import com.tapbot.core.model.TelegramUser
import com.tapbot.core.network.TelegramApiClient
import com.tapbot.core.runner.BotForegroundService
import com.tapbot.core.runner.runtime.BotRuntimeState
import com.tapbot.core.security.CredentialStore
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface coordinating the lifecycle of the active on-device Telegram Bot.
 * In Phase 3, this delegates to [BotForegroundService] to guarantee execution continues
 * even when the Compose UI is closed or the screen is locked.
 */
interface BotInstanceManager {
    val activeState: StateFlow<BotRuntimeState>

    suspend fun validateToken(token: String): Result<TelegramUser>
    suspend fun start(): Result<Unit>
    suspend fun stop(): Result<Unit>
    suspend fun restart(): Result<Unit>
}

class DefaultBotInstanceManager(
    private val context: Context? = null,
    private val credentialStore: CredentialStore,
    private val telegramApi: TelegramApiClient,
    private val serviceLauncher: ((action: String) -> Unit)? = null
) : BotInstanceManager {

    override val activeState: StateFlow<BotRuntimeState> = BotForegroundService.runtimeState

    override suspend fun validateToken(token: String): Result<TelegramUser> {
        if (token.isBlank()) {
            return Result.failure(IllegalArgumentException("Token cannot be blank"))
        }
        return telegramApi.getMe(token.trim())
    }

    override suspend fun start(): Result<Unit> {
        val token = credentialStore.getToken()
        if (token.isNullOrBlank()) {
            val err = "No Telegram Bot Token saved in Keystore."
            BotForegroundService.updateStateDirectly(BotRuntimeState.Error(err))
            return Result.failure(IllegalStateException(err))
        }

        launchService(BotForegroundService.ACTION_START_BOT, isForeground = true)
        return Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> {
        launchService(BotForegroundService.ACTION_STOP_BOT, isForeground = false)
        return Result.success(Unit)
    }

    override suspend fun restart(): Result<Unit> {
        launchService(BotForegroundService.ACTION_RESTART_BOT, isForeground = true)
        return Result.success(Unit)
    }

    private fun launchService(action: String, isForeground: Boolean) {
        if (serviceLauncher != null) {
            serviceLauncher.invoke(action)
            return
        }

        val ctx = context ?: return
        val intent = Intent(ctx, BotForegroundService::class.java).apply {
            this.action = action
        }
        if (isForeground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
    }
}
