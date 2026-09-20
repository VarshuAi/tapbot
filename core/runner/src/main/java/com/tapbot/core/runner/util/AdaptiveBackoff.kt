package com.tapbot.core.runner.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Adaptive exponential backoff controller for long-polling loops.
 *
 * Prevents busy-loops, excessive radio wakeups, and battery drain
 * by escalating backoff delays on consecutive network/API failures:
 * 2s -> 4s -> 8s -> 16s -> 32s -> 60s (capped at maxDelayMs).
 *
 * Automatically resets backoff to 0ms upon first successful API response,
 * and enables instantaneous resumption when network connectivity is restored.
 */
class AdaptiveBackoff(
    val initialDelayMs: Long = 2000L,
    val maxDelayMs: Long = 60000L,
    val multiplier: Double = 2.0
) {
    var consecutiveFailures: Int = 0
        private set

    var currentDelayMs: Long = 0L
        private set

    /**
     * Resets backoff state upon a successful API response or manual refresh.
     */
    fun recordSuccess() {
        consecutiveFailures = 0
        currentDelayMs = 0L
    }

    /**
     * Registers a failure and computes the next exponential backoff delay.
     */
    fun recordFailure(): Long {
        consecutiveFailures++
        currentDelayMs = if (consecutiveFailures == 1) {
            initialDelayMs
        } else {
            (currentDelayMs * multiplier).toLong().coerceAtMost(maxDelayMs)
        }
        return currentDelayMs
    }

    /**
     * Suspends for the current backoff delay.
     * If a [networkState] flow is supplied and network is offline, suspends
     * until network reconnects or timeout occurs.
     */
    suspend fun delayWithBackoff(networkState: StateFlow<Boolean>? = null) {
        val delayTime = if (currentDelayMs > 0) currentDelayMs else initialDelayMs
        if (networkState != null && !networkState.value) {
            // Suspended due to network loss; wake up immediately when network returns (max wait: maxDelayMs)
            withTimeoutOrNull(maxDelayMs) {
                networkState.filter { it }.first()
            }
        } else {
            delay(delayTime)
        }
    }

    fun reset() {
        consecutiveFailures = 0
        currentDelayMs = 0L
    }
}
