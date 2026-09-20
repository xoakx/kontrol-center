package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.security.SecureVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecureVaultTest {

    private lateinit var context: Context
    private lateinit var vault: SecureVault

    @Before
    fun setup() {
        com.example.e2e.harness.FakeAndroidKeyStoreProvider.install()
        context = ApplicationProvider.getApplicationContext()
        vault = SecureVault(context, "test_vault_prefs")
        vault.clearAll()
    }

    @Test
    fun testStoreAndRetrieveSecretRoundtrip() {
        val samplePrivateKey = """
            -----BEGIN OPENSSH PRIVATE KEY-----
            b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAABlwAAAAdzc2gtcn
            NhAAAAAwEAAQAAAYEAv6c34...TEST_SSH_KEY...
            -----END OPENSSH PRIVATE KEY-----
        """.trimIndent()

        vault.storeSecret("test_ssh_alias", samplePrivateKey)
        assertTrue(vault.hasSecret("test_ssh_alias"))

        val retrieved = vault.getSecret("test_ssh_alias")
        assertNotNull(retrieved)
        assertEquals(samplePrivateKey, retrieved)
    }

    @Test
    fun testHostCredentialsHelpers() {
        val hostId = 42
        val keyPem = "-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgk...-----END PRIVATE KEY-----"
        val passphrase = "UltraSecurePassphrase!2026"
        val arcadeJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhcmNhZGUifQ.signature"

        vault.storeSshPrivateKey(hostId, keyPem)
        vault.storeHostPassphrase(hostId, passphrase)
        vault.storeArcadeToken(hostId, arcadeJwt)

        assertEquals(keyPem, vault.getSshPrivateKey(hostId))
        assertEquals(passphrase, vault.getHostPassphrase(hostId))
        assertEquals(arcadeJwt, vault.getArcadeToken(hostId))

        // Delete all for host
        vault.deleteHostCredentials(hostId)
        assertNull(vault.getSshPrivateKey(hostId))
        assertNull(vault.getHostPassphrase(hostId))
        assertNull(vault.getArcadeToken(hostId))
    }

    @Test
    fun testRawPreferencesDoesNotContainPlaintext() {
        val secret = "SUPER_CONFIDENTIAL_TOKEN_XYZ_987"
        vault.storeSecret("token_alias", secret)

        val rawPrefs = context.getSharedPreferences("test_vault_prefs", Context.MODE_PRIVATE)
        val rawValue = rawPrefs.getString("token_alias", "") ?: ""

        assertTrue("Encrypted payload must not be blank", rawValue.isNotBlank())
        assertFalse("Raw SharedPreferences must never leak plain text", rawValue.contains("SUPER_CONFIDENTIAL"))
    }

    @Test
    fun testRandomizedIvProducesDifferentCiphertextForSamePlaintext() {
        val plain = "SAME_SECRET_TEXT"
        val encrypted1 = vault.encrypt(plain)
        val encrypted2 = vault.encrypt(plain)

        // AES-GCM must use random IV each invocation
        assertNotEquals("Ciphertexts for the same plaintext must differ due to unique IVs", encrypted1, encrypted2)

        // Both decrypt back to identical plaintext
        assertEquals(plain, vault.decrypt(encrypted1))
        assertEquals(plain, vault.decrypt(encrypted2))
    }

    @Test
    fun testConcurrentReadWriteThreadSafety() = runBlocking {
        val count = 40
        val jobs = (1..count).map { index ->
            async(Dispatchers.IO) {
                val alias = "concurrent_key_$index"
                val secret = "Secret_Payload_For_Worker_$index"
                vault.storeSecret(alias, secret)
                val readBack = vault.getSecret(alias)
                assertEquals(secret, readBack)
            }
        }
        jobs.awaitAll()

        for (index in 1..count) {
            assertTrue(vault.hasSecret("concurrent_key_$index"))
            assertEquals("Secret_Payload_For_Worker_$index", vault.getSecret("concurrent_key_$index"))
        }
    }

    @Test
    fun testTamperedPayloadFailsGracefully() {
        val alias = "tamper_test"
        vault.storeSecret(alias, "IntegrityTestMessage")

        val rawPrefs = context.getSharedPreferences("test_vault_prefs", Context.MODE_PRIVATE)
        val validEncrypted = rawPrefs.getString(alias, "")!!

        // Flip a byte in the base64 encoded ciphertext
        val tamperedChars = validEncrypted.toCharArray()
        val indexToFlip = tamperedChars.size - 4
        tamperedChars[indexToFlip] = if (tamperedChars[indexToFlip] == 'A') 'B' else 'A'
        val tamperedPayload = String(tamperedChars)

        rawPrefs.edit().putString(alias, tamperedPayload).commit()

        // GCM authentication tag verification will fail, getSecret returns null safely
        val result = vault.getSecret(alias)
        assertNull("Tampered payload should fail GCM authentication and return null", result)
    }
}
