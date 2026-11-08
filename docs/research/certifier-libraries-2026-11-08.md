# Certifier libraries — verified API surface and field coverage

**Author:** Hamza (backend/DB) · **Date:** 2026-11-08 · **Consumer:** SC-211
**Status:** implemented, 99 new tests green, final live probe passed on the hardened client (2026-11-08)
**Scope:** the three Tier-1 libraries (`certifier-hms`, `certifier-hfsaa`, `certifier-hma`), the shared `certifier-core`, and the `:ingestion` translators

## What was built

```mermaid
flowchart LR
  HMS[HMS] --> LH["certifier-hms<br/>HmsEstablishment"]
  HFSAA[HFSAA] --> LF["certifier-hfsaa<br/>HfsaaEntry"]
  HMA[HMA] --> LM["certifier-hma<br/>HmaCertifiedOutlet"]
  CORE["certifier-core<br/>CertifierHttp · FetchPolicy · snapshots"] -.-> LH
  CORE -.-> LF
  CORE -.-> LM
  LH --> T["ingestion translators"]
  LF --> T
  LM --> T
  T --> SL["SourceListing<br/>DirectoryStandard"]
```

| Module | Own domain model | Depends on |
|---|---|---|
| `:certifier-core` | `CertifierProfile`, `HandCutDeclaration`, `SourceSnapshot`, `CertifierPage` | coroutines, serialization, Ktor client |
| `:certifier-hms` | `HmsEstablishment`, `HmsAddress`, `HmsLocation`, `HmsCategory` | `:certifier-core`, jsoup |
| `:certifier-hfsaa` | `HfsaaEntry`, `HfsaaAddress`, `HfsaaLocation`, `HfsaaEntryType` | `:certifier-core` |
| `:certifier-hma` | `HmaCertifiedOutlet`, `HmaAddress` | `:certifier-core`, jsoup |
| `:ingestion` | `SourceListing`, `SourceRef`, `DirectoryStandard`, `SourceTier` | the three libraries |

**Boundary rule:** no library depends on `:domain` or `:application`. Each translator is the only place a library model becomes a Tahir's List model, so any library can be extracted into its own repository later (D-12).

## Public API

```kotlin
val hms = HmsDirectory(CertifierHttp.createFor(HmsProfile)).certifiedRestaurants()
val hfsaa = HfsaaLocator(CertifierHttp.createFor(HfsaaProfile)).entries(chapter = "New York")
val hma = HmaCertifiedOutlets(CertifierHttp.createFor(HmaProfile)).outlets()

val listings: List<SourceListing> =
    HmsTranslator().translate(hms) +
    HfsaaTranslator().translate(hfsaa) +
    HmaTranslator().translate(hma)
```

Every page carries `records`, `snapshots` (URL, fetched-at, SHA-256, body) and honest `warnings`. Every profile carries `declaredHandCut` and `handCutBasis`; the tier is supplied by the app, never inferred by the library.

## Verified field coverage (live probe, 2026-11-08)

```mermaid
flowchart LR
  subgraph HMS
    H1[certified-listing page] --> HR["370 restaurants<br/>324 coords · 324 expiry"]
    HR --> HG["gaps: 46 without coords<br/>10 without city · no cert id"]
  end
  subgraph HFSAA
    F1[public locator RPC] --> FR["313 entries<br/>312 coords · 313 cert ids"]
    FR --> FG["gaps: no expiry<br/>undocumented API"]
  end
  subgraph HMA
    M1[WordPress REST] --> MR["76 outlets<br/>76 addresses · 63 statements"]
    MR --> MG["gaps: no coords<br/>geocode every row"]
  end
```

| Capability | HMS | HFSAA | HMA |
|---|---|---|---|
| Records | **370 restaurants** (687 all categories) | **313** (248 restaurants) | **76** outlets |
| Source record id | `data-id` | UUID | WordPress page id |
| Certificate id | — | **`R-…` / `M-…`** | — |
| Certification status | `Certified` | `certified` | statement on 63/76 |
| Expiry | **324 rows** | — | — |
| Coordinates | **324/370** | **312/313** | **none published** |
| Street address | 370 | 313 | 76 |
| City | 360 (10 blank; recovered from address) | 313 | 76 |
| County / region | county + state | state + ZIP | province + postal |
| Phone | 324 (map records) | 313 | most outlets |
| Website | — | some | some |
| Opening hours | — | some | — |
| Cuisine | — | some | — |
| Google place id | — | some | — |
| Cross-contamination | **— (not published)** | **— (not published)** | **— (not published)** |
| Hand-cut declaration | profile (research, not a field) | profile (research, not a field) | profile (research, not a field) |
| Brand | — (derive from name) | — (derive from name) | — (derive from name) |
| Chapters / regions | US states | 10 chapters | Canadian provinces |
| HTTP access | 1 GET, 10.7 MB | 1 POST RPC | 1 GET, 3.1 MB |

## What is possible

- A 15–25 row sample per metro: **Dallas 43**, **NYC 58** (HMS 32 + HFSAA 26), **Toronto 28** before dedupe.
- Claim-backed `VERIFIED` for every Tier-1 row, citing the directory URL and, where published, the certificate id or expiry.
- Hand-cut `TRUE` for all three current certifiers — from their own declaration, never from the tier.
- Dedupe by normalized location; cross-source confirmation (HMS + HFSAA) in NYC.
- Honest rejections: a row with no confirmed Photon match is excluded, never given invented coordinates.
- A per-source snapshot with `requestedUrl`, final `url`, `fetchedAt`, and SHA-256, so every claim can cite the exact bytes that were read. The final probe confirmed HMS redirects `/certified-listing/` to `/certified-listing`; both URLs are recorded.

## What is not possible (and why)

| Limit | Consequence |
|---|---|
| HMA publishes no coordinates | Every HMA row must be geocoded from its address. |
| HMS publishes no certificate id | HMS claims cite the listing URL + expiry, not a certificate number. |
| HFSAA publishes no expiry | HFSAA claims carry the certificate id but no expiry date. |
| 46 HMS restaurants are missing from `Map_Hidden` | No directory coordinates; geocoding only. |
| 10 HMS rows have a blank city | City is derived from the address, or left null. |
| No directory publishes cross-contamination evidence | Tier-1 rows are index-qualified by certification policy (D-16), not by a published field. Manual/web rows keep `UNCERTAIN` unless the curator has evidence. |
| No directory publishes a brand id | Brands are resolved/created by name; a name collision is possible and should be reported. |
| Zabihah has no unlicensed bulk path | Tier-3 stays a hand-curated, cited batch (D-13). |
| The HFSAA RPC is undocumented | Treated as public-but-unstable; key rotation is handled, schema drift is not guaranteed. |

## Politeness (implemented, not aspirational)

`CertifierHttp` enforces, in order: fresh snapshot → zero network; HTTPS-only plus the certifier's explicit host allow-list; one wire request at a time; robots.txt before any real fetch (fail-closed); one request at a time per host; 1 req/second minimum; exponential backoff with `Retry-After`; three attempts; a hard 10-wire-request run budget (redirect hops and retries each count); at most three redirects, each re-checked against the allow-list, robots, throttle and budget; a 16 MB response cap; a 24 h on-disk snapshot cache in a user-scoped directory with digest verification and `0700`/`0600` permissions; and an identifying User-Agent (`TahirsListBot/0.1 (+repo URL)`).

Snapshots record the URL that actually served the bytes (`url`) and the URL requested (`requestedUrl`), so a redirect cannot rewrite provenance. A `401` is the only status that triggers HFSAA key re-discovery; a `403` is a hard stop.

A full probe is **six requests** (three robots.txt + three data), and a repeat run is **zero**. An ingestion run adds exactly one Photon pre-flight query before any directory request (D-14); if it fails, the directories are never contacted.

## Tests (99 new, all green)

| Module | Tests | What they pin |
|---|---:|---|
| `certifier-core` | 42 | cache/TTL, ETag/304, retries, `Retry-After`, budget, per-host spacing, robots, snapshot redaction, hand-cut derivation, HTTPS/allow-list, redirects, size cap, cache digest |
| `certifier-hms` | 11 | 20-record fixture, entity decoding, blank-city recovery, coordinate-less row, no-records failure |
| `certifier-hfsaa` | 12 | 13-row fixture, certificate ids, chapter filter, headers, rotated-key re-discovery, 403 hard stop, hostile bundle URL, honest failure |
| `certifier-hma` | 8 | 8-outlet fixture, Elementor anchors, address splitting, pagination, malformed JSON |
| `:ingestion` | 22 | 8 translator/derivation tests; 14 artifact-lifecycle tests: per-run dirs, pending-run abort/archive with unique names, checksum at load, loaded/abandoned markers |
| `:application` | 4 (new) | `CheckGeocoderHealth`: probe resolves, no match, provider failure, cancellation not swallowed |

Run them all in the container:

```bash
./scripts/backend-test.sh test-only
# or, targeted:
docker run --rm -v "$PWD/backend:/app" -w /app -v tahirs-list-gradle-test-cache:/gradle-home \
  -e GRADLE_USER_HOME=/gradle-home eclipse-temurin:21-jdk \
  ./gradlew --no-daemon --project-cache-dir=/tmp/gradle-project-cache \
  :certifier-core:test :certifier-hms:test :certifier-hfsaa:test :certifier-hma:test :ingestion:test
```

## Live probe

```bash
./scripts/certifier-live.sh
```

Actual output on 2026-11-08 (hardened client, final run): cold = 3 data fetches + 3 `robots.txt`; warm = 3 cache hits, 0 network calls. HMS `requestedUrl` is `/certified-listing/`; the served `url` is `/certified-listing` (redirect recorded, not lost).

```
| source | records | with coords | with certificate | sample fields |
| HMS    |     370 |         324 | 324 (expiry)     | id, name, category, address, phone, lat/lng, status, expiry, features |
| HFSAA  |     313 |         312 | 313              | certificate id, chapter, cuisine, address, phone, lat/lng, hours, place id |
| HMA    |      76 |           0 | 63 (statement)   | slug, name, address, phone, website, certification statement |

Dallas  HMS=43
NYC     HMS=32 + HFSAA=26 = 58
Toronto HMA=28

Warnings:
  - 96 HMS establishments have no directory coordinates; geocoding is required
  - 10 HMS establishments have no city in the directory

PROBE OK
```

## Vocabulary rule

The libraries expose exactly one cutting axis: `HandCutDeclaration` = `HAND_CUT`, `NOT_HAND_CUT`, or `UNSPECIFIED`. The derived product boolean is TRUE only when the certifier declares hand-cut, and FALSE for every other Tier-1 declaration — including silence. `NULL` is reserved for rows with no certifier claim (manual rows). There is no counter-category anywhere in code, data, tests, or docs. Verification follows the certifier's tier; hand-cut follows that certifier's own declaration (see `AGENTS.md`, engineering ground rules).

## Next steps (after design approval)

1. Wire the translators into `RunSampleIngestion`, add Photon match-confirmation, and write the artifact set.
2. Generate V23 from `candidates.json`; prove idempotency and the upgrade path.
3. Only then expose claims in the UI (D-8) and revisit the Zabihah licence (Q-7).
