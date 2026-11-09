package io.github.personal.momoflow

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/** Token in Android Keystore AES/GCM; vocabulary cache contains no credentials. */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("momoflow_local", Context.MODE_PRIVATE)
    private val alias = "momoflow_maimemo_token_key_v1"
    private val b64flags = Base64.NO_WRAP

    fun saveToken(token: String) {
        require(token.isNotBlank())
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keystore.containsAlias(alias)) {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generator.generateKey()
        }
        val key = keystore.getKey(alias, null)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val bytes = cipher.doFinal(token.trim().toByteArray(Charsets.UTF_8))
        prefs.edit().putString("token_iv", Base64.encodeToString(cipher.iv, b64flags))
            .putString("token_data", Base64.encodeToString(bytes, b64flags)).apply()
    }

    fun token(): String? {
        val iv = prefs.getString("token_iv", null) ?: return null
        val data = prefs.getString("token_data", null) ?: return null
        return try {
            val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = keystore.getKey(alias, null) ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(iv, b64flags)))
            String(cipher.doFinal(Base64.decode(data, b64flags)), Charsets.UTF_8)
        } catch (_: Exception) {
            null // e.g. changed lock-screen / invalidated Keystore key
        }
    }

    fun forgetToken() { prefs.edit().remove("token_iv").remove("token_data").apply() }
    fun saveSnapshot(snapshot: StudySnapshot) {
        // Do not jump back to the first card after every refresh.
        val old = snapshot()?.words?.getOrNull(index())?.id
        val next = snapshot.words.indexOfFirst { it.id == old }.coerceAtLeast(0)
        prefs.edit().putString("snapshot", snapshot.toJson().toString()).putInt("index", next).apply()
    }
    fun snapshot(): StudySnapshot? = try {
        prefs.getString("snapshot", null)?.let { StudySnapshot.parse(JSONObject(it)) }
    } catch (_: Exception) { null }
    fun isToday(snapshot: StudySnapshot) = snapshot.date == StudyDay.today()
    fun index(): Int = prefs.getInt("index", 0)
    fun setIndex(value: Int) { prefs.edit().putInt("index", value).apply() }
    fun reveal(): Boolean = prefs.getBoolean("reveal", false)
    fun setReveal(value: Boolean) { prefs.edit().putBoolean("reveal", value).apply() }
    fun experimentalPromotion(): Boolean = prefs.getBoolean("experimental_promotion", false)
    fun setExperimentalPromotion(enabled: Boolean) { prefs.edit().putBoolean("experimental_promotion", enabled).apply() }
    fun active(): Boolean = prefs.getBoolean("session_active", false)
    fun setActive(value: Boolean) { prefs.edit().putBoolean("session_active", value).apply() }
    fun cachedMeaning(vocId: String): String? = prefs.getString("meaning.$vocId", null)
    fun meaningSource(vocId: String): String? = prefs.getString("meaning_source.$vocId", null)
    // A failed official lookup is never marked as checked, so it remains retryable.
    fun officialMeaningCheckedToday(vocId: String): Boolean =
        prefs.getString("meaning_checked.$vocId", null) == StudyDay.today()
    fun markOfficialMeaningCheckedToday(vocId: String) {
        prefs.edit().putString("meaning_checked.$vocId", StudyDay.today()).apply()
    }
    fun saveMeaning(vocId: String, meaning: String) {
        prefs.edit().putString("meaning.$vocId", meaning)
            .putString("meaning_source.$vocId", "official").apply()
    }
    fun clearStudyData() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("meaning.") || it.startsWith("meaning_source.") || it.startsWith("meaning_checked.") || it.startsWith("feedback.") }.forEach { editor.remove(it) }
        editor.remove("official_trial").remove("official_trial_report").remove("momo_inspect.word")
            .remove("momo_inspect.until").remove("momo_inspect.report")
            .remove("snapshot").remove("index").putBoolean("reveal", false).putBoolean("session_active", false).apply()
    }
}
