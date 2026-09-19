package com.tapbot.core.logging

import com.tapbot.core.model.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class RingBufferLogRepositoryTest {

    @Test
    fun `appendLog respects maximum capacity ring buffer boundary`() {
        val repo = InMemoryRingBufferLogRepository(maxCapacityPerBot = 5)
        val botId = "test_bot"

        for (i in 1..10) {
            repo.appendLog(botId, LogLevel.INFO, "TestTag", "Message $i")
        }

        val logs = repo.getLogStream(botId).value
        assertEquals(5, logs.size)
        assertEquals("Message 6", logs.first().message)
        assertEquals("Message 10", logs.last().message)
    }

    @Test
    fun `clearLogs empties buffer`() {
        val repo = InMemoryRingBufferLogRepository(maxCapacityPerBot = 10)
        val botId = "test_bot"

        repo.appendLog(botId, LogLevel.INFO, "TestTag", "Hello")
        repo.appendLog(botId, LogLevel.ERROR, "TestTag", "World")
        assertEquals(2, repo.getLogStream(botId).value.size)

        repo.clearLogs(botId)
        assertEquals(0, repo.getLogStream(botId).value.size)
    }
}
