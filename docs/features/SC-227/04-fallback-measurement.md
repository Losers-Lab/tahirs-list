# SC-227 · Measured fallback quality (Geoapify and geocod.io)

`02-geocoder-alternatives.md` compared terms and quotas. This note measures the
two keyed providers against **HMS's own map pins**, which are the closest thing
to ground truth we have for US rows.

## Method

- 60 HMS establishments inside the four configured metro circles, picked in a
  deterministic pseudo-random order from the 149 in-metro rows with coordinates
  (HMS is US-only, so this measures Dallas/Austin/NYC, not Toronto).
- Query sent: `street, city, state` after the same cleaning the pipeline now
  applies (unit/suite stripped, `Rd` → `Road`, …), i.e. **address only** — no
  business name, which is the form both providers can actually answer.
- Geoapify: `GET /v1/geocode/search?text=…&filter=countrycode:us&limit=1`
  (no `format=json`, so the native GeoJSON shape), one query per row.
- geocod.io: `POST /v1.9/geocode` with a JSON array of the 60 queries.
- Reference point: the HMS directory pin. Distance is great-circle.
- Quota used: 60 Geoapify credits and 60 geocod.io lookups, once.

## Result

| provider | returned a result | ≤ 100 m | ≤ 250 m | ≤ 1 km | median | house number matched |
|---|---:|---:|---:|---:|---:|---:|
| **Geoapify** | 60/60 | 49 | 55 | 56 | **2 m** | 54/60 |
| **geocod.io** | 60/60 | 24 | 34 | 34 | 170 m | 35/60 |

Geoapify's median agreement with the HMS pin is 2 m, i.e. it lands on the same
building. geocod.io returns every address but often at street or range level;
its median is 170 m and only 34 of 60 are within 250 m. Adding the ZIP might
improve geocod.io, but it is still the weaker option and it cannot accept a
business name at all.

## What this means

- **Geoapify is the fallback to implement.** It is measurably accurate on our
  data, allows storage, and its free quota (3,000/day) is 15× a full run.
- **geocod.io is not worth wiring** unless Geoapify's terms fail a legal read;
  Photon plus a precise address check would be no worse on these rows.
- Neither provider replaces the confirmation rule: a fallback result still has
  to carry the house number and street, because a provider can also return a
  street centroid.

## Not measured

- Canadian rows (HMS has none in this sample). Geoapify's Canada coverage
  should be checked against HMA addresses before it is used for Toronto.
- Throughput under the real 1 req/s / 5 req/s pacing, and the 429 behaviour.
- Whether Geoapify's free-plan commercial terms hold for a public directory
  (see the open question in `02-geocoder-alternatives.md`).
