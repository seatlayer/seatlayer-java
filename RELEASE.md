# Releasing to Maven Central

Maven Central is the strictest registry SeatLayer publishes to, and the only one with a
**manual human verification step that has to clear before the first publish can happen at
all**. npm, PyPI, RubyGems and NuGet let you create an account and push within minutes.
Central does not: you must prove you control the `io.seatlayer` namespace, and Sonatype
reviews that proof. Budget for the wait — see step 2.

Everything below that is a repository concern is already done. What remains is
account-, key- and DNS-shaped, and only the owner can do it.

---

## What is already satisfied in this repo

Do not redo these; they are wired into `pom.xml` and verified by `mvn verify`.

| Central requirement | Where it is handled |
| --- | --- |
| `groupId` / `artifactId` / `version`, non-SNAPSHOT | `pom.xml` — `io.seatlayer:seatlayer-java:0.1.0` |
| `name`, `description`, `url` | `pom.xml` |
| `licenses` (name + url) | `pom.xml` — MIT |
| `developers` | `pom.xml` |
| `scm` — `connection`, `developerConnection`, `url` | `pom.xml` |
| Main jar | `maven-jar-plugin`, default lifecycle |
| `-sources.jar` | `maven-source-plugin`, `attach-sources` |
| `-javadoc.jar` | `maven-javadoc-plugin`, `attach-javadocs` |
| Javadoc actually builds under strict doclint | `<doclint>all,-missing</doclint>`, `failOnError=true` |
| `.asc` signature on every artifact | `maven-gpg-plugin` in the `release` profile |
| Upload transport | `central-publishing-maven-plugin`, `extensions=true` |

---

## 0. Pre-flight, in the repo

```bash
mvn -B clean verify
```

Must end `BUILD SUCCESS` with 48 tests passing and three jars in `target/`.

Then flip the changelog heading in `CHANGELOG.md` from `## 0.1.0 — unreleased` to
`## 0.1.0 — <publish date>`, and commit. The version on Central is permanent, so the
changelog entry describing it should not say "unreleased".

---

## 1. Central Portal account

1. Sign in at <https://central.sonatype.com> (GitHub or Google SSO is fine).
2. Account → **Generate User Token**. This returns a `<username>` / `<password>` pair.
   These are *not* your login credentials — they are a scoped publishing token, and they
   are the only thing that belongs in `settings.xml`.

Keep the token. Regenerating it invalidates the previous one.

---

## 2. Namespace verification — THE HUMAN STEP, AND THE SLOW ONE

Central will not accept an artifact in a namespace you have not proven you control.
Nothing currently exists under `https://repo1.maven.org/maven2/io/seatlayer/` (verified
404), so this is a first-time claim and there is no shortcut.

**We publish under `io.seatlayer`, which is verified by DNS.**

In the Portal: **Namespaces → Add Namespace → `io.seatlayer`**. The Portal shows a
verification key, something like `abc123xyz`.

Add it as a TXT record on the apex of the domain:

```
seatlayer.io.   TXT   "abc123xyz"
```

The domain is on Cloudflare, so this is: DNS → Records → Add record → type TXT, name `@`,
content the verification key. Then press **Verify Namespace** in the Portal.

Confirm the record is actually visible before pressing verify, or the check fails and you
wait again:

```bash
dig +short TXT seatlayer.io
```

**Wall-clock cost.** Cloudflare publishes TXT records in well under a minute, so the DNS
side is effectively instant. The Portal's own check is usually minutes; first-time
namespace claims are sometimes queued for Sonatype staff review, which is where the
multi-hour-to-a-business-day tail comes from. Start this step first — it is the only one
that can block for a day, and every other step below takes minutes.

Once verified, the namespace covers `io.seatlayer` and everything under it, so the other
JVM artifacts SeatLayer may publish later need no further verification.

### Why `io.seatlayer` and not `io.github.seatlayer`

`io.github.<user>` is verified by creating a temporary public GitHub repo whose name is
the verification code — no DNS, no review queue, typically verified in under a minute.
It is genuinely cheaper *in setup*.

It is still the wrong choice here:

- SeatLayer owns `seatlayer.io`, so the DNS proof is available at zero cost beyond one
  TXT record the owner can add in the Cloudflare dashboard in about a minute.
- `io.seatlayer` is the coordinate every other SeatLayer surface already advertises. The
  README, the docs site and the published `pom.xml` all say `io.seatlayer`. Changing it
  to `io.github.seatlayer` means the install snippet no longer matches the brand.
- **The groupId is permanent in practice.** A published artifact's coordinates can never
  be changed, only abandoned and re-published under a new groupId, orphaning everyone on
  the old one. Picking the cheap namespace now to save ten minutes buys a migration later.

The cost difference is one DNS record versus one throwaway repo. Pay the DNS record.

---

## 3. GPG key — generate it AND publish it to a keyserver

**This is the step people miss.** Signing locally is not enough. Central fetches your
*public* key from the public keyserver network to check the signature. A perfectly valid
signature from a key nobody can find is rejected with a validation error that reads like a
signing failure, and the usual reaction is to re-sign rather than to distribute the key.

Generate the key:

```bash
gpg --full-generate-key
# RSA and RSA, 4096 bits, no expiry (or a long one you will actually track),
# real name + an @seatlayer.io address
```

Find its id:

```bash
gpg --list-secret-keys --keyid-format=long
# sec   rsa4096/A1B2C3D4E5F6A7B8 2026-08-04 [SC]
#                 ^^^^^^^^^^^^^^^^ the key id
```

**Publish the public half** — do all of these, they do not fully sync with each other:

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys A1B2C3D4E5F6A7B8
gpg --keyserver keys.openpgp.org     --send-keys A1B2C3D4E5F6A7B8
gpg --keyserver pgp.mit.edu          --send-keys A1B2C3D4E5F6A7B8
```

Then prove it is actually retrievable from somewhere other than your own machine, before
you rely on it:

```bash
gpg --keyserver keyserver.ubuntu.com --recv-keys A1B2C3D4E5F6A7B8
```

Propagation is usually seconds to a few minutes. If you publish and immediately deploy,
you can lose to that race — wait for a successful `--recv-keys` first.

Back up the secret key somewhere durable. Losing it does not invalidate what is already
published, but it means future releases are signed by a different key.

---

## 4. Credentials — `~/.m2/settings.xml`, never this repo

No secret goes in the repository. The `release` profile reads the passphrase from a
property or the environment; the token lives in your user-level settings.

`~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <!-- id MUST equal <publishingServerId> in pom.xml -->
      <id>central</id>
      <username>TOKEN_USERNAME_FROM_STEP_1</username>
      <password>TOKEN_PASSWORD_FROM_STEP_1</password>
    </server>
  </servers>

  <profiles>
    <profile>
      <id>gpg</id>
      <properties>
        <gpg.keyname>A1B2C3D4E5F6A7B8</gpg.keyname>
        <gpg.passphrase>YOUR_KEY_PASSPHRASE</gpg.passphrase>
      </properties>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>gpg</activeProfile>
  </activeProfiles>
</settings>
```

`chmod 600 ~/.m2/settings.xml`.

Prefer not to keep the passphrase on disk at all? Drop the `gpg` profile above and pass it
per-invocation instead — the plugin also honours `MAVEN_GPG_PASSPHRASE`:

```bash
export MAVEN_GPG_KEY=A1B2C3D4E5F6A7B8
read -rs MAVEN_GPG_PASSPHRASE && export MAVEN_GPG_PASSPHRASE
```

Maven can also encrypt the token password (`mvn --encrypt-password`) if you would rather
not have it in cleartext.

---

## 5. Publish

Confirm signing works and the `.asc` files are produced **before** uploading anything:

```bash
mvn -Prelease clean verify
ls target/*.asc
```

You should see four signatures — jar, sources, javadoc, and the pom. If `target/*.asc` is
empty, the `release` profile did not activate or GPG did not run; fix that now, because
Central rejects an unsigned bundle.

Then, the release:

```bash
mvn -Prelease clean deploy
```

That is the whole publish command. It builds, tests, signs, bundles and uploads, and
because `waitUntil=validated` it blocks until Central has finished validating and reports
any rejection to your terminal rather than silently exiting 0.

**It does not go live yet.** `autoPublish` is `false` on purpose. The deployment lands in
the Portal as **VALIDATED** and waits. Go to <https://central.sonatype.com> →
**Deployments**, check the artifact list and the coordinates, and press **Publish**.

Up to that button, a deployment can be dropped and redone freely. After it, see step 7.

Sync to `repo1.maven.org` takes a few minutes; appearing in the search UI can take a few
hours longer. Both are normal — the artifact is resolvable well before search finds it.

---

## 6. Verify it resolves from a clean local repository

Do not verify against your own `~/.m2`; it already has the artifact from the local build
and will succeed regardless of whether the publish worked. Point Maven at an empty repo:

```bash
mvn dependency:get \
  -Dartifact=io.seatlayer:seatlayer-java:0.1.0 \
  -Dmaven.repo.local=/tmp/central-check \
  -DremoteRepositories=central::::https://repo1.maven.org/maven2
```

Then confirm the classified artifacts and signatures are all there too:

```bash
mvn dependency:get -Dartifact=io.seatlayer:seatlayer-java:0.1.0:jar:sources \
  -Dmaven.repo.local=/tmp/central-check
mvn dependency:get -Dartifact=io.seatlayer:seatlayer-java:0.1.0:jar:javadoc \
  -Dmaven.repo.local=/tmp/central-check
curl -sI https://repo1.maven.org/maven2/io/seatlayer/seatlayer-java/0.1.0/seatlayer-java-0.1.0.jar.asc \
  | head -1
```

Finally, the thing that actually matters — a consumer can compile against it. In an empty
directory with the README's install snippet as the only dependency, `mvn compile` on a
one-line file that does `new SeatLayer("sk_test_x")` should resolve and build.

---

## 7. What is IRREVERSIBLE

Read this before pressing **Publish**.

- **A released version can never be replaced, edited or deleted.** Not by you, not by
  Sonatype support. `io.seatlayer:seatlayer-java:0.1.0` is that content, permanently. This
  is deliberate: the entire JVM ecosystem assumes Central coordinates are immutable, and
  build reproducibility depends on it.
- **A bad release is fixed only by superseding it** — publish `0.1.1`. The broken `0.1.0`
  stays downloadable forever. You can mark it deprecated in documentation; you cannot
  remove it.
- **The groupId is effectively permanent too.** Once consumers depend on
  `io.seatlayer:seatlayer-java`, changing the coordinate strands every one of them on an
  artifact that stops receiving updates.
- **Before the Publish button, nothing is committed.** A VALIDATED deployment can be
  dropped in the Portal with no trace. That gap is the entire safety margin, which is why
  `autoPublish` is `false` in `pom.xml` — do not set it to `true` for convenience.

So: check the coordinates, the version, and the contents of the three jars in the Portal's
deployment view. Then publish.

---

## Subsequent releases

1. Bump `<version>` in `pom.xml`, update `CHANGELOG.md`, commit, tag.
2. `mvn -Prelease clean deploy`
3. Approve in the Portal.

Namespace verification and key distribution are one-time. Only step 5 onward repeats.
