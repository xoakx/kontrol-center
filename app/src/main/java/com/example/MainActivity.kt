package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DesktopMac
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.AgentRfcScreen
import com.example.ui.screens.DisplayInputScreen
import com.example.ui.screens.HostsOverviewScreen
import com.example.ui.screens.HubConnectScreen
import com.example.ui.screens.ProvisioningModal
import com.example.ui.screens.TerminalScreen
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FrostedGlassBorder
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppTab
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

  private val viewModel: MainViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        HostManagerApp(viewModel = viewModel)
      }
    }
  }
}

@Composable
fun HostManagerApp(viewModel: MainViewModel) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val activeHost by viewModel.currentHost.collectAsStateWithLifecycle()
  val audioState by viewModel.audioRelayEngine.state.collectAsStateWithLifecycle()
  val rfcs by viewModel.rfcList.collectAsStateWithLifecycle()
  val pendingRfcCount = rfcs.count { it.status == "PENDING_APPROVAL" }

  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(uiState.quickNotice) {
    uiState.quickNotice?.let { notice ->
      snackbarHostState.showSnackbar(notice)
      viewModel.clearNotice()
    }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      // Dynamic Host Status Header Bar
      Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder),
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
      ) {
        Row(
          modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (activeHost?.isOnline == true) EmeraldSuccess else Color.Gray)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(
                text = activeHost?.name ?: "No Host Selected",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "${activeHost?.address ?: "127.0.0.1"} • Key Auth",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (audioState.isStreaming) {
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = EmeraldSuccess.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.4f))
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(12.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Audio 48k", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldSuccess)
                }
              }
            }

            Surface(
              shape = RoundedCornerShape(8.dp),
              color = CyanPrimary.copy(alpha = 0.15f),
              border = androidx.compose.foundation.BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.4f)),
              modifier = Modifier.clickable {
                viewModel.setProvisioningModalVisible(true)
              }
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("1-Click SSH", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = CyanPrimary)
              }
            }
          }
        }
      }
    },
    bottomBar = {
      // iOS-style Control Center Bottom Bar
      NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 8.dp,
        modifier = Modifier.border(1.dp, FrostedGlassBorder)
      ) {
        NavigationBarItem(
          selected = uiState.selectedTab == AppTab.OVERVIEW,
          onClick = { viewModel.selectTab(AppTab.OVERVIEW) },
          icon = { Icon(imageVector = Icons.Default.Layers, contentDescription = "Overview") },
          label = { Text("Overview", fontSize = 10.sp) },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = CyanPrimary,
            selectedTextColor = CyanPrimary,
            indicatorColor = CyanPrimary.copy(alpha = 0.15f)
          ),
          modifier = Modifier.testTag("nav_overview")
        )

        NavigationBarItem(
          selected = uiState.selectedTab == AppTab.TERMINAL,
          onClick = { viewModel.selectTab(AppTab.TERMINAL) },
          icon = { Icon(imageVector = Icons.Default.Terminal, contentDescription = "Terminal") },
          label = { Text("Terminal", fontSize = 10.sp) },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = CyanPrimary,
            selectedTextColor = CyanPrimary,
            indicatorColor = CyanPrimary.copy(alpha = 0.15f)
          ),
          modifier = Modifier.testTag("nav_terminal")
        )

        NavigationBarItem(
          selected = uiState.selectedTab == AppTab.DISPLAY,
          onClick = { viewModel.selectTab(AppTab.DISPLAY) },
          icon = { Icon(imageVector = Icons.Default.DesktopMac, contentDescription = "Display") },
          label = { Text("Display", fontSize = 10.sp) },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = CyanPrimary,
            selectedTextColor = CyanPrimary,
            indicatorColor = CyanPrimary.copy(alpha = 0.15f)
          ),
          modifier = Modifier.testTag("nav_display")
        )

        NavigationBarItem(
          selected = uiState.selectedTab == AppTab.CONNECT,
          onClick = { viewModel.selectTab(AppTab.CONNECT) },
          icon = { Icon(imageVector = Icons.Default.Sync, contentDescription = "Connect") },
          label = { Text("Connect", fontSize = 10.sp) },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = CyanPrimary,
            selectedTextColor = CyanPrimary,
            indicatorColor = CyanPrimary.copy(alpha = 0.15f)
          ),
          modifier = Modifier.testTag("nav_connect")
        )

        NavigationBarItem(
          selected = uiState.selectedTab == AppTab.AGENT,
          onClick = { viewModel.selectTab(AppTab.AGENT) },
          icon = {
            if (pendingRfcCount > 0) {
              BadgedBox(badge = { Badge { Text("$pendingRfcCount") } }) {
                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "Agent")
              }
            } else {
              Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "Agent")
            }
          },
          label = { Text("Agent", fontSize = 10.sp) },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.secondary,
            selectedTextColor = MaterialTheme.colorScheme.secondary,
            indicatorColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
          ),
          modifier = Modifier.testTag("nav_agent")
        )
      }
    }
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      Crossfade(targetState = uiState.selectedTab, label = "tab_crossfade") { tab ->
        when (tab) {
          AppTab.OVERVIEW -> HostsOverviewScreen(viewModel = viewModel)
          AppTab.TERMINAL -> TerminalScreen(viewModel = viewModel)
          AppTab.DISPLAY -> DisplayInputScreen(viewModel = viewModel)
          AppTab.CONNECT -> HubConnectScreen(viewModel = viewModel)
          AppTab.AGENT -> AgentRfcScreen(viewModel = viewModel)
        }
      }

      // Provisioning Modal
      if (uiState.showProvisioningModal) {
        ProvisioningModal(
          engine = viewModel.provisioningEngine,
          onDismiss = { viewModel.setProvisioningModalVisible(false) }
        )
      }
    }
  }
}

