package com.tapbot.core.runner

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import android.os.PowerManager
import com.tapbot.core.logging.BotLogRepository
import com.tapbot.core.model.LogLevel
import com.tapbot.core.network.TelegramApiClient
import com.tapbot.core.runner.runtime.BotRuntime
import com.tapbot.core.runner.runtime.BotRuntimeState
import com.tapbot.core.runner.runtime.PingPongBotRuntime
import com.tapbot.core.runner.runtime.RuntimeContext
import com.tapbot.core.security.CredentialStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Android Foreground Service hosting the on-device Telegram Bot Runtime.
 * Ensures the bot loop continues executing when the app UI is closed,
 * minimized, or when the screen is locked.
 */
class BotForegroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private lateinit var notificationHelper: NotificationHelper
    private var powerManager: PowerManager? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var activeRuntime: BotRuntime? = null
    private val lifecycleMutex = Mutex()

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        notificationHelper.createNotificationChannel()

        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_START_BOT -> {
                serviceScope.launch { startBotInternal() }
            }
            ACTION_STOP_BOT, ACTION_STOP_ALL -> {
                serviceScope.launch { stopBotInternal() }
            }
            ACTION_RESTART_BOT -> {
                serviceScope.launch {
                    stopBotInternal()
                    startBotInternal()
                }
            }
            else -> {
                // System restart recovery (intent == null on START_STICKY)
                if (wasRunningPersisted()) {
                    ServiceLocator.logRepository?.appendLog(
                        DEFAULT_BOT_ID,
                        LogLevel.INFO,
                        TAG,
                        "System restarted service (START_STICKY). Recovering bot execution..."
                    )
                    serviceScope.launch { startBotInternal() }
                }
            }
        }

        return START_STICKY
    }

    private suspend fun startBotInternal() = lifecycleMutex.withLock {
        if (activeRuntime != null && _runtimeState.value.isRunning) {
            return@withLock
        }

        val credStore = ServiceLocator.pocCredentialStore
        val logRepo = ServiceLocator.logRepository
        val telegramApi = ServiceLocator.telegramApiClient ?: TelegramApiClient()

        val token = credStore?.getToken()
        if (token.isNullOrBlank()) {
            val err = "Cannot start background bot: Telegram Bot Token not found in Keystore."
            logRepo?.appendLog(DEFAULT_BOT_ID, LogLevel.ERROR, TAG, err)
            _runtimeState.value = BotRuntimeState.Error(err)
            return@withLock
        }

        _runtimeState.value = BotRuntimeState.Starting
        persistRunningState(true)

        // Show initial foreground notification
        val initialNotification = notificationHelper.buildBotRunningNotification("Telegram Bot", "Starting runtime...")
        startForeground(NotificationHelper.NOTIFICATION_ID, initialNotification)

        val runtime = PingPongBotRuntime(botId = DEFAULT_BOT_ID)
        activeRuntime = runtime

        // Observe runtime state to update notification & shared state
        serviceScope.launch {
            runtime.state.collect { st ->
                _runtimeState.value = st
                when (st) {
                    is BotRuntimeState.Connected -> {
                        val notif = notificationHelper.buildBotRunningNotification("@${st.botUsername}")
                        startForeground(NotificationHelper.NOTIFICATION_ID, notif)
                    }
                    is BotRuntimeState.Running -> {
                        val notif = notificationHelper.buildBotRunningNotification(
                            "@${st.botUsername}",
                            "Running • Polls: ${st.pollCount} | Msg: ${st.messageCount}"
                        )
                        startForeground(NotificationHelper.NOTIFICATION_ID, notif)
                    }
                    is BotRuntimeState.Error -> {
                        logRepo?.appendLog(DEFAULT_BOT_ID, LogLevel.ERROR, TAG, "Runtime error: ${st.message}")
                    }
                    else -> {}
                }
            }
        }

        val context = RuntimeContext(
            botId = runtime.botId,
            token = token,
            telegramApi = telegramApi,
            log = { level, tag, msg ->
                // Acquire temporary wake lock only during active message reply processing
                if (level == LogLevel.INFO && msg.contains("Received message")) {
                    acquireMessageWakeLock()
                }
                logRepo?.appendLog(runtime.botId, level, tag, msg)
            },
            scope = serviceScope
        )

        runtime.initialize(context)
        runtime.start()
        logRepo?.appendLog(DEFAULT_BOT_ID, LogLevel.INFO, TAG, "Bot runtime active in Foreground Service.")
    }

    private suspend fun stopBotInternal() = lifecycleMutex.withLock {
        persistRunningState(false)
        _runtimeState.value = BotRuntimeState.Stopping

        try {
            activeRuntime?.stop()
        } catch (_: Exception) {}

        activeRuntime = null
        _runtimeState.value = BotRuntimeState.Stopped

        ServiceLocator.logRepository?.appendLog(DEFAULT_BOT_ID, LogLevel.INFO, TAG, "Background service stopped by user.")

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Acquires a temporary partial wake lock for 15 seconds to ensure CPU completes
     * processing an incoming Telegram message and sends the reply, without keeping
     * the CPU awake indefinitely during long-polling socket waits.
     */
    private fun acquireMessageWakeLock() {
        try {
            powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TapBot:MessageReplyWakeLock")?.apply {
                setReferenceCounted(false)
                acquire(15000L /* 15 seconds max */)
            }
        } catch (e: Exception) {
            ServiceLocator.logRepository?.appendLog(DEFAULT_BOT_ID, LogLevel.WARN, TAG, "WakeLock acquisition notice: ${e.message}")
        }
    }

    private fun registerNetworkCallback() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                ServiceLocator.logRepository?.appendLog(DEFAULT_BOT_ID, LogLevel.INFO, TAG, "Network restored. Polling active.")
            }

            override fun onLost(network: Network) {
                ServiceLocator.logRepository?.appendLog(DEFAULT_BOT_ID, LogLevel.WARN, TAG, "Network connection lost. Polling suspended.")
            }
        }
        networkCallback = callback
        connectivityManager?.registerNetworkCallback(request, callback)
    }

    private fun persistRunningState(isRunning: Boolean) {
        prefs.edit().putBoolean(KEY_WAS_RUNNING, isRunning).apply()
    }

    private fun wasRunningPersisted(): Boolean {
        return prefs.getBoolean(KEY_WAS_RUNNING, false)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_BOT = "com.tapbot.action.START_BOT"
        const val ACTION_STOP_BOT = "com.tapbot.action.STOP_BOT"
        const val ACTION_STOP_ALL = "com.tapbot.action.STOP_ALL"
        const val ACTION_RESTART_BOT = "com.tapbot.action.RESTART_BOT"
        const val EXTRA_BOT_ID = "extra_bot_id"
        const val DEFAULT_BOT_ID = "bot_pingpong_poc"
        private const val TAG = "BotForegroundService"
        private const val PREFS_NAME = "tapbot_service_state"
        private const val KEY_WAS_RUNNING = "key_was_running"

        private val _runtimeState = MutableStateFlow<BotRuntimeState>(BotRuntimeState.Stopped)
        val runtimeState: StateFlow<BotRuntimeState> = _runtimeState.asStateFlow()

        internal fun updateStateDirectly(state: BotRuntimeState) {
            _runtimeState.value = state
        }
    }
}

/**
 * ServiceLocator hook for injecting modules into Android Service.
 */
object ServiceLocator {
    var pocCredentialStore: CredentialStore? = null
    var logRepository: BotLogRepository? = null
    var telegramApiClient: TelegramApiClient? = null
}
