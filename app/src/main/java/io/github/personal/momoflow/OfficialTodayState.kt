package io.github.personal.momoflow

import org.json.JSONObject

/** Today's official item as returned by /study/get_today_items for a single voc_id. */
data class OfficialTodayState(
    val vocId: String,
    val spelling: String,
    val firstResponse: String?,
    val isFinished: Boolean,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("voc_id", vocId)
        .put("voc_spelling", spelling)
        .put("first_response", firstResponse)
        .put("is_finished", isFinished)

    companion object {
        fun parse(json: JSONObject): OfficialTodayState {
            val id = json.getString("voc_id")
            require(id.isNotBlank() && json.has("is_finished")) {
                "墨墨今日词条缺少 ID 或完成字段"
            }
            val response = json.optString("first_response", "").trim()
                .takeUnless { it.isEmpty() || it == "null" }
            return OfficialTodayState(
                vocId = id,
                spelling = json.optString("voc_spelling", ""),
                firstResponse = response,
                isFinished = json.getBoolean("is_finished"),
            )
        }
    }
}

/**
 * Compares only official read-only observations. Never claims MomoFlow wrote a
 * response. Multiple official fields can change asynchronously or during replays.
 */
object OfficialSyncAssessment {
    fun explain(
        before: OfficialRecord?,
        after: OfficialRecord?,
        beforeToday: OfficialTodayState?,
        afterToday: OfficialTodayState?,
        expected: LocalMark,
    ): String {
        val allIds = listOfNotNull(
            before?.vocId, after?.vocId, beforeToday?.vocId, afterToday?.vocId
        ).distinct()
        if (allIds.size > 1) return "官方读取的单词 ID 不一致，本次结果作废。"
        if (before == after && beforeToday == afterToday) {
            return "暂未观察到墨墨服务端字段变化；可能尚未同步，不能认定已提交。请先让墨墨正常同步，再核验一次。"
        }

        val firstChanged = afterToday?.firstResponse == expected.wire &&
            beforeToday?.firstResponse != expected.wire
        val latestChanged = after?.lastResponse == expected.wire &&
            before?.lastResponse != expected.wire

        val evidence = mutableListOf<String>()
        if (before?.lastResponse != after?.lastResponse) {
            evidence += "最近反馈：${before?.lastResponse ?: "无"} → ${after?.lastResponse ?: "无"}"
        }
        if (before?.studyCount != after?.studyCount) {
            evidence += "学习次数：${before?.studyCount?.toString() ?: "无"} → ${after?.studyCount?.toString() ?: "无"}"
        }
        if (before?.nextStudyDate != after?.nextStudyDate) {
            evidence += "下次复习日期：${before?.nextStudyDate ?: "无"} → ${after?.nextStudyDate ?: "无"}"
        }
        if (beforeToday?.firstResponse != afterToday?.firstResponse) {
            evidence += "今日首次反馈：${beforeToday?.firstResponse ?: "无"} → ${afterToday?.firstResponse ?: "无"}"
        }
        if (beforeToday?.isFinished != afterToday?.isFinished) {
            evidence += "今日完成状态：${beforeToday?.isFinished?.toString() ?: "无"} → ${afterToday?.isFinished?.toString() ?: "无"}"
        }

        val summary = evidence.joinToString("；").ifBlank { "官方记录的其他字段发生变化" }
        return when {
            after?.lastResponse != null && after.lastResponse != expected.wire &&
                afterToday?.firstResponse != expected.wire ->
                "官方数据变化，但反馈类型与本次预期${expected.label}不匹配。$summary。不得判定这次操作成功。"
            firstChanged || latestChanged ->
                "已观察到目标单词的墨墨官方数据变化，反馈类型与${expected.label}相符。$summary。这说明服务端状态更新，但无法仅凭对照证明更新由 MomoFlow 发起。"
            else ->
                "官方数据有变化，但没有足够证据证明本次${expected.label}反馈已同步。$summary。"
        }
    }
}
