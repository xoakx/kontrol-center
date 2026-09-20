package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.api.AgentStatusItem
import com.example.data.repository.FleetRepository
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FrostedGlassBorder
import com.example.ui.theme.RoseError
import com.example.viewmodel.FleetViewModel

@Composable
fun FleetDaemonsScreen(
    viewModel: FleetViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("fleet_screen")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Summary Card
            item {
                Spacer(modifier = Modifier.height(8.dp))
                FleetSummaryHeader(
                    activeCount = uiState.activeCount,
                    totalCount = uiState.totalCount,
                    failedCount = uiState.failedCount,
                    overallHealth = uiState.overallHealth,
                    isLoading = uiState.isLoading,
                    onRefresh = { viewModel.refresh() }
                )
            }

            // User Notification or Error Banner
            if (uiState.errorMessage != null || uiState.userNotice != null) {
                item {
                    val isError = uiState.errorMessage != null
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isError) RoseError.copy(alpha = 0.15f) else EmeraldSuccess.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isError) RoseError.copy(alpha = 0.4f) else EmeraldSuccess.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isError) RoseError else EmeraldSuccess,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.errorMessage ?: uiState.userNotice ?: "",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 12 Supervised Fleet Daemon Cards
            items(uiState.daemons, key = { it.id }) { daemon ->
                FleetDaemonItemCard(
                    daemon = daemon,
                    isActionPending = uiState.actionInProgress == daemon.service,
                    onRestart = { viewModel.restartDaemon(daemon.service) },
                    onStop = { viewModel.stopDaemon(daemon.service) },
                    onStart = { viewModel.startDaemon(daemon.service) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun FleetSummaryHeader(
    activeCount: Int,
    totalCount: Int,
    failedCount: Int,
    overallHealth: String,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FrostedGlassBorder, RoundedCornerShape(18.dp))
            .testTag("fleet_summary_header")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Fleet Daemons",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "12 Supervised Background Agents (CPUAffinity 8-19)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading,
                    modifier = Modifier.testTag("refresh_fleet_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = CyanPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Fleet",
                            tint = CyanPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Active count badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = EmeraldSuccess.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "$activeCount / $totalCount Active",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = EmeraldSuccess,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                // Health status badge
                val (badgeBg, badgeBorder, badgeColor) = when (overallHealth) {
                    "OPTIMAL" -> Triple(EmeraldSuccess.copy(alpha = 0.15f), EmeraldSuccess.copy(alpha = 0.4f), EmeraldSuccess)
                    "WARNING" -> Triple(AmberWarning.copy(alpha = 0.15f), AmberWarning.copy(alpha = 0.4f), AmberWarning)
                    else -> Triple(RoseError.copy(alpha = 0.15f), RoseError.copy(alpha = 0.4f), RoseError)
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = badgeBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeBorder)
                ) {
                    Text(
                        text = if (failedCount > 0) "$failedCount FAILED • $overallHealth" else overallHealth,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FleetDaemonItemCard(
    daemon: AgentStatusItem,
    isActionPending: Boolean,
    onRestart: () -> Unit,
    onStop: () -> Unit,
    onStart: () -> Unit
) {
    val isStandby = FleetRepository.isStandbyDaemon(daemon.id)
    val isActive = daemon.status.active
    val state = daemon.status.state ?: if (isActive) "running" else "stopped"
    val isFailed = state.equals("failed", ignoreCase = true)

    val statusColor = when {
        isFailed -> RoseError
        isActive -> EmeraldSuccess
        isStandby -> AmberWarning
        else -> Color.Gray
    }

    val displayStatus = when {
        isFailed -> "FAILED"
        isActive -> "ACTIVE • RUNNING"
        isStandby -> "STANDBY (ON-DEMAND)"
        else -> "STOPPED"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FrostedGlassBorder, RoundedCornerShape(16.dp))
            .testTag("daemon_card_${daemon.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Title & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = daemon.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = daemon.service,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                    modifier = Modifier.testTag("status_badge_${daemon.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = displayStatus,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Text(
                text = daemon.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Telemetry: PID, RAM, Category
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column {
                        Text(
                            text = "PID",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = daemon.status.pid?.toString() ?: "--",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column {
                        Text(
                            text = "RAM",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val mem = daemon.status.memoryMb
                        Text(
                            text = if (mem != null && mem > 0.0) String.format("%.1f MB", mem) else "--",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column {
                        Text(
                            text = "AFFINITY",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "E-Cores (8-19)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = CyanPrimary
                        )
                    }
                }

                // Interactive Control Actions (Restart / Stop)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isActionPending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = CyanPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        // Restart button
                        OutlinedButton(
                            onClick = onRestart,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanPrimary),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("btn_restart_${daemon.id}")
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restart", fontSize = 11.sp)
                        }

                        // Stop / Start toggle button
                        if (isActive) {
                            OutlinedButton(
                                onClick = onStop,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseError),
                                border = androidx.compose.foundation.BorderStroke(1.dp, RoseError.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("btn_stop_${daemon.id}")
                            ) {
                                Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Stop", fontSize = 11.sp)
                            }
                        } else {
                            OutlinedButton(
                                onClick = onStart,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldSuccess),
                                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("btn_start_${daemon.id}")
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Start", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
