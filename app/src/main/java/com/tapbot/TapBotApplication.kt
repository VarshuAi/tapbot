package com.tapbot

import android.app.Application
import com.tapbot.core.logging.BotLogRepository
import com.tapbot.core.logging.InMemoryRingBufferLogRepository
import com.tapbot.core.network.CatalogApi
import com.tapbot.core.network.MockCatalogApi
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

        // 3. Initialize Catalog client
        catalogApi = MockCatalogApi()

        // 4. Initialize Telegram API client
        telegramApiClient = TelegramApiClient()

        // 5. Initialize runner service controller
        botServiceController = AndroidBotServiceController(this)

        // 6. Initialize BotInstanceManager for Phase 2 Proof of Concept
        botInstanceManager = DefaultBotInstanceManager(
            credentialStore = pocCredentialStore,
            telegramApi = telegramApiClient,
            logRepository = logRepository
        )

        // 7. Connect ServiceLocator hooks for BotForegroundService
        ServiceLocator.credentialStore = credentialStore
        ServiceLocator.logRepository = logRepository
        ServiceLocator.telegramApiClient = telegramApiClient
    }
}
