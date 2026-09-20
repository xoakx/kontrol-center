package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Hardware-backed credential vault wrapping Android Keystore and AES-256-GCM cipher.
 * Stores SSH private keys, passphrases, and Arcade API tokens securely in private SharedPreferences.
 * 
 * Target compatibility: Android API 24+ (minSdk 24).
 * Thread-safe: Protected by ReentrantReadWriteLock.
 */
class SecureVault(
    private val context: Context,
    private val prefsName: String = PREFS_NAME
) {
    companion object {
        private const val TAG = "SecureVault"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "kontrol_center_master_key_v1"
        private const val AES_GCM_CIPHER = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128 // bits
        private const val GCM_IV_LENGTH = 12 // bytes (96 bits recommended for GCM)
        private const val PREFS_NAME = "com.example.kontrolcenter.secure_vault"

        @Volatile
        private var instance: SecureVault? = null

        fun getInstance(context: Context): SecureVault {
            return instance ?: synchronized(this) {
                instance ?: SecureVault(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val lock = ReentrantReadWriteLock()

    /**
     * Retrieves or generates an AES-256 master key inside AndroidKeyStore.
     */
    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        
        if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            val entry = keyStore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val keyGenSpec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts a plaintext string using AES-256-GCM with a random IV.
     * Output format: Base64( [12-byte IV] + [Ciphertext + AuthTag] )
     */
    fun encrypt(plainText: String): String {
        lock.write {
            val cipher = Cipher.getInstance(AES_GCM_CIPHER)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + cipherBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)

            return Base64.encodeToString(combined, Base64.NO_WRAP)
        }
    }

    /**
     * Decrypts a Base64 payload containing IV + Ciphertext using AES-256-GCM.
     */
    fun decrypt(encryptedPayloadBase64: String): String {
        lock.read {
            val combined = Base64.decode(encryptedPayloadBase64, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LENGTH) {
                throw IllegalArgumentException("Payload too short to contain valid GCM IV")
            }

            val iv = ByteArray(GCM_IV_LENGTH)
            val cipherBytes = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherBytes, 0, cipherBytes.size)

            val cipher = Cipher.getInstance(AES_GCM_CIPHER)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateMasterKey(), spec)
            val plainBytes = cipher.doFinal(cipherBytes)

            return String(plainBytes, Charsets.UTF_8)
        }
    }

    // ==========================================
    // GENERIC SECRET OPERATIONS
    // ==========================================

    fun storeSecret(alias: String, secret: String) {
        val encrypted = encrypt(secret)
        lock.write {
            prefs.edit().putString(alias, encrypted).apply()
        }
    }

    fun getSecret(alias: String): String? {
        val encrypted = lock.read {
            prefs.getString(alias, null)
        } ?: return null

        return try {
            decrypt(encrypted)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to decrypt secret for alias '$alias'", t)
            null
        }
    }

    fun hasSecret(alias: String): Boolean {
        return lock.read {
            prefs.contains(alias)
        }
    }

    fun deleteSecret(alias: String) {
        lock.write {
            prefs.edit().remove(alias).apply()
        }
    }

    fun clearAll() {
        lock.write {
            prefs.edit().clear().apply()
        }
    }

    // ==========================================
    // HOST SPECIFIC CONVENIENCE METHODS
    // ==========================================

    fun storeSshPrivateKey(hostId: Int, privateKeyPem: String) {
        storeSecret("ssh_priv_host_$hostId", privateKeyPem)
    }

    fun getSshPrivateKey(hostId: Int): String? {
        return getSecret("ssh_priv_host_$hostId")
    }

    fun storeHostPassphrase(hostId: Int, passphrase: String) {
        storeSecret("passphrase_host_$hostId", passphrase)
    }

    fun getHostPassphrase(hostId: Int): String? {
        return getSecret("passphrase_host_$hostId")
    }

    fun storeArcadeToken(hostId: Int, token: String) {
        storeSecret("arcade_token_host_$hostId", token)
    }

    fun getArcadeToken(hostId: Int): String? {
        return getSecret("arcade_token_host_$hostId")
    }

    fun deleteHostCredentials(hostId: Int) {
        deleteSecret("ssh_priv_host_$hostId")
        deleteSecret("passphrase_host_$hostId")
        deleteSecret("arcade_token_host_$hostId")
    }
}
