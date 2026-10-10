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

## Results

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
- RUNNING: trying a temporary software emulator at minimum-supported API 26 (no hardware virtualization available).
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
