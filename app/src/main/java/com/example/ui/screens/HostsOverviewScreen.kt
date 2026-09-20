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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.HostEntity
import com.example.ui.components.ControlCenterTile
import com.example.ui.components.HostSummaryCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FrostedGlassBorder
import com.example.ui.theme.IndigoSecondary
import com.example.ui.theme.RoseError
import com.example.viewmodel.AppTab
import com.example.viewmodel.ExecutiveDashboardSummary
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.OverviewViewModel

@Composable
fun HostsOverviewScreen(
    viewModel: MainViewModel,
    overviewViewModel: OverviewViewModel = viewModel.overviewViewModel,
    onNavigateToHardware: () -> Unit = { viewModel.selectTab(AppTab.HARDWARE) },
    onNavigateToFleet: () -> Unit = { viewModel.selectTab(AppTab.FLEET) },
    onNavigateToNetSec: () -> Unit = { viewModel.selectTab(AppTab.NETSEC) },
    onNavigateToTerminal: () -> Unit = { viewModel.selectTab(AppTab.TERMINAL) },
    onNavigateToAgent: () -> Unit = { viewModel.selectTab(AppTab.AGENT) },
    modifier: Modifier = Modifier
) {
    val hosts by viewModel.hostsList.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeHost by viewModel.currentHost.collectAsStateWithLifecycle()
    val audioState by viewModel.audioRelayEngine.state.collectAsStateWithLifecycle()
    val snippets by viewModel.snippetList.collectAsStateWithLifecycle()
    val overviewState by overviewViewModel.uiState.collectAsStateWithLifecycle()
    val summary = overviewState.summary

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Bar
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Control Center",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Streamlined remote workstation manager",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Add Host Button
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { viewModel.setAddHostDialogVisible(true) }
                            .testTag("add_host_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Host",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Add Host",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Executive Health Overview Header & Status Badge
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "EXECUTIVE OVERVIEW",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val overallColor = when (summary.overallStatus) {
                            "OPTIMAL" -> EmeraldSuccess
                            "WARNING" -> AmberWarning
                            "CRITICAL" -> RoseError
                            else -> Color.Gray
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = overallColor.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, overallColor.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("overall_health_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(overallColor)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = summary.overallStatus,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = overallColor
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { overviewViewModel.refresh() },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("executive_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Executive Health",
                            tint = CyanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // The 4 Primary Executive Overview Cards (2x2 Grid for <5-Second Readability)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExecutiveHealthCard(
                        title = "SYSTEM HEALTH",
                        headline = summary.systemHealthCard,
                        subtext = "Dual RTX 5060 Ti • Ultra 7 • NPU",
                        badgeText = if (summary.systemHealthCard.contains("Optimal")) "OPTIMAL" else if (summary.overallStatus == "OFFLINE") "OFFLINE" else "ATTENTION",
                        badgeColor = if (summary.systemHealthCard.contains("Optimal")) EmeraldSuccess else if (summary.overallStatus == "OFFLINE") Color.Gray else AmberWarning,
                        accentColor = CyanPrimary,
                        icon = Icons.Default.Memory,
                        drilldownLabel = "Silicon Sensors ->",
                        testTag = "card_system_health",
                        onClick = onNavigateToHardware,
                        modifier = Modifier.weight(1f)
                    )
                    ExecutiveHealthCard(
                        title = "FLEET DAEMONS",
                        headline = summary.fleetDaemonsCard,
                        subtext = "12 Supervised Fleet Agents",
                        badgeText = if (summary.fleetDaemonsCard.contains("Active")) "SUPERVISED" else "OFFLINE",
                        badgeColor = if (summary.fleetDaemonsCard.contains("12/12") || summary.fleetDaemonsCard.contains("11/12")) EmeraldSuccess else AmberWarning,
                        accentColor = IndigoSecondary,
                        icon = Icons.Default.Layers,
                        drilldownLabel = "Manage Fleet ->",
                        testTag = "card_fleet_daemons",
                        onClick = onNavigateToFleet,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExecutiveHealthCard(
                        title = "SECURITY & SIEM",
                        headline = summary.securitySiemCard,
                        subtext = "CrowdSec LAPI • Suricata 8",
                        badgeText = if (summary.securitySiemCard.contains("Offline")) "OFFLINE" else if (summary.securitySiemCard.contains("0 Alerts")) "SECURE" else "PROTECTED",
                        badgeColor = if (summary.securitySiemCard.contains("Offline")) Color.Gray else RoseError,
                        accentColor = RoseError,
                        icon = Icons.Default.Shield,
                        drilldownLabel = "SIEM & Unban ->",
                        testTag = "card_security_siem",
                        onClick = onNavigateToNetSec,
                        modifier = Modifier.weight(1f)
                    )
                    ExecutiveHealthCard(
                        title = "WORKSTATION MESH",
                        headline = summary.connectedWorkstationsCard,
                        subtext = "${activeHost?.name ?: "fml"} (${activeHost?.address ?: "100.111.123.93"})",
                        badgeText = if (activeHost?.isOnline == true) "ONLINE" else "OFFLINE",
                        badgeColor = if (activeHost?.isOnline == true) EmeraldSuccess else Color.Gray,
                        accentColor = EmeraldSuccess,
                        icon = Icons.Default.VpnKey,
                        drilldownLabel = "Refresh Mesh ->",
                        testTag = "card_connected_workstations",
                        onClick = { overviewViewModel.refresh() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Host Cards List
            item {
                Text(
                    text = "CONFIGURED HOSTS (${hosts.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (hosts.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setAddHostDialogVisible(true) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Host",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                text = "No Workstations Configured",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap here or '+ Add Host' above to connect your Linux workstation in 1 click via SSH.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(hosts) { host ->
                    HostSummaryCard(
                        host = host,
                        isSelected = host.id == uiState.selectedHostId,
                        onSelect = { viewModel.selectHost(host.id) },
                        onOpenProvisioning = {
                            viewModel.selectHost(host.id)
                            viewModel.setProvisioningModalVisible(true)
                        },
                        onDelete = { viewModel.deleteHost(host) }
                    )
                }
            }

            // Quick Control Tiles Grid
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "QUICK ACTIONS: ${activeHost?.name ?: "None"}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ControlCenterTile(
                        title = "Termux Shell",
                        subtitle = "SSH Console",
                        icon = Icons.Default.Terminal,
                        isActive = false,
                        onClick = { viewModel.selectTab(AppTab.TERMINAL) },
                        modifier = Modifier.weight(1f)
                    )
                    ControlCenterTile(
                        title = "Screen Mirror",
                        subtitle = "VNC / 60 FPS",
                        icon = Icons.Default.DisplaySettings,
                        isActive = false,
                        onClick = { viewModel.selectTab(AppTab.DISPLAY) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ControlCenterTile(
                        title = "Audio Relay",
                        subtitle = if (audioState.isStreaming) "Streaming 48kHz" else "Tap to Stream",
                        icon = Icons.Default.GraphicEq,
                        isActive = audioState.isStreaming,
                        activeColor = EmeraldSuccess,
                        onClick = {
                            viewModel.audioRelayEngine.toggleStreaming()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ControlCenterTile(
                        title = "Trackpad",
                        subtitle = "Touch Input",
                        icon = Icons.Default.Mouse,
                        isActive = false,
                        onClick = { viewModel.selectTab(AppTab.DISPLAY) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ControlCenterTile(
                        title = "Cockpit Web",
                        subtitle = "Port 9090",
                        icon = Icons.Default.OpenInBrowser,
                        isActive = false,
                        onClick = { viewModel.selectTab(AppTab.CONNECT) },
                        modifier = Modifier.weight(1f)
                    )
                    ControlCenterTile(
                        title = "AI Agent RFCs",
                        subtitle = "Auto Governance",
                        icon = Icons.Default.AutoAwesome,
                        isActive = false,
                        activeColor = MaterialTheme.colorScheme.secondary,
                        onClick = { viewModel.selectTab(AppTab.AGENT) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Quick Snippets runner
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "PINNED REMOTE COMMANDS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(snippets.filter { it.isFavorite }) { snippet ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, FrostedGlassBorder, RoundedCornerShape(16.dp))
                        .clickable {
                            viewModel.terminalEngine.executeCommand(snippet.command)
                            viewModel.selectTab(AppTab.TERMINAL)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = snippet.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = snippet.command,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Run",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Add Host Dialog
        if (uiState.showAddHostDialog) {
            AddHostDialog(
                onDismiss = { viewModel.setAddHostDialogVisible(false) },
                onAddAndProvision = { name, ip, port, user, pass, cockpit, audio ->
                    viewModel.addNewHostAndStartProvision(name, ip, port, user, pass, cockpit, audio)
                }
            )
        }
    }
}

@Composable
fun AddHostDialog(
    onDismiss: () -> Unit,
    onAddAndProvision: (name: String, address: String, port: Int, user: String, pass: String, cockpit: Boolean, audio: Boolean) -> Unit
) {
    var hostName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("192.168.1.") }
    var portString by remember { mutableStateOf("22") }
    var username by remember { mutableStateOf("hostmanager") }
    var password by remember { mutableStateOf("") }
    var autoInstallCockpit by remember { mutableStateOf(true) }
    var autoInstallAudio by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder),
            modifier = Modifier.fillMaxWidth(0.96f)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add & Auto-Provision Host",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel")
                    }
                }

                Text(
                    text = "App will automatically generate an SSH key on your device, configure the user account, and install required services in one click.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = hostName,
                    onValueChange = { hostName = it },
                    label = { Text("Display Name (e.g. Media Server)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("host_name_input")
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("IP / Hostname") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(2f)
                            .testTag("host_address_input")
                    )
                    OutlinedTextField(
                        value = portString,
                        onValueChange = { portString = it },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Initial SSH User (or root)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Initial Password (for key setup only)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = autoInstallCockpit,
                        onCheckedChange = { autoInstallCockpit = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Auto-install Cockpit Web Console (9090)", fontSize = 12.sp)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = autoInstallAudio,
                        onCheckedChange = { autoInstallAudio = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Auto-configure PipeWire Audio Relay", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val port = portString.toIntOrNull() ?: 22
                        onAddAndProvision(
                            hostName,
                            address,
                            port,
                            username,
                            password,
                            autoInstallCockpit,
                            autoInstallAudio
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("start_provision_button")
                ) {
                    Text(
                        text = "Start 1-Click Provisioning",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ExecutiveHealthCard(
    title: String,
    headline: String,
    subtext: String,
    badgeText: String,
    badgeColor: Color,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    drilldownLabel: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Card Top Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = title,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Headline Text
            Text(
                text = headline,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )

            // Subtext
            Text(
                text = subtext,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )

            // Bottom Drilldown Prompt
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = drilldownLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor
                )
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

