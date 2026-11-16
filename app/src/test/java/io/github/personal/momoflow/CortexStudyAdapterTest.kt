package io.github.personal.momoflow

import org.junit.Assert.*
import org.junit.Test

class CortexStudyAdapterTest {
    private val adapter = CortexStudyAdapter()
    private val word = StudyWord("voc-001", "resilient", 1, false, false)

    @Test fun pathsRemainDiscoveryMetadataNotPublicApiPaths() {
        assertEquals(
            "https://cortex.maimemo.com/cortex/api/v1/study/oplog",
            CortexStudyContract.endpointUrl(CortexStudyContract.Endpoint.UPLOAD_OPLOG)
        )
        assertEquals(
            "/api/v1/study/merge/memo",
            CortexStudyContract.Endpoint.MERGE_MEMO.path
        )
        assertNotEquals(
            "https://open.maimemo.com/open/api/v1",
            CortexStudyContract.BASE_URL
        )
    }

    @Test fun keepsAllThreeUserChoicesWithoutAssumingWireNumbers() {
        val known = adapter.draft(word, LocalMark.FAMILIAR)
        val vague = adapter.draft(word, LocalMark.VAGUE)
        val forgotten = adapter.draft(word, LocalMark.FORGET)

        assertEquals("voc-001", known.vocId)
        assertEquals(LocalMark.FAMILIAR, known.selectedMark)
        assertEquals(setOf(CortexResponseTag.FAMILIAR), known.responseCandidates)
        assertEquals(
            setOf(CortexResponseTag.UNCERTAIN, CortexResponseTag.VAGUE),
            vague.responseCandidates
        )
        assertEquals(setOf(CortexResponseTag.FORGET), forgotten.responseCandidates)
        assertEquals("RESPONSE_FAMILIAR", CortexResponseTag.FAMILIAR.protoName)
        assertEquals("RESPONSE_FORGET", CortexResponseTag.FORGET.protoName)
    }

    @Test fun blocksEverySubmissionEvenWhenDraftIsPresent() {
        for (mark in LocalMark.entries) {
            val result = adapter.submit(adapter.draft(word, mark))
            assertEquals(CortexSubmissionState.NOT_SENT_UNVERIFIED, result.state)
            assertFalse(result.networkAttempted)
            assertEquals(CortexEvidenceGap.entries.toSet(), result.missing)
            assertTrue(CortexEvidenceGap.REQUEST_SCHEMA in result.missing)
            assertTrue(CortexEvidenceGap.READBACK in result.missing)
        }
    }

    @Test fun invalidIdsCannotEnterDraftOrBeTreatedAsVerified() {
        try {
            adapter.draft(word.copy(id = " "), LocalMark.FORGET)
            fail("Blank word IDs must be rejected")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
        assertNotNull(adapter.missingEvidence)
        assertTrue(adapter.missingEvidence.isNotEmpty())
    }
}
