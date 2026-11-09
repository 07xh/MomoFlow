package io.github.personal.momoflow

import android.content.Context
import org.json.JSONObject
import java.util.UUID

/**
 * One user tap arms one short-lived action on the official foreground review screen.
 * This is never an HTTP write. A successful ACTION_CLICK dispatch is NOT proof of sync.
 */
data class ForegroundFeedbackAction(
    val nonce: String,
    val wordId: String,
    val spelling: String,
    val mark: LocalMark,
    val day: String,
    val expiresAtMs: Long
) {
    fun toJson(): JSONObject = JSONObject()
        .put("nonce", nonce).put("word_id", wordId).put("spelling", spelling)
        .put("mark", mark.wire).put("day", day).put("expires_at", expiresAtMs)

    companion object {
        fun parse(value: JSONObject): ForegroundFeedbackAction = ForegroundFeedbackAction(
            nonce = value.getString("nonce"),
            wordId = value.getString("word_id"),
            spelling = value.getString("spelling"),
            mark = LocalMark.entries.first { it.wire == value.getString("mark") },
            day = value.getString("day"),
            expiresAtMs = value.getLong("expires_at")
        )
    }
}

/** Pure, conservative eligibility policy: one word and three distinct actionable buttons. */
object ForegroundFeedbackGate {
    fun canAct(
        expectedLabel: String,
        wordMatches: Int,
        visibleButtonCounts: Map<String, Int>,
        actionableButtonCounts: Map<String, Int>,
        uniqueActionTargets: Boolean,
    ): Boolean {
        val labels = listOf("认识", "模糊", "忘记")
        return expectedLabel in labels && wordMatches == 1 && uniqueActionTargets &&
            labels.all { visibleButtonCounts[it] == 1 && actionableButtonCounts[it] == 1 }
    }
}

class OfficialForegroundActionStore(context: Context) {
    private val prefs = context.getSharedPreferences("momoflow_local", Context.MODE_PRIVATE)
    private val context = context.applicationContext

    fun arm(word: StudyWord, mark: LocalMark, nowMs: Long = System.currentTimeMillis()): ForegroundFeedbackAction {
        val action = ForegroundFeedbackAction(
            UUID.randomUUID().toString(), word.id, word.spelling, mark,
            StudyDay.today(), nowMs + 60_000L
        )
        check(prefs.edit()
            .putString("auto_feedback.pending", action.toJson().toString())
            .putString("auto_feedback.state", "ARMED")
            .putBoolean("auto_feedback.verified", false)
            .commit()) { "无法保存单次反馈请求" }
        return action
    }

    fun pending(nowMs: Long = System.currentTimeMillis()): ForegroundFeedbackAction? {
        val raw = prefs.getString("auto_feedback.pending", null) ?: return null
        val action = try { ForegroundFeedbackAction.parse(JSONObject(raw)) }
        catch (_: Exception) { clear(); return null }
        if (action.day != StudyDay.today() || nowMs >= action.expiresAtMs) {
            prefs.edit().remove("auto_feedback.pending")
                .putString("auto_feedback.state", "EXPIRED").commit()
            return null
        }
        return action
    }

    /** Must consume the action BEFORE any click attempt to guarantee at most one attempt. */
    fun claim(nonce: String): Boolean {
        val action = pending() ?: return false
        if (action.nonce != nonce || !SecureStore(context).autoFeedbackEnabled()) return false
        val snapshot = SecureStore(context).snapshot() ?: return false
        if (!SecureStore(context).active() || !SecureStore(context).isToday(snapshot)) return false
        if (snapshot.words.getOrNull(SecureStore(context).index())?.id != action.wordId) return false
        val trial = OfficialTrialStore(context).pending() ?: return false
        if (trial.wordId != action.wordId || trial.expected != action.mark) return false
        return prefs.edit().remove("auto_feedback.pending")
            .putString("auto_feedback.state", "CLAIMED").commit()
    }

    fun complete(accepted: Boolean) {
        if (prefs.getString("auto_feedback.state", null) != "CLAIMED") return
        prefs.edit().putString(
            "auto_feedback.state",
            if (accepted) "CLICK_SENT" else "CLICK_REJECTED"
        ).commit()
    }

    /** Return-to-MomoFlow readback is automatic once, but may still report sync delay. */
    fun takeReadback(): Boolean {
        if (prefs.getString("auto_feedback.state", null) != "CLICK_SENT" ||
            prefs.getBoolean("auto_feedback.verified", false)) return false
        return prefs.edit().putBoolean("auto_feedback.verified", true).commit()
    }

    fun summary(): String {
        pending() // expire stale requests before rendering status
        return when (prefs.getString("auto_feedback.state", "") ?: "") {
            "ARMED" -> "已等待墨墨前台出现完全一致的单词和三个唯一按钮；60 秒内仅尝试一次。"
            "CLAIMED" -> "正在向墨墨前台派发一次按钮操作，尚未验证官方记录。"
            "CLICK_SENT" -> "墨墨前台已接受点击请求；仍需核对官方学习记录，不能直接认定服务器回写成功。"
            "CLICK_REJECTED" -> "墨墨前台未接受点击请求；没有标记官方完成。"
            "EXPIRED" -> "本次前台操作已超时，未执行点击。"
            else -> "没有待执行的前台反馈。"
        }
    }

    fun clear() {
        prefs.edit().remove("auto_feedback.pending")
            .remove("auto_feedback.state").remove("auto_feedback.verified").commit()
    }
}
