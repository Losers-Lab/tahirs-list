# SC-227 · Photon request policy

## What komoot actually publishes

There is **no numeric rate limit**. The demo-server terms are deliberately
qualitative:

> You are welcome to use the API for your project as long as the number of
> requests stay in a reasonable limit. Extensive usage will be throttled or
> completely banned. We do not give guarantees for availability and reserve the
> right to implement changes without notice.
> — <https://photon.komoot.io/>

The repository says the same and points heavier users at self-hosting
([komoot/photon README, “Demo server”](https://github.com/komoot/photon#demo-server)).
The Photon administrator has described the public service as “one tiny server”
and asked an application to reduce traffic when it accounted for **60% of all
requests** — even though that client had already limited itself to **1 req/s**
([dawarich#614](https://github.com/Freika/dawarich/issues/614)).

The de-facto community convention is therefore **one request per second, one in
flight**: the CRAN `photon` package (“For the public API … 1 request per second”)
and the maintainer’s own guidance both point that way. There is no published
burst allowance and no rate-limit headers on a 200 response; a 503 can arrive
without `Retry-After`.

## Policy applied in `PhotonGeocoder`

| Rule | Value | Why |
|---|---|---|
| Concurrency | 1 in flight (`Mutex`) | The demo server is one small instance; bursts are what get clients throttled |
| Spacing | ≥ 1 s between requests, default | Community/CRAN convention; comfortably below “extensive” |
| Query size | `limit=1` | Only the first feature is ever used |
| Identity | `User-Agent: TahirsListBot/0.1 (+repo)` | Polite and contactable |
| Retry statuses | 429, 500, 502, 503, 504, IO/timeout | A later attempt can plausibly succeed |
| No retry | 400/401/403/404, malformed 200, empty body | Retrying cannot fix them |
| Backoff | 1 s → 2 s → 4 s (max 4 attempts) | Bounded; no jitter needed for a single client |
| `Retry-After` | Honoured, clamped to 60 s | One header cannot stall a run |
| Exhaustion | `GeocodingException` | Ingestion aborts before writing a completed run |
| Cancellation | Propagated unchanged | No masking a caller cancel as an outage |

A full four-metro run makes roughly 100 selected-row lookups plus ~76 HMA
outlets that have no coordinates, so ≈ 3 minutes of paced traffic instead of a
sub-minute burst. That is the intended trade.

## What this does not solve

- The public demo is still **best effort**. A long outage stops a run; the
  operator retries when the service is healthy. The alternative is
  self-hosting Photon (country extracts are much smaller than the 95 GB planet
  dump) or a hosted instance, which is a deployment/cost decision, not an
  adapter change.
- The geocode cache (SC-198) is wired into the application, not the ingestion
  CLI; wiring it would make re-runs nearly free of Photon calls. Left as a
  follow-up because SC-227 is about burst policy, not caching.

## Evidence

`PhotonGeocoderTest` covers: 503→200, `Retry-After` honouring and clamping,
permanent 4xx not retried, malformed 200 not retried, attempt budget and total
backoff, `limit=1`, spacing between sequential calls, concurrent callers
serialized with one in flight, and cancellation during backoff.
