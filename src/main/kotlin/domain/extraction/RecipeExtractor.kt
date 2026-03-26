package com.tenmilelabs.domain.extraction

import com.tenmilelabs.domain.model.ExtractedRecipe

interface RecipeExtractor {
    suspend fun extract(url: String): ExtractedRecipe
}
