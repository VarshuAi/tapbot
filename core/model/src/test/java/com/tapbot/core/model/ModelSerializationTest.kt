package com.tapbot.core.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `serialize and deserialize BotMetadata`() {
        val bot = BotMetadata(
            id = "bot_sample",
            name = "Sample Bot",
            summary = "Sample summary",
            description = "Full description",
            author = "Architect",
            version = "1.0.0",
            iconUrl = "https://example.com/icon.png",
            category = "Utilities",
            tags = listOf("sample", "test"),
            requiredCredentials = listOf(BotCredentialSpec.TELEGRAM_BOT_TOKEN),
            packageInfo = BotPackageInfo(
                packageUrl = "https://example.com/bot.botpkg",
                sha256Checksum = "deadbeef"
            )
        )

        val serialized = json.encodeToString(bot)
        val deserialized = json.decodeFromString<BotMetadata>(serialized)

        assertEquals("bot_sample", deserialized.id)
        assertEquals("Sample Bot", deserialized.name)
        assertEquals(1, deserialized.requiredCredentials.size)
        assertEquals("bot_token", deserialized.requiredCredentials.first().key)
    }

    @Test
    fun `deserialize TelegramApiResponse with update`() {
        val rawJson = """
            {
                "ok": true,
                "result": [
                    {
                        "update_id": 998877,
                        "message": {
                            "message_id": 42,
                            "date": 1726740000,
                            "chat": {
                                "id": 1234567,
                                "type": "private",
                                "first_name": "Alice"
                            },
                            "from": {
                                "id": 1234567,
                                "is_bot": false,
                                "first_name": "Alice",
                                "username": "alice_dev"
                            },
                            "text": "/start"
                        }
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<TelegramApiResponse<List<TelegramUpdate>>>(rawJson)
        assertTrue(response.ok)
        assertNotNull(response.result)
        assertEquals(1, response.result?.size)

        val update = response.result?.first()
        assertEquals(998877L, update?.updateId)
        assertEquals("/start", update?.message?.text)
        assertEquals("alice_dev", update?.message?.from?.username)
    }
}
