# F09 — Backup freshness and in-app nudges Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use delegation only if separately authorized. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show a truthful device-local last-export time and a dismissible reminder to make a manual backup.

**Architecture:** Persist only successful completion metadata in a device-local preference store after the complete export stream closes. Derive freshness from an injected clock and show a Settings/Today in-app nudge; do not schedule background export or notifications.

**Tech Stack:** Existing Kotlin, pure domain contracts, Room, Compose, Java 17 and native Android adapters as appropriate; candidate dependency changes must be documented before integration.

**Spec:** [Backup freshness and in-app nudges specification](../specs/2026-10-26-roadmap-09-backup-freshness.md)

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../specs/2026-10-26-roadmap-09-backup-freshness.md) · [Plan](2026-10-26-roadmap-09-backup-freshness.md) · [QA scenarios](../../feature-roadmap/09-backup-freshness-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Global constraints

- Exactly two phases per task: implementation, then QA; do not interleave a test cycle into implementation.
- During implementation: no Gradle, sync, resolution, build, lint, schema generation, test execution, emulator or device. Test authoring belongs to QA.
- Preserve existing personal data/user edits, module boundaries, minimum API28 and local-first/offline defaults; follow the spec for any explicit compatibility exception.
- Calendar stays tasks/memories only; preserve navigation, drafts, revision checks, photo ownership and EN/FR/FA/RTL/light/dark support.
- Read [WORKFLOW.md](../../feature-roadmap/WORKFLOW.md) for exact isolation, source handoff and QA result/evidence obligations.

Dependencies: Task 01; show encrypted action after task 05 is available. Task 04 is not required for in-app-only nudges.

## Review focus

- F09-R01 (Truthful status): execute F09-QA01, F09-QA02, F09-QA03, F09-QA08; author assertion-level regressions in Q01 for the specified conditions.
- F09-R02 (Nudge schedule): execute F09-QA01, F09-QA04; author assertion-level regressions in Q01 for the specified conditions.
- F09-R03 (Time corrections): execute F09-QA05; author assertion-level regressions in Q01 for the specified conditions.
- F09-R04 (Privacy and destination): execute F09-QA06, F09-QA07, F09-QA08; author assertion-level regressions in Q01 for the specified conditions.
- F09-R05 (Restore/reset and failure): execute F09-QA03, F09-QA06, F09-QA08; author assertion-level regressions in Q01 for the specified conditions.

## File map and interfaces

Existing paths are source anchors, not a request to edit every file regardless of need. New paths are proposed ownership boundaries; reconcile with existing equivalents before creating duplicates.

- Modify/review: [app/src/main/java/com/luma/lifeos/ui/backup/BackupViewModel.kt](../../../app/src/main/java/com/luma/lifeos/ui/backup/BackupViewModel.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/backup/BackupSettingsCard.kt](../../../app/src/main/java/com/luma/lifeos/ui/backup/BackupSettingsCard.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/today/TodayScreen.kt](../../../app/src/main/java/com/luma/lifeos/ui/today/TodayScreen.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/privacy/AppDataReset.kt](../../../app/src/main/java/com/luma/lifeos/ui/privacy/AppDataReset.kt)
- Create if required by selected implementation: `app/src/main/java/com/luma/lifeos/backup/BackupFreshnessStore.kt`
- Create if required by selected implementation: `app/src/main/java/com/luma/lifeos/backup/BackupNudgePolicy.kt`

Interfaces consumed/produced:

- Proposed `BackupSuccess(at: Instant, encrypted: Boolean)` contains no provider URI or record contents.
- Proposed `BackupFreshnessStore.recordSuccess(value: BackupSuccess)` and `observe(): Flow<BackupFreshnessState>`; record only after service completion AND output close.
- Proposed `shouldShowBackupNudge(now: Instant, lastSuccess: Instant?, snoozedUntil: Instant?, enabled: Boolean): Boolean`; default stale threshold 7 days and dismissal snooze 7 days.

QA-owned tests (create only in phase 2; extend existing equivalent suites instead of duplicating them where practical):

- QA create/extend: `app/src/test/java/com/luma/lifeos/roadmap/F09StateTest.kt`
- QA create/extend: `app/src/androidTest/java/com/luma/lifeos/roadmap/F09FlowTest.kt`

## Phase 1 — Implementation

Initial status: **PENDING**.

### F09-I01 — Add local metadata store

- [ ] Create BackupFreshnessStore with install anchor, last closed-export outcome, nudge preference and snoozedUntil; exclude from portable tables and clear via full reset.
- [ ] Source-review the changed declarations/callers against F09-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F09-I02 — Implement deterministic policy

- [ ] Create BackupNudgePolicy with inclusive 7-day thresholds, fresh-install grace, 7-day dismissal and future-clock uncertainty policy using injected Clock.
- [ ] Source-review the changed declarations/callers against F09-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F09-I03 — Integrate export completion

- [ ] Record metadata only after caller stream close succeeds; separate preference-write failure from archive success. Restore must not invoke recordSuccess.
- [ ] Source-review the changed declarations/callers against F09-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F09-I04 — Add status and nudge UI

- [ ] Expose device-local timestamp/plain/encrypted state and provider-existence caveat in Settings plus dismissible Today nudge; localize EN/FR/FA and preserve app-lock gate.
- [ ] Source-review the changed declarations/callers against F09-R01–R05 and record paths/decisions. Do not run compilation or tests.

### F09-I05 — Source handoff

- [ ] Review all changed files, call sites, permission/manifest declarations, EN/FR/FA resources and persistence/archive contracts for the actual selected scope.
- [ ] Run source-only checks such as `git diff --check`, file/link checks and XML parsing where relevant. A successful static check is not a test/build pass.
- [ ] Record Git/patch identity, implementation decisions, source-to-requirement mapping and known uncertainty. Generated schemas and all runtime verification remain pending QA.
- [ ] Mark phase 1 **SOURCE COMPLETE — QA PENDING** only after I01–I04 and this handoff are complete. Do not mark the task complete or run a Gradle/emulator command here.

## Phase 2 — QA

Initial status: **NOT RUN**. Entry requires I01–I05 handoff and isolated target verification. This phase owns test authoring, Gradle, schemas, emulator/device use and defect retesting.

### F09-Q01 — Fixtures and automated coverage

- [ ] Read the full [QA scenarios](../../feature-roadmap/09-backup-freshness-qa.md), prepare its synthetic fixture, and create the QA-owned tests above. Give tests descriptive scenario-matching names; assert expected IDs/values and failure atomicity independently of implementation helpers.
- [ ] Cover every requirement and review-focus condition. Prefer small deterministic domain/state tests, real Room migration/transaction tests for persistence and Compose/native flows for platform behavior. Existing test passes cannot replace missing native or manual coverage.
- [ ] If a QA defect needs production correction, retain a failing regression and capture failure before correction where feasible; fix and rerun here without rewriting expected behavior to match a defect.
- [ ] For an explicitly deferred investigation/no-change branch, do not author tests that pretend new behavior exists. Record the branch evidence, mark inapplicable feature assertions N/A with rationale, and run the applicable existing baseline gates below. This accepts assessment scope only; feature runtime QA stays deferred.

### F09-Q02 — Gradle, schemas and affected regressions

Run from repository root **only in QA**, after reading the shared command/isolation policy:

```sh
bash gradlew :app:testDebugUnitTest
bash gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

- [ ] Record actual commands, exit codes and JUnit/lint outcomes. Expected automated gate: exit 0; required tests actually executed with no failures/errors; skipped tests explained; no lint errors/fatals and new warnings classified.
- [ ] Where schema changed, generate/export current Room schema through QA build/KSP, inspect against hand-written migration, preserve old JSON and execute populated-upgrade plus archive compatibility cases. Commit schema evidence with the validated task, not as an unverified phase-1 claim.
- [ ] Run affected instrumented suites only on a confirmed isolated package/AVD using the shared QA catalogue; do not assume current debug package is isolated. Test-class filters and variant commands must match authored test packages and target identity.

### F09-Q03 — Scenarios, native behavior and acceptance

- [ ] Execute all F09-QA01–QA08 cases and every relevant shared matrix cell; Actual export picker/close behavior, device-local reset, RTL/large-text nudge.
- [ ] Record actual result, requirement mapping and artifact links in the scenario ledger. Source/compile/host passes cannot certify native/manual cases.
- [ ] Resolve defects, rerun owning and affected regression cases, and identify invalidated evidence. Leave blocked/unrun cases visible with precise prerequisites.
- [ ] Mark complete only for the exact validated scope when required cases pass. Assessment/deferred branch closure must explicitly state that feature runtime acceptance has not occurred. Record release blockers/authorized exceptions without silently self-waiving.

Suggested future commit grouping: one source handoff followed by QA tests, generated schemas and evidence; do not commit unrelated user changes. Creating these documents does not authorize implementation, release or commit actions now.
