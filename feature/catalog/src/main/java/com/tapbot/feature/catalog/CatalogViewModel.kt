package com.tapbot.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tapbot.core.model.BotInstance
import com.tapbot.core.model.BotMetadata
import com.tapbot.core.model.BotUpdateProgress
import com.tapbot.core.model.BotVersion
import com.tapbot.core.network.BotPackageDownloader
import com.tapbot.core.network.CatalogApi
import com.tapbot.core.network.CatalogRepository
import com.tapbot.core.network.ManifestValidator
import com.tapbot.core.network.OfflineFirstCatalogRepository
import com.tapbot.core.runner.manager.BotInstanceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CatalogViewModel(
    private val catalogRepository: CatalogRepository,
    private val botInstanceManager: BotInstanceManager? = null,
    private val packageDownloader: BotPackageDownloader? = null,
    private val manifestValidator: ManifestValidator? = null
) : ViewModel() {

    // Secondary constructor accepting CatalogApi for backward compatibility
    constructor(catalogApi: CatalogApi) : this(
        catalogRepository = OfflineFirstCatalogRepository(
            remoteSource = object : com.tapbot.core.network.RemoteBotDataSource {
                override suspend fun getPublishedBots(category: String?, search: String?) = catalogApi.getBots()
                override suspend fun getBotDetails(botId: String) = catalogApi.getBotDetails(botId)
                override suspend fun getCategories() = Result.success(listOf("All", "Utilities", "Media", "Productivity"))
                override suspend fun getBotVersions(botId: String) = catalogApi.getBotVersions(botId)
                override suspend fun getLatestVersion(botId: String) = catalogApi.getLatestVersion(botId)
            }
        ),
        botInstanceManager = null,
        packageDownloader = null,
        manifestValidator = null
    )

    private val _uiState = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    private var allBots: List<BotMetadata> = emptyList()
    private var allCategories: List<String> = listOf("All")
    private var allFeaturedBots: List<BotMetadata> = emptyList()

    init {
        loadCatalog(forceRefresh = false)
        observeInstalledBots()
        observeUpdateProgress()
    }

    private fun observeInstalledBots() {
        val manager = botInstanceManager ?: return
        viewModelScope.launch {
            manager.instances.collect { list ->
                _uiState.update { current ->
                    if (current is CatalogUiState.Success) {
                        current.copy(installedInstances = list)
                    } else current
                }
                checkForUpdates()
            }
        }
    }

    private fun observeUpdateProgress() {
        val manager = botInstanceManager ?: return
        viewModelScope.launch {
            manager.updateProgress.collect { progressMap ->
                _uiState.update { current ->
                    if (current is CatalogUiState.Success) {
                        current.copy(updateProgress = progressMap)
                    } else current
                }
            }
        }
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
                    val selectedTab = (current as? CatalogUiState.Success)?.selectedTab ?: 0
                    val filtered = filterBots(selectedCat, query)

                    val installed = botInstanceManager?.instances?.value ?: emptyList()
                    val existingUpdates = (current as? CatalogUiState.Success)?.availableUpdates ?: emptyMap()
                    val activeProgress = botInstanceManager?.updateProgress?.value ?: emptyMap()

                    _uiState.value = CatalogUiState.Success(
                        bots = filtered,
                        featuredBots = allFeaturedBots,
                        categories = allCategories,
                        selectedCategory = selectedCat,
                        searchQuery = query,
                        isRefreshing = false,
                        isOffline = false,
                        selectedTab = selectedTab,
                        installedInstances = installed,
                        availableUpdates = existingUpdates,
                        updateProgress = activeProgress
                    )
                    checkForUpdates()
                }
                .onFailure { error ->
                    if (current is CatalogUiState.Success) {
                        _uiState.value = current.copy(isRefreshing = false, isOffline = true)
                    } else {
                        _uiState.value = CatalogUiState.Error(
                            error.message ?: "Failed to load catalog. Please check your network connection."
                        )
                    }
                }
        }
    }

    fun checkForUpdates() {
        val manager = botInstanceManager ?: return
        val current = _uiState.value as? CatalogUiState.Success ?: return
        val instances = current.installedInstances.ifEmpty { manager.instances.value }
        if (instances.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { state ->
                if (state is CatalogUiState.Success) state.copy(isCheckingUpdates = true) else state
            }

            val updates = mutableMapOf<String, BotVersion>()
            for (instance in instances) {
                catalogRepository.checkForUpdate(instance.botId, instance.version)
                    .onSuccess { updateVersion ->
                        if (updateVersion != null) {
                            updates[instance.installationId] = updateVersion
                        }
                    }
            }

            _uiState.update { state ->
                if (state is CatalogUiState.Success) {
                    state.copy(
                        availableUpdates = updates,
                        isCheckingUpdates = false
                    )
                } else state
            }
        }
    }

    fun updateBot(installationId: String, targetVersion: BotVersion) {
        val manager = botInstanceManager ?: return
        viewModelScope.launch {
            manager.updateWithRollback(
                installationId = installationId,
                targetVersion = targetVersion,
                packageDownloader = packageDownloader,
                manifestValidator = manifestValidator
            )
            checkForUpdates()
        }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { current ->
            if (current is CatalogUiState.Success) {
                current.copy(selectedTab = tabIndex)
            } else current
        }
    }

    fun startBot(installationId: String) {
        viewModelScope.launch {
            botInstanceManager?.start(installationId)
        }
    }

    fun stopBot(installationId: String) {
        viewModelScope.launch {
            botInstanceManager?.stop(installationId)
        }
    }

    fun restartBot(installationId: String) {
        viewModelScope.launch {
            botInstanceManager?.restart(installationId)
        }
    }

    fun uninstallBot(installationId: String) {
        viewModelScope.launch {
            botInstanceManager?.uninstall(installationId)
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
        fun provideFactory(
            catalogRepository: CatalogRepository,
            botInstanceManager: BotInstanceManager? = null,
            packageDownloader: BotPackageDownloader? = null,
            manifestValidator: ManifestValidator? = null
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CatalogViewModel(
                        catalogRepository = catalogRepository,
                        botInstanceManager = botInstanceManager,
                        packageDownloader = packageDownloader,
                        manifestValidator = manifestValidator
                    ) as T
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
