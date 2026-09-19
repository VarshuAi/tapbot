package com.tapbot.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tapbot.core.model.BotMetadata
import com.tapbot.core.network.CatalogApi
import com.tapbot.core.network.CatalogRepository
import com.tapbot.core.network.OfflineFirstCatalogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CatalogViewModel(
    private val catalogRepository: CatalogRepository
) : ViewModel() {

    // Secondary constructor accepting CatalogApi for backward compatibility
    constructor(catalogApi: CatalogApi) : this(
        OfflineFirstCatalogRepository(
            remoteSource = object : com.tapbot.core.network.RemoteBotDataSource {
                override suspend fun getPublishedBots(category: String?, search: String?) = catalogApi.getBots()
                override suspend fun getBotDetails(botId: String) = catalogApi.getBotDetails(botId)
                override suspend fun getCategories() = Result.success(listOf("All", "Utilities", "Media", "Productivity"))
            }
        )
    )

    private val _uiState = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    private var allBots: List<BotMetadata> = emptyList()
    private var allCategories: List<String> = listOf("All")
    private var allFeaturedBots: List<BotMetadata> = emptyList()

    init {
        loadCatalog(forceRefresh = false)
    }

    fun loadCatalog(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val current = _uiState.value
            if (current is CatalogUiState.Success && forceRefresh) {
                _uiState.value = current.copy(isRefreshing = true)
            } else if (current !is CatalogUiState.Success) {
                _uiState.value = CatalogUiState.Loading
            }

            catalogRepository.getBots(forceRefresh = forceRefresh)
                .onSuccess { bots ->
                    allBots = bots
                    allFeaturedBots = catalogRepository.getFeaturedBots().getOrDefault(bots.take(2))
                    allCategories = catalogRepository.getCategories().getOrDefault(listOf("All"))

                    val selectedCat = (current as? CatalogUiState.Success)?.selectedCategory ?: "All"
                    val query = (current as? CatalogUiState.Success)?.searchQuery ?: ""
                    val filtered = filterBots(selectedCat, query)

                    _uiState.value = CatalogUiState.Success(
                        bots = filtered,
                        featuredBots = allFeaturedBots,
                        categories = allCategories,
                        selectedCategory = selectedCat,
                        searchQuery = query,
                        isRefreshing = false
                    )
                }
                .onFailure { err ->
                    if (current is CatalogUiState.Success) {
                        _uiState.value = current.copy(isRefreshing = false)
                    } else {
                        _uiState.value = CatalogUiState.Error(err.message ?: "Failed to load catalog")
                    }
                }
        }
    }

    fun refresh() {
        loadCatalog(forceRefresh = true)
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
                    bot.description.contains(query, ignoreCase = true) ||
                    bot.tags.any { it.contains(query, ignoreCase = true) }
            matchesCategory && matchesQuery
        }
    }

    companion object {
        fun provideFactory(catalogRepository: CatalogRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CatalogViewModel(catalogRepository) as T
                }
            }

        fun provideFactory(catalogApi: CatalogApi): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CatalogViewModel(catalogApi) as T
                }
            }
    }
}
