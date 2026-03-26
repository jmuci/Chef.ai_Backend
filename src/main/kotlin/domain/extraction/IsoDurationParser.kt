package com.tenmilelabs.domain.extraction

object IsoDurationParser {

    // Matches ISO 8601 durations: PT1H30M, P0DT0H30M, PT45M, PT1H, PT30S, etc.
    private val ISO_PATTERN = Regex(
        """P(?:(\d+)D)?T?(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Parses an ISO 8601 duration string (e.g. "PT1H30M") into total minutes.
     * Also handles plain numeric strings (e.g. "30" → 30 minutes).
     * Returns null if input is null, blank, or unparseable.
     */
    fun parseToMinutes(duration: String?): Int? {
        if (duration.isNullOrBlank()) return null

        val trimmed = duration.trim()

        // Plain number fallback — some sites emit just "30" meaning 30 minutes
        trimmed.toIntOrNull()?.let { return it }

        val match = ISO_PATTERN.matchEntire(trimmed) ?: return null

        val days = match.groupValues[1].toIntOrNull() ?: 0
        val hours = match.groupValues[2].toIntOrNull() ?: 0
        val minutes = match.groupValues[3].toIntOrNull() ?: 0
        val seconds = match.groupValues[4].toIntOrNull() ?: 0

        val total = days * 1440 + hours * 60 + minutes + if (seconds > 0) 1 else 0
        return if (total == 0 && seconds == 0) 0 else total
    }
}
