package com.tapbot.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tapbot.core.model.BotMetadata
import com.tapbot.core.network.CatalogApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CatalogViewModel(
    private val catalogApi: CatalogApi
) : ViewModel() {

    private val _uiState = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    private var allBots: List<BotMetadata> = emptyList()

    init {
        loadCatalog()
    }

    fun loadCatalog() {
        viewModelScope.launch {
            _uiState.value = CatalogUiState.Loading
            catalogApi.getBots()
                .onSuccess { bots ->
                    allBots = bots
                    _uiState.value = CatalogUiState.Success(bots = bots)
                }
                .onFailure { err ->
                    _uiState.value = CatalogUiState.Error(err.message ?: "Failed to load catalog")
                }
        }
    }

    fun selectCategory(category: String) {
        val current = _uiState.value as? CatalogUiState.Success ?: return
        val filtered = filterBots(category, current.searchQuery)
        _uiState.update {
            current.copy(selectedCategory = category, bots = filtered)
        }
    }

    fun updateSearchQuery(query: String) {
        val current = _uiState.value as? CatalogUiState.Success ?: return
        val filtered = filterBots(current.selectedCategory, query)
        _uiState.update {
            current.copy(searchQuery = query, bots = filtered)
        }
    }

    private fun filterBots(category: String, query: String): List<BotMetadata> {
        return allBots.filter { bot ->
            val matchesCategory = (category == "All" || bot.category.equals(category, ignoreCase = true))
            val matchesQuery = query.isBlank() ||
                    bot.name.contains(query, ignoreCase = true) ||
                    bot.summary.contains(query, ignoreCase = true) ||
                    bot.tags.any { it.contains(query, ignoreCase = true) }
            matchesCategory && matchesQuery
        }
    }

    companion object {
        fun provideFactory(catalogApi: CatalogApi): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CatalogViewModel(catalogApi) as T
                }
            }
    }
}
