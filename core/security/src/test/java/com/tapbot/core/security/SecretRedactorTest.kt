package com.tapbot.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretRedactorTest {

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
    fun `redact preserves normal text without tokens`() {
        val normalText = "Received update: message text 'ping' from @alice"
        val sanitized = SecretRedactor.redact(normalText)
        assertEquals(normalText, sanitized)
    }
}
