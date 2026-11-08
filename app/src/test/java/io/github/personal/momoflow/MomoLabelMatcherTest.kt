package io.github.personal.momoflow

import org.junit.Assert.*
import org.junit.Test

class MomoLabelMatcherTest {
    @Test fun matchesActualMaiMemoReviewButtonCaptions() {
        assertEquals("认识", MomoLabelMatcher.feedbackLabel("认识"))
        assertEquals("认识", MomoLabelMatcher.feedbackLabel("认识\n59 天后"))
        assertEquals("认识", MomoLabelMatcher.feedbackLabel("认识59天后"))
        assertEquals("模糊", MomoLabelMatcher.feedbackLabel("模糊 今日 / 4 天后"))
        assertEquals("忘记", MomoLabelMatcher.feedbackLabel("忘记 今日/3 天后"))
        assertEquals("忘记", MomoLabelMatcher.feedbackLabel("忘记\n今日 / 3 天后"))
    }

    @Test fun rejectsOrdinarySentencesAndUnrelatedElements() {
        assertNull(MomoLabelMatcher.feedbackLabel("认识新朋友"))
        assertNull(MomoLabelMatcher.feedbackLabel("忘记密码"))
        assertNull(MomoLabelMatcher.feedbackLabel("模糊查询"))
        assertNull(MomoLabelMatcher.feedbackLabel("这里是认识按钮"))
        assertNull(MomoLabelMatcher.feedbackLabel("已认识"))
        assertNull(MomoLabelMatcher.feedbackLabel("important"))
        assertNull(MomoLabelMatcher.feedbackLabel(null))
    }
}
