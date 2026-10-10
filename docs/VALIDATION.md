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
