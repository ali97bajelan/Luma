# IP-I04 — Reminder readiness specification

**Priority** P1 · **Horizon** Next · **Status** PENDING

[Task index](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md) · [Proposal](../../20261118-Proposal.md) · [Implementation plan](../plans/2026-11-20-product-improvements-i04-reminder-readiness.md) · [Final emulator QA](../plans/2026-11-20-product-improvements-q01-emulator-qa.md#qa-04)

## Intent and current behavior

Let users diagnose and repair reminder access directly inside schedule setup.

ReminderNotificationAccess exposes a combined granted flag. Existing task/habit planners and the Focus invitation worker already apply quiet hours and schedule eligibility, and Focus settings already separate schedule and notification summaries. Keep the existing WorkManager/inexact-alarm scheduling decisions authoritative.

## Scope

Use a shared Luma readiness component in the existing task/habit/Focus invitation surfaces, with retriable access actions. Show saved setting separately from transport availability and projected eligibility. Add a direct, user-triggered test notification using the same reminder channel so its outcome exercises real access.

Out of scope. This task excludes exact-alarm permission, new channels or claims of delivery at a guaranteed time.

## Requirements

- **IP-I04-R1** Distinguish app-level blocking, reminder-channel blocking with app access granted, and access available. On Android 13+ a runtime denial and an app-wide Settings block can present the same app-level state. Show the permission request when available and the corresponding Android settings action otherwise, then refresh on return. Save/cancel selects schedule state independently from access prompts.
- **IP-I04-R2** Before saving a draft schedule, explain quiet-hours effects and show the next eligible local time if the existing planner can produce one. For a valid configuration with no slot, give the relevant reason, such as missing due time or quiet-hours suppression. Label the time as eligible/inexact. Keep planning preview read-only.
- **IP-I04-R3** Expose a localized, neutral test notification on demand only. It shares the applicable channel and includes no record details, lock-sensitive actions or scheduling side effects. Report request/post attempt or blocked state, never visibility. Keep quiet hours, protected previews, snooze and revision/identity checks for real reminders.

[Android notification permission model](https://source.android.com/docs/core/display/notification-perm)

## Implementation approach

Reuse one access adapter and the production planning rules for a read-only preview. A second scheduler would drift on time zones, suppressions and stale reminders. Existing settings continue to own global quiet hours.

## Acceptance

**QA-04** On API 33+ deny runtime permission, also disable app access via Settings, and test channel blocking with app access granted. On API 28 independently exercise app-wide blocking. Check quiet hours across midnight, eligible time, absent due time, Focus invitation, test post/block and stale snooze/protected preview regression.

**Product validation** Observe whether users can diagnose and repair a deliberately blocked reminder without leaving the setup flow other than opening Android settings.

Apply the [shared implementation contract](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md#shared-implementation-contract). Runtime acceptance is owned by IP-Q01 after all handoffs.
