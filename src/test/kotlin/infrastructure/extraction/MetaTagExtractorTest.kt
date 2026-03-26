package infrastructure.extraction

import com.tenmilelabs.infrastructure.extraction.MetaTagExtractor
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MetaTagExtractorTest {

    private val extractor = MetaTagExtractor()
    private val sourceUrl = "https://example.com/recipe"

    @Test
    fun extractsOpenGraphTags() {
        val html = """
        <html><head>
            <meta property="og:title" content="Amazing Pasta">
            <meta property="og:description" content="The best pasta recipe ever">
        </head><body></body></html>
        """.trimIndent()

        val result = extractor.extract(Jsoup.parse(html), sourceUrl)

        assertNotNull(result)
        assertEquals("Amazing Pasta", result.title)
        assertEquals("The best pasta recipe ever", result.description)
        assertTrue(result.ingredients.isEmpty())
        assertTrue(result.steps.isEmpty())
    }

    @Test
    fun extractsTwitterTags() {
        val html = """
        <html><head>
            <meta name="twitter:title" content="Twitter Recipe">
            <meta name="twitter:description" content="From twitter">
        </head><body></body></html>
        """.trimIndent()

        val result = extractor.extract(Jsoup.parse(html), sourceUrl)

        assertNotNull(result)
        assertEquals("Twitter Recipe", result.title)
        assertEquals("From twitter", result.description)
    }

    @Test
    fun returnsNullWithoutStructuredMetaTags() {
        // Even with <title> and standard meta description, if there's no og: or twitter: tags,
        // it's likely not a recipe page (too many false positives like google.com)
        val html = """
        <html><head>
            <title>My Page</title>
            <meta name="description" content="A description">
        </head><body></body></html>
        """.trimIndent()

        val result = extractor.extract(Jsoup.parse(html), sourceUrl)

        assertNull(result)
    }

    @Test
    fun returnsNullWhenNoTitleFound() {
        val html = "<html><head></head><body></body></html>"
        assertNull(extractor.extract(Jsoup.parse(html), sourceUrl))
    }

    @Test
    fun prefersOgOverTwitter() {
        val html = """
        <html><head>
            <meta property="og:title" content="OG Title">
            <meta name="twitter:title" content="Twitter Title">
        </head><body></body></html>
        """.trimIndent()

        val result = extractor.extract(Jsoup.parse(html), sourceUrl)
        assertNotNull(result)
        assertEquals("OG Title", result.title)
    }

    @Test
    fun returnsNullFieldsForMissingData() {
        val html = """
        <html><head>
            <meta property="og:title" content="Title Only">
        </head><body></body></html>
        """.trimIndent()

        val result = extractor.extract(Jsoup.parse(html), sourceUrl)
        assertNotNull(result)
        assertNull(result.description)
        assertNull(result.servings)
        assertNull(result.prepTimeMinutes)
    }
}
