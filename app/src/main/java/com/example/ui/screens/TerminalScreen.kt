package com.example.ui.screens

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.HostEntity
import com.example.service.TerminalLineType
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FrostedGlassBorder
import com.example.ui.theme.RoseError
import com.example.ui.theme.TerminalBlack
import com.example.ui.theme.TerminalGreen
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.TerminalViewModel

@Composable
fun TerminalScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val terminalViewModel = remember(viewModel) {
        TerminalViewModel(viewModel.terminalEngine) { viewModel.currentHost.value }
    }
    val activeHost by viewModel.currentHost.collectAsStateWithLifecycle()

    TerminalScreen(
        terminalViewModel = terminalViewModel,
        activeHost = activeHost,
        modifier = modifier
    )
}

@Composable
fun TerminalScreen(
    terminalViewModel: TerminalViewModel,
    activeHost: HostEntity? = null,
    modifier: Modifier = Modifier
) {
    val terminalState by terminalViewModel.terminalState.collectAsStateWithLifecycle()
    val uiState by terminalViewModel.uiState.collectAsStateWithLifecycle()
    val isCtrlActive by terminalViewModel.isCtrlActive.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on output change if enabled
    LaunchedEffect(terminalState.lines.size) {
        if (uiState.isAutoScrollEnabled && terminalState.lines.isNotEmpty()) {
            listState.animateScrollToItem(terminalState.lines.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBlack)
    ) {
        // Top terminal status bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
            border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = TerminalGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SSH: ${activeHost?.username ?: "hostmanager"}@${activeHost?.address ?: "127.0.0.1"}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (terminalState.isExecuting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = TerminalGreen
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(
                        onClick = { terminalViewModel.clearConsole() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ClearAll,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Terminal Output Screen
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            items(terminalState.lines) { line ->
                val fallbackColor = when (line.type) {
                    TerminalLineType.INPUT -> CyanPrimary
                    TerminalLineType.OUTPUT -> Color(0xFFE2E8F0)
                    TerminalLineType.SYSTEM -> Color(0xFF94A3B8)
                    TerminalLineType.SUCCESS -> EmeraldSuccess
                    TerminalLineType.ERROR -> RoseError
                }
                Text(
                    text = line.annotatedText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = uiState.fontSizeSp.sp,
                    lineHeight = (uiState.fontSizeSp + 5).sp,
                    color = fallbackColor,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }

        // Quick Snippets Drawer
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F172A))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val quickCmds = listOf(
                "uptime",
                "free -h",
                "df -h",
                "docker ps",
                "nvidia-smi",
                "systemctl status ssh",
                "ss -tulpn",
                "cat /etc/os-release"
            )
            items(quickCmds) { cmd ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .clickable { terminalViewModel.executeCommand(cmd) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = cmd,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = CyanPrimary
                    )
                }
            }
        }

        // Termux Accessory Key Bar
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0F1D))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val keys = listOf("CTRL", "ESC", "TAB", "CTRL+C", "UP", "DOWN", "|", "~", "-", "/", "CLEAR")
            items(keys) { key ->
                val isCtrlKey = (key == "CTRL")
                val isKeyActive = isCtrlKey && isCtrlActive
                val bgColor = if (isKeyActive) CyanPrimary else Color(0xFF1E293B)
                val textColor = if (isKeyActive) Color.Black else Color.White

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = bgColor,
                    modifier = Modifier
                        .clickable { terminalViewModel.sendQuickKey(key) }
                        .testTag("quick_key_$key")
                ) {
                    Text(
                        text = key,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0B1120))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = terminalState.currentInput,
                onValueChange = { terminalViewModel.updateInput(it) },
                placeholder = {
                    Text(
                        text = "Enter shell command...",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color.White
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { terminalViewModel.executeCommand() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanPrimary,
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedContainerColor = Color(0xFF0F172A),
                    unfocusedContainerColor = Color(0xFF0F172A)
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("terminal_input_field")
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = { terminalViewModel.executeCommand() },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyanPrimary)
                    .size(46.dp)
                    .testTag("terminal_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Command",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
