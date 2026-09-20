package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.api.GpuTelemetryDto
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FrostedGlassBorder
import com.example.ui.theme.RoseError
import com.example.viewmodel.HardwareUiState
import com.example.viewmodel.HardwareViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HardwareSensorsScreen(
    viewModel: HardwareViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val telemetry = uiState.telemetry
    val smarthome = uiState.smartHome

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("hardware_screen")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Bar & Tunables Quick Actions
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Silicon & Telemetry",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Intel Core Ultra 7 265K • Dual RTX 5060 Ti • NPU 4",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { viewModel.refreshAll() },
                        enabled = !uiState.isLoading,
                        modifier = Modifier.testTag("refresh_hardware_button")
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = CyanPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = CyanPrimary)
                        }
                    }
                }
            }

            // Notice / Error Banner
            if (uiState.userNotice != null || uiState.errorMessage != null) {
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
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isError) RoseError else EmeraldSuccess,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.errorMessage ?: uiState.userNotice ?: "",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Power & Tunables Control Card (CPU Governor Toggle & Audio Re-Anchor)
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, FrostedGlassBorder, RoundedCornerShape(18.dp))
                        .testTag("tunables_control_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Hardware & Power Tunables",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Governor toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "CPU Scaling Governor",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Profile: ${uiState.governor.uppercase()} (TuneD sync)",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = if (uiState.governor.equals("performance", ignoreCase = true)) EmeraldSuccess else AmberWarning
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (uiState.isGovernorToggling) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = CyanPrimary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Switch(
                                    checked = uiState.governor.equals("performance", ignoreCase = true),
                                    onCheckedChange = { checked ->
                                        viewModel.toggleCpuGovernor(if (checked) "performance" else "powersave")
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = EmeraldSuccess,
                                        checkedTrackColor = EmeraldSuccess.copy(alpha = 0.3f),
                                        uncheckedThumbColor = AmberWarning,
                                        uncheckedTrackColor = AmberWarning.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.testTag("cpu_governor_switch")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Audio Re-Anchor Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Audio Pipeline Re-Anchor",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Restart PipeWire null sinks & bounce arbitrator",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            OutlinedButton(
                                onClick = { viewModel.reanchorAudio() },
                                shape = RoundedCornerShape(8.dp),
                                enabled = !uiState.isAudioReanchoring,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanPrimary),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.5f)),
                                modifier = Modifier.testTag("audio_reanchor_button")
                            ) {
                                if (uiState.isAudioReanchoring) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = CyanPrimary)
                                } else {
                                    Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Re-Anchor", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Dual RTX 5060 Ti GPUs
            item {
                Text(
                    text = "DUAL NVIDIA RTX 5060 TI (16GB GDDR6)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (telemetry != null && telemetry.gpus.isNotEmpty()) {
                items(telemetry.gpus.size) { idx ->
                    val gpu = telemetry.gpus[idx]
                    GpuMetricCard(gpu = gpu, testTag = "gpu_card_${gpu.index}")
                }
            } else {
                item {
                    Text("No GPU telemetry available.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Intel Core Ultra 7 265K 20-Core Topology (8 P-Cores, 12 E-Cores)
            item {
                IntelCpu20CoreCard(
                    uiState = uiState,
                    modifier = Modifier.testTag("cpu_20core_card")
                )
            }

            // OpenVINO Intel AI Boost NPU Accelerator
            item {
                OpenVinoNpuCard(
                    uiState = uiState,
                    modifier = Modifier.testTag("npu_status_card")
                )
            }

            // RAM and NVMe Storage Breakdown
            if (telemetry != null) {
                item {
                    RamStorageCard(
                        telemetry = telemetry,
                        modifier = Modifier.testTag("ram_storage_card")
                    )
                }
            }

            // Smart Home Telemetry Integration
            item {
                Text(
                    text = "SMART HOME & ENVIRONMENTAL SENSORS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("smarthome_section")
                )
            }

            if (smarthome != null) {
                // Apollo MSR-2 Radar
                val apollo = smarthome.sensors["apollo_msr2"]
                if (apollo != null) {
                    item {
                        ApolloRadarCard(apollo = apollo, modifier = Modifier.testTag("apollo_radar_card"))
                    }
                }

                // XMOS XVF3800 Voice Satellite
                val xmos = smarthome.voiceSatellites["xvf3800"]
                if (xmos != null) {
                    item {
                        XmosSatelliteCard(xmos = xmos, modifier = Modifier.testTag("xmos_voice_card"))
                    }
                }

                // Sonoff Door Contact
                val sonoff = smarthome.zigbeePerimeter["sonoff_door"]
                if (sonoff != null) {
                    item {
                        SonoffDoorCard(sonoff = sonoff, modifier = Modifier.testTag("sonoff_door_card"))
                    }
                }

                // Levoit Air Purifier
                val levoit = smarthome.airPurifier["levoit_purifier"]
                if (levoit != null) {
                    item {
                        LevoitPurifierCard(
                            purifier = levoit,
                            onSetFanSpeed = { viewModel.setPurifierFanSpeed(it) },
                            onTogglePower = { viewModel.togglePurifierPower() },
                            modifier = Modifier.testTag("levoit_purifier_card")
                        )
                    }
                }
            } else {
                item {
                    Text("No smart home telemetry connected.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun GpuMetricCard(gpu: GpuTelemetryDto, testTag: String) {
    val isSpike = gpu.tempC >= 85
    val tempColor = when {
        gpu.tempC >= 95 -> RoseError
        gpu.tempC >= 85 -> AmberWarning
        else -> EmeraldSuccess
    }

    val memUsedPct = if (gpu.memTotalMb > 0) (gpu.memUsedMb.toFloat() / gpu.memTotalMb.toFloat()) else 0f

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FrostedGlassBorder, RoundedCornerShape(16.dp))
            .testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Card Title & Temp Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "GPU ${gpu.index}: ${gpu.name}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (gpu.index == "0") "Primary Display / Wayland / Reflex LLM" else "Headless Compute / Qwen 14B Deep LLM",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = tempColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, tempColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "${gpu.tempC}°C",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = tempColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // VRAM Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("VRAM (${gpu.memUsedMb} / ${gpu.memTotalMb} MB)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${(memUsedPct * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyanPrimary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { memUsedPct.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (memUsedPct > 0.9f) RoseError else CyanPrimary,
                trackColor = MaterialTheme.colorScheme.surface
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Core Metrics Row: Util %, Power W, Fan Speed %, Clock
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn(title = "UTILIZATION", value = "${gpu.utilPct}%")
                MetricColumn(title = "POWER", value = String.format("%.1f W", gpu.powerW))
                MetricColumn(title = "FAN SPEED", value = "${gpu.fanSpeedPct}%")
                MetricColumn(title = "CLOCK", value = "${gpu.clockMhz} MHz")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntelCpu20CoreCard(uiState: HardwareUiState, modifier: Modifier = Modifier) {
    val telem = uiState.telemetry
    val cpu = telem?.cpu
    val tempColor = when {
        uiState.cpuTemp >= 95.0 -> RoseError
        uiState.cpuTemp >= 85.0 -> AmberWarning
        else -> EmeraldSuccess
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, FrostedGlassBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Intel Core Ultra 7 265K (Arrow Lake-S)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "20 Physical Cores: 8 Lion Cove (P) + 12 Skymont (E)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = tempColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, tempColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = String.format("%.1f°C", uiState.cpuTemp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = tempColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Load Averages
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetricColumn(title = "LOAD 1M", value = cpu?.load1m ?: "0.0")
                MetricColumn(title = "LOAD 5M", value = cpu?.load5m ?: "0.0")
                MetricColumn(title = "LOAD 15M", value = cpu?.load15m ?: "0.0")
                MetricColumn(title = "GOVERNOR", value = uiState.governor)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 8 Performance Cores (0..7)
            Text(
                text = "P-Cores 0-7 (Avg: ${String.format("%.1f%%", uiState.pCoreAverageLoad)})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyanPrimary,
                modifier = Modifier.testTag("p_cores_section")
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                maxItemsInEachRow = 8
            ) {
                for (i in uiState.pCoreLoads.indices) {
                    CoreBox(coreIndex = i, load = uiState.pCoreLoads[i], isPerformance = true)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 12 Efficiency Cores (8..19)
            Text(
                text = "E-Cores 8-19 (Avg: ${String.format("%.1f%%", uiState.eCoreAverageLoad)}) — Dedicated Fleet Affinity",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = EmeraldSuccess,
                modifier = Modifier.testTag("e_cores_section")
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                maxItemsInEachRow = 6
            ) {
                for (i in uiState.eCoreLoads.indices) {
                    CoreBox(coreIndex = i + 8, load = uiState.eCoreLoads[i], isPerformance = false)
                }
            }
        }
    }
}

@Composable
fun CoreBox(coreIndex: Int, load: Float, isPerformance: Boolean) {
    val barColor = if (isPerformance) CyanPrimary else EmeraldSuccess
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder),
        modifier = Modifier
            .width(36.dp)
            .height(44.dp)
    ) {
        Column(
            modifier = Modifier.padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "C$coreIndex", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { (load / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = barColor,
                trackColor = Color.Transparent
            )
            Text(text = "${load.toInt()}%", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = barColor)
        }
    }
}

@Composable
fun OpenVinoNpuCard(uiState: HardwareUiState, modifier: Modifier = Modifier) {
    val telem = uiState.telemetry?.npu
    val isPresent = telem?.present == true

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, FrostedGlassBorder, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Intel AI Boost NPU 4 Accelerator",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isPresent) "Node: ${telem?.device} • Service: ${telem?.service}" else "NPU Accelerator not detected",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isPresent) EmeraldSuccess.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isPresent) EmeraldSuccess.copy(alpha = 0.4f) else Color.Gray)
            ) {
                Text(
                    text = if (isPresent) "HEALTHY (:8002)" else "OFFLINE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPresent) EmeraldSuccess else Color.Gray,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun RamStorageCard(telemetry: com.example.data.api.TelemetryResponse, modifier: Modifier = Modifier) {
    val mem = telemetry.memory
    val storage = telemetry.storage

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, FrostedGlassBorder, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // RAM Column
            Column(modifier = Modifier.weight(1f)) {
                Text("RAM (DDR5 64GB)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (mem.usedPct / 100f).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = CyanPrimary,
                    trackColor = MaterialTheme.colorScheme.surface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${mem.usedMb} / ${mem.totalMb} MB (${String.format("%.1f", mem.usedPct)}%)",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Storage Column
            Column(modifier = Modifier.weight(1f)) {
                Text("Storage (NVMe Btrfs)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (storage.usedPct / 100f).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = EmeraldSuccess,
                    trackColor = MaterialTheme.colorScheme.surface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${String.format("%.1f", storage.usedGb)} / ${String.format("%.1f", storage.totalGb)} GB",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ApolloRadarCard(apollo: com.example.data.api.SensorDeviceDto, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, FrostedGlassBorder, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Sensors, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Apollo MSR-2 mmWave Radar", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (apollo.presence) EmeraldSuccess.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (apollo.presence) EmeraldSuccess.copy(alpha = 0.4f) else Color.Gray)
                ) {
                    Text(
                        text = if (apollo.presence) "OCCUPIED" else "CLEAR",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (apollo.presence) EmeraldSuccess else Color.Gray,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricColumn("DISTANCE", "${String.format("%.2f", apollo.targetDistanceM)} m")
                MetricColumn("MOVEMENT", "${apollo.movementEnergy}%")
                MetricColumn("STILL ENERGY", "${apollo.stillEnergy}%")
                MetricColumn("CO2", "${apollo.co2Ppm} ppm")
                MetricColumn("LUX", "${apollo.illuminanceLux.toInt()} lx")
            }
        }
    }
}

@Composable
fun XmosSatelliteCard(xmos: com.example.data.api.VoiceSatelliteDto, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, FrostedGlassBorder, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("XMOS XVF3800 DSP Satellite", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = EmeraldSuccess.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = xmos.state.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldSuccess,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Beam: ${xmos.beamAngleDeg}° • Wake: \"${xmos.wakeWord}\" • Location: ${xmos.location}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (xmos.lastIntent.isNotBlank()) {
                Text(
                    text = "Last Intent: ${xmos.lastIntent}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CyanPrimary
                )
            }
        }
    }
}

@Composable
fun SonoffDoorCard(sonoff: com.example.data.api.ZigbeeContactDto, modifier: Modifier = Modifier) {
    val isOpen = sonoff.state.equals("open", ignoreCase = true)
    val stateColor = if (isOpen) AmberWarning else EmeraldSuccess

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, FrostedGlassBorder, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(sonoff.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "Battery: ${sonoff.batteryPct}% • LQI: ${sonoff.lqi}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = stateColor.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, stateColor.copy(alpha = 0.4f))
            ) {
                Text(
                    text = sonoff.state.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = stateColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun LevoitPurifierCard(
    purifier: com.example.data.api.AirPurifierDto,
    onSetFanSpeed: (Int) -> Unit,
    onTogglePower: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOn = purifier.power.equals("on", ignoreCase = true)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, FrostedGlassBorder, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(purifier.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = "PM2.5: ${purifier.pm25Aqi} AQI • Quality: ${purifier.airQuality} • Filter: ${purifier.filterLifePct}%",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isOn) EmeraldSuccess.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isOn) EmeraldSuccess.copy(alpha = 0.4f) else Color.Gray)
                ) {
                    Text(
                        text = if (isOn) "ON (SPD ${purifier.fanSpeed})" else "OFF",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOn) EmeraldSuccess else Color.Gray,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Speed Control Buttons (1..4)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Fan Speed:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for (spd in 1..4) {
                    val isSelected = isOn && purifier.fanSpeed == spd
                    OutlinedButton(
                        onClick = { onSetFanSpeed(spd) },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isSelected) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) CyanPrimary else FrostedGlassBorder
                        ),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("purifier_spd_$spd")
                    ) {
                        Text("$spd", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun MetricColumn(title: String, value: String) {
    Column {
        Text(text = title, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
