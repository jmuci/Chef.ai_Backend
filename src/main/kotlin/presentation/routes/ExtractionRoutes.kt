package com.tenmilelabs.presentation.routes

import com.tenmilelabs.application.dto.ErrorResponse
import com.tenmilelabs.application.dto.ExtractRecipeRequest
import com.tenmilelabs.application.dto.toResponse
import com.tenmilelabs.domain.exception.FetchFailedException
import com.tenmilelabs.domain.exception.InvalidUrlException
import com.tenmilelabs.domain.exception.NoRecipeDataException
import com.tenmilelabs.domain.service.RecipeExtractionService
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerializationException

fun Route.extractionRoutes(extractionService: RecipeExtractionService) {
    route("/api/recipes") {
        post("/extract") {
            try {
                val request = call.receive<ExtractRecipeRequest>()
                val recipe = extractionService.extractRecipe(request.url)
                call.respond(HttpStatusCode.OK, recipe.toResponse())
            } catch (ex: InvalidUrlException) {
                call.application.environment.log.warn("Invalid URL for extraction: ${ex.message}")
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(ex.message ?: "Invalid URL"))
            } catch (ex: FetchFailedException) {
                call.application.environment.log.warn("Failed to fetch URL: ${ex.url} - ${ex.message}")
                call.respond(HttpStatusCode.BadGateway, ErrorResponse(ex.message ?: "Failed to fetch URL"))
            } catch (ex: NoRecipeDataException) {
                call.application.environment.log.info("No recipe data found at: ${ex.url}")
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorResponse(ex.message ?: "No recipe data found")
                )
            } catch (ex: SerializationException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            } catch (ex: JsonConvertException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            } catch (ex: Exception) {
                call.application.environment.log.error("Recipe extraction failed unexpectedly", ex)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal error"))
            }
        }
    }
}
