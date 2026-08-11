package com.primalsword.priore

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CTraderCredentials(
    val clientId: String,
    val clientSecret: String,
    val accessToken: String,
    val refreshToken: String,
    val environment: String,
)

object CredentialStore {
    private const val PREFS = "priore_secure_credentials"
    private const val KEY_ALIAS = "priore_ctrader_credentials_v1"

    fun save(context: Context, credentials: CTraderCredentials) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("client_id", encrypt(credentials.clientId))
            .putString("client_secret", encrypt(credentials.clientSecret))
            .putString("access_token", encrypt(credentials.accessToken))
            .putString("refresh_token", encrypt(credentials.refreshToken))
            .putString("environment", credentials.environment)
            .apply()
    }

    fun load(context: Context): CTraderCredentials? {
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val clientId = prefs.getString("client_id", null)?.let(::decrypt) ?: return null
            val clientSecret = prefs.getString("client_secret", null)?.let(::decrypt) ?: return null
            val accessToken = prefs.getString("access_token", null)?.let(::decrypt) ?: return null
            val refreshToken = prefs.getString("refresh_token", "")?.let(::decrypt).orEmpty()
            CTraderCredentials(
                clientId = clientId,
                clientSecret = clientSecret,
                accessToken = accessToken,
                refreshToken = refreshToken,
                environment = prefs.getString("environment", "demo") ?: "demo",
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val raw = Base64.decode(value, Base64.NO_WRAP)
        require(raw.size > 12) { "Credencial criptografada inválida" }
        val iv = raw.copyOfRange(0, 12)
        val encrypted = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }
}
