# Habits acceptance log

Validation started: 17 October 2026. This log records execution evidence for the
approved habits plan. Device and UI checks remain assigned to later tasks.

## T22 — Pure domain rules

| Case | Result | Environment and evidence |
| --- | --- | --- |
| Counted progress, skip independence, schedule versions, effective dates, input limits and weekday masks | PASS | 13 tests in `HabitRulesTest`; JDK 17, Gradle 9.1.0, JVM-only domain module. Device/API/package/locale/width/font scale: N/A. |
| Date-only occurrence, future-date/instant rejection, DST gap and overlap offsets | PASS | 5 tests in `HabitTimeTest`; JDK 17, Gradle 9.1.0, JVM-only domain module. Device/API/package/locale/width/font scale: N/A. |
| Pending archive replacement and caller-owned weekday mutation | PASS | Regression tests prove a pending archive can be replaced for a started habit, while defensive immutable snapshots preserve schedule inputs and historical day state. Included in `HabitRulesTest`. |
| Existing domain regressions | PASS | `:core:domain:test` completed successfully with `-PdomainOnly=true`: 113 tests, 0 failures, 0 errors, 0 skipped. Device/API/package/locale/width/font scale: N/A. |

Commands used:

```text
GRADLE_USER_HOME=/tmp/luma-gradle-home bash ./gradlew --offline -PdomainOnly=true :core:domain:test --tests 'com.luma.lifeos.domain.habit.*'
GRADLE_USER_HOME=/tmp/luma-gradle-home bash ./gradlew --offline -PdomainOnly=true :core:domain:test
```

The repository tracks `gradlew` without its execute bit. The sandbox also blocks
Java network-interface enumeration required by Gradle startup, so these commands
ran with the Gradle user home in `/tmp` and the build runner outside the sandbox.

## T23 — Room schema 3, repository and backup

| Case | Result | Environment and evidence |
| --- | --- | --- |
| Schema-3 inventory, backup validation and data JVM regressions | PASS | `:core:data:testDebugUnitTest`: 24 tests, 0 failures/errors/skips. Includes explicit schema 1/2/3 archive inventories and current-schema SQL fixture. JDK 17, Gradle 9.1.0; device/API/package/locale/width/font scale: N/A. |
| Room persistence, 1→3/2→3 migration, and backup replacement/rollback | PASS | 13 instrumentation tests: `HabitPersistenceTest` (7), `HabitMigrationTest` (1 covering both source versions), and `HabitBackupTest` (5). Includes file-backed reopen/delete, legacy journal/mood/person/place/tag/photo links and usage preservation, schema-1/2 replacement, schema-3 date-only restore with reminders disabled/freshened, invalid-zone/runtime-graph rejection, object-only JSON validation, and rollback of logs/choice/runtime/legacy data. `OK (13 tests)`. |
| Data instrumentation APK, app debug build and JVM suite | PASS | `:core:data:testDebugUnitTest :core:data:assembleDebugAndroidTest :app:assembleDebug` completed successfully. |

Device evidence: `sdk_gphone64_x86_64`, API 36, isolated test package
`com.luma.lifeos.data.test`, locale en-US, 1080×2400, font scale 1.0. The
instrumentation APK was installed only into the attached emulator; no normal app
package was used. The direct AndroidJUnitRunner command was:

```text
adb -s emulator-5554 shell am instrument -w -r -e timeout_msec 90000 \
  -e class com.luma.lifeos.data.HabitPersistenceTest,com.luma.lifeos.data.HabitMigrationTest,com.luma.lifeos.data.HabitBackupTest \
  com.luma.lifeos.data.test/androidx.test.runner.AndroidJUnitRunner
```

Gradle's `:core:data:connectedDebugAndroidTest` cannot resolve the AGP 8.7.3
host-output artifact `com.android.tools.utp:android-test-plugin-host-additional-test-output:31.7.3`
from the configured repositories. The APK compiled successfully and the same
AndroidJUnitRunner classes were executed directly through ADB. That first device
run exposed array-shaped `times_json` being parsed as an object. Validation now
accepts arrays only for `habit_reminder.times_json`; metadata and summary JSON
columns remain object-only.

T23 acceptance is complete. The schema-3 selector, generated schema exports,
backup format notes and migration/persistence/rollback coverage are retained.

## T24 — Functional capture, history and Today entry

| Case | Result | Environment and evidence |
| --- | --- | --- |
| App JVM regressions, view-model rules and translation catalog | PASS | `:app:testDebugUnitTest`: 200 tests, 0 failures/errors/skips across 28 suites. Includes correction date moves and failed-draft recreation, future-start edit/archive semantics, and supported-language resource/plural checks. JDK 17, Gradle 9.1.0. |
| Habit capture, daily progress, individual correction/Undo, Today preview and lifecycle controls | PASS | `HabitCaptureFlowTest` (12 tests): repeated 9/8 logging, undo/correct an individual history item, date correction preserving identity/recordedAt, no-habit vs none-scheduled, future guard, scheduled-row preview/cap, Room submillisecond retry reconciliation, detail retry/undo, historical-date lifecycle action, future-start edit/archive, readable archived history, default-unit plural and accessibility, editor semantics, and 320 dp / 2.0 font scale separate 48 dp actions. |
| Navigation and language switching | PASS | `AppNavigationTest` (5) and `LanguageFlowTest` (4), including Persian/French/English resource refresh with blank stored unit displayed through localized quantity resources. Combined direct runner result: `OK (21 tests)`. |
| Standard app and test APK builds | PASS | `:app:assembleDebug :app:assembleDebugAndroidTest`, with production application ID `com.luma.lifeos`. |

Device evidence: disposable `sdk_gphone64_x86_64`, API 36, 1080×2400. Tests
used only the isolated `com.luma.lifeos.t24sandbox` / `.test` packages on
`emulator-5556`; no production app installation was replaced. The flows switch
en/fa/fr. The layout test constrains the composition to 320 dp and font scale
2.0 while verifying distinct name/open and +Log actions at least 48 dp high.
Compose accessibility semantics were checked; native TalkBack speech was NOT RUN.

Direct device command:

```text
adb -s emulator-5556 shell am instrument -w -r -e timeout_msec 90000 \
  -e class com.luma.lifeos.habits.HabitCaptureFlowTest,com.luma.lifeos.AppNavigationTest,com.luma.lifeos.language.LanguageFlowTest \
  com.luma.lifeos.t24sandbox.test/androidx.test.runner.AndroidJUnitRunner
```

T24 acceptance is complete. Habits are reachable from Today while primary
navigation remains unchanged for T25/T26. The tracking zone stays fixed for
civil dates, individual logs retain correction and deletion paths, and custom
unit labels remain user text without machine pluralization.

## Later acceptance

T25–T29: NOT RUN. Mixed Timeline/Review, OS reminders, full integration/release,
native TalkBack speech and later device acceptance remain open.
