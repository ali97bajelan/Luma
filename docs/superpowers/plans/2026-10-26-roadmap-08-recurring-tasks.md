# F08 — Recurring tasks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use delegation only if separately authorized. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create daily, selected-weekday and monthly task series, including one-level recurring task families, without duplicate occurrences or rewriting completed history.

**Architecture:** Keep immutable root-series occurrence identity separate from task rows and use versioned future schedule segments. Root series own versioned child templates; every root occurrence gets a distinct child snapshot family materialized transactionally. Reuse canonical task-family IDs, revisions, completion, Undo, archive and recovery contracts; do not add a parallel recurrence or hierarchy subsystem.

**Tech Stack:** Existing Kotlin, pure domain contracts, Room, Compose, Java 17 and native Android adapters as appropriate; candidate dependency changes must be documented before integration.

**Spec:** [Recurring tasks specification](../specs/2026-10-26-roadmap-08-recurring-tasks.md)

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../specs/2026-10-26-roadmap-08-recurring-tasks.md) · [Plan](2026-10-26-roadmap-08-recurring-tasks.md) · [QA scenarios](../../feature-roadmap/08-recurring-tasks-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Global constraints

- Exactly two phases per task: implementation, then QA; do not interleave a test cycle into implementation.
- During implementation: no Gradle, sync, resolution, build, lint, schema generation, test execution, emulator or device. Test authoring belongs to QA.
- Preserve existing personal data/user edits, module boundaries, minimum API28 and local-first/offline defaults; follow the spec for any explicit compatibility exception.
- A recurring task is a root; child templates and occurrence snapshots are one level only and consume the canonical task-family contracts. Do not introduce child-owned recurrence, arbitrary nesting, or a parallel family repository/command policy.
- Family recurrence implementation must reconcile with the then-current TI-01/TI-06–TI-11 source and TI-QA01 outcomes before code changes; until that review, this plan is implementation guidance, not evidence of runtime compatibility.
- Calendar stays tasks/memories only; preserve navigation, drafts, revision checks, photo ownership and EN/FR/FA/RTL/light/dark support.
- Read [WORKFLOW.md](../../feature-roadmap/WORKFLOW.md) for exact isolation, source handoff and QA result/evidence obligations.

Dependencies: Tasks 02 and 04; schema/archive work relies on task 01 guarantees. Family recurrence consumes TI-01/TI-06–TI-11 canonical task-family source contracts and must reconcile with TI-QA01 outcomes before implementation or acceptance.

## Review focus

- F08-R01 (Rules and month end): execute F08-QA01, F08-QA02; author assertion-level regressions in Q01 for the specified conditions.
- F08-R02 (History and missed work): execute F08-QA01, F08-QA03; author assertion-level regressions in Q01 for the specified conditions.
- F08-R03 (Edit scopes): execute F08-QA04, F08-QA05; author assertion-level regressions in Q01 for the specified conditions.
- F08-R04 (Cross-surface behavior): execute F08-QA06, F08-QA07, F08-QA10; verify independent due-date window queries include children whose nominal root date falls outside the requested range, honor concrete occurrence overrides, retain parent context and exclude inactive rows from actionable projections.
- F08-R05 (Persistence and compatibility): execute F08-QA06, F08-QA08, F08-QA12; author assertion-level regressions in Q01 for the specified conditions.
- F08-R06 (One-level recurring task families): execute F08-QA09–F08-QA12; author assertion-level regressions for fresh child snapshot/reset, independent fields, lifecycle separate from completion, parent completion after child skip, direct-ID rejection for inactive rows, bounded inverse date-window queries, protected-family reorder policy, archive compatibility and immutable completed history.

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
- Do not add a parallel `TaskSeriesStore`; extend the existing canonical task-family persistence/repository. Add a narrow series adapter only if source review proves no compatible ownership point exists, and keep family transactions/revisions in the canonical owner.
- Create if required by selected implementation: `app/src/main/java/com/luma/lifeos/ui/todo/TaskRecurrenceEditor.kt`

Interfaces consumed/produced:

- Proposed `RecurrenceRule` is Daily, Weekdays(Set<DayOfWeek>) or Monthly(anchorDay: Int); `TaskSeries` has UUID, start/end inclusive dates, civil dueTime, schedule revisions and root title/note.
- Proposed `TaskOccurrenceKey(seriesId: UUID, nominalDate: LocalDate)` remains unique; `ensureOccurrences(from: LocalDate, through: LocalDate)` is idempotent and creates at most the requested 366-day window.
- `SeriesChildTemplate` has a stable template UUID, effective schedule version, sibling position, title/note, importance, optional signed `dueDateOffsetDays` from the root nominal date, and optional civil due time. Null offset means undated. The template is not a task row and cannot own a series.
- `TaskOccurrenceChildKey(parentOccurrenceKey: TaskOccurrenceKey, childTemplateId: UUID)` uniquely identifies a generated child snapshot. Each snapshot has its own stable task UUID; status starts open and completion timestamp null. An occurrence-only child added with `This occurrence` gets a fresh child-template UUID scoped to that root occurrence and is not added to the series template. IDs and completion state never carry over from a different root occurrence.
- Every child edit requires `This occurrence` or `This and future`. The former writes a per-occurrence child override/skip; the latter creates an effective template version and transactionally updates only eligible unmodified open future snapshots. Completed roots/children, skipped keys and one-off overrides are preserved.
- Parent and child materialization, family edits, completion, skip/end and archive restore consume the canonical task-family repository/revision/command contracts; no duplicate family subsystem is allowed.
- One-off parent overrides/skips retain the nominalDate key even when dueDate moves; task ID stays stable. `editFuture(seriesId, effectiveDate, expectedRevision, change)` creates a new schedule segment transactionally.

QA-owned tests (create only in phase 2; extend existing equivalent suites instead of duplicating them where practical):

- QA create/extend: `core/domain/src/test/kotlin/com/luma/lifeos/domain/roadmap/F08ContractTest.kt`
- QA create/extend: `core/data/src/androidTest/kotlin/com/luma/lifeos/data/roadmap/F08PersistenceTest.kt`
- QA create/extend: `app/src/test/java/com/luma/lifeos/roadmap/F08StateTest.kt`
- QA create/extend: `app/src/androidTest/java/com/luma/lifeos/roadmap/F08FlowTest.kt`
- Extend those same QA-owned suites with F08-QA09–F08-QA12 for family snapshot/reset, independent child fields and edit scopes, family skip/end, and completed-history/archive safety; do not create a second test subsystem.

## Phase 1 — Implementation

Initial status: **PENDING**.

### F08-I01 — Define series and occurrence rules

- [ ] Create or extend TaskRecurrence.kt with root-series validators, anchor-preserving month arithmetic, inclusive ranges, stable parent/child occurrence keys, versioned child-template rules, signed child date offsets, explicit This occurrence/This and future scopes, separate ACTIVE/SKIPPED/CANCELLED occurrence lifecycle, and skip/end policy.
- [ ] Reconcile child task fields, IDs, family revisions and completion/Undo behavior against the canonical task-family contracts; reject child-owned recurrence and deeper nesting.
- [ ] Source-review the changed declarations/callers against F08-R01–R06 and record paths/decisions. Do not run compilation or tests.


### F08-I02 — Persist and materialize atomically

- [ ] Extend the existing task-family persistence/repository contracts with root series, versioned child templates, root occurrences and child snapshots; do not create an independent TaskSeriesStore when the canonical family store can own them. Add transactional uniqueness for the root key and `(parentOccurrenceKey, childTemplateId)`.
- [ ] Materialize each bounded root occurrence and all effective child snapshots atomically; enforce the 366-day bound, paging, open/null-completion reset, unique task UUIDs per occurrence, stable sibling order, child date offsets and independent importance. Persist lifecycle separately from completion, retain inactive rows read-only, and reject direct-ID mutations against SKIPPED/CANCELLED rows.
- [ ] Apply family revision guards and atomic family edit/skip/end/completion paths; retain immutable completed rows and stale-command/Undo semantics. Reconcile migration against current schema/archive baseline during implementation.
- [ ] Source-review the changed declarations/callers against F08-R01–R06 and record paths/decisions. Do not run compilation or tests.


### F08-I03 — Integrate editors and projections

- [ ] Extend the existing task-family editor with root recurrence controls, child-template editing and required This occurrence/This and future scope selection for child add/edit/reorder/retire. Preserve draft/retry/navigation behavior; show child date/importance independently and do not cascade root fields.
- [ ] Add clear family-scope confirmation for one-occurrence skip and end series. Children follow their root cadence only; no child recurrence editor is exposed.
- [ ] Ensure Calendar/task/widget/Today/search/Focus readers request only relevant bounded due-date ranges before observing tasks; inverse-map each effective child offset to nominal root dates, query concrete root/child date overrides, deduplicate and page within bounds. Children match independently with parent context and direct child IDs; inactive rows stay out of actionable projections.
- [ ] Before UI implementation, inspect Luma theme/tokens/components/scaffold and redesign docs as required by AGENTS.md; use the shared task editor and design system.
- [ ] Source-review the changed declarations/callers against F08-R01–R06 and record paths/decisions. Do not run compilation or tests.


### F08-I04 — Integrate reminder/archive contracts

- [ ] Reconcile parent/child reminder schedule/cancellation after family edits, skip/end, completion and deletion; a child uses its own due date/time and keeps the existing reminder retry policy.
- [ ] Round-trip series segments, child templates, per-occurrence child snapshots/order/importance/date offsets, overrides, lifecycle and completion history in portable archives. Preserve schema-14 non-recurring hierarchy as-is and restore pre-hierarchy task-only formats as roots; select the new archive version from the implementation baseline. Reject malformed families atomically and invalidate stale family commands on restore.
- [ ] Record next Room/archive versions from the actual implementation baseline; never assume a version is free. Keep device-local generation cursors/journals out of portable data.
- [ ] Source-review the changed declarations/callers against F08-R01–R06 and record paths/decisions. Do not run compilation or tests.

### F08-I05 — Source handoff

- [ ] Reconcile current source and TI-QA01 outcomes for the canonical task-family contract before handoff; record any needed API adaptation without duplicating task-family identity, commands or persistence.
- [ ] Review all changed files, call sites, permission/manifest declarations, EN/FR/FA resources and persistence/archive contracts for root and child occurrence families; confirm no parallel family model/command policy was introduced.
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

- [ ] Execute all F08-QA01–QA12 cases and every relevant shared matrix cell; scope dialogs, family snapshot/reset, large missed-range paging, independent child dates/importance, cross-surface counts, skip/end, completed-history immutability, DST travel and notification reconciliation.
- [ ] Record actual result, requirement mapping and artifact links in the scenario ledger. Source/compile/host passes cannot certify native/manual cases.
- [ ] Resolve defects, rerun owning and affected regression cases, and identify invalidated evidence. Leave blocked/unrun cases visible with precise prerequisites.
- [ ] Mark complete only for the exact validated scope when required cases pass. Assessment/deferred branch closure must explicitly state that feature runtime acceptance has not occurred. Record release blockers/authorized exceptions without silently self-waiving.

Suggested future commit grouping: one source handoff followed by QA tests, generated schemas and evidence; do not commit unrelated user changes. Creating these documents does not authorize implementation, release or commit actions now.
