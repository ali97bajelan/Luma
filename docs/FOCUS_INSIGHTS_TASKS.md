# Focus names, tags, and insights tasks

Date: 2026-11-04 · Status: **FI01, FI03, FI04, FI05, and FI06 source implementation complete; FI02 runtime verification and FI07 pending**

[Specification](superpowers/specs/2026-11-03-focus-names-tags-insights-design.md) · [Implementation plan](superpowers/plans/2026-11-03-focus-names-tags-insights.md)

These extend F16 rather than replacing its existing task status. P0 scope assumes one optional tag per session. Product acceptance remains pending; a source test pass is not Android integration acceptance.

| ID | Priority | Deliverable | Depends on | Acceptance | State |
| --- | --- | --- | --- | --- | --- |
| FI01 | P0 | Name/tag rules, metadata models and test fixtures | — | FI-A01 | DONE (source checks) |
| FI02 | P0 | Transactional metadata persistence, command identity and caller updates | FI01 | FI-A02 | IN PROGRESS (source changes; Room/runtime verification pending) |
| FI03 | P0 | Room migration, portable backup/legacy adapters and privacy format docs | FI02 | FI-A03 | DONE (source checks; Room validation pending) |
| FI04 | P0 | Range-correct tag totals, average and longest-session insight source | FI01, FI02 | FI-A04 | DONE (source checks; Room observation/runtime validation pending) |
| FI05 | P0 | Setup fields, retained drafts, metadata display and focus insight cards | FI02, FI04 | FI-A05 | DONE (source checks; Android UI/lifecycle acceptance pending) |
| FI06 | P0 | Name search and tag-filtered history with stable paging | FI02, FI04 | FI-A06 | DONE (source checks; Room/Compose validation pending) |
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

FI01 source verification: `python3 scripts/run_focus_domain_tests.py` passes 23 tests. Android runtime/UI and Room generated-schema validation remain NOT RUN; product acceptance remains pending.

FI02 source progress: required Start input now reaches the repository transaction; session name and normalized tag persist with session/run/runtime/receipt writes, and command payload identity includes normalized metadata. Database version 10 adds the focus tag catalog and nullable session metadata through migration 9→10. Standalone domain tests pass 23 tests. Emulator, Room migration execution, and generated-schema validation remain NOT RUN because `adb`/Android CLI are unavailable in this environment; FI-A02 remains pending runtime verification.

FI03 source verification: `python3 scripts/run_focus_metadata_archive_tests.py` passes 16 JUnit tests, including 6 metadata adapter tests. `python3 scripts/check_focus_metadata_sql.py` passes 4 migration fixtures for RUNNING, PAUSED, FINISHED and INTERRUPTED v9 sessions plus a stale `EXCLUDE_PAUSED` export fixture; it executes the SQL extracted directly from `LumaDatabase.kt` and verifies existing focus rows, indices, foreign keys and receipts survive. Room-generated schema 10 and Room migration execution remain NOT RUN; FI-A03 remains pending Room/runtime validation.

FI04 source verification: the standalone focus-domain plus insight-state suite passes 38/38 JUnit tests, covering range clipping, tag conservation, deterministic longest-session ties, exact HALF_UP rounding, duplicate/conflicting snapshots, overflow, same-query retry, cancellation, stale query/generation rejection, and empty/error separation. A SQLite smoke check of the DAO suggestion query confirms newest-session ordering, normalized-name tie order, omission of unused tags, and the 10-row limit. Room query compilation, invalidation behavior and Android/runtime integration remain NOT RUN; FI-A04 product acceptance remains pending those checks.

FI05 source verification (2026-11-04): `python3 scripts/run_focus_setup_tests.py` passes 17/17 JUnit tests for valid Start enablement, saved-draft recreation, immutable retries, successful/reset clearing, terminal-screen session identity, stale insight suppression/cancellation, period/zone selection and day rollover. `python3 scripts/run_focus_domain_tests.py` passes 34/34 JUnit tests. EN/FR/FA resource XML parity passes across 1,447 named resources, and `git diff --check` passes. Actual Compose layout, IME/RTL/large-font interaction, Android lifecycle and Room integration remain NOT RUN; FI-A05 product acceptance remains pending those checks.

FI06 source verification (2026-11-04): `python3 scripts/run_focus_history_tests.py` passes 44/44 JUnit tests, covering the focus domain plus filter matching, >50-row filtered paging, filter restoration, page-offset reset and stale query/generation rejection. `python3 scripts/check_focus_history_sql.py` executes the DAO candidate SQL against SQLite and confirms 60 terminal range matches, stable batched ordering and half-open range boundaries. EN/FR/FA string key parity passes across 1,434 keys, and `git diff --check` passes. Room query compilation, Compose interaction/lifecycle and relock invalidation of cached history remain NOT RUN; FI-A06 source evidence is complete while product acceptance remains pending those checks.
