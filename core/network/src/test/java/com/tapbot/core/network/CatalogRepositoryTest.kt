package com.tapbot.core.network

import com.tapbot.core.model.BotCredentialSpec
import com.tapbot.core.model.BotMetadata
import com.tapbot.core.model.BotPackageInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class CatalogRepositoryTest {

    private val testBot1 = BotMetadata(
        id = "bot_music",
        name = "Music Bot",
        summary = "Telegram audio streamer",
        description = "Streams audio to your local Android device via Telegram commands.",
        author = "TapBot Publishing",
        version = "1.0.0",
        iconUrl = "https://cdn.tapbot.dev/music.png",
        category = "Media",
        tags = listOf("media", "audio"),
        requiredCredentials = listOf(BotCredentialSpec.TELEGRAM_BOT_TOKEN),
        packageInfo = BotPackageInfo(
            packageUrl = "https://r2.tapbot.dev/music.botpkg",
            sha256Checksum = "abcdef1234567890",
            runtimeType = "native_art",
            packageSizeBytes = 1024L
        ),
        permissionsRequired = listOf("INTERNET"),
        releaseNotes = "Initial music release",
        minimumAppVersion = 1,
        minimumRuntimeVersion = "1.0.0",
        updatedAt = "2026-09-19T00:00:00Z",
        isFeatured = true
    )

    private val testBot2 = BotMetadata(
        id = "bot_ping",
        name = "Ping Bot",
        summary = "Echo latency bot",
        description = "Echoes messages with latency timestamps.",
        author = "TapBot Publishing",
        version = "1.1.0",
        iconUrl = "https://cdn.tapbot.dev/ping.png",
        category = "Utilities",
        tags = listOf("utilities", "echo"),
        requiredCredentials = listOf(BotCredentialSpec.TELEGRAM_BOT_TOKEN),
        packageInfo = BotPackageInfo(
            packageUrl = "https://r2.tapbot.dev/ping.botpkg",
            sha256Checksum = "123456abcdef7890",
            runtimeType = "native_art",
            packageSizeBytes = 2048L
        ),
        permissionsRequired = listOf("INTERNET"),
        releaseNotes = "Minor speedups",
        minimumAppVersion = 1,
        minimumRuntimeVersion = "1.0.0",
        updatedAt = "2026-09-19T00:00:00Z",
        isFeatured = false
    )

    private class FakeRemoteSource(
        var botsResult: Result<List<BotMetadata>>,
        var categoriesResult: Result<List<String>> = Result.success(listOf("All", "Media", "Utilities"))
    ) : RemoteBotDataSource {
        var callCount = 0

        override suspend fun getPublishedBots(category: String?, search: String?): Result<List<BotMetadata>> {
            callCount++
            return botsResult
        }

        override suspend fun getBotDetails(botId: String): Result<BotMetadata> {
            return botsResult.mapCatching { list ->
                list.firstOrNull { it.id == botId } ?: throw NoSuchElementException("Bot $botId not found")
            }
        }

        override suspend fun getCategories(): Result<List<String>> = categoriesResult
    }

    @Test
    fun getBots_fetchesFromRemoteAndPopulatesCache() = runTest {
        val tempDir = File.createTempFile("cache_test", "").apply { delete(); mkdirs() }
        val cacheFile = File(tempDir, "test_catalog.json")
        val cache = LocalCatalogCache(cacheFile)

        val remoteSource = FakeRemoteSource(Result.success(listOf(testBot1, testBot2)))
        val repository = OfflineFirstCatalogRepository(remoteSource, cache)

        // Initial fetch with no prior cache
        val result = repository.getBots(forceRefresh = false)
        assertTrue(result.isSuccess)
        val bots = result.getOrThrow()
        assertEquals(2, bots.size)
        assertEquals(1, remoteSource.callCount)

        // Cache should now have the bots
        assertTrue(cache.hasCache())
        assertEquals(2, cache.getCachedBots()?.size)

        // Subsequent fetch with forceRefresh = false should use cache without remote call
        val cachedResult = repository.getBots(forceRefresh = false)
        assertTrue(cachedResult.isSuccess)
        assertEquals(2, cachedResult.getOrThrow().size)
        assertEquals(1, remoteSource.callCount) // Remote was NOT called again

        // Subsequent fetch with forceRefresh = true should hit remote
        val refreshedResult = repository.getBots(forceRefresh = true)
        assertTrue(refreshedResult.isSuccess)
        assertEquals(2, remoteSource.callCount)

        tempDir.deleteRecursively()
    }

    @Test
    fun getBots_fallsBackToCacheWhenRemoteFails() = runTest {
        val tempDir = File.createTempFile("cache_test_fallback", "").apply { delete(); mkdirs() }
        val cacheFile = File(tempDir, "test_catalog.json")
        val cache = LocalCatalogCache(cacheFile)

        // Pre-populate cache
        cache.saveCatalog(listOf(testBot1), listOf("All", "Media"))

        // Remote throws network error
        val failingSource = FakeRemoteSource(Result.failure(IOException("No network connectivity")))
        val repository = OfflineFirstCatalogRepository(failingSource, cache)

        val result = repository.getBots(forceRefresh = true)
        assertTrue(result.isSuccess)
        val bots = result.getOrThrow()
        assertEquals(1, bots.size)
        assertEquals("bot_music", bots[0].id)

        tempDir.deleteRecursively()
    }

    @Test
    fun getFeaturedBots_filtersCorrectly() = runTest {
        val cache = LocalCatalogCache()
        val remoteSource = FakeRemoteSource(Result.success(listOf(testBot1, testBot2)))
        val repository = OfflineFirstCatalogRepository(remoteSource, cache)

        val featuredResult = repository.getFeaturedBots()
        assertTrue(featuredResult.isSuccess)
        val featured = featuredResult.getOrThrow()
        assertEquals(1, featured.size)
        assertEquals("bot_music", featured[0].id)
        assertTrue(featured[0].isFeatured)
    }

    @Test
    fun getCategories_returnsRemoteOrCache() = runTest {
        val cache = LocalCatalogCache()
        val remoteSource = FakeRemoteSource(
            botsResult = Result.success(listOf(testBot1)),
            categoriesResult = Result.success(listOf("All", "Media", "Utilities", "Productivity"))
        )
        val repository = OfflineFirstCatalogRepository(remoteSource, cache)

        val categoriesResult = repository.getCategories()
        assertTrue(categoriesResult.isSuccess)
        val categories = categoriesResult.getOrThrow()
        assertTrue(categories.contains("Media"))
        assertTrue(categories.contains("Utilities"))
    }

    @Test
    fun getBotDetails_returnsSpecificBot() = runTest {
        val cache = LocalCatalogCache()
        cache.saveCatalog(listOf(testBot1, testBot2), listOf("All"))
        val remoteSource = FakeRemoteSource(Result.success(listOf(testBot1, testBot2)))
        val repository = OfflineFirstCatalogRepository(remoteSource, cache)

        val detailResult = repository.getBotDetails("bot_ping", forceRefresh = false)
        assertTrue(detailResult.isSuccess)
        val bot = detailResult.getOrThrow()
        assertEquals("bot_ping", bot.id)
        assertEquals("Ping Bot", bot.name)
        assertEquals("1.1.0", bot.version)
    }
}
