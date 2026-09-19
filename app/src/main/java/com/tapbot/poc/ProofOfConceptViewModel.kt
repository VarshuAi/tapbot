package com.tapbot.poc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tapbot.core.logging.BotLogRepository
import com.tapbot.core.model.BotLogEntry
import com.tapbot.core.model.TelegramUser
import com.tapbot.core.runner.manager.BotInstanceManager
import com.tapbot.core.runner.runtime.BotRuntimeState
import com.tapbot.core.security.CredentialStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PocUiState(
    val enteredToken: String = "",
    val isTokenSaved: Boolean = false,
    val isValidatingToken: Boolean = false,
    val validatedBotUser: TelegramUser? = null,
    val validationError: String? = null,
    val runtimeState: BotRuntimeState = BotRuntimeState.Stopped,
    val logs: List<BotLogEntry> = emptyList()
)

class ProofOfConceptViewModel(
    private val credentialStore: CredentialStore,
    private val botInstanceManager: BotInstanceManager,
    private val logRepository: BotLogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PocUiState())
    val uiState: StateFlow<PocUiState> = _uiState.asStateFlow()

    init {
        loadSavedToken()
        observeRuntimeState()
        observeLogs()
    }

    private fun loadSavedToken() {
        viewModelScope.launch {
            val savedToken = credentialStore.getToken()
            if (!savedToken.isNullOrBlank()) {
                _uiState.update { it.copy(enteredToken = savedToken, isTokenSaved = true) }
                validateTokenInternal(savedToken)
            }
        }
    }

    private fun observeRuntimeState() {
        viewModelScope.launch {
            botInstanceManager.activeState.collect { state ->
                _uiState.update { it.copy(runtimeState = state) }
            }
        }
    }

    private fun observeLogs() {
        viewModelScope.launch {
            logRepository.getLogStream("bot_pingpong_poc").collect { logs ->
                _uiState.update { it.copy(logs = logs) }
            }
        }
    }

    fun onTokenChanged(newToken: String) {
        _uiState.update {
            it.copy(
                enteredToken = newToken,
                isTokenSaved = false,
                validationError = null
            )
        }
    }

    fun saveToken() {
        viewModelScope.launch {
            val token = _uiState.value.enteredToken.trim()
            if (token.isNotBlank()) {
                credentialStore.saveToken(token)
                _uiState.update { it.copy(isTokenSaved = true) }
                validateTokenInternal(token)
            }
        }
    }

    fun validateToken() {
        val token = _uiState.value.enteredToken.trim()
        validateTokenInternal(token)
    }

    private fun validateTokenInternal(token: String) {
        if (token.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isValidatingToken = true, validationError = null) }
            val result = botInstanceManager.validateToken(token)
            result.onSuccess { user ->
                _uiState.update {
                    it.copy(
                        isValidatingToken = false,
                        validatedBotUser = user,
                        validationError = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isValidatingToken = false,
                        validatedBotUser = null,
                        validationError = err.message ?: "Validation failed"
                    )
                }
            }
        }
    }

    fun startBot() {
        viewModelScope.launch {
            botInstanceManager.start()
        }
    }

    fun stopBot() {
        viewModelScope.launch {
            botInstanceManager.stop()
        }
    }

    fun restartBot() {
        viewModelScope.launch {
            botInstanceManager.restart()
        }
    }

    fun clearLogs() {
        logRepository.clearLogs("bot_pingpong_poc")
    }

    companion object {
        fun provideFactory(
            credentialStore: CredentialStore,
            botInstanceManager: BotInstanceManager,
            logRepository: BotLogRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ProofOfConceptViewModel(credentialStore, botInstanceManager, logRepository) as T
            }
        }
    }
}
