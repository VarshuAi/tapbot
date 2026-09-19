package com.tapbot.core.security

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretRedactorTest {

    @After
    fun tearDown() {
        SecretRedactor.clearRegisteredSecrets()
    }

    @Test
    fun `redact raw Telegram bot token in message`() {
        val rawToken = "123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ12345a"
        val text = "Connecting with token $rawToken to Telegram server"

        val sanitized = SecretRedactor.redact(text)

        assertFalse("Sanitized text should not contain raw token", sanitized.contains(rawToken))
        assertTrue("Sanitized text should contain REDACTED", sanitized.contains("[REDACTED_TELEGRAM_TOKEN]"))
        assertTrue("Sanitized text should preserve prefix", sanitized.contains("1234****"))
    }

    @Test
    fun `redact bot token in Telegram API URL`() {
        val rawToken = "987654321:abcdefghijklmnopqrstuvwxyz0123456"
        val url = "https://api.telegram.org/bot$rawToken/getUpdates?timeout=30"

        val sanitized = SecretRedactor.redact(url)

        assertFalse(sanitized.contains(rawToken))
        assertTrue(sanitized.contains("/bot9876****:[REDACTED_TELEGRAM_TOKEN]/getUpdates"))
    }

    @Test
    fun `redact OpenAI and Anthropic API keys`() {
        val openAiKey = "sk-proj-1234567890abcdefghijklmnopqrstuvwxyzABCD"
        val anthropicKey = "sk-ant-api03-1234567890abcdefghijklmnopqrstuvwxyz"

        val text = "OpenAI key: $openAiKey and Anthropic key: $anthropicKey"
        val sanitized = SecretRedactor.redact(text)

        assertFalse(sanitized.contains(openAiKey))
        assertFalse(sanitized.contains(anthropicKey))
        assertTrue(sanitized.contains("[REDACTED_API_KEY]"))
    }

    @Test
    fun `redact Bearer authorization headers`() {
        val bearerToken = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xyz123456789"
        val logLine = "Sending request with header Authorization: $bearerToken"

        val sanitized = SecretRedactor.redact(logLine)

        assertFalse(sanitized.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xyz123456789"))
        assertTrue(sanitized.contains("[REDACTED_BEARER_TOKEN]"))
    }

    @Test
    fun `redact JSON key-value credential entries`() {
        val jsonPayload = """{"bot_token": "secret_custom_token_9999", "status": "active"}"""
        val sanitized = SecretRedactor.redact(jsonPayload)

        assertFalse(sanitized.contains("secret_custom_token_9999"))
        assertTrue(sanitized.contains("[REDACTED_CREDENTIAL]"))
    }

    @Test
    fun `redact dynamically registered user secrets`() {
        val customUserApiKey = "my-custom-super-confidential-secret-api-key"
        SecretRedactor.registerSecret(customUserApiKey)

        val debugLog = "DEBUG: initialized client with key $customUserApiKey in memory"
        val sanitized = SecretRedactor.redact(debugLog)

        assertFalse(sanitized.contains(customUserApiKey))
        assertTrue(sanitized.contains("[REDACTED_SECRET]"))
    }

    @Test
    fun `redact preserves normal text without tokens`() {
        val normalText = "Received update: message text 'ping' from @alice"
        val sanitized = SecretRedactor.redact(normalText)
        assertEquals(normalText, sanitized)
    }
}
