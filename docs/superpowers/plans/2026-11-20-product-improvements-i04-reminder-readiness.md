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

- [ ] **1.** Map the existing shared reminder channel, permissions and planner inputs. The task editor currently derives automatic reminders from due date/time; add status beside these fields and preserve that automatic behavior, with no new task reminder toggle. Extend the current access model with a specific reason while keeping existing request callbacks.
- [ ] **2.** Adapt existing planner inputs for display without writing alarms or reminder preferences. Show device or habit tracking zone as applicable and derive quiet-hour results from the same logic as delivery.
- [ ] **3.** Wire the shared readiness block, settings return refresh and explicit generic test action. Keep schedule saving independent of access. Use local resources and accessible status feedback.
- [ ] **4.** Review source and localization against IP-I04-R1, IP-I04-R2, IP-I04-R3. Record the changed files, interface decisions, risks and source evidence for IP-Q01.

## QA handoff

- [ ] Map `blockedReasonRefreshesFromSystemSettings`, `quietHoursPreviewAndSchedulerAgree`, `testNotificationLeavesScheduleUntouched` to [QA-04](./2026-11-20-product-improvements-q01-emulator-qa.md#qa-04) for the final verification task. Extend the existing test surfaces where appropriate. ReminderNotificationAccessTest; ReminderEventIntentTest; existing reminder planner and connected suites.
- [ ] Record source status as **SOURCE COMPLETE — QA PENDING** after handoff and leave the QA result **NOT RUN** until IP-Q01 executes.

**Acceptance evidence** On API 33+ deny runtime permission, then separately block app notifications and the reminder channel. On API 28 verify the older branch. Check quiet hours across midnight, eligible time, absent due time, Focus invitation, test post/block and stale snooze/protected preview regression.
