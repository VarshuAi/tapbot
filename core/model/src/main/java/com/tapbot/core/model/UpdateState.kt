package com.tapbot.core.model

import kotlinx.serialization.Serializable

/**
 * State machine stages for the 9-Step Atomic Bot Update & Rollback Engine.
 */
@Serializable
enum class UpdateState {
    IDLE,
    CHECKING,
    DOWNLOADING,
    VERIFYING,
    INSTALLING,
    STARTING,
    SUCCESS,
    FAILED,
    ROLLING_BACK
}

/**
 * Real-time progress container for an ongoing bot update or rollback.
 */
@Serializable
data class BotUpdateProgress(
    val state: UpdateState = UpdateState.IDLE,
    val progress: Float = 0f,
    val currentVersion: String = "",
    val targetVersion: String = "",
    val message: String? = null,
    val error: String? = null
) {
    val isInProgress: Boolean
        get() = state in listOf(
            UpdateState.CHECKING,
            UpdateState.DOWNLOADING,
            UpdateState.VERIFYING,
            UpdateState.INSTALLING,
            UpdateState.STARTING,
            UpdateState.ROLLING_BACK
        )

    val isTerminal: Boolean
        get() = state == UpdateState.SUCCESS || state == UpdateState.FAILED
}
