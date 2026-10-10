# Luma

A privacy-first, local-first native Android life record, based on the supplied
October 2026 product definition. The foundation includes an application shell,
a pure Kotlin domain, and Room-backed local persistence. Capture, integrations, and reviews follow
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
./gradlew :core:data:lintDebug :app:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest # running emulator or attached device
./gradlew :core:data:connectedDebugAndroidTest # persistence and schema baseline checks
```

On Windows use `gradlew.bat`. APK output:
`app/build/outputs/apk/debug/app-debug.apk`.

Pure domain tests also run without installing the Android SDK:

```sh
./gradlew -PdomainOnly=true :core:domain:test
```

CI is configured for domain tests, Android compilation/lint, navigation, and persistence instrumentation
on an API 36 emulator. Workflow creation does not imply a successful hosted run.
See [docs/VALIDATION.md](docs/VALIDATION.md) for checks actually run here.

T03 was implemented without running code, Gradle, or an emulator, at the user's
request. The Room Gradle plugin exports schema JSON under `core/data/schemas` during
your first build. Commit those generated files before changing the database schema;
see [schema history](core/data/schemas/README.md). The commands above are for later
user validation and were not executed during this task.

## Project references

- [ARCHITECTURE.md](ARCHITECTURE.md): stack, module boundaries, relational schema, privacy, and phases.
- [TASKS.md](TASKS.md): all 20 ordered tasks with acceptance criteria and status.
- Original `.docx` files: product specification and implementation task source.
- `docs/`: readable source extracts and validation evidence.

The navigation destinations currently show introductory placeholders. They do
not store records or simulate statistics. No future backlog features, network
permission, telemetry, or mandatory AI service are included.
