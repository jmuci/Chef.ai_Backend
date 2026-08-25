package com.tenmilelabs.tools.themealdb

/**
 * Derives browse-facing meal-type/style tags — and two nutritional labels — from TheMealDB's own
 * vocabulary, which has no notion of when a dish is eaten.
 *
 * This exists because the Search tab's browse cards (`SearchCategory` in the Android client) send
 * a literal English word into the ordinary recipe search, which matches titles, descriptions, tags
 * and labels. TheMealDB's vocabulary is cuisine- and ingredient-shaped (Beef, Chinese, Dessert,
 * Starter, DinnerParty, HangoverFood), so words like "lunch" and "kid friendly" appear nowhere a
 * search can reach them and those cards come back empty. See issue jmuci/ChefAI#182.
 *
 * Like [TheMealDbMapper.estimateTimingsAndServings], everything here is **synthesized editorial
 * metadata, not sourced fact** — a deterministic function of a recipe's category, tags, title and
 * ingredient list. A dish legitimately belongs to more than one meal type (a soup is lunch *and*
 * dinner *and* comfort food), so [classify] returns sets rather than picking a winner.
 *
 * Tags versus labels is a deliberate split, not a stylistic one:
 * - **Tags** are search/display only. Nothing filters on them, so a loose assignment costs
 *   nothing worse than a slightly generous browse result.
 * - **Labels** are the dietary vocabulary that `PostgresSyncRepository.findCandidateRecipeIds`
 *   filters meal-plan generation by. Only [HIGH_PROTEIN] and [LOW_CARB] are assigned here, both
 *   from the recipe's actual ingredient list rather than from tag guesswork, and no label that
 *   encodes an allergen or an ethical commitment (Gluten-Free, Vegan, Nut-Free, ...) is ever
 *   inferred — those must stay sourced, since a wrong one is a health claim, not a bad
 *   recommendation.
 */
object MealTypeClassifier {

    const val HIGH_PROTEIN = "High Protein"
    const val LOW_CARB = "Low Carb"

    /** Tag and label display names, exactly as they must appear in `tags`/`labels`. */
    data class Classification(
        val tagNames: Set<String>,
        val labelNames: Set<String>,
    )

    /**
     * Output tag → the normalized TheMealDB category/tag terms that imply it. Terms are matched
     * against [normalize]d values, so "Side Dish", "SideDish" and "side dish" are one term.
     */
    private val TAG_SOURCES: Map<String, Set<String>> = mapOf(
        "Dinner" to setOf(
            "beef", "chicken", "pork", "lamb", "goat", "seafood", "pasta", "miscellaneous",
            "mainmeal", "stew", "curry", "casserole", "dinnerparty", "paella", "kebab",
            "chilli", "sausages", "datenight",
        ),
        "Lunch" to setOf(
            "starter", "side", "sidedish", "soup", "salad", "sandwich", "light", "onthego",
            "streetfood", "pasta", "egg", "bun", "pie",
        ),
        "Breakfast" to setOf("breakfast", "brunch", "pancake", "egg"),
        "Snack" to setOf("snack", "onthego", "streetfood", "nutty", "treat", "bun", "fruity"),
        "Dessert" to setOf(
            "dessert", "desert", "cake", "pudding", "tart", "treat", "chocolate", "caramel",
            "baking", "sweet",
        ),
        "Kid-Friendly" to setOf(
            "cheesy", "cheasy", "chocolate", "pancake", "bun", "pasta", "cake", "sweet",
            "fruity", "mild",
        ),
        "Comfort Food" to setOf(
            "warming", "warm", "casserole", "stew", "calorific", "heavy", "greasy",
            "hangoverfood", "pie", "soup", "cheesy", "cheasy", "pudding",
        ),
        "Budget" to setOf("cheap", "pulse", "beans", "egg", "miscellaneous"),
        "Quick" to setOf("onthego", "streetfood", "snack", "salad", "sandwich"),
        "Healthy" to setOf(
            "light", "lowcalorie", "salad", "vegetables", "fresh", "vegan", "vegetarian",
            "fish", "pulse", "beans",
        ),
        "Sandwich" to setOf("sandwich"),
    )

    /**
     * Output tag → lowercase substrings that imply it when found in the title. TheMealDB tags are
     * sparse (18 of 789 meals in the August import carried none at all), so the title is often the
     * only signal — it is what makes "Air Fryer" reachable at all.
     */
    private val TITLE_HINTS: Map<String, Set<String>> = mapOf(
        "Lunch" to setOf("salad", "soup", "sandwich", "wrap", "burger", "toast"),
        "Breakfast" to setOf("pancake", "omelette", "omelet", "porridge", "granola", "waffle"),
        "Dessert" to setOf("cookie", "cake", "pie", "tart", "pudding", "brownie", "ice cream"),
        "Kid-Friendly" to setOf("pancake", "cookie", "pizza", "mac and cheese", "nugget", "waffle"),
        "Comfort Food" to setOf("pie", "stew", "roast", "casserole", "mac and cheese", "lasagne", "lasagna"),
        "Sandwich" to setOf("sandwich", "wrap", "burger", "panini", "burrito", "taco"),
        "Air Fryer" to setOf("air fryer", "air-fryer"),
    )

    /**
     * Output tag → normalized source terms that veto it even when something else matched. A
     * chilli-heavy or alcoholic dish is not Kid-Friendly however cheesy it also is.
     */
    private val TAG_EXCLUSIONS: Map<String, Set<String>> = mapOf(
        "Kid-Friendly" to setOf("alcoholic", "spicy", "chilli", "strongflavor", "sour", "shellfish", "hot"),
        "Healthy" to setOf("calorific", "unhealthy", "greasy", "heavy", "hangoverfood", "alcoholic", "highfat"),
    )

    /**
     * Ingredient-name fragments marking a protein the dish is *built around*. Matched as
     * substrings of the normalized name so the catalog's variants ("Chicken"/"Chicken Breast")
     * collapse — see [STOCK_FRAGMENTS] for why stock forms are then excluded again.
     */
    private val PRIMARY_PROTEIN_FRAGMENTS = setOf(
        "beef", "steak", "mince", "chicken", "pork", "bacon", "sausage", "ham", "lamb", "goat",
        "turkey", "duck", "veal", "salmon", "tuna", "cod", "haddock", "mackerel", "sardine",
        "anchov", "prawn", "shrimp", "crab", "lobster", "mussel", "squid", "fish",
        "chorizo", "salami", "pepperoni", "prosciutto", "pancetta", "meat", "liver",
        "venison", "rabbit", "quail", "tofu", "lentil", "chickpea", "paneer",
    )

    /**
     * Proteins that are just as often a binder or a garnish. Eggs appear in most of the catalog's
     * baking, so counting them unconditionally labelled 70% of all recipes High Protein — apple
     * pie included. These only count in a dish that carries no high-carb ingredient, which is
     * what separates an omelette from a cake.
     */
    private val SECONDARY_PROTEIN_FRAGMENTS = setOf("egg", "yogurt", "yoghurt", "bean")

    /**
     * Stock/sauce forms that contain a protein word but contribute no meaningful protein. Checked
     * before [PROTEIN_FRAGMENTS] so "Chicken Stock" doesn't make a vegetable soup High Protein.
     */
    private val STOCK_FRAGMENTS = setOf("stock", "broth", "bouillon", "sauce", "fat", "dripping", "skin")

    /**
     * Ingredient-name fragments that disqualify a recipe from [LOW_CARB]. Deliberately broad —
     * this is a claim about absence, so a false negative (a low-carb dish not labelled) costs a
     * browse result, while a false positive is a wrong dietary claim someone may plan meals on.
     * `bean` appears here as well as in [SECONDARY_PROTEIN_FRAGMENTS]: legumes carry enough carb
     * to disqualify the claim while still being a protein source.
     */
    private val CARB_FRAGMENTS = setOf(
        "flour", "sugar", "rice", "pasta", "spaghetti", "macaroni", "noodle", "lasagne", "lasagna",
        "bread", "breadcrumb", "bun", "potato", "oats", "oat", "honey", "syrup", "cornstarch",
        "cornflour", "tortilla", "couscous", "polenta", "pastry", "biscuit", "cracker", "chocolate",
        "banana", "raisin", "date", "jam", "marmalade", "cereal", "quinoa", "barley", "semolina",
        "cassava", "casabe", "corn", "plantain", "yam", "breadfruit", "tapioca", "gnocchi",
        "dumpling", "wonton", "filo", "phyllo", "dough", "crouton", "molasses", "treacle",
        "bean", "pea", "chestnut", "crisp", "chip", "wrapper", "pastri",
    )

    /**
     * Classifies one meal. [category] and [tags] come straight off TheMealDB's payload,
     * [ingredientNames] is the recipe's resolved ingredient display names (used only for the two
     * labels). Returns empty sets when nothing matches — the caller adds nothing in that case
     * rather than falling back to a default meal type.
     */
    fun classify(
        category: String?,
        tags: List<String>,
        title: String,
        ingredientNames: List<String>,
    ): Classification {
        val sourceTerms = buildSet {
            category?.takeIf { it.isNotBlank() }?.let { add(normalize(it)) }
            tags.filter { it.isNotBlank() }.forEach { add(normalize(it)) }
        }
        val lowerTitle = title.lowercase()

        val tagNames = buildSet {
            for ((tagName, sources) in TAG_SOURCES) {
                if (sourceTerms.any { it in sources }) add(tagName)
            }
            for ((tagName, hints) in TITLE_HINTS) {
                if (hints.any { it in lowerTitle }) add(tagName)
            }
        }.filterNot { tagName ->
            TAG_EXCLUSIONS[tagName]?.let { vetoes -> sourceTerms.any { it in vetoes } } ?: false
        }.toSet()

        return Classification(
            tagNames = tagNames,
            labelNames = classifyLabels(ingredientNames, isDessert = "Dessert" in tagNames)
        )
    }

    private fun classifyLabels(ingredientNames: List<String>, isDessert: Boolean): Set<String> {
        // An empty ingredient list must never read as "contains no carbs", and a dessert is not a
        // nutritional recommendation however its ingredient list reads.
        if (ingredientNames.isEmpty() || isDessert) return emptySet()

        val normalized = ingredientNames.map { normalize(it) }
        fun containsAny(fragments: Set<String>): Boolean =
            normalized.any { name -> STOCK_FRAGMENTS.none { it in name } && fragments.any { it in name } }

        val hasCarb = normalized.any { name -> CARB_FRAGMENTS.any { it in name } }
        val hasProtein = containsAny(PRIMARY_PROTEIN_FRAGMENTS) ||
            (containsAny(SECONDARY_PROTEIN_FRAGMENTS) && !hasCarb)

        return buildSet {
            if (hasProtein) add(HIGH_PROTEIN)
            // Low Carb is a claim about what a dish *lacks*, so it needs a complete ingredient
            // list to be safe — and a protein base, or every plain side salad and spice mix in
            // the catalog would qualify.
            if (hasProtein && !hasCarb) add(LOW_CARB)
        }
    }

    /** Lowercases and strips everything that isn't a letter or digit: "Side Dish" → "sidedish". */
    private fun normalize(raw: String): String =
        raw.lowercase().filter { it.isLetterOrDigit() }
}
