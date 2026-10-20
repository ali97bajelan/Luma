# Luma privacy and local-data controls

Settings → Privacy and local data explains Luma's storage, optional permissions,
backup copies and full reset. The same read-only policy appears in the native
Health Connect rationale activity.

Life records, task titles and notes, names, imported observations and private photo
copies are stored on this device. The app has no account, advertising or telemetry
SDK and makes no network requests. The built debug manifest does not request Internet access.
Automatic cloud backup and device-transfer extraction are disabled; manual backup
is explicit and unencrypted. A chosen document provider controls its destination.
This describes Luma's own behavior, not other apps or device services.

## Home-screen widgets

Habits and Tasks widgets show a date and summary counts by default. Each widget
instance can separately opt in to names, progress and row actions. Luma explains
that those details appear wherever the launcher displays that instance before
the setting can be saved. Quick capture opens the existing Mood, Memory or
Journal editor and does not save an entry until the user saves in that editor.
Widget previews use fictional content.

The launcher controls widget placement and may retain a cached view. Turning
details off revokes the widget's existing row and Undo actions and updates its
current view, but Luma cannot control copies retained by the launcher. Removing
a widget or resetting Luma also cannot guarantee that the launcher has cleared
its cached view. Widget action intents contain only the widget ID and an opaque
command token; entry names and notes are not sent to the launcher in those
intents.

## Optional permissions and retained history

Core browsing/capture does not require usage, health, location, camera or broad
media permission. Journals/memories select photos through the system picker and
copy only selected images into private storage.

Usage Access reads saved foreground app usage and app labels. Enabled usage imports
run automatically when Luma opens or returns to active use and can also refresh
in the background. Enabled Health Connect step imports also run on open/return
when read access is granted. Manual import remains available in Settings.
Settings exposes Android Usage Access management
while imports are off as well as on, with an explanation before launching native
controls. A status refresh is also available. Health Connect reads daily steps
only while Luma is open; Luma does not write health records. Granting step access
requires an explanatory Continue before the native permission request. Manage
Health Connect access remains available while imports are off.

Cancelling an explanation does not open permission UI. Continue is a launch choice,
not an Android permission grant; Android/provider controls remain authoritative.
Foreground return refreshes enabled integrations; access is checked before and
after imports. Revocation retains saved observations and does not manufacture zero
measurements. Turning imports off prevents new import requests and cancels scheduled
usage refreshes; it does not itself revoke provider permission or remove saved
history. A native usage query already in progress can finish, but its result is
discarded while imports remain off. Opt-out during a pending access check now
returns before starting the usage query.

Use the relevant Android/provider Settings control to revoke access. Removing local
copies is a separate action. Original Health Connect/Android usage records and
original picked photos belong to their providers.

## Delete all local data

Delete and reset Luma requires a confirmation that names the data affected and
explains irreversibility, app closure, resetting preferences and exporting first
if the user wants to retain content. Nothing is erased when opening the route,
dismissing the confirmation or recreating an activity. Reset confirmation is not
saved for automatic replay after process death.

Luma uses Android's
[clearApplicationUserData API](https://developer.android.com/reference/android/app/ActivityManager#clearApplicationUserData()).
Android resets the calling app's private storage and app-owned external storage,
without uninstalling it. It also revokes runtime permissions, clears notifications
and removes URI grants. This covers the database, saved tasks and their protected drafts, imported
copies/catalogs, private photos, caches, backup staging, preferences and
WorkManager storage together. The system stops the app so an open store or pending
writer cannot recreate only part of its data in the same running process.

The API's true result means Android accepted the reset request, not an independently
verified completion. The UI keeps duplicate actions disabled while waiting for
Android to close Luma. False results or exceptions display generic retry guidance
and a link to Luma's Android app settings, without logging content or exceptions.
Reopen Luma after it closes; imports start with their default off state.

The existing full app-data reset also clears the task table and its private drafts;
no separate task reset is needed. Original provider records, original picked photos
and backups exported to shared or other-provider storage are outside Luma's reset.
Manage those copies separately.
The app does not promise forensic erasure or reset every provider's special-access
state; native controls remain available to review access after a reset.

## Validation status

266 JVM tests pass. Tests cover confirmation/cancel/duplicates/rejection/retry,
accepted-request wording and clearing the ViewModel during a confirmed OS request.
The reset boundary is substituted; **no real app data was reset**. Existing access,
revocation and opt-out guards continue to pass; the delayed-access opt-out regression
failed before its fix and passes afterward. Five new/extended Compose cases compile
for privacy/dialog controls and access management while imports are off.

The 19 October 2026 TD07 UI run used only a disposable API 36 emulator; it does not
verify destructive reset. Native erasure/process termination, real
permission/provider UI, exported-backup survival, settings reset, OEM behavior and
visual/accessibility acceptance remain pending. Use a disposable application ID
with seeded records, photos, drafts, preferences and external backup for destructive
reset validation, then reopen and inspect storage/import defaults. The focused TD07
emulator evidence is listed in
[`TODO_ACCEPTANCE_2026-10-19.md`](TODO_ACCEPTANCE_2026-10-19.md).
See [VALIDATION.md](VALIDATION.md) for commands and the privacy audit evidence.
