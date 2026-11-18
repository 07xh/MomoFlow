package io.github.personal.momoflow

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/** Three states shown to users, regardless of whether transport was foreground or Cortex. */
enum class FeedbackOutcome(val label: String) {
    PENDING("待确认"), SUCCEEDED("已成功"), FAILED("已失败")
}

object FeedbackOutcomePolicy {
    fun fromOfficialReadback(status: OfficialSyncStatus): FeedbackOutcome = when (status) {
        OfficialSyncStatus.OBSERVED_MATCH -> FeedbackOutcome.SUCCEEDED
        OfficialSyncStatus.CONFLICT, OfficialSyncStatus.INVALID_WORD -> FeedbackOutcome.FAILED
        else -> FeedbackOutcome.PENDING
    }
}

data class FeedbackOperation(
    val id: String,
    val vocId: String,
    val mark: LocalMark,
    val day: String,
    val accountDigest: String,
    val outcome: FeedbackOutcome,
    val explanation: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("voc_id", vocId).put("mark", mark.wire)
        .put("day", day).put("account_digest", accountDigest)
        .put("outcome", outcome.name).put("explanation", explanation)

    companion object {
        fun parse(o: JSONObject) = FeedbackOperation(
            o.getString("id"), o.getString("voc_id"),
            LocalMark.entries.first { it.wire == o.getString("mark") },
            o.getString("day"), o.getString("account_digest"),
            FeedbackOutcome.valueOf(o.getString("outcome")),
            o.optString("explanation"),
        )
    }
}

/**
 * Committed before the user leaves for a foreground action. A pending item
 * survives process death; it is never silently treated as a successful write.
 * Transaction records contain no Bearer token, cookies or passwords.
 */
class CortexFeedbackLedger(context: Context) {
    private val prefs = context.getSharedPreferences("momoflow_cortex_ledger", Context.MODE_PRIVATE)

    private fun digest(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun key(vocId: String): String = "operation." + MessageDigest.getInstance("SHA-256")
        .digest(vocId.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    @Synchronized
    fun current(vocId: String, openApiToken: String): FeedbackOperation? = try {
        prefs.getString(key(vocId), null)?.let { FeedbackOperation.parse(JSONObject(it)) }
            ?.takeIf { it.accountDigest == digest(openApiToken) && it.day == StudyDay.today() }
    } catch (_: Exception) { null }

    /**
     * A second notification tap does not mint another operation while the
     * original is unresolved; a different mark must be cancelled first.
     */
    @Synchronized
    fun begin(vocId: String, mark: LocalMark, openApiToken: String): FeedbackOperation {
        val existing = current(vocId, openApiToken)
        check(existing?.outcome != FeedbackOutcome.PENDING) {
            "该单词已有待确认反馈，请先核验或取消，不能重复发起"
        }
        val operation = FeedbackOperation(
            UUID.randomUUID().toString(), vocId, mark, StudyDay.today(),
            digest(openApiToken), FeedbackOutcome.PENDING, "已建立基线，等待墨墨官方记录回读"
        )
        check(prefs.edit().putString(key(vocId), operation.toJson().toString()).commit()) {
            "无法保存反馈事务，未执行任何操作"
        }
        return operation
    }

    @Synchronized
    fun recordReadback(
        vocId: String, openApiToken: String,
        status: OfficialSyncStatus, explanation: String
    ): FeedbackOperation? {
        val existing = current(vocId, openApiToken) ?: return null
        if (existing.outcome != FeedbackOutcome.PENDING) return existing
        val updated = existing.copy(
            outcome = FeedbackOutcomePolicy.fromOfficialReadback(status),
            explanation = explanation.take(800)
        )
        check(prefs.edit().putString(key(vocId), updated.toJson().toString()).commit())
        return updated
    }

    @Synchronized
    fun cancel(vocId: String, openApiToken: String) {
        val existing = current(vocId, openApiToken) ?: return
        if (existing.outcome != FeedbackOutcome.PENDING) return
        val updated = existing.copy(outcome = FeedbackOutcome.FAILED, explanation = "操作已取消，未确认服务端反馈")
        check(prefs.edit().putString(key(vocId), updated.toJson().toString()).commit())
    }
}
