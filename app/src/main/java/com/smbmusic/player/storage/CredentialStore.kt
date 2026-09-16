package com.smbmusic.player.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec


data class SmbCredentials(
    val address: String,
    val username: String,
    val password: String
)

class CredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("smb_music_settings", Context.MODE_PRIVATE)

    fun load(): SmbCredentials {
        return SmbCredentials(
            address = prefs.getString("address", "") ?: "",
            username = prefs.getString("username", "") ?: "",
            password = decryptPassword()
        )
    }

    fun save(credentials: SmbCredentials) {
        val encrypted = encrypt(credentials.password)
        prefs.edit()
            .putString("address", credentials.address)
            .putString("username", credentials.username)
            .putString("password_ct", encrypted.cipherText)
            .putString("password_iv", encrypted.iv)
            .apply()
    }

    fun saveLastFolder(url: String) {
        prefs.edit().putString("last_folder", url).apply()
    }

    fun loadLastFolder(): String = prefs.getString("last_folder", "") ?: ""

    private data class Encrypted(val cipherText: String, val iv: String)

    private fun encrypt(plainText: String): Encrypted {
        if (plainText.isEmpty()) return Encrypted("", "")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Encrypted(
            Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        )
    }

    private fun decryptPassword(): String {
        val cipherText = prefs.getString("password_ct", "") ?: ""
        val iv = prefs.getString("password_iv", "") ?: ""
        if (cipherText.isEmpty() || iv.isEmpty()) return ""

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            val plain = cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP))
            String(plain, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun getOrCreateKey(): SecretKey {
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
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "smb_music_credentials_v1"
    }
}
