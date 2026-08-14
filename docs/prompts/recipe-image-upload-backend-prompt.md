# Backend Prompt: Recipe Image Blobs — Upload, Serving & Reclamation

**Generated**: August 2026
**Related issues**: #94, #132 (Android repo)
**Android counterpart**: `docs/prompts/recipe-image-sync-stage2-plan.md` in the ChefAI Android repo
**Background, if useful**: ADR-006 (sync protocol), ADR-011 (cross-device images)

This document is self-contained — it can be handed to a session in `ktor-chefai` as-is. All
issue references point at the Android repo, not this one.

## Before you start: two things to verify against the real schema

This prompt was written from the Android client's side of the contract, so its assumptions
about existing backend tables are inferences, not facts. Check both and adapt the SQL below:

- **The owner column on `recipes`.** Written here as `recipes.user_id`. The Android entity calls
  the field `creatorId`, so the backend column may well be `creator_id`. Whichever it is, it is
  the column every ownership check in §3 and §6 keys on.
- **How `image_url` is stored for a user-picked photo.** §3a derives provenance from
  `recipes.image_url = ''` — an empty string, not `NULL`. If the column is nullable and the
  client's blank string arrives as `NULL`, that check must become
  `coalesce(image_url, '') = ''`, or every user photo is misclassified as `SCRAPED` and refused
  whenever the kill switch is on.

> Verified against this repo: the owner column is `recipes.creator_id`, and `image_url` is a
> `TEXT NOT NULL` column (never `NULL`), so `recipes.image_url = ''` is used directly — no
> `coalesce` needed.

## Context

The Android client stores every recipe hero image as a file on the device. Two sources feed it:

| Source | `recipes.image_url` | Can another device re-derive it? |
|---|---|---|
| Scraped from the recipe's source page at import | the third-party CDN URL | Yes, but only by driving an off-screen WebView — several CDNs (Akamai/Food Network, Cloudflare/Serious Eats) 403 every plain HTTP client, including the app's image loader |
| A photo the user picked from their gallery | empty string | No. That device holds the only copy in existence. |

A blank `image_url` is therefore a meaningful, load-bearing signal on the server too: it means
the image is the user's own and cannot be recovered from anywhere else.

The backend needs to accept those bytes, serve them back to the same user's other devices, and
reclaim them when they stop being referenced. Stack is Kotlin + Ktor + PostgreSQL + Exposed DSL +
JWT — same patterns as the existing recipe/bookmark sync endpoints.

The client never sends image bytes through `/sync/push`. That payload is JSON, batched fifty
recipes at a time. Images get their own endpoint, and the recipe row only ever carries a pointer.

## 1. Database Schema

```sql
CREATE TABLE image_blobs (
    id                  UUID    PRIMARY KEY,
    user_id             UUID    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content_hash        TEXT    NOT NULL,          -- lowercase sha256 hex of the stored bytes
    provenance          TEXT    NOT NULL,          -- USER | SCRAPED
    mime_type           TEXT    NOT NULL,          -- image/jpeg | image/png | image/webp
    byte_size           BIGINT  NOT NULL,
    storage_key         TEXT    NOT NULL,          -- opaque to callers; see §2
    created_at          BIGINT  NOT NULL,
    unreferenced_since  BIGINT  NULL,              -- set when no live recipe points here; see §6
    UNIQUE (user_id, content_hash)
);
CREATE INDEX idx_image_blobs_user_id            ON image_blobs(user_id);
CREATE INDEX idx_image_blobs_unreferenced_since ON image_blobs(unreferenced_since)
    WHERE unreferenced_since IS NOT NULL;
ALTER TABLE recipes ADD COLUMN image_blob_id TEXT NULL;   -- the content hash, not a FK
```

Three schema choices that are deliberate, not accidental:

- **`UNIQUE (user_id, content_hash)`, not `UNIQUE (content_hash)`.** Blobs are not deduplicated
  across users, so deletion, quota accounting, and any future takedown stay per-user and
  refcount-free. It also removes an existence oracle: without it, user B could probe whether
  user A has a given image by uploading a candidate and observing a dedup hit. Cross-user dedup
  is the obvious storage optimisation later — see §8 — but it is not worth its refcounting at
  this scale.
- **`provenance` is recorded but nothing reads it.** Serving (§3b) turns on recipe visibility
  alone. The column exists so that an owner-only serving rule can be re-imposed by config, and so
  a takedown can be executed by source, without a backfill that would be impossible to
  reconstruct after the fact. Derive it server-side; never accept it from the client.
- **`recipes.image_blob_id` stores the hash, not a surrogate FK.** The client treats it as an
  opaque change-token — "the image behind this recipe is different from the one I have" — and
  comparing hashes is exactly that test. A join to resolve a UUID would buy nothing.

## 2. Blob storage abstraction

Do not reach for an S3 SDK in this pass. Define the seam and implement the boring side of it:

```kotlin
interface BlobStore {
    suspend fun put(key: String, bytes: ByteArray, mimeType: String)
    suspend fun openRead(key: String): InputStream?
    suspend fun delete(key: String)
}
class LocalDiskBlobStore(private val root: Path) : BlobStore { /* … */ }
```

`storage_key` is generated as `"${userId}/${contentHash}"` and is opaque outside this interface.

Adding `aws-sdk-kotlin` / an R2 client is a dependency decision for whoever owns the deploy — flag
it and get sign-off rather than adding it here. The whole point of the seam is that swapping
`LocalDiskBlobStore` for an object-store implementation later touches one class, no endpoint, and
no client code.

## 3. Endpoints

### 3a. `PUT /recipes/{recipeId}/image` — upload

```
Authorization: Bearer <jwt>
Content-Type: image/jpeg            (or image/png, image/webp)
X-Content-SHA256: <64 lowercase hex chars>
Body: raw image bytes, ≤ 5 MiB      (NOT multipart — exactly one file, no form fields)
```

Validation, in this order. Every rejection must be cheap and must happen before the bytes are
written anywhere:

| # | Check | Failure |
|---|---|---|
| 1 | Valid JWT | 401 |
| 2 | Recipe exists and `recipes.user_id = caller` | 404 — same response for "doesn't exist" and "not yours", so the endpoint is not a recipe-existence oracle |
| 3 | `Content-Type` starts with `image/` and is one of jpeg/png/webp | 415 |
| 4 | Body ≤ 5 MiB, enforced while streaming | 413 — abort the read; never buffer an unbounded body to find out how big it was |
| 5 | `sha256(body)` equals `X-Content-SHA256` | 422 |
| 6 | Magic bytes match the declared type (`FF D8 FF` jpeg, `89 50 4E 47` png, `RIFF….WEBP` webp) | 415 — do not trust `Content-Type`; without this the endpoint is an authenticated arbitrary-file host |
| 7 | `provenance == SCRAPED` and `config.allowScrapedImageUpload == false` | 403 `{"error":"SCRAPED_UPLOAD_DISABLED"}` |
| 8 | User's total `byte_size` + this upload ≤ quota | 403 `{"error":"QUOTA_EXCEEDED"}` |

Provenance is derived server-side, never taken from the client:

```kotlin
val provenance = if (recipe.imageUrl.isBlank()) Provenance.USER else Provenance.SCRAPED
```

Success path:

- If `(user_id, hash)` already exists and `recipes.image_blob_id == hash` → return 200 with the
  existing values and do not touch `updated_at`. This matters: bumping `updated_at` on a no-op
  re-upload makes the recipe appear in every subsequent pull delta, and a client retrying a
  misclassified error would churn forever.
- Otherwise `BlobStore.put`, insert the `image_blobs` row (or reuse an existing one for the same
  `(user_id, hash)`), set `recipes.image_blob_id = hash`, set `recipes.updated_at = now()`.
- If the recipe previously pointed at a different blob, run the dereference check from §6 on the
  old one.

Response `200`:

```json
{"imageBlobId": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
 "updatedAt": 1755100000000}
```

Do all of this in one transaction, with the `BlobStore.put` before the commit so a crash leaves
an orphaned object (reclaimable by the §6 sweep) rather than a row pointing at nothing.

### 3b. `GET /recipes/{recipeId}/image` — serve

```
Authorization: Bearer <jwt>
If-None-Match: "<hash>"          (optional)
```

Visibility follows the recipe, not the image's provenance:

| | Caller is owner | Caller is another user |
|---|---|---|
| PRIVATE recipe | serve | 404 |
| PUBLIC recipe | serve | serve |

In other words: if the caller is allowed to see the recipe, they are allowed to see its image,
whether the image was scraped or photographed by the owner. Every "not visible" case returns
404, not 403, for the same non-oracle reason as §3a check 2.

Gate the non-owner branch on `config.serveScrapedBlobsToNonOwners` (§5) when
`provenance = SCRAPED`. It ships `true`; the flag exists so the owner-only rule can be re-imposed
without a deploy of new code, not because it is expected to be flipped soon. See §8.

Responses:

```
200  Content-Type: <mime_type>
     Content-Length: <byte_size>
     ETag: "<content_hash>"
     Cache-Control: private, max-age=31536000, immutable
     <bytes, streamed — do not load into memory>
304  when If-None-Match matches (blobs are immutable, so this is always safe)
404  no blob, recipe unknown, or not visible to the caller
```

### 3c. `DELETE /recipes/{recipeId}/image` — clear the pointer

Owner only. Sets `recipes.image_blob_id = NULL`, bumps `updated_at`, runs the §6 dereference
check. Returns 204. Needed because the client's editor lets a user replace a photo with a source
URL.

## 4. Sync protocol extension

Extend the existing `SyncRecipeDto` — do not add a new sync endpoint.

```diff
  data class SyncRecipeDto(
      val uuid: String,
      …
      val imageUrl: String,
      val imageUrlThumbnail: String,
+     val imageBlobId: String? = null,
      …
  )
```

**Push**: ignore whatever the client sends in `imageBlobId`. Persist the server's own value and
echo it back. The pointer is set exclusively by §3a and cleared exclusively by §3c.

This is not defensive pedantry — it is what keeps blobs out of last-writer-wins. A client cannot
point its recipe at a blob it never uploaded, and because blobs are immutable and
content-addressed, two devices can never disagree about a blob's contents. Which blob a recipe
points at is settled by the same `updated_at` LWW that already governs the row, with no new
conflict class.

**Pull**: include `imageBlobId` in every recipe, including soft-deleted ones.

Nullable with a default, so an older client that doesn't send or understand the field keeps
working.

## 5. Config

```kotlin
data class ImageBlobConfig(
    val allowScrapedImageUpload: Boolean = true,        // false → 403 SCRAPED_UPLOAD_DISABLED on §3a
    val serveScrapedBlobsToNonOwners: Boolean = true,   // false → §3b serves scraped blobs to the owner only
    val maxUploadBytes: Long = 5 * 1024 * 1024,
    val perUserQuotaBytes: Long = 500L * 1024 * 1024,
    val unreferencedRetentionDays: Int = 30,
)
```

The first two are kill switches, not tuning knobs. Hosting and publicly serving third-party
images is a deliberate, revisitable decision made while the app has no users (see §8); these
flags are what make retreating from it a config change instead of a release.
`allowScrapedImageUpload = false` stops accepting new scraped blobs — the Android client treats
403 `SCRAPED_UPLOAD_DISABLED` as permanent and stops retrying that recipe.
`serveScrapedBlobsToNonOwners = false` narrows serving to the uploading user, with existing blobs
untouched and no data migration.

Add a per-user upload rate limit (suggested: 60/minute) on §3a.

## 6. Reclamation

A blob becomes unreferenced when no live recipe points at it:

```sql
SELECT NOT EXISTS (
    SELECT 1 FROM recipes
    WHERE image_blob_id = :hash AND user_id = :userId AND deleted_at IS NULL
);
```

Run that check whenever a recipe is soft-deleted through `/sync/push`, repointed by §3a, or
cleared by §3c. When it comes back true, set `unreferenced_since = now()`; when a blob is
referenced again, clear it back to `NULL`.

A scheduled job then hard-deletes blobs where
`unreferenced_since < now() - unreferencedRetentionDays`, removing the storage object first and
the row second (an orphaned row is recoverable; an orphaned object is only findable by a full
scan).

The retention window exists because the client's delete is a soft delete with no undo
(ChefAI#129), and it deletes the local file immediately. The window is the only thing that would
make a future "undo delete" feature possible at all. Coordinate the number with #55 (account
deletion & data retention) — account deletion should cascade blobs immediately, not wait out the
window.

The same job should sweep storage objects with no `image_blobs` row (crash-orphans from §3a).

## 7. Client-side wiring (Android, for alignment — not this task)

- `RecipeEntity` / `Recipe` / `SyncRecipeDto` gain `imageBlobId: String?` (Room v4).
- `RecipeImageUploadWorker` sweeps recipes with a local file and no matching blob pointer,
  uploads via §3a, and writes the returned hash back with a targeted `UPDATE` that does not bump
  `updated_at`. It only considers recipes already `SYNCED`, so the recipe always exists
  server-side before its image does.
- `RecipeImageBackfillWorker` gains a preferred tier: when `imageBlobId != null`, fetch §3b with
  the authenticated client and store it locally, only falling back to the scrape ladder
  otherwise.
- The image loader (Coil) never calls these endpoints — bytes are fetched by the worker and
  rendered from the local file, so no auth token is ever handed to the image-loading stack.

## 8. Later, not now — the 50-user checkpoint (#144)

Each of these swaps in behind the endpoints above without a client change — worth preserving that
property when implementing:

- **DMCA §512(c) plumbing** — a registered agent, a takedown route keyed on provenance and the
  recipe's `image_url`, a repeat-infringer policy. Publicly serving scraped third-party images
  (§3b) is a real posture, consciously accepted here because the app has no users; that reasoning
  expires when it gets some. This is the item that makes the posture supported rather than merely
  accepted, and `serveScrapedBlobsToNonOwners = false` is the retreat if it isn't worth building.
- **Object storage / CDN** — replace `LocalDiskBlobStore`; §3b may then 302 to a short-lived
  signed URL instead of streaming. Egress from the app server is fine at 50 users and not at
  5,000.
- **Cross-user dedup** — under §1's per-user keying, many users importing the same recipe store
  byte-identical copies. The first real storage lever after the quota, at the cost of refcounting
  on delete and a more careful takedown story.
- **Thumbnails** — `imageUrlThumbnail` is currently set to `imageUrl` by the client and is a lie.
  Server-side derivatives are the right fix; they need an image-processing dependency, so treat
  that as its own decision.

## 9. Definition of Done

- Migration creating `image_blobs` and adding `recipes.image_blob_id`
- `BlobStore` interface + `LocalDiskBlobStore`, no cloud SDK dependency added
- `PUT /recipes/{id}/image` with all eight validations from §3a, including magic-byte sniffing
  and the streaming size cap
- Re-uploading identical bytes for an unchanged recipe is a no-op that does not move `updated_at`
- `GET /recipes/{id}/image` honouring the §3b visibility table, with ETag / 304
- `DELETE /recipes/{id}/image`
- `imageBlobId` returned on pull; client-supplied values on push ignored
- Reclamation job + dereference checks on soft-delete, repoint, and clear
- Quota and rate limit enforced
- Tests: hash mismatch → 422; a PNG body declared as `image/jpeg` → 415; oversized body → 413
  with nothing written; a second user → 404 on another user's PRIVATE recipe image, 200 on a
  PUBLIC one regardless of provenance; `allowScrapedImageUpload = false` → 403 for a scraped
  image and 200 for a user photo; `serveScrapedBlobsToNonOwners = false` → that same second user
  now 404s on a PUBLIC recipe's scraped image but still 200s on its owner's photo
- Integration test: push recipe → upload image → pull → `imageBlobId` present → GET returns the
  exact bytes → soft-delete the recipe → blob marked unreferenced → sweep after the window
  deletes both row and object
