# F04 — Optional habit and task reminders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use delegation only if separately authorized. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver private, optional approximate local reminders with safe completion and snooze actions.

**Architecture:** Implement existing habit planner/repository contracts first. Share Android scheduling/notification adapters with a separate task planner; validate revision, date, expiry and consumed action token inside each mutation transaction.

**Tech Stack:** Existing Kotlin, pure domain contracts, Room, Compose, Java 17 and native Android adapters as appropriate; candidate dependency changes must be documented before integration.

**Spec:** [Optional habit and task reminders specification](../specs/2026-10-26-roadmap-04-reminders.md)

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../specs/2026-10-26-roadmap-04-reminders.md) · [Plan](2026-10-26-roadmap-04-reminders.md) · [QA scenarios](../../feature-roadmap/04-reminders-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Global constraints

- Exactly two phases per task: implementation, then QA; do not interleave a test cycle into implementation.
- During implementation: no Gradle, sync, resolution, build, lint, schema generation, test execution, emulator or device. Test authoring belongs to QA.
- Preserve existing personal data/user edits, module boundaries, minimum API28 and local-first/offline defaults; follow the spec for any explicit compatibility exception.
- Calendar stays tasks/memories only; preserve navigation, drafts, revision checks, photo ownership and EN/FR/FA/RTL/light/dark support.
- Read [WORKFLOW.md](../../feature-roadmap/WORKFLOW.md) for exact isolation, source handoff and QA result/evidence obligations.

Dependencies: Task 02; reuse the existing T27/T28 habit reminder specification and planner contracts.

## Review focus

- F04-R01 (Delivery promise): execute F04-QA01, F04-QA06, F04-QA08; author assertion-level regressions in Q01 for the specified conditions.
- F04-R02 (Task schedule): execute F04-QA03, F04-QA06; author assertion-level regressions in Q01 for the specified conditions.
- F04-R03 (Habit eligibility): execute F04-QA02, F04-QA04, F04-QA05; author assertion-level regressions in Q01 for the specified conditions.
- F04-R04 (Quiet hours and privacy): execute F04-QA04, F04-QA07; author assertion-level regressions in Q01 for the specified conditions.
- F04-R05 (Safe actions and restore): execute F04-QA05, F04-QA06, F04-QA08; author assertion-level regressions in Q01 for the specified conditions.

## File map and interfaces

Existing paths are source anchors, not a request to edit every file regardless of need. New paths are proposed ownership boundaries; reconcile with existing equivalents before creating duplicates.

- Modify/review: [docs/superpowers/specs/2026-10-17-habits-and-navigation-design.md](../specs/2026-10-17-habits-and-navigation-design.md)
- Modify/review: [docs/superpowers/plans/2026-10-17-habit-reminders.md](2026-10-17-habit-reminders.md)
- Modify/review: [core/data/src/main/kotlin/com/luma/lifeos/data/habit/HabitEntities.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/habit/HabitEntities.kt)
- Modify/review: [core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoRepository.kt](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoRepository.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/LumaApplication.kt](../../../app/src/main/java/com/luma/lifeos/LumaApplication.kt)
- Modify/review: [app/src/main/AndroidManifest.xml](../../../app/src/main/AndroidManifest.xml)
- Create if required by selected implementation: `core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TaskReminderPlanner.kt`
- Create if required by selected implementation: `core/data/src/main/kotlin/com/luma/lifeos/data/todo/TaskReminderStore.kt`
- Create if required by selected implementation: `app/src/main/java/com/luma/lifeos/reminders/TaskReminderCoordinator.kt`
- Create if required by selected implementation: `app/src/main/java/com/luma/lifeos/ui/todo/TaskReminderEditor.kt`

Interfaces consumed/produced:

- Retain the existing proposed HabitReminderRepository/ReminderSlot contracts in the linked plan; do not fork a second incompatible habit planner.
- Proposed `TaskReminderConfig(taskId: UUID, enabled: Boolean, revision: UUID)` and `nextTaskReminder(task: TodoTask, config: TaskReminderConfig, now: Instant, zone: ZoneId): TaskReminderSlot?`.
- `TaskReminderSlot` carries task ID, source task revision, config revision, due civil date/minute, scheduled Instant, expiry and action token. TaskReminderStore consumes completion/snooze exactly once transactionally.

QA-owned tests (create only in phase 2; extend existing equivalent suites instead of duplicating them where practical):

- QA create/extend: `core/domain/src/test/kotlin/com/luma/lifeos/domain/roadmap/F04ContractTest.kt`
- QA create/extend: `core/data/src/androidTest/kotlin/com/luma/lifeos/data/roadmap/F04PersistenceTest.kt`
- QA create/extend: `app/src/test/java/com/luma/lifeos/roadmap/F04StateTest.kt`
- QA create/extend: `app/src/androidTest/java/com/luma/lifeos/roadmap/F04FlowTest.kt`

## Phase 1 — Implementation

Status: **SOURCE COMPLETE — QA PENDING**.

### F04-I01 — Implement habit planning first

- [x] Use the existing T27 domain types, persisted reminder rows and revision/consumed-token transactions. Carry source implementation steps into this phase; relocate every old test/Gradle step to phase 2.
- [x] Source-review the changed declarations/callers against F04-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F04-I02 — Add task configuration and planner

- [x] Create TaskReminderPlanner/Store with due-date/time eligibility, gap/overlap/quiet-hour policy and 10-minute snooze. Add explicit versioned persistence and archive adapters; retain existing due-time and overdue semantics.
- [x] Source-review the changed declarations/callers against F04-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F04-I03 — Connect Android adapters

- [x] Implement bounded inexact scheduling, immutable explicit PendingIntents, notification channels, receiver/worker reconciliation and atomic command handling. Reuse habit adapters rather than separate schedulers with conflicting ownership.
- [x] Source-review the changed declarations/callers against F04-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F04-I04 — Expose settings and lifecycle behavior

- [x] Add localized controls/status, context permission request, generic/private preview choice, cancellation and failure recovery. Wire reboot/time/zone/foreground/restore invalidation and document no catch-up behavior.
- [x] Source-review the changed declarations/callers against F04-R01–R05 and record paths/decisions. Do not run compilation or tests.

### F04-I05 — Source handoff

- [x] Review all changed files, call sites, permission/manifest declarations, EN/FR/FA resources and persistence/archive contracts for the actual selected scope.
- [x] Run source-only checks such as `git diff --check`, file/link checks and XML parsing where relevant. A successful static check is not a test/build pass.
- [x] Record Git/patch identity, implementation decisions, source-to-requirement mapping and known uncertainty. Generated schemas and all runtime verification remain pending QA.
- [x] Mark phase 1 **SOURCE COMPLETE — QA PENDING** only after I01–I04 and this handoff are complete. Do not mark the task complete or run a Gradle/emulator command here.

### Phase 1 source handoff — 2026-10-26

- Baseline: `5a35b507635eaf0e52d2122d5ac6fefb5e10b3d5` (`main`). Worktree creation was unavailable because `.git` is read-only in this sandbox; F04 changes were made in the existing checkout without staging or committing. The pre-existing release APK edit and calendar evidence logs were preserved.
- Source patch SHA-256: `dcf885714a6feeb2e9968f41fdd51314bbc44222b0574bb2d792a197e5d92350` (path-prefixed binary diffs for F04 source/resource/backup/roadmap/spec changes, excluding this plan file to avoid a self-referential handoff hash and excluding the pre-existing APK/calendar-log edits).
- Changed paths: habit/task reminder domain planners and contracts; Room repositories, DAOs, schema migration and archive/restore adapters; Android AlarmManager/notification/worker/receiver integration, settings and task/habit controls, lifecycle routing and EN/FR/FA resources; backup-format and roadmap/spec/plan documentation.
- F04-R01: reminders are opt-in, use inexact `setAndAllowWhileIdle`, request notification permission from the enabling control, report delivery state and keep notification previews generic unless explicitly opted into private names.
- F04-R02: task reminders require due date and time; resolve civil times by zone rules, skip quiet times and past-due enabling, and validate task/config revisions and local-day expiry transactionally.
- F04-R03: habit slots use the saved tracking zone, schedule lifecycle/weekdays, target/skip state, one-use action tokens and a ten-minute same-window snooze; logs keep the attributed habit date.
- F04-R04: quiet hours default to 22:00–08:00, are editable/disableable, and skip affected slots. Locked-route authentication is a future F06 integration; no lock gate is claimed by this handoff.
- F04-R05: task completion delegates to `TodoRepository` revision rules without creating a life event; task reminder archive schema 6 stores config/runtime, restore disables rows and rotates revisions, and Room migration 6→7 creates no enabled rows.
- Source-only verification: `git diff --check`; XML parsing of manifest and three string resources; equality of all 29 reminder resource keys across EN/FR/FA; active-manifest check for absence of exact-alarm, full-screen and foreground-service permissions; F04 spec/plan/QA/workflow file existence. All passed. No Gradle, tests, schema generation, build, lint, emulator or device checks were run.
- QA uncertainty: source inspection does not establish compilation, Room schema correctness/migration behavior, notification timing, OS permission/channel behavior, reboot recovery, or private-preview/authentication behavior. F04-QA01–QA08 and schema export remain **NOT RUN**.

### Phase 1 source follow-up — 2026-10-27

Status: **SOURCE COMPLETE — QA PENDING / NOT VALIDATED**.

- Baseline: `be7669c3e9212a2bb3939f6a8925e7cdef86e483`. Changes stay in the current checkout; the pre-existing release APK modification and calendar evidence logs are preserved. No staging, commit or release was performed.
- [x] F04-R01/I04: add current notification access and an explicit Allow notifications control to `ReminderSettingsRoute.kt`. Share the permission/rationale/settings flow in new `ReminderNotificationAccess.kt` with `ReminderPermissionSwitch.kt`; distinguish first request from prior requests using a device-local request-history flag in `ReminderPreferences.kt`, while checking current Android permission/app/channel state dynamically. Refresh displayed access on resume and reconcile eligible reminders after access changes. Global access leaves individual reminders off.
- [x] F04-R01/R05/I03: fix `ReminderEvent.toIntent` in `ReminderIntents.kt`. Inside `Intent.apply`, unqualified `action` resolved to the Intent action, so the serialized event extra held a qualified broadcast action that `reminderEventFrom` rejected. Explicitly use `this@ReminderEvent.action` for the short event action; retain qualified broadcast actions and immutable explicit PendingIntents.
- [x] Expose existing channel creation to the access flow in `ReminderPlatform.kt`; app blocking opens app notification settings and channel management opens the existing reminder channel. Retain approximate scheduling and existing permissions.
- [x] Update EN/FR/FA strings, F04-R01 specification, roadmap summary and F04-QA01/QA06 scenario coverage. Source-review permission callbacks, permanent denial, channel blocking, lifecycle refresh and broadcast decoding/callers.
- Source patch SHA-256: `8ac92d862fb05e485c2538dfbbac251ac580250c8d48dcf15f79f4f1e9baf00d`. Input: sorted paths for the four changed existing reminder Kotlin files, new `ReminderNotificationAccess.kt`, `ReminderSettingsRoute.kt`, three string resources, roadmap, F04 QA and F04 spec. Each path is followed by NUL, its `git diff --binary HEAD` (new-file content for the new helper), then NUL. This plan and unrelated edits are excluded.
- Source-only checks passed: `git diff --check`; XML parsing of manifest and EN/FR/FA strings; 34 unique, matching reminder resource keys per locale; POST_NOTIFICATIONS declaration and absence of exact-alarm/full-screen permissions. No tests were authored or executed; no Gradle, build, lint, dependency resolution, emulator or device operations were performed.
- QA remains **NOT RUN**, including compilation and actual OS prompt/denial/channel/resume behavior, alarm delivery, completion/log/snooze action delivery and all existing F04 acceptance scenarios. Source review does not establish runtime behavior.

## Phase 2 — QA

Initial status: **NOT RUN**. Entry requires I01–I05 handoff and isolated target verification. This phase owns test authoring, Gradle, schemas, emulator/device use and defect retesting.

### F04-Q01 — Fixtures and automated coverage

- [ ] Read the full [QA scenarios](../../feature-roadmap/04-reminders-qa.md), prepare its synthetic fixture, and create the QA-owned tests above. Give tests descriptive scenario-matching names; assert expected IDs/values and failure atomicity independently of implementation helpers.
- [ ] Cover every requirement and review-focus condition. Prefer small deterministic domain/state tests, real Room migration/transaction tests for persistence and Compose/native flows for platform behavior. Existing test passes cannot replace missing native or manual coverage.
- [ ] If a QA defect needs production correction, retain a failing regression and capture failure before correction where feasible; fix and rerun here without rewriting expected behavior to match a defect.
- [ ] For an explicitly deferred investigation/no-change branch, do not author tests that pretend new behavior exists. Record the branch evidence, mark inapplicable feature assertions N/A with rationale, and run the applicable existing baseline gates below. This accepts assessment scope only; feature runtime QA stays deferred.

### F04-Q02 — Gradle, schemas and affected regressions

Run from repository root **only in QA**, after reading the shared command/isolation policy:

```sh
bash gradlew -PdomainOnly=true :core:domain:test
bash gradlew :core:data:testDebugUnitTest :app:testDebugUnitTest
bash gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :core:data:assembleDebugAndroidTest :core:data:lintDebug
```

- [ ] Record actual commands, exit codes and JUnit/lint outcomes. Expected automated gate: exit 0; required tests actually executed with no failures/errors; skipped tests explained; no lint errors/fatals and new warnings classified.
- [ ] Where schema changed, generate/export current Room schema through QA build/KSP, inspect against hand-written migration, preserve old JSON and execute populated-upgrade plus archive compatibility cases. Commit schema evidence with the validated task, not as an unverified phase-1 claim.
- [ ] Run affected instrumented suites only on a confirmed isolated package/AVD using the shared QA catalogue; do not assume current debug package is isolated. Test-class filters and variant commands must match authored test packages and target identity.

### F04-Q03 — Scenarios, native behavior and acceptance

- [ ] Execute all F04-QA01–QA08 cases and every relevant shared matrix cell; Native notification permissions/channel controls, reboot, battery restrictions and physical-device delivery.
- [ ] Record actual result, requirement mapping and artifact links in the scenario ledger. Source/compile/host passes cannot certify native/manual cases.
- [ ] Resolve defects, rerun owning and affected regression cases, and identify invalidated evidence. Leave blocked/unrun cases visible with precise prerequisites.
- [ ] Mark complete only for the exact validated scope when required cases pass. Assessment/deferred branch closure must explicitly state that feature runtime acceptance has not occurred. Record release blockers/authorized exceptions without silently self-waiving.

Suggested future commit grouping: one source handoff followed by QA tests, generated schemas and evidence; do not commit unrelated user changes. Creating these documents does not authorize implementation, release or commit actions now.
