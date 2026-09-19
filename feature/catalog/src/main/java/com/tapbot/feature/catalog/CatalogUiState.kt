package com.tapbot.feature.catalog

import com.tapbot.core.model.BotMetadata

sealed interface CatalogUiState {
    object Loading : CatalogUiState
    data class Success(
        val bots: List<BotMetadata>,
        val selectedCategory: String = "All",
        val searchQuery: String = ""
    ) : CatalogUiState
    data class Error(val message: String) : CatalogUiState
}
