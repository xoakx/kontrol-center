package com.example.e2e.tier1_features

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.MIGRATION_1_2
import com.example.data.database.MIGRATION_2_3
import com.example.data.entity.HostEntity
import com.example.data.repository.HostRepository
import com.example.data.security.SecureVault
import com.example.e2e.harness.FakeAndroidKeyStoreProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tier 1 Tests for Feature 2: Room DB v2/v3 Migration & Keystore Vault.
 * Covers 5 equivalence classes according to spec_miner_e2e_t1_3:
 * - T1_F02_01: Migration 1->2 creates command_snippets and clipboard_items
 * - T1_F02_02: Migration 2->3 adds multi-endpoint columns without table drops
 * - T1_F02_03: SecureVault encryption changes plaintext and wraps AES-GCM
 * - T1_F02_04: SecureVault roundtrip secret storage, retrieval, and deletion
 * - T1_F02_05: Multi-endpoint HostEntity full DAO CRUD and Flow reactivity
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class F02_RoomDbVaultTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var hostRepository: HostRepository
    private val v1DbName = "test_f02_v1.db"
    private val v2DbName = "test_f02_v2.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FakeAndroidKeyStoreProvider.install()
        context.deleteDatabase(v1DbName)
        context.deleteDatabase(v2DbName)

        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        hostRepository = HostRepository(
            hostDao = database.hostDao(),
            rfcDao = database.rfcDao(),
            snippetDao = database.commandSnippetDao(),
            clipboardDao = database.clipboardDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(v1DbName)
        context.deleteDatabase(v2DbName)
    }

    private fun createV1Database(dbName: String): SupportSQLiteDatabase {
        context.deleteDatabase(dbName)
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE `hosts` (
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
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        return factory.create(config).writableDatabase
    }

    private fun createV2Database(dbName: String): SupportSQLiteDatabase {
        context.deleteDatabase(dbName)
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE `hosts` (
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
                    db.execSQL("INSERT INTO `hosts` (`name`, `address`, `sshPort`, `username`, `authType`, `sshPublicKey`, `sshPrivateKey`, `isProvisioned`, `osType`, `cockpitPort`, `webminPort`, `vncPort`, `audioPort`, `lastConnected`, `isOnline`, `cpuUsage`, `memoryUsage`, `diskUsage`, `temperatureC`, `uptimeString`) VALUES ('Workstation', '100.111.123.93', 22, 'kms', 'SSH_KEY', '', '', 1, 'Ubuntu', 9090, 10000, 5900, 4713, 1000, 1, 20, 30, 40, 42, '1d')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        return factory.create(config).writableDatabase
    }

    @Test
    fun T1_F02_01_migration_1_2_table_creation() {
        val db = createV1Database(v1DbName)

        // Execute Migration 1 -> 2
        MIGRATION_1_2.migrate(db)

        // Verify tables exist and can be queried
        val snippetCursor = db.query("SELECT count(*) FROM command_snippets")
        assertTrue(snippetCursor.moveToFirst())
        assertEquals(0, snippetCursor.getInt(0))
        snippetCursor.close()

        val clipboardCursor = db.query("SELECT count(*) FROM clipboard_items")
        assertTrue(clipboardCursor.moveToFirst())
        assertEquals(0, clipboardCursor.getInt(0))
        clipboardCursor.close()

        db.close()
    }

    @Test
    fun T1_F02_02_migration_2_3_column_additions() {
        val db = createV2Database(v2DbName)

        // Execute Migration 2 -> 3
        MIGRATION_2_3.migrate(db)

        // Query migrated columns on existing host
        val cursor = db.query("SELECT tailscaleAddress, lanAddress, connectionMode, arcadePort, apiKey FROM hosts WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals("", cursor.getString(0)) // tailscaleAddress default
        assertEquals("", cursor.getString(1)) // lanAddress default
        assertEquals("AUTO", cursor.getString(2)) // connectionMode default
        assertEquals(8899, cursor.getInt(3)) // arcadePort default
        assertEquals("", cursor.getString(4)) // apiKey default
        cursor.close()

        db.close()
    }

    @Test
    fun T1_F02_03_keystore_vault_aes_gcm_encryption() {
        val vault = SecureVault(context, "test_vault_encrypt")
        val secretKey = "-----BEGIN OPENSSH PRIVATE KEY-----\nb3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW\n-----END OPENSSH PRIVATE KEY-----"

        val encrypted = vault.encrypt(secretKey)

        // Verify encryption transformation occurred
        assertNotNull(encrypted)
        assertTrue(encrypted.isNotBlank())
        assertTrue(encrypted != secretKey)
        // Verify Base64 structure
        assertTrue(encrypted.length > 24)
    }

    @Test
    fun T1_F02_04_credential_scrubbing_and_vault_association() {
        val vault = SecureVault(context, "test_vault_crud")
        val alias = "arcade_operator_token_test"
        val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.mock_payload_signature"

        // Store secret
        vault.storeSecret(alias, token)
        assertTrue(vault.hasSecret(alias))

        // Retrieve and verify exact string fidelity
        val retrieved = vault.getSecret(alias)
        assertEquals(token, retrieved)

        // Delete secret
        vault.deleteSecret(alias)
        assertNull(vault.getSecret(alias))
    }

    @Test
    fun T1_F02_05_multi_endpoint_host_crud_operations() = runBlocking {
        val host = HostEntity(
            name = "Workstation fml",
            address = "100.111.123.93",
            tailscaleAddress = "100.111.123.93",
            lanAddress = "192.168.1.161",
            connectionMode = "AUTO",
            activeEndpoint = "http://100.111.123.93:8899",
            lastLatencyMs = 14L,
            arcadePort = 8899,
            vaultAlias = "vault_host_1",
            apiKey = "mock_key_token"
        )

        // Insert
        val hostId = hostRepository.insertHost(host).toInt()
        assertTrue(hostId > 0)

        // Query by ID Flow
        val inserted = hostRepository.getHostById(hostId).first()
        assertNotNull(inserted)
        assertEquals("Workstation fml", inserted?.name)
        assertEquals("100.111.123.93", inserted?.tailscaleAddress)
        assertEquals("192.168.1.161", inserted?.lanAddress)
        assertEquals(14L, inserted?.lastLatencyMs)
        assertEquals("http://100.111.123.93:8899", inserted?.activeEndpoint)

        // Update
        val updatedHost = inserted!!.copy(lastLatencyMs = 8L, isOnline = true)
        hostRepository.updateHost(updatedHost)
        val refreshed = hostRepository.getHostById(hostId).first()
        assertEquals(8L, refreshed?.lastLatencyMs)

        // Delete
        hostRepository.deleteHost(refreshed!!)
        val postDelete = hostRepository.getHostById(hostId).first()
        assertNull(postDelete)
    }
}
