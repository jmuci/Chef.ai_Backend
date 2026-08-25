# Home Layout SDUI (Backend)

## Overview

PocketChef Home is server-driven. The backend owns the complete Home layout and returns typed UI components to Android via:

- `GET /api/v1/home/layout`

**This endpoint is anonymous — no `Authorization` header required.** That's the spec'd behavior,
not an oversight: the Home screen is the anonymous-first entry point, and must load before an
account exists. `homeRoutes(homeLayoutService)` is mounted directly in `Routing.kt`, outside every
`authenticate(...)` block, alongside `authRoutes`.

This wasn't always true in practice. For a period (`4ba84d4`/`5bbe028`, 2026-03-26) the route was
accidentally nested inside `authenticate("auth-jwt")`, which 401'd every anonymous request — the
Android client fell back to a bundled fixture with dead image URLs, so every Home recipe image
rendered broken for signed-out sessions. Fixed in `6a4bb33` (2026-08-24), which moved `homeRoutes`
back outside the auth block; `smoke-test.sh` now asserts a `200` with no auth header as part of
its standard run, specifically to catch a regression back to the broken state.

It currently returns one static layout from a bundled resource file; the `userId` isn't used for
personalization yet (see [Future Personalization Hook](#future-personalization-hook)) — there's
no principal to read in the first place, since the route sits outside any `authenticate` block.

## Endpoint Contract

### Request

- Method/Path: `GET /api/v1/home/layout`
- Headers:
  - `Authorization: Bearer <jwt>` — **not accepted**. The route reads no auth principal at all;
    a token, valid or not, has no effect on the response.
  - `If-None-Match: <etag>` for cache revalidation (optional)

### Success Response

- `200 OK` with `HomeLayoutResponse` JSON body
- Headers:
  - `ETag: "<layoutChecksum>"`
  - `Cache-Control: max-age=300`
  - `X-Min-Schema-Version: 1.0.0`

### Not Modified Response

- `304 Not Modified` (empty body) when `If-None-Match` matches current checksum

### Error Response

- `500 Internal Server Error` if the layout resource cannot be read or parsed

## Data Model

Top-level response:

- `schemaVersion` (`String`)
- `layoutChecksum` (`String`, MD5 of canonical `components` JSON)
- `components` (`List<HomeComponent>`)
- `sidecar` (`HomeSidecar?`) — see below

Component `type` values:

- `section_header`
- `carousel`
- `large_card`
- `squared_card`
- `list_card`

Card components (`large_card`, `squared_card`, `list_card`) carry only a `recipeId` pointer —
they don't embed recipe content. `HomeSidecar` is where that content actually lives:

### HomeSidecar

Loaded from a second bundled resource, `/src/main/resources/home_sidecar.json`, decoded
independently of the layout resource and always present in the response (non-null in practice,
though the DTO allows null for forward compatibility). It denormalizes everything a client needs
to render a card without a follow-up request:

- `recipes: List<SidecarRecipeDto>` — full card content: title, description, image URLs,
  prep/cook time, servings, `creatorId`, `privacy`, `tagIds`, `labelIds`, `ingredients`, `steps`
- `tags: List<SidecarTagDto>`, `labels: List<SidecarLabelDto>` — display names for the tag/label
  IDs referenced by `recipes`
- `creators: List<SidecarCreatorDto>` — display name + avatar for each `creatorId` referenced by
  `recipes`

A `large_card`/`squared_card`/`list_card` component's `recipeId` is resolved by the client against
`sidecar.recipes` — the same pointer-plus-sidecar-lookup pattern as `/sync/pull`'s reference data,
but scoped to exactly what's needed for the components in this one response instead of a general
delta/gap union.

Implementation location:

- DTOs + polymorphic serializer: `/src/main/kotlin/application/dto/HomeLayoutDtos.kt`
- Service: `/src/main/kotlin/domain/service/HomeLayoutService.kt`
- Route: `/src/main/kotlin/presentation/routes/HomeRoutes.kt`
- Bundled resources: `/src/main/resources/home_layout.json`, `/src/main/resources/home_sidecar.json`

## Unknown Type Handling

The custom `HomeComponent` serializer maps unrecognized `type` values to an `UnknownHomeComponent` sentinel. The service sanitizes the parsed structure and drops unknown components before responding, so malformed/forward-compatible resource data does not crash the server.

## Checksum and ETag

`layoutChecksum` is computed as:

1. Serialize the sanitized `components` list to compact canonical JSON.
2. Compute MD5 of that JSON string.
3. Return the hex digest in both:
   - response body (`layoutChecksum`)
   - response header (`ETag` with quotes)

## Future Personalization Hook

`HomeLayoutService.getHomeLayout(userId: String? = null)` already accepts a `userId` parameter for
a future user-aware resolution path without changing the API contract — but the route doesn't
pass one through yet (`homeRoutes` calls `getHomeLayout()` with no argument, and since the route
sits outside every `authenticate` block, there's no principal to pass even if it wanted to).
Every caller, anonymous or not, currently gets the same static layout and sidecar. Personalizing
this later would need the route to move under `authenticate("auth-jwt", optional = true)` —
the same anonymous-capable pattern `recipeSearchRoutes`/`recipeDetailRoutes` already use (see
[`docs/recipe-search.md`](recipe-search.md)) — rather than requiring auth outright, to avoid
reintroducing the `6a4bb33` regression.