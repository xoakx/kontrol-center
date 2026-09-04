package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.ClipboardDao
import com.example.data.dao.CommandSnippetDao
import com.example.data.dao.HostDao
import com.example.data.dao.RfcDao
import com.example.data.entity.ClipboardItemEntity
import com.example.data.entity.CommandSnippetEntity
import com.example.data.entity.HostEntity
import com.example.data.entity.RfcItemEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        HostEntity::class,
        RfcItemEntity::class,
        CommandSnippetEntity::class,
        ClipboardItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hostDao(): HostDao
    abstract fun rfcDao(): RfcDao
    abstract fun commandSnippetDao(): CommandSnippetDao
    abstract fun clipboardDao(): ClipboardDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "host_manager_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }

            private suspend fun populateInitialData(db: AppDatabase) {
                val hostDao = db.hostDao()
                val rfcDao = db.rfcDao()
                val snippetDao = db.commandSnippetDao()
                val clipDao = db.clipboardDao()

                // Initial primary host
                val primaryHost = HostEntity(
                    id = 1,
                    name = "Mac Mini / Linux Lab",
                    address = "192.168.1.150",
                    sshPort = 22,
                    username = "andrew",
                    authType = "SSH_KEY",
                    sshPublicKey = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIOr8vP9n3qK7+m8J8sN5h2f9V8k1j0l2h3g4f5 hostmanager@android",
                    isProvisioned = true,
                    osType = "Ubuntu 24.04 LTS",
                    cockpitPort = 9090,
                    webminPort = 10000,
                    vncPort = 5900,
                    audioPort = 4713,
                    isOnline = true,
                    cpuUsage = 18,
                    memoryUsage = 36,
                    diskUsage = 52,
                    temperatureC = 44,
                    uptimeString = "12d 8h 15m"
                )
                val secondaryHost = HostEntity(
                    id = 2,
                    name = "Arch Workstation",
                    address = "192.168.1.200",
                    sshPort = 2222,
                    username = "hostmanager",
                    authType = "PASSWORD",
                    sshPublicKey = "",
                    isProvisioned = false,
                    osType = "Arch Linux",
                    cockpitPort = 9090,
                    webminPort = 10000,
                    vncPort = 5900,
                    audioPort = 4713,
                    isOnline = true,
                    cpuUsage = 42,
                    memoryUsage = 61,
                    diskUsage = 74,
                    temperatureC = 58,
                    uptimeString = "2d 4h 10m"
                )
                hostDao.insertHost(primaryHost)
                hostDao.insertHost(secondaryHost)

                // Initial RFC proposals from AI Agent
                rfcDao.insertRfc(
                    RfcItemEntity(
                        id = 1,
                        hostId = 1,
                        rfcNumber = "RFC-042",
                        title = "Enable PipeWire TCP Audio Relay & Low-Latency Sink",
                        description = "Configure PipeWire network module to broadcast 48kHz audio to Android client and open firewall port 4713.",
                        proposedCommands = "sudo ufw allow 4713/tcp comment 'PipeWire Audio Relay'\nsudo systemctl restart pipewire-pulse",
                        rollbackScript = "sudo ufw delete allow 4713/tcp\nsudo systemctl restart pipewire",
                        impact = "LOW",
                        status = "PENDING_APPROVAL"
                    )
                )
                rfcDao.insertRfc(
                    RfcItemEntity(
                        id = 2,
                        hostId = 1,
                        rfcNumber = "RFC-043",
                        title = "Clean Dangling Docker Images & Prune System Cache",
                        description = "System volume usage exceeds 50%. Free up ~8.4GB of unreferenced layer data.",
                        proposedCommands = "docker system prune -af --volumes\nsudo journalctl --vacuum-time=7d",
                        rollbackScript = "# Docker prune is irreversible; non-destructive to active containers",
                        impact = "MEDIUM",
                        status = "PENDING_APPROVAL"
                    )
                )
                rfcDao.insertRfc(
                    RfcItemEntity(
                        id = 3,
                        hostId = 1,
                        rfcNumber = "RFC-040",
                        title = "One-Click Automated SSH Key Injection & Sudo Setup",
                        description = "Provision dedicated passwordless key login for mobile host companion.",
                        proposedCommands = "mkdir -p ~/.ssh && chmod 700 ~/.ssh && echo 'ssh-ed25519 AAA...' >> ~/.ssh/authorized_keys",
                        rollbackScript = "sed -i '/hostmanager@android/d' ~/.ssh/authorized_keys",
                        impact = "HIGH",
                        status = "EXECUTED",
                        executionLog = "[OK] Key injected into ~/.ssh/authorized_keys\n[OK] Permissions set: 0600\n[OK] Sudoers entry validated.",
                        executedAt = System.currentTimeMillis() - 86400000L
                    )
                )

                rfcDao.insertRfc(
                    RfcItemEntity(
                        id = 4,
                        hostId = 1,
                        rfcNumber = "RFC-044",
                        title = "Auto-Detect Display Server & Deploy Matched Remote Desktop (krdp)",
                        description = "Identify Wayland vs X11 and desktop environment (KDE Plasma vs GNOME). Install and enable krdp and PipeWire audio stream.",
                        proposedCommands = "sudo bash -c 'echo \"Session: \$XDG_SESSION_TYPE\"; apt-get update -qq && apt-get install -y krdp krfb pipewire-pulse wl-clipboard && systemctl --user enable --now krdp.service'",
                        rollbackScript = "sudo apt-get remove -y krdp krfb",
                        impact = "MEDIUM",
                        status = "PENDING_APPROVAL"
                    )
                )

                // Command snippets
                snippetDao.insertSnippets(
                    listOf(
                        CommandSnippetEntity(
                            title = "Display Server & Desktop Probe",
                            command = "echo \"Display: \$XDG_SESSION_TYPE | DE: \$XDG_CURRENT_DESKTOP | Compositor: \$(pgrep -x kwin_wayland || pgrep -x gnome-shell || echo Xorg)\"",
                            category = "System",
                            isFavorite = true
                        ),
                        CommandSnippetEntity(
                            title = "System Telemetry Overview",
                            command = "uptime && free -h && df -h /",
                            category = "System",
                            isFavorite = true
                        ),
                        CommandSnippetEntity(
                            title = "GPU / VRAM Status",
                            command = "nvidia-smi || radeontop -d - || echo 'No discrete GPU detected'",
                            category = "System",
                            isFavorite = true
                        ),
                        CommandSnippetEntity(
                            title = "Active Network Sockets",
                            command = "ss -tulpn | grep -E '(LISTEN|ESTAB)'",
                            category = "Network",
                            isFavorite = false
                        ),
                        CommandSnippetEntity(
                            title = "Docker Running Containers",
                            command = "docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'",
                            category = "Containers",
                            isFavorite = true
                        ),
                        CommandSnippetEntity(
                            title = "Start WayVNC Screen Server",
                            command = "wayvnc 0.0.0.0 5900",
                            category = "Media",
                            isFavorite = false
                        ),
                        CommandSnippetEntity(
                            title = "Start Cockpit Web Console",
                            command = "sudo systemctl enable --now cockpit.socket",
                            category = "System",
                            isFavorite = true
                        )
                    )
                )

                // Initial clipboard items
                clipDao.insertClipboard(
                    ClipboardItemEntity(
                        hostId = 1,
                        content = "git clone https://github.com/torvalds/linux.git",
                        direction = "HOST_TO_PHONE"
                    )
                )
                clipDao.insertClipboard(
                    ClipboardItemEntity(
                        hostId = 1,
                        content = "curl -fsSL https://get.docker.com | sh",
                        direction = "PHONE_TO_HOST"
                    )
                )
            }
        }
    }
}
