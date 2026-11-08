package io.github.personal.momoflow

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OfficialRecordTest {
    @Test fun parseRealSchemaAndCompare() {
        val before = OfficialRecord.parse(JSONObject("""{
            "voc_id":"word-id", "last_response":"FORGET", "study_count":2,
            "last_study_date":"2026-11-09", "next_study_date":"2026-11-11"
        }"""))
        val after = OfficialRecord.parse(JSONObject("""{
            "voc_id":"word-id", "last_response":"FAMILIAR", "study_count":3,
            "last_study_date":"2026-11-09", "next_study_date":"2026-11-14"
        }"""))
        assertEquals("FAMILIAR", after.lastResponse)
        assertTrue(OfficialRecordComparison.explain(before, after, LocalMark.FAMILIAR).contains("记录已发生变化"))
        assertTrue(OfficialRecordComparison.explain(before, before, LocalMark.FAMILIAR).contains("不能认定"))
        assertTrue(OfficialRecordComparison.explain(before, after, LocalMark.FORGET).contains("不一致"))
    }
}
