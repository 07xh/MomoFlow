package io.github.personal.momoflow

import org.junit.Assert.*
import org.junit.Test

class ForegroundFeedbackGateTest {
    private val labels = mapOf("认识" to 1, "模糊" to 1, "忘记" to 1)
    private fun eligible(
        wordCount: Int = 1,
        visible: Map<String, Int> = labels,
        actionable: Map<String, Int> = labels,
        targetsDistinct: Boolean = true,
        expected: String = "认识"
    ) = ForegroundFeedbackGate.canAct(expected, wordCount, visible, actionable, targetsDistinct)

    @Test fun stableUniqueTargetCanBeEligible() {
        assertTrue(eligible())
        assertTrue(eligible(expected = "模糊"))
        assertTrue(eligible(expected = "忘记"))
    }

    @Test fun wrongOrHiddenCurrentWordAlwaysFailsClosed() {
        assertFalse(eligible(wordCount = 0))
        assertFalse(eligible(wordCount = 2))
    }

    @Test fun duplicateOrMissingFeedbackLabelsAreRejected() {
        assertFalse(eligible(visible = labels + ("认识" to 2)))
        assertFalse(eligible(visible = labels - "忘记"))
        assertFalse(eligible(actionable = labels - "模糊"))
        assertFalse(eligible(actionable = labels + ("忘记" to 2)))
    }

    @Test fun sharedClickableParentAndUnknownMarkAreRejected() {
        assertFalse(eligible(targetsDistinct = false))
        assertFalse(eligible(expected = "熟知"))
    }

    @Test fun noPartialThreeButtonPageMayClick() {
        assertFalse(eligible(visible = mapOf("认识" to 1), actionable = mapOf("认识" to 1)))
    }
}
