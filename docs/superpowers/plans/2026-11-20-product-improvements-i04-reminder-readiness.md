# IP-I04 — Reminder readiness Implementation Plan

> **For agentic workers** Use the applicable Superpowers implementation skill when this plan is authorized for execution. Steps use checkbox syntax for tracking.

**Goal** Let users diagnose and repair reminder access directly inside schedule setup.

**Architecture** Reuse one access adapter and the production planning rules for a read-only preview. A second scheduler would drift on time zones, suppressions and stale reminders. Existing settings continue to own global quiet hours.

**Tech stack** Kotlin, Jetpack Compose, Luma components and existing route/state/repository contracts.

**Spec** [IP-I04 specification](../specs/2026-11-20-product-improvements-i04-reminder-readiness-design.md) · [Task index](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md) · [IP-Q01 scenario](./2026-11-20-product-improvements-q01-emulator-qa.md#qa-04)

## Files and interfaces

Paths are from the project root. Unqualified sibling filenames share the preceding directory.

- `app/src/main/java/com/luma/lifeos/reminders/ReminderNotificationAccess.kt + ReminderPermissionSwitch.kt + ReminderPlatform.kt` — Access reason/repair and test posting on the current channel.
- `app/src/main/java/com/luma/lifeos/reminders/ReminderWork.kt + FocusInvitationWork.kt` — Share production eligibility decisions with preview if current helpers cannot be reused directly.
- `app/src/main/java/com/luma/lifeos/ui/habits/HabitDetailRoute.kt + app/src/main/java/com/luma/lifeos/ui/todo/TodoEditorScreen.kt + app/src/main/java/com/luma/lifeos/ui/focus/FocusInvitationSettings.kt` — Context-sensitive status and repair; attach through the existing task reminder editor state.
- `app/src/main/java/com/luma/lifeos/ui/settings/ReminderSettingsRoute.kt` — Consistent global quiet hours/access copy.
- Update string resources and plurals in `app/src/main/res/values/`, `values-fr/` and `values-fa/` for affected copy.

Use rememberReminderNotificationAccess and existing scheduling inputs, including nextTaskReminder and nextReminderSlot. Keep the current request() callback and have the editor consume a read-only access/eligibility result.

## Global constraints

Existing task/habit/Focus invitation planners and notification settings. Leaves IP-I08 Focus runtime behavior independently owned. Apply the [shared implementation contract](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md#shared-implementation-contract). Record source handoff before IP-Q01.

## Review focus

Android channel blocking may coexist with granted app access. Treat equivalent Android 13+ app-level blocked signals as one state and choose the repair from requestability.

## Implementation checklist

- [x] **1.** Map the existing shared reminder channel, permissions and planner inputs. The task editor currently derives automatic reminders from due date/time; add status beside these fields and preserve that automatic behavior, with no new task reminder toggle. Extend the current access model with a specific reason while keeping existing request callbacks.
- [x] **2.** Adapt existing planner inputs for display without writing alarms or reminder preferences. Show device or habit tracking zone as applicable and derive quiet-hour results from the same logic as delivery.
- [x] **3.** Wire the shared readiness block, settings return refresh and explicit generic test action. Keep schedule saving independent of access. Use local resources and accessible status feedback.
- [x] **4.** Review source and localization against IP-I04-R1, IP-I04-R2, IP-I04-R3. Record the changed files, interface decisions, risks and source evidence for IP-Q01.

## QA handoff

- [x] Map `blockedReasonRefreshesFromSystemSettings`, `quietHoursPreviewAndSchedulerAgree`, `testNotificationLeavesScheduleUntouched` to [QA-04](./2026-11-20-product-improvements-q01-emulator-qa.md#qa-04). Defer any test/fixture extensions to IP-Q01 per the shared implementation workflow; use ReminderNotificationAccessTest, ReminderEventIntentTest, existing reminder planner and connected suites there.
- [x] Record source status as **SOURCE COMPLETE — QA PENDING** after handoff and leave the QA result **NOT RUN** until IP-Q01 executes.

## Source handoff — 2026-11-21

- Source status: **SOURCE COMPLETE — QA PENDING**. QA-04 and its runtime scenarios remain **NOT RUN**.
- Base Git revision: `117f970073fbaa83ba337d6602d053ae8442b753`.
- Source/resource patch SHA-256: `e0b4fcfd9ce12a467da8ac4443e4cfca396f13c5b830f44d1320e2e9b8141a10` for tracked source/resource changes from `git diff --binary -- app/src`; new-file content SHA-256: `ReminderReadinessBlock.kt` `2fc6f2354e2caf750657219ef11f420d2755edd46ed16b3d69488595e63f7728`, `ReminderSchedulePreview.kt` `478e7a1516bf0e14235ce0b172424d5d455c0005d11a805da4d93a3bbfc07b23`.
- Changed paths: `app/src/main/java/com/luma/lifeos/reminders/ReminderNotificationAccess.kt`, `ReminderPlatform.kt`, `ReminderPreferences.kt`, new `ReminderReadinessBlock.kt`, new `ReminderSchedulePreview.kt`, `app/src/main/java/com/luma/lifeos/ui/focus/FocusInvitationSettings.kt`, `ui/habits/HabitDetailRoute.kt`, `ui/settings/ReminderSettingsRoute.kt`, `ui/todo/TodoEditorScreen.kt`, and `app/src/main/res/values/strings.xml`, `values-fr/strings.xml`, `values-fa/strings.xml`; this plan, the IP-I04 spec and task index.
- Interface decisions: the access snapshot distinguishes app-level blocking, reminder-channel blocking and available access while retaining the existing request callback; Android 13 runtime denial and app-level Settings blocking share the app-blocked state. Readiness previews call the production task, habit and Focus planners and never schedule or save. The task editor supplies an in-memory task to its pure planner because its existing screen receives draft state rather than a reminder repository. The test action posts only neutral localized text to the existing reminder channel and writes no schedule or reminder registry state.
- Source evidence: app/channel checks feed the existing reminder permission gate; task eligibility uses `nextTaskReminder`, habit eligibility uses `planningInputs` plus `nextReminderSlot`, and Focus uses `nextFocusInvitation` with the delivery quiet-hours rule. Access refresh is lifecycle-based when returning from Android settings, and the habit preview refreshes on screen resume to pick up quiet-hours changes. Test tags and schedule callbacks remain wired.
- Verification: source review found no critical issues; Studio inspections reported no source errors, and `git diff --check` passed. The requested single `./gradlew check` failed at `:core:data:compileDebugUnitTestKotlin` and `:core:data:compileReleaseUnitTestKotlin`: `FocusMetadataArchiveAdapterTest.kt` lines 120, 123 and 125 omit required `taskId` and `taskOrigin` arguments. The app production Kotlin compile tasks completed. IP-Q01’s emulator/API/locale matrix remains **NOT RUN**.
- Known risks: Android runtime permission, app-level blocking, channel blocking, API 28 behavior, inexact delivery, protected previews and snooze/revision regressions still need IP-Q01 runtime evidence. The test action reports a post attempt or blocked access and does not claim notification visibility.
- QA mapping: `blockedReasonRefreshesFromSystemSettings`, `quietHoursPreviewAndSchedulerAgree`, and `testNotificationLeavesScheduleUntouched` map to QA-04. Existing `ReminderNotificationAccessTest`, `ReminderEventIntentTest`, reminder planner and connected suites remain for IP-Q01.

**Acceptance evidence** On API 33+ deny runtime permission, then separately block app notifications and the reminder channel. On API 28 verify the older branch. Check quiet hours across midnight, eligible time, absent due time, Focus invitation, test post/block and stale snooze/protected preview regression.
