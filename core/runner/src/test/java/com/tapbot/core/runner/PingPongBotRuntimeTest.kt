package com.tapbot.core.runner

import com.tapbot.core.model.LogLevel
import com.tapbot.core.model.TelegramChat
import com.tapbot.core.model.TelegramMessage
import com.tapbot.core.model.TelegramUpdate
import com.tapbot.core.model.TelegramUser
import com.tapbot.core.network.TelegramApiClient
import com.tapbot.core.runner.runtime.BotRuntimeState
import com.tapbot.core.runner.runtime.PingPongBotRuntime
import com.tapbot.core.runner.runtime.RuntimeContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class PingPongBotRuntimeTest {

    private class FakeTelegramApiClient : TelegramApiClient() {
        var sentMessages = mutableListOf<Pair<Long, String>>()
        var updateQueue = mutableListOf<List<TelegramUpdate>>()
        val pollCount = AtomicInteger(0)

        override suspend fun getMe(token: String): Result<TelegramUser> {
            return Result.success(
                TelegramUser(
                    id = 123456789,
                    isBot = true,
                    firstName = "TestBot",
                    username = "test_pingpong_bot"
                )
            )
        }

        override suspend fun getUpdates(
            token: String,
            offset: Long?,
            timeoutSeconds: Int
        ): Result<List<TelegramUpdate>> {
            pollCount.incrementAndGet()
            return if (updateQueue.isNotEmpty()) {
                Result.success(updateQueue.removeAt(0))
            } else {
                delay(100)
                Result.success(emptyList())
            }
        }

        override suspend fun sendMessage(
            token: String,
            chatId: Long,
            text: String,
            parseMode: String?
        ): Result<Boolean> {
            sentMessages.add(chatId to text)
            return Result.success(true)
        }
    }

    @Test
    fun `bot receives ping and replies pong`() = runBlocking {
        val fakeApi = FakeTelegramApiClient()
        val update = TelegramUpdate(
            updateId = 1,
            message = TelegramMessage(
                messageId = 100,
                from = TelegramUser(id = 42, isBot = false, firstName = "Alice", username = "alice"),
                chat = TelegramChat(id = 999, type = "private", username = "alice"),
                date = 1726740000,
                text = "ping"
            )
        )
        fakeApi.updateQueue.add(listOf(update))

        val runtime = PingPongBotRuntime()
        val context = RuntimeContext(
            botId = "test_bot",
            token = "123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ1234567",
            telegramApi = fakeApi,
            log = { _, _, _ -> },
            scope = this
        )

        runtime.initialize(context)
        runtime.start()

        // Wait briefly for poll loop to process update
        delay(300)

        assertTrue(fakeApi.sentMessages.isNotEmpty())
        assertEquals(999L, fakeApi.sentMessages.first().first)
        assertEquals("pong", fakeApi.sentMessages.first().second)

        runtime.stop()
        assertEquals(BotRuntimeState.Stopped, runtime.state.value)
    }

    @Test
    fun `bot receives start command and replies with welcome text`() = runBlocking {
        val fakeApi = FakeTelegramApiClient()
        val update = TelegramUpdate(
            updateId = 2,
            message = TelegramMessage(
                messageId = 101,
                from = TelegramUser(id = 43, isBot = false, firstName = "Bob", username = "bob"),
                chat = TelegramChat(id = 888, type = "private", username = "bob"),
                date = 1726740000,
                text = "/start"
            )
        )
        fakeApi.updateQueue.add(listOf(update))

        val runtime = PingPongBotRuntime()
        val context = RuntimeContext(
            botId = "test_bot",
            token = "123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ1234567",
            telegramApi = fakeApi,
            log = { _, _, _ -> },
            scope = this
        )

        runtime.initialize(context)
        runtime.start()

        delay(300)

        assertTrue(fakeApi.sentMessages.isNotEmpty())
        assertEquals(888L, fakeApi.sentMessages.first().first)
        assertTrue(fakeApi.sentMessages.first().second.contains("Hello!"))

        runtime.stop()
    }
}
