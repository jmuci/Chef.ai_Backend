package com.tenmilelabs.support

/**
 * The literal search terms the Android client's Search-tab browse cards send to
 * `GET /api/v1/recipes/search`, mirroring `SearchCategory.query` in `jmuci/ChefAI`.
 *
 * This is checked in so the backend can assert its own seed vocabulary actually answers them.
 * Every one of these is an ordinary free-text search, so a card is only non-empty if some tag,
 * label, title or description matches — which is how "Lunch" came to return nothing at all
 * (jmuci/ChefAI#182). Keep in sync with the client enum; a term here with no backing vocabulary
 * is a card that renders "No recipes found".
 */
object BrowseCategories {

    /** Term → the curated tag or label display name expected to answer it. */
    val TERM_TO_VOCABULARY: Map<String, String> = mapOf(
        // "Search by Meal"
        "breakfast" to "Breakfast",
        "lunch" to "Lunch",
        "snack" to "Snack",
        "dinner" to "Dinner",
        "dessert" to "Dessert",
        "kid friendly" to "Kid-Friendly",
        // "Popular Categories"
        "low carb" to "Low Carb",
        "sandwich" to "Sandwich",
        "quick" to "Quick",
        "budget" to "Budget",
        "air fryer" to "Air Fryer",
        "vegetarian" to "Vegetarian",
        "protein" to "High Protein",
        "healthy" to "Healthy",
        "comfort food" to "Comfort Food",
    )

    /**
     * "cookies" is deliberately absent from [TERM_TO_VOCABULARY]: it is answered by recipe titles
     * rather than by a tag, and inventing a "Cookies" tag to satisfy a test would be the tail
     * wagging the dog.
     */
    const val TITLE_ANSWERED_TERM = "cookies"
}
