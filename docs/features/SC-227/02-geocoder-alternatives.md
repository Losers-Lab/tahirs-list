# SC-227 · Free geocoder alternatives to photon.komoot.io

**Researched:** 2026-11-09. All quotas/terms below were read from the vendor's own
page on that date unless marked *unverified* (third party or search-snippet only).
Prices/limits change without notice; re-check before signup.

Workload assumed: ingestion CLI, **~150–200 forward lookups per run**, a few runs
per month, queries are **"business name + street address"**, US (Dallas, Austin,
NYC) and Canada (Toronto), results cached ~90 days in Postgres.

---

## Bottom line

**Keep Photon as primary; add one keyed fallback behind it, and do not replace
Photon.** Photon's free demo is still the only zero-signup, zero-key, OSM-native
service that handles *business-name + address* free text with global coverage —
but it is a single small server with no SLA, so a run can stall. Self-hosting
Photon is the only option that removes the dependency without a vendor account
(a North-America dump is 15.6 GB compressed), but it is a deployment decision,
not an adapter tweak.

Among genuinely free, keyed options, **Geoapify** is the best fallback for this
workload: 3,000 credits/day, up to 5 req/s, no credit card, free-plan commercial
use allowed with attribution, free-text `name` + address input, and explicit
"cache/store results with no limits". **OpenGeocoding** is the second choice
(50,000 credits/month, 2 req/s, no card, commercial use and permanent storage
explicitly allowed) but is a small single-vendor service with no SLA. For a
**US-only address-shaped backstop**, **geocod.io** (2,500 lookups/day forever,
US/CA, long-term storage allowed) is the safest terms-wise, but it cannot take a
business name in the query and has no Canadian business-name support.

Do **not** plan on OpenCage (free tier is "testing only"), LocationIQ free
(48-hour cache limit), Mapbox (temporary results may not be cached; permanent
geocoding has no free tier), ArcGIS (stored geocodes cost $4/1,000 with no free
allowance) or MapTiler free (non-commercial only).

---

## Comparison table

| Provider | Free quota | Rate limit | Key? | Caching / ToS allowed | Coverage (US/CA) | Licence / attribution | Operational risk |
|---|---|---|---|---|---|---|---|
| **photon.komoot.io** (baseline) | none published ("reasonable limit") | no numeric; community 1 req/s | no | Yes — OSM ODbL, attribution | Global, good US/CA | ODbL; "© OpenStreetMap contributors" | **High**: one small server, 503s, no SLA, may throttle/ban |
| **Nominatim** (nominatim.openstreetmap.org) | none; 1 req/s absolute max | 1 req/s per *application*; recurring scripts **4 req/min**, single thread | no (valid UA/Referer) | Caching **required**; ODbL share-alike | Global, good US/CA | ODbL | **High**: donated servers, bulk explicitly discouraged/banned |
| **geocode.maps.co** (Nominatim-based) | 25,000 demo requests | 5 req/s during demo, then 1 req/s | yes | Not verified | Global (Nominatim) | OSM ODbL (inherited) | Medium; tiny vendor, unclear demo scope |
| **Photon self-host (US / CA / NA dump)** | unlimited (your hardware) | your hardware | no | Yes — ODbL | US 10.6 GB; CA 4.6 GB; NA 15.6 GB (tar.bz2) | ODbL | Low vendor risk; ops burden (Java 21, disk, updates) |
| **Pelias self-host** | unlimited | your hardware | no | Yes — ODbL etc. | Any region you import | ODbL / per-source | High setup: 8 GB RAM min, tens of GB disk, Elasticsearch; `pelias/docker` does not support Windows hosts |
| **openrouteservice (hosted Pelias)** | 1,000 geocodes/day (Standard) | not published per-second | yes | OSM ODbL; storage terms not read | Global | ODbL | Medium; public site had PHP outage at read time |
| **OpenCage** | 2,500 req/day | 1 req/s | yes | Permanent storage allowed, **but free plan is "testing only"** | Global, good US/CA | OSM + others; attribution | Low infra risk, **terms block production use** |
| **LocationIQ** | 5,000 req/day | 2 req/s, 60 req/min | yes | **Free: cache for 48 h only**; "store forever" is for customers | Global, good US/CA | OSM attribution + free-plan backlink | Low infra risk, **terms conflict with 90-day cache** |
| **Geoapify** | 3,000 credits/day | up to 5 req/s | yes | Yes — "cache/store results with no limits"; OSM attribution; free plan needs Geoapify attribution | Global, good US/CA | ODbL + Geoapify attribution | Low–medium; commercial free use wording inconsistent (pricing FAQ says yes, ToS says "with limitations") |
| **MapTiler** | 100k API req/month; 1k search sessions/month | not published | yes | Permanent result storage allowed | Global | OSM + MapTiler | Low infra risk; **free plan is non-commercial/testing only** |
| **Mapbox** | Temporary geocoding: 100k req/month | not published on pricing page | yes (access token) | **Temporary results may not be cached**; Permanent = $5/1,000, no free tier, card required | Global, good US/CA | Mapbox proprietary + OSM | Low infra risk; v6 geocoding **no longer returns POIs** |
| **HERE** | 30,000 geocode transactions/month | not published per-second; 429/503 on quota | yes | Storage terms not read — **unknown** | Global, good US/CA | HERE proprietary | Low infra; credit card required at registration (vendor blog) |
| **ArcGIS / Esri** | 20,000 non-stored geocodes/month | not published | yes | Stored results need `forStorage=true`: **$4/1,000, zero free** | Global, good US/CA | Esri proprietary | Low infra; address-only, no business names |
| **TomTom** | 20,000 geocoding req/month | ~5 req/s (third-party, *unverified*); 429 over QPS | yes | `storeResult` flag deprecated; storage terms **unknown** | Global, good US/CA | TomTom proprietary | Low infra; geocoding explicitly returns **no POIs** |
| **geocod.io** | 2,500 lookups/day, forever | 1,000 lookups/min | yes | Yes — ToS allow long-term storage | US, CA, MX, AU, NZ | Mixed public/proprietary; commercial ok | Low–medium; **address-only**, no business names |
| **US Census Geocoder** | free, no published cap | none published; 10,000/batch file | no | Public domain; caching fine | **US/PR only** | US Government public domain | Medium (shared gov service, no SLA); **address-only** |
| **NYC Geoclient** | free | 2,500 req/min, 500,000/day | yes (free app id/key) | NYC open data; storage terms not read | **NYC only** | NYC open data | Medium; **address/intersection/blockface only** |
| **Texas A&M GeoServices** | 2,500 credits/month; free "partner" refills | not published | yes (free account) | Explicit: may store data; commercial integration allowed | **US addresses only** | TIGER/MAF + proprietary | Medium; **address-only**, no Canada |
| **Smarty** | 42-day trial, 1,000 lookups | not published | yes | Address data keepable; **rooftop geocodes only while subscribed** | US only | USPS/proprietary | Low infra; **not an ongoing free tier** |
| **OpenGeocoding** | 50,000 credits/month | 2 req/s | yes | Yes — "keep your results"; commercial allowed | Global; vendor claims 97.7% on its US/CA test subset | OSM ODbL + Overture + OpenAddresses + GeoNames | **Medium–high**: very small vendor, no SLA, vendor-run benchmark |
| **Stadia Maps** | 200,000 credits/month = 10,000 geocodes (20 credits each) | not published | yes | **Free plan: temporary storage only**; persistent needs Standard ($80/mo) | Global | OSM + others | Low infra; **free plan non-commercial, temp storage** |
| **Geocode Earth (Pelias)** | 14-day trial only | 10 req/s on paid Lite | yes | Explicit: store as long as needed | Global | ODbL | Low infra; **no ongoing free tier** |

### Fit for "business name + street address"

| Provider | Accepts business name in the query? | Notes |
|---|---|---|
| Photon / Photon self-host | **Yes** | Built for free-text POI + address search |
| Nominatim / maps.co | Yes | Free-text search covers names and addresses |
| Geoapify | **Yes** (`name=` or free `text=`) | Best keyed fit |
| OpenGeocoding | Yes | Free-text forward geocoding |
| OpenCage / LocationIQ / ORS / Pelias | Yes | Free-text geocoders |
| Mapbox | **No POI data in v6** | Would need Search Box API (different product/billing) |
| TomTom Geocoding | **No** | "No POIs will be returned"; Places Search is a separate, smaller quota |
| HERE | Likely yes (Geocoding & Search) | Needs a live test |
| ArcGIS, Census, Geoclient, geocod.io, TAMU, Smarty | **No** — address-only | Adapter would have to drop the business name and query the street address alone |

---

## Per-provider notes and evidence

### 1. Photon (komoot demo) — baseline

- Terms: "You can use the API for your project, but please be fair – extensive
  usage will be throttled. We do not guarantee for the availability and usage
  might be subject of change in the future."
  <https://photon.komoot.io/> (read 2026-11-09)
- README: demo server, no guarantees; heavier users told to self-host.
  <https://github.com/komoot/photon#demo-server> (read 2026-11-09)
- No numeric quota or rate-limit header is published. Community convention is
  1 req/s; the operator has asked a 1 req/s client to slow down when it was 60%
  of all traffic (`dawarich#614`, cited in `01-request-policy.md`).
- **No other documented public Photon instance exists.** The project lists only
  the komoot demo; anything else is an ad-hoc/unvetted deployment. There is no
  federation or mirror list to fall back on.

### 2. Nominatim (OSMF public server)

- Policy: absolute maximum **1 request/second**; valid HTTP Referer or
  User-Agent required; attribution required; ODbL.
- **Bulk geocoding is explicitly discouraged.** "periodic requests from apps are
  considered bulk geocoding"; scripts running longer than a day or at regular
  intervals are limited to **4 requests per minute**, single thread, one machine,
  and results **must be cached**. Repeated identical queries may be blocked.
- Autocomplete and "systematic queries" are forbidden; reselling geocoding is
  forbidden.
  <https://operations.osmfoundation.org/policies/nominatim/> (read 2026-11-09)
- Practical read for us: 150–200 lookups at 4 req/min ≈ 40–50 minutes — slower
  than Photon at 1 req/s, and legally in the "discouraged" bucket. Not a good
  automatic fallback for an ingestion run.

### 3. Public Nominatim mirrors / keyless-ish

- `geocode.maps.co`: free demo account **25,000 requests at 5 req/s, then limited
  to 1 req/s**; API key required; Nominatim-compatible endpoints.
  <https://geocode.maps.co/plans/> (read 2026-11-09). Whether the 25,000 is
  one-time or monthly, and whether storage is allowed, is **not stated** — ask
  support before relying on it.
- `nominatim.terravo.eu` is Europe-only (listed on the OSM wiki), so it is not
  useful for US/CA. Geofabrik's Nominatim is a paid commercial service.
  <https://wiki.openstreetmap.org/wiki/Nominatim#Alternatives_.2F_Third-party_providers>
  (read 2026-11-09)

### 4. Self-hosted Photon (recommended if the dependency must go)

Evidence: <https://github.com/komoot/photon> and
<https://download1.graphhopper.com/public/> (read 2026-11-09).

- Requires **Java 21+**; runs from a prebuilt jar, embedded OpenSearch by default.
- Prebuilt Photon 1.x dumps (tar.bz2), updated weekly:
  - USA: **10.6 GB**
  - Canada: **4.6 GB**
  - North America (US+CA+MX+Central America/Caribbean): **15.6 GB**
  - Planet: **63.1 GB**
- Disk: the README advises swapping directories atomically for updates, so budget
  **≈2× the dump size**; unpacked data is larger than the compressed dump.
- RAM: the README recommends **64 GB for planet**; it explicitly says running
  with less is possible and gives `-Xmx8G` as an example heap. **No official RAM
  figure is published for country extracts** — 4–8 GB is a plausible starting
  point but must be measured (treat as unverified).
- This is the only option that removes vendor-account and ToS-change risk while
  keeping the exact same query semantics (business name + address).

### 5. Pelias (self-host and hosted)

- Self-host: MIT, Elasticsearch-based, Docker Compose; **≥8 GB RAM** minimum,
  "scripts can easily download tens of GB". Full planet: ~600M documents,
  ~450 GB in Elasticsearch, ~16 h on 36 cores. `pelias/docker` explicitly says
  **Windows is not supported** (relevant: our host is Windows; we would need a
  Linux container/VM).
  <https://github.com/pelias/docker> and
  <https://github.com/pelias/documentation/blob/master/full_planet_considerations.md>
  (read 2026-11-09)
- Hosted Pelias:
  - **Geocode Earth**: 14-day free trial, no card, then Lite **$100/month** for
    150k requests, 10 req/s. Explicitly allows storing results.
    <https://geocode.earth/> (read 2026-11-09)
  - **openrouteservice** (Pelias under the hood): Standard plan free, **1,000
    `/geocode` requests/day**, 2,000 `/directions`/day (ORS staff, 2025).
    <https://ask.openrouteservice.org/t/pricing-plan-from-the-api/5806>
    (read 2026-11-09). Rate limit per second is not published. Note: at read time
    `openrouteservice.org` itself displayed a shutdown notice for PHP issues,
    while docs/account subdomains worked — a reminder of small-operator risk.

### 6. Commercial free tiers

**OpenCage** — 2,500 requests/day, 1 req/s, no credit card, "store data
permanently", but the free plan is **"testing only"**; production use requires a
paid plan; inactive trials deleted after 3 months; one trial per person.
<https://opencagedata.com/pricing> (read 2026-11-09).

**LocationIQ** — free: **5,000 requests/day, 2 req/s, 60 req/min**, commercial
use allowed **with a visible "Search by LocationIQ.com" backlink**. Caching FAQ:
"You can store response data forever. If you have a free account, you can cache
API request-response pairs for **upto 48 hours**." Read conservatively: the free
tier does **not** grant our 90-day cache.
<https://locationiq.com/pricing> (read 2026-11-09).

**Geoapify** — free: **3,000 credits/day, up to 5 req/s**, no card. One geocoding
request = 1 credit. Free plan: commercial use allowed in production per the
pricing FAQ, provided you stay within quotas and show **Geoapify attribution**
plus OSM attribution; the Terms page says free commercial use is allowed "with
some limitations" and invites contact — a small conflict to resolve in writing.
Docs state "Cache/store results with no limits" (Places/Batch pages) and the
geocoding response carries OSM `attribution`/`license` fields.
<https://www.geoapify.com/pricing/>, <https://www.geoapify.com/terms-and-conditions/>,
<https://apidocs.geoapify.com/docs/geocoding/forward-geocoding/> (read 2026-11-09).

**MapTiler** — free: 100k API requests/month and 1k search sessions/month, no
card; but "Suitable for testing, PoC, prototyping, personal, or **non-commercial
use**" and the geocoding page says "Utilize the free tier in your **non-commercial**
projects". Permanent storage of results is allowed for most use cases.
<https://www.maptiler.com/cloud/pricing/>,
<https://www.maptiler.com/cloud/geocoding/> (read 2026-11-09). Not usable for a
production directory on the free plan.

**Mapbox** — Temporary Geocoding: **100,000 requests/month free**, then $0.75/1k.
Permanent Geocoding (the only mode that may be cached): **$5.00/1,000, no free
tier**, requires contacting sales; Permanent storage requires a valid credit card
on file. Temporary results "are not allowed to be cached". Geocoding v6 **no
longer provides POI data**.
<https://www.mapbox.com/pricing>, <https://docs.mapbox.com/api/search/geocoding/>
(read 2026-11-09).

**HERE** — Base Plan: **30,000 geocode transactions/month free**, $0.88/1k after
(third-party figure; vendor page shows 0–30,000 free). Registration requires a
credit card (HERE developer blog: "The Base Plan requires a credit card during
registration, but users will not be charged unless they exceed the free tier").
Quota exceeded returns **HTTP 429/503**; sudden surges can be throttled even
below quota. No per-second limit published. Storage terms not read.
<https://www.here.com/get-started/pricing>,
<https://docs.here.com/policies/docs/limits-and-quotas> (read 2026-11-09);
card claim from <https://www.here.com/developer/blog/how-to-get-started-with-here-platform>
(vendor page, read via search snippet 2026-11-09 — *verify at signup*).

**ArcGIS / Esri** — **20,000 free geocodes/month** (non-stored), then $0.50/1k.
Storing results requires `forStorage=true` and is billed at **$4 per 1,000 with
zero free allowance**, including batch. Address geocoding only.
<https://location.arcgis.com/pricing/> (read 2026-11-09).

**TomTom** — **20,000 geocoding requests/month free**, no credit card. Over-QPS
returns **429**; a commonly cited limit is **5 req/s** (third party, *unverified* —
TomTom's pricing page does not publish QPS). Geocoding "No POIs will be returned".
`storeResult` was deprecated in 2018; storage terms are not stated on the pricing
page — legal read needed.
<https://docs.tomtom.com/pricing>,
<https://docs.tomtom.com/geocoding-api/documentation/tomtom-maps/v1/geocode>
(read 2026-11-09).

**Stadia Maps** — free: 200,000 credits/month; forward geocoding costs 20 credits,
so **10,000 geocodes/month**. Free plan: **commercial use not allowed** and
**temporary storage only**; persistent storage starts at Standard ($80/mo).
<https://stadiamaps.com/pricing/> (read 2026-11-09).

**Smarty** — 42-day trial with **1,000 lookups**, no card; then paid (12k
lookups/year from $552). US-only; rooftop geocoding data "can only be stored as
long as you have an active US Rooftop geocoding subscription". Not an ongoing
free tier.
<https://www.smarty.com/pricing/us-address-verification> (read 2026-11-09).

**OpenGeocoding** — free: **50,000 credits/month, 2 req/s**, no card; commercial
use allowed; "Keep your results… No 30-day deletion rules". Data: OSM (ODbL),
Overture, OpenAddresses, GeoNames (CC BY 4.0). Vendor publishes a benchmark
claiming 97.7% pass on a 1,947-query US/Canada subset — *vendor-run, not
independent*. Very small operator (Mokto, Denmark), no SLA on free.
<https://opengeocoding.com/> (read 2026-11-09).

### 7. US-only government / address-only options

**US Census Geocoder** — free, **no key**, no published rate limit; single-record
and batch (max **10,000 records/file**); **US, Puerto Rico and US Island Areas
only**; structured address input only (street + city/state/zip); benchmark
MAF/TIGER. Public domain.
<https://geocoding.geo.census.gov/geocoder/Geocoding_Services_API.html>
(read 2026-11-09).

**NYC Geoclient** — free account + app id/key; guidelines **2,500 req/min,
500,000 req/day**; interface to DCP's Geosupport; supports address, intersection,
blockface — **NYC only**, no business-name or Canada.
<https://api-portal.nyc.gov/>, usage guidelines mirrored at
<https://austensen.github.io/geoclient/reference/geoclient-package.html>
(read 2026-11-09).

**Texas A&M GeoServices** — every registered user gets **2,500 credits/month
free**; a free "partner" program refills 2,500-credit batches on request. Terms
explicitly allow commercial integration and storing the data. US street addresses
only (Census/TIGER-based); Canadian coverage is not documented — *treat as
US-only*. Address input, not business names.
<https://geoservices.tamu.edu/Pricing/Free/>, <https://geoservices.tamu.edu/Pricing/>
(read 2026-11-09).

**geocod.io** — **2,500 lookups/day free, forever**; no card until you exceed it;
rate limit **1,000 lookups/min**; coverage **US, Canada, Mexico, Australia, NZ**;
single/batch/list endpoints; their pricing page states the ToS "allow you to
store results long-term". Address/coordinate lookups, not business-name search.
<https://www.geocod.io/pricing> (read 2026-11-09).

### 8. Not viable as geocoders

- **Wikidata / OSM Overpass / Nominatim `/details`**: these are data queries, not
  forward geocoders. Overpass has its own fair-use limits and is unsuitable for
  address lookup. Say so rather than trying to bend them.
- **Google Geocoding**: requires a billing account/credit card; excluded by the
  zero-cost constraint (and not researched further).

---

## Recommended integration shape

### What the existing port requires

`GeocoderPort` is one method:

```kotlin
interface GeocoderPort { suspend fun geocode(address: String): GeocodedPlace? }
```

`GeocodedPlace(location, displayName, providerRef)` — only a single optional
provider reference, no provider name. `PhotonGeocoder` implements the port with
its own Ktor CIO client, `Mutex` pacing (`minInterval = 1 s`), bounded retries on
408/429/5xx + IO, `Retry-After` clamping, and Photon-specific GeoJSON parsing.
`GeocodingGraph.upstream()` builds it from `APP_GEOCODER_BASE_URL`; the default
binding is `CachingGeocoder(upstream, store, ttl = 90 days, provider = "photon")`.

### A second adapter needs

1. **A separate class per provider** (e.g. `GeoapifyGeocoder`), not a
   parameterised Photon parser — response shapes differ:
   - Geoapify: `GET https://api.geoapify.com/v1/geocode/search?text=…&limit=1&format=json&apiKey=…`
     → `results[0].lat`/`.lon`, `.formatted`, `.place_id`.
   - OpenGeocoding: `GET /v1/search?text=…` → JSON with `features`/`geometry`
     (GeoJSON-like; verify exact shape at signup).
2. **Config, not hardcoding.** Today only `APP_GEOCODER_BASE_URL` exists
   (`McpSettings.geocoderBaseUrl`). Add: provider selector, a **secret** API key
   env var, and per-provider pacing/attempt settings. Photon stays at 1 req/s;
   Geoapify can safely run at 5 req/s (0.2 s), which is why it "tolerates more
   load". Validate HTTPS in `ConfigValidation` as today; never log the key (the
   key goes in the query string for Geoapify — do not log full URLs).
3. **Provider-specific retry classes:**
   - Retryable: 408, 429, 500, 502, 503, 504, IO/timeout (same set as Photon).
   - Fatal, do **not** retry and do **not** fall through to another keyed
     provider on bad credentials: 401/403 (bad/revoked key), 402 (Smarty-style
     quota exhausted), 400 (malformed query). A revoked key should surface as a
     config error, not silently burn the other provider's quota.
   - `200` with an empty result array = **null** (genuine no-match), not a
     failure; never fall back on null.
4. **A fallback decorator** implementing `GeocoderPort`:
   `FallbackGeocoder(primary, secondary)` — try primary; on `GeocodingException`
   only, call secondary; if both fail, throw the primary's exception (or an
   aggregate). Keep cancellation untouched, as `PhotonGeocoder` already does.
   Place it **inside** `CachingGeocoder` so a fallback result is cached:
   `CachingGeocoder(FallbackGeocoder(photon, geoapify), store, ttl)`.
5. **Fix provenance before adding a fallback.** `CachingGeocoder` currently
   hardcodes `provider = "photon"`; if Geoapify answers, the cached row would be
   mislabelled. Either let the fallback expose which delegate answered, or put
   `CachingGeocoder` inside each adapter. Without this, audit/dedupe data is
   wrong.
6. **Headers:** `User-Agent: TahirsListBot/0.1 (+repo)` on every provider
   (required by Nominatim, polite elsewhere); `Accept: application/json`; auth
   via query param (`apiKey`) or header (`Authorization: Bearer …`) depending on
   provider. No browser CORS features needed — this is server-side.
7. **Health probe:** `CheckGeocoderHealth` receives the *upstream* binding. With
   a fallback, decide explicitly whether the probe checks Photon only (so an
   outage is visible in metrics while runs still succeed) or the fallback chain
   (so "ingestion can run" is the health signal). Recommendation: probe the
   primary and emit a separate "running on fallback" warning, otherwise a
   permanent Photon outage becomes invisible.
8. **Query shape caveat:** the CLI currently passes a single "name + address"
   string. Address-only providers (geocod.io, Census, TAMU, Geoclient) cannot use
   that string well; a fallback to them would need street-address-only input
   (or a second, structured method on the port). Prefer a fallback that accepts
   free text, or accept degraded matches in exchange for a lower US-only quota.

---

## Still unknown / needs a signup or legal read

1. **Geoapify free-plan commercial use** — pricing FAQ says yes with attribution;
   ToS says "with some limitations" and invites contact. Get written confirmation
   for a public directory before making it the fallback.
2. **HERE** — exact free quota wording and rate limit on the live pricing page
   (JS-rendered; read via search snippet), whether a card is mandatory today, and
   whether results may be stored/redisplayed. Needs account + legal read.
3. **TomTom** — per-second QPS (5 req/s is third-party only) and storage rights
   after `storeResult` was retired.
4. **ArcGIS** — whether the free tier requires a card, and whether "not stored"
   still permits transient caching; stored geocodes are $4/1k with no free
   allowance, so this is likely disqualifying anyway.
5. **MapTiler** — whether a "commercial-ish" non-profit/directory could use the
   free plan; current wording says non-commercial, so assume no.
6. **LocationIQ** — the free-tier caching sentence is self-contradictory
   ("store forever" vs "free: up to 48 hours"); do not rely on free-tier caching
   until support confirms in writing.
7. **geocode.maps.co** — demo quota scope (one-time vs monthly), storage rights,
   rate-limit behaviour after the demo.
8. **Texas A&M** — Canadian coverage (probably none) and whether 2,500
   credits/month is enough for 2–3 runs of ~200 lookups (it is, numerically:
   400–600/month).
9. **OpenGeocoding** — company viability, data-refresh cadence, SLA (none on
   free), and whether the vendor benchmark's US/Canada 97.7% holds for
   restaurant-name queries (the corpus is not public per-country).
10. **ODbL share-alike** — cached coordinates + display in a directory: confirm
    whether the "Produced Work" attribution route is sufficient, or whether the
    cache counts as a "Derivative Database" requiring share-alike. This applies to
    Photon, Nominatim, Geoapify and OpenGeocoding alike; it is a legal read, not
    an engineering one.
11. **Key rotation / secrets** — no key exists today. Decide where the key lives
    (env/secret store), how it is rotated, and how a 401 is surfaced (see §4.3).

---

## Method note

Vendor pages were fetched directly on 2026-11-09 (pricing, terms, API reference,
download indexes). Third-party numbers are labelled *unverified*; the HERE
credit-card claim and the TomTom 5 req/s figure come from vendor-blog/search
snippets rather than a directly rendered pricing page and should be confirmed at
signup. Quotas not published by a vendor are recorded as "not published" rather
than estimated.
