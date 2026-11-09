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

    @Test fun contradictoryLatestFeedbackCannotBeCalledAMatch() {
        val beforeToday = OfficialTodayState("voc-1", "important", null, false)
        val afterToday = beforeToday.copy(firstResponse = "FAMILIAR", isFinished = true)
        val result = OfficialSyncAssessment.evaluate(
            record("FORGET"), record("VAGUE", 3),
            beforeToday, afterToday, LocalMark.FAMILIAR
        )
        assertEquals(OfficialSyncStatus.CONFLICT, result.status)
        assertTrue(result.message.contains("不匹配"))
    }

    @Test fun missingBeforeTodayIsIncompleteNotPositive() {
        val afterToday = OfficialTodayState("voc-1", "important", "FAMILIAR", true)
        val result = OfficialSyncAssessment.evaluate(
            record("FORGET"), record("FAMILIAR", 3),
            null, afterToday, LocalMark.FAMILIAR
        )
        assertEquals(OfficialSyncStatus.INCOMPLETE, result.status)
        assertTrue(result.message.contains("不能判断"))
    }

    @Test fun vanishedHistoricalRecordIsIncomplete() {
        val today = OfficialTodayState("voc-1", "important", null, false)
        val result = OfficialSyncAssessment.evaluate(
            record("FORGET"), null,
            today, today.copy(firstResponse = "FAMILIAR"), LocalMark.FAMILIAR
        )
        assertEquals(OfficialSyncStatus.INCOMPLETE, result.status)
    }

    @Test fun onlyCompletionChangedIsNotEvidenceOfExpectedAnswer() {
        val beforeToday = OfficialTodayState("voc-1", "important", "FORGET", false)
        val result = OfficialSyncAssessment.evaluate(
            record("FORGET"), record("FORGET", 3),
            beforeToday, beforeToday.copy(isFinished = true), LocalMark.FAMILIAR
        )
        assertEquals(OfficialSyncStatus.UNCONFIRMED_CHANGE, result.status)
    }

    @Test fun changedFirstResponseAloneCanProveObservedOfficialState() {
        val beforeToday = OfficialTodayState("voc-1", "important", null, false)
        val result = OfficialSyncAssessment.evaluate(
            null, null, beforeToday, beforeToday.copy(firstResponse = "VAGUE"),
            LocalMark.VAGUE
        )
        assertEquals(OfficialSyncStatus.OBSERVED_MATCH, result.status)
        assertTrue(result.message.contains("无法仅凭对照证明"))
    }

}
