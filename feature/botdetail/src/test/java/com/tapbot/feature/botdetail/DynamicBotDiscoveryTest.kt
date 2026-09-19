package com.tapbot.feature.botdetail

import com.tapbot.core.logging.BotLogRepository
import com.tapbot.core.model.BotCredentialSpec
import com.tapbot.core.model.BotLogEntry
import com.tapbot.core.model.BotMetadata
import com.tapbot.core.model.BotPackageInfo
import com.tapbot.core.model.BotRunState
import com.tapbot.core.model.LogLevel
import com.tapbot.core.network.CatalogApi
import com.tapbot.core.runner.BotServiceController
import com.tapbot.core.security.SecureCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Acceptance Test: Proves that a newly published bot (Music Controller Bot)
 * is dynamically discovered by the Android app from its manifest/catalog metadata
 * WITHOUT ANY hardcoded bot-specific code in the Android application.
 */
class DynamicBotDiscoveryTest {

    private val testDispatcher = StandardTestDispatcher()

    // 1. Dynamic Bot definition sourced directly from manifest.json
    private val musicBotMetadata = BotMetadata(
        id = "music-controller-bot",
        name = "Music Controller Bot",
        summary = "Control your home audio and media playback directly via Telegram.",
        description = "Music Controller Bot runs locally in the background on your Android device.",
        author = "TapBot Publishing",
        version = "1.0.0",
        iconUrl = "https://cdn.tapbot.dev/assets/music-icon.png",
        category = "Media",
        tags = listOf("media", "native_art"),
        requiredCredentials = listOf(
            BotCredentialSpec(
                key = "bot_token",
                label = "Telegram Bot Token",
                description = "API token obtained from @BotFather on Telegram.",
                isSecret = true,
                isRequired = true,
                placeholder = "123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ"
            ),
            BotCredentialSpec(
                key = "spotify_client_id",
                label = "Spotify Client ID",
                description = "Client ID from your Spotify Developer Dashboard for remote queueing.",
                isSecret = false,
                isRequired = true,
                placeholder = "your_spotify_client_id"
            ),
            BotCredentialSpec(
                key = "default_volume",
                label = "Default Volume (0-100)",
                description = "Initial playback volume percentage on startup.",
                isSecret = false,
                isRequired = false,
                placeholder = "75"
            )
        ),
        packageInfo = BotPackageInfo(
            packageUrl = "https://r2.tapbot.dev/packages/music-controller-bot_1.0.0.botpkg",
            sha256Checksum = "6623cf5ee2d0de42200515fc24eed93fa642937724c12e6a108f6c5608b0bc2a",
            runtimeType = "native_art",
            packageSizeBytes = 1581L
        )
    )

    private val fakeCatalogApi = object : CatalogApi {
        override suspend fun getBots(): Result<List<BotMetadata>> = Result.success(listOf(musicBotMetadata))
        override suspend fun getBotDetails(botId: String): Result<BotMetadata> {
            return if (botId == musicBotMetadata.id) Result.success(musicBotMetadata)
            else Result.failure(NoSuchElementException("Bot not found: $botId"))
        }
    }

    private val fakeCredentialStore = object : SecureCredentialStore {
        private val storage = mutableMapOf<String, MutableMap<String, String>>()

        override suspend fun saveCredential(botId: String, key: String, secretValue: String) {
            storage.getOrPut(botId) { mutableMapOf() }[key] = secretValue
        }

        override suspend fun getCredential(botId: String, key: String): String? {
            return storage[botId]?.get(key)
        }

        override suspend fun deleteCredential(botId: String, key: String) {
            storage[botId]?.remove(key)
        }

        override suspend fun hasCredential(botId: String, key: String): Boolean {
            return storage[botId]?.containsKey(key) == true
        }

        override suspend fun getAllCredentials(botId: String): Map<String, String> {
            return storage[botId]?.toMap() ?: emptyMap()
        }

        override suspend fun deleteCredentials(botId: String) {
            storage.remove(botId)
        }

        override suspend fun hasRequiredCredentials(botId: String, requiredKeys: List<String>): Boolean {
            val botStorage = storage[botId] ?: return false
            return requiredKeys.all { botStorage.containsKey(it) && botStorage[it]?.isNotBlank() == true }
        }
    }

    private val fakeController = object : BotServiceController {
        private val state = MutableStateFlow<BotRunState>(BotRunState.Stopped)
        override fun startBot(botId: String) { state.value = BotRunState.Running(1, 0) }
        override fun stopBot(botId: String) { state.value = BotRunState.Stopped }
        override fun stopAll() { state.value = BotRunState.Stopped }
        override fun getBotRunState(botId: String): StateFlow<BotRunState> = state.asStateFlow()
    }

    private val fakeLogRepo = object : BotLogRepository {
        private val logs = MutableStateFlow<List<BotLogEntry>>(emptyList())
        override fun appendLog(botId: String, level: LogLevel, tag: String, message: String) {
            logs.value += BotLogEntry(botId = botId, level = level, tag = tag, message = message)
        }
        override fun getLogStream(botId: String): StateFlow<List<BotLogEntry>> = logs.asStateFlow()
        override fun clearLogs(botId: String) { logs.value = emptyList() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun secondBot_discoveredDynamicallyWithoutAndroidCodeChanges() = runTest(testDispatcher) {
        // 1. Initialize generic ViewModel with the newly published bot's ID
        val viewModel = BotDetailViewModel(
            botId = "music-controller-bot",
            catalogApi = fakeCatalogApi,
            credentialStore = fakeCredentialStore,
            botServiceController = fakeController,
            logRepository = fakeLogRepo
        )

        testDispatcher.scheduler.advanceUntilIdle()

        // 2. Verify state discovered strictly from manifest metadata
        val state = viewModel.uiState.value
        assertTrue("Expected BotDetailUiState.Success but got $state", state is BotDetailUiState.Success)

        val success = state as BotDetailUiState.Success
        val bot = success.bot

        // Discovered Bot Name
        assertEquals("Music Controller Bot", bot.name)
        // Discovered Version
        assertEquals("1.0.0", bot.version)
        // Discovered Icon URL
        assertEquals("https://cdn.tapbot.dev/assets/music-icon.png", bot.iconUrl)
        // Discovered Runtime Requirement
        assertEquals("native_art", bot.packageInfo.runtimeType)
        assertEquals("6623cf5ee2d0de42200515fc24eed93fa642937724c12e6a108f6c5608b0bc2a", bot.packageInfo.sha256Checksum)

        // Discovered Credential Requirements Schema (3 dynamic fields)
        assertEquals(3, bot.requiredCredentials.size)

        val tokenSpec = bot.requiredCredentials.find { it.key == "bot_token" }
        assertNotNull(tokenSpec)
        assertEquals("Telegram Bot Token", tokenSpec?.label)
        assertEquals(true, tokenSpec?.isSecret)
        assertEquals(true, tokenSpec?.isRequired)

        val spotifySpec = bot.requiredCredentials.find { it.key == "spotify_client_id" }
        assertNotNull(spotifySpec)
        assertEquals("Spotify Client ID", spotifySpec?.label)
        assertEquals(false, spotifySpec?.isSecret)
        assertEquals(true, spotifySpec?.isRequired)

        val volumeSpec = bot.requiredCredentials.find { it.key == "default_volume" }
        assertNotNull(volumeSpec)
        assertEquals("Default Volume (0-100)", volumeSpec?.label)
        assertEquals(false, volumeSpec?.isSecret)
        assertEquals(false, volumeSpec?.isRequired)

        // 3. Verify user can input values dynamically and they are saved securely
        viewModel.updateCredential("bot_token", "987654321:XYZ-TEST-TOKEN")
        viewModel.updateCredential("spotify_client_id", "my_spotify_app_id_123")
        viewModel.updateCredential("default_volume", "80")

        viewModel.saveCredentials()
        testDispatcher.scheduler.advanceUntilIdle()

        // 4. Verify all credentials persisted to hardware-backed Keystore store dynamically
        val saved = fakeCredentialStore.getAllCredentials("music-controller-bot")
        assertEquals("987654321:XYZ-TEST-TOKEN", saved["bot_token"])
        assertEquals("my_spotify_app_id_123", saved["spotify_client_id"])
        assertEquals("80", saved["default_volume"])
    }
}
