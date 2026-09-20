package com.tapbot.feature.botdetail

import com.tapbot.core.logging.BotLogRepository
import com.tapbot.core.model.BotCredentialSpec
import com.tapbot.core.model.BotLogEntry
import com.tapbot.core.model.BotMetadata
import com.tapbot.core.model.BotPackageInfo
import com.tapbot.core.model.BotRunState
import com.tapbot.core.model.LogLevel
import com.tapbot.core.network.BotPackageDownloader
import com.tapbot.core.network.CatalogRepository
import com.tapbot.core.network.InstalledBotRecord
import com.tapbot.core.network.LocalBotInstallationManager
import com.tapbot.core.runner.BotServiceController
import com.tapbot.core.security.CredentialStore
import com.tapbot.core.security.SecureCredentialStore
import com.tapbot.core.security.SecretRedactor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class BotCredentialSecurityTest {

    private val testDispatcher = StandardTestDispatcher()

    private val secureBot = BotMetadata(
        id = "secure-tele-bot",
        name = "Secure Telegram Bot",
        summary = "Telegram bot with strict on-device credential storage.",
        description = "Runs local processing without leaking secrets to any server.",
        author = "TapBot Security Team",
        version = "2.0.0",
        iconUrl = "https://cdn.tapbot.dev/bot.png",
        category = "Security",
        tags = listOf("security", "telegram"),
        requiredCredentials = listOf(
            BotCredentialSpec.TELEGRAM_BOT_TOKEN,
            BotCredentialSpec(
                key = "custom_api_key",
                label = "Custom API Key",
                description = "Third party key",
                isSecret = true,
                isRequired = false
            )
        ),
        packageInfo = BotPackageInfo(
            packageUrl = "https://r2.tapbot.dev/packages/secure_bot_2.0.0.botpkg",
            sha256Checksum = "feedbeefcafebabedeadbeefcafebabefeedbeefcafebabedeadbeefcafebabe",
            runtimeType = "native_art",
            packageSizeBytes = 4096L
        ),
        permissionsRequired = listOf("INTERNET"),
        releaseNotes = "Strict security enhancements",
        minimumAppVersion = 1,
        minimumRuntimeVersion = "1.0.0",
        updatedAt = "2026-09-19T00:00:00Z",
        isFeatured = true
    )

    // Spy catalog repository to ensure NO credentials or secrets are ever sent across the wire
    private val spyCatalogRepo = object : CatalogRepository {
        val capturedCalls = mutableListOf<String>()

        override suspend fun getBots(forceRefresh: Boolean): Result<List<BotMetadata>> {
            capturedCalls.add("getBots")
            return Result.success(listOf(secureBot))
        }

        override suspend fun getFeaturedBots(): Result<List<BotMetadata>> {
            capturedCalls.add("getFeaturedBots")
            return Result.success(listOf(secureBot))
        }

        override suspend fun getCategories(): Result<List<String>> {
            capturedCalls.add("getCategories")
            return Result.success(listOf("Security"))
        }

        override suspend fun getBotDetails(botId: String, forceRefresh: Boolean): Result<BotMetadata> {
            capturedCalls.add("getBotDetails:$botId")
            return Result.success(secureBot)
        }

        override suspend fun getBotVersions(botId: String): Result<List<com.tapbot.core.model.BotVersion>> = Result.success(emptyList())
        override suspend fun getLatestVersion(botId: String): Result<com.tapbot.core.model.BotVersion?> = Result.success(null)
        override suspend fun checkForUpdate(botId: String, currentVersion: String): Result<com.tapbot.core.model.BotVersion?> = Result.success(null)
    }

    private val inMemoryCredentialStore = object : SecureCredentialStore {
        val storage = mutableMapOf<String, MutableMap<String, String>>()

        override suspend fun saveCredential(botId: String, key: String, secretValue: String) {
            storage.getOrPut(botId) { mutableMapOf() }[key] = secretValue
        }

        override suspend fun getCredential(botId: String, key: String): String? =
            storage[botId]?.get(key)

        override suspend fun deleteCredential(botId: String, key: String) {
            storage[botId]?.remove(key)
        }

        override suspend fun hasCredential(botId: String, key: String): Boolean =
            storage[botId]?.containsKey(key) == true

        override suspend fun getAllCredentials(botId: String): Map<String, String> =
            storage[botId]?.toMap() ?: emptyMap()

        override suspend fun deleteCredentials(botId: String) {
            storage.remove(botId)
        }

        override suspend fun hasRequiredCredentials(botId: String, requiredKeys: List<String>): Boolean =
            requiredKeys.all { storage[botId]?.containsKey(it) == true }
    }

    private val spyBotServiceController = object : BotServiceController {
        var startCount = 0
        var stopCount = 0
        private val state = MutableStateFlow<BotRunState>(BotRunState.Stopped)

        override fun startBot(botId: String) {
            startCount++
            state.value = BotRunState.Running(1, 0)
        }

        override fun stopBot(botId: String) {
            stopCount++
            state.value = BotRunState.Stopped
        }

        override fun stopAll() {
            state.value = BotRunState.Stopped
        }

        override fun getBotRunState(botId: String): StateFlow<BotRunState> = state.asStateFlow()
    }

    private val fakeLogRepo = object : BotLogRepository {
        private val logs = MutableStateFlow<List<BotLogEntry>>(emptyList())
        override fun appendLog(botId: String, level: LogLevel, tag: String, message: String) {}
        override fun getLogStream(botId: String): StateFlow<List<BotLogEntry>> = logs.asStateFlow()
        override fun clearLogs(botId: String) {}
    }

    private val fakeDownloader = object : BotPackageDownloader {
        override suspend fun downloadPackage(
            packageUrl: String,
            expectedSha256: String,
            targetFile: File,
            onProgress: (Float) -> Unit
        ): Result<File> {
            targetFile.writeText("VALID_PAYLOAD")
            return Result.success(targetFile)
        }
    }

    private val fakeInstallationManager = object : LocalBotInstallationManager {
        val installed = mutableMapOf<String, String>()
        override fun isInstalled(botId: String): Boolean = installed.containsKey(botId)
        override fun getInstalledVersion(botId: String): String? = installed[botId]
        override fun installBot(bot: BotMetadata, packageFile: File): Result<Unit> {
            installed[bot.id] = bot.version
            return Result.success(Unit)
        }
        override fun uninstallBot(botId: String): Result<Unit> {
            installed.remove(botId)
            return Result.success(Unit)
        }
        override fun getInstalledBots(): List<InstalledBotRecord> = emptyList()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        SecretRedactor.clearRegisteredSecrets()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        SecretRedactor.clearRegisteredSecrets()
    }

    private fun createViewModel(): BotDetailViewModel {
        return BotDetailViewModel(
            botId = secureBot.id,
            catalogRepository = spyCatalogRepo,
            credentialStore = inMemoryCredentialStore,
            botServiceController = spyBotServiceController,
            logRepository = fakeLogRepo,
            packageDownloader = fakeDownloader,
            installationManager = fakeInstallationManager,
            appVersionCode = 1
        )
    }

    @Test
    fun `acceptance criterion - user entered secret stays on device and is never sent to remote catalog`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val validTelegramToken = "123456789:ABCdefGHIjklMNOpqrSTUvwxYZ_123456789"
        val customSecret = "super-secret-user-api-key-999"

        // User enters credentials in UI
        viewModel.updateCredential("bot_token", validTelegramToken)
        viewModel.updateCredential("custom_api_key", customSecret)

        // User saves credentials
        viewModel.saveCredentials()
        advanceUntilIdle()

        // 1. Verify stored securely on device
        assertEquals(validTelegramToken, inMemoryCredentialStore.getCredential(secureBot.id, "bot_token"))
        assertEquals(customSecret, inMemoryCredentialStore.getCredential(secureBot.id, "custom_api_key"))

        // 2. Verify backend spy was NEVER called with credentials or any other endpoints
        val state = viewModel.uiState.value as BotDetailUiState.Success
        assertTrue(state.credentialSaveSuccess)
        assertFalse(state.isConfiguring)

        // Only metadata fetching was called on catalog repo
        assertTrue(spyCatalogRepo.capturedCalls.all { it.startsWith("getBotDetails:") })
        // Verify no sensitive tokens are in the catalog repository records
        assertFalse(spyCatalogRepo.capturedCalls.any { it.contains(validTelegramToken) })
        assertFalse(spyCatalogRepo.capturedCalls.any { it.contains(customSecret) })
    }

    @Test
    fun `missing required credentials block bot start and set error message`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Install bot first
        viewModel.installBot()
        advanceUntilIdle()

        // Credentials have not been entered
        viewModel.startBot()
        advanceUntilIdle()

        // Bot must not start
        assertEquals(0, spyBotServiceController.startCount)

        val state = viewModel.uiState.value as BotDetailUiState.Success
        assertNotNull(state.generalCredentialError)
        assertTrue(state.credentialErrors.containsKey("bot_token"))
    }

    @Test
    fun `invalid telegram token format is rejected during validation`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Invalid format: doesn't match bot token pattern (\d{5,16}:[a-zA-Z0-9_-]{6,64})
        viewModel.updateCredential("bot_token", "invalid_token_123")
        viewModel.saveCredentials()
        advanceUntilIdle()

        val state = viewModel.uiState.value as BotDetailUiState.Success
        assertFalse(state.credentialSaveSuccess)
        assertTrue(state.credentialErrors["bot_token"]?.contains("Invalid Telegram Bot Token format") == true)

        // Ensure not saved in store
        assertNull(inMemoryCredentialStore.getCredential(secureBot.id, "bot_token"))
    }

    @Test
    fun `credential deletion purges all keys from device store and clears UI state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val validTelegramToken = "987654321:XYZdefGHIjklMNOpqrSTUvwxYZ_987654321"
        viewModel.updateCredential("bot_token", validTelegramToken)
        viewModel.saveCredentials()
        advanceUntilIdle()

        // Verify saved initially
        assertTrue(inMemoryCredentialStore.hasCredential(secureBot.id, "bot_token"))

        // Delete credentials
        viewModel.deleteCredentials()
        advanceUntilIdle()

        // Verify erased from store
        assertFalse(inMemoryCredentialStore.hasCredential(secureBot.id, "bot_token"))
        assertEquals(emptyMap<String, String>(), inMemoryCredentialStore.getAllCredentials(secureBot.id))

        val state = viewModel.uiState.value as BotDetailUiState.Success
        assertTrue(state.credentials.isEmpty())
        assertTrue(state.isConfiguring)
    }

    @Test
    fun `secret redactor masks saved credentials in console and logs`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val validTelegramToken = "123456789:ABCdefGHIjklMNOpqrSTUvwxYZ_123456789"
        viewModel.updateCredential("bot_token", validTelegramToken)
        viewModel.saveCredentials()
        advanceUntilIdle()

        // The dynamic secret was registered with SecretRedactor upon save
        val sampleLog = "Connecting to Telegram with token 123456789:ABCdefGHIjklMNOpqrSTUvwxYZ_123456789..."
        val redacted = SecretRedactor.redact(sampleLog)

        assertFalse("Log should not contain raw token", redacted.contains(validTelegramToken))
        assertTrue("Log should contain redaction placeholder", redacted.contains("[REDACTED_TELEGRAM_TOKEN]") || redacted.contains("[REDACTED_SECRET]"))
    }
}
