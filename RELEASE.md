# Releasing to Maven Central

`io.seatlayer:seatlayer-java` is published to Maven Central through the Central
Portal by `.github/workflows/release.yml`. A tag push publishes all the way to
Central: `pom.xml` sets `<autoPublish>true</autoPublish>`, so there is no manual
approval step in the Portal.

**A version on Maven Central can never be replaced, edited or deleted.** A bad
release is fixed only by publishing a newer version. The workflow gate (tests, a
tag-versus-`<version>` check and GPG signing) runs before anything is uploaded,
and any failure means nothing is sent. Do not weaken it.

## One-time setup

These are already in place and only need revisiting when a credential changes.

- **Namespace.** `io.seatlayer` is verified in the Central Portal by a DNS TXT
  record on `seatlayer.io`. It covers every artifact under `io.seatlayer`.
- **Signing key.** Central checks each `.asc` signature against the public key on
  the public keyserver network, so the public key must be published (for example
  to `keyserver.ubuntu.com` and `keys.openpgp.org`) and retrievable with
  `gpg --recv-keys` before a release.
- **Actions secrets** used by the workflow:

  | Secret | Purpose |
  | --- | --- |
  | `MAVEN_CENTRAL_USERNAME` | Central Portal user token name |
  | `MAVEN_CENTRAL_PASSWORD` | Central Portal user token password |
  | `MAVEN_GPG_PRIVATE_KEY` | Armored private signing key |
  | `MAVEN_GPG_PASSPHRASE` | Passphrase for the signing key |

  Central has no Trusted Publishing (OIDC) option, so these are the only stored
  release secrets across the SeatLayer server SDKs.

## Release steps

1. Bump `<version>` in `pom.xml` (never a `-SNAPSHOT`).
2. Add a dated entry at the top of `CHANGELOG.md`.
3. Run the gate locally:

   ```bash
   mvn -B clean verify
   ```

4. Merge the release commit to `main`, then tag it and push the tag:

   ```bash
   git tag v0.8.1 && git push origin v0.8.1
   ```

The workflow runs `mvn -B verify`, refuses to publish if the tag and the pom
version disagree, then runs `mvn -B -Prelease deploy`, which signs the jar,
sources jar, javadoc jar and pom and uploads them to the Central Portal.

## Verify

The artifact appears on `repo1.maven.org` within minutes; search indexing can lag
by up to a few hours.

```bash
curl -sI https://repo1.maven.org/maven2/io/seatlayer/seatlayer-java/0.8.1/seatlayer-java-0.8.1.pom
```

Then resolve it from a clean local repository in a scratch project:

```bash
mvn -Dmaven.repo.local=/tmp/m2-clean dependency:get -Dartifact=io.seatlayer:seatlayer-java:0.8.1
```
