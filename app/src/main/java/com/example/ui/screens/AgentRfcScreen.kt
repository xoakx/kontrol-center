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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.RfcItemEntity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FrostedGlassBorder
import com.example.ui.theme.RoseError
import com.example.ui.theme.TerminalBlack
import com.example.ui.theme.TerminalGreen
import com.example.viewmodel.MainViewModel

@Composable
fun AgentRfcScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val rfcs by viewModel.rfcList.collectAsStateWithLifecycle()
    val agentEngine = viewModel.agentRfcEngine
    val agentState by agentEngine.state.collectAsStateWithLifecycle()
    val activeHost by viewModel.currentHost.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Pending RFCs, 1: History, 2: Agent Chat & Diagnostic
    var userPromptText by remember { mutableStateOf("") }
    var inspectingRfc by remember { mutableStateOf<RfcItemEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Agent RFC Approvals",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Automated server change governance for ${activeHost?.name ?: "Host"}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Tabs
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder)
        ) {
            val pendingCount = rfcs.count { it.status == "PENDING_APPROVAL" }
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Pending ($pendingCount)", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("History", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Agent Chat", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
            }
        }

        // Content
        when (selectedTab) {
            0 -> {
                // Pending RFCs
                val pendingRfcs = rfcs.filter { it.status == "PENDING_APPROVAL" }
                if (pendingRfcs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("No pending RFCs", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Your host configuration is up to date and in compliance.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(pendingRfcs) { rfc ->
                            RfcCard(
                                rfc = rfc,
                                onApprove = { viewModel.approveAndExecuteRfc(rfc) },
                                onReject = { viewModel.rejectRfc(rfc) },
                                onInspect = { inspectingRfc = rfc }
                            )
                        }
                    }
                }
            }
            1 -> {
                // Historic RFCs
                val pastRfcs = rfcs.filter { it.status != "PENDING_APPROVAL" }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(pastRfcs) { rfc ->
                        PastRfcCard(rfc = rfc, onInspect = { inspectingRfc = rfc })
                    }
                }
            }
            2 -> {
                // Agent Chat & Autonomous RFC proposal engine
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Suggested prompts
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(agentState.suggestedPrompts) { prompt ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder),
                                modifier = Modifier.clickable {
                                    viewModel.submitAgentPrompt(prompt)
                                }
                            ) {
                                Text(
                                    text = prompt,
                                    fontSize = 11.sp,
                                    color = CyanPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Chat History
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(agentState.chatHistory) { msg ->
                            val isAgent = msg.sender == "AGENT"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isAgent) Arrangement.Start else Arrangement.End
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isAgent) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f) else CyanPrimary,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder),
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = if (isAgent) "🤖 AI System Agent" else "You",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (isAgent) CyanPrimary else Color.Black
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = msg.text,
                                            fontSize = 13.sp,
                                            color = if (isAgent) MaterialTheme.colorScheme.onSurface else Color.Black
                                        )

                                        msg.rfcAttachment?.let { rfc ->
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFF0F172A),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { selectedTab = 0 }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(text = "${rfc.rfcNumber}: ${rfc.title}", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                                    Text(text = "Review ->", fontSize = 11.sp, color = CyanPrimary, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (agentState.isThinking) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = CyanPrimary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Agent evaluating system state & drafting RFC...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    // Prompt Input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = userPromptText,
                            onValueChange = { userPromptText = it },
                            placeholder = { Text("Ask agent to audit or configure host...") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("agent_prompt_input")
                        )

                        IconButton(
                            onClick = {
                                if (userPromptText.isNotBlank()) {
                                    viewModel.submitAgentPrompt(userPromptText)
                                    userPromptText = ""
                                }
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(CyanPrimary)
                                .size(48.dp)
                                .testTag("agent_send_button")
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.Black)
                        }
                    }
                }
            }
        }

        // RFC Inspection Modal
        inspectingRfc?.let { rfc ->
            RfcDetailDialog(
                rfc = rfc,
                onDismiss = { inspectingRfc = null },
                onApprove = {
                    viewModel.approveAndExecuteRfc(rfc)
                    inspectingRfc = null
                },
                onReject = {
                    viewModel.rejectRfc(rfc)
                    inspectingRfc = null
                }
            )
        }
    }
}

@Composable
fun RfcCard(
    rfc: RfcItemEntity,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onInspect: () -> Unit
) {
    val impactColor = when (rfc.impact) {
        "LOW" -> EmeraldSuccess
        "MEDIUM" -> AmberWarning
        else -> RoseError
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FrostedGlassBorder, RoundedCornerShape(20.dp))
            .clickable { onInspect() }
            .testTag("rfc_card_${rfc.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = rfc.rfcNumber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = impactColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${rfc.impact} IMPACT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = impactColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = "Awaiting Approval",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AmberWarning
                )
            }

            Text(
                text = rfc.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = rfc.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Shell Commands Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(TerminalBlack)
                    .padding(10.dp)
            ) {
                Text(
                    text = rfc.proposedCommands,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalGreen,
                    maxLines = 3
                )
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reject")
                }

                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("approve_rfc_${rfc.id}")
                ) {
                    Icon(imageVector = Icons.Default.Gavel, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Approve & Execute", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PastRfcCard(
    rfc: RfcItemEntity,
    onInspect: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FrostedGlassBorder, RoundedCornerShape(16.dp))
            .clickable { onInspect() }
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = rfc.rfcNumber, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = rfc.status,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = if (rfc.status == "EXECUTED") EmeraldSuccess else RoseError
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = rfc.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }

            Icon(imageVector = Icons.Default.Code, contentDescription = "Logs", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun RfcDetailDialog(
    rfc: RfcItemEntity,
    onDismiss: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder),
            modifier = Modifier.fillMaxWidth(0.96f)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${rfc.rfcNumber}: ${rfc.title}", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Text(rfc.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Text("Proposed Shell Script (Commands to Run):", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TerminalBlack)
                        .padding(10.dp)
                ) {
                    Text(
                        text = rfc.proposedCommands,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TerminalGreen
                    )
                }

                Text("Rollback Safety Script (In case of failure):", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E1B2E))
                        .padding(10.dp)
                ) {
                    Text(
                        text = rfc.rollbackScript,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFFE2E8F0)
                    )
                }

                if (rfc.executionLog.isNotEmpty()) {
                    Text("Execution Log:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0F172A))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = rfc.executionLog,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = CyanPrimary
                        )
                    }
                }

                if (rfc.status == "PENDING_APPROVAL") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) {
                            Text("Reject")
                        }
                        Button(
                            onClick = onApprove,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Text("Approve & Execute")
                        }
                    }
                }
            }
        }
    }
}
