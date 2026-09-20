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
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.data.api.RfcItem
import com.example.data.entity.RfcItemEntity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FrostedGlassBorder
import com.example.ui.theme.RoseError
import com.example.ui.theme.TerminalBlack
import com.example.ui.theme.TerminalGreen
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.RfcFilter
import com.example.viewmodel.RfcViewModel

/**
 * Modernized Agent RFC Screen supporting [RfcViewModel] for autonomous RFC governance.
 */
@Composable
fun AgentRfcScreen(
    rfcViewModel: RfcViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by rfcViewModel.uiState.collectAsStateWithLifecycle()
    var inspectingRfc by remember { mutableStateOf<RfcItem?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Header
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
                    text = "Automated server change governance & SRE proposals",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Offline Banner
        if (uiState.isOffline) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AmberWarning.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, AmberWarning.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.CloudOff, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Viewing cached proposals. Changes will sync when mesh connection is restored.",
                        fontSize = 11.sp,
                        color = AmberWarning
                    )
                }
            }
        }

        // Error message banner
        uiState.errorMessage?.let { errorMsg ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = RoseError.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, RoseError.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = errorMsg, fontSize = 12.sp, color = RoseError, modifier = Modifier.weight(1f))
                    IconButton(onClick = { rfcViewModel.dismissError() }, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss", tint = RoseError, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // User notice banner
        uiState.userNotice?.let { notice ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = EmeraldSuccess.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = notice, fontSize = 12.sp, color = EmeraldSuccess, modifier = Modifier.weight(1f))
                    IconButton(onClick = { rfcViewModel.clearNotice() }, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = EmeraldSuccess, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Tabs
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder)
        ) {
            val pendingCount = uiState.pendingRfcs.size
            TabRow(
                selectedTabIndex = uiState.selectedTab,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { rfcViewModel.setSelectedTab(0) },
                    text = { Text("Pending ($pendingCount)", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { rfcViewModel.setSelectedTab(1) },
                    text = { Text("History (${uiState.historyRfcs.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
            }
        }

        // Search & Filter Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { rfcViewModel.setSearchQuery(it) },
                placeholder = { Text("Search RFCs...", fontSize = 12.sp) },
                singleLine = true,
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanPrimary,
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedContainerColor = Color(0xFF0F172A),
                    unfocusedContainerColor = Color(0xFF0F172A)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            )

            // Risk filters
            val risks = listOf(null, "LOW", "MEDIUM", "HIGH", "CRITICAL")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(risks) { risk ->
                    val isSelected = uiState.selectedRiskFilter == risk
                    val chipColor = when (risk) {
                        "LOW" -> EmeraldSuccess
                        "MEDIUM" -> AmberWarning
                        "HIGH", "CRITICAL" -> RoseError
                        else -> CyanPrimary
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) chipColor.copy(alpha = 0.25f) else Color(0xFF1E293B),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) chipColor else Color.Transparent),
                        modifier = Modifier.clickable { rfcViewModel.setRiskFilter(if (isSelected) null else risk) }
                    ) {
                        Text(
                            text = risk ?: "ALL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) chipColor else Color.LightGray,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // RFC Cards List
        if (uiState.isLoading && uiState.filteredRfcs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CyanPrimary)
            }
        } else if (uiState.filteredRfcs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("No RFC proposals matching filter", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Your server configurations are compliant.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.filteredRfcs) { rfc ->
                    RfcItemCard(
                        rfc = rfc,
                        isVoting = (uiState.votingInProgressId == rfc.id),
                        onApprove = { rfcViewModel.approveRfc(rfc) },
                        onReject = { rfcViewModel.rejectRfc(rfc) },
                        onInspect = { inspectingRfc = rfc }
                    )
                }
            }
        }

        // Detail Inspection Modal
        inspectingRfc?.let { rfc ->
            RfcItemDetailDialog(
                rfc = rfc,
                onDismiss = { inspectingRfc = null },
                onApprove = {
                    rfcViewModel.approveRfc(rfc)
                    inspectingRfc = null
                },
                onReject = {
                    rfcViewModel.rejectRfc(rfc)
                    inspectingRfc = null
                }
            )
        }
    }
}

/**
 * Backwards-compatible overload of [AgentRfcScreen] for [MainViewModel].
 */
@Composable
fun AgentRfcScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val rfcs by viewModel.rfcList.collectAsStateWithLifecycle()
    val agentEngine = viewModel.agentRfcEngine
    val agentState by agentEngine.state.collectAsStateWithLifecycle()
    val activeHost by viewModel.currentHost.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
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
            val pendingCount = rfcs.count { it.status == "PENDING_APPROVAL" || it.status == "PROPOSED" }
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

        when (selectedTab) {
            0 -> {
                val pendingRfcs = rfcs.filter { it.status == "PENDING_APPROVAL" || it.status == "PROPOSED" }
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
                val pastRfcs = rfcs.filter { it.status != "PENDING_APPROVAL" && it.status != "PROPOSED" }
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
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(agentState.suggestedPrompts) { prompt ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder),
                                modifier = Modifier.clickable { viewModel.submitAgentPrompt(prompt) }
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
fun RfcItemCard(
    rfc: RfcItem,
    isVoting: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onInspect: () -> Unit
) {
    val impactColor = when (rfc.riskLevel.uppercase()) {
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
                            text = rfc.id,
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
                            text = "${rfc.riskLevel.uppercase()} IMPACT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = impactColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = rfc.status,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when (rfc.status.uppercase()) {
                        "APPROVED", "EXECUTED" -> EmeraldSuccess
                        "REJECTED", "FAILED" -> RoseError
                        else -> AmberWarning
                    }
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

            // Monospace Commands / Diff Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(TerminalBlack)
                    .padding(10.dp)
            ) {
                val commandText = if (rfc.proposedSteps.isEmpty()) {
                    "(Advisory proposal - no executable command steps)"
                } else {
                    rfc.proposedSteps.joinToString("\n")
                }
                Text(
                    text = commandText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalGreen,
                    maxLines = 3
                )
            }

            // Action Buttons
            if (rfc.status.equals("PROPOSED", ignoreCase = true) || rfc.status.equals("PENDING_APPROVAL", ignoreCase = true)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        enabled = !isVoting,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reject")
                    }

                    Button(
                        onClick = onApprove,
                        enabled = !isVoting,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("approve_rfc_${rfc.id}")
                    ) {
                        if (isVoting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(imageVector = Icons.Default.Gavel, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Approve & Execute", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RfcItemDetailDialog(
    rfc: RfcItem,
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
                    Text("${rfc.id}: ${rfc.title}", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Text(rfc.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Text("Proposed Shell Steps / Execution Diff:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TerminalBlack)
                        .padding(10.dp)
                ) {
                    val stepContent = if (rfc.proposedSteps.isEmpty()) {
                        "(Advisory proposal - no executable command steps)"
                    } else {
                        rfc.proposedSteps.joinToString("\n")
                    }
                    Text(
                        text = stepContent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TerminalGreen
                    )
                }

                rfc.taskId?.let { taskId ->
                    Text("Execution Task ID: $taskId", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = CyanPrimary)
                }

                if (rfc.status.equals("PROPOSED", ignoreCase = true) || rfc.status.equals("PENDING_APPROVAL", ignoreCase = true)) {
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

                if (rfc.status == "PENDING_APPROVAL" || rfc.status == "PROPOSED") {
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
