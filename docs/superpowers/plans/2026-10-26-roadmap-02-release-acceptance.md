# F02 — Current release and Calendar acceptance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use delegation only if separately authorized. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce current, revision-bound release acceptance for existing features and the tasks-and-memories Calendar.

**Architecture:** Use the existing Calendar QA package as the authoritative behavioral baseline. Consolidate outstanding checks into one evidence ledger; do not create a second Calendar implementation or change its membership to satisfy tests.

**Tech Stack:** Existing Kotlin, pure domain contracts, Room, Compose, Java 17 and native Android adapters as appropriate; candidate dependency changes must be documented before integration.

**Spec:** [Current release and Calendar acceptance specification](../specs/2026-10-26-roadmap-02-release-acceptance.md)

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../specs/2026-10-26-roadmap-02-release-acceptance.md) · [Plan](2026-10-26-roadmap-02-release-acceptance.md) · [QA scenarios](../../feature-roadmap/02-release-acceptance-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Global constraints

- Exactly two phases per task: implementation, then QA; do not interleave a test cycle into implementation.
- During implementation: no Gradle, sync, resolution, build, lint, schema generation, test execution, emulator or device. Test authoring belongs to QA.
- Preserve existing personal data/user edits, module boundaries, minimum API28 and local-first/offline defaults; follow the spec for any explicit compatibility exception.
- Calendar stays tasks/memories only; preserve navigation, drafts, revision checks, photo ownership and EN/FR/FA/RTL/light/dark support.
- Read [WORKFLOW.md](../../feature-roadmap/WORKFLOW.md) for exact isolation, source handoff and QA result/evidence obligations.

Dependencies: Task 01 for recovery release evidence; coordinate defects with task 07.

## Review focus

- F02-R01 (Scope freeze): execute F02-QA01, F02-QA08; author assertion-level regressions in Q01 for the specified conditions.
- F02-R02 (Calendar contract): execute F02-QA02; author assertion-level regressions in Q01 for the specified conditions.
- F02-R03 (Historical failures): execute F02-QA03; author assertion-level regressions in Q01 for the specified conditions.
- F02-R04 (Critical platform flows): execute F02-QA04, F02-QA05, F02-QA06, F02-QA07; author assertion-level regressions in Q01 for the specified conditions.
- F02-R05 (Release decision): execute F02-QA08; author assertion-level regressions in Q01 for the specified conditions.

## File map and interfaces

Existing paths are source anchors, not a request to edit every file regardless of need. New paths are proposed ownership boundaries; reconcile with existing equivalents before creating duplicates.

- Modify/review: [docs/CALENDAR_QA.md](../../CALENDAR_QA.md)
- Modify/review: [docs/CALENDAR_TASKS.md](../../CALENDAR_TASKS.md)
- Modify/review: [docs/validation/2026-10-25-calendar-agenda.md](../../validation/2026-10-25-calendar-agenda.md)
- Modify/review: [docs/TODO_ACCEPTANCE_2026-10-19.md](../../TODO_ACCEPTANCE_2026-10-19.md)
- Modify/review: [docs/VALIDATION.md](../../VALIDATION.md)
- Modify/review: [.github/workflows/android.yml](../../../.github/workflows/android.yml)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/calendar/CalendarViewModel.kt](../../../app/src/main/java/com/luma/lifeos/ui/calendar/CalendarViewModel.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt](../../../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt)
- Create if required by selected implementation: `docs/validation/roadmap-02-release-acceptance.md`

Interfaces consumed/produced:

- Evidence rows contain scenario ID, source revision plus dirty patch hash, build variant, device/API, fixture ID, command/action, expected/actual outcome and artifact link.
- Reuse CQA-01 through CQA-32 identifiers and their complete expected results; this package adds a release rollup, not replacement abbreviated checks.

QA-owned tests (create only in phase 2; extend existing equivalent suites instead of duplicating them where practical):

- QA create/extend: `core/domain/src/test/kotlin/com/luma/lifeos/domain/roadmap/F02ContractTest.kt`
- QA create/extend: `core/data/src/androidTest/kotlin/com/luma/lifeos/data/roadmap/F02PersistenceTest.kt`
- QA create/extend: `app/src/test/java/com/luma/lifeos/roadmap/F02StateTest.kt`
- QA create/extend: `app/src/androidTest/java/com/luma/lifeos/roadmap/F02FlowTest.kt`
- QA create/extend: `integration/health/src/test/kotlin/com/luma/lifeos/integration/health/roadmap/F02ProviderTest.kt`

## Phase 1 — Implementation

Initial status: **PENDING**.

### F02-I01 — Build the coverage ledger

- [ ] Create roadmap-02-release-acceptance.md with links to all CQA-01–32 and unresolved feature acceptance checks; initialize every new result NOT RUN.
- [ ] Source-review the changed declarations/callers against F02-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F02-I02 — Identify isolation and fixtures

- [ ] Write candidate identity fields, separate application/test package strategy, fixture inventory and representative-device matrix. Document how existing personal installs are excluded.
- [ ] Source-review the changed declarations/callers against F02-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F02-I03 — Map existing coverage

- [ ] Read existing test sources and old failure records; map them to full scenario requirements without marking any runtime result passed. List missing cases for QA authoring.
- [ ] Source-review the changed declarations/callers against F02-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F02-I04 — Freeze the QA handoff

- [ ] Document release smoke routes, data-reset boundaries and evidence paths. Production behavior changes are not part of this preparation phase; reproduced QA defects can be corrected under phase 2.
- [ ] Source-review the changed declarations/callers against F02-R01–R05 and record paths/decisions. Do not run compilation or tests.

### F02-I05 — Source handoff

- [ ] Review all changed files, call sites, permission/manifest declarations, EN/FR/FA resources and persistence/archive contracts for the actual selected scope.
- [ ] Run source-only checks such as `git diff --check`, file/link checks and XML parsing where relevant. A successful static check is not a test/build pass.
- [ ] Record Git/patch identity, implementation decisions, source-to-requirement mapping and known uncertainty. Generated schemas and all runtime verification remain pending QA.
- [ ] Mark phase 1 **SOURCE COMPLETE — QA PENDING** only after I01–I04 and this handoff are complete. Do not mark the task complete or run a Gradle/emulator command here.

## Phase 2 — QA

Initial status: **NOT RUN**. Entry requires I01–I05 handoff and isolated target verification. This phase owns test authoring, Gradle, schemas, emulator/device use and defect retesting.

### F02-Q01 — Fixtures and automated coverage

- [ ] Read the full [QA scenarios](../../feature-roadmap/02-release-acceptance-qa.md), prepare its synthetic fixture, and create the QA-owned tests above. Give tests descriptive scenario-matching names; assert expected IDs/values and failure atomicity independently of implementation helpers.
- [ ] Cover every requirement and review-focus condition. Prefer small deterministic domain/state tests, real Room migration/transaction tests for persistence and Compose/native flows for platform behavior. Existing test passes cannot replace missing native or manual coverage.
- [ ] If a QA defect needs production correction, retain a failing regression and capture failure before correction where feasible; fix and rerun here without rewriting expected behavior to match a defect.
- [ ] For an explicitly deferred investigation/no-change branch, do not author tests that pretend new behavior exists. Record the branch evidence, mark inapplicable feature assertions N/A with rationale, and run the applicable existing baseline gates below. This accepts assessment scope only; feature runtime QA stays deferred.

### F02-Q02 — Gradle, schemas and affected regressions

Run from repository root **only in QA**, after reading the shared command/isolation policy:

```sh
bash gradlew -PdomainOnly=true :core:domain:test
bash gradlew :core:data:testDebugUnitTest :app:testDebugUnitTest :integration:usage:testDebugUnitTest :integration:health:testDebugUnitTest
bash gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :core:data:assembleDebugAndroidTest :core:data:lintDebug :integration:usage:lintDebug :integration:health:lintDebug
```

- [ ] Record actual commands, exit codes and JUnit/lint outcomes. Expected automated gate: exit 0; required tests actually executed with no failures/errors; skipped tests explained; no lint errors/fatals and new warnings classified.
- [ ] Where schema changed, generate/export current Room schema through QA build/KSP, inspect against hand-written migration, preserve old JSON and execute populated-upgrade plus archive compatibility cases. Commit schema evidence with the validated task, not as an unverified phase-1 claim.
- [ ] Run affected instrumented suites only on a confirmed isolated package/AVD using the shared QA catalogue; do not assume current debug package is isolated. Test-class filters and variant commands must match authored test packages and target identity.

### F02-Q03 — Scenarios, native behavior and acceptance

- [ ] Execute all F02-QA01–QA08 cases and every relevant shared matrix cell; Complete CQA matrix, native permissions/provider fixtures, TalkBack, release smoke, pickers and reset on isolated devices.
- [ ] Record actual result, requirement mapping and artifact links in the scenario ledger. Source/compile/host passes cannot certify native/manual cases.
- [ ] Resolve defects, rerun owning and affected regression cases, and identify invalidated evidence. Leave blocked/unrun cases visible with precise prerequisites.
- [ ] Mark complete only for the exact validated scope when required cases pass. Assessment/deferred branch closure must explicitly state that feature runtime acceptance has not occurred. Record release blockers/authorized exceptions without silently self-waiving.

Suggested future commit grouping: one source handoff followed by QA tests, generated schemas and evidence; do not commit unrelated user changes. Creating these documents does not authorize implementation, release or commit actions now.
