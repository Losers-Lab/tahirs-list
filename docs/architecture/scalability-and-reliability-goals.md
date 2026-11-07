---
type: architecture-note
title: "Scalability and Reliability Goals"
description: "Capacity model and reliability targets for Tahir's List, benchmarked against public traffic estimates for Zabihah (the closest comparable halal directory), with the scaling ladder and the failure modes that actually bite at each stage."
tags: [tahirs-list, scalability, reliability, capacity, slo, operations, zabihah, postgis, ktor, sveltekit]
status: draft
generated: 2026-11-07
generated_by: hamza
verified: false
sources:
  - resource: https://www.crunchbase.com/organization/zabihah/tech_details
    title: "Crunchbase - Zabihah tech details (monthly visits estimate)"
  - resource: https://www.siteworthtraffic.com/report/zabihah.com
    title: "SiteWorthTraffic - zabihah.com estimate (report is roughly 5 years old)"
  - resource: https://www.similarweb.com/website/zabihah.com/
    title: "Similarweb - zabihah.com traffic share (absolute figures paywalled)"
  - resource: https://www.zabihah.com/
    title: "Zabihah - '60,000+ halal places' first-party claim"
  - resource: docs/handoff/07-current-state-and-risks.md
    title: "Current state and risks (R1-R5, R8, R13)"
  - resource: ARCHITECTURE.md
    title: "Ratified architecture and hosting decision (U-08)"
---

# Scalability and Reliability Goals

> **Status: draft for founder ratification.** This note records the capacity model,
> the reliability targets, and the scaling triggers we design against. It does not
> reopen a ratified architecture decision; it gives that decision a measurable
> envelope. Numbers marked *estimate* are engineering estimates to be validated by
> the load test described in section 8, not measurements.

> **Why this document exists.** The stack decision (Kotlin/Ktor + SvelteKit +
> PostgreSQL/PostGIS) was being weighed partly on "will it handle the traffic?".
> This note answers that with a reference scale, a load model, and explicit
> targets, so future architecture arguments are settled by numbers rather than
> intuition.

---

## 1. TL;DR

- **Reference scale:** a directory like Zabihah runs at roughly **50k-200k
  visits/month** with **~60k indexed listings** and ~77% organic-search traffic.
- **Derived origin load:** **~0.15 pageviews/second average**, **~1.5-2/second at
  a 10x peak**, **~5-10 database queries/second** at peak.
- **Verdict:** the current stack handles this scale with **roughly 50-100x
  headroom** on one CX-class VPS. Capacity is **not** a reason to change the
  stack, and would not be a reason to choose Go either.
- **The real risks are operational**, not architectural: no backups, no CDN, no
  CI, no production host. Those are tracked as R1/R4/R5 in the handoff and are
  the subject of the goals in section 5.

---

## 2. Reference traffic: what Zabihah actually gets

Third-party panels are noisy, sometimes stale, and inconsistent about
visits vs. unique visitors vs. pageviews. Treat every figure below as an
**order-of-magnitude estimate**, not a fact. The conclusion in section 4 holds
across a 10x range, so the uncertainty does not change the engineering answer.

| Source | Figure | As of | Caveat |
|---|---|---|---|
| Crunchbase (tech details) | **~110,500 monthly visits**, +577.7% growth | accessed 2026-11-07 | Aggregated third-party estimate; window unknown; may include bots |
| SiteWorthTraffic | ~1,465 daily uniques, ~3,727 daily pageviews; ~43,950 monthly uniques, ~111,810 monthly pageviews; Alexa rank ~280k | report says "updated 5 years ago" | Stale (Alexa-era) and likely undercounts |
| Similarweb (comparison pages) | **~77% of traffic from organic search**; no absolute number exposed | Sep 2026 | Absolute figures are paywalled |
| zabihah.com | **60,000+ listings**, city directories, blog, iOS/Android apps | 2026-11-07 | First-party claim; useful as a *content* scale anchor |

**Working envelope for design:**

- Visits/month: **50,000 - 200,000** (design point: **150,000**)
- Pageviews/month at 2.5 pages/visit: **125,000 - 500,000** (design point: **375,000**)
- Indexable listing pages: **~60,000**
- Traffic mix: **~75-80% organic search**, remainder direct/app/referral

Why this is the right comparable: same product category, same content shape
(directory + city pages + listing pages), same SEO-driven acquisition, same
mobile-app companion.

---

## 3. Load model

The arithmetic is deliberately simple and pessimistic in the places that matter.

### 3.1 Request rate

```
avg pageview rate  = 375,000 / (30 * 86,400)      = 0.145 pageviews/s
peak factor        = 10x (Ramadan iftar hours, a viral post, a news mention)
peak pageview rate = 1.45 pageviews/s
```

A pageview is one origin HTML request; static assets are CDN/browser-cached.
So **origin HTML requests per second = pageviews per second**.

### 3.2 Database load

- Each SSR page performs **1-4 queries** (listing + certification + images +
  reviews cache).
- At peak: **~6-10 queries/second**.
- `ST_DWithin` + `ST_DistanceSphere` over ~60k rows with a GiST index is a
  **single-digit-millisecond** operation *estimate* on modest hardware.
- Writes are rare by comparison: favourites, claims, owner edits, verification
  transitions. Peak write rate is well under **1/second**.

### 3.3 Crawler load

- Googlebot re-crawling 60k pages weekly: **~0.1-0.3 requests/second**.
- Even a 5x crawl-rate spike stays under **1.5 requests/second**.
- Crawl traffic is anonymous and cacheable, which is exactly why section 6
  treats CDN caching as a reliability control, not an optimization.

### 3.4 Bandwidth and storage

- Pageviews x ~1-1.5 MB (images dominate): **~375-560 GB/month** *estimate*.
- Hetzner CX-class includes ~20 TB/month; R2 has zero egress.
- 60k listings with a few images each: **tens of GB** in object storage,
  comfortably inside R2's free tier.

### 3.5 Comparison points

| Scale | Avg pageviews/s | Peak (10x) | Feasible on one CX23? |
|---|---|---|---|
| Zabihah estimate (design point) | 0.15 | ~1.5 | Yes, with wide margin |
| 10x Zabihah (1.5M visits/mo) | 1.4 | ~14 | Yes, with CDN |
| 100x Zabihah (15M visits/mo) | 14 | ~140 | Not on one box; stage 2 |
| 1000x Zabihah | 145 | ~1,450 | Multi-node + replicas; stage 3 |

Go's concurrency advantage becomes material in the **thousands of req/s**
range. That is 100-1000x above the reference scale.

---

## 4. Capacity of the current stack

Component budgets below are **estimates** for the ratified stack (Ktor 3 on
JDK 21, SvelteKit 3 on Node 24, PostgreSQL 17 + PostGIS 3.4), all containerized
on one Hetzner CX-class VPS (2 vCPU / 4 GB / 40 GB).

| Component | Idle RAM (estimate) | Notes |
|---|---|---|
| Caddy (TLS, reverse proxy) | 15-30 MB | Can be replaced by Cloudflare Tunnel at MVP |
| SvelteKit (Node 24, adapter-node) | 120-200 MB | One process; horizontal replicas later |
| Ktor (JDK 21, tuned: jlink + MaxRAMPercentage) | 200-350 MB | Untuned JVM baseline is 400-600 MB |
| PostgreSQL 17 + PostGIS | 150-300 MB | Shared buffers ~25% of RAM |
| Verification/AI worker (if split out) | 150-300 MB | Split once AI work is real |
| **Total** | **~0.6-1.1 GB** | Fits 4 GB with page cache headroom |

Headroom check at the design point:

| Resource | Estimated peak use | Available | Headroom |
|---|---|---|---|
| CPU | < 10% of 2 vCPU | 2 vCPU | ~10x+ |
| App RSS | < 600 MB | ~3 GB usable | ~5x |
| DB connections | ~10-20 | ~100 default | ~5x |
| Origin req/s | ~1.5 | hundreds/s | ~100x |

**Conclusion:** at the reference scale the stack is sized correctly by a wide
margin. Neither the JVM floor nor Node's event loop is a limiting factor.

---

## 5. Reliability goals

These are the targets we design and review against for M1. They are deliberately
honest about a single-node deployment: a single box cannot promise 99.9%.

### 5.1 Service objectives

| Goal | Target (M1) | How it is met |
|---|---|---|
| Availability | **99.5% monthly** (~3.6 h allowed downtime) | Restart policies, health checks, Cloudflare in front, tested restore |
| SSR HTML latency (cache miss) | **p95 < 500 ms** | Indexed queries, 1-4 queries/page, 2 s load timeout already in loaders |
| SSR HTML latency (edge cache hit) | **p95 < 100 ms** | `Cache-Control: s-maxage` + stale-while-revalidate |
| Search API latency | **p95 < 300 ms** | PostGIS GiST indexes, hard `LIMIT`, quantized coordinates |
| Error rate (5xx) | **< 1%** of requests over 5 min | Alerting + rollback path |
| Image availability | **> 99.9%** | R2/CDN, never proxy image bytes through the app |
| Deploy downtime | **< 30 s** (stretch: < 5 s with two app containers behind Caddy) | Rolling restart |
| Backup RPO | **<= 24 h** (target <= 15 min once WAL archiving exists) | Nightly `pg_dump` + object mirror |
| Restore RTO | **<= 2 h**, rehearsed | Documented runbook, quarterly drill |
| Backup age alert | **> 26 h = page** | Monitoring check |

### 5.2 Trust-critical reliability (product, not infrastructure)

For this product, a stale "Verified" badge is a worse failure than a 5xx.

| Goal | Target | Notes |
|---|---|---|
| Verified status freshness | Certified listings reflect approve/deny within **5 s** | Read-your-writes after committee action |
| Expired certification handling | Never display **Verified** past expiry once U-14/expiry semantics are ratified | Currently an open product question; see ARCHITECTURE Part 2 |
| Audit integrity | Every verification decision is append-only and attributable | State machine already records transitions |

### 5.3 Non-goals for M1

Explicitly **not** required at the reference scale, and listed so nobody builds
them "just in case":

- Multi-region deployment
- Active-active or automatic failover
- Kubernetes
- Redis / external cache (Postgres + CDN are sufficient)
- Microservice extraction
- Read replicas
- Auto-scaling
- 99.9%+ SLA language in any public material

---

## 6. What actually breaks first

Ranked by likelihood x impact at the reference scale. Note that **none of these
are language or framework failures.**

| Rank | Failure mode | Impact | Mitigation | Status |
|---|---|---|---|---|
| 1 | **No database/object backup or restore** (handoff R1) | Critical: total data loss on disk failure or a `volume prune` | Nightly `pg_dump` + `mc mirror`/R2, restore drilled | **Open** |
| 2 | **No CDN in front of SSR** | Crawlers and repeat visitors hit origin; latency and cost rise | Cloudflare proxy, cache anonymous HTML, purge on write | Open |
| 3 | **No CI** (R4) | Regressions reach `main`; "green build" is a claim, not a fact | GitHub Actions running the containerized test scripts | Open |
| 4 | **No production host** (R5) | No real traffic means no real reliability data | Provision CX23, deploy, monitor | Open |
| 5 | **Images served from origin** | Bandwidth and sockets consumed by binaries | Presigned R2 URLs + CDN; never proxy bytes | Designed in, not deployed |
| 6 | **CPU work on the request path** | One resize/AI call stalls other requests | Upload -> job -> worker process | Planned |
| 7 | **Missing or unused geo indexes** | Search latency spikes on 60k+ rows | GiST indexes exist in migrations; add `EXPLAIN` assertions to tests | Partially verified |
| 8 | **Connection pool exhaustion** | Requests queue, then time out | Pool ~10/process; PgBouncer only at stage 1 | Not started |
| 9 | **Watchdog blind to crash loops** (R3) | Stack appears healthy while down | Check HTTP health/restart counts, not `.State.Running` | Open |
| 10 | **Default credentials** (R13) | Trivial compromise | Rotate before any public exposure | **Open** |

Operational maturity beats architectural elegance: fixing ranks 1-5 removes more
real risk than any rewrite.

---

## 7. Scaling ladder

Each stage has an explicit **trigger**. Do not advance stages on vibes.

| Stage | Trigger | Shape | Est. cost/month |
|---|---|---|---|
| **0 - Launch** | now | 1x CX23: app + Postgres + Caddy (+ worker), Cloudflare, R2 | **US$5-8** |
| **1 - Growth** | > ~70% sustained CPU, or p95 > 500 ms, or > 500k visits/mo | 2 app replicas, Postgres on its own box or managed, PgBouncer, read replica | ~US$20-40 |
| **2 - Scale** | > 2-3M visits/mo, or DB CPU > 70% sustained | 3+ app nodes behind a LB, managed Postgres + replicas, Redis, dedicated queue, observability stack | ~US$100-300 |
| **3 - Very large** | > 20M visits/mo | CDN-first, prerendered/edge HTML, service extraction, hot-path rewrite if justified | revenue-funded |

Stage 0 fits Zabihah's estimated scale outright. Stage 1 is headroom, not a
requirement.

### Cost notes

- The full-stack-vs-Kotlin memory difference (~600 MB vs ~350 MB) does **not**
  change the VPS tier at stage 0. It becomes relevant only if we target a 1 GB
  instance or run many replicas.
- Egress is the only cost that scales with traffic; R2 removes it.
- AI verification cost scales with submissions, not visits; downscaling images
  before the vision call (epic 196, story #197) is the control.

---

## 8. Measurement plan

Targets without measurement are wishes. Minimum instrumentation for M1:

**Collect**
- Request rate, status codes, p50/p95/p99 latency (per route class)
- DB query latency and slow-query log (> 100 ms)
- Connection pool saturation
- Container RSS and JVM heap
- CDN cache hit ratio
- Job queue depth and oldest-job age
- Backup age

**Alert (page a human)**
- Availability: 3 consecutive failures from 2 regions
- 5xx > 1% for 5 minutes
- p95 > 1 s for 10 minutes
- Disk > 80%
- Backup age > 26 h
- Restart count increasing (crash loop)
- Certificate expiry < 14 days

**Validate capacity once**
- k6 (or vegeta) in a container against staging:
  sustained **50 requests/second for 10 minutes**, mixed detail + search, with
  p95 < 300 ms and error rate < 0.1%.
- That is ~35x the design-point peak. Passing it is the evidence that this
  document's estimates are conservative.

**Validate recovery once**
- Restore the latest `pg_dump` into a clean container and run the smoke tests.
- Restore a sample of object storage.
- Time both. Record the measured RTO here.

---

## 9. Assumptions and open questions

These assumptions drive the numbers above; if one changes, revisit the model.

1. **Peak factor is 10x average.** Unvalidated; a viral moment or Ramadan peak
   could exceed it briefly.
2. **~75-80% of HTML requests are anonymous and cacheable.** Authenticated
   pages bypass edge cache.
3. **2.5 pages/visit.** From the SiteWorthTraffic estimate (2.44-2.6 range).
4. **CDN hit ratio of 60-80%** on anonymous HTML once caching is configured.
5. **No first-party Zabihah traffic data exists.** All figures are third-party.
6. **The app surface stays API-shaped.** Extensions and a future mobile client
   add API traffic but are cacheable at the edge and small in absolute terms.

Open questions for the founder:

- Is 99.5% the right M1 availability target, or should we explicitly publish
  "best effort" until backups and CI exist?
- Do we want Cloudflare in front from day one (recommended, free) or only after
  launch?
- Should the AI verification worker run as a separate container from the start,
  or in-process until submission volume justifies it?

---

## 10. What this means for the stack decision

- Capacity does **not** favor a rewrite. The current stack has ~50-100x headroom
  at the reference scale, and the alternative runtimes have no material
  advantage below thousands of requests/second.
- A single-runtime deployment (full-stack SvelteKit) would be marginally simpler
  to operate, but the gain is measured in one fewer container, not in
  throughput.
- Therefore: **treat scalability and reliability as an operations program, not
  a language migration.** Fix R1-R5, add the CDN, then ship M1. Revisit the
  stack only if a trigger in section 7 or a product need (native client,
  third-party API) fires.

---

## 11. References

- `ARCHITECTURE.md` - ratified stack and hosting decision (U-08), geo/search (U-09), reviews (U-10)
- `docs/handoff/07-current-state-and-risks.md` - R1-R16, the operational risk register
- `docs/handoff/05-operations-and-infrastructure.md` - current runtime and deploy paths
- `docs/handoff/08-roadmap-and-open-questions.md` - epic 196 cost-hardening stories
- External: Crunchbase, SiteWorthTraffic, Similarweb, zabihah.com (see frontmatter; accessed 2026-11-07)
