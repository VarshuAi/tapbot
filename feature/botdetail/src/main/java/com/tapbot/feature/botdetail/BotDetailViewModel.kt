package com.tapbot.feature.botdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tapbot.core.logging.BotLogRepository
import com.tapbot.core.network.CatalogApi
import com.tapbot.core.runner.BotServiceController
import com.tapbot.core.security.SecureCredentialStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BotDetailViewModel(
    private val botId: String,
    private val catalogApi: CatalogApi,
    private val credentialStore: SecureCredentialStore,
    private val botServiceController: BotServiceController,
    private val logRepository: BotLogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<BotDetailUiState>(BotDetailUiState.Loading)
    val uiState: StateFlow<BotDetailUiState> = _uiState.asStateFlow()

    init {
        loadBotData()
        observeRunState()
        observeLogs()
    }

    private fun loadBotData() {
        viewModelScope.launch {
            _uiState.value = BotDetailUiState.Loading
            catalogApi.getBotDetails(botId)
                .onSuccess { bot ->
                    val savedCredentials = credentialStore.getAllCredentials(botId)
                    _uiState.value = BotDetailUiState.Success(
                        bot = bot,
                        credentials = savedCredentials,
                        runState = botServiceController.getBotRunState(botId).value,
                        logs = logRepository.getLogStream(botId).value
                    )
                }
                .onFailure { err ->
                    _uiState.value = BotDetailUiState.Error(err.message ?: "Failed to load bot details")
                }
        }
    }

    private fun observeRunState() {
        viewModelScope.launch {
            botServiceController.getBotRunState(botId).collect { state ->
                _uiState.update { current ->
                    if (current is BotDetailUiState.Success) {
                        current.copy(runState = state)
                    } else current
                }
            }
        }
    }

    private fun observeLogs() {
        viewModelScope.launch {
            logRepository.getLogStream(botId).collect { logs ->
                _uiState.update { current ->
                    if (current is BotDetailUiState.Success) {
                        current.copy(logs = logs)
                    } else current
                }
            }
        }
    }

    fun updateCredential(key: String, value: String) {
        val current = _uiState.value as? BotDetailUiState.Success ?: return
        val updated = current.credentials.toMutableMap().apply { put(key, value) }
        _uiState.value = current.copy(credentials = updated, credentialSaveSuccess = false)
    }

    fun saveCredentials() {
        val current = _uiState.value as? BotDetailUiState.Success ?: return
        viewModelScope.launch {
            _uiState.value = current.copy(isSavingCredentials = true)
            for ((key, value) in current.credentials) {
                if (value.isNotBlank()) {
                    credentialStore.saveCredential(botId, key, value)
                }
            }
            _uiState.value = current.copy(isSavingCredentials = false, credentialSaveSuccess = true)
        }
    }

    fun startBot() {
        saveCredentials()
        botServiceController.startBot(botId)
    }

    fun stopBot() {
        botServiceController.stopBot(botId)
    }

    fun clearLogs() {
        logRepository.clearLogs(botId)
    }

    companion object {
        fun provideFactory(
            botId: String,
            catalogApi: CatalogApi,
            credentialStore: SecureCredentialStore,
            botServiceController: BotServiceController,
            logRepository: BotLogRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BotDetailViewModel(
                    botId,
                    catalogApi,
                    credentialStore,
                    botServiceController,
                    logRepository
                ) as T
            }
        }
    }
}
