# Luma

A privacy-first, local-first native Android life record, based on the supplied
October 2026 product definition. The foundation includes an application shell,
a pure Kotlin domain, Room-backed local persistence, Today/daily Timeline browsing,
mood check-ins, offline journals with reusable tags, direct/reversible memories, people with linked event histories, and reusable manual places. Remaining capture,
integrations, and reviews follow [TASKS.md](TASKS.md).

## Build and run

Use Android Studio with support for AGP 8.7.3, JDK 17, and Android SDK Platform 35. Open this directory, let Gradle sync, then run the `app`
configuration on an Android 9/API 28 or newer device. No account or runtime
permissions are needed for browsing, mood check-ins, journals, memories, people, or manual places.

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
see [schema history](core/data/schemas/README.md). Current build and test results, including pending device checks and the app lint
analyzer compatibility failure, are recorded in `docs/VALIDATION.md`.

## Project references

- [ARCHITECTURE.md](ARCHITECTURE.md): stack, module boundaries, relational schema, privacy, and phases.
- [TASKS.md](TASKS.md): all 20 ordered tasks with acceptance criteria and status.
- Original `.docx` files: product specification and implementation task source.
- `docs/`: readable source extracts and validation evidence.

Today and the daily Timeline browse stored mood and journal records. Quick Capture
creates mood, journal and memory records offline; journals include a full detail
view. Existing moods/journals can be remembered without copying their content.
People can be linked during capture/edit; person detail shows shared history,
and deleting a person keeps the entries. Places can be managed from Settings
and attached optionally during capture/edit without location permission. Review lists the current week’s memories; full statistics and Settings remain
assigned to later tasks. No future backlog features, network
permission, telemetry, or mandatory AI service are included.
