package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.ClipboardItemEntity
import com.example.service.AudioRelayMode
import com.example.service.ClipboardSyncMode
import com.example.service.HostClipboardTool
import com.example.service.RemoteClipboardState
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FrostedGlassBorder
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.RemoteFileItem

@Composable
fun HubConnectScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val activeHost by viewModel.currentHost.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val audioRelayEngine = viewModel.audioRelayEngine
    val audioState by audioRelayEngine.state.collectAsStateWithLifecycle()
    val clipboardHistory by viewModel.clipboardHistory.collectAsStateWithLifecycle()
    val clipboardState by viewModel.remoteClipboardService.state.collectAsStateWithLifecycle()

    var activeSubTab by remember { mutableIntStateOf(0) } // 0: Audio Relay, 1: Files, 2: Clipboard, 3: Cockpit
    var inputClipboardText by remember { mutableStateOf("") }
    var uploadFileName by remember { mutableStateOf("") }
    var uploadFileSize by remember { mutableStateOf("12.4 MB") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "KDE Connect Hub",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Connected to ${activeHost?.name ?: "Host"}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Subtabs
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder)
        ) {
            TabRow(
                selectedTabIndex = activeSubTab,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(selected = activeSubTab == 0, onClick = { activeSubTab = 0 }, text = { Text("Audio", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) })
                Tab(selected = activeSubTab == 1, onClick = { activeSubTab = 1 }, text = { Text("Files", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) })
                Tab(selected = activeSubTab == 2, onClick = { activeSubTab = 2 }, text = { Text("Clipboard", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) })
                Tab(selected = activeSubTab == 3, onClick = { activeSubTab = 3 }, text = { Text("Cockpit", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) })
            }
        }

        // Content Panes
        when (activeSubTab) {
            0 -> {
                // AUDIO RELAY PANE
                AudioRelayCard(audioRelayEngine = audioRelayEngine, audioState = audioState)
            }
            1 -> {
                // REMOTE FILE MANAGER PANE
                FileManagerCard(
                    currentPath = uiState.selectedDirectoryPath,
                    files = uiState.fileItems,
                    onNavigate = { viewModel.loadDirectoryFiles(it) },
                    onUploadClick = { viewModel.setFileTransferModal(true, "PUSH") }
                )
            }
            2 -> {
                // SHARED CLIPBOARD PANE
                ClipboardCard(
                    inputText = inputClipboardText,
                    clipboardState = clipboardState,
                    clipboardHistory = clipboardHistory,
                    onInputChange = { inputClipboardText = it },
                    onPushToHost = {
                        viewModel.pushClipboardToHost(inputClipboardText)
                        inputClipboardText = ""
                    },
                    onFetchFromHost = {
                        viewModel.fetchHostClipboard()
                    },
                    onToggleAutoSync = {
                        viewModel.toggleClipboardAutoSync()
                    },
                    onSetSyncMode = { mode ->
                        viewModel.setClipboardSyncMode(mode)
                    },
                    onSetTargetTool = { tool ->
                        viewModel.setClipboardTargetTool(tool)
                    },
                    onCopyLocal = { text ->
                        clipboardManager.setText(AnnotatedString(text))
                        viewModel.showNotice("Copied to Android device clipboard")
                    },
                    onDeleteItem = { item ->
                        viewModel.deleteClipboardItem(item)
                    },
                    onClearHistory = {
                        viewModel.clearClipboardHistory()
                    }
                )
            }
            3 -> {
                // COCKPIT & WEBMIN PANE
                CockpitLauncherCard(
                    host = activeHost,
                    onOpenCockpit = {
                        val hostIp = activeHost?.address ?: "192.168.1.150"
                        val port = activeHost?.cockpitPort ?: 9090
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://$hostIp:$port"))
                        context.startActivity(intent)
                    },
                    onOpenWebmin = {
                        val hostIp = activeHost?.address ?: "192.168.1.150"
                        val port = activeHost?.webminPort ?: 10000
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://$hostIp:$port"))
                        context.startActivity(intent)
                    }
                )
            }
        }

        // Upload Modal
        if (uiState.showFileTransferModal) {
            Dialog(onDismissRequest = { viewModel.setFileTransferModal(false) }) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder),
                    modifier = Modifier.fillMaxWidth(0.95f)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Send File to Host (SFTP)", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Target directory: ${uiState.selectedDirectoryPath}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        OutlinedTextField(
                            value = uploadFileName,
                            onValueChange = { uploadFileName = it },
                            label = { Text("File Name (e.g. update_package.zip)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = uploadFileSize,
                            onValueChange = { uploadFileSize = it },
                            label = { Text("File Size") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(
                                onClick = {
                                    val name = uploadFileName.ifBlank { "attachment_file.tar.gz" }
                                    viewModel.uploadSimulatedFile(name, uploadFileSize)
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Upload Now")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioRelayCard(
    audioRelayEngine: com.example.service.AudioRelayEngine,
    audioState: com.example.service.AudioRelayState
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FrostedGlassBorder, RoundedCornerShape(22.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header with low-latency badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, tint = CyanPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Low-Latency Audio Relay", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (audioState.isStreaming) EmeraldSuccess.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (audioState.isStreaming) EmeraldSuccess else FrostedGlassBorder)
                ) {
                    Text(
                        text = if (audioState.isStreaming) "AudioTrack ACTIVE" else "AudioTrack IDLE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (audioState.isStreaming) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Mode Toggle (Host to Phone vs Phone to Host)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (audioState.mode == AudioRelayMode.HOST_TO_PHONE) CyanPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (audioState.mode == AudioRelayMode.HOST_TO_PHONE) CyanPrimary else FrostedGlassBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { audioRelayEngine.setMode(AudioRelayMode.HOST_TO_PHONE) }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Headphones, contentDescription = null, tint = CyanPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Host -> Phone", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("AudioTrack 48kHz", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (audioState.mode == AudioRelayMode.PHONE_TO_HOST) CyanPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (audioState.mode == AudioRelayMode.PHONE_TO_HOST) CyanPrimary else FrostedGlassBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { audioRelayEngine.setMode(AudioRelayMode.PHONE_TO_HOST) }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = CyanPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Phone -> Host", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("AudioRecord Mic", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Audio Waveform Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0A0F1D))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    audioState.visualizerAmplitudes.forEach { amp ->
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .height((56.dp * amp).coerceAtLeast(4.dp))
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (audioState.isStreaming) CyanPrimary else Color(0xFF334155))
                        )
                    }
                }
            }

            // Latency Buffer Profile Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Buffer Latency Profile", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10 to "10ms (Ultra)", 20 to "20ms (Optimal)", 50 to "50ms (Safe)").forEach { (lat, label) ->
                        val isSelected = audioState.bufferLatencyMs == lat
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) CyanPrimary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) CyanPrimary else FrostedGlassBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { audioRelayEngine.setLatency(lat) }
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) CyanPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Audio Specs row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Stream: 48kHz Stereo S16LE", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Target: ${audioState.targetHost}:${audioState.targetPort}", fontSize = 11.sp, color = CyanPrimary, fontFamily = FontFamily.Monospace)
            }

            // Telemetry counters
            if (audioState.isStreaming) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Processed: ${audioState.packetsTransferred} kB", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Underruns: ${audioState.underruns}", fontSize = 10.sp, color = if (audioState.underruns > 0) Color(0xFFF59E0B) else EmeraldSuccess)
                }
            }

            // Volume Gain Slider & Mute Toggle
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Volume Gain / Limiter", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (audioState.isMuted) "MUTED" else "${(audioState.volumeGain * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (audioState.isMuted) Color(0xFFEF4444) else CyanPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (audioState.isMuted) "Unmute" else "Mute",
                            fontSize = 11.sp,
                            color = CyanPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { audioRelayEngine.toggleMute() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Slider(
                    value = audioState.volumeGain,
                    onValueChange = { audioRelayEngine.setVolumeGain(it) },
                    valueRange = 0f..1.5f
                )
            }

            // Toggle Streaming Button
            Button(
                onClick = { audioRelayEngine.toggleStreaming() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (audioState.isStreaming) Color(0xFFEF4444) else EmeraldSuccess
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("audio_streaming_toggle_button")
            ) {
                Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (audioState.isStreaming) "Stop Audio Relay" else "Start Low-Latency Audio Relay",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun FileManagerCard(
    currentPath: String,
    files: List<RemoteFileItem>,
    onNavigate: (String) -> Unit,
    onUploadClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FrostedGlassBorder, RoundedCornerShape(22.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Host File System", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(currentPath, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = CyanPrimary)
                }

                Button(
                    onClick = onUploadClick,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Upload", fontSize = 11.sp)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(files) { file ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (file.isDirectory) onNavigate(file.path)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.Assignment,
                                    contentDescription = null,
                                    tint = if (file.isDirectory) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = file.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "${file.permissions} • ${file.sizeString} ${file.modifiedTime}",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (file.isDirectory) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Open",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = "Download",
                                    tint = CyanPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClipboardCard(
    inputText: String,
    clipboardState: RemoteClipboardState,
    clipboardHistory: List<ClipboardItemEntity>,
    onInputChange: (String) -> Unit,
    onPushToHost: () -> Unit,
    onFetchFromHost: () -> Unit,
    onToggleAutoSync: () -> Unit,
    onSetSyncMode: (ClipboardSyncMode) -> Unit,
    onSetTargetTool: (HostClipboardTool) -> Unit,
    onCopyLocal: (String) -> Unit,
    onDeleteItem: (ClipboardItemEntity) -> Unit,
    onClearHistory: () -> Unit
) {
    val localClipboard = LocalClipboardManager.current

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FrostedGlassBorder, RoundedCornerShape(22.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header with Live Sync Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Seamless Clipboard Sync", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            text = "Android ⟷ Linux Workstation (SSH)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (clipboardState.isAutoSyncEnabled) EmeraldSuccess.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (clipboardState.isAutoSyncEnabled) EmeraldSuccess else FrostedGlassBorder
                    ),
                    modifier = Modifier.clickable { onToggleAutoSync() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (clipboardState.isAutoSyncEnabled) Icons.Default.Sync else Icons.Default.Pause,
                            contentDescription = null,
                            tint = if (clipboardState.isAutoSyncEnabled) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (clipboardState.isAutoSyncEnabled) "LIVE SYNC ON" else "SYNC PAUSED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (clipboardState.isAutoSyncEnabled) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Sync Mode Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Sync Flow Mode",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        ClipboardSyncMode.BIDIRECTIONAL to "Two-Way",
                        ClipboardSyncMode.PHONE_TO_HOST_ONLY to "Phone -> Host",
                        ClipboardSyncMode.HOST_TO_PHONE_ONLY to "Host -> Phone",
                        ClipboardSyncMode.MANUAL to "Manual"
                    ).forEach { (mode, label) ->
                        val isSelected = clipboardState.syncMode == mode
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) CyanPrimary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) CyanPrimary else FrostedGlassBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSetSyncMode(mode) }
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) CyanPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Host Clipboard Tool Selector (wl-copy vs xclip)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Host Clipboard Tool",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Target: ${clipboardState.targetHostAddress}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = CyanPrimary
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        HostClipboardTool.AUTO_DETECT to "Auto (wl-copy/xclip)",
                        HostClipboardTool.WAYLAND_WL_COPY to "Wayland (wl-copy)",
                        HostClipboardTool.X11_XCLIP to "X11 (xclip)"
                    ).forEach { (tool, label) ->
                        val isSelected = clipboardState.targetTool == tool
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) CyanPrimary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) CyanPrimary else FrostedGlassBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSetTargetTool(tool) }
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) CyanPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                // Command Pipeline Monospace Hint
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "SSH Pipe: echo <b64> | base64 -d | ${if (clipboardState.targetTool == HostClipboardTool.X11_XCLIP) "xclip -selection clipboard" else "wl-copy"}",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Input TextField
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    placeholder = { Text("Paste or enter text to push to host clipboard...", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Quick Paste from local clipboard + Clear buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    val clip = localClipboard.getText()?.text ?: ""
                                    if (clip.isNotBlank()) onInputChange(clip)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(12.dp), tint = CyanPrimary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Paste Device Clip", fontSize = 10.sp, color = CyanPrimary)
                            }
                        }

                        if (inputText.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onInputChange("") }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    Text("${inputText.length} chars", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Quick Snippets
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Quick Snippets:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "git status" to "git status",
                        "nvidia-smi" to "nvidia-smi",
                        "vLLM Serve" to "python3 -m vllm.entrypoints.openai.api_server",
                        "Host IP" to clipboardState.targetHostAddress
                    ).forEach { (label, snippet) ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onInputChange(snippet) }
                        ) {
                            Text(
                                text = label,
                                fontSize = 9.sp,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Push & Pull Action Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPushToHost,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Push via SSH", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onFetchFromHost,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pull from Host")
                }
            }

            // Real-time Telemetry Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Pushed: ${clipboardState.totalPushedCount} | Pulled: ${clipboardState.totalPulledCount}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = clipboardState.statusMessage,
                    fontSize = 11.sp,
                    color = CyanPrimary,
                    fontWeight = FontWeight.Medium
                )
            }

            // Synced Clipboard History Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sync History (${clipboardHistory.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                if (clipboardHistory.isNotEmpty()) {
                    Text(
                        text = "Clear All",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onClearHistory() }
                            .padding(4.dp)
                    )
                }
            }

            // Synced Clipboard History List
            if (clipboardHistory.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No clipboard entries yet. Text copied on Android or host will appear here automatically.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    clipboardHistory.take(10).forEach { item ->
                        val isPhoneToHost = item.direction == "PHONE_TO_HOST"
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isPhoneToHost) EmeraldSuccess.copy(alpha = 0.2f) else CyanPrimary.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = if (isPhoneToHost) "PHONE -> HOST" else "HOST -> PHONE",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPhoneToHost) EmeraldSuccess else CyanPrimary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(item.timestamp)),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Action icons: Copy to local, Re-push, Delete
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = { onCopyLocal(item.content) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy to Android",
                                                tint = CyanPrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { onInputChange(item.content) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "Load to Input",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { onDeleteItem(item) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = item.content,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 3,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CockpitLauncherCard(
    host: com.example.data.entity.HostEntity?,
    onOpenCockpit: () -> Unit,
    onOpenWebmin: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FrostedGlassBorder, RoundedCornerShape(22.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "Cockpit & Webmin Management",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Access full web consoles for storage, systemd services, users, networking, and containers without locking down any parameters.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Cockpit Tile
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenCockpit() }
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CyanPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, tint = CyanPrimary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Cockpit Web Console", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Port ${host?.cockpitPort ?: 9090} • TLS Active", fontSize = 11.sp, color = EmeraldSuccess)
                        }
                    }
                    Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = CyanPrimary)
                }
            }

            // Webmin Tile
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenWebmin() }
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Webmin Admin Console", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Port ${host?.webminPort ?: 10000} • Linux Admin", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}
