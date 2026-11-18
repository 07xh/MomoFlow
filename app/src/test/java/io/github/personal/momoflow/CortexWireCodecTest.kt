package io.github.personal.momoflow

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class CortexWireCodecTest {
    private val numbers = CortexWireNumbers(
        vocField = 1, responseField = 2, wrapField = 1,
        responseCodes = mapOf(
            LocalMark.FAMILIAR to 1,
            LocalMark.VAGUE to 2,
            LocalMark.FORGET to 4,
        )
    )

    @Test fun exactBytesForReferenceEncodingOnly() {
        // A locally generated reference, NOT an observed network trace.
        val bytes = CortexWireCodec.encodeOp(12345, LocalMark.FAMILIAR, numbers)
        assertEquals("CgUIuWAQAQ==", Base64.getEncoder().encodeToString(bytes))
    }

    @Test fun realSampleVerifierRequiresAllThreeChoices() {
        val samples = LocalMark.entries.mapIndexed { i, mark ->
            CortexWireSample(100L + i, mark, 55L,
                CortexWireCodec.encodeBase64(100L + i, mark, numbers))
        }
        assertNotNull(CortexWireCodec.verify(numbers, samples))
        try {
            CortexWireCodec.verify(numbers, samples.take(2))
            fail("No partial profile")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun alteredProtobufMustNeverValidate() {
        val samples = LocalMark.entries.mapIndexed { i, mark ->
            CortexWireSample(100L + i, mark, 55L,
                CortexWireCodec.encodeBase64(100L + i, mark, numbers))
        }.toMutableList()
        samples[1] = samples[1].copy(oplogBase64 = samples[0].oplogBase64)
        try {
            CortexWireCodec.verify(numbers, samples)
            fail("Mismatched actual bytes must be rejected")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun numericIdAndDistinctResponseCodesRequired() {
        try {
            CortexWireCodec.encodeOp(0, LocalMark.FORGET, numbers)
            fail("Zero voc_id must be rejected")
        } catch (_: IllegalArgumentException) {}
        try {
            numbers.copy(responseCodes = mapOf(
                LocalMark.FAMILIAR to 1, LocalMark.VAGUE to 1, LocalMark.FORGET to 4
            ))
            fail("Ambiguous response mapping must be rejected")
        } catch (_: IllegalArgumentException) {}
    }
}
