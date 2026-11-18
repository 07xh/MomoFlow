package io.github.personal.momoflow

import org.junit.Assert.*
import org.junit.Test

class CortexAuthPolicyTest {
    @Test fun storedTokenCannotBeCalledAuthorizedWithoutReadback() {
        val pending = CortexConnectionView(true, CortexCheckResult.NEVER, 0, 0)
        assertEquals("已保存 · 尚未核验", pending.headline)
        assertFalse(pending.headline.contains("成功"))
    }

    @Test fun http403IsRejectionNotAutomaticExpiration() {
        assertEquals(CortexCheckResult.HTTP_REJECTED, CortexAuthPolicy.classify(403))
        val rejected = CortexConnectionView(true, CortexCheckResult.HTTP_REJECTED, 1000, 403)
        assertTrue(rejected.headline.contains("403"))
        assertTrue(rejected.detail.contains("不能直接判定过期"))
    }

    @Test fun differentErrorsDoNotMasqueradeAsSuccess() {
        assertEquals(CortexCheckResult.RATE_LIMITED, CortexAuthPolicy.classify(429))
        assertEquals(CortexCheckResult.NETWORK_ERROR, CortexAuthPolicy.classify(-1))
        assertEquals("尚未配置", CortexConnectionView(false, CortexCheckResult.READ_OK, 50, 200).headline)
    }

    @Test fun passingReadOnlyDoesNotClaimWriteback() {
        assertTrue(CortexConnectionView(true, CortexCheckResult.READ_OK, 20, 200)
            .detail.contains("不能证明后台反馈写回"))
    }
}
