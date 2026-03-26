package infrastructure.extraction

import com.tenmilelabs.infrastructure.extraction.JsonLdExtractor
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonLdExtractorTest {

    private val extractor = JsonLdExtractor()
    private val sourceUrl = "https://example.com/recipe"

    private fun doc(scriptContent: String): org.jsoup.nodes.Document {
        return Jsoup.parse(
            """<html><head><script type="application/ld+json">$scriptContent</script></head><body></body></html>"""
        )
    }

    @Test
    fun extractsSingleRecipeObject() {
        val json = """
        {
            "@context": "https://schema.org",
            "@type": "Recipe",
            "name": "Chocolate Cake",
            "description": "A rich chocolate cake",
            "prepTime": "PT30M",
            "cookTime": "PT1H",
            "totalTime": "PT1H30M",
            "recipeYield": "8 servings",
            "recipeIngredient": ["2 cups flour", "1 cup sugar", "3 eggs"],
            "recipeInstructions": ["Mix dry ingredients", "Add wet ingredients", "Bake at 350F"]
        }
        """.trimIndent()

        val result = extractor.extract(doc(json), sourceUrl)

        assertNotNull(result)
        assertEquals("Chocolate Cake", result.title)
        assertEquals("A rich chocolate cake", result.description)
        assertEquals(30, result.prepTimeMinutes)
        assertEquals(60, result.cookTimeMinutes)
        assertEquals(90, result.totalTimeMinutes)
        assertEquals("8 servings", result.servings)
        assertEquals(3, result.ingredients.size)
        assertEquals(3, result.steps.size)
        assertEquals(1, result.steps[0].order)
        assertEquals("Mix dry ingredients", result.steps[0].text)
    }

    @Test
    fun extractsFromGraphArray() {
        val json = """
        {
            "@context": "https://schema.org",
            "@graph": [
                { "@type": "WebPage", "name": "My Site" },
                {
                    "@type": "Recipe",
                    "name": "Pasta",
                    "recipeIngredient": ["200g pasta", "100g sauce"]
                }
            ]
        }
        """.trimIndent()

        val result = extractor.extract(doc(json), sourceUrl)

        assertNotNull(result)
        assertEquals("Pasta", result.title)
        assertEquals(2, result.ingredients.size)
    }

    @Test
    fun handlesHowToStepInstructions() {
        val json = """
        {
            "@type": "Recipe",
            "name": "Soup",
            "recipeInstructions": [
                { "@type": "HowToStep", "text": "Chop vegetables" },
                { "@type": "HowToStep", "text": "Boil water" },
                { "@type": "HowToStep", "text": "Simmer for 20 minutes" }
            ]
        }
        """.trimIndent()

        val result = extractor.extract(doc(json), sourceUrl)

        assertNotNull(result)
        assertEquals(3, result.steps.size)
        assertEquals("Chop vegetables", result.steps[0].text)
        assertEquals(2, result.steps[1].order)
    }

    @Test
    fun handlesHowToSectionInstructions() {
        val json = """
        {
            "@type": "Recipe",
            "name": "Complex Dish",
            "recipeInstructions": [
                {
                    "@type": "HowToSection",
                    "name": "Prep",
                    "itemListElement": [
                        { "@type": "HowToStep", "text": "Dice onions" },
                        { "@type": "HowToStep", "text": "Mince garlic" }
                    ]
                },
                {
                    "@type": "HowToSection",
                    "name": "Cook",
                    "itemListElement": [
                        { "@type": "HowToStep", "text": "Saute onions" }
                    ]
                }
            ]
        }
        """.trimIndent()

        val result = extractor.extract(doc(json), sourceUrl)

        assertNotNull(result)
        assertEquals(3, result.steps.size)
        assertEquals("Dice onions", result.steps[0].text)
        assertEquals("Saute onions", result.steps[2].text)
        assertEquals(3, result.steps[2].order)
    }

    @Test
    fun handlesMissingOptionalFields() {
        val json = """
        {
            "@type": "Recipe",
            "name": "Simple Recipe"
        }
        """.trimIndent()

        val result = extractor.extract(doc(json), sourceUrl)

        assertNotNull(result)
        assertEquals("Simple Recipe", result.title)
        assertNull(result.description)
        assertNull(result.prepTimeMinutes)
        assertTrue(result.ingredients.isEmpty())
        assertTrue(result.steps.isEmpty())
    }

    @Test
    fun returnsNullForNoJsonLd() {
        val document = Jsoup.parse("<html><body><p>No recipe here</p></body></html>")
        assertNull(extractor.extract(document, sourceUrl))
    }

    @Test
    fun returnsNullForNonRecipeJsonLd() {
        val json = """
        {
            "@type": "Article",
            "name": "Not a recipe"
        }
        """.trimIndent()

        assertNull(extractor.extract(doc(json), sourceUrl))
    }

    @Test
    fun parsesKeywordsAsTags() {
        val json = """
        {
            "@type": "Recipe",
            "name": "Tagged Recipe",
            "keywords": "dinner, easy, healthy",
            "recipeCategory": "Main Course",
            "recipeCuisine": "Italian"
        }
        """.trimIndent()

        val result = extractor.extract(doc(json), sourceUrl)

        assertNotNull(result)
        assertTrue(result.tags.contains("dinner"))
        assertTrue(result.tags.contains("easy"))
        assertTrue(result.tags.contains("Main Course"))
        assertTrue(result.tags.contains("Italian"))
    }

    @Test
    fun handlesArrayType() {
        val json = """
        {
            "@type": ["Recipe"],
            "name": "Array Type Recipe"
        }
        """.trimIndent()

        val result = extractor.extract(doc(json), sourceUrl)
        assertNotNull(result)
        assertEquals("Array Type Recipe", result.title)
    }

    @Test
    fun handlesHtmlInDescription() {
        val json = """
        {
            "@type": "Recipe",
            "name": "HTML Desc",
            "description": "<p>A <strong>great</strong> recipe</p>"
        }
        """.trimIndent()

        val result = extractor.extract(doc(json), sourceUrl)
        assertNotNull(result)
        assertEquals("A great recipe", result.description)
    }

    @Test
    fun handlesInvalidJsonGracefully() {
        val document = Jsoup.parse(
            """<html><head><script type="application/ld+json">{ invalid json }</script></head></html>"""
        )
        assertNull(extractor.extract(document, sourceUrl))
    }

    @Test
    fun parsesRecipeYieldFromArray() {
        val json = """
        {
            "@type": "Recipe",
            "name": "Yield Array",
            "recipeYield": ["4 servings", "4"]
        }
        """.trimIndent()

        val result = extractor.extract(doc(json), sourceUrl)
        assertNotNull(result)
        assertEquals("4 servings", result.servings)
    }
}
