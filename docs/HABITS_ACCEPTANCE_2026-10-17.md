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

## Later acceptance

T24–T29: NOT RUN. App flows, OS reminders, navigation, accessibility,
localization and later device acceptance remain open.
