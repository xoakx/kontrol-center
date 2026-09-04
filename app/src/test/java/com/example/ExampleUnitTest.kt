package com.example

import com.example.service.DesktopEnvType
import com.example.service.DisplayServerType
import com.example.service.HostProvisioningScript
import com.example.service.RemoteSetupConfig
import com.example.service.RemoteSetupResult
import com.example.service.RemoteSetupService
import com.example.service.SshKeyAlgorithm
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testWaylandKdeResolutionUsesKrdp() {
        val result = HostProvisioningScript.resolveEnvironmentStack(
            displayServer = DisplayServerType.WAYLAND,
            desktopEnv = DesktopEnvType.KDE_PLASMA
        )
        assertEquals("krdp", result.remoteDesktopPackage)
        assertEquals(3389, result.defaultPort)
        assertTrue(result.companionPackages.contains("krdp"))
        assertTrue(result.companionPackages.contains("wl-clipboard"))
    }

    @Test
    fun testWaylandGnomeResolutionUsesGnomeRemoteDesktop() {
        val result = HostProvisioningScript.resolveEnvironmentStack(
            displayServer = DisplayServerType.WAYLAND,
            desktopEnv = DesktopEnvType.GNOME
        )
        assertEquals("gnome-remote-desktop", result.remoteDesktopPackage)
        assertEquals(3389, result.defaultPort)
        assertTrue(result.companionPackages.contains("gnome-remote-desktop"))
    }

    @Test
    fun testX11ResolutionUsesX11vnc() {
        val result = HostProvisioningScript.resolveEnvironmentStack(
            displayServer = DisplayServerType.X11,
            desktopEnv = DesktopEnvType.XFCE
        )
        assertEquals("x11vnc", result.remoteDesktopPackage)
        assertEquals(5900, result.defaultPort)
        assertTrue(result.companionPackages.contains("x11vnc"))
        assertTrue(result.companionPackages.contains("xclip"))
    }

    @Test
    fun testGeneratedScriptContainsDisplayDetectionKeywords() {
        val script = HostProvisioningScript.generateStandaloneBashScript()
        assertTrue(script.contains("XDG_SESSION_TYPE"))
        assertTrue(script.contains("kwin_wayland"))
        assertTrue(script.contains("krdp"))
        assertTrue(script.contains("gnome-remote-desktop"))
        assertTrue(script.contains("x11vnc"))
        assertTrue(script.contains("pipewire-pulse"))
    }

    @Test
    fun testRemoteSetupServiceKeyGeneration() {
        val service = RemoteSetupService()
        val keyPair = service.generateLocalKeyPair(
            comment = "test@workstation",
            algorithm = SshKeyAlgorithm.RSA_2048
        )
        assertNotNull(keyPair)
        assertTrue(keyPair.publicKeyString.startsWith("ssh-rsa") || keyPair.publicKeyString.startsWith("ssh-ed25519"))
        assertTrue(keyPair.keyFingerprint.startsWith("SHA256:"))
        assertTrue(keyPair.privateKeyPem.isNotBlank())
    }

    @Test
    fun testRemoteSetupServicePublicKeyDistributionScript() {
        val service = RemoteSetupService()
        val dummyPub = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQC... test@android"
        val script = service.buildPublicKeyDistributionScript(
            publicKey = dummyPub,
            targetUser = "hostmanager"
        )
        assertTrue(script.contains("chmod 0700"))
        assertTrue(script.contains("chmod 0600"))
        assertTrue(script.contains("authorized_keys"))
        assertTrue(script.contains("KEY_INJECTED_SUCCESS"))
        assertTrue(script.contains("chown -R") && script.contains("TARGET_USER"))
    }

    @Test
    fun testRemoteSetupServiceCreateConfiguredHostEntity() {
        val service = RemoteSetupService()
        val config = RemoteSetupConfig(
            hostAddress = "192.168.1.55",
            sshPort = 2222,
            initialUsername = "ubuntu",
            targetManagementUser = "hostmanager",
            installCockpit = true,
            installAudioRelay = true
        )
        val dummyResult = RemoteSetupResult(
            isSuccess = true,
            hostAddress = "192.168.1.55",
            managementUser = "hostmanager",
            keyFingerprint = "SHA256:abcd...",
            publicKey = "ssh-rsa AAAAB3NzaC...",
            remoteDesktopPort = 3389,
            remoteDesktopProtocol = "RDP",
            detectedServer = DisplayServerType.WAYLAND,
            detectedDesktop = DesktopEnvType.KDE_PLASMA,
            executionTimeMs = 3200
        )
        val hostEntity = service.createConfiguredHostEntity(
            name = "Dual RTX Rig",
            config = config,
            result = dummyResult
        )

        assertEquals("Dual RTX Rig", hostEntity.name)
        assertEquals("192.168.1.55", hostEntity.address)
        assertEquals(2222, hostEntity.sshPort)
        assertEquals("hostmanager", hostEntity.username)
        assertEquals("SSH_KEY", hostEntity.authType)
        assertTrue(hostEntity.isProvisioned)
        assertTrue(hostEntity.isOnline)
        assertEquals(3389, hostEntity.vncPort)
        assertEquals(9090, hostEntity.cockpitPort)
        assertEquals("KDE Plasma 6 (Wayland)", hostEntity.osType)
    }

    @Test
    fun testAudioStreamServiceScriptGeneration() {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val audioService = com.example.service.AudioStreamService(scope)
        val script = audioService.generateHostAudioCaptureScript(targetPort = 4713, sampleRate = 48000)

        assertTrue(script.contains("module-native-protocol-tcp"))
        assertTrue(script.contains("port=4713"))
        assertTrue(script.contains("parec"))
        assertTrue(script.contains("rate=48000"))
        assertTrue(script.contains("channels=2"))
    }

    @Test
    fun testAudioRelayEngineStateAndPresets() {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val engine = com.example.service.AudioRelayEngine(scope)

        assertEquals(com.example.service.AudioRelayMode.HOST_TO_PHONE, engine.state.value.mode)
        assertEquals(20, engine.state.value.bufferLatencyMs)
        assertEquals(48000, engine.state.value.sampleRateHz)

        engine.setLatency(10)
        assertEquals(10, engine.state.value.bufferLatencyMs)

        engine.setVolumeGain(1.2f)
        assertEquals(1.2f, engine.state.value.volumeGain, 0.01f)

        engine.toggleMute()
        assertTrue(engine.state.value.isMuted)

        engine.setTargetHost("10.0.0.12", 4713)
        assertEquals("10.0.0.12", engine.state.value.targetHost)
        assertEquals(4713, engine.state.value.targetPort)

        engine.setMode(com.example.service.AudioRelayMode.PHONE_TO_HOST)
        assertEquals(com.example.service.AudioRelayMode.PHONE_TO_HOST, engine.state.value.mode)
    }

    @Test
    fun testClipboardSyncToolCommands() {
        val waylandTool = com.example.service.HostClipboardTool.WAYLAND_WL_COPY
        val x11Tool = com.example.service.HostClipboardTool.X11_XCLIP

        assertEquals("wl-copy", waylandTool.copyCmd)
        assertEquals("wl-paste -n", waylandTool.pasteCmd)
        assertEquals("xclip -selection clipboard", x11Tool.copyCmd)
        assertEquals("xclip -o -selection clipboard", x11Tool.pasteCmd)
    }

    @Test
    fun testClipboardBase64SafePipeline() {
        val rawText = "echo 'Testing 1 2 3'; rm -rf /tmp/test && \$VAR"
        val encoded = java.util.Base64.getEncoder().encodeToString(rawText.toByteArray(Charsets.UTF_8))
        assertNotNull(encoded)

        val waylandPipeline = "echo '$encoded' | base64 -d | wl-copy"
        assertTrue(waylandPipeline.contains("base64 -d"))
        assertTrue(waylandPipeline.contains("wl-copy"))

        val x11Pipeline = "echo '$encoded' | base64 -d | xclip -selection clipboard"
        assertTrue(x11Pipeline.contains("xclip -selection clipboard"))
    }

    @Test
    fun testClipboardSyncModeBehaviors() {
        val modes = com.example.service.ClipboardSyncMode.values()
        assertEquals(4, modes.size)
        assertTrue(modes.contains(com.example.service.ClipboardSyncMode.BIDIRECTIONAL))
        assertTrue(modes.contains(com.example.service.ClipboardSyncMode.PHONE_TO_HOST_ONLY))
        assertTrue(modes.contains(com.example.service.ClipboardSyncMode.HOST_TO_PHONE_ONLY))
        assertTrue(modes.contains(com.example.service.ClipboardSyncMode.MANUAL))
    }
}

