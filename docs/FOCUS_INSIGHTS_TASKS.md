# Focus names, tags, and insights tasks

Date: 2026-11-03 · Status: **Planning handoff only; no product tasks implemented**

[Specification](superpowers/specs/2026-11-03-focus-names-tags-insights-design.md) · [Implementation plan](superpowers/plans/2026-11-03-focus-names-tags-insights.md)

These extend F16 rather than replacing its existing task status. P0 scope assumes one optional tag per session. Product acceptance remains pending; a source test pass is not Android integration acceptance.

| ID | Priority | Deliverable | Depends on | Acceptance | State |
| --- | --- | --- | --- | --- | --- |
| FI01 | P0 | Name/tag rules, metadata models and test fixtures | — | FI-A01 | TODO |
| FI02 | P0 | Transactional metadata persistence, command identity and caller updates | FI01 | FI-A02 | TODO |
| FI03 | P0 | Room migration, portable backup/legacy adapters and privacy format docs | FI02 | FI-A03 | TODO |
| FI04 | P0 | Range-correct tag totals, average and longest-session insight source | FI01, FI02 | FI-A04 | TODO |
| FI05 | P0 | Setup fields, retained drafts, metadata display and focus insight cards | FI02, FI04 | FI-A05 | TODO |
| FI06 | P0 | Name search and tag-filtered history with stable paging | FI02, FI04 | FI-A06 | TODO |
| FI07 | P0 | Privacy/resource review and code-only verification evidence | FI03, FI05, FI06 | FI-A07 | TODO |

## Suggestions outside this implementation

- P1: Repeat previous session with editable prefill and explicit Start.
- P1: Reuse daily aggregates for a seven-day trend and best day.
- Later: Opt-in daily goal, consistency streak, multiple tags and metadata editing.

## Completion rules

- Keep tasks unchecked until code and specified source tests exist and pass.
- Run standalone Kotlin/JVM tests directly, plus static and archive/SQL fixture checks as applicable.
- Do not invoke Gradle, emulator/device tooling, instrumentation, lint or APK assembly.
- Keep runtime/UI/Room generated-schema validation marked NOT RUN; do not mark end-to-end acceptance PASS from source checks.
- Preserve existing F16 unresolved enforcement and platform QA limitations.
