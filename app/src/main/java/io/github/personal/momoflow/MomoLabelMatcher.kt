package io.github.personal.momoflow

/**
 * Accepts only the three documented review-button labels and narrowly scoped
 * scheduling suffixes from the official MaiMemo review page.
 * Pure Kotlin so it can be unit-tested without Android UI dependencies.
 */
object MomoLabelMatcher {
    private val names = listOf("认识", "模糊", "忘记")
    private val detail = Regex("""^[\s,，:：;；·（）()]*((今日|今天)(\s*[/／]\s*\d+\s*天后)?|\d+\s*天后)?[\s,，:：;；·（）()]*$""")

    fun feedbackLabel(value: String?): String? {
        val normalized = value?.replace('\u00A0', ' ')?.trim() ?: return null
        if (normalized.length > 64) return null
        return names.firstOrNull { label ->
            normalized.startsWith(label) && detail.matches(normalized.substring(label.length))
        }
    }
}
