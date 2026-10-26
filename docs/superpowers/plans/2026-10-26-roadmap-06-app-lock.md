# F06 — Optional app lock and private previews Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use delegation only if separately authorized. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Gate private app UI with system authentication while preserving drafts and intended destinations.

**Architecture:** Place a single lock coordinator above private Compose navigation. Use system biometric/device-credential authentication with API-appropriate fallback; enforce the same gate on activity, widget and notification entry paths. Device lock settings remain device-local.

**Tech Stack:** Existing Kotlin, pure domain contracts, Room, Compose, Java 17 and native Android adapters as appropriate; candidate dependency changes must be documented before integration.

**Spec:** [Optional app lock and private previews specification](../specs/2026-10-26-roadmap-06-app-lock.md)

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../specs/2026-10-26-roadmap-06-app-lock.md) · [Plan](2026-10-26-roadmap-06-app-lock.md) · [QA scenarios](../../feature-roadmap/06-app-lock-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Global constraints

- Exactly two phases per task: implementation, then QA; do not interleave a test cycle into implementation.
- During implementation: no Gradle, sync, resolution, build, lint, schema generation, test execution, emulator or device. Test authoring belongs to QA.
- Preserve existing personal data/user edits, module boundaries, minimum API28 and local-first/offline defaults; follow the spec for any explicit compatibility exception.
- Calendar stays tasks/memories only; preserve navigation, drafts, revision checks, photo ownership and EN/FR/FA/RTL/light/dark support.
- Read [WORKFLOW.md](../../feature-roadmap/WORKFLOW.md) for exact isolation, source handoff and QA result/evidence obligations.

Dependencies: Task 02; integrate task 04 notification destinations if available.

## Review focus

- F06-R01 (Enable and recovery): execute F06-QA01, F06-QA05; author assertion-level regressions in Q01 for the specified conditions.
- F06-R02 (Lifecycle and entry points): execute F06-QA02, F06-QA03; author assertion-level regressions in Q01 for the specified conditions.
- F06-R03 (Draft preservation): execute F06-QA03, F06-QA04; author assertion-level regressions in Q01 for the specified conditions.
- F06-R04 (Preview privacy): execute F06-QA06, F06-QA07; author assertion-level regressions in Q01 for the specified conditions.
- F06-R05 (External surfaces and restore): execute F06-QA07, F06-QA08; author assertion-level regressions in Q01 for the specified conditions.

## File map and interfaces

Existing paths are source anchors, not a request to edit every file regardless of need. New paths are proposed ownership boundaries; reconcile with existing equivalents before creating duplicates.

- Modify/review: [app/src/main/java/com/luma/lifeos/MainActivity.kt](../../../app/src/main/java/com/luma/lifeos/MainActivity.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt](../../../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/widget/WidgetLaunchViewModel.kt](../../../app/src/main/java/com/luma/lifeos/widget/WidgetLaunchViewModel.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/widget/WidgetActionReceiver.kt](../../../app/src/main/java/com/luma/lifeos/widget/WidgetActionReceiver.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/widget/WidgetActionWorker.kt](../../../app/src/main/java/com/luma/lifeos/widget/WidgetActionWorker.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/privacy/PrivacySettingsRoute.kt](../../../app/src/main/java/com/luma/lifeos/ui/privacy/PrivacySettingsRoute.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/privacy/AppDataReset.kt](../../../app/src/main/java/com/luma/lifeos/ui/privacy/AppDataReset.kt)
- Modify/review: [app/build.gradle.kts](../../../app/build.gradle.kts)
- Modify/review: [gradle/libs.versions.toml](../../../gradle/libs.versions.toml)
- Create if required by selected implementation: `app/src/main/java/com/luma/lifeos/privacy/AppLockCoordinator.kt`
- Create if required by selected implementation: `app/src/main/java/com/luma/lifeos/privacy/AppLockPreferences.kt`
- Create if required by selected implementation: `app/src/main/java/com/luma/lifeos/ui/privacy/AppLockSettingsCard.kt`

Interfaces consumed/produced:

- Proposed `AppLockState` is Disabled, Locked, Authenticating or Unlocked; `AppLockCoordinator.onForeground(nowElapsed: Long)`, `onBackground(nowElapsed: Long)` and `onAuthenticationResult(success: Boolean)` define transitions.
- Use monotonic elapsed time for same-process timeout, and always lock on cold process start when enabled. Proposed timeout choices: immediately, 1 minute (default), 5 minutes.
- Queue typed destinations and opaque pending-command tokens rather than arbitrary route text. Successful unlock opens the destination once; pending mutation is revalidated for revision/date/expiry and consumed at most once, never replayed blindly.

QA-owned tests (create only in phase 2; extend existing equivalent suites instead of duplicating them where practical):

- QA create/extend: `core/data/src/androidTest/kotlin/com/luma/lifeos/data/roadmap/F06PersistenceTest.kt`
- QA create/extend: `app/src/test/java/com/luma/lifeos/roadmap/F06StateTest.kt`
- QA create/extend: `app/src/androidTest/java/com/luma/lifeos/roadmap/F06FlowTest.kt`

## Phase 1 — Implementation

Initial status: **PENDING**.

### F06-I01 — Implement lock state and preferences

- [ ] Create coordinator/preferences with monotonic timeout and cold-start lock, no persisted authenticated flag, explicit credential-unavailable recovery and pending typed destination.
- [ ] Source-review the changed declarations/callers against F06-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F06-I02 — Integrate system authentication

- [ ] Add a pinned compatible AndroidX authentication dependency only if required. Implement API 28 fallback separately from API 30+ authenticator combinations; handle cancellation, lockout and credential-change callbacks.
- [ ] Source-review the changed declarations/callers against F06-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F06-I03 — Gate rendering and entry paths

- [ ] Install the gate before private navigation draws; route widget and notification navigation/mutation intents through it. Revalidate opaque pending commands after unlock with existing revision/idempotence checks. Mask recent-app snapshots and apply separate screenshot policy while preserving existing drafts.
- [ ] Source-review the changed declarations/callers against F06-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F06-I04 — Add localized controls and reset integration

- [ ] Expose enable/disable (authenticated), timeout and screenshot options. Document external widget visibility, generic locked notifications, device-local restore exclusions and confirmed reset recovery.
- [ ] Source-review the changed declarations/callers against F06-R01–R05 and record paths/decisions. Do not run compilation or tests.

### F06-I05 — Source handoff

- [ ] Review all changed files, call sites, permission/manifest declarations, EN/FR/FA resources and persistence/archive contracts for the actual selected scope.
- [ ] Run source-only checks such as `git diff --check`, file/link checks and XML parsing where relevant. A successful static check is not a test/build pass.
- [ ] Record Git/patch identity, implementation decisions, source-to-requirement mapping and known uncertainty. Generated schemas and all runtime verification remain pending QA.
- [ ] Mark phase 1 **SOURCE COMPLETE — QA PENDING** only after I01–I04 and this handoff are complete. Do not mark the task complete or run a Gradle/emulator command here.

## Phase 2 — QA

Initial status: **NOT RUN**. Entry requires I01–I05 handoff and isolated target verification. This phase owns test authoring, Gradle, schemas, emulator/device use and defect retesting.

### F06-Q01 — Fixtures and automated coverage

- [ ] Read the full [QA scenarios](../../feature-roadmap/06-app-lock-qa.md), prepare its synthetic fixture, and create the QA-owned tests above. Give tests descriptive scenario-matching names; assert expected IDs/values and failure atomicity independently of implementation helpers.
- [ ] Cover every requirement and review-focus condition. Prefer small deterministic domain/state tests, real Room migration/transaction tests for persistence and Compose/native flows for platform behavior. Existing test passes cannot replace missing native or manual coverage.
- [ ] If a QA defect needs production correction, retain a failing regression and capture failure before correction where feasible; fix and rerun here without rewriting expected behavior to match a defect.
- [ ] For an explicitly deferred investigation/no-change branch, do not author tests that pretend new behavior exists. Record the branch evidence, mark inapplicable feature assertions N/A with rationale, and run the applicable existing baseline gates below. This accepts assessment scope only; feature runtime QA stays deferred.

### F06-Q02 — Gradle, schemas and affected regressions

Run from repository root **only in QA**, after reading the shared command/isolation policy:

```sh
bash gradlew :app:testDebugUnitTest :core:data:testDebugUnitTest
bash gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :core:data:assembleDebugAndroidTest :core:data:lintDebug
```

- [ ] Record actual commands, exit codes and JUnit/lint outcomes. Expected automated gate: exit 0; required tests actually executed with no failures/errors; skipped tests explained; no lint errors/fatals and new warnings classified.
- [ ] Where schema changed, generate/export current Room schema through QA build/KSP, inspect against hand-written migration, preserve old JSON and execute populated-upgrade plus archive compatibility cases. Commit schema evidence with the validated task, not as an unverified phase-1 claim.
- [ ] Run affected instrumented suites only on a confirmed isolated package/AVD using the shared QA catalogue; do not assume current debug package is isolated. Test-class filters and variant commands must match authored test packages and target identity.

### F06-Q03 — Scenarios, native behavior and acceptance

- [ ] Execute all F06-QA01–QA08 cases and every relevant shared matrix cell; Real biometric/device-credential UI on API 28 and modern hardware, Recents snapshots, screenshots, widget and notification launches.
- [ ] Record actual result, requirement mapping and artifact links in the scenario ledger. Source/compile/host passes cannot certify native/manual cases.
- [ ] Resolve defects, rerun owning and affected regression cases, and identify invalidated evidence. Leave blocked/unrun cases visible with precise prerequisites.
- [ ] Mark complete only for the exact validated scope when required cases pass. Assessment/deferred branch closure must explicitly state that feature runtime acceptance has not occurred. Record release blockers/authorized exceptions without silently self-waiving.

Suggested future commit grouping: one source handoff followed by QA tests, generated schemas and evidence; do not commit unrelated user changes. Creating these documents does not authorize implementation, release or commit actions now.
