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


## T08 results — 2026-10-11

- PASS: 75 JVM tests (24 domain and 51 application), zero failures/errors/skips.
  New tests cover required/bounded person input, nullable preserve versus explicit
  clear selection semantics, person draft restoration and text limits, failed
  loading/deletion, missing-edit protection, duplicate submit prevention and
  reconciliation after committed create/delete results are lost. Existing capture
  editor tests now verify persisted selections load, restore, forward and clear,
  including people on promoted source events.
- PASS: application debug APK and application/data instrumentation APK builds.
- PASS: data-module Android lint, no issues found; `git diff --check` passes.
- PASS: independent production review found no critical or important defect.
  The reviewed fake-repository selection gap was corrected so tests reopen an
  actual populated selection, remove it, and verify explicit empty submission.
  Device flows wait for loaded catalog/history and enabled editor controls.
  Generated Room history observation includes person/junction/content tables.
- BLOCKED: full application lint was retried and still crashes with
  `IncompatibleClassChangeError` inside bundled Lifecycle/Compose analyzers.
  No rules were disabled and no build-tool versions changed.
- PENDING: device execution. Five Room cases compile for catalog identity,
  duplicate names, photo-reference preservation, all capture links, preservation/
  clearing, chronological complete source graphs, link-only deletion, foreign-key
  rollback of content/tags/links/cache, and live history invalidation after catalog,
  body, marker, link and deletion changes. Two Compose People flows compile for
  creation within journal capture with retained parent draft, multiple selections,
  recreation, linked history/removal, and deleting a linked person while keeping
  a standalone memory. Primary-navigation tests include People. No emulator or
  device was launched.

Successful final local check:

```sh
bash gradlew --offline :core:domain:test :app:testDebugUnitTest \
  :core:data:testDebugUnitTest :app:assembleDebug \
  :app:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest \
  :core:data:lintDebug --no-daemon --console=plain
```

Full application lint attempt:

```sh
bash gradlew --offline :app:lintDebug --continue --no-daemon --console=plain
```

Device check command:

```sh
bash gradlew :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

Manual acceptance: create/edit people offline, including two people with equal
names; attach several to moods, journals, direct and promoted memories. Recreate
with unsaved selections, create another person from capture, return and select
them. Move an event to another time/day and confirm chronological shared history;
edit a name and check all feeds refresh. Delete a selected person from another
screen and confirm the retained chip can be removed; a rejected missing-ID save
must retain content. Delete a person and verify entries, markers, tags and other
people survive. Check large fonts, landscape, keyboard scrolling, discard/delete
confirmations and visible oversized-input rejection. No contacts permission is
required; photo management and full people filters remain later work.


## T09 results — 2026-10-11

- PASS: 91 JVM tests (27 domain and 64 application), zero failures/errors/skips.
  New tests cover required/bounded place input, stored coordinate pairing/ranges,
  absent selection versus explicit clearing, place editor draft restoration,
  failed loading/deletion, missing-edit protection, repeated submits, ambiguous
  committed create/delete recovery and visible oversized-input rejection.
  Capture-editor tests verify selection restoration/loading/forwarding/clearing,
  including promoted sources with retained original content. Catalog/detail tests
  distinguish loading/empty/missing/data/error, retry and cancellation.
- PASS: application debug APK and application/data instrumentation APK builds.
- PASS: data-module Android lint, no issues found; `git diff --check` passes.
- PASS: independent review found no concrete correctness or data-loss defects.
  Generated Room observers include place/link tables for feed, memory and
  person-history refresh after catalog/link changes.
- BLOCKED: full application lint repeats the existing `IncompatibleClassChangeError`
  inside bundled Lifecycle/Compose analyzers. No lint rules were disabled and
  no build-tool versions were changed.
- PENDING: device execution. Five Room cases compile for catalog retry identity,
  equal names, coordinate preservation, single-place reuse across all captures,
  explicit clearing/default preservation, promotion/unmark source retention,
  place deletion without losing the complete graph, foreign-key rollback of
  content/tags/people/links/cache, and live place-detail/feed refresh on renames
  and deletion. Two Compose flows compile for creation within journal capture,
  draft/selection recreation, renaming, clearing, reuse in memory capture and
  deletion while retaining the memory. Device flows wait for loaded catalogs and
  enabled controls. No emulator/device was launched.

Successful local check:

```sh
bash gradlew --offline :core:domain:test :app:testDebugUnitTest \
  :core:data:testDebugUnitTest :app:assembleDebug \
  :app:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest \
  :core:data:lintDebug --no-daemon --console=plain
```

Full application lint attempt:

```sh
bash gradlew --offline :app:lintDebug --continue --no-daemon --console=plain
```

Device check command:

```sh
bash gradlew :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

Manual acceptance: create/edit/delete places through Settings, reuse a place in
mood, journal, direct and promoted memory forms, and save with no place selected.
Create a new place from an unsaved capture, return, explicitly select it, recreate
and save. Replace or clear an existing selection; verify people, tags, source
content and memory markers survive. Rename/delete a place and check feeds refresh.
Delete a selected place elsewhere and remove its retained unavailable chip;
rejected missing-reference writes must keep the draft. Exercise discard/delete
confirmations, large fonts/landscape/keyboard scrolling and oversized-paste
rejection. Verify core flows require no location permission. No continuous
location tracking or automatic location capture is implemented.


## T10 results — 2026-10-11

- PASS: 110 JVM tests (29 domain, 74 application, 7 data), zero failures/errors/skips.
  Domain cases cover safe relative paths, metadata, optional replacement snapshots,
  counts and duplicate identities. Pure file tests cover bounded/interrupted/invalid
  imports, stable completed-copy recovery across new engines, durable/overlapping
  leases, current-reference protection, orphan removal, unrelated-file retention
  and path/symlink containment. Editor cases cover selected-photo restoration,
  explicit clearing, failed imports, interrupted-result recovery with the same
  identity, discard without writing, and completion despite cleanup failure.
  Regression tests prove pending imports remain leased during restoration and
  newer photo drafts clear uncertain delete/unmark intents before restoration.
  Saved-state references/metadata round-trip without storing image bytes.
- PASS: application debug APK and application/data instrumentation APK builds.
- PASS: data-module Android lint, no issues found; `git diff --check` passes.
- PASS: independent code review; the reported recovery defect was reproduced by
  two failing tests, fixed, and reviewed again with no remaining critical or
  important issues. The pending-file lease regression also failed with its
  protection removed and passes with the fix restored.
- PASS: merged application manifest still excludes Internet, broad media/storage,
  camera, contacts and location permissions. Only AndroidX's internal signature
  receiver permission is present. Automatic backup remains disabled.
- BLOCKED: full application lint again crashes with `IncompatibleClassChangeError`
  in bundled Lifecycle/Compose analyzers (`NonNullableMutableLiveDataDetector`,
  `FrequentlyChangingValueDetector`/`RememberInCompositionDetector`). No rules
  were disabled and no build-tool versions changed.
- PENDING: device execution. Seven photo Room test cases compile for copied image
  metadata, reopening without source access, closing/reopening a disk database
  and file store, create retries, atomic reference/body/tag rollback with retained
  files, promotion/unmark, targeted attachment removal, unavailable/corrupt media,
  event deletion with another draft lease, identity conflicts, and preservation
  of unknown legacy metadata. Two Compose flows compile with actual Room/private
  storage for preview/save/reopen/remove while retaining body/tags and for removing
  an unavailable image. Native picker/provider behavior, full application restart
  and visual/accessibility acceptance require device execution. No emulator or
  device was launched.

Successful local check:

```sh
bash gradlew --offline :core:domain:test :app:testDebugUnitTest \
  :core:data:testDebugUnitTest :app:assembleDebug \
  :app:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest \
  :core:data:lintDebug --no-daemon --console=plain
```

After adding the disk-reopening case and fixed audit clock to the photo device
fixtures, the data instrumentation APK and data lint were checked again:

```sh
bash gradlew --offline :core:data:assembleDebugAndroidTest :core:data:lintDebug \
  --no-daemon --console=plain
```

Full application lint attempt:

```sh
bash gradlew --offline :app:lintDebug --continue --no-daemon --console=plain
```

Device check command:

```sh
bash gradlew :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

Manual acceptance: add images using the system photo picker on API 28 and a
native-picker device; cancel the picker without changing the draft. Save a journal
and direct/promoted memory, fully restart the app, revoke/remove the original
provider image and confirm the private copy is still available. Recreate during
copy/save; retry without duplicating an attachment. Reject non-images, corrupt,
oversized and excessive selections without losing text or earlier images. Remove
one image and verify body, tags, people, place, marker and other photos survive;
discard an unsaved removal and verify the stored photo remains. Delete an entry,
unmark a promoted memory, and confirm the appropriate references/content survive.
Exercise missing-image retry/removal, large fonts, landscape, keyboard scrolling
and discard/delete confirmations. Check that media is never uploaded or backed
up automatically and broad storage access is not requested.

Known storage limits: original image bytes (including embedded metadata) stay in
app-private storage. Abandoned draft leases protect potentially restorable photos
until explicit release, which can retain unused files. Managed-file cleanup only
reads/deletes canonical UUID files in its photo root; arbitrary legacy paths show
unavailable and remain outside collection. Future backup/restore or import writers
must lease managed files before committing references. Portable export and draft
storage controls remain later work.

## T11 results — 2026-10-11

- PASS: 126 JVM tests (32 domain, 87 application, 7 data), zero failures/errors/skips.
  New domain cases cover actual activity/usage metadata, page bounds and editable
  manual-record eligibility. Window cases cover ordered append/prepend with a
  300-record ceiling, short/end pages, errors/retry, subscription-before-read,
  live changes, non-cancellable late reads, skipped civil days, DST, stopped-screen
  observation and foreground refresh. Detail cases cover loading/data/deletion,
  failure/retry and cancellation. Three review regressions failed before fixes:
  lifecycle startup preceding saved-window selection, interrupted date changes,
  and deletion of every record after window eviction. All pass with the fixes.
- PASS: application debug APK and application/data instrumentation APK builds.
- PASS: data-module Android lint reports zero issues; `git diff --check` passes.
- PASS: independent read-only review found no critical or data-loss issue. The
  reported restoration and empty-window defects were reproduced and fixed;
  Today also now routes imported journals to generic read-only detail.
- PASS: merged manifest has only AndroidX's internal signature receiver permission;
  automatic backup remains disabled. No schema, build-tool or SDK version changes.
- BLOCKED: full application lint again crashes with `IncompatibleClassChangeError`
  in the bundled Lifecycle/Compose detectors. No rules were disabled.
- PENDING: device execution. Five Room cases compile for reverse-inserted 2,001
  mixed records with equal timestamps, indexed query-plan assertions and page
  boundaries; actual activity/app-usage values and journal/tag/memory/people/place
  graphs; typed-only updates; catalog-only rename invalidation; and DST half-open
  day bounds. Two Compose flows compile for mixed signal read-only detail,
  live update/deletion, manual journal routing and calendar navigation, and for
  scrolling a 500-record day through automatic page loading/window eviction and
  loading earlier records. No emulator or device was launched. Compilation does
  not establish smooth scrolling, fast multi-day navigation or visual acceptance.

Successful local check:

```sh
bash gradlew --offline :core:domain:test :app:testDebugUnitTest \
  :core:data:testDebugUnitTest :app:assembleDebug \
  :app:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest \
  :core:data:lintDebug --no-daemon --console=plain
```

Full application lint attempt:

```sh
bash gradlew --offline :app:lintDebug --continue --no-daemon --console=plain
```

Device check command:

```sh
bash gradlew :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

Manual acceptance: use Previous/Next/Today and calendar selection across empty
and populated days; confirm selected dates, local timestamps, DST and timezone
changes. Scroll mixed long days on a representative device, measure day-switch
latency/frame behavior, load earlier rows after eviction and recreate/return from
background. Edit/delete manual records and rename linked catalogs; ensure the
window refreshes without duplicates or stale dates. Open imported and incomplete
records from Timeline, Today and person history, verify real values/source and
read-only actions, and exercise missing-record/error/retry states. Check large
fonts, landscape, screen-reader labels and linked photos. T12/T13 acquisition is
not implemented, so signal fixtures live only in test databases.

Implementation uses the existing Compose/Room dependencies and a native bounded
window. Paging artifact requests returned HTTP 404 in this environment; the
temporary dependency attempt was removed from the catalog and build files.

## T12 results — 2026-10-11

- PASS: 153 JVM tests (49 domain, 92 application, 7 data, 5 usage adapter), zero
  failures/errors/skips. The 27 added cases cover native millisecond bounds/totals,
  growing retry identities, missing-day versus zero semantics, duplicate/conflicting
  observations, selected-zone aggregation, seven-day bounds/two-day overlap,
  access/revocation before and after queries, errors/cancellation, serialization,
  immediate opt-out, late access refresh, transient service failures, non-overlapping
  clock corrections, corrections during queries, reboot handling and cumulative
  drift. Settings cases cover durable state, duplicate action suppression, opt-out
  while busy, retry/cancellation and report loading/missing/error states; worker
  policy tests cover bounded error/locked retries.
- PASS: application debug APK and application/data instrumentation APK builds.
- PASS: data and usage-adapter Android lint report no issues; `git diff --check`
  passes. CI configuration now includes usage-adapter tests/lint and data JVM tests;
  this does not establish a successful hosted run.
- PASS: independent read-only review and follow-ups found no remaining Critical or
  Important defects after fixes. The initial review identified native clock identity,
  opt-out blocked by a query and transient access errors misclassified as unsupported.
  The overlap-only clock fix was insufficient; persisted wall/monotonic/boot anchors
  and a durable native-start boundary now also prevent non-overlapping translations
  from being imported as additional history. Late access rescheduling after disable
  and busy-UI opt-out suppression have regression coverage. Transient adapter errors,
  late scheduling and busy-UI opt-out failed assertions before their fixes; new API
  tests initially failed compilation before their implementations were added.
- PASS: merged manifest retains min/target API 28/35 and disabled automatic backup.
  Added permissions are special `PACKAGE_USAGE_STATS`, WorkManager `WAKE_LOCK` and
  `RECEIVE_BOOT_COMPLETED`, alongside the existing internal signature receiver
  permission. No `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE` or
  `QUERY_ALL_PACKAGES`. Room schema version 1 and SDK/build-tool versions are unchanged.
- BLOCKED: full application lint again fails inside bundled Lifecycle/Compose
  detectors with `IncompatibleClassChangeError` (`NonNullableMutableLiveDataDetector`,
  `FrequentlyChangingValueDetector`, `RememberInCompositionDetector`). No detector
  rules were disabled and this is not a successful full lint result.
- PENDING: device execution. Six Room cases compile for growing retry bounds and
  stable IDs/audit/links/metadata/labels; mid-batch rollback; disabled/revoked retention;
  native bucket-start grouping in a display zone; overlapping-shift rejection; and
  clock anchor/boundary durability across repository recreation/opt-out, followed by
  fresh-interval recovery with old history retained. Two Compose flows compile for
  optional access/import/retry/opt-out/day navigation and for empty/revoked access
  with core navigation still available. No emulator or device was launched.
- PENDING: actual native/OEM Usage access settings and service behavior, boot-count
  readability, WorkManager background scheduling under battery restrictions,
  revocation/restart acceptance, timezone/visual/accessibility checks.

Successful local suite:

```sh
bash gradlew --offline -I /tmp/luma-t12-mirror.init.gradle \
  :core:domain:test :app:testDebugUnitTest :core:data:testDebugUnitTest \
  :integration:usage:testDebugUnitTest :app:assembleDebug \
  :app:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest \
  :core:data:lintDebug :integration:usage:lintDebug --no-daemon --console=plain
```

After the last UI explanation and persistence-fixture updates, the app JVM suite,
debug and app/data instrumentation APKs, and data lint were run again successfully.
The domain and adapter production sources/tests were unchanged after the full suite.
The application lint attempt used the same temporary init script:

```sh
bash gradlew --offline -I /tmp/luma-t12-mirror.init.gradle \
  :app:lintDebug --continue --no-daemon --console=plain
```

Dependency resolution in this environment: WorkManager 2.10.0 was cached and
compatible with compile API 35. Its older AndroidX transitives are constrained to
versions compatible with the existing project/cache. The app's existing Lifecycle
platform selects 2.9.0; three newly needed artifacts were not cached and official
Google Maven requests returned HTTP 404. A temporary Gradle init script fetched
only those artifacts from the Aliyun Google mirror; subsequent checks ran offline.
Project repositories remain `google()`/`mavenCentral()` and no mirror was committed.
The temporary `/tmp/luma-t12-mirror.init.gradle` contained:

```groovy
settingsEvaluated { settings ->
    settings.dependencyResolutionManagement.repositories {
        exclusiveContent {
            forRepository { maven { url = uri('https://maven.aliyun.com/repository/google') } }
            filter {
                includeVersion('androidx.lifecycle', 'lifecycle-livedata', '2.9.0')
                includeVersion('androidx.lifecycle', 'lifecycle-service', '2.9.0')
                includeVersion('androidx.lifecycle', 'lifecycle-livedata-core-ktx', '2.9.0')
            }
        }
    }
}
```

Usage measurements preserve Android's native reporting intervals, which may
expand the query range. Displayed dates follow interval starts in the selected
zone; no duration is prorated into exact civil-day totals. App foreground time can
overlap across apps and is not device screen-on time. Empty/unavailable queries
do not create zero-valued records or advance checkpoints.

The conservative clock policy is a material limitation: after a device reboot or
cumulative wall/elapsed drift over one second, old history/checkpoints remain, but
native intervals starting at or before the persisted safety boundary stop receiving
updates. The affected interval can remain permanently incomplete; imports resume
with newly started intervals. App process restart within the same boot preserves
the clock anchor. This is explained in Settings and avoids silently counting
rewritten historical intervals twice. Unreadable boot identity produces an error
and keeps existing history. It does not guess clock continuity.

Device command, on an attached device or already running emulator:

```sh
bash gradlew :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

Manual acceptance: browse/capture without Usage access; enable imports and follow
native settings; return and import known app usage, checking actual native bounds
and totals rather than assuming equality with Digital Wellbeing's own display.
Retry growing intervals and verify stable records/labels. Revoke access while a
query runs, opt out while busy, recreate the app and confirm saved history/checkpoints
persist. Exercise denied, unsupported, locked, empty and transient-error states,
including an absent settings activity and unavailable app labels. Validate background
jobs, bounded retries and cancellation on a representative device. Restart the
device or correct wall time and confirm the incomplete-interval explanation, absence
of duplicated history and subsequent fresh-interval recovery. Check selected zones,
large fonts, landscape, screen-reader labels and existing Places/core routes.

## T13 — Health Connect steps (2026-10-12)

Implemented optional foreground, read-only Health Connect step imports in the
existing schema. Device acceptance remains pending; T13 is marked implemented
(validation pending), not complete.

- PASS: 188 JVM tests: 71 domain, 99 app, 7 data, 5 usage adapter, 6 health
  adapter; zero failures/errors/skips. Health cases cover fixed-zone identities,
  growing current-day bounds, seven-date refreshes, DST/midnight windows, null
  versus zero, incomplete/conflicting responses, unsupported/update/denied access,
  revocation during reads, errors/cancellation, immediate opt-out, queued imports,
  stale enable revisions, cancellation before remaining native aggregates and
  delayed status refresh. App cases cover durable controls, duplicate suppression,
  opt-out while busy, retry and failed observer restoration.
- PASS: debug APK and app/data instrumentation APK builds. Six new Room cases
  compile for stable IDs/audit/metadata/links, corrected counts, explicit zero,
  missing-data removal, rollback, disabled/stale revisions, saved zone restoration,
  backward checkpoint rejection and summary rebuilding. Two Compose flows compile
  for optional access/import/correction/opt-out, empty/unsupported/revoked data and
  retained Settings navigation/privacy explanation. Compilation is not device
  execution.
- PASS: core:data and integration:usage lint report no issues. `git diff --check`
  passes. CI includes health adapter tests/lint; no hosted CI result is claimed.
- PASS: merged manifest keeps API 28/35 and automatic backup disabled. READ_STEPS
  is the only new data permission, plus scoped Health Connect package visibility
  and rationale/Android 14 permission-usage routes. No INTERNET, WRITE_STEPS,
  background-health-read, health-history, sleep, workout, location or broad package
  permission is added. Database version remains 1; pre-existing release artifacts
  and generated schema were not added to version control.
- PASS: independent review identified continued reads after opt-out, absent
  retention explanation in Settings, and a stale status-refresh race. Three
  domain regressions failed before the fixes (pending-query timeout, unwanted
  post-disable query and overwritten successful status), then passed. A composed
  adapter regression now verifies exactly one native aggregate request when
  disabling during the first read, with no commit or subsequent reads. The Settings
  card displays retention details directly. No review findings remain deferred.
- BLOCKED: full application and health-adapter lint fail inside bundled Lifecycle/
  Compose detectors with `IncompatibleClassChangeError`: respectively
  NonNullableMutableLiveDataDetector, FrequentlyChangingValueDetector and
  RememberInCompositionDetector. Health's Activity dependency now also brings the
  existing incompatible Lifecycle detector into that module. No rules were disabled;
  this is not a successful lint result for those modules.
- PENDING: actual native Health Connect availability/installation, permission
  grant/deny/revocation, Android 13 APK versus Android 14+ framework behavior,
  step aggregation across phone/watch sources, deletion/correction and restart
  acceptance, native rationale routes, visual/accessibility rendering and device
  execution of Room/Compose tests. No emulator/device was launched.

Successful checks (excluding the two blocked lint tasks):

```sh
bash gradlew --offline -I /tmp/luma-t13-mirror.init.gradle \
  :core:domain:test :app:testDebugUnitTest :core:data:testDebugUnitTest \
  :integration:usage:testDebugUnitTest :integration:health:testDebugUnitTest \
  :app:assembleDebug :app:assembleDebugAndroidTest \
  :core:data:assembleDebugAndroidTest :core:data:lintDebug \
  :integration:usage:lintDebug --no-daemon --console=plain
```

The lint attempt ran the same suite with `:app:lintDebug` and
`:integration:health:lintDebug`, `--continue`; tests and APK tasks succeeded,
while those lint analyzers failed. Local command logs are under
`/tmp/luma-t13-final-check.log` and `/tmp/luma-t13-passing.log`.

Environment/dependency details: initial sandboxed Gradle failed because the
existing Gradle cache is read-only; permitted Gradle execution used that cache and
local daemon sockets. Official Google Maven returned missing Health Connect
artifacts, as prior T12 validation observed for other AndroidX artifacts. A
local-only init script resolves `androidx.health.connect` through the Aliyun Google
mirror; project repositories remain google()/mavenCentral(). The script is:

```groovy
settingsEvaluated { settings ->
    settings.dependencyResolutionManagement.repositories {
        exclusiveContent {
            forRepository { maven { url = uri('https://maven.aliyun.com/repository/google') } }
            filter { includeGroup('androidx.health.connect') }
        }
    }
}
```

Health Connect 1.1.0-beta01 is pinned to preserve the supplied API 35/AGP 8.7.3
settings. Published AAR metadata was inspected for beta01 (API 35/AGP 8.6.0),
beta02, rc02, rc03 and stable 1.1.0 (all API 36/AGP 8.9.1). Thus native behavior
and upgrading to stable are material follow-ups when the toolchain is updated.
Activity/Core/Lifecycle transitives are aligned with existing project versions.
See [AndroidX release history](https://developer.android.com/jetpack/androidx/releases/health-connect).

A pre-existing DailySummaryPersistenceTest compile failure was also fixed:
Clock.getZone referenced its inherited property recursively; it now explicitly
uses the outer test's ZoneId. All existing persistence test sources compile.
One simulated-provider test blocked because it awaited access refresh before
releasing a queued query; that test worker was stopped and the test was corrected
to validate import serialization separately. The final suite passes.

Bounded sync semantics: each successful import refreshes seven dates in the
persisted import zone. A null aggregate removes the owned daily snapshot rather
than creating zero, reflecting source deletion. Provider failures/revocation never
clear snapshots or advance the checkpoint. Older dates remain local snapshots;
disabling retains them. Import timezone is fixed across travel and re-enabling;
Timeline/Today/summary display dates follow each interval's start in the display
zone, without repartitioning counts. These limits are explained in Settings.

Manual acceptance on a supported device: navigate/capture with health disabled;
enable steps, read the explanation, deny access and verify normal app use. Install
or update Health Connect where needed; grant READ_STEPS and import a known
phone/watch fixture. Repeat imports as today's total grows or is corrected and
verify stable rows; remove source data within the refreshed window and verify
missing versus zero. Revoke during a query, disable while busy and confirm no
later reads/commits. Restart the app and change timezone, verifying the saved
import zone/history/status. Open both native privacy links and manage access after
opt-out. Test large fonts, landscape and screen readers. Device commands remain:

```sh
bash gradlew :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```
