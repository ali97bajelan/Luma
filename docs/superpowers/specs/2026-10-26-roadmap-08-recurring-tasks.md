# F08 — Recurring tasks specification

Date: 26 October 2026 · Priority: **P2** · Status: **DRAFT — NOT IMPLEMENTED / NOT VALIDATED**

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](2026-10-26-roadmap-08-recurring-tasks.md) · [Plan](../plans/2026-10-26-roadmap-08-recurring-tasks.md) · [QA scenarios](../../feature-roadmap/08-recurring-tasks-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Intent and dependencies

Create daily, selected-weekday and monthly task series without duplicate occurrences or rewriting completed history.

Dependencies: Tasks 02 and 04; schema/archive work relies on task 01 guarantees.

Rank and scope come from [the feature proposal](../../FEATURE_IMPROVEMENT_PROPOSAL_2026-10-26.md). Specific new defaults in this document are proposed implementation choices, not claims of user approval. Read the existing implementation at execution time; this document does not certify its current runtime behavior.

## Architecture and alternatives

Recommended approach: Separate immutable series occurrence identity from task rows and use versioned future schedule segments. Materialize a bounded visible range transactionally and lazily; existing TodoRepository mutations remain the path for occurrence completion.

Considered alternatives: retain the current behavior and document its limitations, or introduce an independent subsystem. Retaining behavior is acceptable only for an explicitly documented investigation/deferred outcome; otherwise it does not meet the intent above. An independent replacement would duplicate existing persistence/navigation and recovery paths; extend the current module contracts unless a specific requirement makes reuse impossible. The recommendation is proposed, not a recorded user selection.

## Existing source and handoff material

- [core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoModels.kt](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoModels.kt)
- [core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoRepository.kt](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoRepository.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/todo/TodoEntity.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/todo/TodoEntity.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/todo/TodoDao.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/todo/TodoDao.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomTodoRepository.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomTodoRepository.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/db/LumaDatabase.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/db/LumaDatabase.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupRows.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupRows.kt)
- [app/src/main/java/com/luma/lifeos/ui/todo/TodoEditorRoute.kt](../../../app/src/main/java/com/luma/lifeos/ui/todo/TodoEditorRoute.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomCalendarRepository.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomCalendarRepository.kt)



## Contracts and ownership

Names below are proposed interfaces to create or adapt, not claims that these declarations already exist. Keep existing public callers working or migrate every caller explicitly in the owning change. Resolve exact current types while preserving these behaviors.

- Proposed `RecurrenceRule` is Daily, Weekdays(Set<DayOfWeek>) or Monthly(anchorDay: Int); `TaskSeries` has UUID, start/end inclusive dates, civil dueTime, schedule revisions and title/note.
- Proposed `TaskOccurrenceKey(seriesId: UUID, nominalDate: LocalDate)` remains unique; `ensureOccurrences(from: LocalDate, through: LocalDate)` is idempotent and creates at most the requested 366-day window.
- One-off overrides/skips retain the nominalDate key even when dueDate moves; task ID stays stable. `editFuture(seriesId, effectiveDate, expectedRevision, change)` creates a new schedule segment transactionally.

## Requirements

### F08-R01 — Rules and month end

Daily means each date; weekdays requires >=1 selected weekday; monthly day 1–31 clamps to the last valid date without changing the original anchor. Jan 31 → Feb 28 → Mar 31 in 2027. Start/end inclusive within years 1–9999; no unbounded pre-generation.

### F08-R02 — History and missed work

Each occurrence is an independent task with stable ID and nominal date. Missed open occurrences remain overdue; no auto-completion or catch-up burst. A first series creates no dates before its start; materialization of elapsed valid dates is bounded/paged and must not silently omit overdue work.

### F08-R03 — Edit scopes

Offer This occurrence and This and future. A single override changes only its task; future edits preserve completed/skipped past records and explicit one-off overrides. End series cancels unmodified future open generated tasks; deletion/history removal is a separate explicit confirmation.

### F08-R04 — Cross-surface behavior

Occurrences use existing dated task membership in Today/Calendar/tasks/widgets. Habits stay excluded from Calendar. Civil date/time values do not shift with travel; reminder instants recalculate by task 04 policy; no standalone life event is invented.

### F08-R05 — Persistence and compatibility

Uniqueness and revision checks are transactional under retries/concurrent generators. Archive stores series, schedule segments, occurrences, overrides and skips; old archives restore with no series. Derived generation cursors/runtime are device-local and rebuilt; replacement revokes stale commands.

## Two-phase delivery contract

**Phase 1 — Implementation:** implement the source/configuration/resources/documentation described in the plan, review the source and diff, and record the handoff. **No Gradle command, sync, dependency resolution, build, schema generation, lint, automated test execution, emulator launch or device test. Test authoring is deferred to QA.** Source checks do not prove compilation or correctness.

**Phase 2 — QA:** author tests/fixtures, generate and inspect schemas where relevant, run Gradle/build/lint/automated suites, execute native/manual cases, correct discovered defects and record evidence. Begin only after this task’s implementation source handoff; all initial outcomes are NOT RUN. Follow the [shared workflow](../../feature-roadmap/WORKFLOW.md) for isolation, matrix, commands, evidence and completion gates.

## Scope boundaries

No arbitrary cron/RRULE editor, natural-language recurrence, infinite eager generation or replacement of counted habits.

## Acceptance and traceability

All five requirements must map to implementation steps and QA outcomes. [The QA document](../../feature-roadmap/08-recurring-tasks-qa.md) provides executable scenarios with fixtures/actions/oracles; [the plan](../plans/2026-10-26-roadmap-08-recurring-tasks.md) owns concrete files and implementation/QA checklists. Both phases start pending. A source-complete task is not feature-complete. For assessment/deferred branches, report the delivered investigation separately from feature/runtime acceptance.
