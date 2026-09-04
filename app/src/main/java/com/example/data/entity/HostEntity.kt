package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hosts")
data class HostEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val address: String,
    val sshPort: Int = 22,
    val username: String = "hostmanager",
    val authType: String = "SSH_KEY", // SSH_KEY or PASSWORD
    val sshPublicKey: String = "",
    val sshPrivateKey: String = "",
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
    val uptimeString: String = "4d 12h 30m"
)
