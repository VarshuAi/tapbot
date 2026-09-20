package com.tapbot.feature.catalog

import com.tapbot.core.model.BotInstance
import com.tapbot.core.model.BotMetadata
import com.tapbot.core.model.BotUpdateProgress
import com.tapbot.core.model.BotVersion

sealed interface CatalogUiState {
    object Loading : CatalogUiState
    data class Success(
        val bots: List<BotMetadata>,
        val featuredBots: List<BotMetadata> = emptyList(),
        val categories: List<String> = listOf("All"),
        val selectedCategory: String = "All",
        val searchQuery: String = "",
        val isRefreshing: Boolean = false,
        val isOffline: Boolean = false,
        val selectedTab: Int = 0, // 0: Store, 1: My Bots
        val installedInstances: List<BotInstance> = emptyList(),
        val availableUpdates: Map<String, BotVersion> = emptyMap(),
        val updateProgress: Map<String, BotUpdateProgress> = emptyMap(),
        val isCheckingUpdates: Boolean = false
    ) : CatalogUiState
    data class Error(val message: String) : CatalogUiState
}
