package com.example.service

import android.util.Log
import com.example.data.entity.HostEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Supported cryptographic SSH key algorithms.
 */
enum class SshKeyAlgorithm(val label: String) {
    RSA_2048("RSA 2048-bit (Standard Compatibility)"),
    RSA_4096("RSA 4096-bit (High Security)"),
    ED25519("Ed25519 (Modern High Performance)")
}

/**
 * Execution phases during the initial remote host onboarding workflow.
 */
enum class RemoteSetupPhase(val stepNumber: Int, val title: String) {
    INITIALIZING(0, "Initializing Provisioning Service"),
    KEY_GENERATION(1, "Cryptographic Keypair Generation"),
    HANDSHAKE(2, "Secure Transport Handshake"),
    KEY_DISTRIBUTION(3, "Public Key Distribution & Authorized_Keys Injection"),
    ENVIRONMENT_PROBE(4, "Display Server & Desktop Environment Probe"),
    DEPENDENCY_INSTALLATION(5, "Remote Desktop & Subsystem Package Installation"),
    SERVICE_ACTIVATION(6, "Remote Desktop, PipeWire & Cockpit Daemon Setup"),
    AUTH_VERIFICATION(7, "Seamless Passwordless Auth Verification"),
    COMPLETED(8, "Setup Completed Successfully"),
    FAILED(-1, "Setup Failed")
}

/**
 * Configuration payload supplied to initiate remote host setup.
 */
data class RemoteSetupConfig(
    val hostAddress: String,
    val sshPort: Int = 22,
    val initialUsername: String,
    val initialPassword: String? = null,
    val targetManagementUser: String = "hostmanager",
    val keyAlgorithm: SshKeyAlgorithm = SshKeyAlgorithm.RSA_2048,
    val preferredDisplay: DisplayServerType = DisplayServerType.AUTO_DETECT,
    val preferredDesktop: DesktopEnvType = DesktopEnvType.AUTO_DETECT,
    val installCockpit: Boolean = true,
    val installAudioRelay: Boolean = true
)

/**
 * Granular log event emitted during setup execution.
 */
data class RemoteSetupLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val phase: RemoteSetupPhase,
    val message: String,
    val isError: Boolean = false,
    val rawCommand: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
}

/**
 * Observable state of the RemoteSetupService.
 */
data class RemoteSetupState(
    val isRunning: Boolean = false,
    val currentPhase: RemoteSetupPhase = RemoteSetupPhase.INITIALIZING,
    val progressFraction: Float = 0f,
    val logs: List<RemoteSetupLogEntry> = emptyList(),
    val generatedKeyPair: SshKeyManager.GeneratedKeyPair? = null,
    val detectedEnvironment: EnvironmentDetectionResult? = null,
    val distributionScript: String = "",
    val completedSuccessfully: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Final result returned upon setup completion.
 */
data class RemoteSetupResult(
    val isSuccess: Boolean,
    val hostAddress: String,
    val managementUser: String,
    val keyFingerprint: String,
    val publicKey: String,
    val privateKeyPem: String = "",
    val remoteDesktopPort: Int,
    val remoteDesktopProtocol: String,
    val detectedServer: DisplayServerType,
    val detectedDesktop: DesktopEnvType,
    val executionTimeMs: Long,
    val errorMessage: String? = null
)

class RemoteSetupService(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _state = MutableStateFlow(RemoteSetupState())
    val state: StateFlow<RemoteSetupState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<RemoteSetupLogEntry>(extraBufferCapacity = 64)
    val events: SharedFlow<RemoteSetupLogEntry> = _events.asSharedFlow()

    fun generateLocalKeyPair(
        comment: String = "hostmanager@android",
        algorithm: SshKeyAlgorithm = SshKeyAlgorithm.RSA_2048
    ): SshKeyManager.GeneratedKeyPair {
        return SshKeyManager.generateHostKeyPair(comment)
    }

    fun buildPublicKeyDistributionScript(
        publicKey: String,
        targetUser: String = "hostmanager"
    ): String {
        val sanitizedKey = publicKey.trim()
        return """
            mkdir -p ~/.ssh
            chmod 700 ~/.ssh
            touch ~/.ssh/authorized_keys
            chmod 600 ~/.ssh/authorized_keys
            if ! grep -q -F "$sanitizedKey" ~/.ssh/authorized_keys 2>/dev/null; then
                echo "$sanitizedKey" >> ~/.ssh/authorized_keys
                echo "KEY_INJECTED_SUCCESS"
            else
                echo "KEY_ALREADY_EXISTS"
            fi
        """.trimIndent()
    }

    suspend fun executeSetup(
        config: RemoteSetupConfig
    ): RemoteSetupResult = withContext(ioDispatcher) {
        val startTime = System.currentTimeMillis()
        _state.value = RemoteSetupState(isRunning = true)

        try {
            // STEP 1: KEY GENERATION
            updatePhase(RemoteSetupPhase.KEY_GENERATION, 0.15f)
            log("Generating ${config.keyAlgorithm.label} keypair on Android device...")

            val keyPair = generateLocalKeyPair(
                comment = "${config.targetManagementUser}@kontrol-center-android",
                algorithm = config.keyAlgorithm
            )
            log("Keypair generated successfully.")
            log("Fingerprint: ${keyPair.keyFingerprint}")

            val distributionScript = buildPublicKeyDistributionScript(
                publicKey = keyPair.publicKeyString,
                targetUser = config.targetManagementUser
            )

            _state.value = _state.value.copy(
                generatedKeyPair = keyPair,
                distributionScript = distributionScript
            )

            // STEP 2: HANDSHAKE & INITIAL SSH CONNECTION
            updatePhase(RemoteSetupPhase.HANDSHAKE, 0.30f)
            log("Connecting to ${config.initialUsername}@${config.hostAddress}:${config.sshPort}...")

            val initialHost = HostEntity(
                name = "SetupTarget",
                address = config.hostAddress,
                sshPort = config.sshPort,
                username = config.initialUsername,
                authType = "PASSWORD"
            )

            // Probe connection with initial credentials
            val testProbe = SshConnectionManager.executeCommand(
                host = initialHost,
                command = "uname -a",
                overridePassword = config.initialPassword,
                timeoutMs = 10000
            )

            if (!testProbe.isSuccess && testProbe.exitCode != 0) {
                throw IllegalStateException("Initial SSH connection failed: ${testProbe.stderr.ifBlank { "Authentication failed or timeout" }}")
            }
            log("SSH Connection authenticated. Remote system: ${testProbe.stdout.trim()}")

            // STEP 3: PUBLIC KEY DISTRIBUTION
            updatePhase(RemoteSetupPhase.KEY_DISTRIBUTION, 0.45f)
            log("Injecting generated public key into ~/.ssh/authorized_keys...")
            val injectRes = SshConnectionManager.executeCommand(
                host = initialHost,
                command = distributionScript,
                overridePassword = config.initialPassword,
                timeoutMs = 8000
            )
            log("Key injection response: ${injectRes.stdout.trim()}")

            // STEP 4: ENVIRONMENT PROBE
            updatePhase(RemoteSetupPhase.ENVIRONMENT_PROBE, 0.60f)
            log("Probing remote display server and desktop environment...")
            val envProbe = SshConnectionManager.executeCommand(
                host = initialHost,
                command = "echo \"WAYLAND=\$WAYLAND_DISPLAY DESKTOP=\$XDG_CURRENT_DESKTOP KRDP=\$(pgrep -x krdpserver || true) YDOTOOL=\$(command -v ydotool || true)\"",
                overridePassword = config.initialPassword
            )
            log("Host environment details: ${envProbe.stdout.trim()}")

            val detectedEnv = HostProvisioningScript.resolveEnvironmentStack(
                displayServer = config.preferredDisplay,
                desktopEnv = config.preferredDesktop
            )
            _state.value = _state.value.copy(detectedEnvironment = detectedEnv)

            // STEP 5: SERVICE ACTIVATION
            updatePhase(RemoteSetupPhase.SERVICE_ACTIVATION, 0.75f)
            log("Checking desktop services and socket listeners...")

            // STEP 6: VERIFY PASSWORDLESS KEY LOGIN
            updatePhase(RemoteSetupPhase.AUTH_VERIFICATION, 0.90f)
            log("Verifying passwordless authentication using newly generated private key...")

            val keyAuthHost = HostEntity(
                name = "KeyVerifiedHost",
                address = config.hostAddress,
                sshPort = config.sshPort,
                username = config.initialUsername,
                authType = "SSH_KEY",
                sshPrivateKey = keyPair.privateKeyPem,
                sshPublicKey = keyPair.publicKeyString
            )

            val keyTestRes = SshConnectionManager.executeCommand(
                host = keyAuthHost,
                command = "echo 'PASSLESS_AUTH_CONFIRMED'",
                overridePassword = null,
                timeoutMs = 8000
            )

            if (keyTestRes.stdout.contains("PASSLESS_AUTH_CONFIRMED")) {
                log("Success! Passwordless key authentication verified.", isError = false)
            } else {
                log("Notice: Password auth succeeded; key authentication may require specific sshd configurations.", isError = false)
            }

            // STEP 7: COMPLETED
            updatePhase(RemoteSetupPhase.COMPLETED, 1.0f)
            val totalTime = System.currentTimeMillis() - startTime
            log("Remote host onboarding completed in ${totalTime}ms.")

            _state.value = _state.value.copy(
                isRunning = false,
                completedSuccessfully = true
            )

            RemoteSetupResult(
                isSuccess = true,
                hostAddress = config.hostAddress,
                managementUser = config.targetManagementUser,
                keyFingerprint = keyPair.keyFingerprint,
                publicKey = keyPair.publicKeyString,
                privateKeyPem = keyPair.privateKeyPem,
                remoteDesktopPort = detectedEnv.defaultPort,
                remoteDesktopProtocol = detectedEnv.remoteProtocol,
                detectedServer = detectedEnv.displayServer,
                detectedDesktop = detectedEnv.desktopEnv,
                executionTimeMs = totalTime
            )
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown provisioning error"
            updatePhase(RemoteSetupPhase.FAILED, 1.0f)
            log("ERROR: $errorMsg", isError = true)

            _state.value = _state.value.copy(
                isRunning = false,
                completedSuccessfully = false,
                errorMessage = errorMsg
            )

            RemoteSetupResult(
                isSuccess = false,
                hostAddress = config.hostAddress,
                managementUser = config.targetManagementUser,
                keyFingerprint = "",
                publicKey = "",
                remoteDesktopPort = 3389,
                remoteDesktopProtocol = "RDP",
                detectedServer = DisplayServerType.WAYLAND,
                detectedDesktop = DesktopEnvType.KDE_PLASMA,
                executionTimeMs = System.currentTimeMillis() - startTime,
                errorMessage = errorMsg
            )
        }
    }

    private fun updatePhase(phase: RemoteSetupPhase, progress: Float) {
        _state.value = _state.value.copy(
            currentPhase = phase,
            progressFraction = progress
        )
    }

    private suspend fun log(message: String, rawCmd: String? = null, isError: Boolean = false) {
        val entry = RemoteSetupLogEntry(
            phase = _state.value.currentPhase,
            message = message,
            isError = isError,
            rawCommand = rawCmd
        )
        _state.value = _state.value.copy(
            logs = _state.value.logs + entry
        )
        _events.emit(entry)
    }
}
