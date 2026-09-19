package com.tapbot.navigation

sealed class Screen(val route: String) {
    object Catalog : Screen("catalog")
    object BotDetail : Screen("bot_detail/{botId}") {
        fun createRoute(botId: String) = "bot_detail/$botId"
    }
    object Poc : Screen("poc")
}
