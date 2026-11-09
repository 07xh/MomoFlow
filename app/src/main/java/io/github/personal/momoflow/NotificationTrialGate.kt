package io.github.personal.momoflow

/** A notification action is only a request to open the official App for MANUAL feedback. */
data class NotificationTrialSelection(val word: StudyWord, val expected: LocalMark)

/** Refuse stale notification actions, inactive sessions, wrong IDs and unknown marks. */
object NotificationTrialGate {
    fun select(
        snapshot: StudySnapshot?,
        isToday: Boolean,
        active: Boolean,
        index: Int,
        wordId: String?,
        markWire: String?,
    ): NotificationTrialSelection? {
        if (!isToday || !active || snapshot == null) return null
        val current = snapshot.words.getOrNull(index) ?: return null
        if (current.id.isBlank() || current.spelling.isBlank() || current.isFinished || current.id != wordId) return null
        // The official UI exposes a spelling, not a voc_id. Never auto-act
        // if today's cache contains two senses with the same spelling.
        if (snapshot.words.count { it.spelling.equals(current.spelling, ignoreCase = true) } != 1) return null
        val mark = LocalMark.entries.firstOrNull { it.wire == markWire } ?: return null
        return NotificationTrialSelection(current, mark)
    }
}
