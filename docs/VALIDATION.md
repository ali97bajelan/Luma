# Validation record

Initial implementation, 2026-10-10 (Asia/Tehran).

Checks are recorded after execution. A configured CI workflow or unexecuted test
is not evidence of passing tests. Device launch/navigation requires an emulator
or a physical Android device.

## Environment

The supplied WSL workspace had no JDK, Gradle, Android SDK, or emulator. Build
verification uses temporary tooling under `/tmp/luma-tools`, keeping generated
caches and downloaded SDKs out of the source tree. Google download endpoints
returned HTTP 404; the official SDK download redirector and a temporary Google Maven mirror
allowed verification to proceed. Standard official repositories remain in the project configuration.

## T01–T02 results (before T03)

- PASS: all Android resource/manifest XML parses; version catalog parses.
- PASS: all 20 tasks and future backlog reflected in TASKS.md.
- PASS: Gradle wrapper launcher shell syntax; official distribution SHA-256 pinned.
- PASS: downloaded Gradle/JDK/SDK archives match published checksums.
- PASS: pure domain Gradle build and all 13 JUnit tests (zero failures/errors).
- PASS: Android debug APK builds (about 11 MB).
- PASS: instrumentation APK compiles; two navigation/recreation tests included.
- PASS: Android lint reports zero issues.
- PASS: Android unit task runs with NO-SOURCE; actual unit coverage is the 13 domain tests.
- PASS: final build/domain tests/lint/test APK check runs offline through the checked-in wrapper.
- PASS: merged app manifest has no Internet or sensitive runtime permission; AndroidX adds only its internal signature permission.
- PASS: ADB device query runs; initially no emulator or physical device was attached.
- PENDING: minimum-supported API 26 device verification; emulator attempts belong to the earlier foundation work. No emulator is being launched for T03.
- PENDING: device launch and both navigation instrumentation tests.

Domain test command used this environment's temporary Google Maven override:

```sh
JAVA_HOME=/tmp/luma-tools/jdk17 GRADLE_USER_HOME=/tmp/luma-gradle-home \
  /tmp/luma-tools/gradle-9.1.0/bin/gradle \
  -I /tmp/luma-repositories.init.gradle -PdomainOnly=true :core:domain:test \
  --no-daemon --console=plain
```

The temporary init script redirects only build dependency resolution to the public
Google Maven mirror. Standard repositories remain in versioned project settings.
No app data is transmitted by these build commands.

Final build command (passed after the domain API dependency adjustment):

```sh
JAVA_HOME=/tmp/luma-tools/jdk17 ANDROID_HOME=/tmp/luma-tools/android-sdk \
GRADLE_USER_HOME=/tmp/luma-gradle-home ./gradlew \
  -I /tmp/luma-repositories.init.gradle --offline \
  :core:domain:test :app:assembleDebug :app:testDebugUnitTest \
  :app:lintDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
```

Artifacts: `app/build/outputs/apk/debug/app-debug.apk` and
`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.

## T03 implementation — validation deferred

At the user's request, T03 was implemented through file edits only. No application
code, tests, Gradle commands, SDK installation, or virtualization was run. The prior
PASS results above do not validate the new persistence module or current app build.

Pending user checks:

- Gradle sync with the added Room/KSP plugins and `core:data` Android module.
- Debug build and Android lint for both the application and data library.
- Generate and commit Room's version 1 JSON schemas under `core/data/schemas`.
- Compile and run `PersistenceTest` and `SchemaMigrationTest` on a device.
- Re-run application navigation/recreation instrumentation after storage wiring.

Commands for the user to run later (not executed during T03):

```sh
./gradlew :app:assembleDebug :app:lintDebug :core:data:lintDebug
./gradlew :app:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest
./gradlew :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

The data tests exercise CRUD, complete snapshot updates, all relation types,
foreign-key cascades, source and bucket uniqueness, failed-write rollback, import
retry reconciliation, atomic checkpoint updates, cache freshness, stable ordering,
half-open time ranges, exported-schema reopening, and unsupported-version failure.
No test result is claimed until the user runs these checks.

## T04 results — 2026-10-11

The current local setup supplies JDK 17, an Android SDK, and the Gradle cache.
The user's AGP 8.7.3, Kotlin Android plugin, minimum API 28 and compile/target API
35 settings were preserved. Version 1 Room JSON was already present at task start.
The Gradle launcher is not executable in this checkout, so commands use `bash
gradlew`. Automatic approval permitted the local build to write the existing
Gradle cache outside the restricted workspace.

- PASS: 13 domain JVM tests and 6 new Today state tests, zero failures/errors.
  Coverage includes a 23-hour DST query, chronological ties and counts, committed
  updates, empty/loading/error transitions, retry, cancellation propagation, and
  UTC date-picker conversion.
- PASS: application debug APK and both application/data instrumentation APKs build.
- PASS: `:core:data:lintDebug`, no issues found.
- PASS: code review; selected-year picker range and scrollable capture-sheet fixes
  address the two important findings. `git diff --check` passes.
- BLOCKED: `:app:lintDebug` crashes internally. The bundled
  `NonNullableMutableLiveDataDetector` (`NullSafeMutableLiveData`) and
  `FrequentlyChangingValueDetector` (`FrequentlyChangingValue`) raise
  `IncompatibleClassChangeError` in the current AGP lint runtime. The crashes also
  occur while analyzing existing `LumaApplication.kt` and `AppNavigationTest.kt`.
  No lint rules were disabled and no existing build-tool versions were changed.
- PENDING: all device tests. No emulator/device was launched or instrumented.
  New sources check rendering states, retry, stored rows/times, picker dates outside
  the default year range, capture access on every tab, selected-date restoration,
  and Room-backed date selection/edit/delete updates. Foreground midnight/zone
  refresh, delayed emissions during navigation, and constrained-size/large-font
  rendering remain manual device checks.
- Existing build warnings: Kotlin plugin loaded in multiple modules and Gradle
  deprecated features. These do not prevent the tested APK/JVM build.

Final successful command:

```sh
bash gradlew --offline :app:testDebugUnitTest :core:domain:test \
  :app:assembleDebug :app:assembleDebugAndroidTest \
  :core:data:assembleDebugAndroidTest :core:data:lintDebug \
  --no-daemon --console=plain
```

The full app lint command was separately attempted twice and failed as described:

```sh
bash gradlew --offline :app:lintDebug --no-daemon --console=plain
```

Device commands for the user:

```sh
bash gradlew :app:connectedDebugAndroidTest :core:data:connectedDebugAndroidTest
```

Manual pass: fresh install without permissions, switch dates and tabs, reopen the
calendar after navigating before 1900/after 2100, rotate/recreate with a selected
date, open/close Quick Capture on every tab, test large text/landscape, and keep
Today visible across midnight or change the system timezone and resume. Capture
forms remain future tasks; T04 creates no records and shows no fabricated metrics.

## T05 results — 2026-10-11

- PASS: 33 JVM tests (16 domain and 17 application), zero failures/errors.
  New coverage checks mood/optional scale ranges, DST gaps and overlaps, one-score
  capture, multiple emotions/optional values, repeated submit prevention, write
  retry with a stable ID, restored drafts, load retry, invalid timestamps, imported
  record protection, delete completion, typed mood feed values, dirty state during
  failed loading, and recovery of a capture committed before process death.
- PASS: debug APK and application/data instrumentation APK compilation.
- PASS: data-module Android lint, no issues found; `git diff --check` is clean.
- PASS: code review followed by fixes and re-review with no important issues.
  Recovery regressions failed before their fixes and passed afterward.
- BLOCKED: full application lint still crashes in the same bundled
  `NullSafeMutableLiveData` and `FrequentlyChangingValue` detectors, with
  `IncompatibleClassChangeError`. Checks were attempted using `--continue`;
  no lint rules were disabled and the existing build-tool versions were preserved.
- PENDING: device execution of the five Room mood test cases and the application
  capture/recreation/edit/delete flow. No emulator or device was launched.
  Room sources cover scale/note/emotion storage, catalog and capture idempotency,
  renamed seed preservation, stable child/audit IDs, unrelated links/metadata,
  invalid-link rollback, day movement, imported-record write rejection and deletion.

Successful final local check:

```sh
bash gradlew --offline :core:domain:test :app:testDebugUnitTest \
  :core:data:testDebugUnitTest :app:assembleDebug \
  :app:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest \
  :core:data:lintDebug --no-daemon --console=plain
```

Full application lint was also attempted as part of this command, which fails
only in the app lint analyzer tasks:

```sh
bash gradlew --offline :core:domain:test :app:testDebugUnitTest \
  :core:data:testDebugUnitTest :app:assembleDebug \
  :app:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest \
  :core:data:lintDebug :app:lintDebug --continue --no-daemon --console=plain
```

Device check command:

```sh
bash gradlew :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

Manual acceptance: capture with one score and Save in a few seconds; select
multiple emotions; clear optional values; backdate a check-in and verify it moves
to that day on Today/Timeline; edit/delete/relaunch and check persistence; exercise
discard/delete confirmations and retained draft input after errors; test time
pickers during DST gaps/overlaps, large fonts and landscape. Mood records with
media references cannot currently be deleted through this flow: T10 owns physical
file cleanup. Journal/memory capture remains T06/T07; complete Timeline remains T11.

## T06 results — 2026-10-11

- PASS: 47 JVM tests (20 domain, 27 application), zero failures/errors.
  Journal coverage checks required body and input sizes, locale-independent tag
  normalization, body formatting, typed feed updates, duplicate submit protection,
  draft text/title/time/tag restoration, failed load/save/delete retention, missing
  and imported record protection, DST gaps, ambiguous create reconciliation, and
  restoration after a confirmed deletion committed without returning its result.
- PASS: application debug APK and application/data instrumentation APK builds.
- PASS: data-module Android lint, no issues found; `git diff --check` passes.
- PASS: independent code review followed by two recovery fixes. Oversized input
  and committed-delete restoration regression tests failed before the fixes and
  passed afterward, with the full local suite green.
- BLOCKED: full app lint again fails inside bundled analyzers with
  `IncompatibleClassChangeError`: Lifecycle `NonNullableMutableLiveDataDetector`
  and Compose `FrequentlyChangingValueDetector`/`RememberInCompositionDetector`.
  Tool versions and lint rules remain unchanged. This prevents a full app-lint
  acceptance claim; APK compilation and JVM execution succeed.
- PENDING: device execution. Four Room journal test sources compile, covering
  tag/capture retry identity, body formatting, content/link/audit preservation,
  day movement, import/media protection, catalog retention, transaction rollback
  using an aborted tag insertion, and summary freshness. Two Compose test sources
  compile for capture/recreation/detail/edit/delete and explicit discard, including
  oversized-paste rejection before recreation. No emulator/device was launched.

Final successful command:

```sh
bash gradlew --offline :core:domain:test :app:testDebugUnitTest \
  :core:data:testDebugUnitTest :app:assembleDebug \
  :app:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest \
  :core:data:lintDebug --no-daemon --console=plain
```

Full lint attempt:

```sh
bash gradlew --offline :core:data:lintDebug :app:lintDebug \
  --continue --no-daemon --console=plain
```

Device check command:

```sh
bash gradlew :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

Manual acceptance: capture a journal without permissions; reuse a tag using a
case/whitespace variant; save multiline body text; recreate during editing; cancel
and keep/discard changes; backdate and verify Today/Timeline placement; open full
body detail, edit, delete and relaunch. Verify large fonts/landscape, keyboard
scrolling, and time-picker DST handling. Drafts accept up to 50,000 body characters,
200 title characters and 50 tags of up to 50 characters each. Oversized input is
visibly rejected while retaining previous text. Existing larger stored journals
remain readable in detail but cannot be loaded into a saved-state editor. Journals
with media references require T10 cleanup before deletion. Tag filters remain T15.

## T07 results — 2026-10-11

- PASS: 63 JVM tests (22 domain and 41 application), zero failures/errors.
  New coverage checks memory importance and text bounds, promotion eligibility,
  required direct text, optional marker capture, repeated submits, restored drafts
  and dirty state during load failure, ambiguous direct/marker saves, confirmed
  unmark/direct-delete restoration, failed removal retry, protected records,
  oversized text and DST gaps. Feed tests verify memory status changes without
  duplicating original events. Review tests verify stable source-content ordering,
  loading/empty/error/retry/cancellation and a 167-hour DST calendar week.
- PASS: application debug APK and application/data instrumentation APK builds.
- PASS: data-module Android lint, no issues found; `git diff --check` passes.
- PASS: independent review found no blocking correctness or data-loss issue.
  Generated Room observations include the memory table, allowing marker-only
  changes to refresh the typed feed and memory Review.
- BLOCKED: full app lint still crashes with `IncompatibleClassChangeError` inside
  bundled Lifecycle `NonNullableMutableLiveDataDetector` and Compose
  `FrequentlyChangingValueDetector`/`RememberInCompositionDetector`. No lint
  rules or build-tool versions were changed.
- PENDING: device execution. Five Room test cases compile for direct CRUD/retry
  identity/day movement, journal/mood promotion and reversal, source edits
  preserving markers, graph/metadata/link preservation, imported/incomplete/media
  protection, transaction rollback and summary freshness. Two Compose device
  flows compile for direct capture/recreation/review/edit/delete and journal
  promotion/unmark with retained body. No emulator/device was launched.

Successful final local check (retried once after automatic permission-review
approval timed out before execution):

```sh
bash gradlew --offline :core:domain:test :app:testDebugUnitTest \
  :core:data:testDebugUnitTest :app:assembleDebug \
  :app:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest \
  :core:data:lintDebug --no-daemon --console=plain
```

Full app lint attempt:

```sh
bash gradlew --offline :app:lintDebug --continue --no-daemon --console=plain
```

Device check command:

```sh
bash gradlew :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

Manual acceptance: capture and backdate a standalone memory offline, clear/set
importance, recreate with a draft and edit/delete after relaunch. Promote a mood
and journal; verify original ID/content/time/tags remain intact, edit the original,
then unmark and verify the source stays present. Revisit current-week memories in
Review, including Sunday/Monday and timezone/DST boundaries. Exercise confirmations,
large text/landscape/keyboard layout and visible oversized-input rejection. Direct
memory title/note limits are 200/10,000 characters. Unmarking is available only for
complete manual mood/journal sources; standalone memory deletion with attached
media requires T10 cleanup. Full weekly statistics remain T16.
