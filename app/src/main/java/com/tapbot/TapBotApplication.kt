package com.tapbot

import android.app.Application
import com.tapbot.core.logging.BotLogRepository
import com.tapbot.core.logging.InMemoryRingBufferLogRepository
import com.tapbot.core.network.BotPackageDownloader
import com.tapbot.core.network.CatalogApi
import com.tapbot.core.network.CatalogRepository
import com.tapbot.core.network.CloudflareCatalogApi
import com.tapbot.core.network.CloudflareRemoteBotDataSource
import com.tapbot.core.network.DefaultLocalBotInstallationManager
import com.tapbot.core.network.LocalBotInstallationManager
import com.tapbot.core.network.LocalCatalogCache
import com.tapbot.core.network.MockCatalogApi
import com.tapbot.core.network.OfflineFirstCatalogRepository
import com.tapbot.core.network.OkHttpBotPackageDownloader
import com.tapbot.core.network.RemoteBotDataSource
import com.tapbot.core.network.TelegramApiClient
import com.tapbot.core.runner.AndroidBotServiceController
import com.tapbot.core.runner.BotServiceController
import com.tapbot.core.runner.ServiceLocator
import com.tapbot.core.runner.manager.BotInstanceManager
import com.tapbot.core.runner.manager.DefaultBotInstanceManager
import com.tapbot.core.security.CredentialStore
import com.tapbot.core.security.KeystoreCredentialStore
import com.tapbot.core.security.KeystoreSecureCredentialStore
import com.tapbot.core.security.SecureCredentialStore
import okhttp3.OkHttpClient
import java.io.File

/**
 * Application entry point for TapBot.
 * Coordinates dependency graph and connects foreground service locator hooks.
 */
class TapBotApplication : Application() {

    lateinit var credentialStore: SecureCredentialStore
        private set

    lateinit var logRepository: BotLogRepository
        private set

    lateinit var catalogApi: CatalogApi
        private set

    lateinit var remoteBotDataSource: RemoteBotDataSource
        private set

    lateinit var localCatalogCache: LocalCatalogCache
        private set

    lateinit var catalogRepository: CatalogRepository
        private set

    lateinit var botPackageDownloader: BotPackageDownloader
        private set

    lateinit var localBotInstallationManager: LocalBotInstallationManager
        private set

    lateinit var telegramApiClient: TelegramApiClient
        private set

    lateinit var botServiceController: BotServiceController
        private set

    lateinit var pocCredentialStore: CredentialStore
        private set

    lateinit var botInstanceManager: BotInstanceManager
        private set

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize secure storage backed by Android Keystore
        credentialStore = KeystoreSecureCredentialStore(this)
        pocCredentialStore = KeystoreCredentialStore(this)

        // 2. Initialize in-memory ring buffer logging
        logRepository = InMemoryRingBufferLogRepository(maxCapacityPerBot = 500)

        // 3. Initialize Catalog data source, cache, and repository connecting to Cloudflare Workers / D1 API
        val okHttpClient = OkHttpClient()
        val isDebug = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val backendUrl = if (isDebug) CloudflareCatalogApi.DEFAULT_LOCAL_URL else CloudflareCatalogApi.DEFAULT_BASE_URL
        remoteBotDataSource = CloudflareRemoteBotDataSource(baseUrl = backendUrl, client = okHttpClient)
        localCatalogCache = LocalCatalogCache(cacheFile = File(cacheDir, "catalog_cache.json"))
        catalogRepository = OfflineFirstCatalogRepository(
            remoteSource = remoteBotDataSource,
            cache = localCatalogCache
        )
        catalogApi = CloudflareCatalogApi(baseUrl = backendUrl, fallback = MockCatalogApi())

        // 4. Initialize package downloader and local installation manager
        botPackageDownloader = OkHttpBotPackageDownloader(
            client = okHttpClient,
            cacheDir = File(cacheDir, "package_cache")
        )
        localBotInstallationManager = DefaultLocalBotInstallationManager(
            storageFile = File(filesDir, "installed_bots.json"),
            installDir = File(filesDir, "installed_packages")
        )

        // 5. Initialize Telegram API client
        telegramApiClient = TelegramApiClient()

        // 6. Initialize runner service controller
        botServiceController = AndroidBotServiceController(this)

        // 7. Initialize BotInstanceManager with persistent storage and hardware Keystore
        botInstanceManager = DefaultBotInstanceManager(
            context = this,
            storageFile = File(filesDir, "instances.json"),
            installDir = File(filesDir, "installed_packages"),
            credentialStore = credentialStore,
            telegramApi = telegramApiClient
        )

        // 8. Connect ServiceLocator hooks for BotForegroundService
        ServiceLocator.botInstanceManager = botInstanceManager
        ServiceLocator.credentialStore = credentialStore
        ServiceLocator.pocCredentialStore = pocCredentialStore
        ServiceLocator.logRepository = logRepository
        ServiceLocator.telegramApiClient = telegramApiClient
    }
}
