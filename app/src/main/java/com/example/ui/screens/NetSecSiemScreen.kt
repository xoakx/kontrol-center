package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.example.data.api.CrowdSecBouncerItem
import com.example.data.api.CrowdSecDecisionItem
import com.example.data.api.FirewallStatusResponse
import com.example.data.api.NetSecOverviewResponse
import com.example.data.api.SuricataAlertItem
import com.example.data.api.TetragonStatusResponse
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FrostedGlassBorder
import com.example.ui.theme.RoseError
import com.example.viewmodel.NetSecUiState
import com.example.viewmodel.NetSecViewModel

@Composable
fun NetSecSiemScreen(
    viewModel: NetSecViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("netsec_screen")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Summary Card
            item {
                Spacer(modifier = Modifier.height(8.dp))
                NetSecSummaryHeader(
                    overview = uiState.overview,
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
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    viewModel.clearNotice()
                                    viewModel.clearError()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // CrowdSec Active Decisions Section (1-Tap Unban)
            item {
                Text(
                    text = "ACTIVE CROWDSEC BANS (${uiState.crowdSecDecisions.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.crowdSecDecisions.isEmpty()) {
                item {
                    CrowdSecCleanStateCard()
                }
            } else {
                items(uiState.crowdSecDecisions, key = { it.id }) { decision ->
                    CrowdSecDecisionCard(
                        decision = decision,
                        isUnbanning = (uiState.unbanInProgressIp ?: uiState.isUnbanningIp) == decision.value,
                        onUnban = { viewModel.unbanIp(decision.value) }
                    )
                }
            }

            // CrowdSec Bouncers Row
            if (uiState.bouncers.isNotEmpty()) {
                item {
                    CrowdSecBouncersStatusCard(bouncers = uiState.bouncers)
                }
            }

            // Severity Filter Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SURICATA 8 INTRUSION ALERTS (${uiState.filteredAlerts.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                SeverityFilterRow(
                    selectedFilter = uiState.selectedSeverityFilter,
                    allCount = uiState.suricataAlerts.size,
                    criticalCount = uiState.suricataAlerts.count { it.alert.severity == 1 },
                    warningCount = uiState.suricataAlerts.count { it.alert.severity == 2 },
                    infoCount = uiState.suricataAlerts.count { it.alert.severity == 3 },
                    onSelectFilter = { viewModel.setSeverityFilter(it) }
                )
            }

            // Suricata Alerts List
            if (uiState.filteredAlerts.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No intrusion alerts matching selected filter.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                itemsIndexed(uiState.filteredAlerts) { index, alert ->
                    SuricataAlertItemCard(
                        alert = alert,
                        index = index,
                        onClick = { viewModel.selectAlert(alert) }
                    )
                }
            }

            // Tetragon eBPF Runtime Posture Card
            if (uiState.tetragonStatus != null) {
                item {
                    TetragonStatusCard(tetragon = uiState.tetragonStatus!!)
                }
            }

            // Nftables Firewall Packet Drops Card
            if (uiState.firewallStatus != null) {
                item {
                    FirewallStatusCard(firewall = uiState.firewallStatus!!)
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Raw Alert Payload Detail Dialog
        if (uiState.selectedAlert != null) {
            SuricataAlertDetailDialog(
                alert = uiState.selectedAlert!!,
                onDismiss = { viewModel.dismissAlertDetails() }
            )
        }
    }
}

@Composable
fun NetSecSummaryHeader(
    overview: NetSecOverviewResponse?,
    overallHealth: String,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    val (badgeBg, badgeBorder, badgeColor, badgeLabel) = when (overallHealth) {
        "CRITICAL" -> Quad(RoseError.copy(alpha = 0.15f), RoseError.copy(alpha = 0.4f), RoseError, "CRITICAL THREAT")
        "WARNING" -> Quad(AmberWarning.copy(alpha = 0.15f), AmberWarning.copy(alpha = 0.4f), AmberWarning, "THREAT ACTIVE")
        else -> Quad(EmeraldSuccess.copy(alpha = 0.15f), EmeraldSuccess.copy(alpha = 0.4f), EmeraldSuccess, "PERIMETER SECURE")
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FrostedGlassBorder, RoundedCornerShape(18.dp))
            .testTag("netsec_summary_header")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Cybersecurity & SIEM",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Suricata 8 IDS • CrowdSec LAPI • Tetragon eBPF • Nftables",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading,
                    modifier = Modifier.testTag("refresh_netsec_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = CyanPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh NetSec", tint = CyanPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Metrics row: Bans, Alerts, Drops, Tetragon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn("ACTIVE BANS", "${overview?.crowdsecBanCount ?: 0}")
                MetricColumn("IDS ALERTS", "${overview?.suricataAlertCount ?: 0}")
                MetricColumn("DROPPED PKTS", "${overview?.totalDroppedPackets ?: 0}")
                MetricColumn("TETRAGON", (overview?.tetragonHealth ?: "healthy").uppercase())
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Posture badge
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = badgeBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, badgeBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = badgeColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$badgeLabel • Suricata IDS ${if (overview?.suricataActive != false) "Active" else "Offline"} • CrowdSec ${if (overview?.crowdsecActive != false) "Active" else "Offline"}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = badgeColor
                    )
                }
            }
        }
    }
}

@Composable
fun SeverityFilterRow(
    selectedFilter: Int?,
    allCount: Int,
    criticalCount: Int,
    warningCount: Int,
    infoCount: Int,
    onSelectFilter: (Int?) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            FilterChip(
                selected = selectedFilter == null,
                onClick = { onSelectFilter(null) },
                label = { Text("All ($allCount)", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyanPrimary.copy(alpha = 0.2f)),
                modifier = Modifier.testTag("filter_all")
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == 1,
                onClick = { onSelectFilter(if (selectedFilter == 1) null else 1) },
                label = { Text("Critical ($criticalCount)", fontSize = 11.sp, color = RoseError) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = RoseError.copy(alpha = 0.2f)),
                modifier = Modifier.testTag("filter_critical")
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == 2,
                onClick = { onSelectFilter(if (selectedFilter == 2) null else 2) },
                label = { Text("Warning ($warningCount)", fontSize = 11.sp, color = AmberWarning) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberWarning.copy(alpha = 0.2f)),
                modifier = Modifier.testTag("filter_warning")
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == 3,
                onClick = { onSelectFilter(if (selectedFilter == 3) null else 3) },
                label = { Text("Info ($infoCount)", fontSize = 11.sp, color = CyanPrimary) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyanPrimary.copy(alpha = 0.2f)),
                modifier = Modifier.testTag("filter_info")
            )
        }
    }
}

@Composable
fun CrowdSecDecisionCard(
    decision: CrowdSecDecisionItem,
    isUnbanning: Boolean,
    onUnban: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FrostedGlassBorder, RoundedCornerShape(16.dp))
            .testTag("ban_card_${decision.value}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = decision.value,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Scenario: ${decision.scenario} • Origin: ${decision.origin}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 1-Tap Unban Button
                if (isUnbanning) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = RoseError, strokeWidth = 2.dp)
                } else {
                    OutlinedButton(
                        onClick = onUnban,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseError),
                        border = androidx.compose.foundation.BorderStroke(1.dp, RoseError.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("unban_button_${decision.value}")
                    ) {
                        Icon(imageVector = Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Unban", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Duration: ${decision.duration.ifBlank { "--" }} • Expires: ${decision.until.ifBlank { "N/A" }}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CrowdSecCleanStateCard() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = EmeraldSuccess.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.GppGood, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("Zero Active Bans", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EmeraldSuccess)
                Text("No host IP addresses or subnets are currently banned by CrowdSec.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun CrowdSecBouncersStatusCard(bouncers: List<CrowdSecBouncerItem>) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("REGISTERED BOUNCERS (${bouncers.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (b in bouncers) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (b.valid) EmeraldSuccess else RoseError)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${b.name} (${b.type})",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SuricataAlertItemCard(
    alert: SuricataAlertItem,
    index: Int,
    onClick: () -> Unit
) {
    val (badgeBg, badgeBorder, badgeColor, badgeText) = when (alert.alert.severity) {
        1 -> Quad(RoseError.copy(alpha = 0.15f), RoseError.copy(alpha = 0.4f), RoseError, "CRITICAL")
        2 -> Quad(AmberWarning.copy(alpha = 0.15f), AmberWarning.copy(alpha = 0.4f), AmberWarning, "WARNING")
        3 -> Quad(CyanPrimary.copy(alpha = 0.15f), CyanPrimary.copy(alpha = 0.4f), CyanPrimary, "INFO")
        else -> Quad(Color.Gray.copy(alpha = 0.15f), Color.Gray.copy(alpha = 0.4f), Color.Gray, "UNKNOWN")
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FrostedGlassBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("alert_card_$index")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Severity badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeBorder)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = alert.proto,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Signature
            Text(
                text = alert.alert.signature,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (alert.alert.category.isNotBlank()) {
                Text(
                    text = alert.alert.category,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Flow: Src -> Dest
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${alert.srcIp}${if (alert.srcPort != null) ":${alert.srcPort}" else ""}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CyanPrimary
                )
                Text("→", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "${alert.destIp}${if (alert.destPort != null) ":${alert.destPort}" else ""}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = EmeraldSuccess
                )
            }
        }
    }
}

@Composable
fun SuricataAlertDetailDialog(
    alert: SuricataAlertItem,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Suricata Alert Payload",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow("Signature", alert.alert.signature)
                DetailRow("Severity", "${alert.alert.severity} (${NetSecViewModel.severityLabel(alert.alert.severity)})")
                DetailRow("Category", alert.alert.category.ifBlank { "--" })
                DetailRow("Signature ID", alert.alert.signatureId?.toString() ?: "--")
                DetailRow("Timestamp", alert.timestamp)
                DetailRow("Protocol", alert.proto)
                DetailRow("Source", "${alert.srcIp}:${alert.srcPort ?: "--"}")
                DetailRow("Destination", "${alert.destIp}:${alert.destPort ?: "--"}")
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Close")
            }
        },
        modifier = Modifier.testTag("alert_detail_dialog")
    )
}

@Composable
fun TetragonStatusCard(tetragon: TetragonStatusResponse) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FrostedGlassBorder, RoundedCornerShape(16.dp))
            .testTag("tetragon_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cilium Tetragon eBPF Enforcement",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = EmeraldSuccess.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = tetragon.status.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldSuccess,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("ACTIVE TRACING POLICIES (${tetragon.tracingPolicies.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))

            for (policy in tetragon.tracingPolicies) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(policy.name, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = policy.mode.uppercase(),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (policy.mode.contains("enforce", ignoreCase = true)) CyanPrimary else AmberWarning
                    )
                }
            }
        }
    }
}

@Composable
fun FirewallStatusCard(firewall: FirewallStatusResponse) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FrostedGlassBorder, RoundedCornerShape(16.dp))
            .testTag("firewall_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Nftables Host Firewall Posture",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = EmeraldSuccess.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "${firewall.tableCount} TABLES",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldSuccess,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("Total Dropped Packets: ${firewall.totalDroppedPackets}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)

            if (firewall.dropRules.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                for (rule in firewall.dropRules) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Chain: ${rule.chain}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${rule.packets} pkts", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = RoseError)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
