package com.tapbot.feature.catalog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tapbot.core.model.BotInstance
import com.tapbot.core.model.BotInstanceStatus
import com.tapbot.core.model.BotUpdateProgress
import com.tapbot.core.model.BotVersion
import com.tapbot.core.model.UpdateState

/**
 * "MY BOTS" Screen displaying real-time independent state for all locally installed bots.
 * Real state indicators:
 * - 🟢 Running (with polls, messages, uptime)
 * - ⚪ Stopped
 * - 🔴 Crashed (with reason & quick restart)
 */
@Composable
fun MyBotsContent(
    instances: List<BotInstance>,
    availableUpdates: Map<String, BotVersion> = emptyMap(),
    updateProgress: Map<String, BotUpdateProgress> = emptyMap(),
    onStartBot: (String) -> Unit,
    onStopBot: (String) -> Unit,
    onRestartBot: (String) -> Unit,
    onConfigureBot: (String) -> Unit,
    onUninstallBot: (String) -> Unit,
    onUpdateBot: (String, BotVersion) -> Unit = { _, _ -> },
    onCheckForUpdates: () -> Unit = {},
    isCheckingUpdates: Boolean = false,
    onBrowseStoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (instances.isEmpty()) {
        EmptyMyBotsView(onBrowseStoreClick = onBrowseStoreClick, modifier = modifier)
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Installed Bots",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        val runningCount = instances.count { it.status.isRunning }
                        Text(
                            text = "$runningCount of ${instances.size} running locally",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = onCheckForUpdates,
                        enabled = !isCheckingUpdates,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        if (isCheckingUpdates) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Checking...", style = MaterialTheme.typography.labelSmall)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = "Check for updates", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Check Updates", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            items(instances, key = { it.installationId }) { instance ->
                BotInstanceCard(
                    instance = instance,
                    updateVersion = availableUpdates[instance.installationId],
                    updateProgress = updateProgress[instance.installationId],
                    onStart = { onStartBot(instance.installationId) },
                    onStop = { onStopBot(instance.installationId) },
                    onRestart = { onRestartBot(instance.installationId) },
                    onConfigure = { onConfigureBot(instance.botId) },
                    onUninstall = { onUninstallBot(instance.installationId) },
                    onUpdate = { version -> onUpdateBot(instance.installationId, version) }
                )
            }
        }
    }
}

@Composable
fun BotInstanceCard(
    instance: BotInstance,
    updateVersion: BotVersion? = null,
    updateProgress: BotUpdateProgress? = null,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onConfigure: () -> Unit,
    onUninstall: () -> Unit,
    onUpdate: (BotVersion) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isRunning = instance.status.isRunning
    val isCrashed = instance.status.isCrashed

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isRunning -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                isCrashed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Avatar, Name, Version, Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isRunning -> Color(0xFF2E7D32).copy(alpha = 0.2f)
                                isCrashed -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = when {
                            isRunning -> Color(0xFF2E7D32)
                            isCrashed -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = instance.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "v${instance.version} • ${instance.runtimeType}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Real State Badge: 🟢 Running / ⚪ Stopped / 🔴 Crashed
                StatusIndicatorBadge(status = instance.status)
            }

            // Real State Diagnostic Detail
            Spacer(modifier = Modifier.height(10.dp))
            StatusDiagnosticDetail(status = instance.status, startedAt = instance.startedAt)

            // Version Update Banner / Active Progress Tracker
            if (updateProgress != null && updateProgress.state != UpdateState.IDLE) {
                Spacer(modifier = Modifier.height(10.dp))
                UpdateProgressBanner(
                    updateProgress = updateProgress,
                    onRetry = { updateVersion?.let { onUpdate(it) } }
                )
            } else if (updateVersion != null) {
                Spacer(modifier = Modifier.height(10.dp))
                UpdateAvailableBanner(
                    updateVersion = updateVersion,
                    onUpdate = { onUpdate(updateVersion) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Independent Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isRunning) {
                        Button(
                            onClick = onStop,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("STOP")
                        }
                    } else {
                        Button(
                            onClick = onStart,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCrashed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isCrashed) "RETRY" else "START")
                        }
                    }

                    OutlinedButton(
                        onClick = onRestart,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Restart", modifier = Modifier.size(16.dp))
                    }
                }

                Row {
                    IconButton(onClick = onConfigure) {
                        Icon(Icons.Default.Key, contentDescription = "Credentials & Config", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    IconButton(onClick = onUninstall) {
                        Icon(Icons.Default.Delete, contentDescription = "Uninstall", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIndicatorBadge(status: BotInstanceStatus) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = when (status) {
            is BotInstanceStatus.Running -> Color(0xFFE8F5E9)
            is BotInstanceStatus.Crashed -> MaterialTheme.colorScheme.errorContainer
            is BotInstanceStatus.Starting -> Color(0xFFFFF8E1)
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (status) {
                    is BotInstanceStatus.Running -> "🟢 Running"
                    is BotInstanceStatus.Crashed -> "🔴 Crashed"
                    is BotInstanceStatus.Starting -> "🟡 Starting"
                    is BotInstanceStatus.Stopping -> "⚪ Stopping"
                    else -> "⚪ Stopped"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = when (status) {
                    is BotInstanceStatus.Running -> Color(0xFF2E7D32)
                    is BotInstanceStatus.Crashed -> MaterialTheme.colorScheme.error
                    is BotInstanceStatus.Starting -> Color(0xFFF57F17)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun StatusDiagnosticDetail(status: BotInstanceStatus, startedAt: Long?) {
    when (status) {
        is BotInstanceStatus.Running -> {
            val uptimeSeconds = if (startedAt != null) (System.currentTimeMillis() - startedAt) / 1000 else 0
            Text(
                text = "⚡ Polling active • ${status.pollCount} polls • ${status.messageCount} msgs • Uptime: ${uptimeSeconds}s",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF2E7D32)
            )
        }
        is BotInstanceStatus.Crashed -> {
            Text(
                text = "⚠️ Crash Reason: ${status.reason} (Count: ${status.crashCount})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold
            )
        }
        is BotInstanceStatus.Starting -> {
            Text(
                text = "Connecting to Telegram Bot API...",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFF57F17)
            )
        }
        else -> {
            Text(
                text = "Process idle. Ready to launch.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyMyBotsView(onBrowseStoreClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Bots Installed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "You haven't installed any bots yet. Browse the Store to install Music Bot, AI Bot, or Utility Bot.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onBrowseStoreClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Browse Bot Store")
            }
        }
    }
}

@Composable
private fun UpdateAvailableBanner(
    updateVersion: BotVersion,
    onUpdate: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Update available — v${updateVersion.version}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val notes = updateVersion.releaseNotes
                if (!notes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onUpdate,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("Update", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun UpdateProgressBanner(
    updateProgress: BotUpdateProgress,
    onRetry: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (updateProgress.state) {
                UpdateState.SUCCESS -> Color(0xFFE8F5E9)
                UpdateState.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                UpdateState.ROLLING_BACK -> Color(0xFFFFF3E0)
                else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (updateProgress.state) {
                        UpdateState.CHECKING -> "🔍 Checking Compatibility"
                        UpdateState.DOWNLOADING -> "📥 Downloading v${updateProgress.targetVersion}"
                        UpdateState.VERIFYING -> "🛡️ Verifying SHA-256 Checksum"
                        UpdateState.INSTALLING -> "📦 Staging & Replacing Package"
                        UpdateState.STARTING -> "🚀 Starting v${updateProgress.targetVersion} & Health Check"
                        UpdateState.SUCCESS -> "✅ Successfully Updated to v${updateProgress.targetVersion}"
                        UpdateState.ROLLING_BACK -> "⚠️ Startup Failed — Rolling Back to v${updateProgress.currentVersion}"
                        UpdateState.FAILED -> "❌ Update Failed"
                        UpdateState.IDLE -> ""
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = when (updateProgress.state) {
                        UpdateState.SUCCESS -> Color(0xFF2E7D32)
                        UpdateState.FAILED -> MaterialTheme.colorScheme.error
                        UpdateState.ROLLING_BACK -> Color(0xFFE65100)
                        else -> MaterialTheme.colorScheme.primary
                    }
                )

                if (updateProgress.state == UpdateState.DOWNLOADING) {
                    Text(
                        text = "${(updateProgress.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (updateProgress.isInProgress) {
                Spacer(modifier = Modifier.height(8.dp))
                if (updateProgress.state == UpdateState.DOWNLOADING) {
                    LinearProgressIndicator(
                        progress = { updateProgress.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }

            updateProgress.message?.let { msg ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            updateProgress.error?.let { err ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Retry Update", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

