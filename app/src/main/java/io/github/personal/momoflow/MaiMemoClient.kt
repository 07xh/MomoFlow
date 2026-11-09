package io.github.personal.momoflow

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Endpoint & schema port based on MIT-licensed fuhan666/maimemo-sdk StudyService. */
class MaiMemoClient(private val token: String) {
    private val origin = "https://open.maimemo.com/open/api/v1"

    private fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        val conn = (URL(origin + path).openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            if (status == 401 || status == 403) throw ApiException("授权已失效（HTTP $status），请更新墨墨 Token")
            if (status == 429) throw ApiException("墨墨 API 请求过于频繁，请稍后重试")
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val result = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw ApiException("墨墨 API 请求失败（HTTP $status）")
            val root = try { JSONObject(result) } catch (_: Exception) { throw ApiException("墨墨 API 返回的不是有效 JSON") }
            if (!root.optBoolean("success", false)) {
                val errors = root.optJSONArray("errors")
                val message = if (errors != null && errors.length() > 0) errors.optJSONObject(0)?.optString("msg") else null
                throw ApiException(message?.take(150)?.ifBlank { "墨墨 API 返回失败" } ?: "墨墨 API 返回失败")
            }
            return root.getJSONObject("data")
        } finally {
            conn.disconnect()
        }
    }

    fun progress(): StudyProgress = StudyProgress.parse(request("POST", "/study/get_study_progress", JSONObject()).getJSONObject("progress"))

    fun unfinished(): List<StudyWord> {
        val payload = JSONObject().put("is_finished", false).put("limit", 1000)
        val array = request("POST", "/study/get_today_items", payload).getJSONArray("today_items")
        return (0 until array.length()).map { StudyWord.parse(array.getJSONObject(it)) }
            .filter { !it.isFinished }.sortedBy { it.order }
    }

    /** Official read-only record to compare before/after a user action in the MaiMemo app. */
    fun record(vocId: String): OfficialRecord? {
        val payload = JSONObject().put("voc_ids", org.json.JSONArray().put(vocId)).put("limit", 1)
        val array = request("POST", "/study/query_study_records", payload).getJSONArray("records")
        if (array.length() == 0) return null
        val value = OfficialRecord.parse(array.getJSONObject(0))
        if (value.vocId != vocId) throw ApiException("墨墨学习记录返回的单词 ID 与请求不一致")
        return value
    }

    /**
     * Query one official TODAY item, regardless of its finished state.
     * This deliberately omits is_finished so a completed word remains visible.
     * The response's first_response and is_finished are only read, never modified.
     */
    fun todayState(vocId: String): OfficialTodayState? {
        val payload = JSONObject()
            .put("voc_ids", org.json.JSONArray().put(vocId))
            .put("limit", 1)
        val array = request("POST", "/study/get_today_items", payload).getJSONArray("today_items")
        if (array.length() == 0) return null
        val value = OfficialTodayState.parse(array.getJSONObject(0))
        if (value.vocId != vocId) throw ApiException("今日词条的单词 ID 与查询不一致")
        return value
    }

    fun meanings(vocId: String): List<String> {
        val safeId = URLEncoder.encode(vocId, "UTF-8")
        val array = request("GET", "/interpretations?voc_id=$safeId").getJSONArray("interpretations")
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
            .filter { it.optString("status", "PUBLISHED") == "PUBLISHED" }
            .map { it.optString("interpretation", "").trim() }
            .filter { it.isNotEmpty() }.distinct().take(3)
    }
}

class ApiException(message: String) : Exception(message)
