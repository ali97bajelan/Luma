# FI07 Focus privacy and code-only verification

Date: 2026-11-04
Status: **Source review and code-only checks PASS; Android integration acceptance NOT RUN**

[Task ledger](../FOCUS_INSIGHTS_TASKS.md) · [Specification](../superpowers/specs/2026-11-03-focus-names-tags-insights-design.md) · [Implementation plan](../superpowers/plans/2026-11-03-focus-names-tags-insights.md)

## FI-A01–FI-A07 review

| Acceptance | Source evidence | Boundary |
| --- | --- | --- |
| FI-A01 · Name and tag rules | Focus domain suite passes 34/34 tests. | Source rules only; Android/Room acceptance is not implied. |
| FI-A02 · Atomic metadata and timer transitions | Domain/setup suites cover name validation and captured Start retry/reconcile reducers. | This FI07 run did not compile command identity, coordinator/runtime, or transaction tests. Room transaction behavior and Android runtime remain NOT RUN; FI02 remains pending runtime verification. |
| FI-A03 · Migration and archive compatibility | Archive suite passes 16/16 tests. SQLite fixture executes the five migration statements against RUNNING, PAUSED, FINISHED, and INTERRUPTED sessions and a stale paused-export case. | Room migration runner and generated schema 10 remain NOT RUN. |
| FI-A04 · Range summaries and longest session | Focus domain and insight-state suites pass; prior FI04 SQLite suggestion-query check remains recorded in the task ledger. | Room query compilation, invalidation, and Android runtime remain NOT RUN. |
| FI-A05 · Setup and insight cards | Setup and insight-state suite, including FI07 visibility tests, passes 24/24 tests. EN/FR/FA resource keys match. | Compose layout, lifecycle, RTL, IME, and large-font behavior remain NOT RUN. |
| FI-A06 · Search and filtered history | History suite passes 44/44 tests. SQLite history fixture confirms 60 terminal range matches, stable batches, and half-open boundaries. | Room query compilation and Compose/lifecycle behavior remain NOT RUN. |
| FI-A07 · Focus privacy gates | Six pure visibility/reducer tests cover Focus, Insights, History, Today, and Review. Focus state masks session metadata and setup drafts, preserves a PII-free blocker for an unresolved Start, and suppresses tag suggestions whose database generation is stale. History observes access and runtime-generation flows in its ViewModel scope, so it clears cache even while its route is not composed. | App-lock authorization, relock/reunlock, restore lifecycle, and actual UI behavior remain NOT RUN. |

## Source review

- Focus uses the existing `LockSection.FOCUS` access decision and settings entry; no separate credential flow was added.
- The Focus view model hides session labels and clears persisted setup drafts on relock. Insight and tag observers stop while access is denied, stale emissions are ignored, and tag snapshots are exposed only for the active database generation.
- History observes the existing Focus access decision and Focus runtime generation independently of route composition. It cancels active loads and tag observation, clears visible rows and retained search/filter state on relock or restore, and reloads only after the replacement generation is available.
- Today already gated Focus reports and in-progress values. Review's weekly Focus report now receives the same access decision and stops collecting while hidden.
- Source search found no Focus metadata in logging or timer-notification code. Metadata is rendered only in the Focus UI surfaces.
- EN/FR/FA contain identical sets of 1,434 string keys. No new strings were needed for FI07.

## Verification run

| Command | Result |
| --- | --- |
| `python3 scripts/run_focus_domain_tests.py` | PASS · 34/34 JUnit tests |
| `python3 scripts/run_focus_metadata_archive_tests.py` | PASS · 16/16 JUnit tests |
| `python3 scripts/run_focus_setup_tests.py` | PASS · 24/24 JUnit tests, including 6 FI07 privacy visibility tests and the pending-Start relock reducer test |
| `python3 scripts/run_focus_history_tests.py` | PASS · 44/44 JUnit tests; runner includes the extracted pure history UI state |
| `python3 scripts/check_focus_metadata_sql.py` | PASS · five extracted SQL statements; four session-state fixtures and stale paused-export case |
| `python3 scripts/check_focus_history_sql.py` | PASS · 60 terminal range matches, stable batches, half-open boundaries |
| EN/FR/FA XML string-key comparison | PASS · 1,434 keys in each locale |
| Relative links in focus task/spec/plan and backup/privacy docs | PASS |
| Focus route and access call-site review | PASS · Focus, History, Today, and Review use the existing access decision |
| `git diff --check` | PASS |

## Not run

Gradle, emulator/device, instrumentation, lint, and APK commands were not run. Actual Compose rendering, app-lock relock/reunlock, Android lifecycle and restore behavior, Room-generated schema validation, Room migration execution, and Room transaction/invalidation behavior remain NOT RUN. FI-A07 has source evidence only; product acceptance remains pending those runtime checks.
