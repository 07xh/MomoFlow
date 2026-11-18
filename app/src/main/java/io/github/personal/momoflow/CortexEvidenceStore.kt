package io.github.personal.momoflow

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray

/**
 * Imports locally observed *complete* request fixtures. Does not retain the
 * sample word IDs, request bytes or authorization material.
 *
 * Sample schema (local-only):
 * {"voc_field":1,"response_field":2,"wrap_field":1,
 *  "responses":{"FAMILIAR":1,"VAGUE":2,"FORGET":4},
 *  "samples":[{"word_id":123,"mark":"FAMILIAR","checkpoint":42,
 *              "oplog":"<base64>","body":{"checkpoint":42,"oplog":"<base64>"}}]}
 *
 * The numbers above are merely illustrative; NEVER accept them without a
 * matching real trace for each button and request envelope.
 */
class CortexEvidenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("momoflow_local", Context.MODE_PRIVATE)

    fun importFromLocalTrace(text: String): CortexVerifiedWire {
        val input = JSONObject(text)
        val codes = input.getJSONObject("responses")
        val numbers = CortexWireNumbers(
            input.getInt("voc_field"), input.getInt("response_field"),
            input.getInt("wrap_field"),
            LocalMark.entries.associateWith { codes.getInt(it.wire) }
        )
        val rows = input.getJSONArray("samples")
        val samples = ArrayList<CortexWireSample>()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val mark = LocalMark.entries.first { it.wire == row.getString("mark") }
            val sample = CortexWireSample(
                row.getLong("word_id"), mark, row.getLong("checkpoint"), row.getString("oplog")
            )
            val observedBody = row.getJSONObject("body")
            val keys = observedBody.keys().asSequence().toSet()
            require(keys == setOf("checkpoint", "oplog")) {
                "Observed upload JSON has different fields: cannot assume the envelope"
            }
            require(observedBody.opt("checkpoint") is Number &&
                observedBody.getLong("checkpoint") == sample.checkpoint &&
                observedBody.getString("oplog") == sample.oplogBase64
            ) { "Trace envelope does not match oplog and checkpoint" }
            samples += sample
        }
        val verified = CortexWireCodec.verify(numbers, samples)
        val safe = JSONObject()
            .put("voc_field", numbers.vocField)
            .put("response_field", numbers.responseField)
            .put("wrap_field", numbers.wrapField)
            .put("responses", JSONObject().apply {
                for (mark in LocalMark.entries) put(mark.wire, numbers.responseCodes.getValue(mark))
            })
        check(prefs.edit().putString("cortex.wire.verified", safe.toString()).commit()) {
            "Could not persist verified wire profile"
        }
        return verified
    }

    /** Restores only app-validated metadata; no original request sample is saved. */
    fun verified(): CortexVerifiedWire? {
        val raw = prefs.getString("cortex.wire.verified", null) ?: return null
        return try {
            val obj = JSONObject(raw)
            val codes = obj.getJSONObject("responses")
            CortexVerifiedWire(CortexWireNumbers(
                obj.getInt("voc_field"), obj.getInt("response_field"), obj.getInt("wrap_field"),
                LocalMark.entries.associateWith { codes.getInt(it.wire) }
            ))
        } catch (_: Exception) { null }
    }

    fun clear() {
        check(prefs.edit().remove("cortex.wire.verified")
            .putBoolean("cortex.direct.enabled", false).commit())
    }
}
