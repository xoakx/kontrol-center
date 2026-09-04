package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.entity.ClipboardItemEntity
import com.example.data.entity.HostEntity
import com.example.data.entity.RfcItemEntity
import com.example.data.repository.HostRepository
import com.example.service.AudioRelayEngine
import com.example.service.AudioRelayMode
import com.example.service.DisplayMode
import com.example.service.ProvisioningEngine
import com.example.service.RemoteSetupConfig
import com.example.service.RemoteSetupService
import com.example.service.ScreenDisplayEngine
import com.example.service.TerminalEngine
import com.example.service.AgentRfcEngine
import com.example.service.DisplayServerType
import com.example.service.HostClipboardTool
import com.example.service.RemoteClipboardService
import com.example.service.ClipboardSyncMode
import com.example.service.VirtualInputService
import com.example.service.SshConnectionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppTab {
    OVERVIEW,   // iOS-style Control Center host tiles & health
    TERMINAL,   // Termux-inspired interactive shell
    DISPLAY,    // RDP/VNC screen monitor + Trackpad & input
    CONNECT,    // KDE Connect: Clipboard, File transfer, Audio relay, Cockpit
    AGENT       // Gemini AI RFC approvals & autonomous triage
}

data class RemoteFileItem(
    val name: String,
    val path: String,
    val sizeString: String,
    val isDirectory: Boolean,
    val permissions: String,
    val modifiedTime: String
)

data class MainUiState(
    val selectedTab: AppTab = AppTab.OVERVIEW,
    val selectedHostId: Int = 1,
    val showAddHostDialog: Boolean = false,
    val showProvisioningModal: Boolean = false,
    val showRfcDetailModal: RfcItemEntity? = null,
    val showFileTransferModal: Boolean = false,
    val fileTransferDirection: String = "PUSH", // PUSH or PULL
    val selectedDirectoryPath: String = "/home/andrew",
    val fileItems: List<RemoteFileItem> = emptyList(),
    val quickNotice: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application, viewModelScope)
    val repository = HostRepository(
        hostDao = db.hostDao(),
        rfcDao = db.rfcDao(),
        snippetDao = db.commandSnippetDao(),
        clipboardDao = db.clipboardDao()
    )

    // Core engines
    val remoteSetupService = RemoteSetupService()
    val provisioningEngine = ProvisioningEngine()
    val audioRelayEngine = AudioRelayEngine(viewModelScope)
    val screenDisplayEngine = ScreenDisplayEngine()
    val terminalEngine = TerminalEngine(viewModelScope)
    val agentRfcEngine = AgentRfcEngine()
    val virtualInputService = VirtualInputService(viewModelScope)
    val remoteClipboardService = RemoteClipboardService(
        context = application.applicationContext,
        clipboardDao = db.clipboardDao(),
        scope = viewModelScope
    )

    // Room DB streams
    val hostsList: StateFlow<List<HostEntity>> = repository.allHosts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rfcList: StateFlow<List<RfcItemEntity>> = repository.allRfcs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val snippetList = repository.allSnippets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val currentHost: StateFlow<HostEntity?> = combine(hostsList, _uiState) { hosts, ui ->
        hosts.find { it.id == ui.selectedHostId } ?: hosts.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val clipboardHistory: StateFlow<List<ClipboardItemEntity>> = currentHost
        .flatMapLatest { host ->
            if (host != null) repository.getClipboardForHost(host.id)
            else flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        screenDisplayEngine.bindInputService(virtualInputService)
        loadDirectoryFiles("/home/andrew")

        viewModelScope.launch {
            currentHost.collect { host ->
                if (host != null) {
                    audioRelayEngine.setTargetHost(host.address, 4713)
                    val isWayland = host.osType.contains("Wayland", ignoreCase = true) || host.osType.contains("KDE", ignoreCase = true)
                    val displayType = if (isWayland) {
                        DisplayServerType.WAYLAND
                    } else {
                        DisplayServerType.X11
                    }
                    terminalEngine.attachHost(host)
                    virtualInputService.attachHost(host, isWayland)
                    remoteClipboardService.attachHost(host, displayType)
                    screenDisplayEngine.attachHost(host)
                }
            }
        }
    }

    fun selectTab(tab: AppTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun selectHost(hostId: Int) {
        _uiState.value = _uiState.value.copy(selectedHostId = hostId)
    }

    fun setAddHostDialogVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(showAddHostDialog = visible)
    }

    fun setProvisioningModalVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(showProvisioningModal = visible)
    }

    fun setRfcDetailModal(rfc: RfcItemEntity?) {
        _uiState.value = _uiState.value.copy(showRfcDetailModal = rfc)
    }

    fun setFileTransferModal(visible: Boolean, direction: String = "PUSH") {
        _uiState.value = _uiState.value.copy(
            showFileTransferModal = visible,
            fileTransferDirection = direction
        )
    }

    fun showNotice(text: String) {
        _uiState.value = _uiState.value.copy(quickNotice = text)
    }

    fun clearNotice() {
        _uiState.value = _uiState.value.copy(quickNotice = null)
    }

    fun addNewHostAndStartProvision(
        name: String,
        address: String,
        sshPort: Int,
        username: String,
        authPassword: String,
        installCockpit: Boolean,
        installAudioRelay: Boolean
    ) {
        viewModelScope.launch {
            val newHost = HostEntity(
                name = name.ifBlank { "Linux Host ($address)" },
                address = address.ifBlank { "192.168.1.100" },
                sshPort = sshPort,
                username = username.ifBlank { "hostmanager" },
                authType = "SSH_KEY",
                isProvisioned = false,
                osType = "Linux Workstation"
            )
            val insertedId = repository.insertHost(newHost).toInt()
            _uiState.value = _uiState.value.copy(
                selectedHostId = insertedId,
                showAddHostDialog = false,
                showProvisioningModal = true
            )

            val setupConfig = RemoteSetupConfig(
                hostAddress = newHost.address,
                sshPort = newHost.sshPort,
                initialUsername = username,
                initialPassword = authPassword.ifBlank { null },
                targetManagementUser = "hostmanager",
                installCockpit = installCockpit,
                installAudioRelay = installAudioRelay
            )

            val setupResult = remoteSetupService.executeSetup(setupConfig)

            val detectedResult = setupResult.detectedServer
            val updatedHost = newHost.copy(
                id = insertedId,
                isProvisioned = setupResult.isSuccess,
                isOnline = setupResult.isSuccess,
                username = username,
                authType = if (setupResult.privateKeyPem.isNotBlank()) "SSH_KEY" else "PASSWORD",
                sshPublicKey = setupResult.publicKey,
                sshPrivateKey = setupResult.privateKeyPem,
                osType = "${setupResult.detectedDesktop.label} (${setupResult.detectedServer.label})",
                vncPort = setupResult.remoteDesktopPort,
                cockpitPort = if (installCockpit) 9090 else 0
            )
            repository.updateHost(updatedHost)
            repository.updateProvisioned(insertedId, setupResult.isSuccess)
            if (setupResult.isSuccess) {
                terminalEngine.attachHost(updatedHost)
                showNotice("Host '$name' provisioned successfully! 1-Click SSH key active on port ${updatedHost.vncPort}.")
            } else {
                showNotice("Host setup warning: ${setupResult.errorMessage ?: "Check credentials"}")
            }
        }
    }

    // RFC Approval / Execution
    fun approveAndExecuteRfc(rfc: RfcItemEntity) {
        viewModelScope.launch {
            val host = currentHost.value
            if (host != null) {
                showNotice("Executing ${rfc.rfcNumber} on ${host.address}...")
                val result = agentRfcEngine.executeApprovedRfc(rfc, host)
                val status = if (result.isSuccess) "EXECUTED" else "FAILED"
                repository.updateRfcStatus(
                    rfcId = rfc.id,
                    status = status,
                    log = if (result.isSuccess) {
                        "[SSH EXECUTION SUCCESS]\n${result.stdout.trim()}"
                    } else {
                        "[SSH EXECUTION FAILED]\n${result.stderr.trim().ifEmpty { result.stdout.trim() }}"
                    },
                    executedAt = System.currentTimeMillis()
                )
                setRfcDetailModal(null)
                showNotice(if (result.isSuccess) "${rfc.rfcNumber} Executed Successfully!" else "${rfc.rfcNumber} Failed (Exit code ${result.exitCode})")
            } else {
                showNotice("Error: No target host selected for RFC execution.")
            }
        }
    }

    fun rejectRfc(rfc: RfcItemEntity) {
        viewModelScope.launch {
            repository.updateRfcStatus(
                rfcId = rfc.id,
                status = "REJECTED",
                log = "User rejected proposed changes.",
                executedAt = null
            )
            setRfcDetailModal(null)
            showNotice("${rfc.rfcNumber} Rejected.")
        }
    }

    // Clipboard Sync
    fun pushClipboardToHost(text: String) {
        if (text.isBlank()) return
        remoteClipboardService.pushTextToHost(text, isAuto = false)
        showNotice("Pushed to Host clipboard via ${remoteClipboardService.state.value.targetTool.name}")
    }

    fun fetchHostClipboard() {
        remoteClipboardService.pullTextFromHost(isAuto = false)
        showNotice("Pulling remote host clipboard...")
    }

    fun toggleClipboardAutoSync() {
        val current = remoteClipboardService.state.value.isAutoSyncEnabled
        if (current) {
            remoteClipboardService.stopMonitoring()
            showNotice("Clipboard Auto-Sync Paused")
        } else {
            remoteClipboardService.startMonitoring()
            showNotice("Clipboard Auto-Sync Resumed")
        }
    }

    fun setClipboardSyncMode(mode: ClipboardSyncMode) {
        remoteClipboardService.setSyncMode(mode)
        showNotice("Sync Mode: ${mode.label}")
    }

    fun setClipboardTargetTool(tool: HostClipboardTool) {
        remoteClipboardService.setTargetTool(tool)
        showNotice("Clipboard Tool: ${tool.label}")
    }

    fun deleteClipboardItem(item: ClipboardItemEntity) {
        viewModelScope.launch {
            repository.deleteClipboard(item)
            showNotice("Clipboard entry deleted")
        }
    }

    fun clearClipboardHistory() {
        viewModelScope.launch {
            val host = currentHost.value ?: return@launch
            repository.clearClipboard(host.id)
            showNotice("Clipboard history cleared")
        }
    }

    // File Manager (SFTP)
    fun loadDirectoryFiles(path: String) {
        val host = currentHost.value
        if (host == null) {
            _uiState.value = _uiState.value.copy(
                selectedDirectoryPath = path,
                fileItems = emptyList()
            )
            return
        }

        viewModelScope.launch {
            try {
                val remoteList = SshConnectionManager.listRemoteFiles(host, path)
                val mapped = remoteList.map { entry ->
                    val sizeBytes = entry["sizeBytes"] as Long
                    val formattedSize = when {
                        sizeBytes > 1024 * 1024 * 1024 -> "%.1f GB".format(sizeBytes / (1024.0 * 1024.0 * 1024.0))
                        sizeBytes > 1024 * 1024 -> "%.1f MB".format(sizeBytes / (1024.0 * 1024.0))
                        sizeBytes > 1024 -> "%.1f KB".format(sizeBytes / 1024.0)
                        else -> "$sizeBytes B"
                    }
                    RemoteFileItem(
                        name = entry["name"] as String,
                        path = entry["path"] as String,
                        sizeString = if (entry["isDirectory"] as Boolean) "" else formattedSize,
                        isDirectory = entry["isDirectory"] as Boolean,
                        permissions = entry["permissions"] as String,
                        modifiedTime = entry["modifiedTime"] as String
                    )
                }

                val parentItem = if (path != "/" && path.isNotBlank()) {
                    val parentPath = path.substringBeforeLast("/").ifBlank { "/" }
                    listOf(RemoteFileItem(".. (Parent Directory)", parentPath, "", true, "drwxr-xr-x", ""))
                } else emptyList()

                _uiState.value = _uiState.value.copy(
                    selectedDirectoryPath = path,
                    fileItems = parentItem + mapped
                )
            } catch (e: Exception) {
                showNotice("SFTP error on $path: ${e.message}")
            }
        }
    }

    fun uploadSimulatedFile(fileName: String, sizeStr: String) {
        val host = currentHost.value
        val currentDir = _uiState.value.selectedDirectoryPath
        if (host != null) {
            viewModelScope.launch {
                try {
                    val dummyContent = "Uploaded via Kontrol Center at ${java.util.Date()}\n"
                    val inputStream = java.io.ByteArrayInputStream(dummyContent.toByteArray())
                    SshConnectionManager.uploadFile(host, inputStream, "$currentDir/$fileName".replace("//", "/"))
                    loadDirectoryFiles(currentDir)
                    _uiState.value = _uiState.value.copy(showFileTransferModal = false)
                    showNotice("File '$fileName' uploaded to $currentDir via SFTP")
                } catch (e: Exception) {
                    showNotice("Upload failed: ${e.message}")
                }
            }
        }
    }

    fun submitAgentPrompt(prompt: String) {
        val host = currentHost.value ?: return
        viewModelScope.launch {
            agentRfcEngine.submitUserPrompt(
                prompt = prompt,
                host = host,
                onRfcGenerated = { rfc ->
                    repository.insertRfc(rfc)
                }
            )
        }
    }
}
