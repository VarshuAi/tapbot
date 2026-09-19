package com.tapbot.feature.botdetail

import com.tapbot.core.model.BotLogEntry
import com.tapbot.core.model.BotMetadata
import com.tapbot.core.model.BotRunState

sealed interface BotDetailUiState {
    object Loading : BotDetailUiState
    data class Success(
        val bot: BotMetadata,
        val credentials: Map<String, String> = emptyMap(),
        val runState: BotRunState = BotRunState.Stopped,
        val logs: List<BotLogEntry> = emptyList(),
        val isPackageDownloaded: Boolean = true,
        val isSavingCredentials: Boolean = false,
        val credentialSaveSuccess: Boolean = false
    ) : BotDetailUiState
    data class Error(val message: String) : BotDetailUiState
}
