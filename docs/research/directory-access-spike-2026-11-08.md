# Tier-1 halal certifier directory access spike

**Author:** Hamza (backend/DB) · **Date:** 2026-11-08 · **Consumer:** SC-211 design
**Status:** complete — all three automated sources confirmed reachable; Zabihah confirmed licensed-only
**Method:** direct HTTP with `curl`; no browser, no JavaScript execution, no auth bypass, no scraping of Google/Yelp. Raw responses kept out of the repo.

## Verdict

| Source | Programmatic access | Mechanism | Confidence |
|---|---|---|---|
| HMS | **Yes** | one server-rendered HTML page with `data-*` records + embedded JSON | High |
| HFSAA | **Yes** | the site's own public locator RPC (Supabase, publishable key) | Medium-high (public, undocumented) |
| HMA | **Yes** | open WordPress REST API, one paginated request | High |
| Zabihah | **No, not without a licence** | robots.txt permits cited linking only; `/developers` sells bulk API access | High (negative confirmed) |

A full sample run costs **three HTTP requests** (one per Tier-1 certifier) plus Photon lookups. That is the whole access story; the remaining risk is parser drift, not reachability.

## Access matrix

| | HMS | HFSAA | HMA |
|---|---|---|---|
| Endpoint | `GET hmsusa.org/certified-listing/` | `POST pmolfcjxryccsisyhhwm.supabase.co/rest/v1/rpc/get_public_locator_entries` | `GET hmacanada.org/wp-json/wp/v2/pages?parent=21800&per_page=100&_fields=id,slug,title,content` |
| Status / type | 200 · `text/html` | 200 · `application/json` | 200 · `application/json` |
| Payload | 10,667,397 B | ~276–296 KB | 3,099,946 B |
| Records | 687 unique (370 restaurants) | 313 (248 restaurants) | 76 certified outlets |
| Auth | none | public publishable key (shipped in the locator bundle) | none |
| Pagination | none (one page) | none (one RPC) | none — `X-WP-Total: 76`, `X-WP-TotalPages: 1` |
| Coordinates | yes, embedded JSON (591/687) | yes, per row | no — address only |
| Certificate data | status + expiry (JSON) | certificate ID + status | certification statement |
| robots.txt | `Allow: /`, `Disallow: /api/` — page allowed | Squarespace default; locator host `Allow: /` | `Disallow:` empty — allowed |
| Request cost per run | 1 | 1 | 1 (all content inline) |

## HMS — hmsusa.org

The certified-listing page is plain server-rendered HTML (Kestrel, `Cache-Control: no-store`). Two parse paths, joined on the record id:

1. `div.MagicListing` carries `data-id`, `data-category`, `data-name`, `data-address`, `data-state-name`, `data-city-name`, `data-county-name`, `data-status-name`, `data-created-datetime`, `data-feature-ids`.
2. `<input id="Map_Hidden" value="[…JSON…]">` carries `ID`, `Category`, `Name`, `Latitude`, `Longitude`, `Address`, `Phone`, `Expires`, `StatusName`, `CityName`, `StateName`, `Products` for 591 records.

Counts by category: Restaurants 370 · Retail Store 157 · Slaughter House 74 · Distributor 28 · Processors 20 · Further Processor 19 · Caterers 12 · Misc 7.

**Metro feasibility (restaurants only):** Texas 45, of which the Dallas allowlist holds **42–43** — Plano 9, Richardson 8, Irving 5, Garland 4, Murphy 4, Dallas 2, Frisco 2, Sachse 2, … New York State 33; the NYC allowlist holds **32** once empty-city rows are recovered from the address (13 by `data-city-name`), e.g. `72-55 Kissena Blvd, Flushing, NY 11367`.

**Gotchas:** the 96 records absent from `Map_Hidden` need Photon geocoding; names contain HTML entities (`Sal&#x2019;s`); one 10 MB response should be cached, never re-fetched per row.

## HFSAA — hfsaa.org

`www.hfsaa.org/newyork` renders an empty Squarespace block; the chapter pages embed the official public locator at `hfsaa-public-locator.developer-907.workers.dev` (Cloudflare Worker, `robots: Allow: /`). Its CSP names the Supabase project and its bundle ships the publishable key, so the locator's own read-only RPC is a public endpoint:

```
POST https://pmolfcjxryccsisyhhwm.supabase.co/rest/v1/rpc/get_public_locator_entries
apikey: sb_publishable_D9EXV4Sip6WURhiClGHZCA_YaGOBY-2
{}
→ 200, Content-Range: 0-312/*, 313 rows
```

Fields: `id`, `type`, `name`, `certificate_id` (`R-…` restaurant / `M-…` meat market), `certification_status`, `chapter_name`, `cuisine_type`, `address`, `city`, `state`, `zip_code`, `latitude`, `longitude`, `storefront_number`, `website_url`, `delivery_url`, `business_hours`, `photo_url`, `google_place_id`, `created_at`, `updated_at`.

Chapters: Chicago 131 · Bay Area 42 · **New York 26** · Maryland 19 · Los Angeles 16 · San Diego 15 · Detroit 14 · Pennsylvania 11 · Orlando 8 · Seattle 7. New York splits into 16 restaurants + 10 meat markets; combined with HMS's NYC rows the metro clears 15–25 before dedupe.

**Risk:** the publishable key is rotatable. The library treats a `401` as a signal to re-discover the key from the public locator bundle (`sb_publishable_[A-Za-z0-9_-]+`), then retry once. This is not an auth bypass — it reads the same credential the public page already ships.

## HMA — hmacanada.org

The certified-restaurants page is WordPress with an open REST API. The parent page `hma-certified-restaurants` is id **21800**; its 76 child pages carry the outlet content, so one request returns the whole directory:

```
GET /wp-json/wp/v2/pages?parent=21800&per_page=100&_fields=id,slug,title,content
→ X-WP-Total: 76, X-WP-TotalPages: 1
```

Address, phone, and website live in Elementor list markup, parseable with stable icon anchors:

| Field | Anchor |
|---|---|
| address | `i.fa-map-marker-alt` → `span.elementor-icon-list-text` |
| phone | `i.fa-phone-alt` → `span.elementor-icon-list-text` |
| website | `i.fa-globe` → enclosing `a[href]` |

All 76 rows parse an address (e.g. `1365 Gerrard St E, Toronto, Ontario, M4L 1Z3`). GTA outlets: **40** — Scarborough 7, Brampton 7, Mississauga 6, Toronto 3, Oakville 3, Etobicoke 2, Markham 2, … No coordinates; the page embeds a Google Maps `q=` iframe, which is address text, not a Google API call.

**Gotchas:** follow `X-WP-TotalPages` if the directory grows past 100; Elementor markup is the drift risk; the payload is 3 MB, so fetch the list once and cache it.

## Zabihah — licensed only

`robots.txt` is an explicit policy, not boilerplate: cited, linked AI search is allowed; bulk extraction is disallowed; *"Licensed bulk access is a product — /developers"*. The `/developers` page advertises the Zabihah Developer API (53,000+ places).

**Conclusion:** there is no unlicensed bulk path, and spoofing an allowed crawler user-agent would violate the stated intent. Tier-3 stays a hand-curated, individually cited batch. If a licence is purchased later, it enters through a `certifier-zabihah` module behind the same interface — never as a scraper.

## Ethics and rate policy (implemented in `certifier-core`)

| Rule | Value |
|---|---|
| User-Agent | `TahirsListBot/0.1 (+https://github.com/Losers-Lab/tahirs-list; data-curation)` |
| Rate | ≤ 1 request/second per host, concurrency 1 |
| Retries | exponential 2 s → 4 s → 8 s, honour `Retry-After`, stop after 3 attempts |
| Cache | on-disk snapshot, 24 h TTL, conditional GET where the host supports it |
| Budget | hard cap per run (default 10 requests); a full sample uses 6 (3 robots + 3 data) |
| robots.txt | fetched and obeyed per host before the first real request |
| Offline | a fresh snapshot is served with no network call at all |

## Design impact

- **E-1 and E-2 are closed**, E-3 becomes a confirmed negative: access is not uncertain.
- The new risk is **parser drift**, answered with fixture tests, a `SOURCE_PARSE_FAILED` quarantine, and cached snapshots.
- Each certifier becomes a **standalone library with its own domain model**, plus a translator into the Tahir's List model (D-12).
- Zabihah's licensing path is recorded as D-13; Tier-3 stays manual until a licence exists.
