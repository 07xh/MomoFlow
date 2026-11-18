package io.github.personal.momoflow

/**
 * Non-public Cortex study endpoints inferred from APK 5.6.10 plain strings.
 *
 * These are discovery leads, NOT a verified HTTP contract: authentication,
 * wire payloads, signing and sync ordering have not been established.
 */
object CortexStudyContract {
    const val BASE_URL = "https://cortex.maimemo.com/cortex"

    enum class Endpoint(val path: String) {
        UPLOAD_OPLOG("/api/v1/study/oplog"),
        MERGE_MEMO("/api/v1/study/merge/memo"),
        DOWNLOAD_STUDY("/api/v1/study/download"),
        CHECKPOINTS("/api/v1/study/checkpoints"),
        STUDY_OVERVIEW("/api/v1/study/overview"),
        ALGORITHM_CONFIG("/api/v1/study/alg_config"),
    }

    fun endpointUrl(endpoint: Endpoint): String = BASE_URL + endpoint.path
}

/**
 * Protobuf enum *names* observed in the decompiled APK's string table.
 * This is not the same wire format as the public Open API's FAMILIAR/VAGUE/FORGET.
 * Neither field numbers nor numeric enum values have been verified.
 */
enum class CortexResponseTag(val protoName: String) {
    FAMILIAR("RESPONSE_FAMILIAR"),
    UNCERTAIN("RESPONSE_UNCERTAIN"),
    VAGUE("RESPONSE_VAGUE"),
    FORGET("RESPONSE_FORGET"),
    WELL_FAMILIAR("RESPONSE_WELL_FAMILIAR"),
    CANCEL_WELL_FAMILIAR("RESPONSE_CANCEL_WELL_FAMILIAR"),
    COMPLETED("RESPONSE_COMPLETED"),
    UNSPECIFIED("STUDY_RESPONSE_UNSPECIFIED"),
}

/**
 * Local intent only: no checkpoint, token, request body, signature or HTTP
 * transaction is fabricated from incomplete APK strings.
 */
data class CortexFeedbackDraft(
    val vocId: String,
    val selectedMark: LocalMark,
    val responseCandidates: Set<CortexResponseTag>,
) {
    init {
        require(vocId.isNotBlank()) { "单词 ID 不能为空" }
        require(responseCandidates.isNotEmpty()) { "反馈枚举候选不能为空" }
    }
}

/** Missing evidence needed before any server submission can be implemented. */
enum class CortexEvidenceGap(val description: String) {
    AUTHENTICATION("Cortex 的合法认证方式和会话边界"),
    REQUEST_SCHEMA("上传请求的完整编码、字段号与请求头"),
    SYNC_SEQUENCE("oplog / merge / checkpoint 的真实调用关系"),
    CHECKPOINT_LIFECYCLE("checkpoint 的取得、更新与冲突处理"),
    SIGNATURE_RULES("有无签名及服务端允许的调用方式"),
    READBACK("本人账号上同词、同反馈的服务器回读闭环"),
}

enum class CortexSubmissionState {
    NOT_SENT_UNVERIFIED,
}

data class CortexSubmissionResult(
    val state: CortexSubmissionState,
    val networkAttempted: Boolean,
    val missing: Set<CortexEvidenceGap>,
)

/**
 * A deliberately inert boundary for future verified integration.
 *
 * Existing official Open API reads and the guarded foreground action stay
 * independent. There is no runtime switch to enable Cortex writes.
 */
class CortexStudyAdapter {
    val missingEvidence: Set<CortexEvidenceGap>
        get() = CortexEvidenceGap.entries.toSet()

    fun draft(word: StudyWord, mark: LocalMark): CortexFeedbackDraft {
        require(word.id.isNotBlank()) { "单词 ID 不能为空" }
        val choices = when (mark) {
            LocalMark.FAMILIAR -> setOf(CortexResponseTag.FAMILIAR)
            // A distinct wasm enum has UNCERTAIN while the Cortex JSON model
            // has VAGUE. Use the wasm NAME only here; numeric value still
            // requires a real on-device runtime capture.
            LocalMark.VAGUE -> setOf(CortexResponseTag.UNCERTAIN)
            LocalMark.FORGET -> setOf(CortexResponseTag.FORGET)
        }
        return CortexFeedbackDraft(word.id, mark, choices)
    }

    @Suppress("UNUSED_PARAMETER")
    fun submit(draft: CortexFeedbackDraft): CortexSubmissionResult =
        CortexSubmissionResult(
            state = CortexSubmissionState.NOT_SENT_UNVERIFIED,
            networkAttempted = false,
            missing = missingEvidence,
        )
}
