package com.tenmilelabs.tools.themealdb

import java.util.UUID
import kotlin.math.roundToInt

/**
 * Pure mapping from TheMealDB's wire format to this repo's recipe schema. No I/O — everything
 * here is a deterministic function of its inputs, which is what makes it unit-testable against a
 * checked-in fixture instead of a live network call.
 *
 * Three things this schema requires that TheMealDB doesn't provide, resolved as documented in
 * docs/meal-plan-roadmap.md's Phase 1 plan:
 * - `prep_time_minutes` / `cook_time_minutes` / `servings` are NOT NULL — [estimateTimingsAndServings]
 *   derives bounded, clearly-synthesized heuristic values from step/ingredient counts. These are
 *   estimates, not sourced facts.
 * - `recipe_ingredients.quantity`/`unit` are NOT NULL, but TheMealDB gives free-text measures
 *   ("300g", "1/2 cup", "Dash") — [parseMeasure] handles fused amounts, unicode fractions, ranges,
 *   and free-text fallback.
 * - Ingredients/tags/labels are resolved by UUID against a shared catalog that is never
 *   auto-created by the write path — [TheMealDbMapper.map] resolves against an existing [Catalog]
 *   snapshot and mints deterministic new UUIDs for anything unmatched.
 */
object TheMealDbMapper {
    /** Fixed system "editorial" creator for every imported recipe — never reuse a real test user. */
    val SYSTEM_CREATOR_ID: UUID = UUID.fromString("10000000-0000-0000-0000-0000000000ff")
    const val SYSTEM_CREATOR_EMAIL = "themealdb@system.local"

    /** TheMealDB categories that map onto this schema's dietary *labels* rather than tags. */
    private val DIETARY_CATEGORY_NAMES = setOf("vegan", "vegetarian")

    private val VULGAR_FRACTIONS = mapOf(
        '¼' to "1/4", '½' to "1/2", '¾' to "3/4",
        '⅓' to "1/3", '⅔' to "2/3",
        '⅕' to "1/5", '⅖' to "2/5", '⅗' to "3/5", '⅘' to "4/5",
        '⅙' to "1/6", '⅚' to "5/6",
        '⅛' to "1/8", '⅜' to "3/8", '⅝' to "5/8", '⅞' to "7/8"
    )

    private val KNOWN_UNITS = setOf(
        "g", "gram", "grams", "kg", "ml", "l", "oz", "lb", "lbs",
        "cup", "cups", "tbsp", "tsp", "pinch", "pinches", "dash", "dashes",
        "can", "cans", "clove", "cloves", "slice", "slices", "sprig", "sprigs",
        "packet", "packets", "stick", "sticks"
    )

    private val MIXED_NUMBER_REGEX = Regex("""^(\d+)\s+(\d+)/(\d+)\s*(.*)$""")
    private val SIMPLE_FRACTION_REGEX = Regex("""^(\d+)/(\d+)\s*(.*)$""")
    private val FUSED_REGEX = Regex("""^(\d+(?:\.\d+)?)([a-zA-Z]+)$""")
    private val LEADING_NUMBER_REGEX = Regex("""^(\d+(?:\.\d+)?)\s*(.*)$""")
    private val RANGE_PREFIX_REGEX = Regex("""^(\d+(?:\.\d+)?)\s*-\s*\d+(?:\.\d+)?""")
    private val STEP_PREFIX_REGEX = Regex("""^(STEP\s*\d+[:.]?|\d+[.)])\s*""", RegexOption.IGNORE_CASE)
    private val SENTENCE_SPLIT_REGEX = Regex("""(?<=[.!?])\s+""")

    fun normalizeName(raw: String): String =
        raw.trim().lowercase().replace(Regex("\\s+"), " ")

    /**
     * Maps a full batch of meals against [catalog], returning both the mapped recipes and the
     * new ingredient/tag/label rows that must be inserted before the recipes that reference them.
     * Resolution is stateful *within this call only* — repeated names across meals in the same
     * batch correctly resolve to the same newly-minted id.
     */
    fun map(meals: List<RawMeal>, catalog: Catalog, importedAtMillis: Long): MappingResult {
        val newIngredients = LinkedHashMap<String, MappedIngredient>()
        val newTags = LinkedHashMap<String, MappedTag>()
        val newLabels = LinkedHashMap<String, MappedLabel>()

        fun resolveIngredient(name: String): MappedIngredient {
            val norm = normalizeName(name)
            catalog.ingredientIdsByNormalizedName[norm]?.let { return MappedIngredient(it, name.trim(), isNew = false) }
            return newIngredients.getOrPut(norm) { MappedIngredient(ingredientUuidFor(norm), name.trim(), isNew = true) }
        }

        fun resolveTag(name: String): MappedTag {
            val norm = normalizeName(name)
            catalog.tagIdsByNormalizedName[norm]?.let { return MappedTag(it, name.trim(), isNew = false) }
            return newTags.getOrPut(norm) { MappedTag(tagUuidFor(norm), name.trim(), isNew = true) }
        }

        fun resolveLabel(name: String): MappedLabel {
            val norm = normalizeName(name)
            catalog.labelIdsByNormalizedName[norm]?.let { return MappedLabel(it, name.trim(), isNew = false) }
            return newLabels.getOrPut(norm) { MappedLabel(labelUuidFor(norm), name.trim(), isNew = true) }
        }

        val recipes = meals.map { meal ->
            mapMeal(meal, importedAtMillis, ::resolveIngredient, ::resolveTag, ::resolveLabel)
        }

        return MappingResult(
            recipes = recipes,
            newIngredients = newIngredients.values.toList(),
            newTags = newTags.values.toList(),
            newLabels = newLabels.values.toList()
        )
    }

    private fun mapMeal(
        meal: RawMeal,
        importedAtMillis: Long,
        resolveIngredient: (String) -> MappedIngredient,
        resolveTag: (String) -> MappedTag,
        resolveLabel: (String) -> MappedLabel
    ): MappedRecipe {
        val steps = splitInstructions(meal.instructions).mapIndexed { index, text ->
            MappedStep(stepUuidFor(meal.idMeal, index + 1), index + 1, text)
        }

        // recipe_ingredients has PRIMARY KEY (recipe_id, ingredient_id) — a meal that lists the
        // same ingredient twice (e.g. "salt" for two different steps) must collapse to one row,
        // summing quantity when the unit matches and otherwise keeping the first-seen amount.
        val mergedIngredients = LinkedHashMap<UUID, MappedRecipeIngredient>()
        for (raw in meal.ingredients) {
            val resolved = resolveIngredient(raw.name)
            val (qty, unit) = parseMeasure(raw.measure)
            val existing = mergedIngredients[resolved.id]
            mergedIngredients[resolved.id] = when {
                existing == null -> MappedRecipeIngredient(resolved.id, qty, unit)
                existing.unit == unit -> existing.copy(quantity = existing.quantity + qty)
                else -> existing
            }
        }

        val (prep, cook, servings) = estimateTimingsAndServings(
            stepCount = steps.size,
            instructionLength = meal.instructions.length,
            ingredientCount = mergedIngredients.size
        )

        val tagIds = buildList {
            meal.area?.takeIf { it.isNotBlank() }?.let { add(resolveTag(it).id) }
            meal.category
                ?.takeIf { it.isNotBlank() && normalizeName(it) !in DIETARY_CATEGORY_NAMES }
                ?.let { add(resolveTag(it).id) }
            meal.tags.forEach { add(resolveTag(it).id) }
        }.distinct()

        val labelIds = buildList {
            meal.category
                ?.takeIf { normalizeName(it) in DIETARY_CATEGORY_NAMES }
                ?.let { add(resolveLabel(it).id) }
        }.distinct()

        val description = listOfNotNull(meal.area, meal.category)
            .plus(meal.tags)
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .ifBlank { meal.name }

        return MappedRecipe(
            id = recipeUuidFor(meal.idMeal),
            title = meal.name,
            description = description,
            imageUrl = meal.thumbnailUrl,
            imageUrlThumbnail = if (meal.thumbnailUrl.isBlank()) "" else "${meal.thumbnailUrl}/preview",
            prepTimeMinutes = prep,
            cookTimeMinutes = cook,
            servings = servings,
            creatorId = SYSTEM_CREATOR_ID,
            recipeExternalUrl = meal.source?.takeIf { it.isNotBlank() }
                ?: "https://www.themealdb.com/meal/${meal.idMeal}",
            updatedAtMillis = importedAtMillis,
            steps = steps,
            ingredients = mergedIngredients.values.toList(),
            tagIds = tagIds,
            labelIds = labelIds
        )
    }

    // ── Measure parsing ────────────────────────────────────────────────────────

    /**
     * Parses a TheMealDB free-text measure into (quantity, unit). Never throws — anything that
     * doesn't look numeric becomes `1.0` of the raw text itself (e.g. "Dash", "to taste"), since
     * both columns are NOT NULL and a wrong-but-present unit beats a crashed import.
     */
    fun parseMeasure(rawMeasure: String): Pair<Double, String> {
        val s = normalizeFractions(rawMeasure.trim())
        if (s.isBlank()) return 1.0 to "unit"

        val rangeCollapsed = RANGE_PREFIX_REGEX.find(s)?.let { match ->
            match.groupValues[1] + s.substring(match.range.last + 1)
        } ?: s

        MIXED_NUMBER_REGEX.matchEntire(rangeCollapsed)?.let { m ->
            val whole = m.groupValues[1].toDouble()
            val num = m.groupValues[2].toDouble()
            val den = m.groupValues[3].toDouble()
            val unit = m.groupValues[4].trim()
            return (whole + safeDiv(num, den)) to unit.ifBlank { "unit" }
        }
        SIMPLE_FRACTION_REGEX.matchEntire(rangeCollapsed)?.let { m ->
            val num = m.groupValues[1].toDouble()
            val den = m.groupValues[2].toDouble()
            val unit = m.groupValues[3].trim()
            return safeDiv(num, den) to unit.ifBlank { "unit" }
        }
        FUSED_REGEX.matchEntire(rangeCollapsed)?.let { m ->
            val qty = m.groupValues[1].toDouble()
            val unit = m.groupValues[2]
            // Only split a fused token when the suffix normalizes to a known unit — otherwise
            // something like a product name that happens to start with digits ("7up") would get
            // mangled. Mirrors the lesson from ChefAI's client-side scraper (adr-010).
            return if (unit.lowercase() in KNOWN_UNITS) qty to unit else 1.0 to s
        }
        LEADING_NUMBER_REGEX.matchEntire(rangeCollapsed)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull()
            val unit = m.groupValues[2].trim()
            if (qty != null) return qty to unit.ifBlank { "unit" }
        }
        // No leading number at all: "Dash", "to taste", "pinch of salt".
        return 1.0 to s
    }

    private fun safeDiv(num: Double, den: Double): Double = if (den == 0.0) num else num / den

    private fun normalizeFractions(s: String): String {
        var result = s
        for ((char, replacement) in VULGAR_FRACTIONS) {
            result = result.replace(Regex("(\\d)$char"), "$1 $replacement")
            result = result.replace(char.toString(), replacement)
        }
        return result
    }

    // ── Instruction splitting ───────────────────────────────────────────────────

    fun splitInstructions(raw: String): List<String> {
        val byNewline = raw.split(Regex("\r\n|\r|\n")).map(::cleanStepText).filter { it.isNotBlank() }
        if (byNewline.size > 1) return byNewline

        val single = cleanStepText(raw)
        if (single.isBlank()) return emptyList()
        if (single.length <= 400) return listOf(single)

        // A long single blob with no newlines at all: fall back to sentence boundaries so one
        // recipe doesn't become a single wall-of-text step.
        return single.split(SENTENCE_SPLIT_REGEX).map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun cleanStepText(raw: String): String =
        raw.trim().replace(STEP_PREFIX_REGEX, "").trim()

    // ── Timing/servings heuristic ───────────────────────────────────────────────

    /**
     * TheMealDB provides no prep/cook time or serving count, but this schema's columns are
     * NOT NULL and meal-plan generation filters on prep+cook via `maxPrepTimeMinutes`. These are
     * crude, clearly-bounded estimates from recipe shape — not sourced facts — chosen so the
     * filter actually discriminates between quick and involved recipes rather than every imported
     * recipe landing on identical values.
     */
    fun estimateTimingsAndServings(stepCount: Int, instructionLength: Int, ingredientCount: Int): Triple<Int, Int, Int> {
        val prep = (5 + ingredientCount * 1.5).roundToInt().coerceIn(5, 25)
        val cook = (10.0 + stepCount * 6 + instructionLength / 80.0).roundToInt().coerceIn(10, 90)
        val servings = when {
            ingredientCount <= 5 -> 2
            ingredientCount <= 10 -> 4
            else -> 6
        }
        return Triple(prep, cook, servings)
    }

    // ── Deterministic UUIDs ─────────────────────────────────────────────────────
    // Deterministic (not random) so re-running the importer against an unchanged TheMealDB
    // catalog regenerates byte-identical UUIDs and the emitted SQL diffs cleanly.

    /** `b0000000-0000-0000-0000-<idMeal padded to 12 digits>` — continues this seed's readable-prefix convention. */
    fun recipeUuidFor(idMeal: String): UUID {
        val digits = idMeal.trim()
        require(digits.isNotEmpty() && digits.all { it.isDigit() } && digits.length <= 12) {
            "unexpected TheMealDB idMeal format: '$idMeal'"
        }
        return UUID.fromString("b0000000-0000-0000-0000-${digits.padStart(12, '0')}")
    }

    fun stepUuidFor(idMeal: String, orderIndex: Int): UUID =
        UUID.nameUUIDFromBytes("themealdb:step:$idMeal:$orderIndex".toByteArray())

    fun ingredientUuidFor(normalizedName: String): UUID =
        UUID.nameUUIDFromBytes("themealdb:ingredient:$normalizedName".toByteArray())

    fun tagUuidFor(normalizedName: String): UUID =
        UUID.nameUUIDFromBytes("themealdb:tag:$normalizedName".toByteArray())

    fun labelUuidFor(normalizedName: String): UUID =
        UUID.nameUUIDFromBytes("themealdb:label:$normalizedName".toByteArray())
}
