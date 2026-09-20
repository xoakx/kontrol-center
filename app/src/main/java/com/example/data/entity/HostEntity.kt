package com.example.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a remote workstation or server monitored by Kontrol Center.
 * Upgraded in Room DB v3 with dual-path mesh networking (Tailscale + LAN),
 * Arcade SRE telemetry endpoints, and SecureVault alias binding.
 */
@Entity(tableName = "hosts")
data class HostEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val address: String, // Fallback/primary address (e.g. 192.168.1.161 or 100.111.123.93)
    val sshPort: Int = 22,
    val username: String = "hostmanager",
    val authType: String = "SSH_KEY", // SSH_KEY or PASSWORD
    val sshPublicKey: String = "",
    val sshPrivateKey: String = "", // Legacy field; new keys reside in SecureVault
    val isProvisioned: Boolean = false,
    val osType: String = "Ubuntu Linux",
    val cockpitPort: Int = 9090,
    val webminPort: Int = 10000,
    val vncPort: Int = 5900,
    val audioPort: Int = 4713,
    val lastConnected: Long = System.currentTimeMillis(),
    val isOnline: Boolean = true,
    val cpuUsage: Int = 24, // percentage
    val memoryUsage: Int = 42, // percentage
    val diskUsage: Int = 58, // percentage
    val temperatureC: Int = 48,
    val uptimeString: String = "4d 12h 30m",

    // ==========================================
    // ROOM DB V3: ZERO-TRUST MESH & ARCADE SRE
    // ==========================================

    @ColumnInfo(name = "tailscaleAddress", defaultValue = "''")
    val tailscaleAddress: String = "", // E.g., "100.111.123.93" or "fml.ts.net"

    @ColumnInfo(name = "lanAddress", defaultValue = "''")
    val lanAddress: String = "", // E.g., "192.168.1.161"

    @ColumnInfo(name = "connectionMode", defaultValue = "'AUTO'")
    val connectionMode: String = "AUTO", // "AUTO", "TAILSCALE", "LAN"

    @ColumnInfo(name = "activeEndpoint", defaultValue = "''")
    val activeEndpoint: String = "", // E.g., "http://100.111.123.93:8899"

    @ColumnInfo(name = "lastLatencyMs", defaultValue = "-1")
    val lastLatencyMs: Long = -1L, // Round-trip latency in ms; -1 if unknown

    @ColumnInfo(name = "arcadePort", defaultValue = "8899")
    val arcadePort: Int = 8899, // FastAPI SRE telemetry & command port

    @ColumnInfo(name = "eveboxPort", defaultValue = "5636")
    val eveboxPort: Int = 5636, // Suricata NetSec EveBox UI port

    @ColumnInfo(name = "vaultAlias", defaultValue = "''")
    val vaultAlias: String = "", // Alias identifier inside SecureVault

    @ColumnInfo(name = "apiKey", defaultValue = "''")
    val apiKey: String = "", // Arcade API auth token or reference

    @ColumnInfo(name = "connectionState", defaultValue = "'DISCONNECTED'")
    val connectionState: String = "DISCONNECTED" // "DISCONNECTED", "CONNECTING", "CONNECTED_TAILSCALE", "CONNECTED_LAN", "FAILED"
)
