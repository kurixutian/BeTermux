package com.newtermux.ai

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores user-provided AI API keys in private preferences encrypted via
 * AndroidKeyStore AES-GCM (API 23+) with a safe fallback when KeyStore is
 * unavailable (API 21-22 or JVM unit test sandbox). Never logs raw keys.
 */
object AiKeyStore {
    private const val PREFS_NAME = "newtermux_ai_secrets"
    private const val KEY_ALIAS = "betermux_ai_master_key_v1"
    private const val PREFIX_GCM = "gcm1:"
    private const val PREFIX_RAW = "raw1:"
    private const val GCM_IV_LEN = 12
    private const val GCM_TAG_BITS = 128

    private val keyErrors = ConcurrentHashMap<String, String>()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun prefKey(provider: AiProviderKind): String = "api_key_${provider.id}"

    private fun isKeyStoreMissingSandbox(): Boolean =
        Build.FINGERPRINT.equals("robolectric", ignoreCase = true) ||
            runCatching { KeyStore.getInstance("AndroidKeyStore") }.isFailure

    fun setApiKey(context: Context, provider: AiProviderKind, rawKey: String): Boolean {
        val trimmed = rawKey.trim()
        val key = prefKey(provider)
        val editor = prefs(context).edit()
        if (trimmed.isEmpty()) {
            keyErrors.remove(key)
            editor.remove(key).apply()
            return true
        }
        val encrypted = encryptWithKeyStore(trimmed)
        val encoded = when {
            encrypted != null -> encrypted
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || isKeyStoreMissingSandbox() ->
                PREFIX_RAW + encodeBase64(trimmed.toByteArray(StandardCharsets.UTF_8))
            else -> {
                keyErrors[key] = "AndroidKeyStore encryption failed; refusing to save unencrypted API key."
                return false
            }
        }
        keyErrors.remove(key)
        editor.putString(key, encoded).apply()
        return true
    }

    fun getApiKey(context: Context, provider: AiProviderKind): String {
        val key = prefKey(provider)
        val stored = prefs(context).getString(key, null) ?: run {
            keyErrors.remove(key)
            return ""
        }
        return when {
            stored.startsWith(PREFIX_GCM) -> {
                val decrypted = decryptWithKeyStore(stored.removePrefix(PREFIX_GCM))
                if (decrypted == null) {
                    keyErrors[key] = "Stored ${provider.displayName} API key could not be decrypted; please re-enter it."
                    ""
                } else {
                    keyErrors.remove(key)
                    decrypted
                }
            }
            stored.startsWith(PREFIX_RAW) -> {
                keyErrors.remove(key)
                String(decodeBase64(stored.removePrefix(PREFIX_RAW)), StandardCharsets.UTF_8)
            }
            else -> {
                keyErrors[key] = "Stored ${provider.displayName} API key format is unrecognized."
                ""
            }
        }
    }

    fun getKeyError(context: Context, provider: AiProviderKind): String? {
        getApiKey(context, provider)
        return keyErrors[prefKey(provider)]
    }

    fun hasApiKey(context: Context, provider: AiProviderKind): Boolean =
        getApiKey(context, provider).isNotBlank()

    fun maskedKeyHint(context: Context, provider: AiProviderKind): String {
        val key = getApiKey(context, provider)
        if (key.isEmpty()) return ""
        return if (key.length <= 8) "Configured (${key.length} chars)"
        else "${key.take(4)}…${key.takeLast(4)}"
    }

    private fun encryptWithKeyStore(plain: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        return runCatching {
            val secretKey = getOrCreateSecretKey() ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv ?: return null
            if (iv.size != GCM_IV_LEN) return null
            val cipherBytes = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
            val combined = ByteArray(GCM_IV_LEN + cipherBytes.size)
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LEN)
            System.arraycopy(cipherBytes, 0, combined, GCM_IV_LEN, cipherBytes.size)
            PREFIX_GCM + encodeBase64(combined)
        }.getOrNull()
    }

    private fun decryptWithKeyStore(payload: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        return runCatching {
            val combined = decodeBase64(payload)
            if (combined.size <= GCM_IV_LEN) return null
            val iv = combined.copyOfRange(0, GCM_IV_LEN)
            val cipherBytes = combined.copyOfRange(GCM_IV_LEN, combined.size)
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val secretKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun getOrCreateSecretKey(): SecretKey? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun encodeBase64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decodeBase64(encoded: String): ByteArray =
        Base64.decode(encoded, Base64.NO_WRAP)
}
