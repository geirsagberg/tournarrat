package net.sagberg.tournarrat.core.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface ApiKeyStore {
    fun getOpenAiApiKey(): String?

    fun setOpenAiApiKey(value: String)

    fun clearOpenAiApiKey()
}

class EncryptedApiKeyStore(context: Context) : ApiKeyStore {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)

    override fun getOpenAiApiKey(): String? {
        val payload = preferences.getString(OPEN_AI_KEY, null) ?: return null
        return runCatching { decrypt(payload) }.getOrNull()
    }

    override fun setOpenAiApiKey(value: String) {
        preferences.edit().putString(OPEN_AI_KEY, encrypt(value.trim())).apply()
    }

    override fun clearOpenAiApiKey() {
        preferences.edit().remove(OPEN_AI_KEY).apply()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val iv = cipher.iv
        return encode(iv) + PAYLOAD_SEPARATOR + encode(encrypted)
    }

    private fun decrypt(payload: String): String {
        val pieces = payload.split(PAYLOAD_SEPARATOR, limit = 2)
        require(pieces.size == 2) { "Malformed encrypted API key payload." }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, decode(pieces[0]))
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
        val decrypted = cipher.doFinal(decode(pieces[1]))
        return String(decrypted, StandardCharsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray =
        Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_ALIAS = "tournarrat_api_key"
        const val OPEN_AI_KEY = "open_ai_api_key"
        const val PREFERENCES_FILE = "secure_api_keys"
        const val PAYLOAD_SEPARATOR = "."
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
