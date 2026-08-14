package com.tenmilelabs.tools.themealdb

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TheMealDbMapperTest {

    // ── parseMeasure ─────────────────────────────────────────────────────────

    @Test
    fun parsesSimpleWholeNumberWithUnit() {
        assertEquals(1.0 to "tbsp", TheMealDbMapper.parseMeasure("1 tbsp"))
        assertEquals(2.0 to "cups", TheMealDbMapper.parseMeasure("2 cups"))
    }

    @Test
    fun parsesFusedMetricAmountWhenSuffixIsKnownUnit() {
        // The exact class of bug ChefAI's adr-010 hit: "300g" must not collapse to qty=1/unit="unit".
        assertEquals(300.0 to "g", TheMealDbMapper.parseMeasure("300g"))
        assertEquals(2.0 to "kg", TheMealDbMapper.parseMeasure("2kg"))
    }

    @Test
    fun leavesFusedTokenAloneWhenSuffixIsNotAKnownUnit() {
        // e.g. a product name that happens to start with a digit — must not be mis-split.
        val (qty, unit) = TheMealDbMapper.parseMeasure("7up")
        assertEquals(1.0, qty)
        assertEquals("7up", unit)
    }

    @Test
    fun parsesSimpleFraction() {
        assertEquals(0.5 to "cup", TheMealDbMapper.parseMeasure("1/2 cup"))
    }

    @Test
    fun parsesUnicodeVulgarFraction() {
        assertEquals(0.5 to "cup", TheMealDbMapper.parseMeasure("½ cup"))
        assertEquals(0.75 to "tsp", TheMealDbMapper.parseMeasure("¾ tsp"))
    }

    @Test
    fun parsesMixedNumberWithUnicodeFraction() {
        val (qty, unit) = TheMealDbMapper.parseMeasure("1½ cups")
        assertEquals(1.5, qty)
        assertEquals("cups", unit)
    }

    @Test
    fun parsesRangeByTakingLowerBound() {
        val (qty, unit) = TheMealDbMapper.parseMeasure("1-2 tsp")
        assertEquals(1.0, qty)
        assertEquals("tsp", unit)
    }

    @Test
    fun fallsBackToWholeTextForNonNumericMeasures() {
        assertEquals(1.0 to "Dash", TheMealDbMapper.parseMeasure("Dash"))
        assertEquals(1.0 to "to taste", TheMealDbMapper.parseMeasure("to taste"))
    }

    @Test
    fun blankMeasureFallsBackToUnit() {
        assertEquals(1.0 to "unit", TheMealDbMapper.parseMeasure(""))
        assertEquals(1.0 to "unit", TheMealDbMapper.parseMeasure("   "))
    }

    // ── splitInstructions ────────────────────────────────────────────────────

    @Test
    fun splitsOnNewlinesAndDropsBlankLines() {
        val result = TheMealDbMapper.splitInstructions("Boil water.\n\nAdd pasta.\nDrain.")
        assertEquals(listOf("Boil water.", "Add pasta.", "Drain."), result)
    }

    @Test
    fun stripsLeadingStepNumbering() {
        val result = TheMealDbMapper.splitInstructions("1. Boil water\n2) Add pasta\nSTEP 3: Drain")
        assertEquals(listOf("Boil water", "Add pasta", "Drain"), result)
    }

    @Test
    fun singleShortBlobStaysOneStep() {
        val result = TheMealDbMapper.splitInstructions("Just mix everything together.")
        assertEquals(listOf("Just mix everything together."), result)
    }

    @Test
    fun longSingleBlobWithNoNewlinesFallsBackToSentenceSplit() {
        val long = "Boil the water. " + "Add the pasta and stir well until fully submerged. ".repeat(10) + "Drain it."
        val result = TheMealDbMapper.splitInstructions(long)
        assertTrue(result.size > 1)
        assertTrue(result.all { it.isNotBlank() })
    }

    @Test
    fun blankInstructionsProduceNoSteps() {
        assertEquals(emptyList(), TheMealDbMapper.splitInstructions(""))
    }

    // ── estimateTimingsAndServings ───────────────────────────────────────────

    @Test
    fun timingsAndServingsStayWithinDocumentedBounds() {
        val (prep, cook, servings) = TheMealDbMapper.estimateTimingsAndServings(
            stepCount = 50,
            instructionLength = 5000,
            ingredientCount = 30
        )
        assertTrue(prep in 5..25)
        assertTrue(cook in 10..90)
        assertTrue(servings in 2..6)
    }

    @Test
    fun smallRecipeGetsSmallEstimates() {
        val (prep, cook, servings) = TheMealDbMapper.estimateTimingsAndServings(
            stepCount = 1,
            instructionLength = 20,
            ingredientCount = 2
        )
        assertEquals(2, servings)
        assertTrue(prep >= 5)
        assertTrue(cook >= 10)
    }

    // ── deterministic UUIDs ──────────────────────────────────────────────────

    @Test
    fun recipeUuidIsDeterministicAndReadable() {
        val id = TheMealDbMapper.recipeUuidFor("52772")
        assertEquals(UUID.fromString("b0000000-0000-0000-0000-000000052772"), id)
        assertEquals(id, TheMealDbMapper.recipeUuidFor("52772"))
    }

    @Test
    fun ingredientUuidIsStableForSameNormalizedName() {
        val a = TheMealDbMapper.ingredientUuidFor(TheMealDbMapper.normalizeName("Chicken Breast"))
        val b = TheMealDbMapper.ingredientUuidFor(TheMealDbMapper.normalizeName("  chicken   breast "))
        assertEquals(a, b)
    }

    @Test
    fun differentNamesProduceDifferentUuids() {
        assertNotEquals(
            TheMealDbMapper.ingredientUuidFor("salt"),
            TheMealDbMapper.ingredientUuidFor("sugar")
        )
    }

    // ── map() end-to-end ─────────────────────────────────────────────────────

    private fun sampleMeal(
        idMeal: String = "52772",
        name: String = "Teriyaki Chicken Casserole",
        category: String? = "Chicken",
        area: String? = "Japanese",
        ingredients: List<RawIngredient> = listOf(
            RawIngredient("Soy Sauce", "3/4 cup"),
            RawIngredient("Water", "1/2 cup"),
            RawIngredient("Brown Sugar", "1/4 cup")
        )
    ) = RawMeal(
        idMeal = idMeal,
        name = name,
        category = category,
        area = area,
        instructions = "Preheat oven.\nMix sauce ingredients.\nBake for 30 minutes.",
        thumbnailUrl = "https://www.themealdb.com/images/media/meals/wvpsxx1468256321.jpg",
        tags = listOf("Meat", "Casserole"),
        source = "https://example-source.com/recipe",
        ingredients = ingredients
    )

    @Test
    fun mapsRecipeCoreFieldsAndAttribution() {
        val result = TheMealDbMapper.map(listOf(sampleMeal()), Catalog.EMPTY, importedAtMillis = 123_456L)
        val recipe = result.recipes.single()

        assertEquals("Teriyaki Chicken Casserole", recipe.title)
        assertEquals(TheMealDbMapper.SYSTEM_CREATOR_ID, recipe.creatorId)
        assertEquals("https://example-source.com/recipe", recipe.recipeExternalUrl)
        assertEquals(123_456L, recipe.updatedAtMillis)
        assertEquals(3, recipe.steps.size)
        assertEquals(1, recipe.steps.first().orderIndex)
    }

    @Test
    fun vegetarianAndVeganCategoriesBecomeLabelsNotTags() {
        val meal = sampleMeal(category = "Vegan", area = "Indian")
        val result = TheMealDbMapper.map(listOf(meal), Catalog.EMPTY, importedAtMillis = 1L)
        val recipe = result.recipes.single()

        assertEquals(1, result.newLabels.size)
        assertEquals("Vegan", result.newLabels.single().displayName)
        assertEquals(listOf(result.newLabels.single().id), recipe.labelIds)
        // area ("Indian") and the free-form tags still become tags, category does not duplicate as one
        assertTrue(result.newTags.any { it.displayName == "Indian" })
    }

    @Test
    fun reusesExistingCatalogIngredientInsteadOfMintingDuplicate() {
        val existingSaltId = UUID.randomUUID()
        val catalog = Catalog(ingredientIdsByNormalizedName = mapOf("salt" to existingSaltId))
        val meal = sampleMeal(ingredients = listOf(RawIngredient("Salt", "1 tsp")))

        val result = TheMealDbMapper.map(listOf(meal), catalog, importedAtMillis = 1L)

        assertTrue(result.newIngredients.isEmpty())
        assertEquals(existingSaltId, result.recipes.single().ingredients.single().ingredientId)
    }

    @Test
    fun duplicateIngredientNamesWithinOneRecipeMergeIntoOneRow() {
        val meal = sampleMeal(
            ingredients = listOf(
                RawIngredient("Salt", "1 tsp"),
                RawIngredient("salt", "1 tsp") // same ingredient, different casing/spacing
            )
        )
        val result = TheMealDbMapper.map(listOf(meal), Catalog.EMPTY, importedAtMillis = 1L)
        val recipe = result.recipes.single()

        assertEquals(1, recipe.ingredients.size)
        assertEquals(2.0, recipe.ingredients.single().quantity) // 1 tsp + 1 tsp merged
    }

    @Test
    fun sameIngredientNameAcrossMealsResolvesToSameNewlyMintedId() {
        val mealA = sampleMeal(idMeal = "1", ingredients = listOf(RawIngredient("Cumin", "1 tsp")))
        val mealB = sampleMeal(idMeal = "2", ingredients = listOf(RawIngredient("cumin", "2 tsp")))

        val result = TheMealDbMapper.map(listOf(mealA, mealB), Catalog.EMPTY, importedAtMillis = 1L)

        assertEquals(1, result.newIngredients.size)
        val idA = result.recipes[0].ingredients.single().ingredientId
        val idB = result.recipes[1].ingredients.single().ingredientId
        assertEquals(idA, idB)
    }
}
