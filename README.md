# Personal Life OS

A privacy-first, local-first native Android life record, based on the supplied
October 2026 product definition. The first slice establishes an application shell
and a pure Kotlin domain. Capture, persistence, integrations, and reviews follow
in the order recorded in [TASKS.md](TASKS.md).

## Build and run

Use Android Studio with support for AGP 9.0, JDK 17, Android SDK Platform 36, and
Build Tools 36.0.0. Open this directory, let Gradle sync, then run the `app`
configuration on an Android 8.0/API 26 or newer device. No account or permissions
are needed for the foundation shell.

From a shell with `JAVA_HOME` and `ANDROID_HOME` configured (or `sdk.dir` in an
untracked `local.properties`):

```sh
./gradlew :app:assembleDebug
./gradlew :core:domain:test :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest # running emulator or attached device
```

On Windows use `gradlew.bat`. APK output:
`app/build/outputs/apk/debug/app-debug.apk`.

Pure domain tests also run without installing the Android SDK:

```sh
./gradlew -PdomainOnly=true :core:domain:test
```

CI checks domain tests, Android compilation/lint, and navigation instrumentation
on an API 36 emulator. Workflow creation does not imply a successful hosted run.
See [docs/VALIDATION.md](docs/VALIDATION.md) for checks actually run here.

## Project references

- [ARCHITECTURE.md](ARCHITECTURE.md): stack, module boundaries, planned relational schema, privacy, and phases.
- [TASKS.md](TASKS.md): all 20 ordered tasks with acceptance criteria and status.
- Original `.docx` files: product specification and implementation task source.
- `docs/`: readable source extracts and validation evidence.

The navigation destinations currently show introductory placeholders. They do
not store records or simulate statistics. No future backlog features, network
permission, telemetry, or mandatory AI service are included.
