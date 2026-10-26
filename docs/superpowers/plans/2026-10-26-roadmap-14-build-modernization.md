# F14 — Build and dependency modernization assessment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use delegation only if separately authorized. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Select and validate only justified compatible build/dependency changes with reproducible release evidence.

**Architecture:** Create a baseline and staged compatibility decision before editing toolchain versions. Separate SDK/plugin/Kotlin/KSP/Compose/Room/Health changes into reviewable groups and preserve existing module boundaries and minimum supported device policy.

**Tech Stack:** Existing Kotlin, pure domain contracts, Room, Compose, Java 17 and native Android adapters as appropriate; candidate dependency changes must be documented before integration.

**Spec:** [Build and dependency modernization assessment specification](../specs/2026-10-26-roadmap-14-build-modernization.md)

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../specs/2026-10-26-roadmap-14-build-modernization.md) · [Plan](2026-10-26-roadmap-14-build-modernization.md) · [QA scenarios](../../feature-roadmap/14-build-modernization-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Global constraints

- Exactly two phases per task: implementation, then QA; do not interleave a test cycle into implementation.
- During implementation: no Gradle, sync, resolution, build, lint, schema generation, test execution, emulator or device. Test authoring belongs to QA.
- Preserve existing personal data/user edits, module boundaries, minimum API28 and local-first/offline defaults; follow the spec for any explicit compatibility exception.
- Calendar stays tasks/memories only; preserve navigation, drafts, revision checks, photo ownership and EN/FR/FA/RTL/light/dark support.
- Read [WORKFLOW.md](../../feature-roadmap/WORKFLOW.md) for exact isolation, source handoff and QA result/evidence obligations.

Dependencies: Task 02 provides current reproducible baseline; can be promoted for a verified release/security blocker.

## Review focus

- F14-R01 (Justified scope): execute F14-QA01, F14-QA08; author assertion-level regressions in Q01 for the specified conditions.
- F14-R02 (Compatibility matrix): execute F14-QA01, F14-QA02, F14-QA06; author assertion-level regressions in Q01 for the specified conditions.
- F14-R03 (Staged changes): execute F14-QA02, F14-QA03, F14-QA08; author assertion-level regressions in Q01 for the specified conditions.
- F14-R04 (Reproducibility and artifacts): execute F14-QA02, F14-QA08; author assertion-level regressions in Q01 for the specified conditions.
- F14-R05 (Release evidence): execute F14-QA03, F14-QA04, F14-QA05, F14-QA06, F14-QA07; author assertion-level regressions in Q01 for the specified conditions.

## File map and interfaces

Existing paths are source anchors, not a request to edit every file regardless of need. New paths are proposed ownership boundaries; reconcile with existing equivalents before creating duplicates.

- Modify/review: [app/build.gradle.kts](../../../app/build.gradle.kts)
- Modify/review: [build.gradle.kts](../../../build.gradle.kts)
- Modify/review: [settings.gradle.kts](../../../settings.gradle.kts)
- Modify/review: [gradle/libs.versions.toml](../../../gradle/libs.versions.toml)
- Modify/review: [gradle/wrapper/gradle-wrapper.properties](../../../gradle/wrapper/gradle-wrapper.properties)
- Modify/review: [core/data/build.gradle.kts](../../../core/data/build.gradle.kts)
- Modify/review: [integration/health/build.gradle.kts](../../../integration/health/build.gradle.kts)
- Modify/review: [.github/workflows/android.yml](../../../.github/workflows/android.yml)
- Modify/review: [app/proguard-rules.pro](../../../app/proguard-rules.pro)
- Create if required by selected implementation: `docs/BUILD_MODERNIZATION_ASSESSMENT_2026-10-26.md`

Interfaces consumed/produced:

- Assessment table rows: component/current value/candidate pinned value/official compatibility source and access date/reason/affected modules/rollback boundary/QA cases.
- Do not invent current latest versions in this package. The implementing engineer chooses pinned candidates from then-current official release notes in phase 1; actual resolution/build is exclusively phase 2.
- CI artifact identity includes Git/patch ID, dependency graph, JDK/SDK/Gradle versions, variant, APK hash, lint/test reports and R8 warnings.

QA-owned tests (create only in phase 2; extend existing equivalent suites instead of duplicating them where practical):

- QA create/extend: `core/domain/src/test/kotlin/com/luma/lifeos/domain/roadmap/F14ContractTest.kt`
- QA create/extend: `core/data/src/androidTest/kotlin/com/luma/lifeos/data/roadmap/F14PersistenceTest.kt`
- QA create/extend: `app/src/test/java/com/luma/lifeos/roadmap/F14StateTest.kt`
- QA create/extend: `app/src/androidTest/java/com/luma/lifeos/roadmap/F14FlowTest.kt`
- QA create/extend: `integration/health/src/test/kotlin/com/luma/lifeos/integration/health/roadmap/F14ProviderTest.kt`

## Phase 1 — Implementation

Initial status: **PENDING**.

### F14-I01 — Write baseline and candidate assessment

- [ ] Read all configured versions and known warnings, inspect current official compatibility/release sources, and create BUILD_MODERNIZATION_ASSESSMENT with exact pinned candidates or justified retain decisions. No version-resolution command in this phase.
- [ ] Source-review the changed declarations/callers against F14-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F14-I02 — Apply coherent configuration groups

- [ ] Edit version catalog/wrapper/plugins/SDK settings only for chosen compatible groups; document each group and rollback paths. Preserve app version/signing/private data and existing user changes.
- [ ] Source-review the changed declarations/callers against F14-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F14-I03 — Update CI and release rules

- [ ] Align CI SDK/JDK setup and artifact upload with selected configuration; change keep rules only for a specific source-supported reason and record intended minified paths.
- [ ] Source-review the changed declarations/callers against F14-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F14-I04 — Prepare comparative handoff

- [ ] List baseline/candidate variant identity, reproducible fixture/performance commands and warning expectations. Author no test execution claim; actual baseline and candidate builds/benchmarks run in QA.
- [ ] Source-review the changed declarations/callers against F14-R01–R05 and record paths/decisions. Do not run compilation or tests.

### F14-I05 — Source handoff

- [ ] Review all changed files, call sites, permission/manifest declarations, EN/FR/FA resources and persistence/archive contracts for the actual selected scope.
- [ ] Run source-only checks such as `git diff --check`, file/link checks and XML parsing where relevant. A successful static check is not a test/build pass.
- [ ] Record Git/patch identity, implementation decisions, source-to-requirement mapping and known uncertainty. Generated schemas and all runtime verification remain pending QA.
- [ ] Mark phase 1 **SOURCE COMPLETE — QA PENDING** only after I01–I04 and this handoff are complete. Do not mark the task complete or run a Gradle/emulator command here.

## Phase 2 — QA

Initial status: **NOT RUN**. Entry requires I01–I05 handoff and isolated target verification. This phase owns test authoring, Gradle, schemas, emulator/device use and defect retesting.

### F14-Q01 — Fixtures and automated coverage

- [ ] Read the full [QA scenarios](../../feature-roadmap/14-build-modernization-qa.md), prepare its synthetic fixture, and create the QA-owned tests above. Give tests descriptive scenario-matching names; assert expected IDs/values and failure atomicity independently of implementation helpers.
- [ ] Cover every requirement and review-focus condition. Prefer small deterministic domain/state tests, real Room migration/transaction tests for persistence and Compose/native flows for platform behavior. Existing test passes cannot replace missing native or manual coverage.
- [ ] If a QA defect needs production correction, retain a failing regression and capture failure before correction where feasible; fix and rerun here without rewriting expected behavior to match a defect.
- [ ] For an explicitly deferred investigation/no-change branch, do not author tests that pretend new behavior exists. Record the branch evidence, mark inapplicable feature assertions N/A with rationale, and run the applicable existing baseline gates below. This accepts assessment scope only; feature runtime QA stays deferred.

### F14-Q02 — Gradle, schemas and affected regressions

Run from repository root **only in QA**, after reading the shared command/isolation policy:

```sh
bash gradlew -PdomainOnly=true :core:domain:test
bash gradlew :core:data:testDebugUnitTest :app:testDebugUnitTest :integration:usage:testDebugUnitTest :integration:health:testDebugUnitTest
bash gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :core:data:assembleDebugAndroidTest :core:data:lintDebug :integration:usage:lintDebug :integration:health:lintDebug
```

- [ ] Record actual commands, exit codes and JUnit/lint outcomes. Expected automated gate: exit 0; required tests actually executed with no failures/errors; skipped tests explained; no lint errors/fatals and new warnings classified.
- [ ] Where schema changed, generate/export current Room schema through QA build/KSP, inspect against hand-written migration, preserve old JSON and execute populated-upgrade plus archive compatibility cases. Commit schema evidence with the validated task, not as an unverified phase-1 claim.
- [ ] Run affected instrumented suites only on a confirmed isolated package/AVD using the shared QA catalogue; do not assume current debug package is isolated. Test-class filters and variant commands must match authored test packages and target identity.

### F14-Q03 — Scenarios, native behavior and acceptance

- [ ] Execute all F14-QA01–QA08 cases and every relevant shared matrix cell; Clean-cache resolution, minimum API device, minified release, comparable performance and actual CI when available.
- [ ] Record actual result, requirement mapping and artifact links in the scenario ledger. Source/compile/host passes cannot certify native/manual cases.
- [ ] Resolve defects, rerun owning and affected regression cases, and identify invalidated evidence. Leave blocked/unrun cases visible with precise prerequisites.
- [ ] Mark complete only for the exact validated scope when required cases pass. Assessment/deferred branch closure must explicitly state that feature runtime acceptance has not occurred. Record release blockers/authorized exceptions without silently self-waiving.

Suggested future commit grouping: one source handoff followed by QA tests, generated schemas and evidence; do not commit unrelated user changes. Creating these documents does not authorize implementation, release or commit actions now.
