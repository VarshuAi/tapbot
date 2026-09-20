package com.tapbot.core.runner.manager

import android.content.Context
import android.content.Intent
import android.os.Build
import com.tapbot.core.model.BotInstance
import com.tapbot.core.model.BotInstanceStatus
import com.tapbot.core.model.BotMetadata
import com.tapbot.core.model.CrashState
import com.tapbot.core.model.TelegramUser
import com.tapbot.core.network.DowngradeAttackChecker
import com.tapbot.core.network.TelegramApiClient
import com.tapbot.core.runner.BotForegroundService
import com.tapbot.core.runner.runtime.BotRuntimeState
import com.tapbot.core.security.CredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Interface coordinating the lifecycle and orchestration of multiple independently
 * installed and managed on-device Telegram bots.
 *
 * Enforces:
 * - Duplicate process prevention
 * - Duplicate Telegram long-polling connection prevention
 * - Conflicting installation prevention
 * - Orphaned service and process prevention
 */
interface BotInstanceManager {
    val instances: StateFlow<List<BotInstance>>

    // Phase 3 backwards-compatibility contract
    val activeState: StateFlow<BotRuntimeState>
    suspend fun validateToken(token: String): Result<TelegramUser>
    suspend fun start(): Result<Unit>
    suspend fun stop(): Result<Unit>
    suspend fun restart(): Result<Unit>

    // Multi-bot independent lifecycle operations
    suspend fun install(bot: BotMetadata, packageFile: File): Result<BotInstance>
    suspend fun uninstall(installationId: String): Result<Unit>
    suspend fun start(installationId: String): Result<Unit>
    suspend fun stop(installationId: String): Result<Unit>
    suspend fun restart(installationId: String): Result<Unit>
    suspend fun update(installationId: String, newVersion: String, packageFile: File): Result<BotInstance>

    fun getInstance(installationId: String): BotInstance?
    fun getInstanceByBotId(botId: String): BotInstance?
    fun observeInstance(installationId: String): Flow<BotInstance?>
    fun updateInstanceStatus(installationId: String, status: BotInstanceStatus)
    fun recordCrash(installationId: String, reason: String)
}

/**
 * Default persistent implementation of [BotInstanceManager].
 * Stores installed instance metadata in `instances.json` and controls [BotForegroundService].
 */
class DefaultBotInstanceManager(
    private val context: Context? = null,
    private val storageFile: File? = null,
    private val installDir: File? = null,
    private val credentialStore: CredentialStore,
    private val telegramApi: TelegramApiClient,
    private val serviceLauncher: ((action: String, installationId: String?) -> Unit)? = null
) : BotInstanceManager {

    // Secondary constructor for single-action launcher backwards-compatibility
    constructor(
        context: Context? = null,
        credentialStore: CredentialStore,
        telegramApi: TelegramApiClient,
        serviceLauncher: ((action: String) -> Unit)? = null
    ) : this(
        context = context,
        storageFile = null,
        installDir = null,
        credentialStore = credentialStore,
        telegramApi = telegramApi,
        serviceLauncher = if (serviceLauncher != null) { action, _ -> serviceLauncher.invoke(action) } else null
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val mutex = Mutex()
    private val _instances = MutableStateFlow<List<BotInstance>>(emptyList())
    override val instances: StateFlow<List<BotInstance>> = _instances.asStateFlow()

    override val activeState: StateFlow<BotRuntimeState> = BotForegroundService.runtimeState

    init {
        loadFromDisk()
    }

    override suspend fun validateToken(token: String): Result<TelegramUser> {
        if (token.isBlank()) {
            return Result.failure(IllegalArgumentException("Token cannot be blank"))
        }
        return telegramApi.getMe(token.trim())
    }

    // -------------------------------------------------------------------------
    // Backwards compatibility single-bot methods (defaults to active/first bot)
    // -------------------------------------------------------------------------

    override suspend fun start(): Result<Unit> {
        val target = _instances.value.firstOrNull()
        return if (target != null) {
            start(target.installationId)
        } else {
            val token = credentialStore.getToken()
            if (token.isNullOrBlank()) {
                val err = "No Telegram Bot Token saved in Keystore."
                BotForegroundService.updateStateDirectly(BotRuntimeState.Error(err))
                return Result.failure(IllegalStateException(err))
            }
            launchService(BotForegroundService.ACTION_START_BOT, null)
            Result.success(Unit)
        }
    }

    override suspend fun stop(): Result<Unit> {
        val target = _instances.value.firstOrNull { it.status.isRunning }
        return if (target != null) {
            stop(target.installationId)
        } else {
            launchService(BotForegroundService.ACTION_STOP_BOT, null)
            Result.success(Unit)
        }
    }

    override suspend fun restart(): Result<Unit> {
        val target = _instances.value.firstOrNull()
        return if (target != null) {
            restart(target.installationId)
        } else {
            launchService(BotForegroundService.ACTION_RESTART_BOT, null)
            Result.success(Unit)
        }
    }

    // -------------------------------------------------------------------------
    // Multi-bot independent operations
    // -------------------------------------------------------------------------

    override suspend fun install(bot: BotMetadata, packageFile: File): Result<BotInstance> = mutex.withLock {
        runCatching {
            // Rule 1: Conflicting Installation Prevention
            val existing = _instances.value.find { it.botId == bot.id }
            if (existing != null) {
                throw IllegalStateException("Conflicting installation: Bot '${bot.name}' is already installed (ID: ${existing.installationId})")
            }

            val installationId = "inst_${bot.id}"
            val targetPackage = if (installDir != null) {
                installDir.mkdirs()
                val dest = File(installDir, "${bot.id}_${bot.version}.botpkg")
                if (packageFile.absolutePath != dest.absolutePath) {
                    packageFile.copyTo(dest, overwrite = true)
                }
                dest
            } else {
                packageFile
            }

            val instance = BotInstance(
                installationId = installationId,
                botId = bot.id,
                name = bot.name,
                version = bot.version,
                status = BotInstanceStatus.Stopped,
                packagePath = targetPackage.absolutePath,
                runtimeType = bot.packageInfo.runtimeType,
                installedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            val updated = _instances.value.toMutableList()
            updated.add(instance)
            _instances.value = updated
            saveToDisk()
            instance
        }
    }

    override suspend fun uninstall(installationId: String): Result<Unit> = mutex.withLock {
        runCatching {
            val instance = _instances.value.find { it.installationId == installationId }
                ?: throw NoSuchElementException("Instance not found: $installationId")

            // Rule 2: Orphaned Process Prevention: stop if active
            if (instance.status.isRunning || instance.status is BotInstanceStatus.Starting) {
                stopLocked(installationId)
            }

            // Purge local package
            instance.packagePath?.let { path ->
                val f = File(path)
                if (f.exists()) f.delete()
            }

            // Wipe credentials
            credentialStore.deleteCredentials(instance.botId)

            _instances.value = _instances.value.filter { it.installationId != installationId }
            saveToDisk()
        }
    }

    override suspend fun start(installationId: String): Result<Unit> = mutex.withLock {
        runCatching {
            startLocked(installationId)
        }
    }

    override suspend fun stop(installationId: String): Result<Unit> = mutex.withLock {
        runCatching {
            stopLocked(installationId)
        }
    }

    override suspend fun restart(installationId: String): Result<Unit> = mutex.withLock {
        runCatching {
            stopLocked(installationId)
            startLocked(installationId)
        }
    }

    override suspend fun update(
        installationId: String,
        newVersion: String,
        packageFile: File
    ): Result<BotInstance> = mutex.withLock {
        runCatching {
            val current = _instances.value.find { it.installationId == installationId }
                ?: throw NoSuchElementException("Instance not found: $installationId")

            // Rule 5: Downgrade Attack Prevention
            if (DowngradeAttackChecker.isDowngrade(currentInstalledVersion = current.version, candidateVersion = newVersion)) {
                throw SecurityException("Downgrade attack detected: Installed version is ${current.version}, cannot install older version $newVersion")
            }

            val wasRunning = current.status.isRunning
            if (wasRunning) {
                stopLocked(installationId)
            }

            val targetPackage = if (installDir != null) {
                installDir.mkdirs()
                val dest = File(installDir, "${current.botId}_${newVersion}.botpkg")
                packageFile.copyTo(dest, overwrite = true)
                dest
            } else {
                packageFile
            }

            val updated = current.copy(
                version = newVersion,
                packagePath = targetPackage.absolutePath,
                updatedAt = System.currentTimeMillis()
            )

            _instances.value = _instances.value.map {
                if (it.installationId == installationId) updated else it
            }
            saveToDisk()

            if (wasRunning) {
                startLocked(installationId)
            }

            updated
        }
    }

    private suspend fun startLocked(installationId: String) {
        val instance = _instances.value.find { it.installationId == installationId }
            ?: throw NoSuchElementException("Instance not found: $installationId")

        // Rule 3: Duplicate Process Prevention
        if (instance.status.isRunning || instance.status is BotInstanceStatus.Starting) {
            return // Already running or starting
        }

        // Fetch Token
        val token = credentialStore.getCredential(instance.botId, "bot_token")
            ?: credentialStore.getCredential(instance.botId, "telegram_bot_token")
            ?: credentialStore.getToken()
            ?: throw IllegalStateException("Cannot start '${instance.name}': Telegram Bot Token not found in Keystore.")

        // Rule 4: Duplicate Telegram Connection Prevention
        for (other in _instances.value) {
            if (other.installationId != installationId && (other.status.isRunning || other.status is BotInstanceStatus.Starting)) {
                val otherToken = credentialStore.getCredential(other.botId, "bot_token")
                    ?: credentialStore.getCredential(other.botId, "telegram_bot_token")
                    ?: credentialStore.getToken()
                if (otherToken == token) {
                    throw IllegalStateException("Duplicate Telegram connection prevented: Token is already in active use by '${other.name}'")
                }
            }
        }

        updateStatusInternal(installationId, BotInstanceStatus.Starting)
        launchService(BotForegroundService.ACTION_START_BOT, installationId)
    }

    private fun stopLocked(installationId: String) {
        val instance = _instances.value.find { it.installationId == installationId }
            ?: throw NoSuchElementException("Instance not found: $installationId")

        updateStatusInternal(installationId, BotInstanceStatus.Stopped)
        launchService(BotForegroundService.ACTION_STOP_BOT, installationId)
    }

    override fun getInstance(installationId: String): BotInstance? {
        return _instances.value.find { it.installationId == installationId }
    }

    override fun getInstanceByBotId(botId: String): BotInstance? {
        return _instances.value.find { it.botId == botId }
    }

    override fun observeInstance(installationId: String): Flow<BotInstance?> {
        return instances.map { list -> list.find { it.installationId == installationId } }
    }

    override fun updateInstanceStatus(installationId: String, status: BotInstanceStatus) {
        updateStatusInternal(installationId, status)
    }

    override fun recordCrash(installationId: String, reason: String) {
        val current = _instances.value.find { it.installationId == installationId } ?: return
        val count = (current.crashState?.crashCount ?: 0) + 1
        val newCrashState = CrashState(
            crashCount = count,
            lastCrashReason = reason,
            lastCrashTimestamp = System.currentTimeMillis()
        )
        val crashedStatus = BotInstanceStatus.Crashed(
            reason = reason,
            timestamp = System.currentTimeMillis(),
            crashCount = count
        )
        _instances.value = _instances.value.map {
            if (it.installationId == installationId) {
                it.copy(status = crashedStatus, crashState = newCrashState)
            } else it
        }
        saveToDisk()
    }

    private fun updateStatusInternal(installationId: String, status: BotInstanceStatus) {
        _instances.value = _instances.value.map {
            if (it.installationId == installationId) {
                val startedAt = if (status.isRunning && it.startedAt == null) {
                    System.currentTimeMillis()
                } else if (status.isStopped) {
                    null
                } else {
                    it.startedAt
                }
                it.copy(status = status, startedAt = startedAt)
            } else it
        }
        saveToDisk()
    }

    private fun launchService(action: String, installationId: String?) {
        if (serviceLauncher != null) {
            serviceLauncher.invoke(action, installationId)
            return
        }

        val ctx = context ?: return
        val intent = Intent(ctx, BotForegroundService::class.java).apply {
            this.action = action
            if (installationId != null) {
                putExtra(BotForegroundService.EXTRA_INSTALLATION_ID, installationId)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
    }

    private fun loadFromDisk() {
        val file = storageFile ?: return
        if (!file.exists()) return
        try {
            val text = file.readText()
            if (text.isNotBlank()) {
                val list = json.decodeFromString(ListSerializer(BotInstance.serializer()), text)
                _instances.value = list
            }
        } catch (_: Exception) {}
    }

    private fun saveToDisk() {
        val file = storageFile ?: return
        try {
            val text = json.encodeToString(ListSerializer(BotInstance.serializer()), _instances.value)
            file.writeText(text)
        } catch (_: Exception) {}
    }
}
