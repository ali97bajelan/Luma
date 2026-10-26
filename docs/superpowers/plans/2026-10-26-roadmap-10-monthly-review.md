# F10 — Monthly reflection and review notes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use delegation only if separately authorized. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend deterministic reflection to calendar months and let users save private notes about a review period.

**Architecture:** Generalize period calculation without breaking WeeklyReviewRepository. Reuse saved-observation aggregates and source drill-down; store user notes separately from rebuildable derived summaries.

**Tech Stack:** Existing Kotlin, pure domain contracts, Room, Compose, Java 17 and native Android adapters as appropriate; candidate dependency changes must be documented before integration.

**Spec:** [Monthly reflection and review notes specification](../specs/2026-10-26-roadmap-10-monthly-review.md)

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../specs/2026-10-26-roadmap-10-monthly-review.md) · [Plan](2026-10-26-roadmap-10-monthly-review.md) · [QA scenarios](../../feature-roadmap/10-monthly-review-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Global constraints

- Exactly two phases per task: implementation, then QA; do not interleave a test cycle into implementation.
- During implementation: no Gradle, sync, resolution, build, lint, schema generation, test execution, emulator or device. Test authoring belongs to QA.
- Preserve existing personal data/user edits, module boundaries, minimum API28 and local-first/offline defaults; follow the spec for any explicit compatibility exception.
- Calendar stays tasks/memories only; preserve navigation, drafts, revision checks, photo ownership and EN/FR/FA/RTL/light/dark support.
- Read [WORKFLOW.md](../../feature-roadmap/WORKFLOW.md) for exact isolation, source handoff and QA result/evidence obligations.

Dependencies: Tasks 02 and 03 performance discipline; task 01 for note persistence/backup.

## Review focus

- F10-R01 (Period comparisons): execute F10-QA01, F10-QA02, F10-QA08; author assertion-level regressions in Q01 for the specified conditions.
- F10-R02 (Metric truthfulness): execute F10-QA03, F10-QA04, F10-QA08; author assertion-level regressions in Q01 for the specified conditions.
- F10-R03 (Review notes): execute F10-QA05, F10-QA08; author assertion-level regressions in Q01 for the specified conditions.
- F10-R04 (Freshness and persistence): execute F10-QA05, F10-QA06; author assertion-level regressions in Q01 for the specified conditions.
- F10-R05 (Performance and UI): execute F10-QA07, F10-QA08; author assertion-level regressions in Q01 for the specified conditions.

## File map and interfaces

Existing paths are source anchors, not a request to edit every file regardless of need. New paths are proposed ownership boundaries; reconcile with existing equivalents before creating duplicates.

- Modify/review: [core/domain/src/main/kotlin/com/luma/lifeos/domain/review/WeeklyReview.kt](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/review/WeeklyReview.kt)
- Modify/review: [core/domain/src/main/kotlin/com/luma/lifeos/domain/repository/WeeklyReviewRepository.kt](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/repository/WeeklyReviewRepository.kt)
- Modify/review: [core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomWeeklyReviewRepository.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomWeeklyReviewRepository.kt)
- Modify/review: [core/data/src/main/kotlin/com/luma/lifeos/data/review/ReviewSql.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/review/ReviewSql.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/review/WeeklyReviewRoute.kt](../../../app/src/main/java/com/luma/lifeos/ui/review/WeeklyReviewRoute.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/review/ReviewSourcesRoute.kt](../../../app/src/main/java/com/luma/lifeos/ui/review/ReviewSourcesRoute.kt)
- Modify/review: [core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupRows.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupRows.kt)
- Create if required by selected implementation: `core/domain/src/main/kotlin/com/luma/lifeos/domain/review/MonthlyReview.kt`
- Create if required by selected implementation: `core/domain/src/main/kotlin/com/luma/lifeos/domain/review/ReviewNoteRepository.kt`
- Create if required by selected implementation: `core/data/src/main/kotlin/com/luma/lifeos/data/review/RoomReviewNoteRepository.kt`
- Create if required by selected implementation: `app/src/main/java/com/luma/lifeos/ui/review/MonthlyReviewRoute.kt`

Interfaces consumed/produced:

- Proposed `MonthlyReviewPeriod(month: YearMonth, zone: ZoneId, today: LocalDate)` with current/previous inclusive ranges.
- Proposed `ReviewNoteKey(kind: WEEK|MONTH, start: LocalDate, zoneId: String)` and `saveNote(key, expectedRevision: UUID?, nextRevision: UUID, body: String)`; body <=10,000 Unicode code points, blank deletes only after explicit confirmation.
- Proposed `MonthlyReviewRepository.observe(period: MonthlyReviewPeriod): Flow<MonthlyReview>` and source pages capped at 50; existing weekly signatures stay available.

QA-owned tests (create only in phase 2; extend existing equivalent suites instead of duplicating them where practical):

- QA create/extend: `core/domain/src/test/kotlin/com/luma/lifeos/domain/roadmap/F10ContractTest.kt`
- QA create/extend: `core/data/src/androidTest/kotlin/com/luma/lifeos/data/roadmap/F10PersistenceTest.kt`
- QA create/extend: `app/src/test/java/com/luma/lifeos/roadmap/F10StateTest.kt`
- QA create/extend: `app/src/androidTest/java/com/luma/lifeos/roadmap/F10FlowTest.kt`

## Phase 1 — Implementation

Initial status: **PENDING**.

### F10-I01 — Define month and note contracts

- [ ] Create MonthlyReview period/comparison functions and ReviewNote repository with explicit keys, code-point limit and revision semantics. Preserve existing weekly API through adaptation.
- [ ] Source-review the changed declarations/callers against F10-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F10-I02 — Implement aggregate and source reads

- [ ] Add month queries/reuse shared aggregate logic without joining duplicate source rows. Bound source pages, cancel stale periods and expose independent optional integration availability.
- [ ] Source-review the changed declarations/callers against F10-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F10-I03 — Persist notes and archive compatibility

- [ ] Add user-note entity/DAO, incremental migration and portable adapter; exclude only derived caches, never user notes. Add safe note draft/edit/delete handling.
- [ ] Source-review the changed declarations/callers against F10-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F10-I04 — Integrate Review surfaces

- [ ] Add week/month mode, matching-period explanations, partial labels, source routes and note editor. Keep deterministic metrics and actual source identity; document benchmark inputs for QA.
- [ ] Source-review the changed declarations/callers against F10-R01–R05 and record paths/decisions. Do not run compilation or tests.

### F10-I05 — Source handoff

- [ ] Review all changed files, call sites, permission/manifest declarations, EN/FR/FA resources and persistence/archive contracts for the actual selected scope.
- [ ] Run source-only checks such as `git diff --check`, file/link checks and XML parsing where relevant. A successful static check is not a test/build pass.
- [ ] Record Git/patch identity, implementation decisions, source-to-requirement mapping and known uncertainty. Generated schemas and all runtime verification remain pending QA.
- [ ] Mark phase 1 **SOURCE COMPLETE — QA PENDING** only after I01–I04 and this handoff are complete. Do not mark the task complete or run a Gradle/emulator command here.

## Phase 2 — QA

Initial status: **NOT RUN**. Entry requires I01–I05 handoff and isolated target verification. This phase owns test authoring, Gradle, schemas, emulator/device use and defect retesting.

### F10-Q01 — Fixtures and automated coverage

- [ ] Read the full [QA scenarios](../../feature-roadmap/10-monthly-review-qa.md), prepare its synthetic fixture, and create the QA-owned tests above. Give tests descriptive scenario-matching names; assert expected IDs/values and failure atomicity independently of implementation helpers.
- [ ] Cover every requirement and review-focus condition. Prefer small deterministic domain/state tests, real Room migration/transaction tests for persistence and Compose/native flows for platform behavior. Existing test passes cannot replace missing native or manual coverage.
- [ ] If a QA defect needs production correction, retain a failing regression and capture failure before correction where feasible; fix and rerun here without rewriting expected behavior to match a defect.
- [ ] For an explicitly deferred investigation/no-change branch, do not author tests that pretend new behavior exists. Record the branch evidence, mark inapplicable feature assertions N/A with rationale, and run the applicable existing baseline gates below. This accepts assessment scope only; feature runtime QA stays deferred.

### F10-Q02 — Gradle, schemas and affected regressions

Run from repository root **only in QA**, after reading the shared command/isolation policy:

```sh
bash gradlew -PdomainOnly=true :core:domain:test
bash gradlew :core:data:testDebugUnitTest :app:testDebugUnitTest
bash gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :core:data:assembleDebugAndroidTest :core:data:lintDebug
```

- [ ] Record actual commands, exit codes and JUnit/lint outcomes. Expected automated gate: exit 0; required tests actually executed with no failures/errors; skipped tests explained; no lint errors/fatals and new warnings classified.
- [ ] Where schema changed, generate/export current Room schema through QA build/KSP, inspect against hand-written migration, preserve old JSON and execute populated-upgrade plus archive compatibility cases. Commit schema evidence with the validated task, not as an unverified phase-1 claim.
- [ ] Run affected instrumented suites only on a confirmed isolated package/AVD using the shared QA catalogue; do not assume current debug package is isolated. Test-class filters and variant commands must match authored test packages and target identity.

### F10-Q03 — Scenarios, native behavior and acceptance

- [ ] Execute all F10-QA01–QA08 cases and every relevant shared matrix cell; Representative-phone aggregation performance, long source lists, review-note drafts and multilingual month presentation.
- [ ] Record actual result, requirement mapping and artifact links in the scenario ledger. Source/compile/host passes cannot certify native/manual cases.
- [ ] Resolve defects, rerun owning and affected regression cases, and identify invalidated evidence. Leave blocked/unrun cases visible with precise prerequisites.
- [ ] Mark complete only for the exact validated scope when required cases pass. Assessment/deferred branch closure must explicitly state that feature runtime acceptance has not occurred. Record release blockers/authorized exceptions without silently self-waiving.

Suggested future commit grouping: one source handoff followed by QA tests, generated schemas and evidence; do not commit unrelated user changes. Creating these documents does not authorize implementation, release or commit actions now.
