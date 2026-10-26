# F10 — Monthly reflection and review notes specification

Date: 26 October 2026 · Priority: **P2** · Status: **DRAFT — NOT IMPLEMENTED / NOT VALIDATED**

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](2026-10-26-roadmap-10-monthly-review.md) · [Plan](../plans/2026-10-26-roadmap-10-monthly-review.md) · [QA scenarios](../../feature-roadmap/10-monthly-review-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Intent and dependencies

Extend deterministic reflection to calendar months and let users save private notes about a review period.

Dependencies: Tasks 02 and 03 performance discipline; task 01 for note persistence/backup.

Rank and scope come from [the feature proposal](../../FEATURE_IMPROVEMENT_PROPOSAL_2026-10-26.md). Specific new defaults in this document are proposed implementation choices, not claims of user approval. Read the existing implementation at execution time; this document does not certify its current runtime behavior.

## Architecture and alternatives

Recommended approach: Generalize period calculation without breaking WeeklyReviewRepository. Reuse saved-observation aggregates and source drill-down; store user notes separately from rebuildable derived summaries.

Considered alternatives: retain the current behavior and document its limitations, or introduce an independent subsystem. Retaining behavior is acceptable only for an explicitly documented investigation/deferred outcome; otherwise it does not meet the intent above. An independent replacement would duplicate existing persistence/navigation and recovery paths; extend the current module contracts unless a specific requirement makes reuse impossible. The recommendation is proposed, not a recorded user selection.

## Existing source and handoff material

- [core/domain/src/main/kotlin/com/luma/lifeos/domain/review/WeeklyReview.kt](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/review/WeeklyReview.kt)
- [core/domain/src/main/kotlin/com/luma/lifeos/domain/repository/WeeklyReviewRepository.kt](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/repository/WeeklyReviewRepository.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomWeeklyReviewRepository.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomWeeklyReviewRepository.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/review/ReviewSql.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/review/ReviewSql.kt)
- [app/src/main/java/com/luma/lifeos/ui/review/WeeklyReviewRoute.kt](../../../app/src/main/java/com/luma/lifeos/ui/review/WeeklyReviewRoute.kt)
- [app/src/main/java/com/luma/lifeos/ui/review/ReviewSourcesRoute.kt](../../../app/src/main/java/com/luma/lifeos/ui/review/ReviewSourcesRoute.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupRows.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupRows.kt)



## Contracts and ownership

Names below are proposed interfaces to create or adapt, not claims that these declarations already exist. Keep existing public callers working or migrate every caller explicitly in the owning change. Resolve exact current types while preserving these behaviors.

- Proposed `MonthlyReviewPeriod(month: YearMonth, zone: ZoneId, today: LocalDate)` with current/previous inclusive ranges.
- Proposed `ReviewNoteKey(kind: WEEK|MONTH, start: LocalDate, zoneId: String)` and `saveNote(key, expectedRevision: UUID?, nextRevision: UUID, body: String)`; body <=10,000 Unicode code points, blank deletes only after explicit confirmation.
- Proposed `MonthlyReviewRepository.observe(period: MonthlyReviewPeriod): Flow<MonthlyReview>` and source pages capped at 50; existing weekly signatures stay available.

## Requirements

### F10-R01 — Period comparisons

Past months compare complete months. Current month includes days 1..today and compares previous month days 1..min(today.dayOfMonth, previous.lengthOfMonth), explicitly labelled; today remains partial. Future months show saved observations if present but no misleading completed-period comparison.

### F10-R02 — Metric truthfulness

Use saved mood/energy/stress, habit logs/schedules, notable memories, usage and grouped activity. Missing is not zero, partial provider coverage is explicit, source units/types stay separate, and each metric opens actual contributing records. No clinical/causal conclusion.

### F10-R03 — Review notes

User-written notes are independent of computed summaries, editable offline with discard/retry and process-restorable drafts. Key includes period kind/start/zone, so changing viewing zone does not silently overwrite another note. Show the note’s period/zone explicitly.

### F10-R04 — Freshness and persistence

Source edits/deletes/imports/restore recompute derived values and source pages. Notes survive restart and current-format archives; old archives restore with no notes. Confirmed note deletion removes only the note and never its source records.

### F10-R05 — Performance and UI

Benchmark weekly and monthly repository aggregation with 50,000 events; target p95 <300 ms including mapping and separately report cold run. Month switch does not publish late old results; retain week/month selection, date and scroll through recreation. Support empty/error/retry/large-text/RTL states.

## Two-phase delivery contract

**Phase 1 — Implementation:** implement the source/configuration/resources/documentation described in the plan, review the source and diff, and record the handoff. **No Gradle command, sync, dependency resolution, build, schema generation, lint, automated test execution, emulator launch or device test. Test authoring is deferred to QA.** Source checks do not prove compilation or correctness.

**Phase 2 — QA:** author tests/fixtures, generate and inspect schemas where relevant, run Gradle/build/lint/automated suites, execute native/manual cases, correct discovered defects and record evidence. Begin only after this task’s implementation source handoff; all initial outcomes are NOT RUN. Follow the [shared workflow](../../feature-roadmap/WORKFLOW.md) for isolation, matrix, commands, evidence and completion gates.

## Scope boundaries

No AI-generated interpretations, diagnoses, correlation claims or annual review in v1.

## Acceptance and traceability

All five requirements must map to implementation steps and QA outcomes. [The QA document](../../feature-roadmap/10-monthly-review-qa.md) provides executable scenarios with fixtures/actions/oracles; [the plan](../plans/2026-10-26-roadmap-10-monthly-review.md) owns concrete files and implementation/QA checklists. Both phases start pending. A source-complete task is not feature-complete. For assessment/deferred branches, report the delivered investigation separately from feature/runtime acceptance.
