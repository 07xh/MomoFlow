package io.github.personal.momoflow

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException

/** No implicit HTTP retries: an ambiguous write must first be reconciled. */
class CortexHttpTransport(private val bearerToken: String, private val profile: CortexVerifiedWire) {
    init { require(bearerToken.isNotBlank()) }

    private fun connection(path: String, method: String): HttpURLConnection =
        (URL(CortexStudyContract.BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $bearerToken")
        }

    /** Fail closed on an unknown checkpoint shape; guessing risks conflicts. */
    fun readCheckpoint(): Long? {
        val conn = connection(CortexStudyContract.Endpoint.CHECKPOINTS.path, "GET")
        return try {
            if (conn.responseCode !in 200..299) return null
            val response = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val obj = JSONObject(response)
            val value = obj.opt("checkpoint")
            if (value is Number && value.toLong() >= 0L) value.toLong() else null
        } finally {
            conn.disconnect()
        }
    }

    /**
     * A response 2xx is transport acceptance, not a verified account update.
     * Even after a socket exception, NEVER automatically send again.
     */
    fun submitOnce(vocId: Long, mark: LocalMark, checkpoint: Long): CortexDispatchVerdict {
        require(checkpoint >= 0L) { "Invalid checkpoint must not be uploaded" }
        val payload = JSONObject()
            .put("checkpoint", checkpoint)
            .put("oplog", CortexWireCodec.encodeBase64(vocId, mark, profile.numbers))
        val conn = connection(CortexStudyContract.Endpoint.UPLOAD_OPLOG.path, "POST")
        return try {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            when (conn.responseCode) {
                in 200..299 -> CortexDispatchVerdict.ACCEPTED_UNVERIFIED
                in 400..499 -> CortexDispatchVerdict.REJECTED
                else -> CortexDispatchVerdict.OUTCOME_UNKNOWN
            }
        } catch (_: IOException) {
            CortexDispatchVerdict.OUTCOME_UNKNOWN
        } finally {
            conn.disconnect()
        }
    }
}
