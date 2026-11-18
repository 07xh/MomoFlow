package io.github.personal.momoflow

/** Parse a personally obtained Cortex Bearer token without assuming JWT shape. */
object CortexTokenInput {
    fun normalize(raw: String): String {
        val value = raw.trim()
        require(!value.equals("Bearer", ignoreCase = true)) { "缺少 Cortex Token" }
        val token = if (value.startsWith("Bearer ", ignoreCase = true)) {
            value.substring(7).trim()
        } else value
        require(token.isNotEmpty() && token.length <= 8192 && token.none { it.isWhitespace() }) {
            "非法或空 Cortex Token"
        }
        return token
    }
}
