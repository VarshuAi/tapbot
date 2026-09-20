package com.tapbot.core.network

import com.tapbot.core.model.BotCredentialSpec
import com.tapbot.core.model.BotMetadata
import com.tapbot.core.model.BotPackageInfo

/**
 * Interface to fetch catalog metadata published by the developer.
 * Hosted on Cloudflare Workers / D1 / R2.
 */
interface CatalogApi {
    suspend fun getBots(): Result<List<BotMetadata>>
    suspend fun getBotDetails(botId: String): Result<BotMetadata>
}

/**
 * Built-in mock implementation for offline previews, testing, and initial boot.
 */
class MockCatalogApi : CatalogApi {

    private val sampleBots = listOf(
        BotMetadata(
            id = "bot_echo",
            name = "Echo Assistant Bot",
            summary = "A lightweight Telegram bot that responds back with structured diagnostics and echoes user messages.",
            description = "Ideal for testing your Telegram Bot Token and verifying on-device foreground execution. The bot listens for incoming messages via Telegram long-polling and replies back with message metadata, latency, and uptime.",
            author = "TapBot Team",
            version = "1.0.0",
            iconUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=200",
            category = "Utilities",
            tags = listOf("echo", "test", "starter"),
            requiredCredentials = listOf(
                BotCredentialSpec.TELEGRAM_BOT_TOKEN
            ),
            packageInfo = BotPackageInfo(
                packageUrl = "https://assets.tapbot.internal/packages/bot_echo_1.0.0.botpkg",
                sha256Checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                runtimeType = "built-in",
                entryClass = "com.tapbot.core.runner.engine.EchoBotEngine"
            ),
            permissionsRequired = listOf("INTERNET", "FOREGROUND_SERVICE")
        ),
        BotMetadata(
            id = "bot_rss_notifier",
            name = "RSS Channel Broadcaster",
            summary = "Periodically checks your favorite RSS/Atom feeds and broadcasts new articles to your Telegram channel.",
            description = "Runs on your Android device 24/7. Configure your target Telegram Channel ID and feed URL. The bot tracks published items in local SQLite storage and pushes instant alerts with rich formatting.",
            author = "OpenFeed Community",
            version = "1.2.0",
            iconUrl = "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?w=200",
            category = "News & Media",
            tags = listOf("rss", "channel", "news", "automation"),
            requiredCredentials = listOf(
                BotCredentialSpec.TELEGRAM_BOT_TOKEN,
                BotCredentialSpec(
                    key = "target_chat_id",
                    label = "Target Chat or Channel ID",
                    description = "Channel or Group ID (e.g. -1001234567890) where messages will be posted.",
                    isSecret = false,
                    isRequired = true,
                    placeholder = "-1001234567890"
                ),
                BotCredentialSpec(
                    key = "feed_url",
                    label = "RSS / Atom Feed URL",
                    description = "HTTP/HTTPS URL of the RSS feed to monitor.",
                    isSecret = false,
                    isRequired = true,
                    placeholder = "https://news.ycombinator.com/rss"
                )
            ),
            packageInfo = BotPackageInfo(
                packageUrl = "https://assets.tapbot.internal/packages/bot_rss_1.2.0.botpkg",
                sha256Checksum = "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
                runtimeType = "built-in",
                entryClass = "com.tapbot.core.runner.engine.RssNotifierBotEngine"
            ),
            permissionsRequired = listOf("INTERNET", "FOREGROUND_SERVICE")
        ),
        BotMetadata(
            id = "bot_ai_assistant",
            name = "Gemini Smart Assistant",
            summary = "Conversational AI assistant powered by Google Gemini running directly through your personal Telegram account.",
            description = "Turns your personal Telegram bot into a generative AI assistant. Supports multi-turn chats, code analysis, and summarization right in Telegram. Your Gemini API key and Telegram token remain encrypted in your device Keystore.",
            author = "TapBot Labs",
            version = "2.1.0",
            iconUrl = "https://images.unsplash.com/photo-1677442136019-21780ecad995?w=200",
            category = "AI & Productivity",
            tags = listOf("ai", "gemini", "assistant", "productivity"),
            requiredCredentials = listOf(
                BotCredentialSpec.TELEGRAM_BOT_TOKEN,
                BotCredentialSpec(
                    key = "gemini_api_key",
                    label = "Gemini API Key",
                    description = "Obtain a free Google AI Studio API key at https://aistudio.google.com",
                    isSecret = true,
                    isRequired = true,
                    placeholder = "AIzaSy...",
                    helpUrl = "https://aistudio.google.com"
                )
            ),
            packageInfo = BotPackageInfo(
                packageUrl = "https://assets.tapbot.internal/packages/bot_ai_assistant_2.1.0.botpkg",
                sha256Checksum = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                runtimeType = "built-in",
                entryClass = "com.tapbot.core.runner.engine.GeminiBotEngine"
            ),
            permissionsRequired = listOf("INTERNET", "FOREGROUND_SERVICE")
        ),
        BotMetadata(
            id = "bot_music",
            name = "Music Bot",
            summary = "Streams audio, queues playlists, and provides music playback controls.",
            description = "Runs locally on Android to manage Telegram audio playback queues, interactive commands, and volume control.",
            author = "TapBot Audio Labs",
            version = "1.0.0",
            iconUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=200",
            category = "Media",
            tags = listOf("music", "audio", "player"),
            requiredCredentials = listOf(
                BotCredentialSpec.TELEGRAM_BOT_TOKEN,
                BotCredentialSpec(
                    key = "spotify_client_id",
                    label = "Spotify Client ID",
                    description = "Optional Spotify integration client identifier",
                    isSecret = false,
                    isRequired = false,
                    placeholder = "Optional client id"
                )
            ),
            packageInfo = BotPackageInfo(
                packageUrl = "https://assets.tapbot.internal/packages/bot_music_1.0.0.botpkg",
                sha256Checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                runtimeType = "native_art"
            ),
            permissionsRequired = listOf("INTERNET", "FOREGROUND_SERVICE")
        ),
        BotMetadata(
            id = "bot_ai",
            name = "AI Bot",
            summary = "Generative AI conversational assistant powered on-device.",
            description = "Provides multi-turn AI reasoning, text summarization, and code explanation directly through your personal Telegram bot.",
            author = "TapBot AI Labs",
            version = "1.0.0",
            iconUrl = "https://images.unsplash.com/photo-1677442136019-21780ecad995?w=200",
            category = "Productivity",
            tags = listOf("ai", "assistant", "productivity"),
            requiredCredentials = listOf(
                BotCredentialSpec.TELEGRAM_BOT_TOKEN
            ),
            packageInfo = BotPackageInfo(
                packageUrl = "https://assets.tapbot.internal/packages/bot_ai_1.0.0.botpkg",
                sha256Checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                runtimeType = "native_art"
            ),
            permissionsRequired = listOf("INTERNET", "FOREGROUND_SERVICE")
        ),
        BotMetadata(
            id = "bot_utility",
            name = "Utility Bot",
            summary = "System diagnostics, uptime monitoring, latency checks, and test crash simulation.",
            description = "Monitors Android ART execution metrics, memory usage, and allows resilience and crash testing via /crash.",
            author = "TapBot Core",
            version = "1.0.0",
            iconUrl = "https://images.unsplash.com/photo-1518770660439-4636190af475?w=200",
            category = "Utilities",
            tags = listOf("utility", "diagnostics", "tools"),
            requiredCredentials = listOf(
                BotCredentialSpec.TELEGRAM_BOT_TOKEN
            ),
            packageInfo = BotPackageInfo(
                packageUrl = "https://assets.tapbot.internal/packages/bot_utility_1.0.0.botpkg",
                sha256Checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                runtimeType = "native_art"
            ),
            permissionsRequired = listOf("INTERNET", "FOREGROUND_SERVICE")
        )
    )

    override suspend fun getBots(): Result<List<BotMetadata>> {
        return Result.success(sampleBots)
    }

    override suspend fun getBotDetails(botId: String): Result<BotMetadata> {
        val bot = sampleBots.find { it.id == botId }
        return if (bot != null) {
            Result.success(bot)
        } else {
            Result.failure(NoSuchElementException("Bot not found: $botId"))
        }
    }
}
