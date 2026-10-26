# F12 — Selective readable exports Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use delegation only if separately authorized. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Export explicitly selected journals and memories as readable Markdown or plain text without exposing unselected records.

**Architecture:** Create a dedicated read-only export selection/formatter service separate from replacement backup. Snapshot selected records for consistent output; use system create-document and explicit user share actions with private temporary file ownership.

**Tech Stack:** Existing Kotlin, pure domain contracts, Room, Compose, Java 17 and native Android adapters as appropriate; candidate dependency changes must be documented before integration.

**Spec:** [Selective readable exports specification](../specs/2026-10-26-roadmap-12-readable-exports.md)

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../specs/2026-10-26-roadmap-12-readable-exports.md) · [Plan](2026-10-26-roadmap-12-readable-exports.md) · [QA scenarios](../../feature-roadmap/12-readable-exports-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Global constraints

- Exactly two phases per task: implementation, then QA; do not interleave a test cycle into implementation.
- During implementation: no Gradle, sync, resolution, build, lint, schema generation, test execution, emulator or device. Test authoring belongs to QA.
- Preserve existing personal data/user edits, module boundaries, minimum API28 and local-first/offline defaults; follow the spec for any explicit compatibility exception.
- Calendar stays tasks/memories only; preserve navigation, drafts, revision checks, photo ownership and EN/FR/FA/RTL/light/dark support.
- Read [WORKFLOW.md](../../feature-roadmap/WORKFLOW.md) for exact isolation, source handoff and QA result/evidence obligations.

Dependencies: Task 01 safe file handling; task 06 privacy gate if installed.

## Review focus

- F12-R01 (Selection): execute F12-QA01, F12-QA04; author assertion-level regressions in Q01 for the specified conditions.
- F12-R02 (Privacy defaults): execute F12-QA02, F12-QA06; author assertion-level regressions in Q01 for the specified conditions.
- F12-R03 (Output format): execute F12-QA03; author assertion-level regressions in Q01 for the specified conditions.
- F12-R04 (Safe file lifecycle): execute F12-QA04, F12-QA05, F12-QA06, F12-QA07; author assertion-level regressions in Q01 for the specified conditions.
- F12-R05 (Scope and reachability): execute F12-QA06, F12-QA07, F12-QA08; author assertion-level regressions in Q01 for the specified conditions.

## File map and interfaces

Existing paths are source anchors, not a request to edit every file regardless of need. New paths are proposed ownership boundaries; reconcile with existing equivalents before creating duplicates.

- Modify/review: [app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt](../../../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/settings/SettingsRoute.kt](../../../app/src/main/java/com/luma/lifeos/ui/settings/SettingsRoute.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/journal/JournalDetailRoute.kt](../../../app/src/main/java/com/luma/lifeos/ui/journal/JournalDetailRoute.kt)
- Modify/review: [core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomJournalRepository.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomJournalRepository.kt)
- Modify/review: [core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomMemoryRepository.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomMemoryRepository.kt)
- Create if required by selected implementation: `core/domain/src/main/kotlin/com/luma/lifeos/domain/export/ReadableExport.kt`
- Create if required by selected implementation: `core/data/src/main/kotlin/com/luma/lifeos/data/export/RoomReadableExportService.kt`
- Create if required by selected implementation: `app/src/main/java/com/luma/lifeos/ui/export/ReadableExportRoute.kt`
- Create if required by selected implementation: `app/src/main/java/com/luma/lifeos/export/ReadableExportWriter.kt`

Interfaces consumed/produced:

- Proposed `ReadableExportSelection(from: LocalDate?, through: LocalDate?, zone: ZoneId, ids: Set<UUID>?, includeNames: Boolean=false)`; export journal/memory records only.
- Proposed `ReadableExportService.prepare(selection): PreparedReadableExport` with exact preview count and `write(format: MARKDOWN|TEXT, output: OutputStream)` over an immutable bounded snapshot.
- Event ID deduplication means a marked journal exports once. Header carries ISO-8601 occurrence offset and zone ID; original user body is preserved as text.

QA-owned tests (create only in phase 2; extend existing equivalent suites instead of duplicating them where practical):

- QA create/extend: `core/domain/src/test/kotlin/com/luma/lifeos/domain/roadmap/F12ContractTest.kt`
- QA create/extend: `core/data/src/androidTest/kotlin/com/luma/lifeos/data/roadmap/F12PersistenceTest.kt`
- QA create/extend: `app/src/test/java/com/luma/lifeos/roadmap/F12StateTest.kt`
- QA create/extend: `app/src/androidTest/java/com/luma/lifeos/roadmap/F12FlowTest.kt`

## Phase 1 — Implementation

Initial status: **PENDING**.

### F12-I01 — Define selection and formatter contracts

- [ ] Create ReadableExport.kt with inclusive range validation, typed selection, deduplication, privacy options and deterministic output metadata rules.
- [ ] Source-review the changed declarations/callers against F12-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F12-I02 — Implement immutable snapshot reads

- [ ] Add RoomReadableExportService over selected journals/memories with transactional membership/content snapshot; cap resources consistently and reject insufficient storage before claiming readiness.
- [ ] Source-review the changed declarations/callers against F12-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F12-I03 — Implement Markdown/text writing

- [ ] Add streaming UTF-8 formatter, safe metadata escaping, exact timestamp/zone display, output-close success handling and provider cancellation; do not add CSV/photos implicitly.
- [ ] Source-review the changed declarations/callers against F12-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F12-I04 — Expose preview and explicit export/share

- [ ] Wire detail/range entry points, count/range/privacy confirmation, lock gate, create-document/share chooser and private temporary-cache cleanup. Keep replacement BackupService independent.
- [ ] Source-review the changed declarations/callers against F12-R01–R05 and record paths/decisions. Do not run compilation or tests.

### F12-I05 — Source handoff

- [ ] Review all changed files, call sites, permission/manifest declarations, EN/FR/FA resources and persistence/archive contracts for the actual selected scope.
- [ ] Run source-only checks such as `git diff --check`, file/link checks and XML parsing where relevant. A successful static check is not a test/build pass.
- [ ] Record Git/patch identity, implementation decisions, source-to-requirement mapping and known uncertainty. Generated schemas and all runtime verification remain pending QA.
- [ ] Mark phase 1 **SOURCE COMPLETE — QA PENDING** only after I01–I04 and this handoff are complete. Do not mark the task complete or run a Gradle/emulator command here.

## Phase 2 — QA

Initial status: **NOT RUN**. Entry requires I01–I05 handoff and isolated target verification. This phase owns test authoring, Gradle, schemas, emulator/device use and defect retesting.

### F12-Q01 — Fixtures and automated coverage

- [ ] Read the full [QA scenarios](../../feature-roadmap/12-readable-exports-qa.md), prepare its synthetic fixture, and create the QA-owned tests above. Give tests descriptive scenario-matching names; assert expected IDs/values and failure atomicity independently of implementation helpers.
- [ ] Cover every requirement and review-focus condition. Prefer small deterministic domain/state tests, real Room migration/transaction tests for persistence and Compose/native flows for platform behavior. Existing test passes cannot replace missing native or manual coverage.
- [ ] If a QA defect needs production correction, retain a failing regression and capture failure before correction where feasible; fix and rerun here without rewriting expected behavior to match a defect.
- [ ] For an explicitly deferred investigation/no-change branch, do not author tests that pretend new behavior exists. Record the branch evidence, mark inapplicable feature assertions N/A with rationale, and run the applicable existing baseline gates below. This accepts assessment scope only; feature runtime QA stays deferred.

### F12-Q02 — Gradle, schemas and affected regressions

Run from repository root **only in QA**, after reading the shared command/isolation policy:

```sh
bash gradlew -PdomainOnly=true :core:domain:test
bash gradlew :core:data:testDebugUnitTest :app:testDebugUnitTest
bash gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :core:data:assembleDebugAndroidTest :core:data:lintDebug
```

- [ ] Record actual commands, exit codes and JUnit/lint outcomes. Expected automated gate: exit 0; required tests actually executed with no failures/errors; skipped tests explained; no lint errors/fatals and new warnings classified.
- [ ] Where schema changed, generate/export current Room schema through QA build/KSP, inspect against hand-written migration, preserve old JSON and execute populated-upgrade plus archive compatibility cases. Commit schema evidence with the validated task, not as an unverified phase-1 claim.
- [ ] Run affected instrumented suites only on a confirmed isolated package/AVD using the shared QA catalogue; do not assume current debug package is isolated. Test-class filters and variant commands must match authored test packages and target identity.

### F12-Q03 — Scenarios, native behavior and acceptance

- [ ] Execute all F12-QA01–QA08 cases and every relevant shared matrix cell; System create-document/share chooser, real text readers, range privacy inspection and large output lifecycle.
- [ ] Record actual result, requirement mapping and artifact links in the scenario ledger. Source/compile/host passes cannot certify native/manual cases.
- [ ] Resolve defects, rerun owning and affected regression cases, and identify invalidated evidence. Leave blocked/unrun cases visible with precise prerequisites.
- [ ] Mark complete only for the exact validated scope when required cases pass. Assessment/deferred branch closure must explicitly state that feature runtime acceptance has not occurred. Record release blockers/authorized exceptions without silently self-waiving.

Suggested future commit grouping: one source handoff followed by QA tests, generated schemas and evidence; do not commit unrelated user changes. Creating these documents does not authorize implementation, release or commit actions now.
