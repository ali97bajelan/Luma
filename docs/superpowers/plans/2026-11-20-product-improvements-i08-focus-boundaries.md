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

- [ ] **1.** Identify start, recovery, emergency and terminal states and the correct persisted credited-time value. Preserve existing Focus state machine and timer/break affordances.
- [ ] **2.** Add clear setup rationale and recovery feedback. Keep focus alternatives accessible under large text. Use established resume/end/emergency callbacks.
- [ ] **3.** Add explicit completion actions using a validated linked-task callback and current reflection/break callbacks. Recheck authorization and identity on activation. Keep no-op guard for missing/ineligible links.
- [ ] **4.** Review source and localization against IP-I08-R1, IP-I08-R2, IP-I08-R3. Record the changed files, interface decisions, risks and source evidence for IP-Q01.

## QA handoff

- [ ] Map `recoveryShowsPersistedCreditOnly`, `completionDoesNotMutateLinkedTask`, `breakDoesNotCreditFocus` to [QA-08](./2026-11-20-product-improvements-q01-emulator-qa.md#qa-08) for the final verification task. Extend the existing test surfaces where appropriate. FocusRuntimeRecoveryTest; FocusLifecycleTest; FocusScreenTest; existing task family suites.
- [ ] Record source status as **SOURCE COMPLETE — QA PENDING** after handoff and leave the QA result **NOT RUN** until IP-Q01 executes.

**Acceptance evidence** Start, switch apps, pause/leave and emergency-exit. Recreate required recovery with process/reboot/clock simulation, then verify credited duration and zero break credit. Finish with an open, completed, deleted and protected linked task. Exercise explicit family completion, cancel and Undo.
