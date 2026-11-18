package io.github.personal.momoflow

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CortexWireCodecTest {
    private val evidence = JSONObject("""{
        "kind":"cortex_wire_evidence_v1",
        "source":"device_runtime_capture","sample_count":3,
        "ready_for_production_write":false,
        "wasm_oplog":{"wrapper_field":13,"voc_id_field":7,"response_field":4,
            "enum_numbers":{"FAMILIAR":11,"UNCERTAIN":9,"FORGET":17}},
        "http":{"method":"POST","path":"/api/v1/study/oplog",
            "json_fields":{"checkpoint":"int","oplog":"str"}}
    }""")

    @Test fun encodesNonDefaultCapturedFields() {
        val profile = CortexWireProfile.fromEvidenceJson(evidence)
        assertArrayEquals(
            byteArrayOf(0x6a, 0x05, 0x38, 0xc1.toByte(), 0x3e, 0x20, 0x0b),
            CortexWireCodec.encode("8001", LocalMark.FAMILIAR, profile)
        )
    }

    @Test fun vagueMeansWasmUncertainNotCortexVague() {
        val profile = CortexWireProfile.fromEvidenceJson(evidence)
        assertEquals(9, profile.enumNumber(LocalMark.VAGUE))
        assertEquals(17, profile.enumNumber(LocalMark.FORGET))
    }

    @Test fun refusesInvalidWordIds() {
        val profile = CortexWireProfile.fromEvidenceJson(evidence)
        for (id in listOf("-1", "a", "", "18446744073709551616")) {
            try {
                CortexWireCodec.encode(id, LocalMark.FORGET, profile)
                fail("invalid ID accepted")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun cannotTreatSyntheticEvidenceAsProductionApproval() {
        val bogus = JSONObject(evidence.toString()).put("source", "_selftest_dump.json")
        try {
            CortexWireProfile.fromEvidenceJson(bogus)
            fail("synthetic source accepted")
        } catch (_: IllegalArgumentException) { }

        val fakeReady = JSONObject(evidence.toString()).put("ready_for_production_write", true)
        try {
            CortexWireProfile.fromEvidenceJson(fakeReady)
            fail("fake production permission accepted")
        } catch (_: IllegalArgumentException) { }
    }
}
