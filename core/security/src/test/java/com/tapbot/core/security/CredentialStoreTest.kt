package com.tapbot.core.security

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialStoreTest {

    private class TestCredentialStore : CredentialStore {
        private val vault = mutableMapOf<String, String>()

        override suspend fun saveCredential(botId: String, key: String, secretValue: String) {
            vault["${botId}_$key"] = secretValue
        }

        override suspend fun getCredential(botId: String, key: String): String? {
            return vault["${botId}_$key"]?.takeIf { it.isNotBlank() }
        }

        override suspend fun deleteCredential(botId: String, key: String) {
            vault.remove("${botId}_$key")
        }

        override suspend fun hasCredential(botId: String, key: String): Boolean {
            return !getCredential(botId, key).isNullOrBlank()
        }

        override suspend fun getAllCredentials(botId: String): Map<String, String> {
            val prefix = "${botId}_"
            return vault.filterKeys { it.startsWith(prefix) }
                .mapKeys { it.key.removePrefix(prefix) }
        }

        override suspend fun deleteCredentials(botId: String) {
            val prefix = "${botId}_"
            vault.keys.filter { it.startsWith(prefix) }.toList().forEach { vault.remove(it) }
        }

        override suspend fun hasRequiredCredentials(botId: String, requiredKeys: List<String>): Boolean {
            return requiredKeys.all { hasCredential(botId, it) }
        }
    }

    @Test
    fun saveAndGetCredential_persistsAndRetrievesSecrets() = runTest {
        val store = TestCredentialStore()

        assertFalse(store.hasCredential("music_bot", "bot_token"))
        assertNull(store.getCredential("music_bot", "bot_token"))

        store.saveCredential("music_bot", "bot_token", "123456789:ABC_SECRET_TOKEN")

        assertTrue(store.hasCredential("music_bot", "bot_token"))
        assertEquals("123456789:ABC_SECRET_TOKEN", store.getCredential("music_bot", "bot_token"))
    }

    @Test
    fun deleteCredential_removesSpecificSecret() = runTest {
        val store = TestCredentialStore()
        store.saveCredential("ai_bot", "telegram_token", "tg_123")
        store.saveCredential("ai_bot", "openai_key", "sk_456")

        store.deleteCredential("ai_bot", "openai_key")

        assertTrue(store.hasCredential("ai_bot", "telegram_token"))
        assertFalse(store.hasCredential("ai_bot", "openai_key"))
        assertNull(store.getCredential("ai_bot", "openai_key"))
    }

    @Test
    fun deleteCredentials_removesAllSecretsForBot() = runTest {
        val store = TestCredentialStore()
        store.saveCredential("multi_bot", "token1", "val1")
        store.saveCredential("multi_bot", "token2", "val2")
        store.saveCredential("other_bot", "token3", "val3")

        store.deleteCredentials("multi_bot")

        assertFalse(store.hasCredential("multi_bot", "token1"))
        assertFalse(store.hasCredential("multi_bot", "token2"))
        assertTrue(store.hasCredential("other_bot", "token3"))
    }

    @Test
    fun hasRequiredCredentials_validatesPresenceOfRequiredKeys() = runTest {
        val store = TestCredentialStore()
        store.saveCredential("bot1", "token", "val1")
        store.saveCredential("bot1", "api_key", "val2")

        assertTrue(store.hasRequiredCredentials("bot1", listOf("token", "api_key")))
        assertFalse(store.hasRequiredCredentials("bot1", listOf("token", "api_key", "extra_secret")))
    }

    @Test
    fun backwardCompatibility_singleTokenMethodsDelegateProperly() = runTest {
        val store = TestCredentialStore()

        assertFalse(store.hasToken())
        store.saveToken("123456:LEGACY_POC_TOKEN")
        assertTrue(store.hasToken())
        assertEquals("123456:LEGACY_POC_TOKEN", store.getToken())

        store.clearToken()
        assertFalse(store.hasToken())
        assertNull(store.getToken())
    }
}
