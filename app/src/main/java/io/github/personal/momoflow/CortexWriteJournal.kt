package io.github.personal.momoflow

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

/** A transport timeout is PENDING, not FAILED: it may already have been sent. */
enum class CortexWriteState { PENDING, SUCCESS, FAILED }

data class CortexWriteEntry(
    val key: String,
    val vocId: String,
    val mark: LocalMark,
    val day: String,
    val before: CortexOfficialSnapshot,
    val state: CortexWriteState,
    val message: String,
    val checkpointBefore: Long? = null,
    val checkpointAfter: Long? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("key", key).put("voc_id", vocId).put("mark", mark.wire)
        .put("day", day).put("before_record", before.record?.toJson())
        .put("before_today", before.today?.toJson()).put("state", state.name)
        .put("message", message.take(240))
        .put("checkpoint_before", checkpointBefore)
        .put("checkpoint_after", checkpointAfter)

    companion object {
        fun fromJson(value: JSONObject): CortexWriteEntry = CortexWriteEntry(
            value.getString("key"), value.getString("voc_id"),
            LocalMark.entries.first { it.wire == value.getString("mark") },
            value.getString("day"),
            CortexOfficialSnapshot(
                value.optJSONObject("before_record")?.let(OfficialRecord::parse),
                value.optJSONObject("before_today")?.let(OfficialTodayState::parse)
            ),
            CortexWriteState.valueOf(value.getString("state")), value.optString("message"),
            (value.opt("checkpoint_before") as? Number)?.toLong(),
            (value.opt("checkpoint_after") as? Number)?.toLong()
        )
    }
}

/**
 * A word is reserved once per China study day and credential pair, regardless
 * of selected feedback type. A second click must NOT create a second POST.
 * Persistence uses synchronous commit BEFORE any potentially ambiguous send.
 */
class CortexWriteJournal(context: Context) {
    private val prefs = context.getSharedPreferences("momoflow_local", Context.MODE_PRIVATE)

    fun key(openToken: String, cortexToken: String, day: String, wordId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(
            (openToken + "\u0000" + cortexToken + "\u0000" + day + "\u0000" + wordId)
                .toByteArray(Charsets.UTF_8)
        )
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun slot(key: String) = "cortex.write.$key"

    @Synchronized fun reserve(entry: CortexWriteEntry): Boolean {
        if (prefs.contains(slot(entry.key))) return false
        return prefs.edit().putString(slot(entry.key), entry.toJson().toString())
            .putString("cortex.last.key", entry.key).commit()
    }

    fun get(key: String): CortexWriteEntry? = try {
        prefs.getString(slot(key), null)?.let { CortexWriteEntry.fromJson(JSONObject(it)) }
    } catch (_: Exception) { null }

    fun latest(): CortexWriteEntry? =
        prefs.getString("cortex.last.key", null)?.let(::get)

    @Synchronized fun update(key: String, state: CortexWriteState, reason: String): CortexWriteEntry? {
        val old = get(key) ?: return null
        val next = old.copy(state = state, message = reason)
        check(prefs.edit().putString(slot(key), next.toJson().toString()).commit())
        return next
    }

    @Synchronized fun recordCheckpoints(key: String, before: Long, after: Long? = null) {
        val old = get(key) ?: error("Missing durable operation before checkpoint update")
        check(prefs.edit().putString(slot(key), old.copy(
            checkpointBefore = before, checkpointAfter = after
        ).toJson().toString()).commit())
    }

    /** Safe only when the transport proved no POST was attempted. */
    @Synchronized fun abandonUnsent(key: String) {
        prefs.edit().remove(slot(key)).commit()
    }
}
