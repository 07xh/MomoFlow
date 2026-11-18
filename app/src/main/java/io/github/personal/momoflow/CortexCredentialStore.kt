package io.github.personal.momoflow

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * Cortex access token is separate from the Open API token. Do not persist the
 * legacy token supplied to exchangeLegacyToken(), or put secrets in preferences.
 */
class CortexCredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("momoflow_cortex_secret", Context.MODE_PRIVATE)
    private val alias = "momoflow_cortex_access_v1"
    private val flags = Base64.NO_WRAP

    fun saveAccessToken(token: String) {
        require(token.isNotBlank())
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(alias)) {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder(
                alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generator.generateKey()
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, ks.getKey(alias, null))
        val ciphertext = cipher.doFinal(token.trim().toByteArray(Charsets.UTF_8))
        check(prefs.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, flags))
            .putString("ciphertext", Base64.encodeToString(ciphertext, flags))
            .commit()) { "Cortex 凭证未能安全保存" }
    }

    fun accessToken(): String? {
        val iv = prefs.getString("iv", null) ?: return null
        val ciphertext = prefs.getString("ciphertext", null) ?: return null
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, ks.getKey(alias, null),
                GCMParameterSpec(128, Base64.decode(iv, flags)))
            String(cipher.doFinal(Base64.decode(ciphertext, flags)), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        check(prefs.edit().clear().commit()) { "Cortex 凭证清除失败" }
    }
}
