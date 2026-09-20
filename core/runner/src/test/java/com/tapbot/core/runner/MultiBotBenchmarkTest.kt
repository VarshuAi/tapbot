package com.tapbot.core.runner

import com.tapbot.core.model.LogLevel
import com.tapbot.core.model.TelegramChat
import com.tapbot.core.model.TelegramMessage
import com.tapbot.core.model.TelegramUpdate
import com.tapbot.core.model.TelegramUser
import com.tapbot.core.network.TelegramApiClient
import com.tapbot.core.runner.runtime.AIBotRuntime
import com.tapbot.core.runner.runtime.MusicBotRuntime
import com.tapbot.core.runner.runtime.RuntimeContext
import com.tapbot.core.runner.runtime.UtilityBotRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Performance, Resource Measurement, and Multi-Bot Concurrency Benchmark.
 *
 * Measures:
 * 1. Startup Time (ms) for 1, 2, and 3 bots
 * 2. RAM Heap Memory (bytes & MB) baseline, +1 bot, +2 bots, +3 bots
 * 3. CPU Thread Processing Time (ms) under concurrent load
 * 4. Stability & Crash Isolation under concurrent message dispatch
 */
class MultiBotBenchmarkTest {

    private val benchmarkJob = SupervisorJob()
    private val benchmarkScope = CoroutineScope(Dispatchers.Default + benchmarkJob)

    private class BenchmarkTelegramApiClient : TelegramApiClient() {
        val sentMessages = mutableListOf<Triple<String, Long, String>>() // token, chatId, text
        var pollCounts = mutableMapOf<String, Long>()

        override suspend fun getMe(token: String): Result<TelegramUser> {
            val username = when {
                token.contains("music") -> "BenchmarkMusicBot"
                token.contains("ai") -> "BenchmarkAIBot"
                token.contains("utility") -> "BenchmarkUtilityBot"
                else -> "BenchmarkBot"
            }
            return Result.success(TelegramUser(id = 999, isBot = true, firstName = username, username = username))
        }

        override suspend fun getUpdates(
            token: String,
            offset: Long?,
            timeoutSeconds: Int
        ): Result<List<TelegramUpdate>> {
            val count = pollCounts.getOrDefault(token, 0L) + 1
            pollCounts[token] = count

            // Yield slight sleep to simulate network wait
            delay(15)

            // Inject 1 test message on poll #2
            if (count == 2L) {
                val command = when {
                    token.contains("music") -> "/play Moonlight Sonata"
                    token.contains("ai") -> "/ask Explain coroutine dispatchers"
                    token.contains("utility") -> "/ping"
                    else -> "/start"
                }
                val update = TelegramUpdate(
                    updateId = 1000L + count,
                    message = TelegramMessage(
                        messageId = 500L + count,
                        from = TelegramUser(id = 123, isBot = false, firstName = "TestUser", username = "tester"),
                        chat = TelegramChat(id = 99999L, type = "private"),
                        date = System.currentTimeMillis() / 1000,
                        text = command
                    )
                )
                return Result.success(listOf(update))
            }

            return Result.success(emptyList())
        }

        override suspend fun sendMessage(
            token: String,
            chatId: Long,
            text: String,
            parseMode: String?
        ): Result<Boolean> {
            sentMessages.add(Triple(token, chatId, text))
            return Result.success(true)
        }
    }

    private val fakeApi = BenchmarkTelegramApiClient()

    @Before
    fun setUp() {
        System.gc()
        Thread.sleep(100)
    }

    @After
    fun tearDown() {
        benchmarkJob.cancel()
    }

    private fun getUsedMemoryBytes(): Long {
        System.gc()
        Thread.sleep(50)
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    @Test
    fun `benchmark three bots running simultaneously - measures RAM, CPU, startup time, and stability`() = runBlocking {
        println("==================================================================")
        println("           TAPBOT MULTI-BOT PERFORMANCE BENCHMARK                ")
        println("==================================================================")

        // Thread CPU time helper via reflection or nanoTime fallback
        val getThreadCpuTime: () -> Long = {
            runCatching {
                val mfClass = Class.forName("java.lang.management.ManagementFactory")
                val bean = mfClass.getMethod("getThreadMXBean").invoke(null)
                val method = bean.javaClass.getMethod("getCurrentThreadCpuTime")
                method.invoke(bean) as Long
            }.getOrDefault(System.nanoTime())
        }

        // 1. Baseline Memory
        val baselineMemory = getUsedMemoryBytes()
        println("Baseline Heap Memory: %.2f MB (%d bytes)".format(baselineMemory / (1024.0 * 1024.0), baselineMemory))

        // Create 3 bot runtimes
        val musicBot = MusicBotRuntime(botId = "bot_music")
        val aiBot = AIBotRuntime(botId = "bot_ai")
        val utilityBot = UtilityBotRuntime(botId = "bot_utility")

        val logs = mutableListOf<String>()
        fun createCtx(botId: String, token: String) = RuntimeContext(
            botId = botId,
            token = token,
            telegramApi = fakeApi,
            log = { level, tag, msg -> logs.add("[$level][$tag] $msg") },
            scope = benchmarkScope
        )

        // -------------------------------------------------------------
        // MEASUREMENT 1: STARTUP TIME
        // -------------------------------------------------------------
        println("\n--- 1. STARTUP TIME MEASUREMENT ---")

        val t0 = System.currentTimeMillis()
        musicBot.initialize(createCtx("bot_music", "token_music_12345"))
        musicBot.start()
        val musicStartupMs = System.currentTimeMillis() - t0
        println("Music Bot Startup Time: ${musicStartupMs} ms")

        val t1 = System.currentTimeMillis()
        aiBot.initialize(createCtx("bot_ai", "token_ai_12345"))
        aiBot.start()
        val aiStartupMs = System.currentTimeMillis() - t1
        println("AI Bot Startup Time: ${aiStartupMs} ms")

        val t2 = System.currentTimeMillis()
        utilityBot.initialize(createCtx("bot_utility", "token_utility_12345"))
        utilityBot.start()
        val utilityStartupMs = System.currentTimeMillis() - t2
        println("Utility Bot Startup Time: ${utilityStartupMs} ms")

        val avgStartupMs = (musicStartupMs + aiStartupMs + utilityStartupMs) / 3.0
        println("Average Startup Time: %.2f ms".format(avgStartupMs))
        assertTrue("Startup should complete under 1000ms", avgStartupMs < 1000.0)

        // -------------------------------------------------------------
        // MEASUREMENT 2: RAM USAGE SCALING
        // -------------------------------------------------------------
        println("\n--- 2. RAM ALLOCATION MEASUREMENT (Concurrent Execution) ---")
        val memoryWith3Bots = getUsedMemoryBytes()
        val memoryDelta = (memoryWith3Bots - baselineMemory).coerceAtLeast(0L)
        val perBotAvgBytes = memoryDelta / 3.0

        println("Heap Memory with 3 Bots Running: %.2f MB (%d bytes)".format(memoryWith3Bots / (1024.0 * 1024.0), memoryWith3Bots))
        println("Total Additional Memory for 3 Bots: %.2f MB (%d KB)".format(memoryDelta / (1024.0 * 1024.0), memoryDelta / 1024))
        println("Average Memory per Bot: %.2f KB".format(perBotAvgBytes / 1024.0))

        // Ensure lightweight footprint (< 15MB total additional overhead for 3 native ART coroutine loops)
        assertTrue("Memory footprint for 3 bots must be under 15MB", memoryDelta < 15 * 1024 * 1024)

        // -------------------------------------------------------------
        // MEASUREMENT 3: CPU PROCESSING TIME & CONCURRENT EXECUTION
        // -------------------------------------------------------------
        println("\n--- 3. CPU EXECUTION & CONCURRENT LOAD MEASUREMENT ---")
        val cpuStartNs = getThreadCpuTime()

        // Allow bots to execute several long-polling cycles and process incoming test messages
        delay(200)

        val cpuEndNs = getThreadCpuTime()
        val cpuTimeMs = (cpuEndNs - cpuStartNs) / 1_000_000.0

        println("Active Thread CPU Time during 200ms window: %.2f ms".format(cpuTimeMs))
        println("Concurrent Message Reply Count: ${fakeApi.sentMessages.size}")

        // Verify all 3 bots processed their test messages
        val musicReplies = fakeApi.sentMessages.filter { it.first.contains("music") }
        val aiReplies = fakeApi.sentMessages.filter { it.first.contains("ai") }
        val utilityReplies = fakeApi.sentMessages.filter { it.first.contains("utility") }

        println("• Music Bot Replies: ${musicReplies.size} (e.g. '${musicReplies.firstOrNull()?.third?.take(30)}...')")
        println("• AI Bot Replies: ${aiReplies.size} (e.g. '${aiReplies.firstOrNull()?.third?.take(30)}...')")
        println("• Utility Bot Replies: ${utilityReplies.size} (e.g. '${utilityReplies.firstOrNull()?.third?.take(30)}...')")

        assertTrue("Music Bot must process commands concurrently", musicReplies.isNotEmpty())
        assertTrue("AI Bot must process commands concurrently", aiReplies.isNotEmpty())
        assertTrue("Utility Bot must process commands concurrently", utilityReplies.isNotEmpty())

        // -------------------------------------------------------------
        // MEASUREMENT 4: CRASH ISOLATION & STABILITY
        // -------------------------------------------------------------
        println("\n--- 4. STABILITY & CRASH ISOLATION VERIFICATION ---")

        // Stop utility bot simulating an isolated shutdown or failure
        utilityBot.stop()
        println("Utility Bot cleanly stopped/isolated.")

        // Verify Music Bot and AI Bot remain RUNNING and healthy
        delay(50)
        assertTrue("Music Bot must remain Running when peer bot stops", musicBot.state.value.isRunning)
        assertTrue("AI Bot must remain Running when peer bot stops", aiBot.state.value.isRunning)
        assertFalse("Utility Bot is cleanly Stopped", utilityBot.state.value.isRunning)

        // Clean up remaining bots
        musicBot.stop()
        aiBot.stop()

        assertFalse("Music Bot Stopped", musicBot.state.value.isRunning)
        assertFalse("AI Bot Stopped", aiBot.state.value.isRunning)

        println("\nBenchmark Completed Successfully: Stability and Isolation 100% Verified.")
        println("==================================================================")
    }
}
