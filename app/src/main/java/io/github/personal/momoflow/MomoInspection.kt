package io.github.personal.momoflow

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject

/** Strictly read-only diagnosis. Never performs gestures or clicks. */
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
    fun lastReport(): String = prefs.getString("$namespace.report", null) ?: "还没有采集到墨墨页面控件结果"

    fun report(visibleWord: Boolean, labels: Set<String>, clickable: Set<String>) {
        if (!active()) return
        val result = JSONObject()
            .put("time", System.currentTimeMillis())
            .put("word_visible", visibleWord)
            .put("labels", labels.sorted().joinToString(" / "))
            .put("clickable", clickable.sorted().joinToString(" / "))
        // Only a boolean and three whitelisted response names are stored; no page text, IDs or credentials.
        prefs.edit().putString("$namespace.report", result.toString()).apply()
    }

    fun reset() {
        prefs.edit().remove("$namespace.word").remove("$namespace.until")
            .remove("$namespace.report").apply()
    }
}

/** Only watches the public MaiMemo package when user explicitly starts a 2-min inspection. */
class MomoInspectionService : AccessibilityService() {
    private var lastScanMs = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != MOMO_PACKAGE) return
        val capture = MomoInspectionStore(this)
        val word = capture.targetWord() ?: return
        val now = System.currentTimeMillis()
        if (now - lastScanMs < 750L) return
        lastScanMs = now
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != MOMO_PACKAGE) return

        var wordVisible = false
        val visible = mutableSetOf<String>()
        val clickable = mutableSetOf<String>()
        var nodes = 0
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 14 || nodes++ >= 250) return
            val candidates = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                .map(String::trim)
            if (candidates.any { it == word }) wordVisible = true
            for (s in RESPONSE_LABELS) {
                if (candidates.any { it == s }) {
                    visible.add(s)
                    // The label might be on a child while the actual clickable element is its parent.
                    if (node.isClickable || node.parent?.isClickable == true) clickable.add(s)
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        capture.report(wordVisible, visible, clickable)
    }

    override fun onInterrupt() = Unit

    companion object {
        const val MOMO_PACKAGE = "com.maimemo.android.momo"
        private val RESPONSE_LABELS = listOf("认识", "模糊", "忘记")
    }
}
