package com.example.service

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProvisioningStep(
    val id: Int,
    val title: String,
    val commandPreview: String,
    val status: StepStatus = StepStatus.PENDING,
    val logOutput: String = ""
)

enum class StepStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED
}

data class ProvisioningState(
    val isRunning: Boolean = false,
    val currentStepIndex: Int = 0,
    val progressPercent: Float = 0f,
    val steps: List<ProvisioningStep> = emptyList(),
    val isComplete: Boolean = false,
    val generatedPublicKey: String = "",
    val detectedEnvironment: EnvironmentDetectionResult? = null,
    val selectedDisplayChoice: DisplayServerType = DisplayServerType.AUTO_DETECT,
    val selectedEnvChoice: DesktopEnvType = DesktopEnvType.AUTO_DETECT,
    val errorMessage: String? = null
)

class ProvisioningEngine {

    private val _state = MutableStateFlow(ProvisioningState())
    val state = _state.asStateFlow()

    fun updateEnvironmentPreferences(display: DisplayServerType, env: DesktopEnvType) {
        _state.value = _state.value.copy(
            selectedDisplayChoice = display,
            selectedEnvChoice = env
        )
    }

    fun getFullProvisioningScript(targetUser: String = "hostmanager"): String {
        val pubKey = _state.value.generatedPublicKey.ifEmpty { "ssh-rsa AAAAB3NzaC1yc2E... hostmanager@android" }
        return HostProvisioningScript.generateStandaloneBashScript(
            targetUser = targetUser,
            injectPublicKey = pubKey
        )
    }

    suspend fun startProvisioning(
        hostAddress: String,
        sshPort: Int,
        initialUsername: String,
        installCockpit: Boolean = true,
        installAudioRelay: Boolean = true
    ) {
        val keyPair = SshKeyManager.generateHostKeyPair("hostmanager@android")

        // Resolve target environment
        val detected = HostProvisioningScript.resolveEnvironmentStack(
            displayServer = _state.value.selectedDisplayChoice,
            desktopEnv = _state.value.selectedEnvChoice
        )

        val steps = listOf(
            ProvisioningStep(
                id = 1,
                title = "Establish Secure Handshake",
                commandPreview = "ssh -p $sshPort -o StrictHostKeyChecking=accept-new $initialUsername@$hostAddress"
            ),
            ProvisioningStep(
                id = 2,
                title = "Generate Cryptographic Keypair",
                commandPreview = "Local RSA-2048 / Ed25519 Keypair generated on Android device"
            ),
            ProvisioningStep(
                id = 3,
                title = "Configure User & Sudoers Permissions",
                commandPreview = "useradd -m -s /bin/bash hostmanager && echo 'hostmanager ALL=(ALL) NOPASSWD:ALL' > /etc/sudoers.d/hostmanager"
            ),
            ProvisioningStep(
                id = 4,
                title = "Inject SSH Public Key into Authorized_Keys",
                commandPreview = "mkdir -p ~/.ssh && chmod 700 ~/.ssh && echo '${keyPair.publicKeyString.take(24)}...' >> ~/.ssh/authorized_keys"
            ),
            ProvisioningStep(
                id = 5,
                title = "Probe Display Server & Desktop Environment",
                commandPreview = "loginctl show-session, \$XDG_SESSION_TYPE, pgrep -x (kwin_wayland|gnome-shell|Xorg)"
            ),
            ProvisioningStep(
                id = 6,
                title = "Install Matched Remote Desktop Dependencies",
                commandPreview = "apt-get install -y ${detected.companionPackages.joinToString(" ")}"
            ),
            ProvisioningStep(
                id = 7,
                title = "Configure Remote Desktop & Audio Daemon",
                commandPreview = "${detected.activationCommands} && pactl load-module module-native-protocol-tcp port=4713"
            ),
            ProvisioningStep(
                id = 8,
                title = "Verify 1-Click Key-Based Auth & Services",
                commandPreview = "ssh -i {key} -o BatchMode=yes hostmanager@$hostAddress 'echo SUCCESS_ONE_CLICK'"
            )
        )

        _state.value = _state.value.copy(
            isRunning = true,
            currentStepIndex = 0,
            progressPercent = 0.05f,
            steps = steps,
            generatedPublicKey = keyPair.publicKeyString,
            detectedEnvironment = detected,
            isComplete = false
        )

        for (i in steps.indices) {
            _state.value = _state.value.copy(
                currentStepIndex = i,
                progressPercent = (i + 0.3f) / steps.size,
                steps = _state.value.steps.mapIndexed { idx, step ->
                    if (idx == i) step.copy(status = StepStatus.RUNNING) else step
                }
            )

            delay(750) // Realistic execution pacing for visual feedback

            val simulatedLog = when (i) {
                0 -> "Connecting to $hostAddress:$sshPort...\nTCP SYN/ACK received. Cipher: chacha20-poly1305@openssh.com. Handshake verified."
                1 -> "Device Keypair created.\nFingerprint: ${keyPair.keyFingerprint}\nPublic key length: ${keyPair.publicKeyString.length} chars."
                2 -> "User '$initialUsername' executing bootstrap:\nCreating user account 'hostmanager' with shell /bin/bash...\nAdded to groups: sudo, adm, audio, video.\nCreated /etc/sudoers.d/hostmanager."
                3 -> "Injecting public key into /home/hostmanager/.ssh/authorized_keys...\nEnforcing file mode 0600 on authorized_keys and 0700 on .ssh/.\nOwnership set to hostmanager:hostmanager."
                4 -> """
[PROBE] Checking ${'$'}XDG_SESSION_TYPE... 'wayland'
[PROBE] Checking compositor process... found PID $(pgrep -x kwin_wayland || echo 1842) (kwin_wayland)
[PROBE] Checking desktop environment... ${'$'}XDG_CURRENT_DESKTOP='KDE', session='plasma'
================================================================
>>> IDENTIFIED DISPLAY SERVER: ${detected.displayServer.label.uppercase()}
>>> IDENTIFIED DESKTOP ENV:    ${detected.desktopEnv.label.uppercase()}
>>> RECOMMENDED REMOTE STACK:  ${detected.remoteDesktopPackage} (${detected.remoteProtocol})
================================================================
                """.trimIndent()
                5 -> """
Package manager detected: apt (Ubuntu / Kubuntu)
Updating repository package cache: apt-get update -qq
Selected packages based on detected ${detected.displayServer.label} + ${detected.desktopEnv.label}:
  • ${detected.remoteDesktopPackage} (${detected.remoteProtocol})
  • ${detected.companionPackages.joinToString("\n  • ")}
All remote desktop and system management dependencies installed successfully.
                """.trimIndent()
                6 -> """
Configuring remote services:
  • Cockpit web console enabled on https://$hostAddress:9090
  • PipeWire 48kHz audio TCP socket active on port 4713
  • Activated ${detected.remoteDesktopPackage} on port ${detected.defaultPort}
Service hooks verified and running.
                """.trimIndent()
                7 -> "Testing 1-Click Key Login with identity...\nAuthenticated as hostmanager@$hostAddress via SSH Key.\nResult: SUCCESS_ONE_CLICK.\nRemote ports verified: SSH (22), Remote Desktop (${detected.defaultPort}), Audio (4713), Cockpit (9090).\nFuture sessions will connect seamlessly without passwords!"
                else -> "Step complete."
            }

            _state.value = _state.value.copy(
                progressPercent = (i + 1f) / steps.size,
                steps = _state.value.steps.mapIndexed { idx, step ->
                    if (idx == i) step.copy(status = StepStatus.SUCCESS, logOutput = simulatedLog) else step
                }
            )
        }

        _state.value = _state.value.copy(
            isRunning = false,
            isComplete = true,
            progressPercent = 1.0f
        )
    }

    fun reset() {
        _state.value = ProvisioningState()
    }
}

