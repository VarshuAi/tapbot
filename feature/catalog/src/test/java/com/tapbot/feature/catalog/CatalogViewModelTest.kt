package com.tapbot.feature.catalog

import com.tapbot.core.model.BotCredentialSpec
import com.tapbot.core.model.BotMetadata
import com.tapbot.core.model.BotPackageInfo
import com.tapbot.core.network.CatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CatalogViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val sampleBot1 = BotMetadata(
        id = "bot_1",
        name = "Audio Player",
        summary = "Telegram audio streamer",
        description = "Plays songs",
        author = "TapBot",
        version = "1.0.0",
        iconUrl = "",
        category = "Media",
        tags = listOf("media", "music"),
        requiredCredentials = listOf(BotCredentialSpec.TELEGRAM_BOT_TOKEN),
        packageInfo = BotPackageInfo(
            packageUrl = "",
            sha256Checksum = "",
            runtimeType = "native_art",
            packageSizeBytes = 100L
        ),
        permissionsRequired = emptyList(),
        isFeatured = true
    )

    private val sampleBot2 = BotMetadata(
        id = "bot_2",
        name = "Task Reminder",
        summary = "Reminds tasks",
        description = "Tasks scheduler",
        author = "TapBot",
        version = "1.2.0",
        iconUrl = "",
        category = "Productivity",
        tags = listOf("productivity", "reminder"),
        requiredCredentials = listOf(BotCredentialSpec.TELEGRAM_BOT_TOKEN),
        packageInfo = BotPackageInfo(
            packageUrl = "",
            sha256Checksum = "",
            runtimeType = "native_art",
            packageSizeBytes = 200L
        ),
        permissionsRequired = emptyList(),
        isFeatured = false
    )

    private class FakeCatalogRepository(
        var botsResult: Result<List<BotMetadata>>,
        var categoriesResult: Result<List<String>> = Result.success(listOf("All", "Media", "Productivity"))
    ) : CatalogRepository {
        var getBotsCount = 0

        override suspend fun getBots(forceRefresh: Boolean): Result<List<BotMetadata>> {
            getBotsCount++
            return botsResult
        }

        override suspend fun getFeaturedBots(): Result<List<BotMetadata>> {
            return botsResult.map { it.filter { b -> b.isFeatured } }
        }

        override suspend fun getCategories(): Result<List<String>> = categoriesResult

        override suspend fun getBotDetails(botId: String, forceRefresh: Boolean): Result<BotMetadata> {
            return botsResult.mapCatching { list ->
                list.firstOrNull { it.id == botId } ?: throw NoSuchElementException(botId)
            }
        }
        var latestVersionMap: MutableMap<String, com.tapbot.core.model.BotVersion> = mutableMapOf()

        override suspend fun getBotVersions(botId: String): Result<List<com.tapbot.core.model.BotVersion>> {
            val ver = latestVersionMap[botId]
            return Result.success(if (ver != null) listOf(ver) else emptyList())
        }

        override suspend fun getLatestVersion(botId: String): Result<com.tapbot.core.model.BotVersion?> {
            return Result.success(latestVersionMap[botId])
        }

        override suspend fun checkForUpdate(botId: String, currentVersion: String): Result<com.tapbot.core.model.BotVersion?> {
            val latest = latestVersionMap[botId] ?: return Result.success(null)
            return if (latest.version != currentVersion && latest.isPublished) {
                Result.success(latest)
            } else {
                Result.success(null)
            }
        }
    }

    private class FakeBotInstanceManager : com.tapbot.core.runner.manager.BotInstanceManager {
        private val _instances = kotlinx.coroutines.flow.MutableStateFlow<List<com.tapbot.core.model.BotInstance>>(emptyList())
        override val instances: kotlinx.coroutines.flow.StateFlow<List<com.tapbot.core.model.BotInstance>> = _instances

        private val _updateProgress = kotlinx.coroutines.flow.MutableStateFlow<Map<String, com.tapbot.core.model.BotUpdateProgress>>(emptyMap())
        override val updateProgress: kotlinx.coroutines.flow.StateFlow<Map<String, com.tapbot.core.model.BotUpdateProgress>> = _updateProgress

        override val activeState: kotlinx.coroutines.flow.StateFlow<com.tapbot.core.runner.runtime.BotRuntimeState> =
            kotlinx.coroutines.flow.MutableStateFlow(com.tapbot.core.runner.runtime.BotRuntimeState.Stopped)

        var updateTargetVersionPassed: com.tapbot.core.model.BotVersion? = null

        fun setInstalledInstances(list: List<com.tapbot.core.model.BotInstance>) {
            _instances.value = list
        }

        override suspend fun validateToken(token: String): Result<com.tapbot.core.model.TelegramUser> =
            Result.success(com.tapbot.core.model.TelegramUser(1, true, "bot", "bot"))

        override suspend fun start(): Result<Unit> = Result.success(Unit)
        override suspend fun stop(): Result<Unit> = Result.success(Unit)
        override suspend fun restart(): Result<Unit> = Result.success(Unit)

        override suspend fun install(bot: BotMetadata, packageFile: java.io.File): Result<com.tapbot.core.model.BotInstance> =
            Result.failure(NotImplementedError())

        override suspend fun uninstall(installationId: String): Result<Unit> = Result.success(Unit)
        override suspend fun start(installationId: String): Result<Unit> = Result.success(Unit)
        override suspend fun stop(installationId: String): Result<Unit> = Result.success(Unit)
        override suspend fun restart(installationId: String): Result<Unit> = Result.success(Unit)

        override suspend fun update(installationId: String, newVersion: String, packageFile: java.io.File): Result<com.tapbot.core.model.BotInstance> =
            Result.failure(NotImplementedError())

        override suspend fun updateWithRollback(
            installationId: String,
            targetVersion: com.tapbot.core.model.BotVersion,
            packageDownloader: com.tapbot.core.network.BotPackageDownloader?,
            manifestValidator: com.tapbot.core.network.ManifestValidator?,
            onProgress: ((com.tapbot.core.model.BotUpdateProgress) -> Unit)?
        ): Result<com.tapbot.core.model.BotInstance> {
            updateTargetVersionPassed = targetVersion
            val current = _instances.value.find { it.installationId == installationId }
                ?: return Result.failure(NoSuchElementException(installationId))
            val updated = current.copy(version = targetVersion.version)
            _instances.value = _instances.value.map { if (it.installationId == installationId) updated else it }
            return Result.success(updated)
        }

        override fun getInstance(installationId: String): com.tapbot.core.model.BotInstance? =
            _instances.value.find { it.installationId == installationId }

        override fun getInstanceByBotId(botId: String): com.tapbot.core.model.BotInstance? =
            _instances.value.find { it.botId == botId }

        override fun observeInstance(installationId: String): kotlinx.coroutines.flow.Flow<com.tapbot.core.model.BotInstance?> =
            kotlinx.coroutines.flow.flowOf(_instances.value.find { it.installationId == installationId })

        override fun updateInstanceStatus(installationId: String, status: com.tapbot.core.model.BotInstanceStatus) {}
        override fun recordCrash(installationId: String, reason: String) {}
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
    fun loadCatalog_populatesBotsCategoriesAndFeatured() = runTest(testDispatcher) {
        val repo = FakeCatalogRepository(Result.success(listOf(sampleBot1, sampleBot2)))
        val viewModel = CatalogViewModel(repo)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CatalogUiState.Success)
        val success = state as CatalogUiState.Success
        assertEquals(2, success.bots.size)
        assertEquals(1, success.featuredBots.size)
        assertEquals("bot_1", success.featuredBots[0].id)
        assertEquals(3, success.categories.size)
        assertFalse(success.isRefreshing)
    }

    @Test
    fun selectCategory_filtersBots() = runTest(testDispatcher) {
        val repo = FakeCatalogRepository(Result.success(listOf(sampleBot1, sampleBot2)))
        val viewModel = CatalogViewModel(repo)

        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectCategory("Media")
        val success = viewModel.uiState.value as CatalogUiState.Success
        assertEquals(1, success.bots.size)
        assertEquals("bot_1", success.bots[0].id)
        assertEquals("Media", success.selectedCategory)

        viewModel.selectCategory("All")
        val allSuccess = viewModel.uiState.value as CatalogUiState.Success
        assertEquals(2, allSuccess.bots.size)
    }

    @Test
    fun updateSearchQuery_filtersByQuery() = runTest(testDispatcher) {
        val repo = FakeCatalogRepository(Result.success(listOf(sampleBot1, sampleBot2)))
        val viewModel = CatalogViewModel(repo)

        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateSearchQuery("Reminder")
        val success = viewModel.uiState.value as CatalogUiState.Success
        assertEquals(1, success.bots.size)
        assertEquals("bot_2", success.bots[0].id)
    }

    @Test
    fun refresh_forcesReloadFromRepository() = runTest(testDispatcher) {
        val repo = FakeCatalogRepository(Result.success(listOf(sampleBot1, sampleBot2)))
        val viewModel = CatalogViewModel(repo)

        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repo.getBotsCount)

        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, repo.getBotsCount)
    }

    @Test
    fun checkForUpdates_detectsNewerPublishedVersion() = runTest(testDispatcher) {
        val repo = FakeCatalogRepository(Result.success(listOf(sampleBot1, sampleBot2)))
        val fakeManager = FakeBotInstanceManager()
        val installedBot = com.tapbot.core.model.BotInstance(
            installationId = "inst_1",
            botId = "bot_1",
            name = "Audio Player",
            version = "1.0.0",
            status = com.tapbot.core.model.BotInstanceStatus.Stopped
        )
        fakeManager.setInstalledInstances(listOf(installedBot))

        // Set latest version to v1.4.0
        val targetVersion = com.tapbot.core.model.BotVersion(
            id = "ver_v14",
            botId = "bot_1",
            version = "1.4.0",
            packageUrl = "https://cdn.tapbot.dev/bot_1_1.4.0.botpkg",
            sha256 = "dummy_sha",
            releaseNotes = "Added FLAC playback support",
            isPublished = true
        )
        repo.latestVersionMap["bot_1"] = targetVersion

        val viewModel = CatalogViewModel(
            catalogRepository = repo,
            botInstanceManager = fakeManager
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as CatalogUiState.Success
        assertTrue("Must detect available update for inst_1", state.availableUpdates.containsKey("inst_1"))
        assertEquals("1.4.0", state.availableUpdates["inst_1"]?.version)
        assertEquals("Added FLAC playback support", state.availableUpdates["inst_1"]?.releaseNotes)

        // Trigger updateBot
        viewModel.updateBot("inst_1", targetVersion)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("1.4.0", fakeManager.updateTargetVersionPassed?.version)
        val afterState = viewModel.uiState.value as CatalogUiState.Success
        assertEquals("1.4.0", afterState.installedInstances.first().version)
    }
}
