# F04 focused alarm and notification QA

Date: 27 October 2026. Status: **FOCUSED QA PASS; FULL F04 ACCEPTANCE INCOMPLETE**.

## Candidate and isolation

- Candidate source commit: `2b6c850da5968adf542cebb4d8ee3cff309f4a4c`.
- QA used the debug package `com.luma.lifeos.qa`, built with the `.qa` application ID
  suffix. The existing `com.luma.lifeos` 1.2.2 package was not replaced. At the end of the
  run only the production package remained installed; the emulator was left running.
- Device: `Medium_Phone_API_36.1`, Android API 36, serial `emulator-5554`. The initial scan
  incorrectly returned an empty device list; after the user's correction the ADB recheck
  found the running device. See [device inventory](device-inventory.log).
- JDK 17.0.20.1 and Gradle 9.1.0. Android builds succeeded using the existing offline
  Gradle cache.

## Results

| Scope | Result | Evidence |
| --- | --- | --- |
| Domain host suite | **PASS** — 138 tests; zero failures, errors or skips | [final verification run](final-verification-tests.log); [domain-only output](domain-tests.log) |
| App and data host tests | **PASS** after updating the stale backup schema inventory expectation from 5 to 6 | [final verification run](final-verification-tests.log); [host-only output](android-host-tests.log); [initial stale assertion](android-host-tests-initial-failure.log) |
| App and data debug lint | **PASS**, no lint errors; app report has four warnings (new guarded POST_NOTIFICATIONS InlinedApi warning, two widget InlinedApi warnings, and targetSdk 35 OldTargetApi); core/data has no issues | [android-lint.log](android-lint.log) |
| App debug and Android test build | **PASS** as part of connected instrumentation execution | [final verification run](final-verification-tests.log) |
| Focused reminder instrumentation | **PASS** — all 3 cases on `Medium_Phone_API_36.1` | [final verification run](final-verification-tests.log); [focused-only output](reminder-connected-tests.log) |
| Manual notification access and channel handoff | **PASS** — explicit prompt, granted-state refresh, and Android Reminders channel page observed | [manual emulator checks](manual-emulator-checks.md) |
| Manual scheduled task alarm, notification and Complete action | **PASS** — notification appeared, Complete cleared it and task was no longer open after app relaunch | [manual emulator checks](manual-emulator-checks.md), [post-action screenshot](task-completed-after-action.png) |
| Full app connected suite | **INCOMPLETE** — 242 tests were selected; `AppNavigationTest.dataWidgetCaptureUsesOneCurrentTimeChooserFromEitherProvider` failed because its Activity stayed PAUSED instead of DESTROYED; the run was stopped after 17 tests | Gradle output from the connected-suite attempt; focused reminder rerun passed |

The final focused connected command ran `ReminderNotificationAccessTest.settingsShowsCurrentAccessWithoutRequestingItOnOpen`,
`ReminderEventIntentTest.eventActionsRoundTripSeparatelyFromQualifiedBroadcastActions`, and
`ReminderEventIntentTest.qualifiedIntentActionInEventExtraIsRejected`. All 3 passed on the
API 36.1 emulator. The intent tests cover task and habit action payloads; the manual task
exercise covered real AlarmManager delivery, a posted notification and the Complete
broadcast action.

The app/data unit-test attempt initially exposed a stale assertion in
`BackupCodecTest.archiveInventoriesRemainExplicitAndAdditiveAcrossSchemaVersions` (it
expected schema 5 after source moved to schema 6 and added `task_reminder`). The test now
checks the additive version 6 inventory, and both host test tasks pass.

## Remaining acceptance gaps

Only the permission grant/channel path and one task notification/action cycle were manually
exercised. Denial and permanent denial, channel/app blocking recovery, API 28–32 behavior,
quiet hours, habit scheduling/logging, snooze, replay/stale revisions, reboot/time changes,
restore, privacy/lock-screen behavior, physical-device delivery, Room migration
instrumentation and remaining F04 matrix cells are still unvalidated. Lint passed with the
warnings classified above. See the [F04 scenario ledger](../../../04-reminders-qa.md).
