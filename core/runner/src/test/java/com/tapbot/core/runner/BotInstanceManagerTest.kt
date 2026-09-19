package com.tapbot.core.runner

import com.tapbot.core.model.TelegramUser
import com.tapbot.core.network.TelegramApiClient
import com.tapbot.core.runner.manager.DefaultBotInstanceManager
import com.tapbot.core.runner.runtime.BotRuntimeState
import com.tapbot.core.security.CredentialStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BotInstanceManagerTest {

    private class FakeCredentialStore(var storedToken: String? = null) : CredentialStore {
        override suspend fun saveToken(token: String) { storedToken = token }
        override suspend fun getToken(): String? = storedToken
        override suspend fun clearToken() { storedToken = null }
        override suspend fun hasToken(): Boolean = !storedToken.isNullOrBlank()
    }

    private class FakeTelegramApiClient : TelegramApiClient() {
        override suspend fun getMe(token: String): Result<TelegramUser> {
            return if (token == "VALID_TOKEN") {
                Result.success(TelegramUser(id = 1, isBot = true, firstName = "Bot", username = "valid_bot"))
            } else {
                Result.failure(IllegalArgumentException("Invalid token"))
            }
        }
    }

    @Test
    fun `start fails when no token is present in CredentialStore`() = runBlocking {
        val credStore = FakeCredentialStore(null)
        val api = FakeTelegramApiClient()
        val actions = mutableListOf<String>()

        val manager = DefaultBotInstanceManager(
            credentialStore = credStore,
            telegramApi = api,
            serviceLauncher = { actions.add(it) }
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
            serviceLauncher = { actions.add(it) }
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
            serviceLauncher = { actions.add(it) }
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
            serviceLauncher = { actions.add(it) }
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
            serviceLauncher = {}
        )

        val validRes = manager.validateToken("VALID_TOKEN")
        assertTrue(validRes.isSuccess)
        assertEquals("valid_bot", validRes.getOrNull()?.username)

        val invalidRes = manager.validateToken("BAD_TOKEN")
        assertFalse(invalidRes.isSuccess)
    }
}
