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
        if (current.id.isBlank() || current.id != wordId) return null
        val mark = LocalMark.entries.firstOrNull { it.wire == markWire } ?: return null
        return NotificationTrialSelection(current, mark)
    }
}
