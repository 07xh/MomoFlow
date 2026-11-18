package io.github.personal.momoflow

import java.util.Base64

/**
 * Cortex wire numbers are NOT established by the APK strings alone.
 * A profile can be used only after comparing actual, user-owned, locally
 * captured requests for ALL three UI choices with this exact serializer.
 */
data class CortexWireNumbers(
    val vocField: Int,
    val responseField: Int,
    val wrapField: Int,
    val responseCodes: Map<LocalMark, Int>,
) {
    init {
        require(listOf(vocField, responseField, wrapField).all { it in 1..536870911 })
        require(vocField != responseField)
        require(responseCodes.keys == LocalMark.entries.toSet())
        require(responseCodes.values.all { it >= 0 } && responseCodes.values.toSet().size == 3)
    }
}

data class CortexWireSample(
    val vocId: Long,
    val mark: LocalMark,
    val checkpoint: Long,
    val oplogBase64: String,
)

/** The only constructor for an accepted wire profile is evidence verification. */
class CortexVerifiedWire internal constructor(val numbers: CortexWireNumbers)

object CortexWireCodec {
    private fun varint(value: Long): ByteArray {
        var remaining = value
        val out = ArrayList<Byte>(10)
        do {
            val current = (remaining and 0x7f).toInt()
            remaining = remaining ushr 7
            out.add((if (remaining != 0L) current or 0x80 else current).toByte())
        } while (remaining != 0L)
        return out.toByteArray()
    }

    private fun appendVarint(target: MutableList<Byte>, field: Int, value: Long) {
        target.addAll(varint(field.toLong() shl 3).toList())
        target.addAll(varint(value).toList())
    }

    fun encodeOp(vocId: Long, mark: LocalMark, numbers: CortexWireNumbers): ByteArray {
        require(vocId > 0L) { "Cortex requires a positive numeric voc_id" }
        val inner = mutableListOf<Byte>()
        appendVarint(inner, numbers.vocField, vocId)
        appendVarint(inner, numbers.responseField, numbers.responseCodes.getValue(mark).toLong())
        val outer = mutableListOf<Byte>()
        outer.addAll(varint((numbers.wrapField.toLong() shl 3) or 2L).toList())
        outer.addAll(varint(inner.size.toLong()).toList())
        outer.addAll(inner)
        return outer.toByteArray()
    }

    fun encodeBase64(vocId: Long, mark: LocalMark, numbers: CortexWireNumbers): String =
        Base64.getEncoder().encodeToString(encodeOp(vocId, mark, numbers))

    /**
     * The fixture is an actual observed oplog byte string, not the guessed
     * hex example from the reverse-engineering report. One per button is mandatory.
     * This verifies serialization only, NOT server acceptance or authorization.
     */
    fun verify(numbers: CortexWireNumbers, samples: List<CortexWireSample>): CortexVerifiedWire {
        require(samples.size == 3 && samples.map { it.mark }.toSet() == LocalMark.entries.toSet()) {
            "Each of 认识 / 模糊 / 忘记 requires a distinct real request sample"
        }
        for (sample in samples) {
            require(sample.checkpoint >= 0L)
            val observed = Base64.getDecoder().decode(sample.oplogBase64)
            require(observed.contentEquals(encodeOp(sample.vocId, sample.mark, numbers))) {
                "Observed Cortex protobuf differs for ${sample.mark}; do not send"
            }
        }
        return CortexVerifiedWire(numbers)
    }
}
