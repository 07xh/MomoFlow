package io.github.personal.momoflow

/**
 * End-to-end coordination for a future *authorized* Cortex submission.
 *
 * Existing API reads do not authorize Cortex writes. The production gateway
 * remains unavailable until an authenticated, verified wire contract exists.
 * This module never serializes or invents a Cortex request.
 */
data class CortexOfficialSnapshot(
    val record: OfficialRecord?,
    val today: OfficialTodayState?,
)

fun interface CortexOfficialReader {
    fun read(vocId: String): CortexOfficialSnapshot
}

/** Can be backed by the currently implemented, documented read-only API. */
class CortexOpenApiReader(private val client: MaiMemoClient) : CortexOfficialReader {
    override fun read(vocId: String): CortexOfficialSnapshot =
        CortexOfficialSnapshot(client.record(vocId), client.todayState(vocId))
}

/** An HTTP response alone is not an official learning-state confirmation. */
enum class CortexDispatchVerdict {
    NOT_CONFIGURED,
    REJECTED,
    ACCEPTED_UNVERIFIED,
    OUTCOME_UNKNOWN,
}

fun interface CortexAuthorizedSender {
    /**
     * One authorized send attempt at most. Implementations MUST NOT retry
     * implicitly after a timeout; first reconcile via official readback.
     */
    fun send(draft: CortexFeedbackDraft, operationId: String): CortexDispatchVerdict
}

/** Default gateway intentionally refuses any non-public write. */
object CortexUnconfiguredSender : CortexAuthorizedSender {
    override fun send(draft: CortexFeedbackDraft, operationId: String) =
        CortexDispatchVerdict.NOT_CONFIGURED
}

enum class CortexTransactionStatus {
    AMBIGUOUS_RESPONSE,
    DUPLICATE_OPERATION,
    MISSING_BASELINE,
    ACCOUNT_CHANGED,
    NOT_SENT,
    REJECTED,
    OUTCOME_UNKNOWN,
    READBACK_PENDING,
    READBACK_CONFLICT,
    OBSERVED_MATCH,
}

data class CortexTransactionResult(
    val status: CortexTransactionStatus,
    /** True only if we entered an implementation that MAY have sent a request. */
    val sendAttempted: Boolean,
    /** A confirmed match is still not proof of causality without server receipt. */
    val officialReadback: OfficialSyncStatus? = null,
)

/**
 * One transaction ID may be used only once inside a live coordinator instance.
 * Callers must persist IDs across process death before enabling any real sender.
 *
 * This class is platform-independent for deterministic unit tests.
 */
class CortexFeedbackTransaction(
    private val reader: CortexOfficialReader,
    private val sender: CortexAuthorizedSender = CortexUnconfiguredSender,
) {
    private val usedOperationIds = mutableSetOf<String>()

    fun run(
        draft: CortexFeedbackDraft,
        operationId: String,
        isSameAccount: () -> Boolean,
    ): CortexTransactionResult {
        require(operationId.isNotBlank()) { "操作 ID 不能为空" }
        val requiredTag = when (draft.selectedMark) {
            LocalMark.FAMILIAR -> CortexResponseTag.FAMILIAR
            LocalMark.FORGET -> CortexResponseTag.FORGET
            // OpLog uses wasm UNCERTAIN (not Cortex JSON VAGUE).
            LocalMark.VAGUE -> CortexResponseTag.UNCERTAIN
        }
        if (requiredTag == null || draft.responseCandidates != setOf(requiredTag)) {
            return CortexTransactionResult(CortexTransactionStatus.AMBIGUOUS_RESPONSE, false)
        }
        // Reserve the ID even for an unsuccessful request, so a timeout will
        // never cause a blind, potentially duplicate resend.
        synchronized(usedOperationIds) {
            if (!usedOperationIds.add(operationId)) {
                return CortexTransactionResult(CortexTransactionStatus.DUPLICATE_OPERATION, false)
            }
        }
        if (!isSameAccount()) return CortexTransactionResult(CortexTransactionStatus.ACCOUNT_CHANGED, false)

        val before = try { reader.read(draft.vocId) }
        catch (_: Exception) { return CortexTransactionResult(CortexTransactionStatus.MISSING_BASELINE, false) }
        if (!validBaseline(before, draft.vocId)) {
            return CortexTransactionResult(CortexTransactionStatus.MISSING_BASELINE, false)
        }
        if (!isSameAccount()) return CortexTransactionResult(CortexTransactionStatus.ACCOUNT_CHANGED, false)

        val dispatch = try { sender.send(draft, operationId) }
        catch (_: Exception) { CortexDispatchVerdict.OUTCOME_UNKNOWN }
        if (dispatch == CortexDispatchVerdict.NOT_CONFIGURED) {
            return CortexTransactionResult(CortexTransactionStatus.NOT_SENT, false)
        }
        if (dispatch == CortexDispatchVerdict.REJECTED) {
            return CortexTransactionResult(CortexTransactionStatus.REJECTED, true)
        }

        // On a timeout the request may have reached the service. Read first;
        // do not issue a second network submission automatically.
        if (!isSameAccount()) return CortexTransactionResult(CortexTransactionStatus.ACCOUNT_CHANGED, true)
        val after = try { reader.read(draft.vocId) }
        catch (_: Exception) {
            return CortexTransactionResult(
                if (dispatch == CortexDispatchVerdict.OUTCOME_UNKNOWN)
                    CortexTransactionStatus.OUTCOME_UNKNOWN
                else CortexTransactionStatus.READBACK_PENDING,
                true
            )
        }
        if (!isSameAccount()) return CortexTransactionResult(CortexTransactionStatus.ACCOUNT_CHANGED, true)
        // Never permit a result for another voc_id, even if all other fields match.
        if (after.today?.vocId != draft.vocId ||
            (after.record != null && after.record.vocId != draft.vocId)) {
            return CortexTransactionResult(CortexTransactionStatus.READBACK_CONFLICT, true, OfficialSyncStatus.INVALID_WORD)
        }
        val result = OfficialSyncAssessment.evaluate(
            before.record, after.record, before.today, after.today, draft.selectedMark
        )
        val status = when (result.status) {
            OfficialSyncStatus.OBSERVED_MATCH -> CortexTransactionStatus.OBSERVED_MATCH
            OfficialSyncStatus.CONFLICT, OfficialSyncStatus.INVALID_WORD -> CortexTransactionStatus.READBACK_CONFLICT
            else -> if (dispatch == CortexDispatchVerdict.OUTCOME_UNKNOWN)
                CortexTransactionStatus.OUTCOME_UNKNOWN
            else CortexTransactionStatus.READBACK_PENDING
        }
        return CortexTransactionResult(status, true, result.status)
    }

    private fun validBaseline(snapshot: CortexOfficialSnapshot, id: String): Boolean =
        snapshot.today?.vocId == id && snapshot.today?.isFinished == false &&
            (snapshot.record == null || snapshot.record.vocId == id)
}
