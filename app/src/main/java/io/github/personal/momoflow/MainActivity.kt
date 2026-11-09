package io.github.personal.momoflow

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Switch
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    companion object {
        const val ACTION_START_OFFICIAL_TRIAL = "io.github.personal.momoflow.START_OFFICIAL_TRIAL"
        const val EXTRA_TRIAL_WORD_ID = "official_trial_word_id"
        const val EXTRA_TRIAL_MARK = "official_trial_mark"
    }

    private val store by lazy { SecureStore(this) }
    private val ink = Color.parseColor("#17212D")
    private val muted = Color.parseColor("#687482")
    private val accent = Color.parseColor("#267764")
    private var page = 0
    private var drawnPage = 0
    private val scrollPositions = intArrayOf(0, 0)
    private var activeScroller: ScrollView? = null
    private var restoringScroll = false
    private var requestGeneration = 0
    private var connectionGeneration = 0
    private var syncing = false
    private var trialBusy = false
    private var trialStatus = ""
    private var queryingWordId: String? = null
    private var message: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = savedInstanceState?.getInt("page", 0)?.coerceIn(0, 1) ?: 0
        savedInstanceState?.getIntArray("scroll")?.let { values ->
            values.take(2).forEachIndexed { i, value -> scrollPositions[i] = value }
        }
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        else window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        trialStatus = OfficialTrialStore(this).lastReport()
        draw()
        handleNotificationTrial(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationTrial(intent)
    }

    /**
     * Android notification actions only prepare a manual trial and foreground
     * the original MaiMemo App. They NEVER pretend to submit a feedback mark.
     */
    private fun handleNotificationTrial(incoming: Intent?) {
        if (incoming?.action != ACTION_START_OFFICIAL_TRIAL) return
        // Consume this explicit tap once; Activity recreation must not resubmit a trial.
        incoming.action = Intent.ACTION_MAIN
        page = 0
        val snapshot = store.snapshot()
        if (trialBusy || OfficialTrialStore(this).pending() != null) {
            hint("请先核对或结束上一次反馈"); draw(); return
        }
        val selected = NotificationTrialGate.select(
            snapshot = snapshot,
            isToday = snapshot?.let(store::isToday) == true,
            active = store.active(),
            index = store.index(),
            wordId = incoming.getStringExtra(EXTRA_TRIAL_WORD_ID),
            markWire = incoming.getStringExtra(EXTRA_TRIAL_MARK)
        )
        if (selected == null) {
            trialStatus = "通知已过期，或词条与当前会话不一致；未开始官方反馈验证，请重新同步后再操作。"
            draw()
            return
        }
        if (trialBusy) {
            hint("正在读取上一次验证的基线，请勿重复点击")
            return
        }
        startOfficialTrial(selected.word, selected.expected, autoAttempt = store.autoFeedbackEnabled())
    }

    override fun onResume() {
        super.onResume()
        draw()
        // The user returned from MaiMemo after a one-shot foreground click.
        // Readback remains subject to official sync delay and can be retried manually.
        if (OfficialTrialStore(this).pending() != null &&
            OfficialForegroundActionStore(this).takeReadback()) {
            verifyOfficialTrial()
        }
    }

    override fun onDestroy() {
        requestGeneration++
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (!restoringScroll) activeScroller?.let { scrollPositions[drawnPage] = it.scrollY }
        outState.putInt("page", page)
        outState.putIntArray("scroll", scrollPositions)
        super.onSaveInstanceState(outState)
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()
    private fun gap(v: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(v)) }
    private fun label(value: String, sp: Float = 15f, color: Int = ink, bold: Boolean = false): TextView = TextView(this).apply {
        text = value; textSize = sp; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(dp(2).toFloat(), 1.05f)
    }
    private fun rounded(color: Int, radius: Int = 20, border: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (border != null) setStroke(dp(1), border)
    }
    private fun pane(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Color.WHITE, 24)
        setPadding(dp(20), dp(20), dp(20), dp(20))
    }
    private fun button(text: String, filled: Boolean = true, action: () -> Unit): Button = Button(this).apply {
        this.text = text; textSize = 14f; isAllCaps = false
        setTextColor(if (filled) Color.WHITE else accent)
        background = rounded(if (filled) accent else Color.parseColor("#EDF4F1"), 14)
        backgroundTintList = null
        stateListAnimator = null; elevation = 0f; translationZ = 0f
        minHeight = dp(48); minimumHeight = dp(48)
        minWidth = 0; minimumWidth = 0
        setPadding(dp(10), dp(12), dp(10), dp(12))
        setOnClickListener { action() }
    }
    private fun horizontal(vararg children: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        children.forEachIndexed { i, child ->
            if (i > 0) addView(View(this@MainActivity), LinearLayout.LayoutParams(dp(8), 1))
            addView(child, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }
    private fun hint(text: String) { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
    private fun section(column: LinearLayout, block: LinearLayout.() -> Unit) {
        column.addView(pane().apply(block)); column.addView(gap(14))
    }
    private fun showDetails(title: String, text: String) {
        val body = label(text, 14f, muted).apply {
            setPadding(dp(22), dp(12), dp(22), dp(20)); setTextIsSelectable(true)
        }
        AlertDialog.Builder(this).setTitle(title)
            .setView(ScrollView(this).apply { addView(body) })
            .setPositiveButton("知道了", null).show()
    }
    private fun draw() {
        if (isDestroyed || isFinishing) return
        if (!restoringScroll) activeScroller?.let { scrollPositions[drawnPage] = it.scrollY }
        drawnPage = page
        restoringScroll = true
        val frame = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#F5F7F6")) }
        val scroller = ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false }
        activeScroller = scroller
        frame.addView(scroller, FrameLayout.LayoutParams(
            minOf(resources.displayMetrics.widthPixels, dp(640)), -1, Gravity.CENTER_HORIZONTAL
        ))
        frame.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
                scroller.layoutParams = (scroller.layoutParams as FrameLayout.LayoutParams).apply {
                    width = minOf(dp(640), resources.displayMetrics.widthPixels - safe.left - safe.right)
                }
            } else {
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(24))
        }
        scroller.addView(column)
        setContentView(frame); frame.requestApplyInsets()
        if (Build.VERSION.SDK_INT >= 30) {
            val appearance = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(appearance, appearance)
        }
        column.addView(label("墨墨流云", 28f, ink, true))
        column.addView(gap(4))
        column.addView(label(if (page == 0) "把注意力留给眼前这个词" else "连接与使用偏好", 13f, muted))
        column.addView(gap(20))
        column.addView(horizontal(*arrayOf("背词", "设置").mapIndexed { index, title ->
            button(title, page == index) { page = index; draw() }.apply { isSelected = page == index }
        }.toTypedArray()))
        column.addView(gap(20))
        if (page == 0) drawStudy(column) else drawSettings(column)
        scroller.post {
            if (activeScroller === scroller) {
                scroller.scrollTo(0, scrollPositions[drawnPage])
                restoringScroll = false
            }
        }
    }

    private fun drawStudy(column: LinearLayout) {
        val snapshot = store.snapshot()?.takeIf(store::isToday)
        val pending = OfficialTrialStore(this).pending()
        section(column) {
            addView(label("今日学习", 14f, muted, true)); addView(gap(10))
            if (snapshot == null) {
                addView(label(if (store.token() == null) "连接墨墨，开始今天的学习" else "同步今天的待学单词", 21f, ink, true))
                addView(gap(8)); addView(label("先在墨墨 App 初始化今日学习，再同步到这里。", 13f, muted))
            } else {
                addView(label("${snapshot.progress.finished} / ${snapshot.progress.total}", 32f, accent, true))
                addView(label("墨墨已完成 / 今日总量", 12f, muted))
                addView(gap(10))
                addView(ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = snapshot.progress.total.coerceAtLeast(1)
                    progress = snapshot.progress.finished.coerceIn(0, max)
                    progressTintList = android.content.res.ColorStateList.valueOf(accent)
                }, LinearLayout.LayoutParams(-1, dp(6)))
                addView(gap(10))
                addView(label("待学 ${snapshot.words.size} 词 · 更新于 ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(snapshot.updatedAtMs))}", 12f, muted))
            }
            addView(gap(14))
            addView(button(if (store.token() == null) "连接墨墨" else if (syncing) "正在同步…" else "同步今日单词", snapshot == null) {
                if (store.token() == null) { page = 1; draw(); editToken() } else refresh()
            }.apply { isEnabled = !syncing && !trialBusy && pending == null })
            if (message.isNotBlank()) { addView(gap(10)); addView(label(message, 12f, muted)) }
        }
        if (snapshot != null && snapshot.words.isNotEmpty()) {
            val index = store.index().coerceIn(0, snapshot.words.lastIndex)
            val word = snapshot.words[index]
            section(column) {
                addView(label("当前单词  ${index + 1} / ${snapshot.words.size}", 12f, accent, true))
                addView(gap(18))
                addView(label(word.spelling, 38f, ink, true).apply { setTextIsSelectable(true) })
                addView(gap(14))
                if (store.reveal()) {
                    addView(label(store.cachedMeaning(word.id) ?: "暂无释义，可尝试查询公开释义", 16f, muted))
                    if (store.cachedMeaning(word.id) != null) {
                        addView(gap(6)); addView(label(when (store.meaningSource(word.id)) {
                            "official" -> "墨墨开放 API 公开释义"
                            "offline" -> "ECDICT 离线词典"
                            else -> "旧版缓存 · 来源待重新查询"
                        }, 11f, muted))
                    }
                }
                addView(gap(12))
                addView(button(if (store.reveal()) "收起释义" else "显示释义", false) {
                    val reveal = !store.reveal(); store.setReveal(reveal); draw()
                    if (reveal && !store.officialMeaningCheckedToday(word.id)) queryMeaning(word)
                    LiveWordNotification.publish(this@MainActivity)
                })
                addView(gap(16))
                val automatic = store.autoFeedbackEnabled() && MomoInspectionService.isUserEnabled(this@MainActivity)
                addView(label(if (automatic) "反馈将切换到墨墨，在同词页面尝试一次点击。" else "反馈将打开墨墨，核对同词后手动提交。", 12f, muted))
                addView(gap(10))
                addView(horizontal(*LocalMark.entries.map { mark ->
                    button(mark.label, mark == LocalMark.FAMILIAR) {
                        if (automatic && !store.active()) { hint("请先开始背词会话，再进行前台自动反馈"); return@button }
                        startOfficialTrial(word, mark, autoAttempt = store.autoFeedbackEnabled())
                    }.apply { isEnabled = !trialBusy && !syncing && pending == null }
                }.toTypedArray()))
                addView(gap(10))
                addView(horizontal(
                    button("上一个", false) { move(-1, snapshot.words.size) }.apply { isEnabled = !trialBusy && pending == null },
                    button("下一个", false) { move(1, snapshot.words.size) }.apply { isEnabled = !trialBusy && pending == null }
                ))
                addView(gap(8)); addView(button("按拼写切换单词", false) { pickWord() }.apply { isEnabled = !trialBusy && pending == null })
            }
            section(column) {
                addView(label(if (store.active()) "背词会话进行中" else "在通知与流体云中背词", 16f, ink, true))
                addView(gap(6)); addView(label("启动后可从通知操作。流体云的展示形式由系统决定。", 12f, muted))
                addView(gap(12))
                addView(button(if (store.active()) "结束背词" else "开始背词") { toggleSession() })
            }
        } else if (snapshot != null) {
            section(column) {
                addView(label(if (snapshot.progress.total > 0 && snapshot.progress.finished >= snapshot.progress.total) "今日学习已完成" else "暂时没有待学单词", 22f, ink, true))
                addView(gap(8)); addView(label("可打开墨墨确认学习计划，或稍后再次同步。", 13f, muted))
                addView(gap(12)); addView(button("打开墨墨", false) { openMomo() })
            }
        }
        if (pending != null || trialBusy || trialStatus.isNotBlank()) section(column) {
            addView(label(if (trialBusy) "正在核对官方记录…" else if (pending != null) "本次反馈待核对" else "最近反馈结果", 16f, ink, true))
            if (pending != null) {
                addView(gap(8)); addView(label("${pending.spelling} · ${pending.expected.label}", 16f, accent, true))
                addView(gap(6)); addView(label("返回后点击核对；自动点击成功派发后会自动读回一次，同步延迟时可重查。", 12f, muted))
                addView(gap(12)); addView(button(if (trialBusy) "核对中…" else "重新核对官方记录") { verifyOfficialTrial() }.apply { isEnabled = !trialBusy })
            }
            if (!trialBusy) OfficialTrialStore(this@MainActivity).lastStatus()?.let { status ->
                val summary = when (status) {
                    OfficialSyncStatus.OBSERVED_MATCH -> "官方记录与所选反馈相符"
                    OfficialSyncStatus.UNCHANGED -> "暂未同步，可稍后重新核对"
                    OfficialSyncStatus.CONFLICT -> "官方反馈与所选类型不符"
                    OfficialSyncStatus.INVALID_WORD -> "官方返回的单词不一致"
                    OfficialSyncStatus.INCOMPLETE -> "官方数据不完整，尚无法确认"
                    OfficialSyncStatus.UNCONFIRMED_CHANGE -> "记录有变化，尚无法确认反馈"
                }
                addView(gap(8)); addView(label(summary, 13f, accent, true))
            }
            if (trialStatus.isNotBlank()) {
                addView(gap(8)); addView(button("查看反馈详情", false) {
                    showDetails("反馈详情", trialStatus + "\n\n" + OfficialForegroundActionStore(this@MainActivity).summary())
                })
            }
            if (pending != null || trialBusy) {
                addView(gap(8)); addView(button("结束本次核对", false) { cancelTrial() })
            }
        }
    }

    private fun pickWord() {
        if (trialBusy || OfficialTrialStore(this).pending() != null) return
        val snapshot = store.snapshot()?.takeIf(store::isToday) ?: return
        val entry = EditText(this).apply {
            hint = "墨墨正在显示的英文词"; setSingleLine(true)
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val dialog = AlertDialog.Builder(this).setTitle("切换到墨墨当前词")
            .setMessage("输入完整拼写，从今日待学词表定位。")
            .setView(entry).setNegativeButton("取消", null).setPositiveButton("切换", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val spelling = entry.text.toString().trim()
            val matches = snapshot.words.withIndex().filter { it.value.spelling.equals(spelling, true) }
            if (matches.size != 1) {
                entry.error = if (matches.isEmpty()) "今日待学词表中找不到该词" else "存在同拼写的多个词条，无法唯一定位"
                return@setOnClickListener
            }
            if (!store.isToday(snapshot) || store.snapshot() != snapshot || trialBusy || OfficialTrialStore(this).pending() != null) {
                entry.error = "词表或反馈状态已变化，请重新打开"; return@setOnClickListener
            }
            store.setIndex(matches.single().index); store.setReveal(false)
            dialog.dismiss(); draw(); LiveWordNotification.publish(this); WordWidget.updateAll(this)
        } }
        dialog.show()
    }

    private fun editToken() {
        val entry = EditText(this).apply {
            hint = "粘贴墨墨开放 API Token"; textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true); setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        val dialog = AlertDialog.Builder(this).setTitle(if (store.token() == null) "连接墨墨" else "更换 Token")
            .setMessage("在墨墨「我的 → 更多设置 → 实验功能 → 开放 API」创建 Token，仅加密保存在本机。")
            .setView(entry).setNegativeButton("取消", null).setPositiveButton("保存", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val token = entry.text.toString().trim()
            if (token.isBlank()) { entry.error = "请输入 Token"; return@setOnClickListener }
            try {
                requestGeneration++; connectionGeneration++; syncing = false; queryingWordId = null
                LiveWordNotification.stop(this); store.clearStudyData(); store.saveToken(token)
                trialStatus = ""; trialBusy = false
                WordWidget.updateAll(this); message = "连接已保存，请同步今日单词"
                dialog.dismiss(); page = 0; draw()
            } catch (_: Exception) { entry.error = "保存失败，请检查设备安全设置" }
        } }
        dialog.show()
    }

    private fun cancelTrial() {
        requestGeneration++
        OfficialForegroundActionStore(this).clear()
        OfficialTrialStore(this).clear()
        MomoInspectionStore(this).reset()
        trialBusy = false
        trialStatus = "本次核对已结束。已发送的墨墨操作无法撤回，官方记录可重新同步。"
        draw()
    }

    private fun drawSettings(column: LinearLayout) {
        section(column) {
            addView(label("墨墨连接", 16f, ink, true)); addView(gap(8))
            addView(label(if (store.token() == null) "尚未连接" else "已保存本机授权", 13f, muted))
            addView(gap(12)); addView(button(if (store.token() == null) "连接墨墨" else "更换 Token", false) { editToken() })
            if (store.token() != null) {
                addView(gap(8)); addView(button("清除授权与缓存", false) {
                    AlertDialog.Builder(this@MainActivity).setTitle("清除本机授权？")
                        .setMessage("将停止会话并清除本机词表、反馈记录及 Token。")
                        .setNegativeButton("取消", null).setPositiveButton("清除") { _, _ ->
                            requestGeneration++; connectionGeneration++; syncing = false; queryingWordId = null
                            LiveWordNotification.stop(this@MainActivity)
                            store.forgetToken(); store.clearStudyData(); trialBusy = false; trialStatus = ""; message = ""
                            WordWidget.updateAll(this@MainActivity); draw()
                        }.show()
                })
            }
        }
        section(column) {
            addView(label("反馈方式", 16f, ink, true)); addView(gap(10))
            addView(Switch(this@MainActivity).apply {
                text = "前台自动反馈"; textSize = 15f; minHeight = dp(48)
                isChecked = store.autoFeedbackEnabled()
                setOnCheckedChangeListener { _, enabled ->
                    store.setAutoFeedbackEnabled(enabled)
                    if (!enabled) OfficialForegroundActionStore(this@MainActivity).clear()
                    LiveWordNotification.publish(this@MainActivity); draw()
                }
            })
            addView(gap(6)); addView(label("主动点反馈后切换墨墨，仅在同词及按钮识别通过时尝试一次。需启动背词会话。", 12f, muted))
            addView(gap(10)); addView(label(if (MomoInspectionService.isUserEnabled(this@MainActivity)) "无障碍服务已开启" else "无障碍服务未开启 · 当前使用人工反馈", 12f, muted))
            addView(gap(10)); addView(button("无障碍服务设置", false) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        }
        section(column) {
            addView(label("流体云与通知", 16f, ink, true)); addView(gap(10))
            if (Build.VERSION.SDK_INT >= 36) {
                addView(Switch(this@MainActivity).apply {
                    text = "请求在流体云显示"; textSize = 15f; minHeight = dp(48)
                    isChecked = store.experimentalPromotion()
                    setOnCheckedChangeListener { _, enabled ->
                        store.setExperimentalPromotion(enabled); LiveWordNotification.publish(this@MainActivity); draw()
                    }
                })
                addView(gap(6)); addView(label("系统可能仅展示标准通知，三档按钮可在展开通知中操作。", 12f, muted))
                addView(gap(10)); addView(button("系统实时通知设置", false) { openPromotionSettings() })
            } else addView(label("当前系统使用标准背词通知。", 13f, muted))
            addView(gap(8)); addView(button("通知权限设置", false) {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            })
            addView(gap(10)); addView(label("桌面长按空白处，可添加「墨墨流云助手」小组件。", 12f, muted))
        }
        section(column) {
            addView(label("更多", 16f, ink, true)); addView(gap(10))
            addView(button("本地练习", false) { showLocalPractice() }); addView(gap(8))
            addView(button("查询当前词的公开释义", false) {
                val snapshot = store.snapshot()?.takeIf(store::isToday)
                val word = snapshot?.words?.getOrNull(store.index())
                if (word == null) hint("请先同步今日单词") else queryMeaning(word)
            }.apply { isEnabled = queryingWordId == null })
            addView(gap(8)); addView(button("诊断与反馈详情", false) { showDiagnostics() })
            addView(gap(8)); addView(button("打开墨墨", false) { openMomo() })
        }
        column.addView(label("MomoFlow 0.5.6 · 个人学习助手", 12f, muted))
    }

    private fun showLocalPractice() {
        val snapshot = store.snapshot()?.takeIf(store::isToday)
        val word = snapshot?.words?.getOrNull(store.index())
        if (word == null) { hint("请先同步今日待学单词"); return }
        AlertDialog.Builder(this).setTitle("本地练习 · ${word.spelling}")
            .setMessage("只记录在本机，不计入墨墨进度。")
            .setPositiveButton("认识") { _, _ -> recordLocal(word, LocalMark.FAMILIAR) }
            .setNeutralButton("模糊") { _, _ -> recordLocal(word, LocalMark.VAGUE) }
            .setNegativeButton("忘记") { _, _ -> recordLocal(word, LocalMark.FORGET) }.show()
    }

    private fun showDiagnostics() {
        val choices = arrayOf("查看通知状态", "查看官方核对详情", "只读识别当前词（2 分钟）", "复制设备兼容性诊断", "复制官方核对报告", "复制页面识别报告")
        AlertDialog.Builder(this).setTitle("诊断与反馈详情").setItems(choices) { _, which ->
            when (which) {
                0 -> showDetails("通知状态", LiveWordNotification.diagnostics(this))
                1 -> showDetails("官方核对详情", trialStatus.ifBlank { "暂无反馈核对记录" } + "\n\n" + OfficialForegroundActionStore(this).summary())
                2 -> {
                    val snapshot = store.snapshot()?.takeIf(store::isToday)
                    val word = snapshot?.words?.getOrNull(store.index())
                    if (word == null) hint("请先同步今日单词") else inspectMomo(word)
                }
                3 -> copyDiagnostics()
                else -> {
                    val report = if (which == 4) OfficialTrialStore(this).lastReport() else MomoInspectionStore(this).lastReport()
                    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("MomoFlow", report))
                    hint("已复制脱敏报告")
                }
            }
        }.setNegativeButton("关闭", null).show()
    }

    private fun requestIsCurrent(generation: Int, token: String, day: String): Boolean {
        if (isDestroyed || isFinishing || generation != requestGeneration || store.token() != token) return false
        if (day != StudyDay.today()) {
            trialBusy = false; trialStatus = "学习日期已变化，请重新同步"; draw(); return false
        }
        return true
    }

    /** One explicit app/notification tap prepares one same-word foreground handoff. */
    private fun startOfficialTrial(word: StudyWord, mark: LocalMark, autoAttempt: Boolean = false) {
        if (trialBusy || syncing || OfficialTrialStore(this).pending() != null) {
            hint("请先核对或结束上一次反馈"); return
        }
        val token = store.token() ?: return hint("请先连接墨墨")
        val snapshot = store.snapshot()
        if (snapshot == null || !store.isToday(snapshot) || word.isFinished ||
            snapshot.words.getOrNull(store.index())?.id != word.id ||
            snapshot.words.count { it.spelling.equals(word.spelling, true) } != 1) {
            return hint("请同步并选择今日真实待学单词")
        }
        if (packageManager.getLaunchIntentForPackage(MomoInspectionService.MOMO_PACKAGE) == null) {
            return hint("请先安装官方墨墨背单词 App")
        }
        val day = snapshot.date
        val generation = ++requestGeneration
        trialBusy = true
        trialStatus = "正在读取 ${word.spelling} 的操作前官方记录。"
        page = 0; draw()
        Thread {
            try {
                val client = MaiMemoClient(token)
                val original = client.record(word.id)
                val beforeToday = client.todayState(word.id)
                    ?: throw ApiException("官方今日列表未返回该词，请重新同步")
                if (beforeToday.isFinished) throw ApiException("该词已在墨墨完成，请重新同步今日单词")
                runOnUiThread {
                    if (!requestIsCurrent(generation, token, day)) return@runOnUiThread
                    if (store.snapshot()?.words?.getOrNull(store.index())?.id != word.id) {
                        trialBusy = false; trialStatus = "当前单词已变化，本次反馈取消"; draw(); return@runOnUiThread
                    }
                    OfficialTrialStore(this).clear()
                    OfficialForegroundActionStore(this).clear()
                    OfficialTrialStore(this).save(OfficialWritebackTrial(
                        word.id, word.spelling, mark, day, System.currentTimeMillis(), original, beforeToday
                    ))
                    MomoInspectionStore(this).begin(word)
                    val autoReady = autoAttempt && store.autoFeedbackEnabled() && store.active() &&
                        MomoInspectionService.isUserEnabled(this)
                    if (autoReady) OfficialForegroundActionStore(this).arm(word, mark)
                    trialBusy = false
                    trialStatus = if (autoReady) {
                        "已授权 ${word.spelling} 的一次${mark.label}操作。仅在墨墨同词和三个按钮均唯一可点击时触发，60 秒失效。返回后自动核对官方记录。"
                    } else {
                        "请在墨墨核对 ${word.spelling} 后手动点${mark.label}，返回后点击「重新核对官方记录」。"
                    }
                    draw(); openMomo()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!requestIsCurrent(generation, token, day)) return@runOnUiThread
                    trialBusy = false
                    trialStatus = "无法准备本次反馈：${error.message ?: "网络异常"}。未开始操作。"
                    draw()
                }
            }
        }.start()
    }

    private fun verifyOfficialTrial() {
        if (trialBusy) return
        val trial = OfficialTrialStore(this).pending() ?: return hint("没有待核对的反馈")
        val token = store.token() ?: return hint("请先连接墨墨")
        val generation = requestGeneration
        trialBusy = true; draw()
        Thread {
            try {
                val client = MaiMemoClient(token)
                val current = client.record(trial.wordId)
                val currentToday = client.todayState(trial.wordId)
                val result = OfficialSyncAssessment.evaluate(
                    trial.before, current, trial.beforeToday, currentToday, trial.expected
                )
                // Never infer a count or remove a word from a dispatched accessibility click.
                val updated = if (result.status == OfficialSyncStatus.OBSERVED_MATCH) {
                    StudySnapshot(trial.day, client.progress(), client.unfinished(), System.currentTimeMillis())
                } else null
                runOnUiThread {
                    if (!requestIsCurrent(generation, token, trial.day) ||
                        OfficialTrialStore(this).pending() != trial) return@runOnUiThread
                    trialBusy = false
                    trialStatus = result.message
                    if (updated != null) {
                        store.saveSnapshot(updated); store.setReveal(false)
                        if (updated.words.isEmpty()) store.setActive(false)
                        OfficialForegroundActionStore(this).clear()
                        OfficialTrialStore(this).finish(trialStatus, result.status)
                        MomoInspectionStore(this).reset()
                        message = "已按墨墨官方返回结果更新今日进度和待学词表"
                        LiveWordNotification.publish(this); WordWidget.updateAll(this)
                    } else OfficialTrialStore(this).saveReport(trialStatus, result.status)
                    draw()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!requestIsCurrent(generation, token, trial.day) ||
                        OfficialTrialStore(this).pending() != trial) return@runOnUiThread
                    trialBusy = false
                    trialStatus = "核对请求失败：${error.message ?: "网络异常"}。保留本次记录，可稍后重查。"
                    OfficialTrialStore(this).saveReport(trialStatus)
                    draw()
                }
            }
        }.start()
    }

    private fun inspectMomo(word: StudyWord) {
        MomoInspectionStore(this).begin(word)
        trialStatus = "只读识别已开启 2 分钟。进入墨墨对应单词页面后返回，查看按钮识别报告。"
        draw()
        openMomo()
    }

    private fun recordLocal(word: StudyWord, mark: LocalMark) {
        val snapshot = store.snapshot() ?: return
        if (trialBusy || OfficialTrialStore(this).pending() != null) { hint("请先核对或结束本次反馈"); return }
        if (!store.isToday(snapshot) || snapshot.words.none { it.id == word.id }) return
        LocalFeedbackStore(this).mark(word.id, mark)
        message = "已在本机记录：${word.spelling} · ${mark.label}。墨墨官方进度未改变。"
        move(1, snapshot.words.size)
    }

    private fun move(delta: Int, size: Int) {
        if (size <= 0 || trialBusy || OfficialTrialStore(this).pending() != null) return
        store.setIndex((store.index() + delta + size) % size)
        store.setReveal(false)
        draw(); LiveWordNotification.publish(this); WordWidget.updateAll(this)
    }

    private fun refresh() {
        val token = store.token()
        if (token == null) { hint("请先保存墨墨开放 API Token"); return }
        if (syncing || trialBusy || OfficialTrialStore(this).pending() != null) return
        val generation = connectionGeneration
        val day = StudyDay.today()
        syncing = true; message = "正在请求墨墨官方接口"; draw()
        Thread {
            try {
                val client = MaiMemoClient(token)
                val progress = client.progress()
                val items = client.unfinished()
                // Populate real Chinese meanings from a bundled MIT offline dictionary.
                // This runs on the sync worker thread, never on the Android UI thread.
                val offlineMeanings = OfflineDictionary.lookup(applicationContext, items)
                val snapshot = StudySnapshot(day, progress, items, System.currentTimeMillis())
                runOnUiThread {
                    // If the user replaced their token during the request, discard the old account response.
                    if (isDestroyed || generation != connectionGeneration || store.token() != token) return@runOnUiThread
                    if (day != StudyDay.today()) { syncing = false; message = "学习日期已变化，请重新同步"; draw(); return@runOnUiThread }
                    val offlineCount = store.cacheOfflineMeanings(offlineMeanings)
                    store.saveSnapshot(snapshot)
                    store.setReveal(false)
                    if (items.isEmpty()) store.setActive(false)
                    syncing = false
                    message = if (items.isEmpty()) "已同步，但未返回待学单词；请核实墨墨当日初始化与进度。" else "已同步 ${items.size} 个真实待学单词，本地词典补充释义 ${offlineCount} 条"
                    WordWidget.updateAll(this)
                    LiveWordNotification.publish(this)
                    draw()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (isDestroyed || generation != connectionGeneration || store.token() != token) return@runOnUiThread
                    syncing = false
                    message = "同步失败：${error.message ?: "网络或数据结构异常"}；未覆盖旧缓存。"
                    draw()
                }
            }
        }.start()
    }

    private fun queryMeaning(word: StudyWord) {
        if (queryingWordId != null) return
        val token = store.token() ?: return
        val generation = connectionGeneration
        val day = StudyDay.today()
        queryingWordId = word.id
        draw()
        Thread {
            try {
                val results = MaiMemoClient(token).meanings(word.id)
                runOnUiThread {
                    if (isDestroyed || generation != connectionGeneration || token != store.token()) return@runOnUiThread
                    if (day != StudyDay.today()) { queryingWordId = null; draw(); return@runOnUiThread }
                    if (token == store.token()) {
                        if (results.isNotEmpty()) {
                            store.saveMeaning(word.id, results.joinToString("\n"))
                            message = "已读取墨墨公开释义"
                        } else {
                            // Official definitions may only be user-created content.
                            // Do not overwrite a valid ECDICT meaning with an empty API result.
                            message = "墨墨未返回用户自建释义，继续使用本地英汉词典"
                        }
                    }
                    if (token == store.token()) store.markOfficialMeaningCheckedToday(word.id)
                    queryingWordId = null; draw()
                    LiveWordNotification.publish(this)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (isDestroyed || generation != connectionGeneration || token != store.token()) return@runOnUiThread
                    queryingWordId = null
                    message = "释义查询失败：${error.message ?: "网络异常"}"
                    draw()
                }
            }
        }.start()
    }

    private fun toggleSession() {
        if (store.active()) {
            OfficialForegroundActionStore(this).clear()
            store.setActive(false); LiveWordNotification.stop(this); draw(); return
        }
        val snapshot = store.snapshot()
        if (snapshot == null || !store.isToday(snapshot) || snapshot.words.isEmpty()) {
            hint("请先成功同步今日未完成的真实单词")
            return
        }
        if (!LiveWordNotification.canPost(this)) {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            } else {
                hint("请在系统设置中开启本应用通知")
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
            return
        }
        store.setActive(true); LiveWordNotification.publish(this); draw()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) toggleSession()
        else if (requestCode == 100) hint("未授予通知权限，背词会话不会启动")
    }

    private fun openPromotionSettings() {
        if (Build.VERSION.SDK_INT < 36) {
            hint("此功能需要 Android 16")
            return
        }
        val promotion = Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        try {
            startActivity(promotion)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            } catch (_: Exception) {
                hint("系统未提供可打开的通知设置入口，可从设置中搜索「流体云」或「实时通知」")
            }
        }
    }

    /** Safe to paste into a support conversation: never reads or prints token, vocabulary or account data. */
    private fun copyDiagnostics() {
        val report = buildString {
            appendLine("MomoFlow 0.5.6 · 设备诊断（不含账号数据）")
            appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL}; SDK ${Build.VERSION.SDK_INT}")
            appendLine("通知权限：${if (LiveWordNotification.canPost(this@MainActivity)) "允许" else "未允许"}")
            appendLine(LiveWordNotification.diagnostics(this@MainActivity))
            appendLine("今日词表缓存：${if (store.snapshot()?.let { store.isToday(it) } == true) "存在" else "没有"}")
            appendLine("背词会话：${if (store.active()) "运行中" else "已结束"}")
        }
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("MomoFlow diagnostics", report))
        hint("已复制脱敏诊断，可粘贴到聊天")
    }

    private fun openMomo() {
        try {
            val intent = packageManager.getLaunchIntentForPackage("com.maimemo.android.momo")
            if (intent != null) startActivity(intent)
            else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.maimemo.com/")))
        } catch (_: ActivityNotFoundException) { hint("无法打开墨墨，请确认已安装墨墨背单词") }
    }
}
