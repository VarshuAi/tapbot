package com.tapbot.core.runner

import com.tapbot.core.logging.InMemoryRingBufferLogRepository
import com.tapbot.core.model.TelegramUser
import com.tapbot.core.network.TelegramApiClient
import com.tapbot.core.runner.manager.DefaultBotInstanceManager
import com.tapbot.core.runner.runtime.BotRuntimeState
import com.tapbot.core.security.CredentialStore
import kotlinx.coroutines.delay
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
        val logRepo = InMemoryRingBufferLogRepository()

        val manager = DefaultBotInstanceManager(credStore, api, logRepo)
        val result = manager.start()

        assertTrue(result.isFailure)
        assertTrue(manager.activeState.value is BotRuntimeState.Error)
    }

    @Test
    fun `validateToken checks token with Telegram API`() = runBlocking {
        val credStore = FakeCredentialStore()
        val api = FakeTelegramApiClient()
        val logRepo = InMemoryRingBufferLogRepository()

        val manager = DefaultBotInstanceManager(credStore, api, logRepo)

        val validRes = manager.validateToken("VALID_TOKEN")
        assertTrue(validRes.isSuccess)
        assertEquals("valid_bot", validRes.getOrNull()?.username)

        val invalidRes = manager.validateToken("BAD_TOKEN")
        assertFalse(invalidRes.isSuccess)
    }
}
