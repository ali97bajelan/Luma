# Luma

A privacy-first, local-first native Android life record, based on the supplied
October 2026 product definition. The foundation includes an application shell,
a pure Kotlin domain, Room-backed local persistence, Today and paged daily Timeline
browsing, mood check-ins, offline journals with reusable tags, direct/reversible
memories, people with linked event histories, reusable manual places, private
photo attachments on journals/memories, offline one-time tasks with optional due
dates, optional on-device app-usage imports, optional Health Connect step imports,
saved daily summaries, simple local lookup, and deterministic weekly reviews.
Remaining reliability and release work follows [TASKS.md](TASKS.md).

Settings → App language offers English, فارسی and Français, including offline
translations and Persian right-to-left layout. Before choosing, Luma uses a
supported device language or English. The choice applies only to Luma; saved
records, user text, selected dates and capture drafts are preserved.

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

T03's initial validation was deferred at the user's request; its Room suite now
passes on API 36 as part of 107 native data tests. The Room Gradle plugin exports
schema JSON under `core/data/schemas`; commit those generated files before changing
the database schema. Current validation includes 277 passing JVM tests and a
50-case app instrumentation run with no failures (one usage-access-dependent case
was skipped). Search and Weekly Review still exceed their measured performance
targets on the emulator; native Health Connect data, system file-picker and reset
checks remain open. See `docs/VALIDATION.md` for exact results.

Settings includes manual [backup, export and restore](docs/BACKUP_FORMAT.md).
Export saves a versioned archive of records and referenced photos through the
system file picker. Restore validates the archive before confirming replacement
of current saved data. Archives are unencrypted; choose a trusted destination.
Room backup round-trip/replacement tests pass on API 36. The native Android export
and restore pickers were exercised with a generated two-event backup in the isolated
validation install, and both restored records appeared in Timeline. Process-lifecycle,
concurrent cleanup and power-loss checks remain pending.

Settings → [Privacy and local data](docs/PRIVACY.md) explains optional permissions
and retained copies, and offers a confirmed full app-data reset. Reset removes
private records/photos/drafts and app preferences, then closes Luma. Shared backup
files and original provider records must be managed separately. Native reset and
real provider-permission and app-data-reset acceptance remain pending; no real app
data was reset or optional health access granted during validation.

## Home-screen widgets

Luma provides Habits, Tasks and Quick capture widgets. Habits and Tasks start in
summary mode, which shows the date and counts without names or row actions. Open
Settings → Home-screen widgets to add a widget or configure an existing instance;
names and progress are an explicit per-instance choice. Quick capture opens the
Mood, Memory or Journal editor directly. Your launcher controls widget placement
and may retain cached views, including after a widget is removed or Luma is reset.

## Project references

- [ARCHITECTURE.md](ARCHITECTURE.md): stack, module boundaries, relational schema, privacy, and phases.
- [TASKS.md](TASKS.md): original T01–T21 and approved extension T22–T29, with acceptance criteria and status.
- [Habits handoff](docs/HABITS_TASKS.md): counted habits, per-completion Timeline records, and the five-destination navigation with a More hub are implemented; optional reminders and full release acceptance remain open.
- [Tasks handoff](docs/TODO_TASKS.md): one-time tasks with optional civil due dates, Open/Completed lists, Today and Quick Capture entry points; integrated release acceptance remains in progress.
- [Tasks acceptance evidence](docs/TODO_ACCEPTANCE_2026-10-19.md): automated and emulator results plus checks still pending.
- Original `.docx` files: product specification and implementation task source.
- `docs/`: readable source extracts and validation evidence.

Timeline’s Search action looks up titles and linked tag, person and place names,
with optional dates and saved catalog selections. Filters combine across categories;
multiple tags or people match any selected item. Results open their source entries.
Lookup uses debounced SQL queries and bounded pages, without searching journal
bodies or notes. No extra permission or integration access is needed.
On the API 36 emulator with 50,000 saved entries, title and no-match searches took
about 2.0–2.2 seconds per page; improving that large-fixture response remains open.

Timeline merges stored manual records and activity/app-usage signals in stable
day order, with calendar navigation and a bounded scrolling window. Today shows
at most the three most-used apps with more than one minute of usage. Timeline
hides app-usage rows by default; its switch reveals only rows with more than one
minute of usage, and remembers the user's choice. Complete
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
Health Connect read-steps access and foreground imports. When enabled and permitted,
both integrations import automatically each time Luma opens or returns to active
use; manual imports remain available in Settings. Step totals use Health
Connect aggregation across sources and refresh the last seven calendar dates in
a saved import timezone, including today so far. Repeated imports update the same
records and reconcile source corrections/deletions in that window. Missing data
stays unavailable; older saved totals remain snapshots. Turning imports off stops
reads and keeps saved history; native Health Connect settings manage revocation.
Sleep, workouts and background health reads are deferred.
Today shares the event previews and date controls, excluding screen-time event
rows. Event counts on Today, Timeline and saved summaries exclude screen time;
usage views and aggregate usage metrics remain available. Its saved daily summary shows
compact entry/journal/memory counts, mood/energy/stress averages,
foreground usage totals and per-app durations, and activity totals by type, unit
and source. Missing measurements display as unavailable; recorded zero stays zero.
Memory promotion keeps one underlying entry. Summaries use the selected day's
timezone. Usage shares Settings and Review's saved-session totals, clipped to the
local day, with native daily aggregates as a fallback. Summaries cache only derived
values that can be rebuilt from saved records. They describe saved observations,
not complete provider coverage. Summary errors expose retry while entries remain
available. “About this summary” reveals sample counts, full app package names,
activity details and data explanations. Usage and Health Connect settings place
controls above expandable import explanations. Quick Capture
creates mood, journal and memory records offline; journals include a full detail
view. Existing moods/journals can be remembered without copying their content.
People can be linked during capture/edit; person detail shows shared history,
and deleting a person keeps the entries. People and Places are managed from
More → People & Places; places can be attached optionally during capture/edit
without location permission.
Journal and memory forms can add up to 20 photos (25 MiB each); selected images
are copied into app-private storage, with unavailable-photo states and safe
removal. Photos remain on-device; no upload or automatic backup is enabled.
Abandoned protected drafts may retain private files until released.
Review selects an ISO Monday–Sunday week and compares its saved observations with
the preceding period. The current week uses matching weekdays and labels today
partial. It shows weighted mood/energy/stress averages, mood by date, emotion
frequencies, notable memories, linked people/places, recorded foreground app usage
and activity grouped by type/unit/source. Source actions open contributing entries,
catalog details and saved daily usage evidence. Missing measurements remain
unavailable, including empty partial session history; explicit aggregate zero is
retained. Reviews run offline with AI disabled. API 36 Room/UI checks pass, but a
50,000-row repository run measured a 364 ms p95 against the 300 ms target, so T16
remains open. Privacy Settings and confirmed local-data reset are implemented in
T18; native reset and provider-permission acceptance remain pending. No unimplemented extension or future backlog features, network
permission, telemetry, or mandatory AI service are included.
