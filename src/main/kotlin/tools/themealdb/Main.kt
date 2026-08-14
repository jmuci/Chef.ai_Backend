package com.tenmilelabs.tools.themealdb

import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import java.io.File

/**
 * One-off bulk import: fetches TheMealDB's catalog, maps it onto this repo's schema, and writes
 * `src/main/resources/sql/seed_themealdb.sql`. No DB connection — apply the output with psql the
 * same way as the other seed scripts. See docs/meal-plan-roadmap.md (Phase 1) for the full
 * rationale.
 *
 * Run: `./gradlew importTheMealDb`
 *
 * Requires outbound HTTPS to www.themealdb.com. Some sandboxed/restricted-egress environments
 * block that host by policy — if every letter fetch fails, that's very likely why; run this from
 * an environment with normal internet access instead.
 */
fun main() = runBlocking {
    val log = KtorSimpleLogger("importTheMealDb")
    val projectRoot = File(".")
    val seedSqlFile = File(projectRoot, "src/main/resources/sql/seed.sql")
    val outputFile = File(projectRoot, "src/main/resources/sql/seed_themealdb.sql")

    log.info("Loading existing catalog from ${seedSqlFile.path}")
    val catalog = if (seedSqlFile.exists()) SeedCatalogLoader.load(seedSqlFile) else Catalog.EMPTY
    log.info(
        "Existing catalog: ${catalog.ingredientIdsByNormalizedName.size} ingredients, " +
            "${catalog.tagIdsByNormalizedName.size} tags, ${catalog.labelIdsByNormalizedName.size} labels"
    )

    val client = TheMealDbClient()
    log.info("Fetching TheMealDB catalog (a-z scan, ~26 requests, spaced out)...")
    val meals = client.fetchAllMeals()
    log.info("Fetched ${meals.size} distinct meals")

    if (meals.isEmpty()) {
        log.error(
            "Fetched zero meals. If every request failed, this environment likely blocks outbound " +
                "HTTPS to www.themealdb.com — run this tool from an environment with normal internet " +
                "access. No output file was written."
        )
        return@runBlocking
    }

    val importedAtMillis = Clock.System.now().toEpochMilliseconds()
    val result = TheMealDbMapper.map(meals, catalog, importedAtMillis)
    log.info(
        "Mapped ${result.recipes.size} recipes; new catalog rows: " +
            "${result.newIngredients.size} ingredients, ${result.newTags.size} tags, ${result.newLabels.size} labels"
    )

    outputFile.writeText(SeedSqlWriter.write(result, importedAtMillis))
    log.info("Wrote ${outputFile.path} (${result.recipes.size} recipes)")
    log.info(
        "Apply with: psql -h localhost -U postgres -d chefai_db -f ${seedSqlFile.path} -f ${outputFile.path}"
    )
}
