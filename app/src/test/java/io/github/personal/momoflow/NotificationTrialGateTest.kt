package io.github.personal.momoflow

import org.junit.Assert.*
import org.junit.Test

class NotificationTrialGateTest {
    private val first = StudyWord("id-1", "similar", 1, false, false)
    private val second = StudyWord("id-2", "reliable", 2, false, false)
    private val snapshot = StudySnapshot(
        "2026-11-09", StudyProgress(1, 3, 1000), listOf(first, second), 123L
    )

    @Test fun explicitCurrentWordActionCanStartAManualTrial() {
        val choice = NotificationTrialGate.select(snapshot, true, true, 0, "id-1", "FAMILIAR")
        assertEquals(first, choice?.word)
        assertEquals(LocalMark.FAMILIAR, choice?.expected)
    }

    @Test fun rejectsStaleWordAfterSwipingToTheNextWord() {
        assertNull(NotificationTrialGate.select(snapshot, true, true, 1, "id-1", "FORGET"))
    }

    @Test fun rejectsExpiredDayAndClosedSession() {
        assertNull(NotificationTrialGate.select(snapshot, false, true, 0, "id-1", "VAGUE"))
        assertNull(NotificationTrialGate.select(snapshot, true, false, 0, "id-1", "VAGUE"))
    }

    @Test fun rejectsForgedUnknownActionAndMissingWord() {
        assertNull(NotificationTrialGate.select(snapshot, true, true, 0, "id-1", "WRITEBACK"))
        assertNull(NotificationTrialGate.select(snapshot, true, true, 0, null, "FAMILIAR"))
        assertNull(NotificationTrialGate.select(null, true, true, 0, "id-1", "FAMILIAR"))
    }

    @Test fun rejectsInvalidCurrentIndex() {
        assertNull(NotificationTrialGate.select(snapshot, true, true, 9, "id-2", "FAMILIAR"))
    }
    @Test fun duplicateSpellingWithDifferentIdsCannotSelectAutoAction() {
        val duplicate = StudyWord("id-3", "similar", 3, false, false)
        val ambiguous = snapshot.copy(words = listOf(first, duplicate, second))
        assertNull(NotificationTrialGate.select(ambiguous, true, true, 0, "id-1", "FAMILIAR"))
    }
    @Test fun rejectsAlreadyCompletedWord() {
        val finished = snapshot.copy(words = listOf(first.copy(isFinished = true)))
        assertNull(NotificationTrialGate.select(finished, true, true, 0, "id-1", "FAMILIAR"))
    }

    @Test fun rejectsEmptyOfficialSpelling() {
        val malformed = snapshot.copy(words = listOf(first.copy(spelling = " ")))
        assertNull(NotificationTrialGate.select(malformed, true, true, 0, "id-1", "FORGET"))
    }
}
