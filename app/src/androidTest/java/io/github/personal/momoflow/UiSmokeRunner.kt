package io.github.personal.momoflow

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import java.io.File

/** Exercises actual native views and stores on an emulator, without a real account. */
class UiSmokeRunner : Instrumentation() {
    private lateinit var activity: Activity
    private fun walk(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { walk(view.getChildAt(it)) } else emptyList()

    private fun click(text: String) {
        runOnMainSync {
            val button = walk(activity.window.decorView).filterIsInstance<Button>().single { it.text.toString() == text }
            check(button.isEnabled) { "Button disabled: $text" }
            check(button.performClick()) { "Click rejected: $text" }
        }
        waitForIdleSync()
    }

    private fun verifyFullyVisible(text: String) {
        runOnMainSync {
            val view = walk(activity.window.decorView).filterIsInstance<TextView>().single { it.text.toString() == text }
            view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
        }
        waitForIdleSync()
        runOnMainSync {
            val view = walk(activity.window.decorView).filterIsInstance<TextView>().single { it.text.toString() == text }
            val bounds = Rect()
            check(view.getGlobalVisibleRect(bounds) && bounds.width() == view.width && bounds.height() == view.height) {
                "Clipped or inaccessible control: $text"
            }
        }
    }

    private fun screenshot(name: String) {
        val directory = File(targetContext.getExternalFilesDir(null), "ui-smoke").apply { mkdirs() }
        val bitmap = checkNotNull(uiAutomation.takeScreenshot()) { "Screenshot unavailable" }
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val result = Bundle()
        try {
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            runOnMainSync {
                val views = walk(activity.window.decorView)
                check(views.filterIsInstance<TextView>().any { it.text.toString() == "今日学习" })
                check(views.none { it is android.widget.EditText }) { "Token entry leaked into study screen" }
            }
            screenshot("01-study.png")
            click("设置")
            screenshot("02-settings.png")
            click("连接墨墨")
            check(uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("连接墨墨").isNotEmpty())
            val cancel = uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("取消").single()
            check(cancel.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            waitForIdleSync()
            click("背词")

            // Test fixtures are never used as product study data or screenshot content.
            val fixture = StudyWord("test-only-id", "test-only-word", 1, false, false)
            runOnMainSync {
                OfficialTrialStore(targetContext).save(OfficialWritebackTrial(
                    fixture.id, fixture.spelling, LocalMark.FAMILIAR, StudyDay.today(),
                    System.currentTimeMillis(), null, null
                ))
                OfficialForegroundActionStore(targetContext).arm(fixture, LocalMark.FAMILIAR)
                MomoInspectionStore(targetContext).begin(fixture)
            }
            click("设置"); click("背词")
            click("结束本次核对")
            runOnMainSync {
                check(OfficialTrialStore(targetContext).pending() == null) { "Trial survived cancellation" }
                check(OfficialForegroundActionStore(targetContext).pending() == null) { "Click request survived cancellation" }
                check(!MomoInspectionStore(targetContext).active()) { "Inspection survived cancellation" }
            }
            // Populated layout is a test fixture only, never an account/API success claim.
            runOnMainSync {
                SecureStore(targetContext).saveSnapshot(StudySnapshot(
                    StudyDay.today(), StudyProgress(1, 3, 0),
                    listOf(fixture.copy(spelling = "ui-test-only-long-vocabulary")), System.currentTimeMillis()
                ))
            }
            click("设置"); click("背词")
            for (caption in listOf("显示释义", "认识", "模糊", "忘记", "开始背词")) verifyFullyVisible(caption)
            verifyFullyVisible("认识")
            screenshot("03-populated-test-fixture.png")
            click("设置")
            runOnMainSync {
                walk(activity.window.decorView).filterIsInstance<android.widget.ScrollView>().single().scrollTo(0, 400)
            }
            waitForIdleSync()
            var savedScroll = 0
            runOnMainSync {
                savedScroll = walk(activity.window.decorView).filterIsInstance<android.widget.ScrollView>().single().scrollY
            }
            check(savedScroll > 0)
            val monitor = addMonitor(MainActivity::class.java.name, null, false)
            runOnMainSync { activity.recreate() }
            activity = checkNotNull(waitForMonitorWithTimeout(monitor, 5_000)) { "Recreation did not finish" }
            removeMonitor(monitor)
            waitForIdleSync()
            runOnMainSync {
                val restored = walk(activity.window.decorView).filterIsInstance<android.widget.ScrollView>().single().scrollY
                check(restored == savedScroll) { "Scroll lost after recreation: $savedScroll -> $restored" }
                SecureStore(targetContext).clearStudyData()
            }
            result.putString("uiSmoke", "PASS: navigation, cancellation, populated controls and scroll recreation")
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("uiSmoke", "FAIL: ${error.stackTraceToString()}")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
