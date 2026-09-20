package com.tapbot.core.runner

import com.tapbot.core.model.BotCredentialSpec
import com.tapbot.core.model.BotInstanceStatus
import com.tapbot.core.model.BotMetadata
import com.tapbot.core.model.BotPackageInfo
import com.tapbot.core.model.TelegramUser
import com.tapbot.core.network.TelegramApiClient
import com.tapbot.core.runner.manager.DefaultBotInstanceManager
import com.tapbot.core.runner.runtime.BotRuntimeState
import com.tapbot.core.security.CredentialStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BotInstanceManagerTest {

    private class FakeCredentialStore(var storedToken: String? = null) : CredentialStore {
        val map = mutableMapOf<String, MutableMap<String, String>>()

        override suspend fun saveCredential(botId: String, key: String, secretValue: String) {
            map.getOrPut(botId) { mutableMapOf() }[key] = secretValue
            if (key == "telegram_bot_token" || key == "bot_token") storedToken = secretValue
        }
        override suspend fun getCredential(botId: String, key: String): String? =
            map[botId]?.get(key) ?: if (key == "telegram_bot_token" || key == "bot_token") storedToken else null

        override suspend fun deleteCredential(botId: String, key: String) {
            map[botId]?.remove(key)
        }
        override suspend fun hasCredential(botId: String, key: String): Boolean =
            map[botId]?.containsKey(key) == true || (key == "telegram_bot_token" && !storedToken.isNullOrBlank())

        override suspend fun getAllCredentials(botId: String): Map<String, String> =
            map[botId]?.toMap() ?: if (storedToken != null) mapOf("bot_token" to storedToken!!) else emptyMap()

        override suspend fun deleteCredentials(botId: String) {
            map.remove(botId)
            storedToken = null
        }
        override suspend fun hasRequiredCredentials(botId: String, requiredKeys: List<String>): Boolean =
            requiredKeys.all { hasCredential(botId, it) }

        override suspend fun saveToken(token: String) { storedToken = token }
        override suspend fun getToken(): String? = storedToken
        override suspend fun clearToken() { storedToken = null }
        override suspend fun hasToken(): Boolean = !storedToken.isNullOrBlank()
    }

    private class FakeTelegramApiClient : TelegramApiClient() {
        override suspend fun getMe(token: String): Result<TelegramUser> {
            return if (token.startsWith("VALID")) {
                Result.success(TelegramUser(id = 1, isBot = true, firstName = "Bot", username = "valid_bot"))
            } else {
                Result.failure(IllegalArgumentException("Invalid token"))
            }
        }
    }

    private fun sampleBot(id: String, name: String, version: String = "1.0.0") = BotMetadata(
        id = id,
        name = name,
        summary = "Summary for $name",
        description = "Description for $name",
        author = "TapBot Team",
        version = version,
        iconUrl = "https://cdn.tapbot.dev/$id.png",
        category = "Utilities",
        requiredCredentials = listOf(BotCredentialSpec.TELEGRAM_BOT_TOKEN),
        packageInfo = BotPackageInfo(
            packageUrl = "https://cdn.tapbot.dev/pkg.botpkg",
            sha256Checksum = "feedbeef",
            runtimeType = "native_art"
        )
    )

    @Test
    fun `start fails when no token is present in CredentialStore`() = runBlocking {
        val credStore = FakeCredentialStore(null)
        val api = FakeTelegramApiClient()
        val actions = mutableListOf<String>()

        val manager = DefaultBotInstanceManager(
            credentialStore = credStore,
            telegramApi = api,
            serviceLauncher = { action, _ -> actions.add(action) }
        )
        val result = manager.start()

        assertTrue(result.isFailure)
        assertTrue(manager.activeState.value is BotRuntimeState.Error)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `start triggers ACTION_START_BOT when token exists`() = runBlocking {
        val credStore = FakeCredentialStore("VALID_TOKEN")
        val api = FakeTelegramApiClient()
        val actions = mutableListOf<String>()

        val manager = DefaultBotInstanceManager(
            credentialStore = credStore,
            telegramApi = api,
            serviceLauncher = { action, _ -> actions.add(action) }
        )
        val result = manager.start()

        assertTrue(result.isSuccess)
        assertEquals(listOf(BotForegroundService.ACTION_START_BOT), actions)
    }

    @Test
    fun `stop triggers ACTION_STOP_BOT`() = runBlocking {
        val credStore = FakeCredentialStore("VALID_TOKEN")
        val api = FakeTelegramApiClient()
        val actions = mutableListOf<String>()

        val manager = DefaultBotInstanceManager(
            credentialStore = credStore,
            telegramApi = api,
            serviceLauncher = { action, _ -> actions.add(action) }
        )
        val result = manager.stop()

        assertTrue(result.isSuccess)
        assertEquals(listOf(BotForegroundService.ACTION_STOP_BOT), actions)
    }

    @Test
    fun `restart triggers ACTION_RESTART_BOT`() = runBlocking {
        val credStore = FakeCredentialStore("VALID_TOKEN")
        val api = FakeTelegramApiClient()
        val actions = mutableListOf<String>()

        val manager = DefaultBotInstanceManager(
            credentialStore = credStore,
            telegramApi = api,
            serviceLauncher = { action, _ -> actions.add(action) }
        )
        val result = manager.restart()

        assertTrue(result.isSuccess)
        assertEquals(listOf(BotForegroundService.ACTION_RESTART_BOT), actions)
    }

    @Test
    fun `validateToken checks token with Telegram API`() = runBlocking {
        val credStore = FakeCredentialStore()
        val api = FakeTelegramApiClient()

        val manager = DefaultBotInstanceManager(
            credentialStore = credStore,
            telegramApi = api,
            serviceLauncher = { _, _ -> }
        )

        val validRes = manager.validateToken("VALID_TOKEN")
        assertTrue(validRes.isSuccess)
        assertEquals("valid_bot", validRes.getOrNull()?.username)

        val invalidRes = manager.validateToken("BAD_TOKEN")
        assertFalse(invalidRes.isSuccess)
    }

    // -------------------------------------------------------------------------
    // Multi-bot independent management tests
    // -------------------------------------------------------------------------

    @Test
    fun `install creates distinct instances for Music Bot, AI Bot, and Utility Bot`() = runBlocking {
        val credStore = FakeCredentialStore()
        val api = FakeTelegramApiClient()
        val manager = DefaultBotInstanceManager(credentialStore = credStore, telegramApi = api)

        val tempPkg = File.createTempFile("test", ".botpkg")

        val musicInst = manager.install(sampleBot("bot_music", "Music Bot"), tempPkg).getOrThrow()
        val aiInst = manager.install(sampleBot("bot_ai", "AI Bot"), tempPkg).getOrThrow()
        val utilInst = manager.install(sampleBot("bot_utility", "Utility Bot"), tempPkg).getOrThrow()

        val all = manager.instances.value
        assertEquals(3, all.size)
        assertEquals("inst_bot_music", musicInst.installationId)
        assertEquals("inst_bot_ai", aiInst.installationId)
        assertEquals("inst_bot_utility", utilInst.installationId)

        assertEquals("Music Bot", manager.getInstance(musicInst.installationId)?.name)
        assertEquals("AI Bot", manager.getInstance(aiInst.installationId)?.name)
        assertEquals("Utility Bot", manager.getInstance(utilInst.installationId)?.name)
    }

    @Test
    fun `install prevents conflicting duplicate installations of the same bot ID`() = runBlocking {
        val credStore = FakeCredentialStore()
        val api = FakeTelegramApiClient()
        val manager = DefaultBotInstanceManager(credentialStore = credStore, telegramApi = api)
        val tempPkg = File.createTempFile("test", ".botpkg")

        val res1 = manager.install(sampleBot("bot_music", "Music Bot"), tempPkg)
        assertTrue(res1.isSuccess)

        val res2 = manager.install(sampleBot("bot_music", "Music Bot Duplicate"), tempPkg)
        assertTrue(res2.isFailure)
        assertTrue(res2.exceptionOrNull()?.message?.contains("Conflicting installation") == true)
        assertEquals(1, manager.instances.value.size)
    }

    @Test
    fun `start prevents duplicate telegram connections when token is shared across bots`() = runBlocking {
        val credStore = FakeCredentialStore()
        val api = FakeTelegramApiClient()
        val launched = mutableListOf<Pair<String, String?>>()

        val manager = DefaultBotInstanceManager(
            credentialStore = credStore,
            telegramApi = api,
            serviceLauncher = { action, id -> launched.add(action to id) }
        )
        val tempPkg = File.createTempFile("test", ".botpkg")

        val bot1 = manager.install(sampleBot("bot_music", "Music Bot"), tempPkg).getOrThrow()
        val bot2 = manager.install(sampleBot("bot_ai", "AI Bot"), tempPkg).getOrThrow()

        // Same token saved for both
        credStore.saveCredential("bot_music", "bot_token", "VALID_TOKEN_123")
        credStore.saveCredential("bot_ai", "bot_token", "VALID_TOKEN_123")

        // Start bot 1 -> success
        val start1 = manager.start(bot1.installationId)
        assertTrue(start1.isSuccess)
        manager.updateInstanceStatus(bot1.installationId, BotInstanceStatus.Running(10, 2))

        // Start bot 2 with SAME token -> must be rejected to prevent Telegram 409 conflict
        val start2 = manager.start(bot2.installationId)
        assertTrue(start2.isFailure)
        assertTrue(start2.exceptionOrNull()?.message?.contains("Duplicate Telegram connection prevented") == true)
    }

    @Test
    fun `start prevents duplicate processes on already running instance`() = runBlocking {
        val credStore = FakeCredentialStore()
        val api = FakeTelegramApiClient()
        val actions = mutableListOf<String>()

        val manager = DefaultBotInstanceManager(
            credentialStore = credStore,
            telegramApi = api,
            serviceLauncher = { action, _ -> actions.add(action) }
        )
        val tempPkg = File.createTempFile("test", ".botpkg")
        val bot = manager.install(sampleBot("bot_music", "Music Bot"), tempPkg).getOrThrow()
        credStore.saveCredential("bot_music", "bot_token", "VALID_TOKEN_1")

        manager.start(bot.installationId)
        assertEquals(1, actions.size)

        // Mark running
        manager.updateInstanceStatus(bot.installationId, BotInstanceStatus.Running(5, 1))

        // Call start again -> no-op, no duplicate service launch
        manager.start(bot.installationId)
        assertEquals(1, actions.size)
    }

    @Test
    fun `uninstall cleans up instance, packages, credentials, and stops running process`() = runBlocking {
        val credStore = FakeCredentialStore()
        val api = FakeTelegramApiClient()
        val actions = mutableListOf<String>()

        val manager = DefaultBotInstanceManager(
            credentialStore = credStore,
            telegramApi = api,
            serviceLauncher = { action, _ -> actions.add(action) }
        )
        val tempPkg = File.createTempFile("test", ".botpkg")
        val bot = manager.install(sampleBot("bot_music", "Music Bot"), tempPkg).getOrThrow()
        credStore.saveCredential("bot_music", "bot_token", "VALID_TOKEN")

        manager.updateInstanceStatus(bot.installationId, BotInstanceStatus.Running(1, 1))

        // Uninstall
        val uninstalled = manager.uninstall(bot.installationId)
        assertTrue(uninstalled.isSuccess)

        // Verifications
        assertEquals(0, manager.instances.value.size)
        assertFalse(credStore.hasCredential("bot_music", "bot_token"))
        assertTrue(actions.contains(BotForegroundService.ACTION_STOP_BOT))
    }

    @Test
    fun `recordCrash updates crash count and transitions state to Crashed`() = runBlocking {
        val credStore = FakeCredentialStore()
        val api = FakeTelegramApiClient()
        val manager = DefaultBotInstanceManager(credentialStore = credStore, telegramApi = api)
        val tempPkg = File.createTempFile("test", ".botpkg")

        val bot = manager.install(sampleBot("bot_util", "Utility Bot"), tempPkg).getOrThrow()

        manager.recordCrash(bot.installationId, "Simulated network timeout crash")

        val updated = manager.getInstance(bot.installationId)
        assertNotNull(updated)
        assertTrue(updated!!.status.isCrashed)
        val crashed = updated.status as BotInstanceStatus.Crashed
        assertEquals("Simulated network timeout crash", crashed.reason)
        assertEquals(1, crashed.crashCount)
        assertEquals(1, updated.crashState?.crashCount)
    }

    @Test
    fun `update rejects downgrade attacks`() = runBlocking {
        val credStore = FakeCredentialStore()
        val api = FakeTelegramApiClient()
        val manager = DefaultBotInstanceManager(credentialStore = credStore, telegramApi = api)
        val tempPkg = File.createTempFile("test", ".botpkg")

        val bot = manager.install(sampleBot("bot_music", "Music Bot", version = "2.0.0"), tempPkg).getOrThrow()

        // Attempting to downgrade to 1.0.0
        val downgradeResult = manager.update(bot.installationId, "1.0.0", tempPkg)
        assertTrue(downgradeResult.isFailure)
        assertTrue(downgradeResult.exceptionOrNull() is SecurityException)
    }
}
