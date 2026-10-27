# Manual emulator checks

Device: `Medium_Phone_API_36.1`, Android API 36 (`emulator-5554`). App under test:
isolated debug package `com.luma.lifeos.qa`. Production package `com.luma.lifeos` was left
installed and untouched.

## Notification permission and settings

- Opened Reminders settings while app notification access was blocked. The page showed the
  blocked status and **Allow notifications** control; no permission prompt appeared merely
  from opening the page.
- Tapped **Allow notifications**. Android displayed its POST_NOTIFICATIONS prompt for Luma.
- Granted access. Returning to the page changed the state to **Reminder notifications are
  allowed**.
- Tapped **Notification settings**. Android opened Luma's **Reminders** channel page with
  **Show notifications** enabled and the Default alert style selected.

## Alarm, notification and task action

- Created a QA-only task titled `QAAlarm`, due 27 October 2026 at 17:17 Asia/Tehran, enabled
  its reminder and saved it.
- The due alarm produced a Luma notification: “You have a reminder. A task is due.” The
  notification displayed **Complete** and **Snooze 10 minutes** actions.
- Tapped **Complete**. The notification cleared. After force-stopping and reopening only
  the QA app, Today showed “No open tasks due today or earlier,” confirming the task action
  persisted.
- Screenshot of the post-action task list: [task-completed-after-action.png](task-completed-after-action.png).

This was one task delivery/action check. Snooze behavior, reboot/recovery, denial and
permanent-denial paths, quiet-hour boundaries, habit delivery, restore/replay races, and
other API/locale/theme/device matrix cells were not exercised.
