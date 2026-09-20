package com.tapbot.feature.botdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tapbot.core.logging.BotLogRepository
import com.tapbot.core.model.BotMetadata
import com.tapbot.core.network.BotPackageDownloader
import com.tapbot.core.network.CatalogApi
import com.tapbot.core.network.CatalogRepository
import com.tapbot.core.network.DowngradeAttackChecker
import com.tapbot.core.network.LocalBotInstallationManager
import com.tapbot.core.network.OfflineFirstCatalogRepository
import com.tapbot.core.runner.BotServiceController
import com.tapbot.core.security.SecretRedactor
import com.tapbot.core.security.SecureCredentialStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class BotDetailViewModel(
    private val botId: String,
    private val catalogRepository: CatalogRepository,
    private val credentialStore: SecureCredentialStore,
    private val botServiceController: BotServiceController,
    private val logRepository: BotLogRepository,
    private val packageDownloader: BotPackageDownloader? = null,
    private val installationManager: LocalBotInstallationManager? = null,
    private val appVersionCode: Int = 1
) : ViewModel() {

    // Secondary constructor accepting CatalogApi for backward compatibility
    constructor(
        botId: String,
        catalogApi: CatalogApi,
        credentialStore: SecureCredentialStore,
        botServiceController: BotServiceController,
        logRepository: BotLogRepository
    ) : this(
        botId = botId,
        catalogRepository = OfflineFirstCatalogRepository(
            remoteSource = object : com.tapbot.core.network.RemoteBotDataSource {
                override suspend fun getPublishedBots(category: String?, search: String?) = catalogApi.getBots()
                override suspend fun getBotDetails(botId: String) = catalogApi.getBotDetails(botId)
                override suspend fun getCategories() = Result.success(listOf("All", "Utilities", "Media", "Productivity"))
                override suspend fun getBotVersions(botId: String) = catalogApi.getBotVersions(botId)
                override suspend fun getLatestVersion(botId: String) = catalogApi.getLatestVersion(botId)
            }
        ),
        credentialStore = credentialStore,
        botServiceController = botServiceController,
        logRepository = logRepository,
        packageDownloader = null,
        installationManager = null
    )

    private val _uiState = MutableStateFlow<BotDetailUiState>(BotDetailUiState.Loading)
    val uiState: StateFlow<BotDetailUiState> = _uiState.asStateFlow()

    init {
        loadBotData()
        observeRunState()
        observeLogs()
    }

    private fun loadBotData() {
        viewModelScope.launch {
            _uiState.value = BotDetailUiState.Loading
            catalogRepository.getBotDetails(botId)
                .onSuccess { bot ->
                    val savedCredentials = credentialStore.getAllCredentials(botId)
                    val installed = installationManager?.isInstalled(bot.id) ?: (packageDownloader == null)
                    val installedVer = installationManager?.getInstalledVersion(bot.id)

                    _uiState.value = BotDetailUiState.Success(
                        bot = bot,
                        isInstalled = installed,
                        installedVersion = installedVer,
                        credentials = savedCredentials,
                        isConfiguring = savedCredentials.isEmpty(),
                        runState = botServiceController.getBotRunState(botId).value,
                        logs = logRepository.getLogStream(botId).value
                    )
                }
                .onFailure { err ->
                    _uiState.value = BotDetailUiState.Error(err.message ?: "Failed to load bot details")
                }
        }
    }

    /**
     * Executes the 8-Step Installation Flow:
     * 1. Retrieve manifest
     * 2. Check compatibility (minimumAppVersion, runtime)
     * 3. Check installed version & Downgrade attack protection
     * 4. Download package with progress
     * 5. Verify SHA-256 checksum
     * 6. Install locally
     * 7. Store installed metadata
     * 8. Show configuration screen (does NOT start automatically)
     */
    fun installBot() {
        val current = _uiState.value as? BotDetailUiState.Success ?: return

        viewModelScope.launch {
            _uiState.value = current.copy(isInstalling = true, installProgress = 0.05f, installError = null)

            val bot = current.bot

            // Step 2: Check compatibility
            if (bot.minimumAppVersion > appVersionCode) {
                _uiState.value = current.copy(
                    isInstalling = false,
                    installError = "App update required (Bot requires App v${bot.minimumAppVersion}, current is v$appVersionCode)"
                )
                return@launch
            }

            if (!bot.packageInfo.runtimeType.equals("native_art", ignoreCase = true)) {
                _uiState.value = current.copy(
                    isInstalling = false,
                    installError = "Unsupported runtime: ${bot.packageInfo.runtimeType}"
                )
                return@launch
            }

            // Step 3: Check installed version & Downgrade attack protection
            val currentInstalledVersion = installationManager?.getInstalledVersion(bot.id)
            if (currentInstalledVersion != null && DowngradeAttackChecker.isDowngrade(currentInstalledVersion, bot.version)) {
                _uiState.value = current.copy(
                    isInstalling = false,
                    installError = "Package downgrade rejected: Candidate v${bot.version} is older than installed v$currentInstalledVersion"
                )
                return@launch
            }

            if (currentInstalledVersion == bot.version && installationManager?.isInstalled(bot.id) == true) {
                _uiState.value = current.copy(
                    isInstalling = false,
                    isInstalled = true,
                    installedVersion = currentInstalledVersion,
                    installProgress = 1.0f
                )
                return@launch
            }

            // Step 4 & 5: Download package & verify SHA-256
            val tempTarget = File.createTempFile("tapbot_${bot.id}_", ".botpkg")
            val downloadResult = if (packageDownloader != null && bot.packageInfo.packageUrl.isNotBlank()) {
                packageDownloader.downloadPackage(
                    packageUrl = bot.packageInfo.packageUrl,
                    expectedSha256 = bot.packageInfo.sha256Checksum,
                    targetFile = tempTarget,
                    onProgress = { p ->
                        _uiState.update { st ->
                            if (st is BotDetailUiState.Success) st.copy(installProgress = p) else st
                        }
                    }
                )
            } else {
                Result.success(tempTarget)
            }

            downloadResult
                .onSuccess { packageArchive ->
                    // Step 6 & 7: Install locally & store metadata
                    installationManager?.installBot(bot, packageArchive)

                    // Step 8: Show configuration screen
                    _uiState.value = current.copy(
                        isInstalled = true,
                        isInstalling = false,
                        installedVersion = bot.version,
                        installProgress = 1.0f,
                        isConfiguring = true
                    )
                }
                .onFailure { err ->
                    _uiState.value = current.copy(
                        isInstalling = false,
                        installError = err.message ?: "Package verification or download failed"
                    )
                }
        }
    }

    private fun observeRunState() {
        viewModelScope.launch {
            botServiceController.getBotRunState(botId).collect { state ->
                _uiState.update { current ->
                    if (current is BotDetailUiState.Success) {
                        current.copy(runState = state)
                    } else current
                }
            }
        }
    }

    private fun observeLogs() {
        viewModelScope.launch {
            logRepository.getLogStream(botId).collect { logs ->
                _uiState.update { current ->
                    if (current is BotDetailUiState.Success) {
                        current.copy(logs = logs)
                    } else current
                }
            }
        }
    }

    /**
     * Validates all required credentials against format rules (e.g. Telegram token regex).
     * @return true if all credentials are valid; false and sets error state if invalid/missing.
     */
    fun validateCredentials(): Boolean {
        val current = _uiState.value as? BotDetailUiState.Success ?: return false
        val errors = mutableMapOf<String, String>()
        for (spec in current.bot.requiredCredentials) {
            val value = current.credentials[spec.key]?.trim().orEmpty()
            if (spec.isRequired && value.isBlank()) {
                errors[spec.key] = "${spec.label} is required"
            } else if (value.isNotBlank() && (spec.key.equals("bot_token", ignoreCase = true) || spec.key.equals("telegram_bot_token", ignoreCase = true))) {
                val tgRegex = Regex("""^\d{5,16}:[a-zA-Z0-9_-]{6,64}$""")
                if (!tgRegex.matches(value)) {
                    errors[spec.key] = "Invalid Telegram Bot Token format (e.g. 123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ)"
                }
            }
        }
        if (errors.isNotEmpty()) {
            _uiState.update {
                if (it is BotDetailUiState.Success) {
                    it.copy(
                        credentialErrors = errors,
                        generalCredentialError = "Please correct the credential errors above before starting the bot."
                    )
                } else it
            }
            return false
        }
        _uiState.update {
            if (it is BotDetailUiState.Success) {
                it.copy(credentialErrors = emptyMap(), generalCredentialError = null)
            } else it
        }
        return true
    }

    fun updateCredential(key: String, value: String) {
        val current = _uiState.value as? BotDetailUiState.Success ?: return
        val updated = current.credentials.toMutableMap().apply { put(key, value) }
        val updatedErrors = current.credentialErrors.toMutableMap().apply { remove(key) }
        _uiState.value = current.copy(
            credentials = updated,
            credentialErrors = updatedErrors,
            generalCredentialError = null,
            credentialSaveSuccess = false
        )
    }

    fun saveCredentials(): Boolean {
        val current = _uiState.value as? BotDetailUiState.Success ?: return false
        if (!validateCredentials()) {
            return false
        }
        viewModelScope.launch {
            _uiState.value = current.copy(isSavingCredentials = true)
            for ((key, value) in current.credentials) {
                if (value.isNotBlank()) {
                    credentialStore.saveCredential(botId, key, value)
                    SecretRedactor.registerSecret(value)
                }
            }
            _uiState.value = current.copy(
                isSavingCredentials = false,
                credentialSaveSuccess = true,
                isConfiguring = false,
                credentialErrors = emptyMap(),
                generalCredentialError = null
            )
        }
        return true
    }

    fun deleteCredentials() {
        val current = _uiState.value as? BotDetailUiState.Success ?: return
        viewModelScope.launch {
            credentialStore.deleteCredentials(botId)
            _uiState.value = current.copy(
                credentials = emptyMap(),
                credentialErrors = emptyMap(),
                generalCredentialError = null,
                credentialSaveSuccess = false,
                isConfiguring = true
            )
        }
    }

    fun setConfiguring(configuring: Boolean) {
        _uiState.update {
            if (it is BotDetailUiState.Success) it.copy(isConfiguring = configuring) else it
        }
    }

    fun startBot() {
        val current = _uiState.value as? BotDetailUiState.Success ?: return
        if (!current.isInstalled) {
            installBot()
            return
        }
        if (!validateCredentials()) {
            return
        }
        saveCredentials()
        botServiceController.startBot(botId)
    }

    fun stopBot() {
        botServiceController.stopBot(botId)
    }

    fun clearLogs() {
        logRepository.clearLogs(botId)
    }

    companion object {
        fun provideFactory(
            botId: String,
            catalogRepository: CatalogRepository,
            credentialStore: SecureCredentialStore,
            botServiceController: BotServiceController,
            logRepository: BotLogRepository,
            packageDownloader: BotPackageDownloader? = null,
            installationManager: LocalBotInstallationManager? = null,
            appVersionCode: Int = 1
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BotDetailViewModel(
                    botId,
                    catalogRepository,
                    credentialStore,
                    botServiceController,
                    logRepository,
                    packageDownloader,
                    installationManager,
                    appVersionCode
                ) as T
            }
        }

        fun provideFactory(
            botId: String,
            catalogApi: CatalogApi,
            credentialStore: SecureCredentialStore,
            botServiceController: BotServiceController,
            logRepository: BotLogRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BotDetailViewModel(
                    botId,
                    catalogApi,
                    credentialStore,
                    botServiceController,
                    logRepository
                ) as T
            }
        }
    }
}
