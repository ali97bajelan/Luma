# F13 — Optional sleep and workout imports Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use delegation only if separately authorized. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Investigate and deliver separately opt-in sleep/workout summaries only where native provider support and user value are established.

**Architecture:** Preserve the steps adapter and permission boundary. Extend health contracts by record kind with independent access/enable state; source-normalize sleep/workout summaries before idempotent persistence. The capability investigation is a source-design deliverable inside implementation, not a third phase.

**Tech Stack:** Existing Kotlin, pure domain contracts, Room, Compose, Java 17 and native Android adapters as appropriate; candidate dependency changes must be documented before integration.

**Spec:** [Optional sleep and workout imports specification](../specs/2026-10-26-roadmap-13-health-imports.md)

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../specs/2026-10-26-roadmap-13-health-imports.md) · [Plan](2026-10-26-roadmap-13-health-imports.md) · [QA scenarios](../../feature-roadmap/13-health-imports-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Global constraints

- Exactly two phases per task: implementation, then QA; do not interleave a test cycle into implementation.
- During implementation: no Gradle, sync, resolution, build, lint, schema generation, test execution, emulator or device. Test authoring belongs to QA.
- Preserve existing personal data/user edits, module boundaries, minimum API28 and local-first/offline defaults; follow the spec for any explicit compatibility exception.
- Calendar stays tasks/memories only; preserve navigation, drafts, revision checks, photo ownership and EN/FR/FA/RTL/light/dark support.
- Read [WORKFLOW.md](../../feature-roadmap/WORKFLOW.md) for exact isolation, source handoff and QA result/evidence obligations.

Dependencies: Task 02 real-step-provider acceptance first; task 14 compatibility work only if needed for selected APIs.

## Review focus

- F13-R01 (Capability gate): execute F13-QA01, F13-QA08; author assertion-level regressions in Q01 for the specified conditions.
- F13-R02 (Granular opt-in): execute F13-QA02, F13-QA04, F13-QA08; author assertion-level regressions in Q01 for the specified conditions.
- F13-R03 (Identity and corrections): execute F13-QA03, F13-QA04; author assertion-level regressions in Q01 for the specified conditions.
- F13-R04 (Time and overlap): execute F13-QA05, F13-QA06; author assertion-level regressions in Q01 for the specified conditions.
- F13-R05 (Presentation and restore): execute F13-QA07, F13-QA08; author assertion-level regressions in Q01 for the specified conditions.

## File map and interfaces

Existing paths are source anchors, not a request to edit every file regardless of need. New paths are proposed ownership boundaries; reconcile with existing equivalents before creating duplicates.

- Modify/review: [integration/health/src/main/kotlin/com/luma/lifeos/integration/health/HealthConnectPlatform.kt](../../../integration/health/src/main/kotlin/com/luma/lifeos/integration/health/HealthConnectPlatform.kt)
- Modify/review: [integration/health/src/main/kotlin/com/luma/lifeos/integration/health/AndroidHealthSource.kt](../../../integration/health/src/main/kotlin/com/luma/lifeos/integration/health/AndroidHealthSource.kt)
- Modify/review: [core/domain/src/main/kotlin/com/luma/lifeos/domain/health](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/health)
- Modify/review: [core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomHealthRepository.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomHealthRepository.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/health/HealthSettingsViewModel.kt](../../../app/src/main/java/com/luma/lifeos/ui/health/HealthSettingsViewModel.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/health/HealthSettingsCard.kt](../../../app/src/main/java/com/luma/lifeos/ui/health/HealthSettingsCard.kt)
- Modify/review: [integration/health/src/main/AndroidManifest.xml](../../../integration/health/src/main/AndroidManifest.xml)
- Modify/review: [gradle/libs.versions.toml](../../../gradle/libs.versions.toml)
- Create if required by selected implementation: `core/domain/src/main/kotlin/com/luma/lifeos/domain/health/HealthSessionImport.kt`
- Create if required by selected implementation: `integration/health/src/main/kotlin/com/luma/lifeos/integration/health/HealthSessionSource.kt`
- Create if required by selected implementation: `core/data/src/main/kotlin/com/luma/lifeos/data/health/HealthSessionReconciler.kt`

Interfaces consumed/produced:

- Proposed `HealthImportKind` STEPS, SLEEP, WORKOUT; state distinguishes unavailable/update-required/access-required/enabled/error independently by kind.
- Proposed `ImportedHealthSession(kind, providerRecordId, dataOrigin, start: Instant, end: Instant, updatedAt: Instant?, attributes)` uses kind+origin+provider ID identity; end must be >=start.
- Proposed `importSessions(kind, window, revision)` atomically publishes records and checkpoint only while enable/access revision remains current.

QA-owned tests (create only in phase 2; extend existing equivalent suites instead of duplicating them where practical):

- QA create/extend: `core/domain/src/test/kotlin/com/luma/lifeos/domain/roadmap/F13ContractTest.kt`
- QA create/extend: `core/data/src/androidTest/kotlin/com/luma/lifeos/data/roadmap/F13PersistenceTest.kt`
- QA create/extend: `app/src/test/java/com/luma/lifeos/roadmap/F13StateTest.kt`
- QA create/extend: `app/src/androidTest/java/com/luma/lifeos/roadmap/F13FlowTest.kt`
- QA create/extend: `integration/health/src/test/kotlin/com/luma/lifeos/integration/health/roadmap/F13ProviderTest.kt`

## Phase 1 — Implementation

Initial status: **PENDING**.

### F13-I01 — Write capability/demand decision

- [ ] Inspect official Health Connect records/permissions and current pinned SDK source; document supported native fields, consent/tooling and concrete desired workflows. Record source links/date and a supported implementation or explicit deferred outcome before integration code.
- [ ] Source-review the changed declarations/callers against F13-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F13-I02 — Extend typed source contracts

- [ ] Supported branch only: create kind-specific session/import values and source adapter with independent access state. Preserve steps aggregation and avoid background permissions or unrelated upgrades. Deferred branch: record N/A with I01 capability/demand evidence; do not create placeholder adapters.
- [ ] Source-review the changed declarations/callers against F13-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F13-I03 — Implement idempotent reconciliation

- [ ] Supported branch only: add source identity indexes, saved-zone attribution, complete-window correction/deletion and atomic enable/access revisions, with migration/archive adapters. Never infer deletion from failed query. Deferred branch: record N/A linked to I01; do not add unused tables or archive versions.
- [ ] Source-review the changed declarations/callers against F13-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F13-I04 — Expose per-kind controls and evidence

- [ ] Supported branch only: add optional enable/read rationale, status/retry, source-labelled details and Review contribution. Deferred branch: record production UI work N/A and deliver recommendation with no new toggles or behavior. I05 may hand off investigation-only scope after I02–I04 have explicit N/A rationale; feature implementation and native QA remain deferred.
- [ ] Source-review the changed declarations/callers against F13-R01–R05 and record paths/decisions. Do not run compilation or tests.

### F13-I05 — Source handoff

- [ ] Review all changed files, call sites, permission/manifest declarations, EN/FR/FA resources and persistence/archive contracts for the actual selected scope.
- [ ] Run source-only checks such as `git diff --check`, file/link checks and XML parsing where relevant. A successful static check is not a test/build pass.
- [ ] Record Git/patch identity, implementation decisions, source-to-requirement mapping and known uncertainty. Generated schemas and all runtime verification remain pending QA.
- [ ] Mark phase 1 **SOURCE COMPLETE — QA PENDING** only after I01–I04 and this handoff are complete. Do not mark the task complete or run a Gradle/emulator command here.

## Phase 2 — QA

Initial status: **NOT RUN**. Entry requires I01–I05 handoff and isolated target verification. This phase owns test authoring, Gradle, schemas, emulator/device use and defect retesting.

### F13-Q01 — Fixtures and automated coverage

- [ ] Read the full [QA scenarios](../../feature-roadmap/13-health-imports-qa.md), prepare its synthetic fixture, and create the QA-owned tests above. Give tests descriptive scenario-matching names; assert expected IDs/values and failure atomicity independently of implementation helpers.
- [ ] Cover every requirement and review-focus condition. Prefer small deterministic domain/state tests, real Room migration/transaction tests for persistence and Compose/native flows for platform behavior. Existing test passes cannot replace missing native or manual coverage.
- [ ] If a QA defect needs production correction, retain a failing regression and capture failure before correction where feasible; fix and rerun here without rewriting expected behavior to match a defect.
- [ ] For an explicitly deferred investigation/no-change branch, do not author tests that pretend new behavior exists. Record the branch evidence, mark inapplicable feature assertions N/A with rationale, and run the applicable existing baseline gates below. This accepts assessment scope only; feature runtime QA stays deferred.

### F13-Q02 — Gradle, schemas and affected regressions

Run from repository root **only in QA**, after reading the shared command/isolation policy:

```sh
bash gradlew -PdomainOnly=true :core:domain:test
bash gradlew :core:data:testDebugUnitTest :app:testDebugUnitTest :integration:health:testDebugUnitTest
bash gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :core:data:assembleDebugAndroidTest :core:data:lintDebug :integration:health:lintDebug
```

- [ ] Record actual commands, exit codes and JUnit/lint outcomes. Expected automated gate: exit 0; required tests actually executed with no failures/errors; skipped tests explained; no lint errors/fatals and new warnings classified.
- [ ] Where schema changed, generate/export current Room schema through QA build/KSP, inspect against hand-written migration, preserve old JSON and execute populated-upgrade plus archive compatibility cases. Commit schema evidence with the validated task, not as an unverified phase-1 claim.
- [ ] Run affected instrumented suites only on a confirmed isolated package/AVD using the shared QA catalogue; do not assume current debug package is isolated. Test-class filters and variant commands must match authored test packages and target identity.

### F13-Q03 — Scenarios, native behavior and acceptance

- [ ] Execute all F13-QA01–QA08 cases and every relevant shared matrix cell; Native Health Connect provider, granular consent/revocation and real synthetic source corrections; mocks alone cannot certify.
- [ ] Record actual result, requirement mapping and artifact links in the scenario ledger. Source/compile/host passes cannot certify native/manual cases.
- [ ] Resolve defects, rerun owning and affected regression cases, and identify invalidated evidence. Leave blocked/unrun cases visible with precise prerequisites.
- [ ] Mark complete only for the exact validated scope when required cases pass. Assessment/deferred branch closure must explicitly state that feature runtime acceptance has not occurred. Record release blockers/authorized exceptions without silently self-waiving.

Suggested future commit grouping: one source handoff followed by QA tests, generated schemas and evidence; do not commit unrelated user changes. Creating these documents does not authorize implementation, release or commit actions now.
