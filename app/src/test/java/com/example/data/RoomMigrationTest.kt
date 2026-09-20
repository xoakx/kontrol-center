package com.example.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.MIGRATION_1_2
import com.example.data.database.MIGRATION_2_3
import com.example.data.entity.HostEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomMigrationTest {

    private lateinit var context: Context
    private val testDbName = "migration_test.db"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(testDbName)
    }

    private fun createV1Database(): SupportSQLiteDatabase {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create v1 tables: hosts and rfc_items
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `hosts` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `address` TEXT NOT NULL,
                            `sshPort` INTEGER NOT NULL,
                            `username` TEXT NOT NULL,
                            `authType` TEXT NOT NULL,
                            `sshPublicKey` TEXT NOT NULL,
                            `sshPrivateKey` TEXT NOT NULL,
                            `isProvisioned` INTEGER NOT NULL,
                            `osType` TEXT NOT NULL,
                            `cockpitPort` INTEGER NOT NULL,
                            `webminPort` INTEGER NOT NULL,
                            `vncPort` INTEGER NOT NULL,
                            `audioPort` INTEGER NOT NULL,
                            `lastConnected` INTEGER NOT NULL,
                            `isOnline` INTEGER NOT NULL,
                            `cpuUsage` INTEGER NOT NULL,
                            `memoryUsage` INTEGER NOT NULL,
                            `diskUsage` INTEGER NOT NULL,
                            `temperatureC` INTEGER NOT NULL,
                            `uptimeString` TEXT NOT NULL
                        )
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `rfc_items` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `hostId` INTEGER NOT NULL,
                            `rfcNumber` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `description` TEXT NOT NULL,
                            `proposedCommands` TEXT NOT NULL,
                            `rollbackScript` TEXT NOT NULL,
                            `impact` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `executionLog` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `executedAt` INTEGER
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        return factory.create(config).writableDatabase
    }

    @Test
    fun testMigration1To2CreatesSnippetAndClipboardTables() {
        val db = createV1Database()

        // Insert legacy host in v1
        val cv = ContentValues().apply {
            put("id", 1)
            put("name", "Workstation Alpha")
            put("address", "192.168.1.50")
            put("sshPort", 22)
            put("username", "hostmanager")
            put("authType", "SSH_KEY")
            put("sshPublicKey", "ssh-rsa AAAAB3NzaC...")
            put("sshPrivateKey", "-----BEGIN PRIVATE KEY-----")
            put("isProvisioned", 1)
            put("osType", "Ubuntu 26.04")
            put("cockpitPort", 9090)
            put("webminPort", 10000)
            put("vncPort", 5900)
            put("audioPort", 4713)
            put("lastConnected", 1700000000L)
            put("isOnline", 1)
            put("cpuUsage", 15)
            put("memoryUsage", 30)
            put("diskUsage", 45)
            put("temperatureC", 42)
            put("uptimeString", "2d 4h")
        }
        val insertedId = db.insert("hosts", SQLiteDatabase.CONFLICT_REPLACE, cv)
        assertEquals(1L, insertedId)

        // Execute MIGRATION_1_2
        MIGRATION_1_2.migrate(db)

        // Verify command_snippets table exists
        val cursorSnippets = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='command_snippets'")
        assertTrue("command_snippets table must exist", cursorSnippets.moveToFirst())
        cursorSnippets.close()

        // Verify clipboard_items table exists
        val cursorClipboard = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='clipboard_items'")
        assertTrue("clipboard_items table must exist", cursorClipboard.moveToFirst())
        cursorClipboard.close()

        // Insert snippet row to verify schema validity
        val snippetCv = ContentValues().apply {
            put("title", "Restart Scribe")
            put("command", "systemctl --user restart gemini-scribe")
            put("category", "System")
            put("isFavorite", 1)
        }
        val snippetId = db.insert("command_snippets", SQLiteDatabase.CONFLICT_REPLACE, snippetCv)
        assertTrue(snippetId > 0)

        // Verify existing host row remained untouched
        val hostCursor = db.query("SELECT name, osType FROM hosts WHERE id = 1")
        assertTrue(hostCursor.moveToFirst())
        assertEquals("Workstation Alpha", hostCursor.getString(0))
        assertEquals("Ubuntu 26.04", hostCursor.getString(1))
        hostCursor.close()

        db.close()
    }

    @Test
    fun testMigration2To3AddsTailscaleAndArcadeColumns() {
        val db = createV1Database()

        // Apply 1->2
        MIGRATION_1_2.migrate(db)

        // Insert v2 host
        val cv = ContentValues().apply {
            put("id", 10)
            put("name", "Kubuntu Rig")
            put("address", "192.168.1.161")
            put("sshPort", 22)
            put("username", "kms")
            put("authType", "SSH_KEY")
            put("sshPublicKey", "")
            put("sshPrivateKey", "")
            put("isProvisioned", 1)
            put("osType", "Linux")
            put("cockpitPort", 9090)
            put("webminPort", 10000)
            put("vncPort", 5900)
            put("audioPort", 4713)
            put("lastConnected", 1700000000L)
            put("isOnline", 1)
            put("cpuUsage", 10)
            put("memoryUsage", 20)
            put("diskUsage", 30)
            put("temperatureC", 35)
            put("uptimeString", "1d")
        }
        db.insert("hosts", SQLiteDatabase.CONFLICT_REPLACE, cv)

        // Apply 2->3
        MIGRATION_2_3.migrate(db)

        // Verify new columns via PRAGMA table_info
        val cursorPragma = db.query("PRAGMA table_info(hosts)")
        val columnNames = mutableSetOf<String>()
        while (cursorPragma.moveToNext()) {
            columnNames.add(cursorPragma.getString(1))
        }
        cursorPragma.close()

        val expectedColumns = listOf(
            "tailscaleAddress",
            "lanAddress",
            "connectionMode",
            "activeEndpoint",
            "lastLatencyMs",
            "arcadePort",
            "eveboxPort",
            "vaultAlias",
            "apiKey",
            "connectionState"
        )
        for (col in expectedColumns) {
            assertTrue("Column '$col' must exist in hosts table", columnNames.contains(col))
        }

        // Verify default values on pre-existing record
        val query = db.query("SELECT tailscaleAddress, lanAddress, connectionMode, arcadePort, eveboxPort, lastLatencyMs, connectionState FROM hosts WHERE id = 10")
        assertTrue(query.moveToFirst())
        assertEquals("", query.getString(0)) // tailscaleAddress default
        assertEquals("", query.getString(1)) // lanAddress default
        assertEquals("AUTO", query.getString(2)) // connectionMode default
        assertEquals(8899, query.getInt(3)) // arcadePort default
        assertEquals(5636, query.getInt(4)) // eveboxPort default
        assertEquals(-1L, query.getLong(5)) // lastLatencyMs default
        assertEquals("DISCONNECTED", query.getString(6)) // connectionState default
        query.close()

        db.close()
    }

    @Test
    fun testRoomDatabaseV3EndToEndWithDao() = runBlocking {
        // Create full in-memory Room v3 database with migrations registered
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        val hostDao = database.hostDao()

        val host = HostEntity(
            name = "Dual RTX 5060 Ti Host",
            address = "100.111.123.93",
            tailscaleAddress = "100.111.123.93",
            lanAddress = "192.168.1.161",
            connectionMode = "AUTO",
            activeEndpoint = "http://100.111.123.93:8899",
            lastLatencyMs = 12L,
            arcadePort = 8899,
            eveboxPort = 5636,
            vaultAlias = "host_vault_1",
            apiKey = "jwt_arcade_token_123",
            connectionState = "CONNECTED_TAILSCALE"
        )

        val id = hostDao.insertHost(host).toInt()
        assertTrue(id > 0)

        val retrieved = hostDao.getHostByIdDirect(id)
        assertNotNull(retrieved)
        assertEquals("Dual RTX 5060 Ti Host", retrieved?.name)
        assertEquals("100.111.123.93", retrieved?.tailscaleAddress)
        assertEquals("192.168.1.161", retrieved?.lanAddress)
        assertEquals(8899, retrieved?.arcadePort)
        assertEquals(12L, retrieved?.lastLatencyMs)
        assertEquals("CONNECTED_TAILSCALE", retrieved?.connectionState)
        assertEquals("host_vault_1", retrieved?.vaultAlias)

        // Test updating connection state via DAO query
        hostDao.updateConnectionStatus(
            hostId = id,
            state = "CONNECTED_LAN",
            activeEndpoint = "http://192.168.1.161:8899",
            latencyMs = 3L,
            isOnline = true
        )

        val updated = hostDao.getHostByIdDirect(id)
        assertNotNull(updated)
        assertEquals("CONNECTED_LAN", updated?.connectionState)
        assertEquals("http://192.168.1.161:8899", updated?.activeEndpoint)
        assertEquals(3L, updated?.lastLatencyMs)

        database.close()
    }
}
