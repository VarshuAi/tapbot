package com.tapbot.core.model

import kotlinx.serialization.Serializable

/**
 * Specification for a required or optional credential for a bot.
 * Allows the Android app to render dynamic input forms without hardcoding bot-specific fields.
 */
@Serializable
data class BotCredentialSpec(
    val key: String,
    val label: String,
    val description: String,
    val isSecret: Boolean = true,
    val isRequired: Boolean = true,
    val placeholder: String = "",
    val helpUrl: String? = null
) {
    companion object {
        val TELEGRAM_BOT_TOKEN = BotCredentialSpec(
            key = "bot_token",
            label = "Telegram Bot Token",
            description = "Token obtained from @BotFather in Telegram.",
            isSecret = true,
            isRequired = true,
            placeholder = "123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ",
            helpUrl = "https://t.me/BotFather"
        )

        val TELEGRAM_API_ID = BotCredentialSpec(
            key = "api_id",
            label = "App api_id",
            description = "App API ID obtained from my.telegram.org (under API development tools).",
            isSecret = false,
            isRequired = true,
            placeholder = "12345678",
            helpUrl = "https://my.telegram.org"
        )

        val TELEGRAM_API_HASH = BotCredentialSpec(
            key = "api_hash",
            label = "App api_hash",
            description = "App API Hash (32-character hexadecimal) obtained from my.telegram.org.",
            isSecret = true,
            isRequired = true,
            placeholder = "0123456789abcdef0123456789abcdef",
            helpUrl = "https://my.telegram.org"
        )
    }
}
