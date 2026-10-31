package com.luma.lifeos.lock

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Platform-backed credential primitives. Passwords use the built-in PBKDF2-HMAC-SHA256 provider;
 * the non-exportable Keystore HMAC key is a pepper and also authenticates the local state file.
 */
internal class AndroidLockCrypto : LockStoreAuthenticator {
    private val random = SecureRandom()

    override fun authenticate(payload: ByteArray): ByteArray = keyedMac(STORE_DOMAIN, payload)

    override fun hasKey(): Boolean {
        val keyStore = loadKeyStore()
        return keyStore.containsAlias(KEY_ALIAS)
    }

    override fun removeKeyIfPresent() {
        loadKeyStore().deleteEntry(KEY_ALIAS)
    }

    fun randomSalt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    fun createPasswordVerifier(password: CharArray, salt: ByteArray): ByteArray {
        val derived = derivePassword(password, salt)
        return try {
            val keyExisted = hasKey()
            try {
                ensureKeyForNewCredential()
                keyedMac(PASSWORD_DOMAIN, derived)
            } catch (failure: Exception) {
                if (!keyExisted) runCatching { removeKeyIfPresent() }
                throw failure
            }
        } finally {
            derived.fill(0)
        }
    }

    fun passwordVerifier(password: CharArray, salt: ByteArray): ByteArray {
        val derived = derivePassword(password, salt)
        return try {
            keyedMac(PASSWORD_DOMAIN, derived)
        } finally {
            derived.fill(0)
        }
    }

    private fun derivePassword(password: CharArray, salt: ByteArray): ByteArray {
        require(salt.size == SALT_BYTES)
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, DERIVED_KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun passwordMatches(password: CharArray, salt: ByteArray, expected: ByteArray): Boolean {
        val actual = passwordVerifier(password, salt)
        return try {
            MessageDigest.isEqual(actual, expected)
        } finally {
            actual.fill(0)
        }
    }

    fun generateRecoveryCode(): CharArray {
        val entropy = ByteArray(RECOVERY_ENTROPY_BYTES).also(random::nextBytes)
        return try {
            formatRecoveryCode(base32(entropy))
        } finally {
            entropy.fill(0)
        }
    }

    fun recoveryVerifier(code: CharArray, salt: ByteArray): ByteArray {
        require(salt.size == SALT_BYTES && isRecoveryCodeFormat(code))
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(RECOVERY_DOMAIN)
        digest.update(salt)
        code.forEach { digest.update(it.code.toByte()) }
        return digest.digest()
    }

    fun recoveryCodeMatches(code: CharArray, salt: ByteArray, expected: ByteArray): Boolean {
        if (!isRecoveryCodeFormat(code)) return false
        val actual = recoveryVerifier(code, salt)
        return try {
            MessageDigest.isEqual(actual, expected)
        } finally {
            actual.fill(0)
        }
    }

    private fun keyedMac(domain: ByteArray, message: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(loadRequiredKey())
        mac.update(domain)
        return mac.doFinal(message)
    }

    private fun loadRequiredKey(): SecretKey {
        val keyStore = loadKeyStore()
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        return existing ?: throw GeneralSecurityException("App lock Keystore key is unavailable")
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /** Called only for a new credential; reads and MAC verification never create replacement keys. */
    @Synchronized
    private fun ensureKeyForNewCredential() {
        val keyStore = loadKeyStore()
        if (keyStore.containsAlias(KEY_ALIAS)) {
            if (keyStore.getKey(KEY_ALIAS, null) !is SecretKey) {
                throw GeneralSecurityException("App lock Keystore key is invalid")
            }
            return
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        generator.generateKey()
    }

    private fun isRecoveryCodeFormat(code: CharArray): Boolean {
        if (code.size != RECOVERY_DISPLAY_LENGTH) return false
        for (index in code.indices) {
            val shouldBeSeparator = index == 5 || index == 11 || index == 17 || index == 23
            if (shouldBeSeparator) {
                if (code[index] != '-') return false
            } else if (code[index] !in RECOVERY_ALPHABET) {
                return false
            }
        }
        return true
    }

    private fun base32(bytes: ByteArray): CharArray {
        val result = CharArray((bytes.size * 8 + 4) / 5)
        var outputIndex = 0
        var buffer = 0
        var bits = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                result[outputIndex++] = RECOVERY_ALPHABET[(buffer shr bits) and 0x1f]
            }
        }
        if (bits > 0) result[outputIndex++] = RECOVERY_ALPHABET[(buffer shl (5 - bits)) and 0x1f]
        check(outputIndex == result.size)
        return result
    }

    private fun formatRecoveryCode(unformatted: CharArray): CharArray {
        check(unformatted.size == RECOVERY_SYMBOLS)
        val output = CharArray(RECOVERY_DISPLAY_LENGTH)
        var source = 0
        var target = 0
        while (source < unformatted.size) {
            if (source > 0 && source < RECOVERY_SYMBOLS - 1 && source % RECOVERY_GROUP_SIZE == 0) {
                output[target++] = '-'
            }
            output[target++] = unformatted[source++]
        }
        check(target == output.size)
        unformatted.fill('\u0000')
        return output
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "com.luma.lifeos.app_lock.verifier.v1"
        const val HMAC_ALGORITHM = "HmacSHA256"
        const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val PBKDF2_ITERATIONS = 600_000
        const val DERIVED_KEY_BITS = 256
        const val SALT_BYTES = 16
        const val RECOVERY_ENTROPY_BYTES = 16 // 128 random bits.
        const val RECOVERY_SYMBOLS = 26
        const val RECOVERY_GROUP_SIZE = 5
        const val RECOVERY_DISPLAY_LENGTH = 30
        const val RECOVERY_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val STORE_DOMAIN = "LUMA-LOCK-STORE-v1\u0000".toByteArray(StandardCharsets.UTF_8)
        val PASSWORD_DOMAIN = "LUMA-LOCK-PASSWORD-v1\u0000".toByteArray(StandardCharsets.UTF_8)
        val RECOVERY_DOMAIN = "LUMA-LOCK-RECOVERY-v1\u0000".toByteArray(StandardCharsets.UTF_8)
    }
}
