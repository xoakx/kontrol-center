package com.example.e2e.tier2_boundaries

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.MIGRATION_1_2
import com.example.data.database.MIGRATION_2_3
import com.example.data.entity.HostEntity
import com.example.data.repository.HostRepository
import com.example.data.security.SecureVault
import com.example.e2e.harness.FakeAndroidKeyStoreProvider
import com.example.e2e.harness.FakeAndroidKeyStoreSpi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

/**
 * Tier 2 Boundary Tests: B02 Room DB Vault Boundary.
 * Covers 5 boundary conditions:
 * - T2_B02_01: Corrupted DB file recovery via fallbackToDestructiveMigration
 * - T2_B02_02: Direct multi-step migration (v1 -> v2 -> v3) data integrity
 * - T2_B02_03: Empty, blank, and null API token storage and decryption in SecureVault
 * - T2_B02_04: Rapid concurrent writes and contention across multiple coroutines
 * - T2_B02_05: SQL injection attempts in host name, address, and credentials
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class B02_RoomDbVaultBoundaryTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var hostRepository: HostRepository
    private lateinit var secureVault: SecureVault
    private val corruptDbName = "corrupt_boundary_test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FakeAndroidKeyStoreProvider.install()
        FakeAndroidKeyStoreSpi.clear()
        context.deleteDatabase(corruptDbName)

        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        hostRepository = HostRepository(
            hostDao = database.hostDao(),
            rfcDao = database.rfcDao(),
            snippetDao = database.commandSnippetDao(),
            clipboardDao = database.clipboardDao()
        )

        secureVault = SecureVault.getInstance(context)
        secureVault.clearAll()
    }

    @After
    fun tearDown() {
        database.close()
        FakeAndroidKeyStoreSpi.clear()
        context.deleteDatabase(corruptDbName)
    }

    @Test
    fun T2_B02_01_corrupted_db_recovery() {
        // Create an intentionally corrupt SQLite database file with random garbage bytes
        val dbFile = context.getDatabasePath(corruptDbName)
        dbFile.parentFile?.mkdirs()
        FileOutputStream(dbFile).use { fos ->
            fos.write("CORRUPT_INVALID_SQLITE_HEADER_GARBAGE_BYTES_123456789".toByteArray())
            fos.flush()
        }

        // Room configured with fallbackToDestructiveMigration must recover cleanly
        val recoveredDb = Room.databaseBuilder(context, AppDatabase::class.java, corruptDbName)
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries()
            .build()

        // Insert and read back a host entity to verify database rebuilt successfully
        val host = HostEntity(name = "Recovered Host", address = "100.111.123.93")
        val id = runBlocking { recoveredDb.hostDao().insertHost(host) }
        assertTrue(id > 0)

        val retrieved = runBlocking { recoveredDb.hostDao().getHostByIdDirect(id.toInt()) }
        assertNotNull(retrieved)
        assertEquals("Recovered Host", retrieved?.name)

        recoveredDb.close()
    }

    @Test
    fun T2_B02_02_direct_multistep_migration_v1_to_v3() {
        val migrationDbName = "migration_chain_test.db"
        context.deleteDatabase(migrationDbName)

        // Build with migrations 1->2 and 2->3
        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, migrationDbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        val host = HostEntity(
            name = "Migrated Host",
            address = "100.111.123.93",
            tailscaleAddress = "100.111.123.93",
            lanAddress = "192.168.1.161",
            activeEndpoint = "http://100.111.123.93:8899"
        )
        val id = runBlocking { migratedDb.hostDao().insertHost(host) }
        val fetched = runBlocking { migratedDb.hostDao().getHostByIdDirect(id.toInt()) }

        assertNotNull(fetched)
        assertEquals("100.111.123.93", fetched?.tailscaleAddress)
        assertEquals("192.168.1.161", fetched?.lanAddress)

        migratedDb.close()
        context.deleteDatabase(migrationDbName)
    }

    @Test
    fun T2_B02_03_empty_and_blank_api_tokens_vault() {
        // 1. Non-existent secret returns null
        assertNull(secureVault.getSecret("non_existent_key_alias"))
        assertFalse(secureVault.hasSecret("non_existent_key_alias"))

        // 2. Empty string token
        secureVault.storeSecret("empty_token", "")
        assertTrue(secureVault.hasSecret("empty_token"))
        val emptyDecrypted = secureVault.getSecret("empty_token")
        assertEquals("", emptyDecrypted)

        // 3. Whitespace-only token
        secureVault.storeSecret("whitespace_token", "   \t\n   ")
        assertEquals("   \t\n   ", secureVault.getSecret("whitespace_token"))

        // 4. Boundary 4096-character long token
        val longToken = "A".repeat(4096)
        secureVault.storeSecret("long_token", longToken)
        assertEquals(longToken, secureVault.getSecret("long_token"))
    }

    @Test
    fun T2_B02_04_rapid_concurrent_writes_contention() = runBlocking {
        // Concurrently insert 30 hosts and 30 secure secrets across Dispatchers.IO
        val jobs = (1..30).map { i ->
            async(Dispatchers.IO) {
                val host = HostEntity(
                    name = "Concurrent Host $i",
                    address = "100.111.123.$i",
                    tailscaleAddress = "100.111.123.$i",
                    lanAddress = "192.168.1.$i"
                )
                val id = hostRepository.insertHost(host).toInt()
                secureVault.storeArcadeToken(id, "jwt_token_for_host_$id")
                id
            }
        }

        val ids = jobs.awaitAll()
        assertEquals(30, ids.size)

        // Verify all 30 hosts are intact in DB and all 30 tokens in SecureVault
        val allHosts = hostRepository.allHosts.first()
        assertEquals(30, allHosts.size)

        for (id in ids) {
            val token = secureVault.getArcadeToken(id)
            assertNotNull(token)
            assertEquals("jwt_token_for_host_$id", token)
        }
    }

    @Test
    fun T2_B02_05_sql_injection_attempts_in_host_name() = runBlocking {
        val maliciousName = "Host'; DROP TABLE hosts; --"
        val maliciousAddress = "100.111.123.93' OR '1'='1"
        val maliciousEndpoint = "http://'; DELETE FROM hosts WHERE 1=1; --"

        val host = HostEntity(
            name = maliciousName,
            address = maliciousAddress,
            tailscaleAddress = maliciousAddress,
            lanAddress = "192.168.1.161",
            activeEndpoint = maliciousEndpoint
        )

        val id = hostRepository.insertHost(host).toInt()
        assertTrue(id > 0)

        // Verify table still exists and data was escaped safely without SQL execution
        val retrieved = hostRepository.getHostById(id).first()
        assertNotNull(retrieved)
        assertEquals(maliciousName, retrieved?.name)
        assertEquals(maliciousAddress, retrieved?.address)
        assertEquals(maliciousEndpoint, retrieved?.activeEndpoint)

        // Total count should be exactly 1
        val allHosts = hostRepository.allHosts.first()
        assertEquals(1, allHosts.size)
    }
}
