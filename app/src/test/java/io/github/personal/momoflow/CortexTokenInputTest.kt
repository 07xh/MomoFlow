package io.github.personal.momoflow

import org.junit.Assert.*
import org.junit.Test

class CortexTokenInputTest {
    @Test fun importsRawOrBearerPrefixedTokens() {
        assertEquals("a.b.c", CortexTokenInput.normalize("a.b.c"))
        assertEquals("a.b.c", CortexTokenInput.normalize("  Bearer a.b.c  "))
        assertEquals("a.b.c", CortexTokenInput.normalize("BEARER a.b.c"))
    }

    @Test fun refusesEmptyMultilineOrOversizedSecrets() {
        for (value in listOf("", "Bearer", "Bearer  ", "a b", "a\nb", "x".repeat(8193))) {
            try {
                CortexTokenInput.normalize(value)
                fail("invalid token accepted")
            } catch (_: IllegalArgumentException) {
                // required
            }
        }
    }
}
