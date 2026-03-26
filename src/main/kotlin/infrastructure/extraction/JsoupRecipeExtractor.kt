package com.tenmilelabs.infrastructure.extraction

import com.tenmilelabs.domain.exception.NoRecipeDataException
import com.tenmilelabs.domain.extraction.RecipeExtractor
import com.tenmilelabs.domain.model.ExtractedRecipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JsoupRecipeExtractor(
    private val fetcher: HtmlFetcher,
    private val jsonLdExtractor: JsonLdExtractor,
    private val microdataExtractor: MicrodataExtractor,
    private val metaTagExtractor: MetaTagExtractor
) : RecipeExtractor {

    override suspend fun extract(url: String): ExtractedRecipe {
        val document = withContext(Dispatchers.IO) { fetcher.fetch(url) }

        return jsonLdExtractor.extract(document, url)
            ?: microdataExtractor.extract(document, url)
            ?: metaTagExtractor.extract(document, url)
            ?: throw NoRecipeDataException(url, "No structured recipe data found at $url")
    }
}
