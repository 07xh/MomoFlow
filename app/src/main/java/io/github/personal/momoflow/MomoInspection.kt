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

/**
 * Scoped service. Read-only diagnostics remain available; ONLY a fresh explicit
 * user notification tap with opt-in can trigger one ACTION_CLICK in MaiMemo.
 * No text dumps, root gestures, network interception or background taps.
 */
class MomoInspectionService : AccessibilityService() {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var lastScanMs = 0L
    private var scheduledNonce: String? = null

    private data class PageScan(
        val wordCount: Int,
        val visible: Map<String, Int>,
        val actions: Map<String, List<AccessibilityNodeInfo>>
    ) {
        val clickable: Map<String, Int> get() = actions.mapValues { it.value.size }
        val distinctActions: Boolean get() {
            val nodes = actions.values.flatten()
            return nodes.size == 3 && nodes.distinct().size == 3
        }
        fun isEligible(label: String): Boolean = ForegroundFeedbackGate.canAct(
            label, wordCount, visible, clickable, distinctActions
        )
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(4) {
            val item = current ?: return null
            if (item.isVisibleToUser && item.isEnabled &&
                (item.isClickable || item.actionList.any {
                    it.id == AccessibilityNodeInfo.ACTION_CLICK
                })) return item
            current = item.parent
        }
        return null
    }

    private fun inspect(root: AccessibilityNodeInfo, target: String): PageScan {
        val visible = mutableMapOf<String, Int>()
        val actions = mutableMapOf<String, MutableList<AccessibilityNodeInfo>>()
        var wordCount = 0
        var visited = 0
        fun traverse(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 16 || visited++ >= 400 || !node.isVisibleToUser) return
            val captions = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                .map(String::trim).distinct()
            if (captions.any { it.equals(target, ignoreCase = true) }) wordCount++
            val labels = captions.mapNotNull(MomoLabelMatcher::feedbackLabel).distinct()
            for (label in labels) {
                visible[label] = (visible[label] ?: 0) + 1
                val clickable = clickableAncestor(node)
                if (clickable != null) {
                    val peers = actions.getOrPut(label) { mutableListOf() }
                    if (peers.none { it == clickable }) peers.add(clickable)
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { traverse(it, depth + 1) }
            }
        }
        traverse(root, 0)
        return PageScan(wordCount, visible, actions)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != MOMO_PACKAGE) return
        val now = System.currentTimeMillis()
        val actionStore = OfficialForegroundActionStore(this)
        val armed = if (SecureStore(this).autoFeedbackEnabled()) actionStore.pending(now) else null
        val capture = MomoInspectionStore(this)
        val target = armed?.spelling ?: capture.targetWord() ?: return
        if (now - lastScanMs < 250L) return
        lastScanMs = now

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != MOMO_PACKAGE) return
        val scan = inspect(root, target)

        if (capture.active() && capture.targetWord()?.equals(target, true) == true) {
            capture.report(
                scan.wordCount > 0,
                scan.visible.filterValues { it > 0 }.keys,
                scan.clickable.filterValues { it > 0 }.keys
            )
        }

        if (armed == null || scheduledNonce != null || !scan.isEligible(armed.mark.label)) return
        // Require stable screen/word/buttons across TWO different UI snapshots.
        scheduledNonce = armed.nonce
        handler.postDelayed({
            scheduledNonce = null
            val pending = actionStore.pending() ?: return@postDelayed
            if (pending.nonce != armed.nonce || !SecureStore(this).autoFeedbackEnabled()) return@postDelayed
            val secondRoot = rootInActiveWindow ?: return@postDelayed
            if (secondRoot.packageName?.toString() != MOMO_PACKAGE) return@postDelayed
            val second = inspect(secondRoot, pending.spelling)
            if (!second.isEligible(pending.mark.label)) return@postDelayed
            val button = second.actions[pending.mark.label]?.singleOrNull() ?: return@postDelayed
            if (actionStore.claim(pending.nonce)) {
                val clicked = try {
                    button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } catch (_: Exception) { false }
                actionStore.complete(clicked)
            }
        }, 350L)
    }

    override fun onInterrupt() = Unit

    companion object {
        const val MOMO_PACKAGE = "com.maimemo.android.momo"

        fun isUserEnabled(context: Context): Boolean {
            val manager = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
            return manager.isEnabled && manager.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            ).any {
                val info = it.resolveInfo.serviceInfo
                info.packageName == context.packageName &&
                    info.name.endsWith(".MomoInspectionService")
            }
        }
    }
}
