package io.github.personal.momoflow

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/** A private practice log only. NEVER sent to MaiMemo or counted in official progress. */
enum class LocalMark(val wire: String, val label: String) {
    FAMILIAR("FAMILIAR", "认识"),
    VAGUE("VAGUE", "模糊"),
    FORGET("FORGET", "忘记")
}

object StudyDay {
    // MaiMemo schedules are defined in China time, not the device's roaming time zone.
    fun today(): String = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()
}

class LocalFeedbackStore(context: Context) {
    private val prefs = context.getSharedPreferences("momoflow_local", Context.MODE_PRIVATE)

    private fun key(date: String) = "feedback.$date"

    private fun read(date: String): JSONObject = try {
        JSONObject(prefs.getString(key(date), "{}") ?: "{}")
    } catch (_: Exception) { JSONObject() }

    fun mark(vocId: String, value: LocalMark, date: String = StudyDay.today()) {
        require(vocId.isNotBlank())
        val state = read(date)
        state.put(vocId, JSONObject()
            .put("mark", value.wire)
            .put("time", System.currentTimeMillis()))
        prefs.edit().putString(key(date), state.toString()).apply()
    }

    fun markFor(vocId: String, date: String = StudyDay.today()): LocalMark? {
        val value = read(date).optJSONObject(vocId)?.optString("mark") ?: return null
        return LocalMark.entries.firstOrNull { it.wire == value }
    }

    fun counts(date: String = StudyDay.today()): Map<LocalMark, Int> {
        val state = read(date)
        val tally = LocalMark.entries.associateWith { 0 }.toMutableMap()
        val keys = state.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = LocalMark.entries.firstOrNull { it.wire == state.optJSONObject(key)?.optString("mark") }
            if (value != null) tally[value] = (tally[value] ?: 0) + 1
        }
        return tally
    }
}
