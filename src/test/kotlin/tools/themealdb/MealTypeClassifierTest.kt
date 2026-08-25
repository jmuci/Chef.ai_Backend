package com.tenmilelabs.tools.themealdb

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class MealTypeClassifierTest {

    private fun classify(
        category: String? = null,
        tags: List<String> = emptyList(),
        title: String = "Untitled",
        ingredients: List<String> = emptyList(),
    ) = MealTypeClassifier.classify(category, tags, title, ingredients)

    // ── The bug this exists to fix (jmuci/ChefAI#182) ──────────────────────────

    @Test
    fun `a main-course category becomes Dinner`() {
        assertTrue("Dinner" in classify(category = "Beef", title = "Beef Wellington").tagNames)
        assertTrue("Dinner" in classify(category = "Seafood", title = "Fish Pie").tagNames)
    }

    @Test
    fun `a starter or soup becomes Lunch`() {
        assertTrue("Lunch" in classify(category = "Starter", title = "Ajo Blanco").tagNames)
        assertTrue("Lunch" in classify(tags = listOf("Soup"), title = "Ribollita").tagNames)
    }

    @Test
    fun `Lunch is reachable from the title alone when TheMealDB carries no usable tag`() {
        // 18 of 789 meals in the August import had no tags at all; the title is the only signal.
        val result = classify(category = "Miscellaneous", title = "Chicken Caesar Salad")
        assertTrue("Lunch" in result.tagNames)
    }

    @Test
    fun `Kid-Friendly is derived but vetoed by alcohol or heat`() {
        assertTrue("Kid-Friendly" in classify(tags = listOf("Cheesy"), title = "Mac and Cheese").tagNames)

        val boozy = classify(tags = listOf("Cheesy", "Alcoholic"), title = "Beer Cheese Fondue")
        assertFalse("Kid-Friendly" in boozy.tagNames, "alcohol must veto Kid-Friendly")

        val spicy = classify(tags = listOf("Chocolate", "Chilli"), title = "Mole Poblano")
        assertFalse("Kid-Friendly" in spicy.tagNames, "chilli must veto Kid-Friendly")
    }

    @Test
    fun `Healthy is vetoed by the tags that contradict it`() {
        assertTrue("Healthy" in classify(tags = listOf("Salad"), title = "Greek Salad").tagNames)
        assertFalse(
            "Healthy" in classify(tags = listOf("Salad", "Calorific"), title = "Loaded Salad").tagNames,
            "Calorific must veto Healthy"
        )
    }

    @Test
    fun `Air Fryer is reachable only from the title`() {
        assertTrue("Air Fryer" in classify(title = "Air Fryer Egg Rolls").tagNames)
        assertTrue("Air Fryer" in classify(title = "Air fryer patatas bravas").tagNames)
        assertFalse("Air Fryer" in classify(category = "Chicken", title = "Roast Chicken").tagNames)
    }

    @Test
    fun `a dish can hold several meal types at once`() {
        // A soup is legitimately lunch, dinner and comfort food — the classifier must not pick one.
        val result = classify(category = "Beef", tags = listOf("Soup", "Warming"), title = "Beef Stew")
        assertTrue(result.tagNames.containsAll(setOf("Lunch", "Dinner", "Comfort Food")))
    }

    @Test
    fun `normalization collapses TheMealDB's inconsistent casing and spacing`() {
        // The live vocabulary contains "SideDish", "MainMeal", "DinnerParty", "HangoverFood".
        assertTrue("Lunch" in classify(tags = listOf("SideDish")).tagNames)
        assertTrue("Dinner" in classify(tags = listOf("MainMeal")).tagNames)
        assertTrue("Dinner" in classify(tags = listOf("DinnerParty")).tagNames)
        assertTrue("Comfort Food" in classify(tags = listOf("HangoverFood")).tagNames)
    }

    @Test
    fun `an unrecognised category yields nothing rather than a default meal type`() {
        assertEquals(emptySet(), classify(category = "Zzzz", title = "Zzzz").tagNames)
    }

    // ── Labels: derived from ingredients, never from tag guesswork ─────────────

    @Test
    fun `High Protein comes from a real protein ingredient`() {
        val result = classify(category = "Beef", title = "Steak", ingredients = listOf("Beef", "Salt", "Butter"))
        assertTrue(MealTypeClassifier.HIGH_PROTEIN in result.labelNames)
    }

    @Test
    fun `stock does not count as a protein source`() {
        // "Chicken Stock" contains "chicken" but contributes no meaningful protein — without the
        // stock veto, most vegetable soups in the catalog would be labelled High Protein.
        val result = classify(
            category = "Vegetarian",
            title = "Carrot Soup",
            ingredients = listOf("Carrots", "Chicken Stock", "Onion")
        )
        assertFalse(MealTypeClassifier.HIGH_PROTEIN in result.labelNames)
    }

    @Test
    fun `Low Carb requires a protein base and no high-carb ingredient`() {
        val lowCarb = classify(title = "Omelette", ingredients = listOf("Eggs", "Butter", "Cheese"))
        assertTrue(MealTypeClassifier.LOW_CARB in lowCarb.labelNames)

        val withPasta = classify(title = "Carbonara", ingredients = listOf("Eggs", "Bacon", "Spaghetti"))
        assertFalse(MealTypeClassifier.LOW_CARB in withPasta.labelNames, "pasta disqualifies Low Carb")

        val saladOnly = classify(title = "Side Salad", ingredients = listOf("Lettuce", "Tomato"))
        assertFalse(
            MealTypeClassifier.LOW_CARB in saladOnly.labelNames,
            "a protein base is required, or every side salad qualifies"
        )
    }

    @Test
    fun `no label is inferred when the ingredient list is empty`() {
        // A missing ingredient list must never read as "contains no carbs".
        assertEquals(emptySet(), classify(category = "Beef", title = "Steak").labelNames)
    }

    @Test
    fun `allergen and ethical labels are never inferred`() {
        val result = classify(
            category = "Vegan",
            tags = listOf("Vegetarian"),
            title = "Chickpea Curry",
            ingredients = listOf("Chickpeas", "Coconut Milk", "Curry Powder")
        )
        val forbidden = setOf("Vegan", "Vegetarian", "Gluten-Free", "Nut-Free", "Dairy-Free", "Contains Seafood")
        assertTrue(
            result.labelNames.none { it in forbidden },
            "classifier must not infer allergen/ethical labels, got ${result.labelNames}"
        )
    }

    @Test
    fun `classification is deterministic`() {
        val args = Triple("Chicken", listOf("Curry", "Spicy"), "Chicken Tikka")
        val first = MealTypeClassifier.classify(args.first, args.second, args.third, listOf("Chicken", "Rice"))
        val second = MealTypeClassifier.classify(args.first, args.second, args.third, listOf("Chicken", "Rice"))
        assertEquals(first, second)
    }

    @Test
    fun `eggs alone do not make a cake High Protein`() {
        // Regression: counting eggs unconditionally labelled 614 of 879 public recipes High
        // Protein, apple pie and alfajores included, which makes the meal-plan dietary filter
        // useless. See jmuci/ChefAI#182.
        val applePie = classify(
            category = "Dessert",
            title = "Apple Pie",
            ingredients = listOf("Apples", "Flour", "Sugar", "Eggs", "Salt")
        )
        assertFalse(MealTypeClassifier.HIGH_PROTEIN in applePie.labelNames)
    }

    @Test
    fun `eggs still count in a dish carrying no carbs`() {
        val omelette = classify(title = "French Omelette", ingredients = listOf("Eggs", "Butter", "Chives"))
        assertTrue(MealTypeClassifier.HIGH_PROTEIN in omelette.labelNames)
    }

    @Test
    fun `a dessert is never given a nutritional label`() {
        // Even a flourless, sugar-free-looking ingredient list: Dessert vetoes both labels.
        val result = classify(
            category = "Dessert",
            title = "Chocolate Mousse",
            ingredients = listOf("Eggs", "Double Cream", "Dark Chocolate")
        )
        assertEquals(emptySet(), result.labelNames)
    }

    @Test
    fun `starchy staples outside the wheat-and-rice vocabulary still disqualify Low Carb`() {
        // Regression: "Cassava pizza" (casabe, sweetcorn) came back Low Carb because neither word
        // was in the carb list. A wrong dietary claim is worse than a missed browse result.
        val cassava = classify(
            title = "Cassava pizza",
            ingredients = listOf("Casabe", "Chorizo", "Mozzarella", "Sweetcorn", "Tomato Sauce")
        )
        assertFalse(MealTypeClassifier.LOW_CARB in cassava.labelNames)
        assertTrue(MealTypeClassifier.HIGH_PROTEIN in cassava.labelNames, "chorizo is still a protein")
    }

    @Test
    fun `legumes disqualify Low Carb without costing the protein claim`() {
        val lambAndBeans = classify(
            title = "Slow-cooked lamb shoulder and beans",
            ingredients = listOf("Lamb Shoulder", "Butter Beans", "Carrots", "Garlic")
        )
        assertTrue(MealTypeClassifier.HIGH_PROTEIN in lambAndBeans.labelNames)
        assertFalse(MealTypeClassifier.LOW_CARB in lambAndBeans.labelNames)
    }
}
