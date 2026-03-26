package infrastructure.extraction

import com.tenmilelabs.domain.exception.NoRecipeDataException
import com.tenmilelabs.domain.extraction.RecipeExtractor
import com.tenmilelabs.domain.model.ExtractedRecipe

class FakeRecipeExtractor : RecipeExtractor {
    var result: ExtractedRecipe? = null
    var exception: Exception? = null

    override suspend fun extract(url: String): ExtractedRecipe {
        exception?.let { throw it }
        return result ?: throw NoRecipeDataException(url, "No recipe data found at $url")
    }
}
