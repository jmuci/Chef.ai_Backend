package infrastructure.extraction

import com.tenmilelabs.infrastructure.extraction.MicrodataExtractor
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MicrodataExtractorTest {

    private val extractor = MicrodataExtractor()
    private val sourceUrl = "https://example.com/recipe"

    @Test
    fun extractsMicrodataRecipe() {
        val html = """
        <html><body>
        <div itemscope itemtype="https://schema.org/Recipe">
            <span itemprop="name">Pancakes</span>
            <span itemprop="description">Fluffy pancakes</span>
            <span itemprop="recipeYield">4 servings</span>
            <meta itemprop="prepTime" content="PT10M">
            <meta itemprop="cookTime" content="PT20M">
            <meta itemprop="totalTime" content="PT30M">
            <span itemprop="recipeIngredient">2 cups flour</span>
            <span itemprop="recipeIngredient">1 cup milk</span>
            <span itemprop="recipeIngredient">2 eggs</span>
            <div itemprop="recipeInstructions">
                <div itemprop="step">Mix ingredients</div>
                <div itemprop="step">Cook on griddle</div>
            </div>
            <span itemprop="recipeCategory">Breakfast</span>
        </div>
        </body></html>
        """.trimIndent()

        val result = extractor.extract(Jsoup.parse(html), sourceUrl)

        assertNotNull(result)
        assertEquals("Pancakes", result.title)
        assertEquals("Fluffy pancakes", result.description)
        assertEquals("4 servings", result.servings)
        assertEquals(10, result.prepTimeMinutes)
        assertEquals(20, result.cookTimeMinutes)
        assertEquals(30, result.totalTimeMinutes)
        assertEquals(3, result.ingredients.size)
        assertEquals("2 cups flour", result.ingredients[0].raw)
        assertEquals(2, result.steps.size)
        assertTrue(result.tags.contains("Breakfast"))
    }

    @Test
    fun extractsTimeFromDatetimeAttribute() {
        val html = """
        <html><body>
        <div itemscope itemtype="https://schema.org/Recipe">
            <span itemprop="name">Quick Dish</span>
            <time itemprop="prepTime" datetime="PT15M">15 min</time>
        </div>
        </body></html>
        """.trimIndent()

        val result = extractor.extract(Jsoup.parse(html), sourceUrl)

        assertNotNull(result)
        assertEquals(15, result.prepTimeMinutes)
    }

    @Test
    fun returnsNullForNoMicrodata() {
        val html = "<html><body><p>No recipe here</p></body></html>"
        assertNull(extractor.extract(Jsoup.parse(html), sourceUrl))
    }

    @Test
    fun returnsNullWhenNoNameFound() {
        val html = """
        <html><body>
        <div itemscope itemtype="https://schema.org/Recipe">
            <span itemprop="description">A recipe without a name</span>
        </div>
        </body></html>
        """.trimIndent()

        assertNull(extractor.extract(Jsoup.parse(html), sourceUrl))
    }

    @Test
    fun handlesLegacyIngredientsItemprop() {
        val html = """
        <html><body>
        <div itemscope itemtype="https://schema.org/Recipe">
            <span itemprop="name">Old Recipe</span>
            <span itemprop="ingredients">1 cup sugar</span>
            <span itemprop="ingredients">2 cups flour</span>
        </div>
        </body></html>
        """.trimIndent()

        val result = extractor.extract(Jsoup.parse(html), sourceUrl)

        assertNotNull(result)
        assertEquals(2, result.ingredients.size)
    }

    @Test
    fun handlesInstructionsAsDirectText() {
        val html = """
        <html><body>
        <div itemscope itemtype="https://schema.org/Recipe">
            <span itemprop="name">Simple Recipe</span>
            <p itemprop="recipeInstructions">Mix everything and bake.</p>
        </div>
        </body></html>
        """.trimIndent()

        val result = extractor.extract(Jsoup.parse(html), sourceUrl)

        assertNotNull(result)
        assertEquals(1, result.steps.size)
        assertEquals("Mix everything and bake.", result.steps[0].text)
    }
}
