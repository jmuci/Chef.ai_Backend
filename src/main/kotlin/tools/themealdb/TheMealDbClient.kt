package com.tenmilelabs.tools.themealdb

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.delay

/**
 * Thin client for TheMealDB's free, explicitly-reusable recipe API
 * (https://www.themealdb.com/api.php). Not scraping — this is a supported public API; the
 * shared test key ("1") covers every endpoint used here.
 *
 * Only [search.php?f=] is used: unlike `filter.php`, it returns *full* meal objects (ingredients,
 * measures, instructions) in one request, so a 26-letter scan (a-z) is enough to reach the
 * catalog without a per-meal follow-up `lookup.php` call. Requests are spaced out as a courtesy
 * even though the endpoint is free and rate-limit-free for the test key.
 */
class TheMealDbClient(
    private val httpClient: HttpClient = defaultHttpClient(),
    private val requestDelayMillis: Long = 1000,
    private val log: Logger = KtorSimpleLogger("TheMealDbClient")
) {
    /**
     * Fetches every meal reachable via a full a-z scan of [search.php?f=], deduplicated by
     * `idMeal`. Skips a letter on failure (logs and continues) rather than aborting the whole run.
     */
    suspend fun fetchAllMeals(): List<RawMeal> {
        val seen = LinkedHashMap<String, RawMeal>()
        for (letter in 'a'..'z') {
            val meals = runCatching { fetchByFirstLetter(letter) }
                .onFailure { log.warn("TheMealDB fetch failed for letter '$letter': ${it.message}") }
                .getOrDefault(emptyList())
            meals.forEach { meal -> seen.putIfAbsent(meal.idMeal, meal) }
            delay(requestDelayMillis)
        }
        return seen.values.toList()
    }

    private suspend fun fetchByFirstLetter(letter: Char): List<RawMeal> {
        val response: JsonElement = httpClient
            .get("https://www.themealdb.com/api/json/v1/1/search.php?f=$letter")
            .body()
        val meals = response.jsonObject["meals"] ?: return emptyList()
        if (meals !is kotlinx.serialization.json.JsonArray) return emptyList()
        return meals.jsonArray.mapNotNull { runCatching { parseMeal(it.jsonObject) }.getOrNull() }
    }

    private fun parseMeal(obj: Map<String, JsonElement>): RawMeal {
        fun str(key: String): String? = obj[key]?.jsonPrimitive?.contentOrNull()

        val ingredients = (1..20).mapNotNull { i ->
            val name = str("strIngredient$i")?.trim()
            if (name.isNullOrBlank()) return@mapNotNull null
            RawIngredient(name = name, measure = str("strMeasure$i")?.trim().orEmpty())
        }

        return RawMeal(
            idMeal = requireNotNull(str("idMeal")) { "meal missing idMeal" },
            name = requireNotNull(str("strMeal")) { "meal missing strMeal" },
            category = str("strCategory"),
            area = str("strArea"),
            instructions = str("strInstructions").orEmpty(),
            thumbnailUrl = str("strMealThumb").orEmpty(),
            tags = str("strTags")?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
            source = str("strSource"),
            ingredients = ingredients
        )
    }

    private fun JsonElement.contentOrNull(): String? =
        this.jsonPrimitive.content.takeIf { it != "null" }

    companion object {
        fun defaultHttpClient(): HttpClient = HttpClient(CIO) {
            expectSuccess = true
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 10_000
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
    }
}

/** Raw shape from TheMealDB, pre-mapping. Intentionally close to the wire format. */
data class RawMeal(
    val idMeal: String,
    val name: String,
    val category: String?,
    val area: String?,
    val instructions: String,
    val thumbnailUrl: String,
    val tags: List<String>,
    val source: String?,
    val ingredients: List<RawIngredient>
)

data class RawIngredient(val name: String, val measure: String)
