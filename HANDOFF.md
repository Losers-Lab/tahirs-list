# Handoff — Tahir's List

> Bismillah. Moved: the handoff bundle no longer lives in this repository.

As of 2026-11-08, all cross-cutting knowledge — product vision and personas,
architecture, development process, team roles, operations and infrastructure,
the work ledger, risk register, roadmap, references, and fresh-machine setup —
was relocated to the team knowledge base:

**[`Losers-Lab/tahirs-list-kb`](https://github.com/Losers-Lab/tahirs-list-kb)**

The bundle remains in Open Knowledge Format (OKF v0.2): plain markdown with
YAML frontmatter, one directory per knowledge area. It is also registered as a
[Basic Memory](https://github.com/basicmachines-co/basic-memory) project, so AI
agents read and write it through the `basic-memory` MCP server (semantic search,
structured notes, validated OKF bundle via `basic-memory okf check`).

What stayed in this repository:

- `docs/features/SC-*` — per-feature documentation (workflow artifacts tied to
  their Shortcut stories)
- `README.md`, `ARCHITECTURE.md`, `AGENTS.md` — the governing documents listed
  in the KB's own index; they remain authoritative over the KB bundle
- the implementation itself (backend, frontend, scripts)

The pre-relocation handoff is preserved in git history (see the merge of
`docs/relocate-kb`); use `git log -- docs/handoff` to walk back to it.
