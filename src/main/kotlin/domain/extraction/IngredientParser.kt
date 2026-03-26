package com.tenmilelabs.domain.extraction

import com.tenmilelabs.domain.model.ParsedIngredient

object IngredientParser {

    private val UNICODE_FRACTIONS = mapOf(
        '½' to "1/2", '⅓' to "1/3", '⅔' to "2/3",
        '¼' to "1/4", '¾' to "3/4",
        '⅛' to "1/8", '⅜' to "3/8", '⅝' to "5/8", '⅞' to "7/8",
        '⅕' to "1/5", '⅖' to "2/5", '⅗' to "3/5", '⅘' to "4/5",
        '⅙' to "1/6", '⅚' to "5/6",
    )

    // Quantity patterns (order matters — most specific first)
    // Parenthetical: "2 (14.5 oz)"
    private val PAREN_QTY = Regex("""^(\d+[\d./]*)\s*(\([^)]+\))\s*""")
    // Range: "1-2", "1–2", "1 to 2"
    private val RANGE_QTY = Regex("""^(\d+[\d./]*\s*[–\-]\s*\d+[\d./]*)\s+""")
    // Mixed number: "1 1/2"
    private val MIXED_QTY = Regex("""^(\d+\s+\d+/\d+)\s+""")
    // Simple fraction: "1/2"
    private val FRACTION_QTY = Regex("""^(\d+/\d+)\s+""")
    // Decimal or integer: "1.5", "2"
    private val SIMPLE_QTY = Regex("""^(\d+\.?\d*)\s+""")

    // Unit normalization map (lowercase key → canonical form)
    private val UNIT_MAP: Map<String, String> = buildMap {
        fun add(canonical: String, vararg aliases: String) {
            put(canonical.lowercase(), canonical)
            for (a in aliases) put(a.lowercase(), canonical)
        }
        add("tablespoon", "tablespoons", "tbsp", "tbsps", "tbs", "tbl")
        add("teaspoon", "teaspoons", "tsp", "tsps")
        add("cup", "cups")
        add("ounce", "ounces", "oz")
        add("pound", "pounds", "lb", "lbs")
        add("pint", "pints", "pt")
        add("quart", "quarts", "qt", "qts")
        add("gallon", "gallons", "gal")
        add("milliliter", "milliliters", "ml", "mls")
        add("liter", "liters", "l")
        add("gram", "grams", "g", "gm", "gms")
        add("kilogram", "kilograms", "kg", "kgs")
        add("milligram", "milligrams", "mg", "mgs")
        add("pinch", "pinches")
        add("dash", "dashes")
        add("can", "cans")
        add("package", "packages", "pkg", "pkgs")
        add("bunch", "bunches")
        add("clove", "cloves")
        add("slice", "slices")
        add("piece", "pieces")
        add("sprig", "sprigs")
        add("head", "heads")
        add("stalk", "stalks")
        add("stick", "sticks")
        add("bag", "bags")
        add("bottle", "bottles")
        add("jar", "jars")
        add("box", "boxes")
        add("handful", "handfuls")
        add("large", "lg")
        add("medium", "med")
        add("small", "sm")
    }

    fun parse(raw: String): ParsedIngredient {
        if (raw.isBlank()) return ParsedIngredient(raw = raw, quantity = null, unit = null, name = raw.trim(), preparation = null)

        var text = normalizeFractions(raw.trim())

        // Extract quantity
        val (quantity, afterQty) = extractQuantity(text)
        text = afterQty

        // Extract unit
        val (unit, afterUnit) = extractUnit(text)
        text = afterUnit

        // Extract preparation (after comma or in trailing parentheses)
        val (preparation, nameText) = extractPreparation(text)

        val name = nameText.trim().ifEmpty { raw.trim() }

        return ParsedIngredient(
            raw = raw,
            quantity = quantity,
            unit = unit,
            name = name,
            preparation = preparation?.trim()
        )
    }

    private fun normalizeFractions(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val replacement = UNICODE_FRACTIONS[ch]
            if (replacement != null) {
                // Insert space before fraction if preceded by a digit
                if (sb.isNotEmpty() && sb.last().isDigit()) sb.append(' ')
                sb.append(replacement)
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun extractQuantity(text: String): Pair<String?, String> {
        // Try each pattern in priority order
        PAREN_QTY.find(text)?.let { m ->
            val qty = "${m.groupValues[1]} ${m.groupValues[2]}"
            return qty to text.removeRange(m.range).trimStart()
        }
        RANGE_QTY.find(text)?.let { m ->
            val qty = m.groupValues[1].replace('–', '-')
            return qty to text.removeRange(m.range).trimStart()
        }
        MIXED_QTY.find(text)?.let { m ->
            return m.groupValues[1] to text.removeRange(m.range).trimStart()
        }
        FRACTION_QTY.find(text)?.let { m ->
            return m.groupValues[1] to text.removeRange(m.range).trimStart()
        }
        SIMPLE_QTY.find(text)?.let { m ->
            return m.groupValues[1] to text.removeRange(m.range).trimStart()
        }
        return null to text
    }

    private fun extractUnit(text: String): Pair<String?, String> {
        if (text.isEmpty()) return null to text

        // Take the first word, check against unit map
        val firstWord = text.split(Regex("""\s+"""), limit = 2).first()
        // Strip trailing period (e.g. "tbsp.")
        val candidate = firstWord.trimEnd('.')
        val normalized = UNIT_MAP[candidate.lowercase()]
        if (normalized != null) {
            val remainder = text.removePrefix(firstWord).trimStart()
            return normalized to remainder
        }
        return null to text
    }

    private fun extractPreparation(text: String): Pair<String?, String> {
        if (text.isEmpty()) return null to text

        // Check for trailing parenthetical: "chicken breast (boneless, skinless)"
        val parenMatch = Regex("""\s*\(([^)]+)\)\s*$""").find(text)
        if (parenMatch != null) {
            val prep = parenMatch.groupValues[1].trim()
            val name = text.removeRange(parenMatch.range).trim()
            return prep to name
        }

        // Check for comma-separated preparation: "flour, sifted"
        val commaIndex = text.indexOf(',')
        if (commaIndex > 0) {
            val name = text.substring(0, commaIndex).trim()
            val prep = text.substring(commaIndex + 1).trim()
            if (prep.isNotEmpty()) return prep to name
        }

        return null to text
    }
}
