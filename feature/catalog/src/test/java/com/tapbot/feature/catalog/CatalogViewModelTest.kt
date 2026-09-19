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
}
