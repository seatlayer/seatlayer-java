# Changelog

## 0.1.0 — unreleased

First release of the SeatLayer Java server SDK.

- `SeatLayer` client with secret-key auth, per-attempt timeouts, and a typed escape hatch.
- Resources: `charts()`, `events()`, `inventory()`, `sessions()`, `webhooks()`, `workspaces()`.
- Automatic `Idempotency-Key` on every mutation, reused across retries so a retried
  booking cannot become two bookings.
- Retries on 429/408/5xx with exponential backoff and full jitter; honours `Retry-After`.
  4xx is never retried.
- Typed exceptions: `SeatLayerAuthException` (with `isModeMismatch()`),
  `SeatLayerConflictException` (with `conflicts()` and `isSoldOut()`),
  `SeatLayerRateLimitException`, `SeatLayerValidationException`,
  `SeatLayerNotFoundException`, `SeatLayerConnectionException`.
- `Webhook.verify()` — raw-body HMAC-SHA256 verification via `MessageDigest.isEqual`.
- `createManageSession` requires explicit capabilities; the API's default grants
  `event:cancel`, which reverses paid bookings.
- Constructor rejects a `pk_` key by name rather than failing as a 401 later.
- `listAll()` returns a lazy `Iterable`, paging as you consume it.
- **Zero runtime dependencies.** Uses `java.net.http.HttpClient` and `javax.crypto.Mac`
  from the JDK, plus a hand-written JSON codec — so the SDK never forces a Jackson or
  OkHttp version on a consumer that already has one.

Requires Java 17.
