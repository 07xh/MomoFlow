package io.github.personal.momoflow

import org.json.JSONObject

/** Read-only projection of an official MaiMemo study record. */
data class OfficialRecord(
    val vocId: String,
    val lastResponse: String?,
    val studyCount: Int,
    val lastStudyDate: String?,
    val nextStudyDate: String?,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("voc_id", vocId)
        .put("last_response", lastResponse)
        .put("study_count", studyCount)
        .put("last_study_date", lastStudyDate)
        .put("next_study_date", nextStudyDate)

    companion object {
        fun parse(json: JSONObject) = OfficialRecord(
            vocId = json.getString("voc_id"),
            lastResponse = json.optString("last_response").ifBlank { null },
            studyCount = json.optInt("study_count", 0),
            lastStudyDate = json.optString("last_study_date").ifBlank { null },
            nextStudyDate = json.optString("next_study_date").ifBlank { null },
        )
    }
}

/** This is evidence of an observed change, not proof that our UI originated it. */
object OfficialRecordComparison {
    fun explain(before: OfficialRecord?, after: OfficialRecord?, expected: LocalMark): String {
        if (before == after) return "官方记录尚未发现变化。可能需要等待墨墨同步；不能认定写入成功。"
        if (after == null) return "没有获取到新的官方记录；不能认定写入成功。"
        val changed = when {
            before == null -> "墨墨现在返回了此词的学习记录"
            else -> "墨墨学习记录已发生变化"
        }
        val observed = after.lastResponse ?: "未提供"
        return if (after.lastResponse == expected.wire) {
            "$changed，最近反馈显示 ${expected.label}。这说明墨墨记录已更新，但还需核对是否对应本次操作。"
        } else {
            "$changed，但最近反馈为 $observed，与预期的${expected.label}不一致；不可标记为回写成功。"
        }
    }
}
