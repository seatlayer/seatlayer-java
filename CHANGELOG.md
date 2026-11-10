# Changelog

## Next: 0.9.0, season seats (joins the season 2a section under ## 0.9.0 in the version PR; publish only once the phase 4 API is live on production)

- Season seats: `Seasons.setAsideSeasonBlock` (`POST /v1/seasons/:key/block`),
  `retrieveSeasonBlock` and `releaseSeasonBlock` (`POST /v1/seasons/:key/block/release`). Each
  write answers the `job` and the `block`; follow the job with `retrieveSeasonBulkJob` (kinds
  `season_block_set_aside` and `season_block_release`, read by id, not listed). Both writes are
  retried safely with one Idempotency-Key, generated when none is given.
- `inventoryMode` on season create and update (opt a season into season seats), read back on every
  season.
- Webhook names `season.block.ready` and `season.block.released` (`Webhooks` sends any name the
  API accepts).
- A season-seats season's date cancel (`createSeasonAmendment`) or past date
  (`markSeasonPerformancePast`) can answer `202` while the date leaves every season seat:
  `Seasons.isDateLeavePending` recognises it.
- New refusals come through `SeatLayerException` and its subclasses with `code()`:
  `season_block_not_enabled`, `block_exists`, `block_not_ready`, `season_not_draft_or_active`,
  `block_selection_empty`, `seat_not_in_block`, `season_block_set_aside`, `season_inventory_locked`,
  `date_leave_in_progress`.
  `inventoryMode` can also answer `season_block_packages` (more than one package),
  `season_block_on_sale`, `season_block_exists` and `invalid_inventory_mode`. The set-aside can
  also answer `season_block_packages`, `season_block_one_package`, `plan_not_published`,
  `season_block_undoing`, `chart_unavailable` and `dates_missing`, and the release
  `release_mismatch`. On `503 season_busy` or `season_not_configured`, send the same request again.

## Unreleased

- A date its season cancelled never opens again: `transitionEventSales` to `testable` or `live`
  and `reopen` answer `409 season_date_cancelled`, and `updateEventSeries` with `sales: "on"`
  leaves it closed as a failed item of the job (`season_date_cancelled`; not in `failedDates`).
- `createSeasonAmendment` refuses to move (`reschedule`), postpone or replace a cancelled date,
  and `markSeasonPerformancePast` refuses to mark one as taken place:
  `409 performance_cancelled`.
- `updateEventSeries` sends only the prices that change: each date keeps its own price for every
  category (or tier) left out, so weekend and weekday prices keep their difference.
- A season's `transferCutoffHours` / `returnCutoffHours` of `null` means the club default
  (Settings › Ticket site), or until the date starts when the club has none.
- `createHolderTransfer` and `createHolderReturn` are not stopped by the club's customer switches
  in Settings › Ticket site; cut-offs still apply.
- Cancelling a date voids its tickets as a `date_cancel_void` job, named by
  `Seasons.dateCancelVoidJobId(amendmentId)` (`sbj_void_…`); follow it with
  `retrieveSeasonBulkJob`. `season.ticket.voided` says `date_cancelled` for each season ticket it
  voids.
- A Season package holds 2 to 50 dates. `Seasons.holderImportRowsPerCall(dates)` gives the most
  rows one `createSeasonHolderImport` call takes (100 up to 20 dates, 43 at 50); a call with more
  answers `422 invalid_rows` with `maxRows` in the error's `body()`.
- `updateSeason`'s `singleTicketDates` can carry `jobRefused`, why a `season_singles` job could
  not start; the change stands, and sending `singleTickets: true` again opens any date still not
  open. An `updateSeason` retried with its first key and `expectedRevision` is not applied twice.
- `eventSeries().createEventSeries`: a series create given up for its key answers
  `409 create_abandoned` with a message to send it again with a new idempotency key.
- Added 39 Season operations to `seasons()`, for 87 in all: performances, package prices, plan
  edits and deletion, one-call publish, checkout tests, occurrence returns and reclaims, holders,
  Season order refunds and cancellations, hosted renewals, renewal links and notices, renewal
  quotes, intents and checkouts, sales and seat reports, seat counts, holder and order CSV exports, and bulk refunds and the release of lapsed renewal offers with their jobs.
- Added `holders()` (customers, holder reads and updates, sign-out, seasons, tickets, and ticket
  transfers), `orders()` (orders across events and seasons, one event's orders, CSV export,
  refunds, cancellations, and ticket emails), and `eventSeries()` (create, list, retrieve, add
  dates, and update).
- Added `holders().createHolderReturn`: give back a holder's seat on one date, one per ticket.
- Added event sales operations to `events()`: buyer checkout, payment gateway, sales readiness,
  preflight and transition, booking fee, and discount codes.
- The three CSV exports return the CSV text as a `String`. `deleteSeasonPlan` and
  `deleteEventDiscountCode` return nothing (the API answers 204).
- 27 of the new operations use header replay, with one `Idempotency-Key` reused on every retry.
  The other 54 are single-attempt and send no key.
- Added `seasons().refundSeasonPerformance` (refund every order of a cancelled date as a job, with
  header replay), `seasons().listSeasonBulkJobs`, and `seasons().retrieveSeasonBulkJob`.
- Documented the new request fields the `Map` bodies carry: `priceTypeKey` on each
  `replaceSeasonPackagePrices` price; `checkoutMode`, `transferCutoffHours` and
  `returnCutoffHours` on `updateSeason`; `holderEmail` and `holderName` on each
  `createSeasonHolderImport` row; and `kind` `postpone` or `cancel`, with `refund` and `reason`, on
  `createSeasonAmendment`.
- `EventListOptions` takes `seriesKey` (builder `seriesKey(...)`) to list one event series' dates;
  the five-argument constructor stays.
- Added `seasons().retrieveSeasonRenewalQuote`: what renewing an offer costs, and whether SeatLayer checkout can take it now.
- Added `seasons().createSeasonRenewalIntent`: record a holder's intent for a renewal taken by phone or at the box office, so your own checkout can commit.
- Added `seasons().createSeasonRenewalCheckout`: a SeatLayer checkout that renews an offer with no hosted page, with header replay.
- Added `holders().createHolderRenewalCheckout`: the same checkout for one of a holder's offers, with header replay.
- Added `holders().declineHolderRenewalOffer`: decline one of a holder's offers, with an optional `reason`.
- Added a `declineSeasonRenewalOffer` overload that takes a `reason`, kept on the offer as `declineReason`.
- `updateSeason` takes `audience` (`embed_only` or `public`).
- `openSeasonSales` answers `warnings` (`checkout_test_missing` until a checkout test or rehearsal has passed) and can refuse with `409 plan_not_published` or `409 checkout_mode_not_chosen`.
- A cancelled date refunds per date only: an amendment's `refund` is `none` or `per_date`, and `refundSeasonPerformance` takes `amount: per_date` (`full` answers `422 date_refund_per_date_only`; refund a whole order with the order refund).
- `addEventSeriesDates` takes `{"retryFailed": true}` to make the failed dates again (`409 series_busy`, `422 nothing_to_retry`).
- Added a `listOrders` overload with `holderRef`; orders carry `holderRef` and `customerId`, and `buyer.email` can be null (a box office sale with no email).
- Season holds, give-backs and date cancels can refuse with `performance_cancelled`, `refund_not_started` (503), `missing_manage_capability` (403) or `invalid_refund`; a holder's status can be `reserved`; hosted renewal readiness carries `missingPrices`.
- `eventSeries().createEventSeries` takes `fromEventKey` in place of `name` and `chartKey`: an event you already set up becomes the first date under its own key, and every other date is a copy of it. It refuses with `404 event_not_found`, or `422 event_has_no_date`, `event_in_season`, `event_in_performance_group`, `event_in_series` or `invalid_request`.
- Every date of an event series carries the series' plain name (`Hamlet`, not `Hamlet · 3 Nov`); tell dates apart by `startsAt`. `updateEventSeries` renames each date that carries the series name, and a series name cannot hold `{n}`, `{date}` or `{time}`.
- Added `seasons().releaseSeasonLapsedOffers`: release the seats of every lapsed renewal offer as a job (`202` with the `job`), with header replay. An offer extended before its turn is skipped and keeps its seat.
- Added `retrieveSeasonHostedRenewals` and `listSeasonRenewalOffers` overloads that take `cursor` and `limit` (1-500 and 1-100); both answers carry `nextCursor`, null on the last page, and a bad cursor or limit answers `400 invalid_cursor` or `invalid_limit`. The one-argument forms read the first page.
- `createSeasonRenewalOffers` answers `201` with the `offers` for 100 contracts or fewer, and `202` with a `renewal_run` `job` and its `batchId` above that: follow the job with `retrieveSeasonBulkJob`, then page the offers with `listSeasonRenewalOffers`. It can refuse with `409 job_mismatch` or `503 season_not_configured`.
- `createSeasonHolderImportFromSeason` answers `202` with a `holder_import` `job` when a commit brings across more than 100 holders; the job's `summary` has the same counts when it completes. It can refuse with `409 job_mismatch`, or `503 job_busy` or `season_not_configured`. A retrieved bulk job carries `summary`, and its `kind` can be `holder_import`.
- `createSeasonRenewalNotices` takes up to 20,000 `offerIds`; more answers `422 too_many_offer_ids`.
- `createSeasonRenewalNotices` takes `batchId` instead of `offerIds`: email every open offer one `createSeasonRenewalOffers` batch made, such as the batch a `renewal_run` job made.
- Single tickets on a season: `createSeason` and `updateSeason` take `singleTickets` in their params (default `false`, Season only; anything else answers `422 invalid_single_tickets`). Every season read carries `singleTickets`, and a season on sale may carry `singleTicketsNotOpen`. `updateSeason`, `openSeasonSales` and `resumeSeasonSales` answer `singleTicketDates` (`direction`, `opened`, `closed`, `notOpened`, `jobId`) when they open or stop the dates' own sales; a `season_singles` job does it for more than 12 upcoming dates.

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
