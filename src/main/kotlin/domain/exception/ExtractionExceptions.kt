package com.tenmilelabs.domain.exception

sealed class ExtractionException(message: String) : Exception(message)

class InvalidUrlException(message: String) : ExtractionException(message)

class FetchFailedException(
    val url: String,
    message: String,
    cause: Throwable? = null
) : ExtractionException(message) {
    init {
        if (cause != null) initCause(cause)
    }
}

class NoRecipeDataException(
    val url: String,
    message: String
) : ExtractionException(message)
