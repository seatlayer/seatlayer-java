# Changelog

## 0.8.0 (2026-09-10)

- Added Event hosting regions: the `EventHostingRegion` enum, a `region` overload on event create, `defaultRegion` on workspace create, and `updateDefaultRegion`. Events created without a region run in Western Europe.

## 0.7.1 — 2026-09-05

- Clarifies the Maven Central package name and description around Java and
  Kotlin seat booking, inventory and webhook workflows, with a direct link to
  the Java integration guide.
- Refreshes README checkout examples, retry guidance and API coverage. Public
  APIs and runtime behavior are unchanged from 0.7.0.

## 0.7.0 — 2026-08-30

- Added coverage for all 48 Fixed Renewable Season server
  operations under `seasons()`, with exact path encoding and operation-specific
  retry/idempotency behavior.
- Season allocations are identity-only and the API response declares host
  pricing authority. Buyer rehearsal validation sends no evidence body because
  SeatLayer discovers the retained hold, booking, cancellation, and delivered
  webhook chain automatically.

## 0.6.1

- Documentation only. Refreshes the README, adds frequently asked
  questions, and aligns package metadata. No API or behaviour changes.

## 0.6.0 — 2026-08-23

- Added exact immutable Event configuration binding reads and compare-and-set
  attach/detach through `Events.retrieveConfigurationBinding` and
  `Events.updateConfigurationBinding`. Updates remain deliberately
  single-attempt.

## 0.5.0 — 2026-08-21

- Added `performanceGroups()`, the trusted server resource for fixed two-to-eight
  performance runs. It creates and activates groups, mints one-time browser
  access, retrieves authoritative group holds, and confirms bookings with
  stable action and order references. Browser-only group routes remain outside
  this secret-key SDK.

- **Security/reliability:** Mutations now default to a single attempt. Automatic header-replay
  retries are limited to chart create/copy, template instantiation, event create, and workspace
  create, preventing transient failures from duplicating holds or best-available results and from
  issuing extra show-once credentials.

- Added `templates().instantiateTemplate` for materializing published catalog templates as drafts,
  with header-replay idempotency.
- Added ticket-release list, full-list replacement, and close methods to `events()`.

## 0.2.0 — 2026-08-12

- Added channel allocation management and origin-bound buyer access sessions.
- Added channel-aware hold and booking controls, including explicit privileged override reasons.
- Added paginated booking lifecycle reads and encoded booking retrieval.
- Booking and cancellation calls now reject missing or blank stable booking references.
- Expanded the README with private-sale guidance and direct links across the SeatLayer SDK family.

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
