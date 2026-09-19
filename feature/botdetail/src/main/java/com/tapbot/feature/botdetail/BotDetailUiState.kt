package com.tapbot.feature.botdetail

import com.tapbot.core.model.BotLogEntry
import com.tapbot.core.model.BotMetadata
import com.tapbot.core.model.BotRunState

sealed interface BotDetailUiState {
    object Loading : BotDetailUiState
    data class Success(
        val bot: BotMetadata,
        val isInstalled: Boolean = false,
        val isInstalling: Boolean = false,
        val installProgress: Float = 0.0f,
        val installError: String? = null,
        val installedVersion: String? = null,
        val credentials: Map<String, String> = emptyMap(),
        val runState: BotRunState = BotRunState.Stopped,
        val logs: List<BotLogEntry> = emptyList(),
        val isSavingCredentials: Boolean = false,
        val credentialSaveSuccess: Boolean = false
    ) : BotDetailUiState
    data class Error(val message: String) : BotDetailUiState
}
