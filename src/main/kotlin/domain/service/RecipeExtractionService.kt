package com.tenmilelabs.domain.service

import com.tenmilelabs.domain.exception.InvalidUrlException
import com.tenmilelabs.domain.extraction.RecipeExtractor
import com.tenmilelabs.domain.model.ExtractedRecipe
import io.ktor.util.logging.*

class RecipeExtractionService(
    private val extractor: RecipeExtractor,
    private val log: Logger
) {
    suspend fun extractRecipe(url: String): ExtractedRecipe {
        validateUrl(url)
        log.info("Extracting recipe from: $url")
        val recipe = extractor.extract(url)
        log.info("Successfully extracted recipe '${recipe.title}' from $url")
        return recipe
    }

    private fun validateUrl(url: String) {
        if (url.isBlank()) throw InvalidUrlException("URL is required")
        val parsed = try {
            java.net.URI(url)
        } catch (e: Exception) {
            throw InvalidUrlException("Malformed URL: $url")
        }
        if (parsed.scheme !in listOf("http", "https")) {
            throw InvalidUrlException("URL must use http or https scheme")
        }
        if (parsed.host.isNullOrBlank()) {
            throw InvalidUrlException("URL must have a valid host")
        }
    }
}
