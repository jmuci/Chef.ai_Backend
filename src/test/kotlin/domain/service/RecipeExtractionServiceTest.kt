package domain.service

import com.tenmilelabs.domain.exception.FetchFailedException
import com.tenmilelabs.domain.exception.InvalidUrlException
import com.tenmilelabs.domain.exception.NoRecipeDataException
import com.tenmilelabs.domain.extraction.RecipeExtractor
import com.tenmilelabs.domain.model.ExtractedRecipe
import com.tenmilelabs.domain.service.RecipeExtractionService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals

class RecipeExtractionServiceTest {

    private val log = LoggerFactory.getLogger("test")

    private fun service(extractor: RecipeExtractor): RecipeExtractionService =
        RecipeExtractionService(extractor, log)

    private val sampleRecipe = ExtractedRecipe(
        title = "Test Recipe",
        description = null,
        servings = null,
        prepTimeMinutes = null,
        cookTimeMinutes = null,
        totalTimeMinutes = null,
        ingredients = emptyList(),
        steps = emptyList(),
        tags = emptyList(),
        yieldAmount = null,
        sourceUrl = "https://example.com/recipe"
    )

    @Test
    fun throwsOnBlankUrl() {
        val svc = service(FakeRecipeExtractor(sampleRecipe))
        assertThrows<InvalidUrlException> {
            runBlocking { svc.extractRecipe("") }
        }
    }

    @Test
    fun throwsOnMalformedUrl() {
        val svc = service(FakeRecipeExtractor(sampleRecipe))
        assertThrows<InvalidUrlException> {
            runBlocking { svc.extractRecipe("not a url") }
        }
    }

    @Test
    fun throwsOnFtpScheme() {
        val svc = service(FakeRecipeExtractor(sampleRecipe))
        assertThrows<InvalidUrlException> {
            runBlocking { svc.extractRecipe("ftp://example.com/recipe") }
        }
    }

    @Test
    fun throwsOnMissingHost() {
        val svc = service(FakeRecipeExtractor(sampleRecipe))
        assertThrows<InvalidUrlException> {
            runBlocking { svc.extractRecipe("https://") }
        }
    }

    @Test
    fun delegatesToExtractor() = runBlocking {
        val svc = service(FakeRecipeExtractor(sampleRecipe))
        val result = svc.extractRecipe("https://example.com/recipe")
        assertEquals("Test Recipe", result.title)
    }

    @Test
    fun propagatesFetchFailedException() {
        val extractor = FakeRecipeExtractor(exception = FetchFailedException("url", "timeout"))
        val svc = service(extractor)
        assertThrows<FetchFailedException> {
            runBlocking { svc.extractRecipe("https://example.com/recipe") }
        }
    }

    @Test
    fun propagatesNoRecipeDataException() {
        val extractor = FakeRecipeExtractor(exception = NoRecipeDataException("url", "no data"))
        val svc = service(extractor)
        assertThrows<NoRecipeDataException> {
            runBlocking { svc.extractRecipe("https://example.com/recipe") }
        }
    }

    private class FakeRecipeExtractor(
        private val result: ExtractedRecipe? = null,
        private val exception: Exception? = null
    ) : RecipeExtractor {
        override suspend fun extract(url: String): ExtractedRecipe {
            exception?.let { throw it }
            return result ?: throw NoRecipeDataException(url, "No data")
        }
    }
}
