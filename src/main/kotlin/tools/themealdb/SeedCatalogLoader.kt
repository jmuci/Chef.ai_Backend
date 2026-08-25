package com.tenmilelabs.tools.themealdb

import java.io.File
import java.util.UUID

/**
 * Reads the existing `ingredients`/`tags`/`labels` catalog straight out of `seed.sql`, so the
 * importer can resolve TheMealDB ingredient/tag/category names against what's already seeded
 * (e.g. reuse the existing "Salt" ingredient row rather than minting a duplicate) without a live
 * DB connection — this tool intentionally never opens one.
 *
 * This is a small regex-based reader tailored to this repo's own consistent single-statement,
 * one-tuple-per-line INSERT format — not a general SQL parser. If `seed.sql`'s formatting
 * changes shape, this needs updating alongside it.
 */
object SeedCatalogLoader {
    private val ROW_REGEX = Regex("""\(\s*'([0-9a-fA-F-]{36})'\s*,\s*'((?:[^'\\]|\\.)*)'""")

    /**
     * `--` line comments, stripped before parsing. [parseTable]'s statement regex is non-greedy up
     * to the first `;`, so a semicolon inside a comment would silently truncate the statement and
     * hide every row after it — the importer would then mint duplicate catalog rows under fresh
     * UUIDs instead of reusing the curated ones, which is near-invisible until two identically
     * named tags show up in the app.
     */
    private val LINE_COMMENT_REGEX = Regex("""--[^\n]*""")

    fun load(seedSqlFile: File): Catalog {
        val text = LINE_COMMENT_REGEX.replace(seedSqlFile.readText(), "")
        return Catalog(
            ingredientIdsByNormalizedName = parseTable(text, "ingredients"),
            tagIdsByNormalizedName = parseTable(text, "tags"),
            labelIdsByNormalizedName = parseTable(text, "labels")
        )
    }

    private fun parseTable(text: String, table: String): Map<String, UUID> {
        val statementRegex = Regex("""INSERT INTO $table \([^)]*\)\s*VALUES([\s\S]*?);""")
        val result = LinkedHashMap<String, UUID>()
        for (statement in statementRegex.findAll(text)) {
            for (row in ROW_REGEX.findAll(statement.groupValues[1])) {
                val id = runCatching { UUID.fromString(row.groupValues[1]) }.getOrNull() ?: continue
                val name = TheMealDbMapper.normalizeName(row.groupValues[2].replace("''", "'"))
                result.putIfAbsent(name, id)
            }
        }
        return result
    }
}
