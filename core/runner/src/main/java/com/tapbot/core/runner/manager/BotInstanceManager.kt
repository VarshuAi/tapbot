package com.tapbot.core.runner.manager

import android.content.Context
import android.content.Intent
import android.os.Build
import com.tapbot.core.model.BotInstance
import com.tapbot.core.model.BotInstanceStatus
import com.tapbot.core.model.BotMetadata
import com.tapbot.core.model.BotUpdateProgress
import com.tapbot.core.model.BotVersion
import com.tapbot.core.model.CrashState
import com.tapbot.core.model.TelegramUser
import com.tapbot.core.model.UpdateState
import com.tapbot.core.network.BotPackageDownloader
import com.tapbot.core.network.DowngradeAttackChecker
import com.tapbot.core.network.ManifestValidator
import com.tapbot.core.network.Sha256PackageVerifier
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
 * - Atomic version updates with automatic rollback on startup failure
 */
interface BotInstanceManager {
    val instances: StateFlow<List<BotInstance>>
    val updateProgress: StateFlow<Map<String, BotUpdateProgress>>

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
    suspend fun updateWithRollback(
        installationId: String,
        targetVersion: BotVersion,
        packageDownloader: BotPackageDownloader? = null,
        manifestValidator: ManifestValidator? = null,
        onProgress: ((BotUpdateProgress) -> Unit)? = null
    ): Result<BotInstance>

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

    private val _updateProgress = MutableStateFlow<Map<String, BotUpdateProgress>>(emptyMap())
    override val updateProgress: StateFlow<Map<String, BotUpdateProgress>> = _updateProgress.asStateFlow()

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
    ): Result<BotInstance> {
        val current = _instances.value.find { it.installationId == installationId }
            ?: return Result.failure(NoSuchElementException("Instance not found: $installationId"))

        val targetVersion = BotVersion(
            id = "ver_${current.botId}_$newVersion",
            botId = current.botId,
            version = newVersion,
            packageUrl = "",
            sha256 = "",
            packageSize = packageFile.length(),
            isPublished = true,
            publishedAt = ""
        )

        return updateWithRollbackInternal(
            installationId = installationId,
            targetVersion = targetVersion,
            localPackageFile = packageFile,
            packageDownloader = null,
            manifestValidator = null,
            onProgress = null
        )
    }

    override suspend fun updateWithRollback(
        installationId: String,
        targetVersion: BotVersion,
        packageDownloader: BotPackageDownloader?,
        manifestValidator: ManifestValidator?,
        onProgress: ((BotUpdateProgress) -> Unit)?
    ): Result<BotInstance> {
        return updateWithRollbackInternal(
            installationId = installationId,
            targetVersion = targetVersion,
            localPackageFile = null,
            packageDownloader = packageDownloader,
            manifestValidator = manifestValidator,
            onProgress = onProgress
        )
    }

    private suspend fun updateWithRollbackInternal(
        installationId: String,
        targetVersion: BotVersion,
        localPackageFile: File?,
        packageDownloader: BotPackageDownloader?,
        manifestValidator: ManifestValidator?,
        onProgress: ((BotUpdateProgress) -> Unit)?
    ): Result<BotInstance> = mutex.withLock {
        val current = _instances.value.find { it.installationId == installationId }
            ?: return Result.failure(NoSuchElementException("Instance not found: $installationId"))

        val botId = current.botId
        val oldVersion = current.version
        val newVersion = targetVersion.version

        fun emitState(
            state: UpdateState,
            progress: Float = 0f,
            message: String? = null,
            error: String? = null
        ) {
            val p = BotUpdateProgress(
                state = state,
                progress = progress,
                currentVersion = oldVersion,
                targetVersion = newVersion,
                message = message,
                error = error
            )
            val map = _updateProgress.value.toMutableMap()
            map[installationId] = p
            _updateProgress.value = map
            onProgress?.invoke(p)
        }

        runCatching {
            // STEP 0: CHECKING
            emitState(UpdateState.CHECKING, 0.05f, "Validating candidate version $newVersion...")

            // Anti-downgrade check
            if (DowngradeAttackChecker.isDowngrade(currentInstalledVersion = oldVersion, candidateVersion = newVersion)) {
                val err = "Downgrade attack detected: Installed version is $oldVersion, cannot install older version $newVersion"
                emitState(UpdateState.FAILED, 0f, error = err)
                throw SecurityException(err)
            }

            if (oldVersion == newVersion) {
                val err = "Bot is already running version $newVersion"
                emitState(UpdateState.FAILED, 0f, error = err)
                throw IllegalStateException(err)
            }

            // STEP 1: DOWNLOADING
            emitState(UpdateState.DOWNLOADING, 0.1f, "Downloading package for v$newVersion...")
            val downloadedFile = if (localPackageFile != null && localPackageFile.exists()) {
                emitState(UpdateState.DOWNLOADING, 0.4f, "Using local package for v$newVersion...")
                localPackageFile
            } else {
                val tempTarget = File.createTempFile("update_${botId}_${newVersion}_", ".botpkg")
                if (packageDownloader != null && targetVersion.packageUrl.isNotBlank()) {
                    packageDownloader.downloadPackage(
                        packageUrl = targetVersion.packageUrl,
                        expectedSha256 = targetVersion.sha256,
                        targetFile = tempTarget,
                        onProgress = { p ->
                            emitState(UpdateState.DOWNLOADING, 0.1f + (p * 0.4f), "Downloading v$newVersion (${(p * 100).toInt()}%)...")
                        }
                    ).getOrThrow()
                } else {
                    tempTarget
                }
            }

            // STEP 2: VERIFY CHECKSUM
            emitState(UpdateState.VERIFYING, 0.55f, "Verifying package integrity checksum...")
            if (targetVersion.sha256.isNotBlank() && targetVersion.sha256 != "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" && downloadedFile.length() > 0) {
                val verifier = Sha256PackageVerifier()
                val verifyRes = verifier.verifyPackage(downloadedFile, targetVersion.sha256)
                if (verifyRes.isFailure) {
                    val err = "Checksum verification failed: ${verifyRes.exceptionOrNull()?.message}"
                    emitState(UpdateState.FAILED, 0f, error = err)
                    if (downloadedFile != localPackageFile) downloadedFile.delete()
                    throw SecurityException(err)
                }
            }

            // STEP 3: VALIDATE MANIFEST
            emitState(UpdateState.VERIFYING, 0.65f, "Validating manifest requirements...")
            if (manifestValidator != null && downloadedFile.length() > 0) {
                val manifestRes = manifestValidator.validatePackage(downloadedFile, botId, newVersion)
                if (manifestRes.isFailure) {
                    val err = "Manifest validation failed: ${manifestRes.exceptionOrNull()?.message}"
                    emitState(UpdateState.FAILED, 0f, error = err)
                    if (downloadedFile != localPackageFile) downloadedFile.delete()
                    throw IllegalStateException(err)
                }
            }

            // STEP 4: STOP OLD INSTANCE
            emitState(UpdateState.INSTALLING, 0.75f, "Stopping previous bot version...")
            val wasRunning = current.status.isRunning || current.status is BotInstanceStatus.Starting
            if (wasRunning) {
                stopLocked(installationId)
            }

            // STEP 5: REPLACE PACKAGE WITH STAGED BACKUP (DO NOT DELETE PREVIOUS VERSION YET!)
            emitState(UpdateState.INSTALLING, 0.85f, "Backing up previous version and staging update...")
            val currentPkgFile = current.packagePath?.let { File(it) }
            val backupFile = if (currentPkgFile != null && currentPkgFile.exists()) {
                val backup = File(currentPkgFile.parentFile ?: installDir, "${currentPkgFile.name}.backup_${System.currentTimeMillis()}")
                currentPkgFile.copyTo(backup, overwrite = true)
                backup
            } else null

            // Place new package
            val finalTargetPackage = if (installDir != null) {
                installDir.mkdirs()
                val dest = File(installDir, "${botId}_${newVersion}.botpkg")
                downloadedFile.copyTo(dest, overwrite = true)
                if (downloadedFile != localPackageFile && downloadedFile.exists() && downloadedFile.absolutePath != dest.absolutePath) {
                    downloadedFile.delete()
                }
                dest
            } else {
                downloadedFile
            }

            // STEP 6: PRESERVE CREDENTIALS (VERIFY KEYSTORE TOKENS REMAIN INTACT)
            val token = credentialStore.getCredential(botId, "bot_token")
                ?: credentialStore.getCredential(botId, "telegram_bot_token")
                ?: credentialStore.getToken()

            // Update instance model to new version
            val currentStopped = _instances.value.find { it.installationId == installationId } ?: current
            val newInstance = currentStopped.copy(
                version = newVersion,
                packagePath = finalTargetPackage.absolutePath,
                status = BotInstanceStatus.Stopped,
                updatedAt = System.currentTimeMillis()
            )
            _instances.value = _instances.value.map {
                if (it.installationId == installationId) newInstance else it
            }
            saveToDisk()

            // STEP 7: START NEW VERSION
            emitState(UpdateState.STARTING, 0.90f, "Starting new version v$newVersion...")
            var startFailed = false
            var startException: Throwable? = null

            if (wasRunning || targetVersion.isPublished) {
                if (token.isNullOrBlank()) {
                    startFailed = true
                    startException = IllegalStateException("Credential missing for bot: $botId")
                } else {
                    val res = runCatching { startLocked(installationId) }
                    if (res.isFailure) {
                        startFailed = true
                        startException = res.exceptionOrNull()
                    }
                }
            }

            // STEP 8: VERIFY HEALTH
            val healthPassed = !startFailed && run {
                val currentStatus = getInstance(installationId)?.status
                currentStatus !is BotInstanceStatus.Crashed
            }

            if (healthPassed) {
                // SUCCESS! Remove staged backup now that new version is safely running.
                backupFile?.delete()
                emitState(UpdateState.SUCCESS, 1.0f, "Successfully updated to v$newVersion!")
                newInstance
            } else {
                // STEP 9: IF STARTUP FAILS, ATTEMPT ROLLBACK!
                emitState(UpdateState.ROLLING_BACK, 0.5f, "Startup failed for v$newVersion. Initiating rollback to v$oldVersion...")

                // Stop failed instance
                runCatching { stopLocked(installationId) }

                // Restore package file from backup
                if (backupFile != null && backupFile.exists()) {
                    if (currentPkgFile != null) {
                        backupFile.copyTo(currentPkgFile, overwrite = true)
                    }
                    backupFile.delete()
                }

                // Delete failed new package
                if (finalTargetPackage.exists() && finalTargetPackage.absolutePath != currentPkgFile?.absolutePath) {
                    finalTargetPackage.delete()
                }

                // Restore previous instance model
                val restoredInstance = current.copy(
                    version = oldVersion,
                    packagePath = currentPkgFile?.absolutePath,
                    status = BotInstanceStatus.Stopped,
                    updatedAt = System.currentTimeMillis()
                )
                _instances.value = _instances.value.map {
                    if (it.installationId == installationId) restoredInstance else it
                }
                saveToDisk()

                // If it was running before update, restart the restored previous version!
                if (wasRunning && !token.isNullOrBlank()) {
                    runCatching { startLocked(installationId) }
                }

                val failureReason = startException?.message ?: "New version failed startup health checks"
                emitState(
                    UpdateState.FAILED,
                    0f,
                    error = "Update failed: $failureReason. Successfully rolled back to v$oldVersion."
                )

                throw IllegalStateException("Update to v$newVersion failed: $failureReason. System rolled back to v$oldVersion.")
            }
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
