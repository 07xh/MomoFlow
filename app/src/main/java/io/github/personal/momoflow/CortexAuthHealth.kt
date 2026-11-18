package io.github.personal.momoflow

import android.content.Context

/** This state describes verification, NOT permission to write learning feedback. */
enum class CortexCheckResult { NEVER, READ_OK, HTTP_REJECTED, RATE_LIMITED, NETWORK_ERROR }

data class CortexConnectionView(
    val hasToken: Boolean,
    val lastResult: CortexCheckResult,
    val checkedAtMs: Long,
    val httpStatus: Int
) {
    val headline: String
        get() = when {
            !hasToken -> "尚未配置"
            lastResult == CortexCheckResult.NEVER -> "已保存 · 尚未核验"
            lastResult == CortexCheckResult.READ_OK -> "上次只读验证成功"
            lastResult == CortexCheckResult.HTTP_REJECTED -> "访问被拒绝 · HTTP $httpStatus"
            lastResult == CortexCheckResult.RATE_LIMITED -> "请求受限 · HTTP 429"
            else -> "连接检查失败 · 状态未知"
        }

    val detail: String
        get() = when {
            !hasToken -> "需要独立的 Cortex Bearer；开放 API Token 不能混用。"
            lastResult == CortexCheckResult.NEVER -> "本机保存凭证不代表服务端授权。请执行只读检查。"
            lastResult == CortexCheckResult.READ_OK -> "只证明上次检查点读取成功，不能证明后台反馈写回。"
            lastResult == CortexCheckResult.HTTP_REJECTED ->
                "服务器拒绝了上次请求。可能是凭证、访问权限或接口约定不匹配，不能直接判定过期。"
            lastResult == CortexCheckResult.RATE_LIMITED -> "请稍后再检查，避免连续重试。"
            else -> "网络或协议响应异常；保留原凭证，未认定授权失效。"
        }
}

object CortexAuthPolicy {
    fun classify(httpStatus: Int): CortexCheckResult = when (httpStatus) {
        401, 403 -> CortexCheckResult.HTTP_REJECTED
        429 -> CortexCheckResult.RATE_LIMITED
        else -> CortexCheckResult.NETWORK_ERROR
    }
}

/** Stores only timestamps and HTTP result codes. Credentials live in Android Keystore. */
class CortexAuthHealthStore(context: Context) {
    private val prefs = context.getSharedPreferences("momoflow_cortex_health_v1", Context.MODE_PRIVATE)

    fun view(hasToken: Boolean): CortexConnectionView {
        val result = try {
            CortexCheckResult.valueOf(prefs.getString("result", CortexCheckResult.NEVER.name)!!)
        } catch (_: Exception) { CortexCheckResult.NEVER }
        return CortexConnectionView(
            hasToken, result,
            prefs.getLong("at", 0L),
            prefs.getInt("http_status", 0)
        )
    }

    fun markSuccess() = write(CortexCheckResult.READ_OK, 200)
    fun markFailure(httpStatus: Int) = write(CortexAuthPolicy.classify(httpStatus), httpStatus)

    private fun write(result: CortexCheckResult, httpStatus: Int) {
        check(prefs.edit().putString("result", result.name)
            .putLong("at", System.currentTimeMillis())
            .putInt("http_status", httpStatus)
            .commit()) { "无法保存 Cortex 核验状态" }
    }

    fun clear() {
        check(prefs.edit().clear().commit()) { "无法清除 Cortex 核验状态" }
    }
}
