# T18 — Privacy, permissions and local-data reset

Extend the existing Settings/integration flow. A Privacy and local data route
explains private device storage, no account/network/telemetry or automatic backup,
optional Usage Access and read-only Health Connect steps, photo picker/private
copies, and manual unencrypted backups. Provider access, stopping imports, deleting
local copies and deleting exported backup files have distinct meanings.

Settings retains all current integrations and adds the privacy route. Usage Access
controls remain available while imports are off. Before opening Usage Access or
requesting Health Connect step permission, show an explanation and Continue/Cancel.
Cancelling does not launch native permission UI. Existing before/after-read guards
and foreground access refresh remain authoritative; off does not claim revoked.
The exported Health rationale activity reuses the read-only local-data policy.

## Deletion behavior

Delete all local data means an explicit full reset of Luma's own app data using
ActivityManager.clearApplicationUserData, not selective SQL deletion. It clears
saved life records/catalogs/imports, private photos including drafts, caches,
backup staging and app preferences together, with Android stopping the app. This
avoids open writers or background work recreating partly deleted data. Confirm
explains irreversibility, app closure/settings reset, and exporting first to retain
content. Original provider data and shared/exported backups are outside the reset.
Android manages runtime permissions/URI grants as part of its native reset.

The retained ViewModel requires an opened confirmation before calling the reset
adapter, blocks duplicates, runs the call off-main, and handles false/exception
results with retry and a link to Luma's Android app settings. A true return means
request accepted, not independently verified deletion. No reset happens on route
entry, dismissal, activity recreation or process restoration. Tests use a fake
platform boundary; never clear the user's installed app data during this task.

## Verification

JVM tests cover confirmation/cancellation, duplicate prevention, rejection/retry,
exception handling and accepted request state. Compiled Compose tests exercise
route disclosure, confirmation cancellation, permission explanation gates and
access controls while imports are off. Existing import revocation/opt-out suites
continue to run. Audit the built merged manifest, production logging/network
code and dependency tree for sensitive telemetry/network access. Build debug/test
APKs and run all JVM/lint checks. Native reset must later be tested on a disposable
application ID with seeded records/photos/preferences and external backup; document
missing device execution instead of claiming deletion from a mocked success.
