package com.tapbot.core.runner

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import android.os.PowerManager
import com.tapbot.core.logging.BotLogRepository
import com.tapbot.core.model.BotRunState
import com.tapbot.core.model.LogLevel
import com.tapbot.core.network.TelegramApiClient
import com.tapbot.core.runner.engine.BotEngine
import com.tapbot.core.runner.engine.BotExecutionContext
import com.tapbot.core.runner.engine.EchoBotEngine
import com.tapbot.core.security.SecureCredentialStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Foreground Service responsible for keeping local Telegram Bots alive
 * in the background even when the app UI is dismissed.
 */
class BotForegroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private lateinit var notificationHelper: NotificationHelper
    private var wakeLock: PowerManager.WakeLock? = null
    private var connectivityManager: ConnectivityManager? = null

    private val activeEngines = ConcurrentHashMap<String, BotEngine>()

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        notificationHelper.createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TapBot:RunnerWakeLock").apply {
            setReferenceCounted(false)
        }

        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val botId = intent?.getStringExtra(EXTRA_BOT_ID)

        when (action) {
            ACTION_START_BOT -> {
                if (botId != null) {
                    startBotInternal(botId)
                }
            }
            ACTION_STOP_BOT -> {
                if (botId != null) {
                    stopBotInternal(botId)
                }
            }
            ACTION_STOP_ALL -> {
                stopAllInternal()
            }
        }

        updateForegroundNotification()

        return START_STICKY
    }

    private fun startBotInternal(botId: String) {
        if (activeEngines.containsKey(botId)) {
            return
        }

        wakeLock?.acquire(10 * 60 * 1000L /* 10 mins */)
        setRunState(botId, BotRunState.Starting)

        serviceScope.launch {
            try {
                val credentialStore = ServiceLocator.credentialStore
                val logRepo = ServiceLocator.logRepository
                val telegramApi = ServiceLocator.telegramApiClient

                val credentials = credentialStore?.getAllCredentials(botId).orEmpty()
                logRepo?.appendLog(botId, LogLevel.INFO, TAG, "Starting bot $botId in Foreground Service...")

                // Factory dispatch for engine (Built-in or dynamically loaded)
                val engine: BotEngine = when (botId) {
                    "bot_echo" -> EchoBotEngine(botId)
                    else -> EchoBotEngine(botId) // Fallback / mock
                }

                val context = BotExecutionContext(
                    botId = botId,
                    credentials = credentials,
                    telegramApi = telegramApi ?: TelegramApiClient(),
                    log = { level, tag, msg -> logRepo?.appendLog(botId, level, tag, msg) },
                    scope = serviceScope,
                    context = applicationContext
                )

                engine.initialize(context)
                engine.start()

                activeEngines[botId] = engine
                setRunState(botId, BotRunState.Running(startedAt = System.currentTimeMillis()))
                updateForegroundNotification()
            } catch (e: Exception) {
                setRunState(botId, BotRunState.Error(e.message ?: "Failed to start bot"))
            }
        }
    }

    private fun stopBotInternal(botId: String) {
        val engine = activeEngines.remove(botId)
        serviceScope.launch {
            try {
                engine?.stop()
            } catch (_: Exception) {}
            setRunState(botId, BotRunState.Stopped)
            updateForegroundNotification()

            if (activeEngines.isEmpty()) {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun stopAllInternal() {
        val botIds = activeEngines.keys().toList()
        for (id in botIds) {
            stopBotInternal(id)
        }
    }

    private fun updateForegroundNotification() {
        val count = activeEngines.size
        val names = activeEngines.keys.toList()
        val notification = notificationHelper.buildForegroundNotification(count, names)

        if (count > 0) {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        }
    }

    private fun registerNetworkCallback() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Resume polling if paused
                for ((botId, _) in activeEngines) {
                    val state = getRunState(botId).value
                    if (state is BotRunState.PausedNoNetwork) {
                        setRunState(botId, BotRunState.Running(startedAt = System.currentTimeMillis()))
                    }
                }
            }

            override fun onLost(network: Network) {
                // Mark paused
                for ((botId, _) in activeEngines) {
                    setRunState(botId, BotRunState.PausedNoNetwork())
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_BOT = "com.tapbot.action.START_BOT"
        const val ACTION_STOP_BOT = "com.tapbot.action.STOP_BOT"
        const val ACTION_STOP_ALL = "com.tapbot.action.STOP_ALL"
        const val EXTRA_BOT_ID = "extra_bot_id"
        private const val TAG = "ForegroundService"

        private val runStates = ConcurrentHashMap<String, MutableStateFlow<BotRunState>>()

        fun getRunState(botId: String): StateFlow<BotRunState> {
            return runStates.computeIfAbsent(botId) { MutableStateFlow(BotRunState.Stopped) }.asStateFlow()
        }

        internal fun setRunState(botId: String, state: BotRunState) {
            runStates.computeIfAbsent(botId) { MutableStateFlow(BotRunState.Stopped) }.value = state
        }
    }
}

/**
 * Lightweight ServiceLocator hook for injecting modules into Android Service.
 */
object ServiceLocator {
    var credentialStore: SecureCredentialStore? = null
    var logRepository: BotLogRepository? = null
    var telegramApiClient: TelegramApiClient? = null
}
