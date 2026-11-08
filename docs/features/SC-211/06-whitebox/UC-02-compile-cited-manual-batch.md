# SC-211 — Phase 6: White-box sequence — UC-02 Compile a cited manual Tier-3 batch

Participants are exactly the classes/interfaces from `05-class-diagram-system.md`.

## Main success scenario

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  participant CLI as IngestionConsole
  participant CMB as CompileManualBatch
  participant CBS as CuratedBatchFileAdapter
  participant GEO as PhotonGeocoder
  participant MCP as MatchConfirmationPolicy
  participant CAND as IngestionCandidate
  participant REP as IngestReport
  participant ART as FileArtifactWriter

  DC->>CLI: 1. compileManualBatch(batchFile)
  CLI->>CMB: execute(batchFile, existingCandidates)
  CMB->>CBS: 3. read(batchFile)  [CuratedBatchSource]
  CBS-->>CMB: List<SourceListing> (tier 3, each with SourceRef + accessedOn)
  loop each valid entry
    CMB->>GEO: 4. geocode("name, address, city, region")
    GEO-->>CMB: GeocodedPlace?
    CMB->>MCP: confirm(query, place, listing)
    MCP-->>CMB: MatchConfirmation | RejectReason
  end
  CMB->>CAND: 5. existing.mergeWith(manual)  [Tier-1 keeps provenance + VERIFIED; manual-only rows UNVERIFIED]
  CMB->>REP: build(merged, rejects, gaps)
  CMB->>ART: 6. writeCandidates(runId, candidates)
  CMB->>ART: writeReport(runId, report)
  CMB-->>CLI: IngestReport
  CLI-->>DC: 7. merged/rejected rows with reasons
```

## Validation rules enforced by `CuratedBatchFileAdapter.read`

- Required: name, address line, city, region, country, source URL, accessed-on
  date, tier = 3.
- Rejected entry → `IngestReject(INVALID_MANUAL_ENTRY, fieldDetail)`.
- The adapter never dereferences the cited URL; it validates presence/shape only.
  (Citation content is human-checked in UC-04.)

## Transaction and consistency notes

- **No DB transaction.** The candidate file is rewritten atomically; a failed
  validation never truncates the existing candidate set.
- Merging is deterministic: for an equal-tier collision, the earlier-observed
  `SourceRef` wins; the report records the merge.
- `ING-4 (manual verification rule):` a manual-only candidate is always
  `UNVERIFIED` with `is_hand_cut = NULL`; a merged candidate inherits the
  Tier-1 verification outcome (`VERIFIED` + hand-cut) and keeps the tier-3 claim
  as additional evidence. A manual row can never upgrade a Tier-1 row's status
  or set hand-cut.

## Exception mapping

| Phase 4 flow | White-box behavior | Observable outcome |
|---|---|---|
| A1 | `SourceListing` with `postal = null`; `IngestionCandidate` carries null structured fields | Report notes the address gap |
| A2 | Row cites the research document as a secondary source; `SourceRef.source = RESEARCH_SECONDARY` | Report marks the secondary citation |
| E1 | `INVALID_MANUAL_ENTRY` reject with field detail | Entry excluded; other entries proceed |
| E2 | `NO_GEOCODER_MATCH` / `AMBIGUOUS_GEOCODER_MATCH` reject | Row excluded; no coordinates |
| E3 | `ING-2 (merge idempotency)` `mergeWith` detects the same venue and returns the existing candidate with the added claim | Report marks `DUPLICATE_MERGED` |

## Authorization placement

- Same local-operator model as UC-01; no HTTP surface.
- The curated file is committed and reviewed in a PR, so its provenance is
  auditable (reviewer = code review, not runtime auth).
