package io.github.personal.momoflow

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Isolated Cortex HTTP boundary, based on the supplied 5.6.10 gateway evidence.
 * Only verified read endpoints and explicit legacy-token exchange are exposed.
 * The speculative protobuf uplink is intentionally NOT available here.
 */
class CortexGatewayService {
    private val origin = "https://cortex.maimemo.com/cortex"

    fun exchangeLegacyToken(legacyToken: String): String {
        require(legacyToken.isNotBlank()) { "旧版登录 Token 不能为空" }
        val result = request("POST", "/api/v1/tokens/exchange", null,
            JSONObject().put("legacy_token", legacyToken.trim()))
        val payload = result.optJSONObject("data") ?: result
        val tokens = payload.optJSONObject("tokens") ?: payload
        val access = tokens.optString("access_token").ifBlank {
            tokens.optString("accessToken")
        }
        if (access.isBlank()) throw ApiException("Cortex 换取接口未返回 access_token；未保存凭证")
        return access
    }

    /** Unmodified server JSON: callers must not assume a checkpoint field's shape. */
    fun checkpoints(accessToken: String): JSONObject =
        request("GET", "/api/v1/study/checkpoints", accessToken)

    /** Aggregate-only read, never treated as per-word feedback confirmation. */
    fun overview(accessToken: String): JSONObject =
        request("GET", "/api/v1/study/overview", accessToken)

    private fun request(
        method: String, path: String, accessToken: String?,
        payload: JSONObject? = null
    ): JSONObject {
        require(path in setOf(
            "/api/v1/tokens/exchange", "/api/v1/study/checkpoints", "/api/v1/study/overview"
        )) { "未登记的 Cortex 端点" }
        if (method == "GET") require(!accessToken.isNullOrBlank())
        val connection = (URL(origin + path).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false // never forward credentials
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            if (!accessToken.isNullOrBlank())
                connection.setRequestProperty("Authorization", "Bearer ${accessToken.trim()}")
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use {
                    it.write(payload.toString().toByteArray(Charsets.UTF_8))
                }
            }
            val status = connection.responseCode
            if (status == 401 || status == 403) throw ApiException("Cortex 身份验证失败（HTTP $status）")
            if (status == 429) throw ApiException("Cortex 请求过于频繁，请稍后再试")
            if (status !in 200..299) throw ApiException("Cortex 接口返回 HTTP $status")
            val raw = connection.inputStream.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > 512 * 1024) throw ApiException("Cortex 响应过大")
                bytes.toString(Charsets.UTF_8)
            }
            val json = try { JSONObject(raw) }
                catch (_: Exception) { throw ApiException("Cortex 响应不是有效 JSON") }
            if (json.has("success") && !json.optBoolean("success", true))
                throw ApiException("Cortex 服务端表示请求失败")
            return json
        } catch (e: ApiException) {
            throw e
        } catch (_: Exception) {
            // Never expose request URLs with query parameters, headers, or tokens.
            throw ApiException("Cortex 网络连接异常，请检查网络与账号会话")
        } finally {
            connection.disconnect()
        }
    }
}
