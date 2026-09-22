package com.tapbot.core.network

import com.tapbot.core.model.BotMetadata
import com.tapbot.core.model.BotVersion

/**
 * High-level repository for accessing catalog bots, categories, and details
 * with seamless offline-first caching and pull-to-refresh capabilities.
 */
interface CatalogRepository {
    suspend fun getBots(forceRefresh: Boolean = false): Result<List<BotMetadata>>
    suspend fun getFeaturedBots(): Result<List<BotMetadata>>
    suspend fun getCategories(): Result<List<String>>
    suspend fun getBotDetails(botId: String, forceRefresh: Boolean = false): Result<BotMetadata>
    suspend fun getBotVersions(botId: String): Result<List<BotVersion>>
    suspend fun getLatestVersion(botId: String): Result<BotVersion?>
    suspend fun checkForUpdate(botId: String, currentVersion: String): Result<BotVersion?>
}

/**
 * Offline-first implementation orchestrating remote Cloudflare API calls
 * and local storage caching.
 */
class OfflineFirstCatalogRepository(
    private val remoteSource: RemoteBotDataSource,
    private val cache: LocalCatalogCache = LocalCatalogCache()
) : CatalogRepository {

    override suspend fun getBots(forceRefresh: Boolean): Result<List<BotMetadata>> {
        // 1. If not forcing refresh and cache is available, return immediately
        if (!forceRefresh && cache.hasCache()) {
            val cached = cache.getCachedBots()
            if (!cached.isNullOrEmpty()) {
                return Result.success(cached)
            }
        }

        // 2. Fetch from remote Cloudflare API
        return remoteSource.getPublishedBots()
            .onSuccess { remoteBots ->
                // Update categories asynchronously or inline
                val categories = remoteSource.getCategories().getOrElse {
                    listOf("All", "Utilities", "Media", "Productivity", "Automation")
                }
                cache.saveCatalog(remoteBots, categories)
            }
            .recoverCatching { error ->
                // 3. If remote fails, fallback to local cache
                val cached = cache.getCachedBots()
                if (!cached.isNullOrEmpty()) {
                    cached
                } else {
                    throw error
                }
            }
    }

    override suspend fun getFeaturedBots(): Result<List<BotMetadata>> {
        return getBots(forceRefresh = false).map { allBots ->
            val featured = allBots.filter { it.isFeatured }
            if (featured.isNotEmpty()) {
                featured
            } else {
                allBots.take(2) // Default top bots as featured
            }
        }
    }

    override suspend fun getCategories(): Result<List<String>> {
        val cached = cache.getCachedCategories()
        if (!cached.isNullOrEmpty()) {
            return Result.success(cached)
        }

        return remoteSource.getCategories().recoverCatching {
            listOf("All", "Utilities", "Media", "Productivity", "Automation")
        }
    }

    override suspend fun getBotDetails(botId: String, forceRefresh: Boolean): Result<BotMetadata> {
        if (!forceRefresh) {
            val cached = cache.getCachedBot(botId)
            if (cached != null) {
                return Result.success(cached)
            }
        }

        return remoteSource.getBotDetails(botId)
            .onSuccess { freshBot ->
                cache.updateBot(freshBot)
            }
            .recoverCatching { error ->
                val fallback = cache.getCachedBot(botId)
                fallback ?: throw error
            }
    }

    override suspend fun getBotVersions(botId: String): Result<List<BotVersion>> {
        return remoteSource.getBotVersions(botId)
    }

    override suspend fun getLatestVersion(botId: String): Result<BotVersion?> {
        return remoteSource.getLatestVersion(botId)
    }

    override suspend fun checkForUpdate(botId: String, currentVersion: String): Result<BotVersion?> {
        return runCatching {
            val latest = getLatestVersion(botId).getOrThrow() ?: return@runCatching null
            // Check if latest.version is newer than currentVersion
            val isCurrentNewerOrSame = DowngradeAttackChecker.isDowngrade(currentInstalledVersion = latest.version, candidateVersion = currentVersion)
            val isDowngrade = DowngradeAttackChecker.isDowngrade(currentInstalledVersion = currentVersion, candidateVersion = latest.version)

            if (!isDowngrade && latest.version != currentVersion) {
                latest
            } else {
                null
            }
        }
    }
}
