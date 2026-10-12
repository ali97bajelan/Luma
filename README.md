# Luma

A privacy-first, local-first native Android life record, based on the supplied
October 2026 product definition. The foundation includes an application shell,
a pure Kotlin domain, Room-backed local persistence, Today and paged daily Timeline browsing,
mood check-ins, offline journals with reusable tags, direct/reversible memories, people with linked event histories, reusable manual places, private photo attachments on journals/memories, optional on-device app-usage imports, optional Health Connect step imports, saved daily summaries, and simple local lookup. Remaining capture,
integrations, and reviews follow [TASKS.md](TASKS.md).

## Build and run

Use Android Studio with support for AGP 8.7.3, JDK 17, and Android SDK Platform 35. Open this directory, let Gradle sync, then run the `app`
configuration on an Android 9/API 28 or newer device. No account or runtime
permissions are needed for browsing, mood check-ins, journals, memories, people, manual places, or photo selection through the system picker.

From a shell with `JAVA_HOME` and `ANDROID_HOME` configured (or `sdk.dir` in an
untracked `local.properties`):

```sh
./gradlew :app:assembleDebug
./gradlew :core:domain:test :app:testDebugUnitTest :core:data:testDebugUnitTest :integration:usage:testDebugUnitTest :integration:health:testDebugUnitTest
./gradlew :app:lintDebug :core:data:lintDebug :integration:usage:lintDebug :integration:health:lintDebug :app:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest # running emulator or attached device
./gradlew :core:data:connectedDebugAndroidTest # persistence and schema baseline checks
```

On Windows use `gradlew.bat`. APK output:
`app/build/outputs/apk/debug/app-debug.apk`.

The data JVM suite includes a host SQL regression using Python 3 (`python3`) and
its standard-library SQLite module. It executes generated lookup SQL against the
exported Room schema; Android instrumentation separately verifies Room mapping.

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
see [schema history](core/data/schemas/README.md). Current build and test results,
including 101 passing API 36 instrumentation tests, remaining native/performance
checks and the app/health lint analyzer compatibility failure, are recorded in
`docs/VALIDATION.md`.

## Project references

- [ARCHITECTURE.md](ARCHITECTURE.md): stack, module boundaries, relational schema, privacy, and phases.
- [TASKS.md](TASKS.md): all 20 ordered tasks with acceptance criteria and status.
- Original `.docx` files: product specification and implementation task source.
- `docs/`: readable source extracts and validation evidence.

Timeline’s Search action looks up titles and linked tag, person and place names,
with optional dates and saved catalog selections. Filters combine across categories;
multiple tags or people match any selected item. Results open their source entries.
Lookup uses debounced SQL queries and bounded pages, without searching journal
bodies or notes. No extra permission or integration access is needed.

Timeline merges stored manual records and activity/app-usage signals in stable
day order, with calendar navigation and a bounded scrolling window. App-usage
rows with zero recorded time are omitted from Timeline. Complete
manual records open their existing content routes; imported records open read-only
typed details. Settings offers optional Android Usage access, manual imports,
background refresh, access/status controls, a session Timeline and per-app Daily
totals. The two views share the selected day, exclude unused apps and clip visits
at local midnight. Android retains session events briefly; incomplete history is
marked. Older aggregate-only days keep their saved totals, dated by native interval
starts, with no invented visits. Totals measure foreground app usage rather than
device screen-on time. Device reboots and clock
corrections conservatively leave affected intervals incomplete, retaining saved
history and importing only newly started intervals. Settings also offers optional
Health Connect read-steps access and foreground imports. Step totals use Health
Connect aggregation across sources and refresh the last seven calendar dates in
a saved import timezone, including today so far. Repeated imports update the same
records and reconcile source corrections/deletions in that window. Missing data
stays unavailable; older saved totals remain snapshots. Turning imports off stops
reads and keeps saved history; native Health Connect settings manage revocation.
Sleep, workouts and background health reads are deferred.
Today shares the event previews and date controls, excluding screen-time event
rows. Event counts on Today, Timeline and saved summaries exclude screen time;
usage views and aggregate usage metrics remain available. Its saved daily summary shows
entry/journal/memory counts, mood/energy/stress averages with sample counts,
foreground usage totals and per-app durations, and activity totals by type, unit
and source. Missing measurements display as unavailable; recorded zero stays zero.
Memory promotion keeps one underlying entry. Summaries use the selected day's
timezone, with usage assigned by native interval starts, and cache only derived
values that can be rebuilt from saved records. They describe saved observations,
not complete provider coverage. Summary errors expose retry while entries remain
available. Quick Capture
creates mood, journal and memory records offline; journals include a full detail
view. Existing moods/journals can be remembered without copying their content.
People can be linked during capture/edit; person detail shows shared history,
and deleting a person keeps the entries. Places can be managed from Settings
and attached optionally during capture/edit without location permission.
Journal and memory forms can add up to 20 photos (25 MiB each); selected images
are copied into app-private storage, with unavailable-photo states and safe
removal. Photos remain on-device; no upload or automatic backup is enabled.
Abandoned protected drafts may retain private files until released.
Review lists the current week’s memories; full statistics and privacy Settings remain
assigned to later tasks. No future backlog features, network
permission, telemetry, or mandatory AI service are included.
