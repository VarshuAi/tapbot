package com.tapbot.core.security

/**
 * High-performance regex-based secret redaction engine.
 * Ensures user Telegram Bot Tokens and sensitive secrets are NEVER printed in logs,
 * console outputs, error traces, or network logs.
 */
object SecretRedactor {

    // Matches standard Telegram Bot Tokens: e.g. 123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ_12345
    private val TELEGRAM_TOKEN_REGEX = Regex(
        """\b(\d{6,12}):([a-zA-Z0-9_-]{30,50})\b"""
    )

    // Matches Telegram bot URLs: e.g. /bot123456789:ABC.../
    private val TELEGRAM_URL_BOT_REGEX = Regex(
        """/bot(\d{6,12}:[a-zA-Z0-9_-]{30,50})/"""
    )

    /**
     * Sanitizes any text string by replacing detected Telegram bot tokens with a safe mask.
     * Preserves the first 4 digits of the bot ID for debugging while completely masking the secret hash.
     */
    fun redact(text: String?): String {
        if (text.isNullOrEmpty()) return ""

        var sanitized = text

        // Redact in URLs first
        sanitized = TELEGRAM_URL_BOT_REGEX.replace(sanitized) { matchResult ->
            val fullToken = matchResult.groupValues[1]
            val masked = maskToken(fullToken)
            "/bot$masked/"
        }

        // Redact raw tokens in text
        sanitized = TELEGRAM_TOKEN_REGEX.replace(sanitized) { matchResult ->
            val fullToken = matchResult.value
            maskToken(fullToken)
        }

        return sanitized
    }

    private fun maskToken(token: String): String {
        val parts = token.split(":")
        return if (parts.size == 2) {
            val botIdPrefix = parts[0].take(4)
            "${botIdPrefix}****:[REDACTED_TELEGRAM_TOKEN]"
        } else {
            "[REDACTED_TELEGRAM_TOKEN]"
        }
    }
}
