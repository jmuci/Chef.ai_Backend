package com.tenmilelabs.domain.service

/**
 * See docs/prompts/recipe-image-upload-backend-prompt.md §5.
 *
 * [allowScrapedImageUpload] and [serveScrapedBlobsToNonOwners] are kill switches, not tuning
 * knobs — they exist so that hosting and publicly serving third-party images can be retreated
 * from with a config change rather than a release, while the app has no users to justify the
 * DMCA takedown plumbing that would make the posture supported instead of merely accepted.
 */
data class ImageBlobConfig(
    val allowScrapedImageUpload: Boolean = true,
    val serveScrapedBlobsToNonOwners: Boolean = true,
    val maxUploadBytes: Long = 5L * 1024 * 1024,
    val perUserQuotaBytes: Long = 500L * 1024 * 1024,
    val unreferencedRetentionDays: Int = 30,
    val uploadRateLimitPerMinute: Int = 60
)
