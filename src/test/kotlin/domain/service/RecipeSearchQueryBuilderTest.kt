package com.tenmilelabs.domain.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecipeSearchQueryBuilderTest {

    @Test
    fun `blank input returns null`() {
        assertNull(RecipeSearchQueryBuilder.build(""))
        assertNull(RecipeSearchQueryBuilder.build("   "))
    }

    @Test
    fun `all-punctuation input sanitises to nothing and returns null`() {
        assertNull(RecipeSearchQueryBuilder.build("!!!"))
        assertNull(RecipeSearchQueryBuilder.build("---"))
    }

    @Test
    fun `a stopword still produces a valid prefix term`() {
        // "the" legitimately matching nothing is a to_tsquery/English-dictionary concern, not a
        // sanitisation one — this builder has no stopword list, it just tokenises.
        assertEquals("the:*", RecipeSearchQueryBuilder.build("the"))
    }

    @Test
    fun `ampersand is stripped rather than interpreted as a tsquery operator`() {
        assertEquals("mac:* & cheese:*", RecipeSearchQueryBuilder.build("mac & cheese"))
    }

    @Test
    fun `apostrophe is stripped without raising`() {
        assertEquals("chef:* & s:* & salad:*", RecipeSearchQueryBuilder.build("chef's salad"))
    }

    @Test
    fun `sql metacharacters and tsquery syntax are neutralised into plain tokens`() {
        // None of tsquery's own operator characters (&, |, !, (, ), :, ') can survive into a
        // token — the bound parameter already stops SQL injection, this stops a to_tsquery syntax
        // error, which is the actual availability risk (verified live: this exact raw string
        // raises "ERROR: syntax error in tsquery" if passed to to_tsquery unsanitised).
        assertEquals("or:* & 1:* & 1:*", RecipeSearchQueryBuilder.build("' OR 1=1--"))
    }

    @Test
    fun `token count is capped at 8`() {
        val manyWords = (1..20).joinToString(" ") { "tok$it" }
        val result = RecipeSearchQueryBuilder.build(manyWords)
        assertEquals(8, result?.split(" & ")?.size)
        assertEquals((1..8).joinToString(" & ") { "tok$it:*" }, result)
    }

    @Test
    fun `a single token is truncated at 40 characters`() {
        val longToken = "a".repeat(60)
        assertEquals("${"a".repeat(40)}:*", RecipeSearchQueryBuilder.build(longToken))
    }

    @Test
    fun `unicode letters survive tokenisation`() {
        assertEquals("münchen:*", RecipeSearchQueryBuilder.build("MÜNCHEN"))
        assertEquals("日本語:*", RecipeSearchQueryBuilder.build("日本語"))
    }
}
