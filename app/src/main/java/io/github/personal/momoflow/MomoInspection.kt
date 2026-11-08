package io.github.personal.momoflow

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject

/**
 * Opt-in, 2-minute, read-only observation. Stores no page dump, credentials or
 * unrelated nodes. A "same frame" result is UI evidence, NOT a server writeback.
 */
class MomoInspectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("momoflow_local", Context.MODE_PRIVATE)
    private val namespace = "momo_inspect"

    fun begin(word: StudyWord) {
        prefs.edit().putString("$namespace.word", word.spelling)
            .putLong("$namespace.until", System.currentTimeMillis() + 120_000L)
            .remove("$namespace.report").apply()
    }

    fun active(): Boolean = System.currentTimeMillis() < prefs.getLong("$namespace.until", 0L)
    fun targetWord(): String? = if (active()) prefs.getString("$namespace.word", null) else null

    private fun labels(value: String): Set<String> = value.split(" / ")
        .filter { it in RESPONSE_LABELS }.toSet()

    fun lastReport(): String {
        val raw = prefs.getString("$namespace.report", null)
            ?: return "尚未获得识别结果。请确认无障碍服务已开启，并进入墨墨真实复习页。"
        return try {
            val data = JSONObject(raw)
            val recognized = data.optString("labels").ifEmpty { "未识别" }
            val clickable = data.optString("clickable").ifEmpty { "未识别" }
            val sameFrame = data.optBoolean("same_frame")
            "曾看到目标拼写：${if (data.optBoolean("word_seen")) "是" else "否"}；" +
                "曾识别反馈按钮：$recognized；可点击：$clickable；" +
                "同一页面快照同时识别目标拼写和三个可点击按钮：${if (sameFrame) "是" else "否"}。" +
                "只读诊断不执行反馈，也不能证明单词 ID 或服务器回写成功。"
        } catch (_: Exception) {
            "诊断记录无效，请重新进行两分钟只读识别。"
        }
    }

    /** Independently accumulate observations, but only a single frame can satisfy same_frame. */
    fun report(wordVisible: Boolean, visible: Set<String>, clickable: Set<String>) {
        if (!active()) return
        val before = try {
            JSONObject(prefs.getString("$namespace.report", "{}") ?: "{}")
        } catch (_: Exception) { JSONObject() }
        val allVisible = labels(before.optString("labels")) + visible.intersect(RESPONSE_LABELS)
        val allClickable = labels(before.optString("clickable")) + clickable.intersect(RESPONSE_LABELS)
        val sameFrame = before.optBoolean("same_frame") ||
            (wordVisible && clickable.containsAll(RESPONSE_LABELS))
        val result = JSONObject()
            .put("time", System.currentTimeMillis())
            .put("word_seen", before.optBoolean("word_seen") || wordVisible)
            .put("labels", allVisible.sorted().joinToString(" / "))
            .put("clickable", allClickable.sorted().joinToString(" / "))
            .put("same_frame", sameFrame)
        prefs.edit().putString("$namespace.report", result.toString()).apply()
    }

    fun reset() {
        prefs.edit().remove("$namespace.word").remove("$namespace.until")
            .remove("$namespace.report").apply()
    }

    companion object {
        private val RESPONSE_LABELS = setOf("认识", "模糊", "忘记")
    }
}

/** Watches ONLY the official MaiMemo package. Never clicks, dispatches gestures or sends data out. */
class MomoInspectionService : AccessibilityService() {
    private var lastScanMs = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != MOMO_PACKAGE) return
        val capture = MomoInspectionStore(this)
        val target = capture.targetWord() ?: return
        val now = System.currentTimeMillis()
        if (now - lastScanMs < 500L) return
        lastScanMs = now
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != MOMO_PACKAGE) return

        var wordVisible = false
        val visible = mutableSetOf<String>()
        val clickable = mutableSetOf<String>()
        var nodes = 0

        fun isClickableNode(node: AccessibilityNodeInfo): Boolean {
            var current: AccessibilityNodeInfo? = node
            repeat(4) {
                val candidate = current ?: return false
                if (candidate.isVisibleToUser && candidate.isEnabled &&
                    (candidate.isClickable || candidate.actionList.any {
                        it.id == AccessibilityNodeInfo.ACTION_CLICK
                    })
                ) return true
                current = candidate.parent
            }
            return false
        }

        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 16 || nodes++ >= 400) return
            if (!node.isVisibleToUser) return
            val candidates = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                .map(String::trim)
            if (candidates.any { it.equals(target, ignoreCase = true) }) wordVisible = true

            candidates.mapNotNull(MomoLabelMatcher::feedbackLabel).forEach { label ->
                visible.add(label)
                if (isClickableNode(node)) clickable.add(label)
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, depth + 1) }
        }

        walk(root, 0)
        capture.report(wordVisible, visible, clickable)
    }

    override fun onInterrupt() = Unit

    companion object {
        const val MOMO_PACKAGE = "com.maimemo.android.momo"
    }
}
