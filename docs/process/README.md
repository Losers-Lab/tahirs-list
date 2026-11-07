# docs/process — ratified process documents

This folder holds team process documents. They are **ratified** when the founder
has approved them as the default way to work, and they are written to be
**advisory**: they describe strong defaults and the reasoning behind them, not
gates that block commits.

## Ratification format

Every document in this folder carries this frontmatter:

```yaml
status: ratified          # draft | proposed | ratified | superseded
enforcement: advisory     # advisory (a nudge) | required (a gate)
ratified:
  by: founder             # founder | adnan
  on: YYYY-MM-DD
  scope: "one line: what this applies to"
owner: adnan
applies_via: "@skill-id, /command"   # how the process is actually triggered
```

What the fields mean in practice:

- **`advisory`** — an agent should follow it when the document is invoked, and
  should mention it when deviating. Nothing blocks a commit if it is not
  followed. This is the normal level for new process.
- **`required`** — the founder has made it a gate (e.g. PR review, TDD for the
  backend). Even then, the enforcement is social: reviewers cite the document.
- **`applies_via`** — the honest answer to "how does this happen?". A process
  that nobody invokes is a wish. If this field is empty, the document is
  reference material only.

## Documents

| Document | Status | Enforcement | Invoked by |
|----------|--------|-------------|------------|
| [feature-workflow.md](./feature-workflow.md) | ratified | advisory | `@tahirs-feature-workflow` skill, `/feature` command |

## Amending a process document

Open a PR with a one-paragraph rationale and the founder's approval, update
`ratified.on` to the new date, and bump `status` back to `proposed` while the
change is under review. Do not let local practice drift from the document: if a
feature needs a different process, change the document first, then do the work.
