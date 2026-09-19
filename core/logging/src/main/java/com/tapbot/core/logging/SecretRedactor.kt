package com.tapbot.core.logging

/**
 * Regex-based secret redaction engine for logs.
 * Masks raw Telegram bot tokens and URLs so that tokens never leak into console,
 * memory buffers, logcat, or persistent files.
 */
object SecretRedactor {

    private val TELEGRAM_TOKEN_REGEX = Regex(
        """\b(\d{6,12}):([a-zA-Z0-9_-]{30,50})\b"""
    )

    private val TELEGRAM_URL_BOT_REGEX = Regex(
        """/bot(\d{6,12}:[a-zA-Z0-9_-]{30,50})/"""
    )

    fun redact(text: String?): String {
        if (text.isNullOrEmpty()) return ""

        var sanitized = text

        sanitized = TELEGRAM_URL_BOT_REGEX.replace(sanitized) { matchResult ->
            val fullToken = matchResult.groupValues[1]
            val masked = maskToken(fullToken)
            "/bot$masked/"
        }

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
