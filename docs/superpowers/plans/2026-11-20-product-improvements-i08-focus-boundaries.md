# IP-I08 — Focus boundaries, recovery and completion Implementation Plan

> **For agentic workers** Use the applicable Superpowers implementation skill when this plan is authorized for execution. Steps use checkbox syntax for tracking.

**Goal** Explain in-app Focus navigation, show time recovery clearly and offer explicit next steps after finishing.

**Architecture** Treat persisted runtime time and existing protected route callbacks as the source of truth. A second timer or task mutation path would conflict with recovery and family completion rules.

**Tech stack** Kotlin, Jetpack Compose, Luma components and existing route/state/repository contracts.

**Spec** [IP-I08 specification](../specs/2026-11-20-product-improvements-i08-focus-boundaries-design.md) · [Task index](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md) · [IP-Q01 scenario](./2026-11-20-product-improvements-q01-emulator-qa.md#qa-08)

## Files and interfaces

Paths are from the project root. Unqualified sibling filenames share the preceding directory.

- `app/src/main/java/com/luma/lifeos/ui/focus/FocusScreen.kt + FocusRoute.kt + FocusCompletionContent.kt` — Setup/recovery wording and explicit post-session actions.
- `app/src/main/java/com/luma/lifeos/focus/FocusRuntime.kt + FocusViewModel.kt + FocusNavigationGuard.kt` — Existing time/guard contracts; expose confirmed state only where required.
- `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt + app/src/main/java/com/luma/lifeos/ui/todo/TodoEditorRoute.kt` — Route authorized task actions through the existing completion-capable destination.
- Update string resources and plurals in `app/src/main/res/values/`, `values-fr/` and `values-fa/` for affected copy.

Use FocusNavigationGuard, the existing runtime recovery state, reflection and break callbacks, and the existing task destination/action flow. If added, expose an optional linked-task action only when the authorized source can still be resolved.

## Global constraints

Existing Focus guard/runtime and task-family completion flow. IP-I04 owns Focus invitation notification readiness. Apply the [shared implementation contract](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md#shared-implementation-contract). Record source handoff before IP-Q01.

## Review focus

A linked task can be deleted or lose authorization between rendering and an action. Recheck at dispatch.

## Implementation checklist

- [x] **1.** Identify start, recovery, emergency and terminal states and the correct persisted credited-time value. Preserve existing Focus state machine and timer/break affordances.
- [x] **2.** Add clear setup rationale and recovery feedback. Keep focus alternatives accessible under large text. Use established resume/end/emergency callbacks.
- [x] **3.** Keep the explicit linked-task action on the existing validated callback and family-action flow. Resolve current task status before showing completion, refresh on resume, and recheck authorization and session/link identity at activation. Keep missing/protected links guarded and reuse the existing family confirmation, retry, and Undo feedback.
- [x] **4.** Review source and localization against IP-I08-R1, IP-I08-R2, IP-I08-R3. Record the changed files, interface decisions, risks and source evidence for IP-Q01.

## QA handoff

- [x] Map `recoveryShowsPersistedCreditOnly`, `completionDoesNotMutateLinkedTask`, `breakDoesNotCreditFocus` to [QA-08](./2026-11-20-product-improvements-q01-emulator-qa.md#qa-08) for the final verification task. Use FocusRuntimeRecoveryTest, FocusLifecycleTest, FocusScreenTest, FocusReportsTest and task-family suites during IP-Q01; results remain NOT RUN.
- [x] Record source status as **SOURCE COMPLETE — QA PENDING** after handoff and leave the QA result **NOT RUN** until IP-Q01 executes.

**Acceptance evidence** Start, switch apps, pause/leave and emergency-exit. Recreate required recovery with process/reboot/clock simulation, then verify credited duration and zero break credit. Finish with an open, completed, deleted and protected linked task. Exercise explicit family completion, cancel and Undo.

## Source handoff — 2026-11-21

- Status: **SOURCE COMPLETE — QA PENDING**. QA-08 remains **NOT RUN** until IP-Q01.
- Baseline: `54ed44a8889bf81f08b4982873fe18f1f008ca5b` on `main`; workspace was clean before implementation.
- Changed source paths:
  - `app/src/main/java/com/luma/lifeos/ui/focus/FocusSetupContent.kt`
  - `app/src/main/java/com/luma/lifeos/ui/focus/FocusScreen.kt`
  - `app/src/main/java/com/luma/lifeos/ui/focus/FocusRoute.kt`
  - `app/src/main/java/com/luma/lifeos/ui/focus/FocusCompletionContent.kt`
  - `app/src/main/java/com/luma/lifeos/ui/focus/FocusSessionDetailContent.kt`
  - `app/src/main/java/com/luma/lifeos/ui/focus/FocusTaskCompletionAction.kt`
  - `app/src/main/java/com/luma/lifeos/ui/focus/FocusTaskCompletionState.kt`
  - `app/src/main/java/com/luma/lifeos/focus/FocusViewModel.kt`
  - `app/src/main/java/com/luma/lifeos/ui/todo/TodoFamilyActions.kt`
  - `core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoRepository.kt`
  - `core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomTodoRepository.kt`
  - `core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomTodoFamilyCommands.kt`
  - `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml`, `app/src/main/res/values-fa/strings.xml`
  - `docs/PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md`
  - `docs/superpowers/specs/2026-11-20-product-improvements-i08-focus-boundaries-design.md`
  - `docs/superpowers/plans/2026-11-20-product-improvements-i08-focus-boundaries.md`
- Unrelated workspace change observed after the Gradle run: `app/build.gradle.kts` changes `versionCode` 20 to 21 and `versionName` 1.6.6 to 1.6.7; it is excluded from the IP-I08 source patch.
- Decisions: keep FocusNavigationGuard and existing pause/end/emergency callbacks; display `session.creditedMillis` as confirmed recovery credit and leave recovery resolution to the existing clock-aware callbacks; explain that time while Luma is stopped counts only when clock continuity is verifiable; resolve current linked-task status before exposing completion, use read-only status retries, and refresh on resume; persist completion command source identity before dispatch, reconcile committed receipts from restored preview/apply states without replaying mutation, and fall back to an authorized status read when no receipt exists; retain eligible family completion receipts in SavedStateHandle and reuse TodoActionFeedbackCard for retryable Undo; validate the current authorized Focus session and task link inside the shared database transaction for both completion and Undo; keep break timing separate from Focus credit.
- Known risk: process recreation, reboot/clock changes, app switching, completion variants, family confirmation/Undo, protected data, locales, RTL, large text and accessibility remain runtime QA scenarios. QA-08 is intentionally not run in this implementation handoff.
- Source patch SHA-256 (app, core domain and core data production source): `a756823d8985e2197de570947e0b3d570768a8c19a8ec27814887c10c2b9e2ad`.
- Static checks: IDE analysis reported no errors in the changed `FocusViewModel.kt` and `TodoFamilyActions.kt`; `git diff --check` passed. Final `./gradlew check` exited 1 after app production Debug and Release Kotlin compilation succeeded; `:core:data:compileDebugUnitTestKotlin` and `:core:data:compileReleaseUnitTestKotlin` failed because unchanged `core/data/src/test/kotlin/com/luma/lifeos/data/backup/FocusMetadataArchiveAdapterTest.kt` lines 120, 123 and 125 omit required `taskId` and `taskOrigin` arguments. QA-08 emulator/device scenarios remain NOT RUN.
