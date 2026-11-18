package io.github.personal.momoflow

import org.junit.Assert.*
import org.junit.Test

class FeedbackOutcomePolicyTest {
    @Test fun serverReadbackHasThreeConservativeStates() {
        assertEquals(FeedbackOutcome.SUCCEEDED,
            FeedbackOutcomePolicy.fromOfficialReadback(OfficialSyncStatus.OBSERVED_MATCH))
        assertEquals(FeedbackOutcome.FAILED,
            FeedbackOutcomePolicy.fromOfficialReadback(OfficialSyncStatus.CONFLICT))
        assertEquals(FeedbackOutcome.FAILED,
            FeedbackOutcomePolicy.fromOfficialReadback(OfficialSyncStatus.INVALID_WORD))
        for (unresolved in listOf(
            OfficialSyncStatus.INCOMPLETE, OfficialSyncStatus.UNCHANGED,
            OfficialSyncStatus.UNCONFIRMED_CHANGE
        )) assertEquals(FeedbackOutcome.PENDING,
            FeedbackOutcomePolicy.fromOfficialReadback(unresolved))
    }

    @Test fun neverConfusesAndroidLocalMarkWithUnverifiedWireEnum() {
        val adapter = CortexStudyAdapter()
        val sample = StudyWord("test-12", "example", 1, false, false)
        assertEquals(setOf(CortexResponseTag.FAMILIAR),
            adapter.draft(sample, LocalMark.FAMILIAR).responseCandidates)
        assertEquals(setOf(CortexResponseTag.UNCERTAIN, CortexResponseTag.VAGUE),
            adapter.draft(sample, LocalMark.VAGUE).responseCandidates)
        assertEquals(setOf(CortexResponseTag.FORGET),
            adapter.draft(sample, LocalMark.FORGET).responseCandidates)
        assertEquals(CortexSubmissionState.NOT_SENT_UNVERIFIED,
            adapter.submit(adapter.draft(sample, LocalMark.FORGET)).state)
    }
}
