package com.tapbot.core.runner

import android.content.Context
import android.content.Intent
import android.os.Build
import com.tapbot.core.model.BotRunState
import com.tapbot.core.runner.runtime.BotRuntimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface BotServiceController {
    fun startBot(botId: String)
    fun stopBot(botId: String)
    fun stopAll()
    fun getBotRunState(botId: String): StateFlow<BotRunState>
}

class AndroidBotServiceController(
    private val context: Context
) : BotServiceController {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val botFlows = java.util.concurrent.ConcurrentHashMap<String, MutableStateFlow<BotRunState>>()

    override fun startBot(botId: String) {
        val intent = Intent(context, BotForegroundService::class.java).apply {
            action = BotForegroundService.ACTION_START_BOT
            putExtra(BotForegroundService.EXTRA_BOT_ID, botId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    override fun stopBot(botId: String) {
        val intent = Intent(context, BotForegroundService::class.java).apply {
            action = BotForegroundService.ACTION_STOP_BOT
            putExtra(BotForegroundService.EXTRA_BOT_ID, botId)
        }
        context.startService(intent)
    }

    override fun stopAll() {
        val intent = Intent(context, BotForegroundService::class.java).apply {
            action = BotForegroundService.ACTION_STOP_ALL
        }
        context.startService(intent)
    }

    override fun getBotRunState(botId: String): StateFlow<BotRunState> {
        return botFlows.computeIfAbsent(botId) { id ->
            val initial = BotForegroundService.botStates.value[id] ?: BotRunState.Stopped
            val flow = MutableStateFlow<BotRunState>(initial)
            scope.launch {
                BotForegroundService.botStates.collect { states ->
                    flow.value = states[id] ?: BotRunState.Stopped
                }
            }
            flow
        }.asStateFlow()
    }
}
