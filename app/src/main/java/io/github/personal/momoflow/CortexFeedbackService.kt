package io.github.personal.momoflow

import android.content.Context

data class CortexWriteOutcome(val state: CortexWriteState, val message: String)

/**
 * Independent Cortex service; never mutates local practice marks, the
 * notification implementation, or the existing foreground-accessibility path.
 * Invoked only for an explicit notification tap and a separately enabled,
 * sample-verified Cortex configuration.
 */
class CortexFeedbackService(context: Context) {
    private val app = context.applicationContext
    private val secure = SecureStore(app)
    private val evidence = CortexEvidenceStore(app)
    private val journal = CortexWriteJournal(app)

    fun ready(): Boolean = secure.cortexDirectEnabled() &&
        secure.token() != null && secure.cortexToken() != null &&
        evidence.verified() != null

    fun latestState(): CortexWriteOutcome? = journal.latest()?.let {
        CortexWriteOutcome(it.state, it.message)
    }

    fun submit(word: StudyWord, mark: LocalMark): CortexWriteOutcome {
        if (!ready()) return CortexWriteOutcome(CortexWriteState.FAILED,
            "Cortex 尚未核准：请保留原有前台反馈方式。")
        val profile = evidence.verified() ?: return failed("缺少已校验的协议结构")
        val open = secure.token() ?: return failed("开放 API 凭证不可用")
        val cortex = secure.cortexToken() ?: return failed("Cortex 授权不可用")
        val numericId = word.id.toLongOrNull()?.takeIf { it > 0L }
            ?: return failed("当前单词 ID 不是经核验的数字格式，拒绝发送")
        val snapshot = secure.snapshot()
        if (!secure.active() || snapshot == null || !secure.isToday(snapshot) ||
            snapshot.words.getOrNull(secure.index())?.id != word.id || word.isFinished) {
            return failed("会话或目标单词已变化，拒绝发送")
        }
        val reader = CortexOpenApiReader(MaiMemoClient(open))
        val before = try { reader.read(word.id) }
        catch (_: Exception) { return failed("读取官方操作前基线失败，未发送") }
        if (before.today?.vocId != word.id || before.today?.isFinished != false ||
            (before.record != null && before.record.vocId != word.id)) {
            return failed("今日同词基线不完整或已完成，未发送")
        }
        if (open != secure.token() || cortex != secure.cortexToken()) {
            return failed("授权已变化，未发送")
        }
        val id = journal.key(open, cortex, StudyDay.today(), word.id)
        val entry = CortexWriteEntry(
            id, word.id, mark, StudyDay.today(), before, CortexWriteState.PENDING,
            "已登记唯一操作，尚未获得服务器确认。"
        )
        if (!journal.reserve(entry)) {
            return journal.get(id)?.let { CortexWriteOutcome(it.state,
                "该词今日已尝试过一次 Cortex 操作。"+it.message) }
                ?: failed("该词操作已被保留，不允许重复提交")
        }
        // The reservation is durably committed BEFORE any possible POST.
        val verdict = try { CortexHttpTransport(cortex, profile).submitOnce(numericId, mark) }
        catch (_: Exception) { CortexDispatchVerdict.OUTCOME_UNKNOWN }
        if (verdict == CortexDispatchVerdict.NOT_CONFIGURED) {
            // Checkpoint read failed BEFORE any POST was opened.
            journal.abandonUnsent(id)
            return failed("Cortex checkpoint 读取失败，未发起提交")
        }
        if (verdict == CortexDispatchVerdict.REJECTED) {
            journal.update(id, CortexWriteState.FAILED,
                "Cortex 服务明确拒绝了请求。为防止重复操作，本词今日不再自动提交。")
            return latestFor(id)
        }
        val report = if (verdict == CortexDispatchVerdict.OUTCOME_UNKNOWN)
            "上传结果不确定（可能已到达服务器），禁止重发；可重新回读确认。"
        else "Cortex HTTP 已接受，仍须核对墨墨官方学习记录。"
        journal.update(id, CortexWriteState.PENDING, report)
        return reconcileFor(id, open, cortex)
    }

    /** Re-read only; does not call Cortex POST or retry a prior operation. */
    fun reconcileLatest(): CortexWriteOutcome {
        val entry = journal.latest() ?: return failed("没有需要回读的 Cortex 操作")
        val open = secure.token() ?: return failed("开放 API 授权不存在，不能回读")
        val cortex = secure.cortexToken() ?: return failed("Cortex 授权不存在，不能回读")
        val expected = journal.key(open, cortex, entry.day, entry.vocId)
        if (entry.key != expected) return failed("账号凭证已变化，禁止比较旧账号状态")
        return reconcileFor(entry.key, open, cortex)
    }

    private fun reconcileFor(key: String, open: String, cortex: String): CortexWriteOutcome {
        val entry = journal.get(key) ?: return failed("操作记录已丢失")
        if (entry.state != CortexWriteState.PENDING) {
            return CortexWriteOutcome(entry.state, entry.message)
        }
        val current = try { CortexOpenApiReader(MaiMemoClient(open)).read(entry.vocId) }
        catch (_: Exception) {
            return CortexWriteOutcome(CortexWriteState.PENDING,
                "官方回读暂不可用，保留待确认；不会重复发送。")
        }
        if (open != secure.token() || cortex != secure.cortexToken()) {
            return failed("账号凭证在回读过程中发生变化，保留原操作记录")
        }
        val comparison = OfficialSyncAssessment.evaluate(
            entry.before.record, current.record,
            entry.before.today, current.today, entry.mark
        )
        val state = when (comparison.status) {
            OfficialSyncStatus.OBSERVED_MATCH -> CortexWriteState.SUCCESS
            OfficialSyncStatus.CONFLICT, OfficialSyncStatus.INVALID_WORD -> CortexWriteState.FAILED
            else -> CortexWriteState.PENDING
        }
        val message = when (state) {
            CortexWriteState.SUCCESS ->
                "已成功：官方回读观察到同词、同反馈匹配变化；仍不能单凭变化证明因果。"
            CortexWriteState.FAILED ->
                "已失败：官方回读结果与目标反馈冲突；不自动再次发送。"
            CortexWriteState.PENDING ->
                "待确认：官方尚未呈现可判定的同词反馈变化；请稍后仅回读，不重发。"
        }
        journal.update(key, state, message)
        return CortexWriteOutcome(state, message)
    }

    private fun latestFor(key: String): CortexWriteOutcome =
        journal.get(key)?.let { CortexWriteOutcome(it.state, it.message) }
            ?: failed("操作记录不可用")

    private fun failed(message: String) = CortexWriteOutcome(CortexWriteState.FAILED, message)
}
