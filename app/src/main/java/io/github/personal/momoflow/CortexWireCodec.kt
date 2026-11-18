package io.github.personal.momoflow

import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Deterministic, OFFLINE protobuf codec. Schema must come from paired real
 * runtime samples. This code cannot send HTTP or authorize a write.
 */
data class CortexWireProfile(
    val wrapperField: Int,
    val vocIdField: Int,
    val responseField: Int,
    val familiarNumber: Int,
    val uncertainNumber: Int,
    val forgetNumber: Int,
) {
    fun enumNumber(mark: LocalMark): Int = when (mark) {
        LocalMark.FAMILIAR -> familiarNumber
        LocalMark.VAGUE -> uncertainNumber // wasm UNCERTAIN != Cortex model VAGUE
        LocalMark.FORGET -> forgetNumber
    }

    companion object {
        fun fromEvidenceJson(root: JSONObject): CortexWireProfile {
            require(root.getString("kind") == "cortex_wire_evidence_v1" &&
                root.getString("source") == "device_runtime_capture") {
                "需要真实运行时协议清单"
            }
            require(root.getInt("sample_count") >= 3) { "样本数不足" }
            require(!root.optBoolean("ready_for_production_write", true)) {
                "结构核验结果不能冒充已验证的生产写回"
            }
            val fields = root.getJSONObject("wasm_oplog")
            val enums = fields.getJSONObject("enum_numbers")
            val result = CortexWireProfile(
                fields.getInt("wrapper_field"),
                fields.getInt("voc_id_field"),
                fields.getInt("response_field"),
                enums.getInt("FAMILIAR"),
                enums.getInt("UNCERTAIN"),
                enums.getInt("FORGET"),
            )
            require(listOf(result.wrapperField, result.vocIdField, result.responseField)
                .all { it in 1..536870911 }) { "非法字段号" }
            require(result.vocIdField != result.responseField) { "字段号冲突" }
            require(listOf(result.familiarNumber, result.uncertainNumber, result.forgetNumber)
                .all { it > 0 } &&
                setOf(result.familiarNumber, result.uncertainNumber, result.forgetNumber).size == 3) {
                "wasm 枚举未核对"
            }
            val http = root.getJSONObject("http")
            require(http.getString("method") == "POST" &&
                http.getString("path") == "/api/v1/study/oplog") { "端点不匹配" }
            val keys = http.getJSONObject("json_fields")
            require(keys.getString("oplog") == "str" && keys.getString("checkpoint") == "int") {
                "未核验上传请求体"
            }
            return result
        }
    }
}

object CortexWireCodec {
    private fun writeVarint(out: ByteArrayOutputStream, value: ULong) {
        var n = value
        while (n >= 128uL) {
            out.write((n and 127uL).toInt() or 128)
            n = n shr 7
        }
        out.write(n.toInt())
    }

    private fun writeVarintField(out: ByteArrayOutputStream, field: Int, value: ULong) {
        writeVarint(out, field.toULong() shl 3)
        writeVarint(out, value)
    }

    /** No network request; no guessed schema defaults. */
    fun encode(wordId: String, mark: LocalMark, profile: CortexWireProfile): ByteArray {
        require(wordId.isNotEmpty() && wordId.all { it in '0'..'9' }) {
            "必须使用十进制 voc_id"
        }
        val id = wordId.toULongOrNull() ?: throw IllegalArgumentException("voc_id 超出 uint64 范围")
        require(id > 0uL) { "voc_id 无效" }
        val inside = ByteArrayOutputStream()
        writeVarintField(inside, profile.vocIdField, id)
        writeVarintField(inside, profile.responseField, profile.enumNumber(mark).toULong())
        val payload = inside.toByteArray()
        val outside = ByteArrayOutputStream()
        writeVarint(outside, (profile.wrapperField.toULong() shl 3) or 2uL)
        writeVarint(outside, payload.size.toULong())
        outside.write(payload)
        return outside.toByteArray()
    }

    fun encodedBase64(wordId: String, mark: LocalMark, profile: CortexWireProfile): String =
        Base64.encodeToString(encode(wordId, mark, profile), Base64.NO_WRAP)
}
