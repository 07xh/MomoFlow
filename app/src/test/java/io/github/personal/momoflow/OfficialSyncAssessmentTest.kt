package io.github.personal.momoflow

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OfficialSyncAssessmentTest {
    private fun record(response: String, count: Int = 2, next: String = "2026-11-11") =
        OfficialRecord("voc-1", response, count, "2026-11-09", next)

    @Test fun todayStateParsesRealFirstResponseAndCompletion() {
        val item = OfficialTodayState.parse(JSONObject("""
          {"voc_id":"voc-1","voc_spelling":"important","first_response":"FAMILIAR","is_finished":true}
        """.trimIndent()))
        assertEquals("FAMILIAR", item.firstResponse)
        assertTrue(item.isFinished)
        val again = OfficialTodayState.parse(item.toJson())
        assertEquals(item, again)
    }

    @Test fun firstResponseConfirmsObservedServerChange() {
        val beforeToday = OfficialTodayState("voc-1", "important", null, false)
        val afterToday = beforeToday.copy(firstResponse = "FAMILIAR", isFinished = true)
        val message = OfficialSyncAssessment.explain(
            record("FORGET"), record("FAMILIAR", 3, "2026-11-15"),
            beforeToday, afterToday, LocalMark.FAMILIAR
        )
        assertTrue(message.contains("服务端状态更新"))
        assertTrue(message.contains("今日首次反馈"))
    }

    @Test fun noChangeNeverMeansSuccess() {
        val state = OfficialTodayState("voc-1", "important", "FORGET", false)
        val message = OfficialSyncAssessment.explain(
            record("FORGET"), record("FORGET"), state, state, LocalMark.FORGET
        )
        assertTrue(message.contains("不能认定"))
    }

    @Test fun wrongResponseIsNotAConfirmedSubmission() {
        val before = OfficialTodayState("voc-1", "important", null, false)
        val after = before.copy(firstResponse = "VAGUE")
        val message = OfficialSyncAssessment.explain(
            record("FORGET"), record("VAGUE", 3), before, after, LocalMark.FAMILIAR
        )
        assertTrue(message.contains("不匹配"))
    }

    @Test fun mismatchedIdInvalidatesComparison() {
        val message = OfficialSyncAssessment.explain(
            record("FORGET"), record("FAMILIAR", 3),
            OfficialTodayState("voc-1", "important", null, false),
            OfficialTodayState("voc-2", "important", "FAMILIAR", true),
            LocalMark.FAMILIAR
        )
        assertTrue(message.contains("ID 不一致"))
    }
}
