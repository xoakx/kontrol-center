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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.HostEntity
import com.example.ui.components.ControlCenterTile
import com.example.ui.components.HostSummaryCard
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FrostedGlassBorder
import com.example.viewmodel.AppTab
import com.example.viewmodel.MainViewModel

@Composable
fun HostsOverviewScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val hosts by viewModel.hostsList.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeHost by viewModel.currentHost.collectAsStateWithLifecycle()
    val audioState by viewModel.audioRelayEngine.state.collectAsStateWithLifecycle()
    val snippets by viewModel.snippetList.collectAsStateWithLifecycle()

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

            items(hosts) { host ->
                HostSummaryCard(
                    host = host,
                    isSelected = host.id == uiState.selectedHostId,
                    onSelect = { viewModel.selectHost(host.id) },
                    onOpenProvisioning = {
                        viewModel.selectHost(host.id)
                        viewModel.setProvisioningModalVisible(true)
                    }
                )
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
                    modifier = Modifier.fillMaxWidth()
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
