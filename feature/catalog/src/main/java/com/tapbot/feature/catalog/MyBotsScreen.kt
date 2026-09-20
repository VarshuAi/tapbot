package com.tapbot.feature.catalog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import com.tapbot.core.model.BotInstance
import com.tapbot.core.model.BotInstanceStatus

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
    onStartBot: (String) -> Unit,
    onStopBot: (String) -> Unit,
    onRestartBot: (String) -> Unit,
    onConfigureBot: (String) -> Unit,
    onUninstallBot: (String) -> Unit,
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
                }
            }

            items(instances, key = { it.installationId }) { instance ->
                BotInstanceCard(
                    instance = instance,
                    onStart = { onStartBot(instance.installationId) },
                    onStop = { onStopBot(instance.installationId) },
                    onRestart = { onRestartBot(instance.installationId) },
                    onConfigure = { onConfigureBot(instance.botId) },
                    onUninstall = { onUninstallBot(instance.installationId) }
                )
            }
        }
    }
}

@Composable
fun BotInstanceCard(
    instance: BotInstance,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onConfigure: () -> Unit,
    onUninstall: () -> Unit,
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
