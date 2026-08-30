# SeatLayer Java Server SDK for Reserved Seating

[![CI](https://github.com/seatlayer/seatlayer-java/actions/workflows/ci.yml/badge.svg)](https://github.com/seatlayer/seatlayer-java/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.seatlayer/seatlayer-java.svg)](https://central.sonatype.com/artifact/io.seatlayer/seatlayer-java)
[![License: MIT](https://img.shields.io/badge/license-MIT-111827.svg)](LICENSE)

The official SeatLayer Java server SDK is the **trusted side** of a reserved-seating
integration: inspect the holds a buyer created, price from server data, and book with a
stable `bookingRef`. From Java or Kotlin you manage seating charts, events, sales channels,
and live seat inventory through one typed ticketing API client.

[SeatLayer artifact on Maven Central](https://central.sonatype.com/artifact/io.seatlayer/seatlayer-java) ·
[SeatLayer server SDK documentation](https://docs.seatlayer.io/server-sdk/install/) ·
[SeatLayer developer platform](https://seatlayer.io/developers/) ·
[SeatLayer JavaScript seat map SDK](https://www.npmjs.com/package/@seatlayer/js) ·
[SeatLayer AI Toolkit](https://github.com/seatlayer/seatlayer-ai-toolkit)

> **Server-side only.** This library authenticates with your secret key. Never ship it in an
> Android app or anything a ticket buyer can reach — browser and mobile surfaces get short-lived,
> origin-bound tokens that you mint here.

## Install

```xml
<dependency>
  <groupId>io.seatlayer</groupId>
  <artifactId>seatlayer-java</artifactId>
  <version>0.6.0</version>
</dependency>
```

```groovy
implementation 'io.seatlayer:seatlayer-java:0.6.0'
```

Published on Maven Central as `io.seatlayer:seatlayer-java`; `0.6.0` is the current release, so no
extra repository declaration is needed. Requires Java 17 or newer. **Zero runtime dependencies** — the SDK uses
`java.net.http.HttpClient` and `javax.crypto.Mac` from the JDK plus a small hand-written JSON
codec, so it never forces a Jackson or OkHttp version on an application that already has one.

## Quick start

```java
import io.seatlayer.SeatLayer;
import java.util.Map;

SeatLayer seatlayer = new SeatLayer(System.getenv("SEATLAYER_SECRET_KEY"));

// 1. Materialize a published catalog template as a draft for this organiser.
Map<String, Object> chart = (Map<String, Object>) seatlayer.templates()
    .instantiateTemplate("your-published-template").get("meta");
seatlayer.charts().publish((String) chart.get("id"));

// 2. Create an event on it.
Map<String, Object> event =
    (Map<String, Object>) seatlayer.events().create((String) chart.get("id"), "Spring Gala").get("meta");

// 3. Sell four seats over the phone.
Map<String, Object> held = seatlayer.inventory().holdBestAvailable((String) event.get("key"), 4);
// … take payment against held.get("items"), which carry authoritative prices …
seatlayer.inventory().book((String) event.get("key"), (String) held.get("holdId"), "order-8842");
```

## Test vs live

## Fixed Renewable Seasons (unpublished candidate)

The source candidate exposes all 48 trusted organizer operations through
`seatlayer.seasons()`. It is not part of the currently published Maven Central
artifact and does not make a production-support claim.

After the test hold/book/cancel journey and matching webhook deliveries,
`validateSeasonBuyerRehearsal(seasonKey)` sends no evidence body; SeatLayer
discovers the retained chain automatically. Retrieved Season holds contain
inventory identity, not an authoritative amount—your platform owns package
price, payment, order, tax, refunds, benefits, and ticket or pass delivery.

```java
Map<String, Object> checked = seatlayer.seasons().validateSeason(Map.of(
    "sourcePerformanceGroupKeys", List.of("pg_subscription_run")));
Map<String, Object> created = seatlayer.seasons().createSeason(Map.of(
    "name", "2027 subscription",
    "sourcePerformanceGroupKeys", List.of("pg_subscription_run")),
    "season-create-2027");
```

Treat `202` as accepted work and poll `retrieveSeasonLifecycle()` with the
returned operation identity. Buyer-session minting and domain-exact booking,
cancellation, and renewal actions remain single-attempt; only declared
header-replay catalogue mutations retry automatically.


Keys carry their own mode. `sk_test_…` keys can only touch test-mode events and `sk_live_…` only
live ones; crossing them returns `403 mode_mismatch`, surfaced as `SeatLayerAuthException` with
`isModeMismatch()`.

```java
SeatLayer seatlayer = new SeatLayer(System.getenv("SEATLAYER_SECRET_KEY"));
if ("production".equals(System.getenv("ENV")) && !"live".equals(seatlayer.mode())) {
    throw new IllegalStateException("Refusing to boot production against test-mode seating data.");
}
```

## The two selling flows

**Buyer picks seats in the browser.** Your frontend holds them; your backend confirms the price and
books. Never price from what the browser sent you — `retrieveHold` is authoritative.

```java
Map<String, Object> hold = seatlayer.inventory().retrieveHold(eventKey, holdId);
// … charge the total of hold.get("items") in hold.get("currency") …
seatlayer.inventory().book(eventKey, holdId, charge.id());
```

**Your backend picks the seats.** Phone orders, box office, comps.

```java
// Payment already taken — book outright, so nothing is stranded if a second call fails.
seatlayer.inventory().bookBestAvailable(eventKey, 2, "phone-1183");

// Or name the seats yourself.
seatlayer.inventory().boxOfficeBook(eventKey, List.of("A-1", "A-2"), "comp-14");
```

## Private and partner sales

Channels reserve inventory for a partner, member group, presale, or other private allocation. A
buyer access session is short-lived and origin-bound, so the browser receives only the allocation
it is allowed to sell; your secret key remains on your server.

```java
seatlayer.channels().create(
        eventKey, "Venue members", null, null, null, "private", null, null);

seatlayer.channels().updateAssignments(
        eventKey, List.of("A-1", "A-2"), 1L, "ch_members", null, null);

Map<String, Object> access = seatlayer.channels().createBuyerAccessSession(
        eventKey,
        false,
        "https://members.example",
        List.of("ch_members"),
        null,
        2,
        null,
        null,
        null,
        null);
```

Pass the returned token to the buyer SDK. Trusted backend sale overloads accept `channelIds`, an
explicit privileged `ignoreChannelRestrictions` flag, and an audit `reason`.

## Listing and pagination

`list()` returns one `Page` plus a cursor. When you want everything, `listAll()` pages for you and
yields as you consume it — a lazy `Iterable` rather than a `List`, because the point of paginating
is to *not* hold an unbounded result set in memory.

```java
// One page, your own paging.
Page<Map<String, Object>> page = seatlayer.events().list(
    EventListOptions.builder().limit(50).build());
page.items();
page.nextCursor();   // Optional.empty() once exhausted

// Or let the SDK walk it.
for (Map<String, Object> event : seatlayer.events().listAll()) {
    sync(event);
}
```

Listing events includes live availability `counts` by default, which costs the server one
round-trip **per event**. `listAll()` turns them off automatically — walking a whole catalogue is
exactly when you don't want that — and you can control it explicitly:

```java
seatlayer.events().list(EventListOptions.builder().limit(50).counts(false).build());
```

## Keeping a hold alive

When an order takes longer than the checkout window — an invoice, a phone sale — extend rather than
release and re-hold. Releasing first hands the seats to whoever is racing for them in between.

```java
try {
    seatlayer.inventory().extendHold(eventKey, holdId, 10 * 60_000L);
} catch (SeatLayerConflictException e) {
    // Gone, expired, or at its renewal cap — the buyer has to re-pick.
}
```

## Embedding the control room

Your secret key never reaches a browser. Mint a scoped token instead.

```java
Map<String, Object> session = seatlayer.sessions().createManageSession(
    eventKey,
    "https://box-office.yourplatform.com",
    List.of("event:view", "event:block"),
    3600);
```

`capabilities` is **required** by this SDK even though the API defaults it. That default grants all
four including `event:cancel`, which reverses paid bookings — not something that should arrive by
forgetting an argument. Grant the smallest set the page needs.

## Webhooks

Verify every delivery against the **raw** body. Re-serialising it changes the bytes and
verification will fail.

```java
import io.seatlayer.Webhook;
import io.seatlayer.WebhookVerificationException;

// Spring: declare the parameter as byte[], never a parsed object
@PostMapping("/webhooks/seatlayer")
public ResponseEntity<Void> handle(
        @RequestBody byte[] payload,
        @RequestHeader("X-SeatLayer-Signature") String signature) {

    Map<String, Object> event;
    try {
        event = Webhook.verify(payload, signature, System.getenv("SEATLAYER_WEBHOOK_SECRET"));
    } catch (WebhookVerificationException e) {
        return ResponseEntity.badRequest().build();
    }

    // The signed body carries `at`, but nothing enforces a freshness window, so a
    // captured delivery stays valid indefinitely. Deduplicate on occurrenceId —
    // this is your replay protection, not an optimisation.
    if (alreadyProcessed((String) event.get("occurrenceId"))) {
        return ResponseEntity.ok().build();
    }

    process(event);
    return ResponseEntity.ok().build();
}
```

## Errors

```java
try {
    seatlayer.inventory().holdBestAvailable(eventKey, 6);
} catch (SeatLayerConflictException e) {
    if (e.isSoldOut()) {
        return showAlternativeDates();      // a business outcome, not a bug
    }
    throw e;
} catch (SeatLayerRateLimitException e) {
    return retryAfter(e.retryAfterSeconds());
} catch (SeatLayerAuthException e) {
    if (e.isModeMismatch()) {
        throw new IllegalStateException("Test key pointed at a live event, or the reverse.");
    }
    throw e;
}
```

Every exception carries `status()`, `code()`, `body()`, and `requestId()` — quote the request id in
support requests. All are unchecked, so they do not force `throws` clauses through your call stack.

## Reliability

**Retries and idempotency.** Reads (`GET`/`HEAD`) retry connection failures, 408, 429 and 5xx with
exponential backoff and full jitter; `Retry-After` wins when the server sends it. Five
provisioning operations have the same retry behaviour with header replay: `charts().create`,
`charts().copy`, `templates().instantiateTemplate`, `events().create`, and
`workspaces().create`. They generate an `Idempotency-Key` when absent and reuse that key across
every attempt. Overloads that accept a key let you provide a stable provisioning key instead.

All other mutations are single-attempt: holds, bookings, lifecycle changes, channel changes,
show-once secret creation, and raw requests. The SDK does not generate a key for them. A supplied
key on an existing typed-method overload is validated and forwarded once for compatibility, but it
does not enable retries or promise replay. Reconcile bookings with their required `bookingRef`;
never retry an unknown booking outcome as though the transport had made it safe.

```java
SeatLayer.builder()
    .secretKey(System.getenv("SEATLAYER_SECRET_KEY"))
    .maxRetries(3)                        // total attempts
    .timeout(Duration.ofSeconds(30))      // per attempt
    .build();
```

## Escape hatch

For surface this SDK does not wrap yet. Raw reads retain read retries; raw mutations use the same
auth and error mapping but are sent once and never receive an automatically generated key:

```java
seatlayer.request("POST", "/v1/events/ev_1/some-new-route", null, Map.of("qty", 2));
```

## API surface

| Resource | Methods |
| --- | --- |
| `charts()` | `list` `listAll` `create` `retrieve` `update` `delete` `copy` `archive` `unarchive` `publish` |
| `templates()` | `instantiateTemplate` |
| `events()` | `list` `listAll` `create` `retrieve` `retrieveConfigurationBinding` `updateConfigurationBinding` `update` `delete` `updateChart` `close` `reopen` `archive` `retrieveHoldTtl` `updateHoldTtl` `listTicketReleases` `updateTicketReleases` `closeTicketRelease` `retrieveReport` `retrieveLog` |
| `channels()` | `list` `create` `update` `updateAssignments` `listAllocation` `retrieveAccessPreview` `retrieveReport` `pause` `unpause` `archive` `createBuyerAccessSession` `listBuyerAccessSessions` `revokeBuyerAccessSession` |
| `inventory()` | `hold` `holdBestAvailable` `bookBestAvailable` `extendHold` `retrieveHold` `release` `book` `bookLabels` `boxOfficeBook` `unbook` `block` `unblock` `unblockAll` `retrieveAvailability` `updateAvailability` `listBookings` `retrieveBooking` |
| `sessions()` | `createManageSession` `revokeManageSession` `createDesignerSession` `revokeDesignerSession` |
| `webhooks()` | `list` `create` `update` `delete` `listDeliveries` |
| `workspaces()` | `list` `create` `retrieve` `update` |

Full reference: [docs.seatlayer.io/server-sdk](https://docs.seatlayer.io/server-sdk/install/)

## Frequently asked questions

### How do I book seats from Java?

Add the [`io.seatlayer:seatlayer-java` artifact](https://central.sonatype.com/artifact/io.seatlayer/seatlayer-java),
construct a `SeatLayer` instance with your secret key, and call `inventory().book(...)` with the
hold id and a stable `bookingRef`. When your own backend picks the seats — phone orders, box
office, comps — `inventory().bookBestAvailable(...)` and `inventory().boxOfficeBook(...)` book
outright with no prior hold. A booking reference is required on every booking call, so each sale is
tied to an immutable order id you can reconcile against later.

### What does the server SDK do that the buyer SDK does not?

The buyer SDK runs in the browser or mobile app and only **selects and holds** seats. This Java SDK
runs on your trusted server and **inspects and books** them. Your secret key never reaches a buyer
surface: browsers and mobile apps receive short-lived, origin-bound tokens minted here through
`sessions().createManageSession(...)` or `channels().createBuyerAccessSession(...)`. Always price a
sale from `inventory().retrieveHold(...)`, never from values the client sent you.

### How do temporary seat holds work server-side?

A hold reserves seats against concurrent buyers for a limited checkout window. From Java you
retrieve it with `inventory().retrieveHold(...)`, whose items and currency are authoritative for
pricing, and confirm it with `inventory().book(...)`. Use `inventory().extendHold(...)` for a long
checkout instead of releasing and re-holding, which would hand the seats to whoever is racing for
them. Booking is a single automatic attempt: after an unknown network outcome you may reconcile and
repeat the exact same event, hold, and `bookingRef` — seats already booked under that reference are
not sold again.

### Can I use my own payment provider?

Yes. SeatLayer never processes payment. Charge through Stripe, Adyen, Braintree, or any provider
you already use, calculating the total from the server-inspected hold items rather than from client
input, then call `inventory().book(...)` with your charge or order id as the `bookingRef`. The
[holds and checkout guide](https://docs.seatlayer.io/buyer-sdk/holds-and-checkout/) walks through
the full handoff.

## Continue your Java integration

- [Follow the SeatLayer server SDK guide](https://docs.seatlayer.io/server-sdk/install/)
  for installation, authentication, and the full hold-to-booking flow.
- [Handle errors, retries, and safe booking repeats](https://docs.seatlayer.io/server-sdk/reliability/)
  before connecting a production order flow.
- [Verify SeatLayer webhooks](https://docs.seatlayer.io/server-sdk/webhooks/)
  to react to holds, expiry, and bookings on your server.
- [Browse the SeatLayer server API reference](https://docs.seatlayer.io/server-api/events/)
  for every endpoint behind this SDK.
- [Generate clients from the SeatLayer OpenAPI description](https://docs.seatlayer.io/openapi.json)
  or explore the raw API surface.
- [Point AI coding agents at the SeatLayer docs index](https://docs.seatlayer.io/llms.txt)
  (`llms.txt`) for an agent-readable map of the documentation.
- [Explore every SeatLayer SDK on GitHub](https://github.com/seatlayer)
  across web, mobile, and server.

## SeatLayer SDK ecosystem

| Surface | Package or source |
|---|---|
| JavaScript | [`@seatlayer/js`](https://www.npmjs.com/package/@seatlayer/js) |
| React | [`@seatlayer/react`](https://www.npmjs.com/package/@seatlayer/react) |
| React Native | [`@seatlayer/react-native`](https://www.npmjs.com/package/@seatlayer/react-native) |
| iOS | [`seatlayer-ios`](https://github.com/seatlayer/seatlayer-ios) |
| Flutter | [`seatlayer`](https://pub.dev/packages/seatlayer) |
| Android | [`seatlayer-android`](https://github.com/seatlayer/seatlayer-android) |
| Server SDKs | [Node.js, Python, PHP, Ruby, .NET, Java, and Go](https://docs.seatlayer.io/server-sdk/install/) |
| Node.js (server) | [`@seatlayer/server`](https://www.npmjs.com/package/@seatlayer/server) |
| Python (server) | [`seatlayer`](https://pypi.org/project/seatlayer/) |
| PHP (server) | [`seatlayer/seatlayer-php`](https://packagist.org/packages/seatlayer/seatlayer-php) |
| Ruby (server) | [`seatlayer`](https://rubygems.org/gems/seatlayer) |
| .NET (server) | [`SeatLayer`](https://www.nuget.org/packages/SeatLayer) |
| Java (server) | [`io.seatlayer:seatlayer-java`](https://central.sonatype.com/artifact/io.seatlayer/seatlayer-java) (this artifact) |
| Go (server) | [`github.com/seatlayer/seatlayer-go`](https://pkg.go.dev/github.com/seatlayer/seatlayer-go) |

## Development

```bash
mvn verify   # compile, test, and build the main/sources/javadoc jars
```

Publishing to Maven Central is documented in [RELEASE.md](RELEASE.md). Signing lives in a
`release` profile, so an ordinary `mvn verify` needs no GPG key.

## License

MIT
