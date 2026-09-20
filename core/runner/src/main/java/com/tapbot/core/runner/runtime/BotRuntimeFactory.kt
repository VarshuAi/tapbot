package com.tapbot.core.runner.runtime

/**
 * Factory for instantiating bot runtimes based on bot ID and runtime metadata.
 */
object BotRuntimeFactory {

    fun createRuntime(botId: String): BotRuntime {
        val normalized = botId.lowercase().replace("-", "_")
        return when {
            normalized.contains("music") -> MusicBotRuntime(botId)
            normalized.contains("ai") || normalized.contains("gemini") -> AIBotRuntime(botId)
            normalized.contains("util") || normalized.contains("diagnostic") -> UtilityBotRuntime(botId)
            normalized.contains("echo") -> UtilityBotRuntime(botId)
            else -> PingPongBotRuntime(botId)
        }
    }
}
