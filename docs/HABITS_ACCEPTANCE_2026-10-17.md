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

## Later acceptance

T23–T29: NOT RUN. Room migrations/backup, app flows, OS reminders, navigation,
accessibility, localization and device acceptance are not part of T22.
