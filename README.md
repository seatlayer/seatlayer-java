# SeatLayer Java SDK

[![CI](https://github.com/seatlayer/seatlayer-java/actions/workflows/ci.yml/badge.svg)](https://github.com/seatlayer/seatlayer-java/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.seatlayer/seatlayer-java.svg)](https://central.sonatype.com/artifact/io.seatlayer/seatlayer-java)
[![License: MIT](https://img.shields.io/badge/license-MIT-111827.svg)](LICENSE)

Official Java server SDK for the [SeatLayer](https://seatlayer.io) reserved-seating API.

> **Server-side only.** This library authenticates with your secret key. Never ship it in an
> Android app or anything a ticket buyer can reach — browser and mobile surfaces get short-lived,
> origin-bound tokens that you mint here.

## Install

```xml
<dependency>
  <groupId>io.seatlayer</groupId>
  <artifactId>seatlayer-java</artifactId>
  <version>0.2.0</version>
</dependency>
```

```groovy
implementation 'io.seatlayer:seatlayer-java:0.2.0'
```

Requires Java 17 or newer. **Zero runtime dependencies** — the SDK uses
`java.net.http.HttpClient` and `javax.crypto.Mac` from the JDK plus a small hand-written JSON
codec, so it never forces a Jackson or OkHttp version on an application that already has one.

## Quick start

```java
import io.seatlayer.SeatLayer;
import java.util.Map;

SeatLayer seatlayer = new SeatLayer(System.getenv("SEATLAYER_SECRET_KEY"));

// 1. Provision a venue for a new organiser from one of your templates.
Map<String, Object> chart = (Map<String, Object>) seatlayer.charts().copy("c_template_arena").get("meta");
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

**Retries.** 429, 408 and 5xx are retried with exponential backoff and full jitter; `Retry-After`
wins when the server sends it. 4xx is never retried — it will not start succeeding.

**Idempotency.** Every mutating request carries an `Idempotency-Key`, generated if you do not supply
one, and **reused across retries** so a retried booking cannot become two bookings.

```java
SeatLayer.builder()
    .secretKey(System.getenv("SEATLAYER_SECRET_KEY"))
    .maxRetries(3)                        // total attempts
    .timeout(Duration.ofSeconds(30))      // per attempt
    .build();
```

## Escape hatch

For surface this SDK does not wrap yet — same auth, retries, idempotency and error mapping:

```java
seatlayer.request("POST", "/v1/events/ev_1/some-new-route", null, Map.of("qty", 2));
```

## API surface

| Resource | Methods |
| --- | --- |
| `charts()` | `list` `listAll` `create` `retrieve` `update` `delete` `copy` `archive` `unarchive` `publish` |
| `events()` | `list` `listAll` `create` `retrieve` `update` `delete` `updateChart` `close` `reopen` `archive` `retrieveHoldTtl` `updateHoldTtl` `retrieveReport` `retrieveLog` |
| `channels()` | `list` `create` `update` `updateAssignments` `listAllocation` `retrieveAccessPreview` `retrieveReport` `pause` `unpause` `archive` `createBuyerAccessSession` `listBuyerAccessSessions` `revokeBuyerAccessSession` |
| `inventory()` | `hold` `holdBestAvailable` `bookBestAvailable` `extendHold` `retrieveHold` `release` `book` `bookLabels` `boxOfficeBook` `unbook` `block` `unblock` `unblockAll` `retrieveAvailability` `updateAvailability` `listBookings` `retrieveBooking` |
| `sessions()` | `createManageSession` `revokeManageSession` `createDesignerSession` `revokeDesignerSession` |
| `webhooks()` | `list` `create` `update` `delete` `listDeliveries` |
| `workspaces()` | `list` `create` `retrieve` `update` |

Full reference: [docs.seatlayer.io/server-sdk](https://docs.seatlayer.io/server-sdk/install/)

## Related resources

- [Server SDK guide](https://docs.seatlayer.io/server-sdk/install/)
- [Errors, retries and idempotency](https://docs.seatlayer.io/server-sdk/reliability/)
- [Webhook verification](https://docs.seatlayer.io/server-sdk/webhooks/)
- [Server API reference](https://docs.seatlayer.io/server-api/events/)
- [OpenAPI description](https://docs.seatlayer.io/openapi.json)
- [Agent-readable documentation](https://docs.seatlayer.io/llms.txt)
- [SeatLayer GitHub organization](https://github.com/seatlayer)

### Other SeatLayer SDKs

| Surface | Package |
|---|---|
| Browser (vanilla) | [`@seatlayer/js`](https://www.npmjs.com/package/@seatlayer/js) |
| React | [`@seatlayer/react`](https://www.npmjs.com/package/@seatlayer/react) |
| React Native | [`@seatlayer/react-native`](https://www.npmjs.com/package/@seatlayer/react-native) |
| iOS | [`seatlayer-ios`](https://github.com/seatlayer/seatlayer-ios) |
| Android | [`seatlayer-android`](https://github.com/seatlayer/seatlayer-android) |
| Flutter | [`seatlayer`](https://pub.dev/packages/seatlayer) |
| Node.js (server) | [`@seatlayer/server`](https://www.npmjs.com/package/@seatlayer/server) |
| Python (server) | [`seatlayer`](https://pypi.org/project/seatlayer/) |
| PHP (server) | [`seatlayer/seatlayer-php`](https://packagist.org/packages/seatlayer/seatlayer-php) |
| Java (server) | [`io.seatlayer:seatlayer-java`](https://central.sonatype.com/artifact/io.seatlayer/seatlayer-java) |
| Go (server) | [`github.com/seatlayer/seatlayer-go`](https://pkg.go.dev/github.com/seatlayer/seatlayer-go) |
| Ruby (server) | [`seatlayer`](https://rubygems.org/gems/seatlayer) |
| .NET (server) | [`SeatLayer`](https://www.nuget.org/packages/SeatLayer) |

## Development

```bash
mvn verify   # compile, test, and build the main/sources/javadoc jars
```

Publishing to Maven Central is documented in [RELEASE.md](RELEASE.md). Signing lives in a
`release` profile, so an ordinary `mvn verify` needs no GPG key.

## License

MIT
