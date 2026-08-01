# Working in this repo

This is the SeatLayer **server** SDK. It talks to the API with a secret key and must never
be usable from a browser.

## Rules

- **No runtime dependencies.** JDK only: `java.net.http.HttpClient`, `javax.crypto.Mac`, and a
  hand-written JSON codec in `Json.java`. Keep it that way —
  a server SDK that drags in a dependency tree is a supply-chain surface for every customer.
- **The public surface is defined upstream** by `workers/api/src/publicApi.ts` in the app repo.
  A method here must map to an operation listed there. Do not wrap internal routes.
- **Method names are the operationIds** from that manifest. Renaming one is a breaking change
  across every SeatLayer server SDK, not just this package.
- **Ergonomics live here, not in the transport.** Things like "capabilities is required" and
  "expectedUpdatedAt is required" are deliberate divergences from the raw API; each one has a
  comment saying why.

## Checks

`mvn verify` compiles, tests, and builds the main/sources/javadoc jars Maven Central requires.
CI runs it on Java 17 and 21.

## The JSON codec

`Json.java` exists so the SDK has no dependencies. It is small but load-bearing — every request
body and response passes through it — so `JsonTest` covers number typing (epoch millis must stay
`Long`, not become `1.75E12`), whole-double encoding (no trailing `.0` on an integer field),
escaping, and malformed input. Extend those tests before touching the parser.
