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

/** A server readback verdict, never a claim that MomoFlow submitted feedback. */
enum class OfficialSyncStatus {
    INVALID_WORD,
    INCOMPLETE,
    UNCHANGED,
    CONFLICT,
    OBSERVED_MATCH,
    UNCONFIRMED_CHANGE,
}

data class OfficialSyncResult(val status: OfficialSyncStatus, val message: String)

/**
 * Compare the SAME target word in the read-only official study-record and
 * today's-list endpoints. A changed completion flag or study count alone is
 * not evidence of the expected answer, and contradictory changed response
 * fields must never be promoted as a successful match.
 */
object OfficialSyncAssessment {
    fun evaluate(
        before: OfficialRecord?,
        after: OfficialRecord?,
        beforeToday: OfficialTodayState?,
        afterToday: OfficialTodayState?,
        expected: LocalMark,
    ): OfficialSyncResult {
        val allIds = listOfNotNull(
            before?.vocId, after?.vocId, beforeToday?.vocId, afterToday?.vocId
        ).distinct()
        if (allIds.size > 1) return OfficialSyncResult(
            OfficialSyncStatus.INVALID_WORD,
            "官方读取的单词 ID 不一致，本次结果作废。"
        )
        if (beforeToday == null || afterToday == null || (before != null && after == null)) {
            return OfficialSyncResult(
                OfficialSyncStatus.INCOMPLETE,
                "本次官方数据不完整（今日词条或历史学习记录缺失），不能判断反馈是否同步。请让墨墨正常同步后重新读取。"
            )
        }
        if (before == after && beforeToday == afterToday) return OfficialSyncResult(
            OfficialSyncStatus.UNCHANGED,
            "暂未观察到墨墨服务端字段变化；可能尚未同步，不能认定已提交。请先让墨墨正常同步，再核验一次。"
        )

        val lastChanged = before?.lastResponse != after?.lastResponse
        val firstChanged = beforeToday.firstResponse != afterToday.firstResponse

        val evidence = mutableListOf<String>()
        if (lastChanged) {
            evidence += "最近反馈：${before?.lastResponse ?: "无"} → ${after?.lastResponse ?: "无"}"
        }
        if (before?.studyCount != after?.studyCount) {
            evidence += "学习次数：${before?.studyCount?.toString() ?: "无"} → ${after?.studyCount?.toString() ?: "无"}"
        }
        if (before?.nextStudyDate != after?.nextStudyDate) {
            evidence += "下次复习日期：${before?.nextStudyDate ?: "无"} → ${after?.nextStudyDate ?: "无"}"
        }
        if (firstChanged) {
            evidence += "今日首次反馈：${beforeToday.firstResponse ?: "无"} → ${afterToday.firstResponse ?: "无"}"
        }
        if (beforeToday.isFinished != afterToday.isFinished) {
            evidence += "今日完成状态：${beforeToday.isFinished} → ${afterToday.isFinished}"
        }
        val summary = evidence.joinToString("；").ifBlank { "官方记录的其他字段发生变化" }

        val conflictingLast = lastChanged && after?.lastResponse != null &&
            after.lastResponse != expected.wire
        val conflictingFirst = firstChanged && afterToday.firstResponse != null &&
            afterToday.firstResponse != expected.wire
        if (conflictingLast || conflictingFirst) {
            return OfficialSyncResult(
                OfficialSyncStatus.CONFLICT,
                "官方数据变化，但新反馈类型与本次预期${expected.label}不匹配（或两个字段互相矛盾）。$summary。不得判定这次操作成功。"
            )
        }

        val matchedLast = lastChanged && after?.lastResponse == expected.wire
        val matchedFirst = firstChanged && afterToday.firstResponse == expected.wire
        if (matchedLast || matchedFirst) {
            return OfficialSyncResult(
                OfficialSyncStatus.OBSERVED_MATCH,
                "已观察到目标单词的墨墨官方数据变化，反馈类型与${expected.label}相符。$summary。这说明服务端状态更新，但无法仅凭对照证明更新由 MomoFlow 发起。"
            )
        }

        return OfficialSyncResult(
            OfficialSyncStatus.UNCONFIRMED_CHANGE,
            "官方数据有变化，但没有足够证据证明本次${expected.label}反馈已同步。$summary。"
        )
    }

    /** Backward-compatible display text used by the current Android screen. */
    fun explain(
        before: OfficialRecord?,
        after: OfficialRecord?,
        beforeToday: OfficialTodayState?,
        afterToday: OfficialTodayState?,
        expected: LocalMark,
    ): String = evaluate(before, after, beforeToday, afterToday, expected).message
}
