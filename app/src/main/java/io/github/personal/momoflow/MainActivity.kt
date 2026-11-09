package io.github.personal.momoflow

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.app.Activity
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
import android.widget.Button
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
    private val ink = Color.parseColor("#142D54")
    private val muted = Color.parseColor("#667D9B")
    private val accent = Color.parseColor("#2563C8")
    private var syncing = false
    private var trialBusy = false
    private var trialStatus = ""
    private var queryingWordId: String? = null
    private var message: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#F4F8FF")
        window.navigationBarColor = Color.parseColor("#F4F8FF")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
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
        val snapshot = store.snapshot()
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
        startOfficialTrial(selected.word, selected.expected)
    }

    override fun onResume() {
        super.onResume()
        draw()
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()
    private fun gap(v: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(v)) }
    private fun label(value: String, sp: Float = 15f, color: Int = ink, bold: Boolean = false): TextView = TextView(this).apply {
        text = value; textSize = sp; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = true
    }
    private fun rounded(color: Int, radius: Int = 20, border: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (border != null) setStroke(dp(1), border)
    }
    private fun pane(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Color.WHITE, 22, Color.parseColor("#DFE8F5"))
        setPadding(dp(20), dp(18), dp(20), dp(18))
        elevation = dp(1).toFloat()
    }
    private fun button(text: String, filled: Boolean = true, action: () -> Unit): Button = Button(this).apply {
        this.text = text; textSize = 14f; isAllCaps = false
        setTextColor(if (filled) Color.WHITE else accent)
        background = rounded(if (filled) accent else Color.parseColor("#E9F2FF"), 14)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        setOnClickListener { action() }
    }
    private fun horizontal(vararg children: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        children.forEachIndexed { i, child ->
            if (i > 0) addView(View(this@MainActivity), LinearLayout.LayoutParams(dp(8), 1))
            addView(child, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
    }
    private fun hint(text: String) { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }

    private fun draw() {
        val scroller = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(Color.parseColor("#F4F8FF")) }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(26), dp(18), dp(28))
        }
        scroller.addView(column)
        setContentView(scroller)

        column.addView(label("墨墨 · 流云背词", 27f, ink, true))
        column.addView(gap(6))
        column.addView(label("非官方个人学习助手 · 真实数据来自墨墨开放 API", 12f, muted))
        column.addView(gap(20))

        val tokenPane = pane()
        tokenPane.addView(label("账号连接", 16f, ink, true))
        tokenPane.addView(gap(8))
        tokenPane.addView(label(if (store.token() == null) "尚未连接墨墨。Token 仅保存在本机加密存储中。" else "已保存个人 Token（不会在界面或日志中显示）", 12f, muted))
        tokenPane.addView(gap(12))
        val entry = EditText(this).apply {
            hint = "粘贴墨墨开放 API Token"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            textSize = 14f
            setSingleLine(true)
            background = rounded(Color.parseColor("#F1F6FF"), 12, Color.parseColor("#D8E6F7"))
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        tokenPane.addView(entry, LinearLayout.LayoutParams(-1, dp(49)))
        tokenPane.addView(gap(10))
        tokenPane.addView(horizontal(
            button(if (store.token() == null) "保存 Token" else "更换 Token") {
                val token = entry.text.toString().trim()
                if (token.isBlank()) { hint("请输入 Token"); return@button }
                try {
                    LiveWordNotification.stop(this)
                    store.clearStudyData() // no cross-account vocabulary leakage
                    store.saveToken(token)
                    WordWidget.updateAll(this)
                    message = "Token 已保存，请点击同步；今天需先在墨墨 App 完成初始化。"
                    draw()
                } catch (_: Exception) { hint("Token 保存失败，请检查设备安全设置") }
            },
            button("清除授权", false) {
                LiveWordNotification.stop(this)
                store.forgetToken(); store.clearStudyData(); message = "授权与学习缓存已清除"; draw()
                WordWidget.updateAll(this)
            }
        ))
        column.addView(tokenPane)
        column.addView(gap(14))

        val snapshot = store.snapshot()?.takeIf { store.isToday(it) }
        val studyPane = pane()
        studyPane.addView(label("今日墨墨学习", 16f, ink, true))
        studyPane.addView(gap(8))
        if (snapshot == null) {
            studyPane.addView(label("还没有同步到今日真实数据。请先打开墨墨，再在这里同步。", 13f, muted))
        } else {
            studyPane.addView(label("${snapshot.progress.finished} / ${snapshot.progress.total}", 31f, accent, true))
            studyPane.addView(label("已完成 / 今日总量 · 未完成词 ${snapshot.words.size} 个", 12f, muted))
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = snapshot.progress.total.coerceAtLeast(1)
                progress = snapshot.progress.finished.coerceIn(0, max)
                progressTintList = android.content.res.ColorStateList.valueOf(accent)
            }
            studyPane.addView(gap(8))
            studyPane.addView(bar, LinearLayout.LayoutParams(-1, dp(7)))
            studyPane.addView(gap(8))
            studyPane.addView(label("最后同步：${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(snapshot.updatedAtMs))}", 11f, muted))
        }
        val localCounts = LocalFeedbackStore(this).counts()
        val localTotal = localCounts.values.sum()
        studyPane.addView(gap(8))
        studyPane.addView(label("本地练习 $localTotal 词（认识 ${localCounts[LocalMark.FAMILIAR] ?: 0} · 模糊 ${localCounts[LocalMark.VAGUE] ?: 0} · 忘记 ${localCounts[LocalMark.FORGET] ?: 0}），不计入墨墨进度", 11f, muted))
        studyPane.addView(gap(12))
        val sync = button(if (syncing) "正在同步…" else "同步今日单词") { refresh() }
        sync.isEnabled = !syncing && store.token() != null
        studyPane.addView(sync)
        if (message.isNotBlank()) { studyPane.addView(gap(9)); studyPane.addView(label(message, 12f, muted)) }
        column.addView(studyPane)
        column.addView(gap(14))

        if (snapshot != null && snapshot.words.isNotEmpty()) {
            val index = store.index().coerceIn(0, snapshot.words.lastIndex)
            val word = snapshot.words[index]
            val wordPane = pane()
            wordPane.addView(label("今日待学 · ${index + 1} / ${snapshot.words.size}", 12f, accent, true))
            wordPane.addView(gap(18))
            wordPane.addView(label(word.spelling, 34f, ink, true))
            wordPane.addView(gap(10))
            val meaning = store.cachedMeaning(word.id)
            if (store.reveal()) wordPane.addView(label(meaning ?: "还没有读取到释义", 14f, muted))
            else wordPane.addView(label("释义已隐藏", 14f, muted))
            wordPane.addView(gap(16))
            wordPane.addView(horizontal(
                button("上一个", false) { move(-1, snapshot.words.size) },
                button("下一个") { move(1, snapshot.words.size) }
            ))
            wordPane.addView(gap(10))
            wordPane.addView(horizontal(
                button(if (store.reveal()) "隐藏释义" else "显示释义", false) {
                    store.setReveal(!store.reveal()); draw()
                    if (store.reveal() && store.cachedMeaning(word.id) == null) queryMeaning(word)
                    LiveWordNotification.publish(this)
                },
                button(if (queryingWordId == word.id) "查询中…" else "查墨墨释义", false) { queryMeaning(word) }
            ))
            wordPane.addView(gap(12))
            val mark = LocalFeedbackStore(this).markFor(word.id)
            if (mark != null) wordPane.addView(label("上次本地记录：${mark.label}", 12f, muted))
            wordPane.addView(gap(10))
            wordPane.addView(label("本地学习反馈（不会同步到墨墨）", 12f, accent, true))
            wordPane.addView(gap(8))
            wordPane.addView(horizontal(
                button("认识", false) { recordLocal(word, LocalMark.FAMILIAR) },
                button("模糊", false) { recordLocal(word, LocalMark.VAGUE) },
                button("忘记", false) { recordLocal(word, LocalMark.FORGET) }
            ))
            wordPane.addView(gap(12))
            wordPane.addView(label("这些反馈只保存在本机，不会改变墨墨的学习完成状态和下次复习日期。", 11f, muted))
            column.addView(wordPane)
            column.addView(gap(14))
        } else if (snapshot != null) {
            val noWords = pane()
            noWords.addView(label(if (snapshot.progress.total == 0) "今日没有返回单词，请确认墨墨 App 已初始化并开启自动同步。" else "当前未返回待学习单词，可能已完成或仍在同步中。", 13f, muted))
            column.addView(noWords)
            column.addView(gap(14))
        }

        val trialPane = pane()
        trialPane.addView(label("墨墨真实回写 · 验证模式", 16f, ink, true))
        trialPane.addView(gap(8))
        trialPane.addView(label("当前为人工提交实验：先记录墨墨官方基线，再在墨墨原 App 完成同一词的反馈，返回后比较官方记录。不会直接向墨墨提交记忆反馈。", 12f, muted))
        val pending = OfficialTrialStore(this).pending()
        if (snapshot != null && snapshot.words.isNotEmpty()) {
            val word = snapshot.words[store.index().coerceIn(0, snapshot.words.lastIndex)]
            trialPane.addView(gap(12))
            trialPane.addView(label("验证墨墨真实反馈 · 当前默认词：${word.spelling}", 13f, ink, true))
            trialPane.addView(gap(6))
            trialPane.addView(label("可输入墨墨原 App 当前显示的英文词（必须在今日待学词表中）。留空则使用默认词，提交前仍需核对完全一致。", 11f, muted))
            trialPane.addView(gap(7))
            val trialSpelling = EditText(this).apply {
                hint = "墨墨当前单词（英文）"
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT
                textSize = 14f
                background = rounded(Color.parseColor("#F1F6FF"), 12, Color.parseColor("#D8E6F7"))
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }
            trialPane.addView(trialSpelling, LinearLayout.LayoutParams(-1, dp(49)))
            trialPane.addView(gap(8))
            trialPane.addView(horizontal(
                button("认识 · 验证", false) { startTrialForSpelling(snapshot, word, trialSpelling.text.toString(), LocalMark.FAMILIAR) },
                button("模糊 · 验证", false) { startTrialForSpelling(snapshot, word, trialSpelling.text.toString(), LocalMark.VAGUE) },
                button("忘记 · 验证", false) { startTrialForSpelling(snapshot, word, trialSpelling.text.toString(), LocalMark.FORGET) }
            ))
            trialPane.addView(gap(10))
            trialPane.addView(button("只读识别墨墨背词页面控件（2 分钟）", false) { inspectMomo(word) })
            trialPane.addView(gap(6))
            trialPane.addView(label("如需控件识别，请先在系统无障碍设置里手动开启“墨墨页面只读诊断”。它仅识别墨墨包名、当前目标词及三个反馈按钮，不执行点击。", 11f, muted))
            trialPane.addView(gap(7))
            trialPane.addView(button("打开无障碍服务设置", false) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
        }
        trialPane.addView(gap(12))
        if (pending != null) {
            trialPane.addView(label("待核对：${pending.spelling} · ${pending.expected.label}", 12f, accent, true))
            trialPane.addView(gap(5))
            trialPane.addView(label("先确认墨墨原 App 显示的是同一个词，在那里手动反馈，再回到此处验证。", 11f, muted))
            trialPane.addView(gap(8))
            trialPane.addView(horizontal(
                button(if (trialBusy) "核对中…" else "验证官方记录") { verifyOfficialTrial() },
                button("取消本次验证", false) {
                    OfficialTrialStore(this).clear()
                    trialStatus = "已取消验证，未向墨墨提交任何操作。"
                    draw()
                }
            ))
        }
        if (trialBusy) { trialPane.addView(gap(8)); trialPane.addView(label("正在读取官方数据…", 12f, muted)) }
        if (trialStatus.isNotBlank()) {
            trialPane.addView(gap(8)); trialPane.addView(label(trialStatus, 12f, muted))
        }
        val officialReport = OfficialTrialStore(this).lastReport()
        if (officialReport.isNotBlank()) {
            trialPane.addView(gap(7))
            trialPane.addView(button("复制官方状态对照结果（不含 Token 或单词 ID）", false) {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(
                    ClipData.newPlainText("MomoFlow official readback", officialReport)
                )
                hint("已复制官方只读对照结果；这是状态观察，不代表 MomoFlow 已写回")
            })
        }
        trialPane.addView(gap(8))
        trialPane.addView(label("只读识别结果：${MomoInspectionStore(this).lastReport()}", 11f, muted))
        trialPane.addView(gap(8))
        trialPane.addView(button("复制只读识别报告（不含单词或 Token）", false) {
            val safeReport = MomoInspectionStore(this).lastReport()
            getSystemService(ClipboardManager::class.java).setPrimaryClip(
                ClipData.newPlainText("MomoFlow read-only inspection", safeReport)
            )
            hint("已复制脱敏识别结果，可用于真机验收")
        })
        column.addView(trialPane)
        column.addView(gap(14))

        val toolsPane = pane()
        toolsPane.addView(label("流体云与快捷入口", 16f, ink, true))
        toolsPane.addView(gap(6))
        toolsPane.addView(label("通知中的认识 / 模糊 / 忘记会开启“官方同词验证”并跳转墨墨；仍需在官方 App 手动点击。当前不会后台自动写回。", 11f, muted))
        toolsPane.addView(gap(8))
        toolsPane.addView(label(LiveWordNotification.diagnostics(this), 12f, muted))
        if (Build.VERSION.SDK_INT >= 36) {
            toolsPane.addView(gap(8))
            toolsPane.addView(button(if (store.experimentalPromotion()) "关闭实验性流体云请求" else "开启实验性流体云请求", false) {
                store.setExperimentalPromotion(!store.experimentalPromotion())
                LiveWordNotification.publish(this)
                draw()
            })
            toolsPane.addView(gap(5))
            toolsPane.addView(label("关闭时仅发送标准通知。开启也不保证 ColorOS 展示为流体云；仅适合用户主动开始的学习会话。", 11f, muted))
            toolsPane.addView(gap(8))
            toolsPane.addView(button("检查系统实时通知 / 流体云开关", false) {
                openPromotionSettings()
            })
            toolsPane.addView(gap(5))
            toolsPane.addView(label("部分一加系统还需要单独开启「在流体云显示实时通知」。如系统没有该入口，将打开本应用的通知设置。", 11f, muted))
        }
        toolsPane.addView(gap(10))
        toolsPane.addView(horizontal(
            button(if (store.active()) "结束背词会话" else "启动背词会话") { toggleSession() },
            button("打开墨墨", false) { openMomo() }
        ))
        toolsPane.addView(gap(10))
        toolsPane.addView(label("可在系统桌面的小组件列表中添加「墨墨流云助手」。流体云显示由 ColorOS 决定。", 12f, muted))
        toolsPane.addView(gap(8))
        toolsPane.addView(button("复制设备兼容性诊断（不含 Token）", false) { copyDiagnostics() })
        column.addView(toolsPane)
        column.addView(gap(16))
        column.addView(label("开放 API 只读；可选无障碍服务只读取墨墨背词页指定控件，默认不启用，不会自动点击。无需 Root。", 11f, muted))
    }

    private fun startTrialForSpelling(snapshot: StudySnapshot, fallback: StudyWord, input: String, mark: LocalMark) {
        val spelling = input.trim()
        val chosen = if (spelling.isEmpty()) fallback else snapshot.words.firstOrNull {
            it.spelling.equals(spelling, ignoreCase = true)
        }
        if (chosen == null) {
            hint("今日未完成词表找不到这个单词，请先在 MomoFlow 同步，并确认英文拼写")
            return
        }
        startOfficialTrial(chosen, mark)
    }

    /** Prepare a manual same-word trial, using only documented read-only official API calls. */
    private fun startOfficialTrial(word: StudyWord, mark: LocalMark) {
        if (trialBusy) return
        val token = store.token() ?: return hint("请先保存 Token")
        val snapshot = store.snapshot()
        if (snapshot == null || !store.isToday(snapshot) || snapshot.words.none { it.id == word.id }) {
            return hint("必须选择今日真实待学习单词")
        }
        if (packageManager.getLaunchIntentForPackage(MomoInspectionService.MOMO_PACKAGE) == null) {
            return hint("请先安装官方墨墨背单词 App")
        }
        trialBusy = true
        trialStatus = "正在读取 ${word.spelling} 的操作前官方记录。"
        draw()
        Thread {
            try {
                val client = MaiMemoClient(token)
                val original = client.record(word.id)
                val beforeToday = client.todayState(word.id)
                    ?: throw ApiException("官方今日列表未返回该词；请先在墨墨完成当天初始化并重新同步")
                runOnUiThread {
                    if (token != store.token()) { trialBusy = false; trialStatus = "账号已变化，本次验证取消"; draw(); return@runOnUiThread }
                    OfficialTrialStore(this).clear()
                    OfficialTrialStore(this).save(OfficialWritebackTrial(
                        word.id, word.spelling, mark, StudyDay.today(), System.currentTimeMillis(), original, beforeToday
                    ))
                    // The optional accessibility service is read-only and only scans
                    // the official app for 2 minutes; no service/permission is auto-enabled.
                    MomoInspectionStore(this).begin(word)
                    trialBusy = false
                    trialStatus = "官方学习记录基线已保存；已准备 2 分钟只读控件识别（需事先手动开启无障碍服务）。请先核对墨墨当前单词是否为 ${word.spelling}，相同才手动点${mark.label}；不同则不要提交。返回后检查同屏识别结果，再验证官方记录。"
                    draw()
                    openMomo()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    trialBusy = false
                    trialStatus = "无法获取操作前官方记录：${error.message ?: "接口异常"}。没有开始验证。"
                    draw()
                }
            }
        }.start()
    }

    private fun verifyOfficialTrial() {
        if (trialBusy) return
        val trial = OfficialTrialStore(this).pending() ?: return hint("没有待验证的操作")
        val token = store.token() ?: return hint("Token 已失效")
        trialBusy = true; draw()
        Thread {
            try {
                val client = MaiMemoClient(token)
                val current = client.record(trial.wordId)
                val currentToday = client.todayState(trial.wordId)
                runOnUiThread {
                    if (token != store.token()) { trialBusy = false; trialStatus = "账号已变化，不可比较"; draw(); return@runOnUiThread }
                    trialBusy = false
                    trialStatus = OfficialSyncAssessment.explain(
                        trial.before, current, trial.beforeToday, currentToday, trial.expected
                    )
                    OfficialTrialStore(this).saveReport(trialStatus)
                    draw()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    trialBusy = false
                    trialStatus = "验证请求失败：${error.message ?: "网络异常"}。不能认定已回写。"
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
        if (!store.isToday(snapshot) || snapshot.words.none { it.id == word.id }) return
        LocalFeedbackStore(this).mark(word.id, mark)
        message = "已在本机记录：${word.spelling} · ${mark.label}。墨墨官方进度未改变。"
        move(1, snapshot.words.size)
    }

    private fun move(delta: Int, size: Int) {
        if (size <= 0) return
        store.setIndex((store.index() + delta + size) % size)
        store.setReveal(false)
        draw(); LiveWordNotification.publish(this); WordWidget.updateAll(this)
    }

    private fun refresh() {
        val token = store.token()
        if (token == null) { hint("请先保存墨墨开放 API Token"); return }
        if (syncing) return
        syncing = true; message = "正在请求墨墨官方接口"; draw()
        Thread {
            try {
                val client = MaiMemoClient(token)
                val progress = client.progress()
                val items = client.unfinished()
                // Populate real Chinese meanings from a bundled MIT offline dictionary.
                // This runs on the sync worker thread, never on the Android UI thread.
                val offlineCount = OfflineDictionary.prefill(applicationContext, items)
                val snapshot = StudySnapshot(StudyDay.today(), progress, items, System.currentTimeMillis())
                runOnUiThread {
                    // If the user replaced their token during the request, discard the old account response.
                    if (store.token() != token) { syncing = false; draw(); return@runOnUiThread }
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
        queryingWordId = word.id
        draw()
        Thread {
            try {
                val results = MaiMemoClient(token).meanings(word.id)
                runOnUiThread {
                    if (token == store.token()) {
                        if (results.isNotEmpty()) {
                            store.saveMeaning(word.id, results.joinToString("\n"))
                        } else {
                            // Official definitions may only be user-created content.
                            // Do not overwrite a valid ECDICT meaning with an empty API result.
                            message = "墨墨未返回用户自建释义，继续使用本地英汉词典"
                        }
                    }
                    queryingWordId = null; draw()
                    LiveWordNotification.publish(this)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    queryingWordId = null
                    message = "释义查询失败：${error.message ?: "网络异常"}"
                    draw()
                }
            }
        }.start()
    }

    private fun toggleSession() {
        if (store.active()) {
            store.setActive(false); LiveWordNotification.stop(this); draw(); return
        }
        val snapshot = store.snapshot()
        if (snapshot == null || !store.isToday(snapshot) || snapshot.words.isEmpty()) {
            hint("请先成功同步今日未完成的真实单词")
            return
        }
        if (!LiveWordNotification.canPost(this)) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
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
            appendLine("MomoFlow 0.5.4 · 设备诊断（不含账号数据）")
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
