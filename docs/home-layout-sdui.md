# Home Layout SDUI (Backend)

## Overview

PocketChef Home is server-driven. The backend owns the complete Home layout and returns typed UI components to Android via:

- `GET /api/v1/home/layout`

This endpoint is public (anonymous allowed) and currently returns one static layout from a bundled resource file.

## Endpoint Contract

### Request

- Method/Path: `GET /api/v1/home/layout`
- Optional headers:
  - `Authorization: Bearer <jwt>` (reserved for future personalization)
  - `If-None-Match: <etag>` for cache revalidation

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

Component `type` values:

- `section_header`
- `carousel`
- `large_card`
- `squared_card`
- `list_card`

Implementation location:

- DTOs + polymorphic serializer: `/src/main/kotlin/application/dto/HomeLayoutDtos.kt`
- Service: `/src/main/kotlin/domain/service/HomeLayoutService.kt`
- Route: `/src/main/kotlin/presentation/routes/HomeRoutes.kt`
- Bundled layout resource: `/src/main/resources/home_layout.json`

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

`HomeLayoutService.getHomeLayout(userId: String? = null)` already supports a future user-aware resolution path without changing the API contract.

## Future Auth

Right now the endpoint is publicly exposed, it should definitely use authentication for authenticated user. Question is how it should work for anonymous users.