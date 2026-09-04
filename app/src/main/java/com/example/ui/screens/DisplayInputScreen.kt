package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.DesktopMac
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.TouchApp
import com.example.service.DisplayMode
import com.example.service.VirtualMouseButton
import com.example.service.VirtualSpecialKey
import com.example.service.HidBackendType
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FrostedGlassBorder
import com.example.viewmodel.MainViewModel

@Composable
fun DisplayInputScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val displayEngine = viewModel.screenDisplayEngine
    val displayState by displayEngine.state.collectAsStateWithLifecycle()
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: Screen Viewer, 1: Trackpad & Input
    var remoteTextToSend by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Mode Selector: Screen View vs Trackpad
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder)
        ) {
            TabRow(
                selectedTabIndex = selectedSubTab,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.DesktopMac, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Screen / Monitor", fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Mouse, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Trackpad & Input", fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
            }
        }

        if (selectedSubTab == 0) {
            // SCREEN VIEWER / VIRTUAL MONITOR
            ScreenViewerPane(
                displayEngine = displayEngine,
                virtualInputService = viewModel.virtualInputService,
                displayState = displayState
            )
        } else {
            // TRACKPAD & KEYBOARD INPUT (Powered by VirtualInputService)
            TrackpadInputPane(
                displayEngine = displayEngine,
                virtualInputService = viewModel.virtualInputService,
                displayState = displayState,
                remoteText = remoteTextToSend,
                onRemoteTextChange = { remoteTextToSend = it },
                onSendText = {
                    if (remoteTextToSend.isNotEmpty()) {
                        viewModel.virtualInputService.sendTextInput(remoteTextToSend)
                        displayEngine.sendKeyPress("STRING: $remoteTextToSend")
                        remoteTextToSend = ""
                    }
                }
            )
        }
    }
}

@Composable
fun ScreenViewerPane(
    displayEngine: com.example.service.ScreenDisplayEngine,
    virtualInputService: com.example.service.VirtualInputService,
    displayState: com.example.service.ScreenDisplayState
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FrostedGlassBorder, RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Screen Controls Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(EmeraldSuccess)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${displayState.resolution} • ${displayState.fps} FPS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (displayState.displayMode == DisplayMode.VIRTUAL_MONITOR) CyanPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder)
                    ) {
                        Text(
                            text = if (displayState.displayMode == DisplayMode.VIRTUAL_MONITOR) "Virtual 2nd Monitor" else "Host Mirror",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (displayState.displayMode == DisplayMode.VIRTUAL_MONITOR) CyanPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (displayState.displayMode == DisplayMode.MIRROR_HOST) {
                                        displayEngine.setDisplayMode(DisplayMode.VIRTUAL_MONITOR)
                                    } else {
                                        displayEngine.setDisplayMode(DisplayMode.MIRROR_HOST)
                                    }
                                }
                        )
                    }
                }
            }

            // Canvas Display simulating Host FrameBuffer Stream
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            virtualInputService.sendMouseMove(dragAmount.x, dragAmount.y)
                            displayEngine.sendPointerMove(dragAmount.x, dragAmount.y)
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Desktop Background Wallpaper
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))
                        )
                    )

                    // Top Desktop Panel / Dock
                    drawRect(
                        color = Color(0x99000000),
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, 24.dp.toPx())
                    )

                    // Simulated Active Window
                    drawRoundRect(
                        color = Color(0xFF1E293B),
                        topLeft = Offset(size.width * 0.1f, size.height * 0.18f),
                        size = Size(size.width * 0.8f, size.height * 0.65f),
                        cornerRadius = CornerRadius(12f, 12f)
                    )

                    // Window Title Bar
                    drawRoundRect(
                        color = Color(0xFF334155),
                        topLeft = Offset(size.width * 0.1f, size.height * 0.18f),
                        size = Size(size.width * 0.8f, 20.dp.toPx()),
                        cornerRadius = CornerRadius(12f, 12f)
                    )

                    // Window buttons
                    drawCircle(Color(0xFFEF4444), radius = 4.dp.toPx(), center = Offset(size.width * 0.14f, size.height * 0.18f + 10.dp.toPx()))
                    drawCircle(Color(0xFFF59E0B), radius = 4.dp.toPx(), center = Offset(size.width * 0.18f, size.height * 0.18f + 10.dp.toPx()))
                    drawCircle(Color(0xFF10B981), radius = 4.dp.toPx(), center = Offset(size.width * 0.22f, size.height * 0.18f + 10.dp.toPx()))

                    // Simulated Virtual Mouse Cursor
                    val normX = (displayState.cursorPosition.x / 1920f) * size.width
                    val normY = (displayState.cursorPosition.y / 1080f) * size.height

                    // Cursor pointer arrow
                    drawCircle(
                        color = CyanPrimary,
                        radius = 6.dp.toPx(),
                        center = Offset(normX, normY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = Offset(normX, normY)
                    )
                }

                // Overlay Status Tag
                Text(
                    text = displayState.activeWindowTitle,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                )

                Text(
                    text = "Tap & Drag to control mouse • Pinch zoom supported",
                    fontSize = 10.sp,
                    color = Color.LightGray.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                )
            }

            // Quick display toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayState.lastInputFeedback,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            virtualInputService.sendMouseButton(VirtualMouseButton.LEFT)
                            displayEngine.sendClick(false)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Left Click", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            virtualInputService.sendMouseButton(VirtualMouseButton.RIGHT)
                            displayEngine.sendClick(true)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Right Click", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun TrackpadInputPane(
    displayEngine: com.example.service.ScreenDisplayEngine,
    virtualInputService: com.example.service.VirtualInputService,
    displayState: com.example.service.ScreenDisplayState,
    remoteText: String,
    onRemoteTextChange: (String) -> Unit,
    onSendText: () -> Unit
) {
    val hidState by virtualInputService.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // HID backend status bar
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (hidState.isInitialized) EmeraldSuccess else CyanPrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Input: ${hidState.activeBackend.displayName}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Quick backend toggle chip
                Text(
                    text = if (hidState.activeBackend == HidBackendType.WAYLAND_YDOTOOL) "Switch to X11" else "Switch to Wayland",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            val nextBackend = if (hidState.activeBackend == HidBackendType.WAYLAND_YDOTOOL)
                                HidBackendType.X11_XDOTEXT else HidBackendType.WAYLAND_YDOTOOL
                            virtualInputService.setBackend(nextBackend)
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Touchpad Surface
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, FrostedGlassBorder, RoundedCornerShape(20.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        virtualInputService.sendMouseMove(dragAmount.x * 1.5f, dragAmount.y * 1.5f)
                        displayEngine.sendPointerMove(dragAmount.x * 1.5f, dragAmount.y * 1.5f)
                    }
                }
                .testTag("remote_trackpad_surface")
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Mouse,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Touchpad Surface",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Swipe to move cursor • Tap or use buttons to click",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Left, Middle, and Right Click Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    virtualInputService.sendMouseButton(VirtualMouseButton.LEFT)
                    displayEngine.sendClick(false)
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxSize()
                    .border(1.dp, FrostedGlassBorder, RoundedCornerShape(14.dp))
                    .testTag("left_click_button")
            ) {
                Text("LEFT", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            }

            Button(
                onClick = {
                    virtualInputService.sendMouseButton(VirtualMouseButton.MIDDLE)
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxSize()
                    .border(1.dp, FrostedGlassBorder, RoundedCornerShape(14.dp))
                    .testTag("middle_click_button")
            ) {
                Text("MID", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            }

            Button(
                onClick = {
                    virtualInputService.sendMouseButton(VirtualMouseButton.RIGHT)
                    displayEngine.sendClick(true)
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxSize()
                    .border(1.dp, FrostedGlassBorder, RoundedCornerShape(14.dp))
                    .testTag("right_click_button")
            ) {
                Text("RIGHT", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            }
        }

        // Special Host Shortcuts Row (Esc, Super/Meta, Tab, Enter)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val specialKeys = listOf(
                "ESC" to VirtualSpecialKey.ESCAPE,
                "SUPER" to VirtualSpecialKey.SUPER,
                "TAB" to VirtualSpecialKey.TAB,
                "ENTER" to VirtualSpecialKey.ENTER,
                "BKSP" to VirtualSpecialKey.BACKSPACE
            )
            for ((label, key) in specialKeys) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { virtualInputService.sendSpecialKey(key) }
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Media Controller Bar
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { displayEngine.sendMediaAction("PREV") }) {
                    Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Previous")
                }
                IconButton(onClick = { displayEngine.sendMediaAction("PLAY_PAUSE") }) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { displayEngine.sendMediaAction("NEXT") }) {
                    Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next")
                }
                IconButton(onClick = { displayEngine.sendMediaAction("VOL_MUTE") }) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.VolumeMute, contentDescription = "Mute")
                }
                IconButton(onClick = { displayEngine.sendMediaAction("VOL_DOWN") }) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.VolumeDown, contentDescription = "Volume Down")
                }
                IconButton(onClick = { displayEngine.sendMediaAction("VOL_UP") }) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Volume Up")
                }
            }
        }

        // Remote Keyboard Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = remoteText,
                onValueChange = onRemoteTextChange,
                placeholder = { Text("Type text to send to host...") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = onSendText,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(52.dp)
            ) {
                Text("Send")
            }
        }
    }
}
