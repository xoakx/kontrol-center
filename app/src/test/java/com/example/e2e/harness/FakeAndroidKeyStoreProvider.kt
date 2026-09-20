package com.example.e2e.harness

import android.security.keystore.KeyGenParameterSpec
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.Collections
import java.util.Date
import java.util.Enumeration
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGenerator
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey

/**
 * In-memory AndroidKeyStore provider for Robolectric test execution.
 * Allows KeyStore.getInstance("AndroidKeyStore") and KeyGenerator.getInstance("AES", "AndroidKeyStore")
 * to succeed during unit tests without requiring a real hardware TEE/SE daemon.
 */
class FakeAndroidKeyStoreProvider : Provider("AndroidKeyStore", 1.0, "Fake AndroidKeyStore for Robolectric") {

    init {
        put("KeyStore.AndroidKeyStore", FakeAndroidKeyStoreSpi::class.java.name)
        put("KeyGenerator.AES", FakeAndroidKeyGeneratorSpi::class.java.name)
    }

    companion object {
        private var installed = false

        @Synchronized
        fun install() {
            if (!installed) {
                Security.insertProviderAt(FakeAndroidKeyStoreProvider(), 1)
                installed = true
            }
        }
    }
}

class FakeAndroidKeyStoreSpi : KeyStoreSpi() {

    companion object {
        val keys = ConcurrentHashMap<String, Key>()
        fun clear() {
            keys.clear()
        }
    }

    override fun engineGetKey(alias: String?, password: CharArray?): Key? = keys[alias]

    override fun engineGetCertificateChain(alias: String?): Array<Certificate>? = null

    override fun engineGetCertificate(alias: String?): Certificate? = null

    override fun engineGetCreationDate(alias: String?): Date? = Date()

    override fun engineSetKeyEntry(alias: String?, key: Key?, password: CharArray?, chain: Array<out Certificate>?) {
        if (alias != null && key != null) {
            keys[alias] = key
        }
    }

    override fun engineSetKeyEntry(alias: String?, key: ByteArray?, chain: Array<out Certificate>?) {}

    override fun engineSetCertificateEntry(alias: String?, cert: Certificate?) {}

    override fun engineDeleteEntry(alias: String?) {
        alias?.let { keys.remove(it) }
    }

    override fun engineAliases(): Enumeration<String> = Collections.enumeration(keys.keys)

    override fun engineContainsAlias(alias: String?): Boolean = alias != null && keys.containsKey(alias)

    override fun engineSize(): Int = keys.size

    override fun engineIsKeyEntry(alias: String?): Boolean = alias != null && keys.containsKey(alias)

    override fun engineIsCertificateEntry(alias: String?): Boolean = false

    override fun engineGetCertificateAlias(cert: Certificate?): String? = null

    override fun engineStore(stream: OutputStream?, password: CharArray?) {}

    override fun engineLoad(stream: InputStream?, password: CharArray?) {}

    override fun engineGetEntry(alias: String?, protParam: KeyStore.ProtectionParameter?): KeyStore.Entry? {
        val key = keys[alias] as? SecretKey ?: return null
        return KeyStore.SecretKeyEntry(key)
    }

    override fun engineSetEntry(alias: String?, entry: KeyStore.Entry?, protParam: KeyStore.ProtectionParameter?) {
        if (alias != null && entry is KeyStore.SecretKeyEntry) {
            keys[alias] = entry.secretKey
        }
    }
}

class FakeAndroidKeyGeneratorSpi : KeyGeneratorSpi() {

    private var keyAlias: String? = null
    private var keySize: Int = 256

    override fun engineInit(secureRandom: SecureRandom?) {}

    override fun engineInit(params: AlgorithmParameterSpec?, random: SecureRandom?) {
        if (params is KeyGenParameterSpec) {
            keyAlias = params.keystoreAlias
            keySize = params.keySize
        }
    }

    override fun engineInit(keysize: Int, random: SecureRandom?) {
        this.keySize = keysize
    }

    override fun engineGenerateKey(): SecretKey {
        val keyBytes = ByteArray(keySize / 8)
        java.security.SecureRandom().nextBytes(keyBytes)
        val key = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
        keyAlias?.let { FakeAndroidKeyStoreSpi.keys[it] = key }
        return key
    }
}
