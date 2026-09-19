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
    }
}
