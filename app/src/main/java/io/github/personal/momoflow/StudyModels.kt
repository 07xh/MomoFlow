package io.github.personal.momoflow

import org.json.JSONArray
import org.json.JSONObject

/** Mirrors the public MaiMemo study API; no synthetic study data is generated. */
data class StudyWord(val id: String, val spelling: String, val order: Int, val isNew: Boolean, val isFinished: Boolean) {
    fun toJson() = JSONObject().put("voc_id", id).put("voc_spelling", spelling)
        .put("order", order).put("is_new", isNew).put("is_finished", isFinished)

    companion object {
        fun parse(json: JSONObject): StudyWord {
            val id = json.getString("voc_id")
            val spelling = json.getString("voc_spelling")
            require(id.isNotBlank() && spelling.isNotBlank()) { "词条缺少必要字段" }
            return StudyWord(id, spelling, json.optInt("order"), json.optBoolean("is_new"), json.optBoolean("is_finished"))
        }
    }
}

data class StudyProgress(val finished: Int, val total: Int, val timeMs: Long) {
    fun toJson() = JSONObject().put("finished", finished).put("total", total).put("study_time", timeMs)
    companion object {
        fun parse(json: JSONObject) = StudyProgress(json.getInt("finished"), json.getInt("total"), json.optLong("study_time"))
    }
}

data class StudySnapshot(val date: String, val progress: StudyProgress, val words: List<StudyWord>, val updatedAtMs: Long) {
    fun toJson(): JSONObject = JSONObject().put("date", date).put("progress", progress.toJson())
        .put("today_items", JSONArray().apply { words.forEach { put(it.toJson()) } })
        .put("updated_at", updatedAtMs)

    companion object {
        fun parse(json: JSONObject): StudySnapshot {
            val a = json.getJSONArray("today_items")
            return StudySnapshot(json.getString("date"), StudyProgress.parse(json.getJSONObject("progress")),
                (0 until a.length()).map { StudyWord.parse(a.getJSONObject(it)) }, json.optLong("updated_at"))
        }
    }
}
