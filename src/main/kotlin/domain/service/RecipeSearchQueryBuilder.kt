package com.tenmilelabs.domain.service

/**
 * Turns raw user search input into a Postgres tsquery string that is safe to pass as a bound
 * parameter to `to_tsquery('english', ?)`.
 *
 * This exists because `to_tsquery` itself rejects malformed syntax — a bound parameter already
 * makes SQL injection impossible, but it does nothing to stop `to_tsquery('english', "a' OR
 * 1=1--")` (or far more mundane input like `"mac & cheese"` or `"chef's salad"`) from raising a
 * Postgres syntax error. Every token here is treated as a plain search term: punctuation is
 * stripped, never interpreted as a tsquery operator.
 */
object RecipeSearchQueryBuilder {
    private const val MAX_TOKENS = 8
    private const val MAX_TOKEN_LENGTH = 40
    private val TOKEN_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")

    /**
     * Returns a tsquery string — every token prefix-matched via `:*` (as-you-type matching) and
     * ANDed together — or `null` if [rawQuery] sanitises down to nothing (all punctuation, blank,
     * etc.).
     *
     * Callers MUST treat `null` as "return no results" and must never call `to_tsquery` with an
     * empty string or fall back to the raw input — both raise a Postgres syntax error.
     *
     * Every token is stripped to letters/digits only (`\p{L}`/`\p{N}`, so accented and non-Latin
     * scripts survive) before being reassembled, which is also what makes the output always
     * syntactically valid tsquery regardless of how adversarial the input is: none of tsquery's own
     * operator characters (`&`, `|`, `!`, `(`, `)`, `:`, `'`) can survive into a token.
     */
    fun build(rawQuery: String): String? {
        val tokens = rawQuery
            .lowercase()
            .split(TOKEN_SEPARATOR)
            .filter { it.isNotBlank() }
            .map { it.take(MAX_TOKEN_LENGTH) }
            .take(MAX_TOKENS)

        if (tokens.isEmpty()) return null

        return tokens.joinToString(separator = " & ") { "$it:*" }
    }
}
