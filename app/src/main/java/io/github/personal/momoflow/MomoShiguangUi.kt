package io.github.personal.momoflow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// The ShiguangTheme, WordSection, WordMetric and VocabGlassDock components
// are copied from 07xh/shiguang-english/native-android/ui, not recreated from a screenshot.
internal data class MomoComposeModel(
    val page: MomoPage,
    val snapshot: StudySnapshot?,
    val index: Int,
    val reveal: Boolean,
    val meaning: String?,
    val mark: LocalMark?,
    val localCounts: Map<LocalMark, Int>,
    val syncing: Boolean,
    val message: String,
    val querying: Boolean,
    val hasOpenToken: Boolean,
    val active: Boolean,
    val autoFeedback: Boolean,
    val fluidAdvanced: Boolean,
    val verifyAdvanced: Boolean,
    val legacyExpanded: Boolean,
    val promotion: Boolean,
    val trialBusy: Boolean,
    val trialStatus: String,
    val pending: OfficialWritebackTrial?,
    val recent: List<FeedbackOperation>,
    val report: String,
    val foregroundReport: String,
    val inspectionReport: String,
    val notificationDiagnostics: String,
    val cortexHealth: CortexConnectionView,
    val cortexBusy: Boolean,
    val cortexStatus: String
)
internal data class MomoComposeActions(
    val navigate: (MomoPage) -> Unit,
    val sync: () -> Unit,
    val previous: () -> Unit,
    val next: () -> Unit,
    val toggleMeaning: () -> Unit,
    val lookupMeaning: () -> Unit,
    val localRate: (LocalMark) -> Unit,
    val toggleSession: () -> Unit,
    val openMomo: () -> Unit,
    val toggleAutoFeedback: () -> Unit,
    val accessibilitySettings: () -> Unit,
    val toggleFluidAdvanced: () -> Unit,
    val togglePromotion: () -> Unit,
    val promotionSettings: () -> Unit,
    val copyDiagnostics: () -> Unit,
    val startTrial: (String, LocalMark) -> Unit,
    val inspectWord: () -> Unit,
    val verify: () -> Unit,
    val cancel: () -> Unit,
    val copyReport: () -> Unit,
    val toggleVerifyAdvanced: () -> Unit,
    val copyInspection: () -> Unit,
    val saveOpenToken: (String) -> Unit,
    val clearOpenToken: () -> Unit,
    val saveCortex: (String) -> Unit,
    val legacyExchange: (String) -> Unit,
    val toggleLegacy: () -> Unit,
    val checkCortex: () -> Unit,
    val clearCortex: () -> Unit
)
private val PrimaryInk = Color(0xFF0F172A)
private val SecondaryInk = Color(0xFF64748B)
private val PageBackground = Color(0xFFF8FAFC)

@Composable
internal fun MomoShiguangApp(m: MomoComposeModel, a: MomoComposeActions) {
    Box(Modifier.fillMaxSize().background(PageBackground)
        .windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize().padding(bottom = 82.dp)) {
            Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 10.dp)) {
                Text("MOMOFLOW  ·  墨墨流云", color = QuietBlue, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.0.sp)
                Spacer(Modifier.height(5.dp))
                Text(m.page.title, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = PrimaryInk)
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (m.page) {
                    MomoPage.STUDY -> StudyContent(m, a)
                    MomoPage.FLUID -> FluidContent(m, a)
                    MomoPage.VERIFY -> VerifyContent(m, a)
                    MomoPage.CONNECTIONS -> ConnectionsContent(m, a)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        VocabGlassDock(
            active = m.page.name.lowercase(Locale.ROOT),
            onSelect = { value ->
                MomoPage.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }?.let(a.navigate)
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun Hint(value: String) {
    if (value.isNotBlank()) Text(value, fontSize = 12.sp, color = SecondaryInk,
        lineHeight = 19.sp)
}

@Composable
private fun Status(value: String, good: Boolean = false, bad: Boolean = false) {
    val tint = if (good) CorrectGreen else if (bad) WrongRed else SecondaryInk
    Surface(shape = RoundedCornerShape(13.dp), color = tint.copy(alpha = 0.07f)) {
        Text(value, modifier = Modifier.fillMaxWidth().padding(12.dp), color = tint,
            fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun HeaderRow(title: String, detail: String = "") {
    Text(title, fontWeight = FontWeight.Bold, color = PrimaryInk, fontSize = 17.sp)
    if (detail.isNotBlank()) Hint(detail)
}

@Composable
private fun MainAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 49.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryInk)) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
@Composable
private fun SubAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 45.dp),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, PaperStroke)) {
        Text(label, fontSize = 12.sp, color = PrimaryInk, fontWeight = FontWeight.Medium,
            maxLines = 2, textAlign = TextAlign.Center)
    }
}
@Composable
private fun ThreeMarks(enabled: Boolean = true, onMark: (LocalMark) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        listOf(LocalMark.FAMILIAR, LocalMark.VAGUE, LocalMark.FORGET).forEachIndexed { i, mark ->
            val color = memoryColor(when (i) { 0 -> 3; 1 -> 1; else -> 0 })
            Surface(modifier = Modifier.weight(1f).height(51.dp),
                onClick = { onMark(mark) }, enabled = enabled,
                color = color.copy(alpha = .09f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, color.copy(alpha = .3f))) {
                Box(contentAlignment = Alignment.Center) {
                    Text(mark.label, fontSize = 13.sp, color = color, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StudyContent(m: MomoComposeModel, a: MomoComposeActions) {
    if (!m.hasOpenToken) {
        Status("尚未连接墨墨开放 API，不会生成模拟学习数据。")
        SubAction("连接墨墨账号") { a.navigate(MomoPage.CONNECTIONS) }
    }
    WordSection {
        HeaderRow("今日学习", "来自墨墨官方开放 API · 非本地估算")
        val p = m.snapshot?.progress
        if (p == null) {
            Hint("没有今日学习记录。先打开墨墨 App 初始化，再同步。")
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text((p.finished).toString() + " / " + p.total, fontSize = 29.sp,
                        color = PrimaryInk, fontWeight = FontWeight.Bold)
                    Hint("已完成 / 今日总量")
                }
                Column(horizontalAlignment = Alignment.End) {
                    WordMetric("未完成", m.snapshot.words.size, "词")
                }
            }
            LinearProgressIndicator(progress = {
                if (p.total == 0) 0f else (p.finished.toFloat() / p.total).coerceIn(0f, 1f)
            }, modifier = Modifier.fillMaxWidth().height(7.dp),
                color = QuietBlue, trackColor = SoftBlue)
            Hint("同步于 " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(m.snapshot.updatedAtMs)))
        }
        MainAction(if (m.syncing) "同步中…" else "同步今日单词",
            enabled = !m.syncing && m.hasOpenToken, onClick = a.sync)
        if (m.message.isNotBlank()) Status(m.message, bad = m.message.contains("失败"))
    }
    val total = m.localCounts.values.sum()
    if (m.snapshot != null && m.snapshot.words.isNotEmpty()) {
        val word = m.snapshot.words[m.index.coerceIn(0, m.snapshot.words.lastIndex)]
        WordSection {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("VOCABULARY", fontSize = 11.sp, color = SecondaryInk,
                    letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)
                Text((m.index + 1).toString() + " / " + m.snapshot.words.size,
                    fontSize = 12.sp, color = SecondaryInk)
            }
            Surface(onClick = a.toggleMeaning,
                color = Color.White, modifier = Modifier.fillMaxWidth().heightIn(min = 210.dp),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, PaperStroke)) {
                Column(Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Text(word.spelling, fontWeight = FontWeight.ExtraBold,
                        fontSize = if (word.spelling.length > 18) 27.sp else 35.sp,
                        lineHeight = 43.sp, color = PrimaryInk, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(if (m.reveal) (m.meaning ?: "未获取到释义") else "轻触词卡查看释义",
                        color = if (m.reveal) PrimaryInk else SecondaryInk,
                        fontSize = if (m.reveal) 16.sp else 12.sp, textAlign = TextAlign.Center)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { SubAction("上一个", onClick = a.previous) }
                Box(Modifier.weight(1f)) { SubAction("下一个", onClick = a.next) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { SubAction(if (m.reveal) "隐藏释义" else "显示释义",
                    onClick = a.toggleMeaning) }
                Box(Modifier.weight(1f)) { SubAction(if (m.querying) "查询中…" else "查询墨墨释义",
                    enabled = !m.querying, onClick = a.lookupMeaning) }
            }
            HorizontalDivider(color = PaperStroke)
            Hint("本地练习反馈 · 不会同步到墨墨，也不会更改复习计划")
            ThreeMarks(onMark = a.localRate)
            if (m.mark != null) Hint("上次本地记录：" + m.mark.label)
        }
    } else if (m.snapshot != null) {
        WordSection { Hint("当前没有返回待学习的真实单词。") }
    }
    WordSection {
        HeaderRow("本地练习统计")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            WordMetric("累计", total)
            WordMetric("认识", m.localCounts[LocalMark.FAMILIAR] ?: 0)
            WordMetric("模糊", m.localCounts[LocalMark.VAGUE] ?: 0)
            WordMetric("忘记", m.localCounts[LocalMark.FORGET] ?: 0)
        }
        Hint("此处仅记录本机学习，不代表墨墨官方完成进度。")
    }
}

@Composable
private fun FluidContent(m: MomoComposeModel, a: MomoComposeActions) {
    WordSection {
        HeaderRow("流体云背词", "在通知中展示当前真实词条")
        Status(if (m.active) "会话运行中" else "会话未启动", good = m.active)
        MainAction(if (m.active) "结束背词会话" else "启动背词会话", onClick = a.toggleSession)
        SubAction("打开墨墨 App", onClick = a.openMomo)
        Hint("需先同步今日词表。能否显示为原生流体云取决于 ColorOS。")
    }
    WordSection {
        HeaderRow("三态反馈", "通知点击触发一次原 App 前台操作")
        Status(if (m.autoFeedback) "已启用 · 仍需无障碍授权" else "默认关闭",
            good = m.autoFeedback)
        MainAction(if (m.autoFeedback) "关闭前台反馈" else "开启前台反馈",
            onClick = a.toggleAutoFeedback)
        SubAction("无障碍服务设置", onClick = a.accessibilitySettings)
        Hint("只在主动点击通知、目标词和按钮全部核对通过时尝试操作；60 秒超时。")
    }
    SubAction(if (m.fluidAdvanced) "收起系统兼容与诊断" else "系统兼容与诊断",
        onClick = a.toggleFluidAdvanced)
    if (m.fluidAdvanced) WordSection {
        HeaderRow("设备与通知")
        Hint(m.notificationDiagnostics)
        SubAction(if (m.promotion) "关闭实验性流体云请求" else "开启实验性流体云请求",
            onClick = a.togglePromotion)
        SubAction("系统实时通知设置", onClick = a.promotionSettings)
        SubAction("复制脱敏设备诊断", onClick = a.copyDiagnostics)
    }
}

@Composable
private fun VerifyContent(m: MomoComposeModel, a: MomoComposeActions) {
    var spelling by remember { mutableStateOf("") }
    WordSection {
        HeaderRow("官方学习记录核验", "基线读取 → 墨墨原 App 反馈 → 返回回读")
        Status("Cortex 后台三态提交尚未启用；本页仅提供官方记录对照。")
        val word = m.snapshot?.words?.getOrNull(m.index)
        if (word != null) {
            Hint("当前待核验词：" + word.spelling)
            OutlinedTextField(value = spelling, onValueChange = { spelling = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("原 App 当前单词（可留空）") },
                shape = RoundedCornerShape(16.dp))
            ThreeMarks(enabled = !m.trialBusy) { a.startTrial(spelling, it) }
            SubAction("只读识别原 App 单词与按钮", onClick = a.inspectWord)
            SubAction("打开无障碍服务设置", onClick = a.accessibilitySettings)
        } else Hint("先同步当日真实单词，才能进行同词反馈核验。")
        if (m.pending != null) {
            HorizontalDivider(color = PaperStroke)
            Text("待确认：" + m.pending.spelling + " · " + m.pending.expected.label,
                color = QuietBlue, fontWeight = FontWeight.Bold)
            MainAction(if (m.trialBusy) "核验中…" else "验证官方记录",
                enabled = !m.trialBusy, onClick = a.verify)
            SubAction("取消当前核验", onClick = a.cancel)
        }
        if (m.trialStatus.isNotBlank()) Status(m.trialStatus,
            bad = m.trialStatus.contains("失败") || m.trialStatus.contains("无法"))
        if (m.report.isNotBlank()) SubAction("复制最近官方核验结果", onClick = a.copyReport)
    }
    if (m.recent.isNotEmpty()) WordSection {
        HeaderRow("最近反馈", "仅表示官方记录观察结果，不是 Cortex 自动写回")
        m.recent.forEach { item ->
            Status((item.spelling.ifBlank { "词条" }) + "  ·  " + item.mark.label +
                "  ·  " + item.outcome.label,
                good = item.outcome == FeedbackOutcome.SUCCEEDED,
                bad = item.outcome == FeedbackOutcome.FAILED)
        }
    }
    SubAction(if (m.verifyAdvanced) "收起技术诊断" else "技术诊断", onClick = a.toggleVerifyAdvanced)
    if (m.verifyAdvanced) WordSection {
        HeaderRow("原 App 操作诊断")
        Hint("前台：" + m.foregroundReport)
        Hint("只读识别：" + m.inspectionReport)
        SubAction("复制识别报告", onClick = a.copyInspection)
    }
}

@Composable
private fun ConnectionsContent(m: MomoComposeModel, a: MomoComposeActions) {
    // Tokens must never enter rememberSaveable / saved instance state.
    var openToken by remember { mutableStateOf("") }
    var cortexToken by remember { mutableStateOf("") }
    var legacyToken by remember { mutableStateOf("") }
    WordSection {
        HeaderRow("墨墨开放 API", "今日词表、进度与学习记录")
        Status(if (m.hasOpenToken) {
            if (m.snapshot != null) "已连接 · 今日数据已同步" else "凭证已保存 · 等待今日同步"
        } else "尚未配置")
        OutlinedTextField(value = openToken, onValueChange = { openToken = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("开放 API Token") },
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(16.dp))
        MainAction(if (m.hasOpenToken) "更换 Open API Token" else "保存 Open API Token",
            onClick = { val candidate = openToken; openToken = ""; a.saveOpenToken(candidate) })
        SubAction("清除开放 API 授权与学习缓存", onClick = a.clearOpenToken)
    }
    WordSection {
        HeaderRow("Cortex 连接", "独立身份凭证 · 当前仅检查只读接口")
        Status(m.cortexHealth.headline, good = m.cortexHealth.hasToken &&
            m.cortexHealth.lastResult == CortexCheckResult.READ_OK,
            bad = m.cortexHealth.lastResult == CortexCheckResult.HTTP_REJECTED)
        Hint(m.cortexHealth.detail)
        OutlinedTextField(value = cortexToken, onValueChange = { cortexToken = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Cortex Bearer Token") },
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(16.dp))
        MainAction(if (m.cortexBusy) "正在验证…" else "只读验证并加密保存",
            enabled = !m.cortexBusy,
            onClick = { val candidate = cortexToken; cortexToken = ""; a.saveCortex(candidate) })
        SubAction(if (m.legacyExpanded) "收起 Legacy 交换" else "Legacy Token 交换（备用）",
            onClick = a.toggleLegacy)
        if (m.legacyExpanded) {
            OutlinedTextField(value = legacyToken, onValueChange = { legacyToken = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Legacy Token") },
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(16.dp))
            SubAction("交换并验证", enabled = !m.cortexBusy,
                onClick = { val candidate = legacyToken; legacyToken = ""; a.legacyExchange(candidate) })
        }
        SubAction("检查 Cortex 只读连接",
            enabled = m.cortexHealth.hasToken && !m.cortexBusy, onClick = a.checkCortex)
        SubAction("清除 Cortex 授权与核验状态", onClick = a.clearCortex)
        if (m.cortexStatus.isNotBlank()) Status(m.cortexStatus,
            bad = m.cortexStatus.contains("失败") || m.cortexStatus.contains("拒绝"))
        Hint("凭证保存或只读成功，均不代表 Cortex 三态后台写回已实现。")
    }
}
