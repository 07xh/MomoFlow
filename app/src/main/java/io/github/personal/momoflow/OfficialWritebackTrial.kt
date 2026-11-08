package io.github.personal.momoflow

import android.content.Context
import org.json.JSONObject

/** Records a user-initiated MANUAL trial; this never writes to MaiMemo. */
data class OfficialWritebackTrial(
    val wordId: String,
    val spelling: String,
    val expected: LocalMark,
    val day: String,
    val startedAtMs: Long,
    val before: OfficialRecord?,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("word_id", wordId).put("spelling", spelling)
        .put("expected", expected.wire).put("day", day)
        .put("started_at_ms", startedAtMs)
        .put("before", before?.toJson())

    companion object {
        fun parse(json: JSONObject): OfficialWritebackTrial {
            val expected = LocalMark.entries.first { it.wire == json.getString("expected") }
            return OfficialWritebackTrial(
                json.getString("word_id"), json.getString("spelling"), expected,
                json.getString("day"), json.getLong("started_at_ms"),
                json.optJSONObject("before")?.let(OfficialRecord::parse),
            )
        }
    }
}

class OfficialTrialStore(context: Context) {
    private val prefs = context.getSharedPreferences("momoflow_local", Context.MODE_PRIVATE)

    fun save(value: OfficialWritebackTrial) {
        prefs.edit().putString("official_trial", value.toJson().toString()).apply()
    }

    fun pending(): OfficialWritebackTrial? = try {
        prefs.getString("official_trial", null)?.let {
            OfficialWritebackTrial.parse(JSONObject(it)).takeIf { trial -> trial.day == StudyDay.today() }
        }
    } catch (_: Exception) { null }

    fun saveReport(report: String) {
        prefs.edit().putString("official_trial_report", report.take(500)).apply()
    }

    fun lastReport(): String = prefs.getString("official_trial_report", "") ?: ""

    fun clear() { prefs.edit().remove("official_trial").remove("official_trial_report").apply() }
}
