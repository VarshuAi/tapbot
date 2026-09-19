package com.tapbot.feature.catalog

import com.tapbot.core.model.BotMetadata

sealed interface CatalogUiState {
    object Loading : CatalogUiState
    data class Success(
        val bots: List<BotMetadata>,
        val featuredBots: List<BotMetadata> = emptyList(),
        val categories: List<String> = listOf("All"),
        val selectedCategory: String = "All",
        val searchQuery: String = "",
        val isRefreshing: Boolean = false,
        val isOffline: Boolean = false
    ) : CatalogUiState
    data class Error(val message: String) : CatalogUiState
}
