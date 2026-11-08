package io.github.personal.momoflow

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StudyModelsTest {
    @Test fun parsesOfficialTodayItemSchema() {
        val item = StudyWord.parse(JSONObject("""{
          "voc_id":"v-1", "voc_spelling":"resilient", "order":3,
          "is_new":false, "is_finished":false
        }"""))
        assertEquals("v-1", item.id)
        assertEquals("resilient", item.spelling)
        assertEquals(3, item.order)
        assertFalse(item.isFinished)
    }

    @Test fun snapshotRoundTripKeepsOfficialStatusUnchanged() {
        val item = StudyWord("v-2", "circuit", 0, true, false)
        val progress = StudyProgress(2, 10, 9_000)
        val snapshot = StudySnapshot("2026-11-09", progress, listOf(item), 123L)
        val parsed = StudySnapshot.parse(snapshot.toJson())
        assertEquals(snapshot, parsed)
        assertEquals(2, parsed.progress.finished)
        assertFalse(parsed.words.single().isFinished)
    }

    @Test fun rejectsEmptyVocabularyIds() {
        try {
            StudyWord.parse(JSONObject("""{"voc_id":"", "voc_spelling":"test"}"""))
            fail("Missing ID must be rejected")
        } catch (expected: IllegalArgumentException) {
            // valid
        }
    }
}
