# F08 — Recurring tasks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use delegation only if separately authorized. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create daily, selected-weekday and monthly task series without duplicate occurrences or rewriting completed history.

**Architecture:** Separate immutable series occurrence identity from task rows and use versioned future schedule segments. Materialize a bounded visible range transactionally and lazily; existing TodoRepository mutations remain the path for occurrence completion.

**Tech Stack:** Existing Kotlin, pure domain contracts, Room, Compose, Java 17 and native Android adapters as appropriate; candidate dependency changes must be documented before integration.

**Spec:** [Recurring tasks specification](../specs/2026-10-26-roadmap-08-recurring-tasks.md)

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../specs/2026-10-26-roadmap-08-recurring-tasks.md) · [Plan](2026-10-26-roadmap-08-recurring-tasks.md) · [QA scenarios](../../feature-roadmap/08-recurring-tasks-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Global constraints

- Exactly two phases per task: implementation, then QA; do not interleave a test cycle into implementation.
- During implementation: no Gradle, sync, resolution, build, lint, schema generation, test execution, emulator or device. Test authoring belongs to QA.
- Preserve existing personal data/user edits, module boundaries, minimum API28 and local-first/offline defaults; follow the spec for any explicit compatibility exception.
- Calendar stays tasks/memories only; preserve navigation, drafts, revision checks, photo ownership and EN/FR/FA/RTL/light/dark support.
- Read [WORKFLOW.md](../../feature-roadmap/WORKFLOW.md) for exact isolation, source handoff and QA result/evidence obligations.

Dependencies: Tasks 02 and 04; schema/archive work relies on task 01 guarantees.

## Review focus

- F08-R01 (Rules and month end): execute F08-QA01, F08-QA02; author assertion-level regressions in Q01 for the specified conditions.
- F08-R02 (History and missed work): execute F08-QA01, F08-QA03; author assertion-level regressions in Q01 for the specified conditions.
- F08-R03 (Edit scopes): execute F08-QA04, F08-QA05; author assertion-level regressions in Q01 for the specified conditions.
- F08-R04 (Cross-surface behavior): execute F08-QA06, F08-QA07; author assertion-level regressions in Q01 for the specified conditions.
- F08-R05 (Persistence and compatibility): execute F08-QA06, F08-QA08; author assertion-level regressions in Q01 for the specified conditions.

## File map and interfaces

Existing paths are source anchors, not a request to edit every file regardless of need. New paths are proposed ownership boundaries; reconcile with existing equivalents before creating duplicates.

- Modify/review: [core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoModels.kt](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoModels.kt)
- Modify/review: [core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoRepository.kt](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoRepository.kt)
- Modify/review: [core/data/src/main/kotlin/com/luma/lifeos/data/todo/TodoEntity.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/todo/TodoEntity.kt)
- Modify/review: [core/data/src/main/kotlin/com/luma/lifeos/data/todo/TodoDao.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/todo/TodoDao.kt)
- Modify/review: [core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomTodoRepository.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomTodoRepository.kt)
- Modify/review: [core/data/src/main/kotlin/com/luma/lifeos/data/db/LumaDatabase.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/db/LumaDatabase.kt)
- Modify/review: [core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupRows.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupRows.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/todo/TodoEditorRoute.kt](../../../app/src/main/java/com/luma/lifeos/ui/todo/TodoEditorRoute.kt)
- Modify/review: [core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomCalendarRepository.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomCalendarRepository.kt)
- Create if required by selected implementation: `core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TaskRecurrence.kt`
- Create if required by selected implementation: `core/data/src/main/kotlin/com/luma/lifeos/data/todo/TaskSeriesStore.kt`
- Create if required by selected implementation: `app/src/main/java/com/luma/lifeos/ui/todo/TaskRecurrenceEditor.kt`

Interfaces consumed/produced:

- Proposed `RecurrenceRule` is Daily, Weekdays(Set<DayOfWeek>) or Monthly(anchorDay: Int); `TaskSeries` has UUID, start/end inclusive dates, civil dueTime, schedule revisions and title/note.
- Proposed `TaskOccurrenceKey(seriesId: UUID, nominalDate: LocalDate)` remains unique; `ensureOccurrences(from: LocalDate, through: LocalDate)` is idempotent and creates at most the requested 366-day window.
- One-off overrides/skips retain the nominalDate key even when dueDate moves; task ID stays stable. `editFuture(seriesId, effectiveDate, expectedRevision, change)` creates a new schedule segment transactionally.

QA-owned tests (create only in phase 2; extend existing equivalent suites instead of duplicating them where practical):

- QA create/extend: `core/domain/src/test/kotlin/com/luma/lifeos/domain/roadmap/F08ContractTest.kt`
- QA create/extend: `core/data/src/androidTest/kotlin/com/luma/lifeos/data/roadmap/F08PersistenceTest.kt`
- QA create/extend: `app/src/test/java/com/luma/lifeos/roadmap/F08StateTest.kt`
- QA create/extend: `app/src/androidTest/java/com/luma/lifeos/roadmap/F08FlowTest.kt`

## Phase 1 — Implementation

Initial status: **PENDING**.

### F08-I01 — Define series and occurrence rules

- [ ] Create TaskRecurrence.kt with rule validators, anchor-preserving month arithmetic, inclusive ranges, stable occurrence keys and explicit edit/skip/end policies.
- [ ] Source-review the changed declarations/callers against F08-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F08-I02 — Persist and materialize atomically

- [ ] Implement TaskSeriesStore, series/segment/occurrence tables and migration with unique keys. Bound each range to 366 days, page past materialization, and preserve repository completion revision behavior.
- [ ] Source-review the changed declarations/callers against F08-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F08-I03 — Integrate editors and projections

- [ ] Add recurrence controls/scope confirmation, one-off reschedule/skip and end-series UI. Ensure Calendar/task/widget readers request their relevant ranges before observing tasks; cross-surface reads do not generate infinite data.
- [ ] Source-review the changed declarations/callers against F08-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F08-I04 — Integrate reminder/archive contracts

- [ ] Cancel obsolete future reminder commands, preserve overrides and completed history, add portable table/version adapters and restore invalidation. Record next schema numbers from execution baseline, never assume version 7 is free.
- [ ] Source-review the changed declarations/callers against F08-R01–R05 and record paths/decisions. Do not run compilation or tests.

### F08-I05 — Source handoff

- [ ] Review all changed files, call sites, permission/manifest declarations, EN/FR/FA resources and persistence/archive contracts for the actual selected scope.
- [ ] Run source-only checks such as `git diff --check`, file/link checks and XML parsing where relevant. A successful static check is not a test/build pass.
- [ ] Record Git/patch identity, implementation decisions, source-to-requirement mapping and known uncertainty. Generated schemas and all runtime verification remain pending QA.
- [ ] Mark phase 1 **SOURCE COMPLETE — QA PENDING** only after I01–I04 and this handoff are complete. Do not mark the task complete or run a Gradle/emulator command here.

## Phase 2 — QA

Initial status: **NOT RUN**. Entry requires I01–I05 handoff and isolated target verification. This phase owns test authoring, Gradle, schemas, emulator/device use and defect retesting.

### F08-Q01 — Fixtures and automated coverage

- [ ] Read the full [QA scenarios](../../feature-roadmap/08-recurring-tasks-qa.md), prepare its synthetic fixture, and create the QA-owned tests above. Give tests descriptive scenario-matching names; assert expected IDs/values and failure atomicity independently of implementation helpers.
- [ ] Cover every requirement and review-focus condition. Prefer small deterministic domain/state tests, real Room migration/transaction tests for persistence and Compose/native flows for platform behavior. Existing test passes cannot replace missing native or manual coverage.
- [ ] If a QA defect needs production correction, retain a failing regression and capture failure before correction where feasible; fix and rerun here without rewriting expected behavior to match a defect.
- [ ] For an explicitly deferred investigation/no-change branch, do not author tests that pretend new behavior exists. Record the branch evidence, mark inapplicable feature assertions N/A with rationale, and run the applicable existing baseline gates below. This accepts assessment scope only; feature runtime QA stays deferred.

### F08-Q02 — Gradle, schemas and affected regressions

Run from repository root **only in QA**, after reading the shared command/isolation policy:

```sh
bash gradlew -PdomainOnly=true :core:domain:test
bash gradlew :core:data:testDebugUnitTest :app:testDebugUnitTest
bash gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :core:data:assembleDebugAndroidTest :core:data:lintDebug
```

- [ ] Record actual commands, exit codes and JUnit/lint outcomes. Expected automated gate: exit 0; required tests actually executed with no failures/errors; skipped tests explained; no lint errors/fatals and new warnings classified.
- [ ] Where schema changed, generate/export current Room schema through QA build/KSP, inspect against hand-written migration, preserve old JSON and execute populated-upgrade plus archive compatibility cases. Commit schema evidence with the validated task, not as an unverified phase-1 claim.
- [ ] Run affected instrumented suites only on a confirmed isolated package/AVD using the shared QA catalogue; do not assume current debug package is isolated. Test-class filters and variant commands must match authored test packages and target identity.

### F08-Q03 — Scenarios, native behavior and acceptance

- [ ] Execute all F08-QA01–QA08 cases and every relevant shared matrix cell; Scope dialogs, large missed-range paging, cross-surface counts, DST travel and notification reconciliation.
- [ ] Record actual result, requirement mapping and artifact links in the scenario ledger. Source/compile/host passes cannot certify native/manual cases.
- [ ] Resolve defects, rerun owning and affected regression cases, and identify invalidated evidence. Leave blocked/unrun cases visible with precise prerequisites.
- [ ] Mark complete only for the exact validated scope when required cases pass. Assessment/deferred branch closure must explicitly state that feature runtime acceptance has not occurred. Record release blockers/authorized exceptions without silently self-waiving.

Suggested future commit grouping: one source handoff followed by QA tests, generated schemas and evidence; do not commit unrelated user changes. Creating these documents does not authorize implementation, release or commit actions now.
