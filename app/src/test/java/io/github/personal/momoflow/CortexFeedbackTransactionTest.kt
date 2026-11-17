package io.github.personal.momoflow

import org.junit.Assert.*
import org.junit.Test

class CortexFeedbackTransactionTest {
    private val word = StudyWord("voc-17", "circuit", 0, false, false)
    private val adapter = CortexStudyAdapter()
    private val before = CortexOfficialSnapshot(
        OfficialRecord("voc-17", "FORGET", 2, "2026-11-18", "2026-11-20"),
        OfficialTodayState("voc-17", "circuit", "FORGET", false)
    )
    private val after = CortexOfficialSnapshot(
        OfficialRecord("voc-17", "FAMILIAR", 3, "2026-11-18", "2026-11-22"),
        OfficialTodayState("voc-17", "circuit", "FAMILIAR", true)
    )

    @Test fun unconfiguredTransportNeverClaimsSubmission() {
        var reads = 0
        val tx = CortexFeedbackTransaction(CortexOfficialReader { reads++; before })
        val result = tx.run(adapter.draft(word, LocalMark.FAMILIAR), "op-a") { true }
        assertEquals(CortexTransactionStatus.NOT_SENT, result.status)
        assertFalse(result.sendAttempted)
        assertEquals(1, reads)
    }

    @Test fun sameOperationIdCannotBeSentTwice() {
        var sends = 0
        val tx = CortexFeedbackTransaction(
            CortexOfficialReader { before },
            CortexAuthorizedSender { _, _ -> sends++; CortexDispatchVerdict.REJECTED }
        )
        val draft = adapter.draft(word, LocalMark.FAMILIAR)
        assertEquals(CortexTransactionStatus.REJECTED, tx.run(draft, "same-id") { true }.status)
        assertEquals(CortexTransactionStatus.DUPLICATE_OPERATION, tx.run(draft, "same-id") { true }.status)
        assertEquals(1, sends)
    }

    @Test fun ambiguousVagueNeverEntersTransport() {
        var sends = 0
        val tx = CortexFeedbackTransaction(
            CortexOfficialReader { before },
            CortexAuthorizedSender { _, _ -> sends++; CortexDispatchVerdict.ACCEPTED_UNVERIFIED }
        )
        assertEquals(
            CortexTransactionStatus.AMBIGUOUS_RESPONSE,
            tx.run(adapter.draft(word, LocalMark.VAGUE), "op-b") { true }.status
        )
        assertEquals(0, sends)
    }

    @Test fun mismatchedBaselineBlocksWithoutNetworkAttempt() {
        var sends = 0
        val tx = CortexFeedbackTransaction(
            CortexOfficialReader { before.copy(today = before.today!!.copy(vocId = "other")) },
            CortexAuthorizedSender { _, _ -> sends++; CortexDispatchVerdict.ACCEPTED_UNVERIFIED }
        )
        assertEquals(CortexTransactionStatus.MISSING_BASELINE,
            tx.run(adapter.draft(word, LocalMark.FAMILIAR), "op-c") { true }.status)
        assertEquals(0, sends)
    }

    @Test fun acceptedResponseNeedsRealMatchingReadback() {
        var reads = 0
        val tx = CortexFeedbackTransaction(
            CortexOfficialReader { reads++; if (reads == 1) before else after },
            CortexAuthorizedSender { _, _ -> CortexDispatchVerdict.ACCEPTED_UNVERIFIED }
        )
        val result = tx.run(adapter.draft(word, LocalMark.FAMILIAR), "op-d") { true }
        assertEquals(CortexTransactionStatus.OBSERVED_MATCH, result.status)
        assertEquals(OfficialSyncStatus.OBSERVED_MATCH, result.officialReadback)
        assertTrue(result.sendAttempted)
    }

    @Test fun unchangedRecordsArePendingNotSuccess() {
        val tx = CortexFeedbackTransaction(
            CortexOfficialReader { before },
            CortexAuthorizedSender { _, _ -> CortexDispatchVerdict.ACCEPTED_UNVERIFIED }
        )
        assertEquals(CortexTransactionStatus.READBACK_PENDING,
            tx.run(adapter.draft(word, LocalMark.FAMILIAR), "op-e") { true }.status)
    }

    @Test fun timeoutNeverResendsAndNeverAssumesSuccess() {
        var sends = 0
        val tx = CortexFeedbackTransaction(
            CortexOfficialReader { before },
            CortexAuthorizedSender { _, _ -> sends++; throw IllegalStateException("timeout") }
        )
        val draft = adapter.draft(word, LocalMark.FAMILIAR)
        assertEquals(CortexTransactionStatus.OUTCOME_UNKNOWN, tx.run(draft, "op-f") { true }.status)
        assertEquals(CortexTransactionStatus.DUPLICATE_OPERATION, tx.run(draft, "op-f") { true }.status)
        assertEquals(1, sends)
    }

    @Test fun unauthorizedAccountChangeStopsBeforeSend() {
        var sends = 0
        val tx = CortexFeedbackTransaction(
            CortexOfficialReader { before },
            CortexAuthorizedSender { _, _ -> sends++; CortexDispatchVerdict.ACCEPTED_UNVERIFIED }
        )
        assertEquals(CortexTransactionStatus.ACCOUNT_CHANGED,
            tx.run(adapter.draft(word, LocalMark.FAMILIAR), "op-g") { false }.status)
        assertEquals(0, sends)
    }

    @Test fun contradictoryOfficialAnswerIsAConflict() {
        val tx = CortexFeedbackTransaction(
            CortexOfficialReader { if (readCall++ == 0) before else after.copy(
                record = after.record!!.copy(lastResponse = "VAGUE")
            ) },
            CortexAuthorizedSender { _, _ -> CortexDispatchVerdict.ACCEPTED_UNVERIFIED }
        )
        assertEquals(CortexTransactionStatus.READBACK_CONFLICT,
            tx.run(adapter.draft(word, LocalMark.FAMILIAR), "op-h") { true }.status)
    }

    private var readCall = 0
}
