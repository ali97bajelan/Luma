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

## T14 — Daily summaries (2026-10-12)

Resumed the unfinished verification/review/documentation step of the existing
T14 plan. The calculator, Room cache and Today card were already committed in
`4f0032c`; current HEAD also includes T13. T14 is implemented (validation pending),
with device acceptance outstanding.

- PASS: all 188 JVM tests executed again: 71 domain, 99 application, 7 data,
  5 usage adapter and 6 health adapter, zero failures/errors/skips. A temporary
  init script forced Test tasks to run rather than reuse up-to-date results.
  Summary coverage includes literal mixed metrics, reversible promotions without
  duplicate entries, unavailable versus known zero, order/dedup/conflicts, exact
  decimal sums, unit/provider separation, native-start dates, DST/skipped dates,
  loading/data/error/retry, wrong-day rejection and cancellation.
- PASS: application debug APK and application/data instrumentation APK builds.
  Eight summary Room cases and three summary Compose flows compile. Room sources
  cover derived-only caching, deterministic rebuilds, fresh reuse, corrupt/version
  fallback, edit/promotion/deletion invalidation, native-start inclusion despite
  outside-day parent occurrence, failed rebuild retention, observation restart,
  zone/skipped-date behavior and concurrent capture/cache transactions.
- PASS: data and usage-adapter lint report no issues; `git diff --check` passes.
  Database schema, dependencies, toolchain and permissions are unchanged.
- PASS: one independent read-only review found missing per-app usage attribution
  on Today. The card now renders labels, package names and each app's stored
  duration, including package fallback and known zero. A new Room-backed Compose
  fixture checks two packages, fractional seconds, zero and native-date attribution
  for an outside-day parent. It was written/compiled before the rendering change;
  final compilation passes. This is not a runtime RED/GREEN claim.
- DEFERRED MINOR: summary Retry shares TodayRoute's outer composition key with
  the feed; retry can restart the feed query, briefly show Loading and reset list
  scroll position. Saved records remain available. Isolating retry collection keys
  remains a follow-up.
- BLOCKED: full app and health-adapter lint still crash inside bundled Lifecycle/
  Compose analyzers with `IncompatibleClassChangeError`, including
  `NonNullableMutableLiveDataDetector` and `FrequentlyChangingValueDetector`.
  No lint rules were disabled. The all-checks command exits unsuccessfully for
  those analyzers; tests and APK tasks succeed. Full lint acceptance is pending.
- PENDING: runtime Room/Compose tests, visual/accessibility checks, large fonts,
  landscape, actual provider-to-summary values and restart acceptance. The plan's
  no-device-execution constraint was retained; no emulator/device was launched.
  Device-only regression execution may still expose rendering or fixture failures.

Successful final check:

```sh
bash gradlew --offline -I /tmp/luma-t13-mirror.init.gradle \
  :core:domain:test :app:testDebugUnitTest :core:data:testDebugUnitTest \
  :integration:usage:testDebugUnitTest :integration:health:testDebugUnitTest \
  :app:assembleDebug :app:assembleDebugAndroidTest \
  :core:data:assembleDebugAndroidTest :core:data:lintDebug \
  :integration:usage:lintDebug --no-daemon --console=plain
```

Before that successful confirmation, the same task list ran with
`-I /tmp/luma-t14-retest.init.gradle`, `:app:lintDebug`,
`:integration:health:lintDebug` and `--continue`. All JVM Test tasks executed and
passed; the two modules' lint analyzers failed as described. The local-only retest
script contained:

```groovy
allprojects {
    tasks.withType(org.gradle.api.tasks.testing.Test).configureEach {
        outputs.upToDateWhen { false }
    }
}
```

Logs: `/tmp/luma-t14-final-check.log` and `/tmp/luma-t14-passing.log`.
The previously documented T13 mirror init script was reused offline; project
repositories remain unchanged. Initial sandbox execution could not write the
existing Gradle cache; automatically approved execution used that cache and local
daemon sockets. Existing untracked release artifacts and schema exports were not
added to version control. No commit was created.

Manual acceptance: compare Today summaries with saved moods, optional scales,
journals, direct memories and promoted entries; edit/delete/backdate them and
verify recomputation on date navigation and resume. Check missing versus recorded
zero activity/usage, per-app labels/packages/durations, selected-zone/native-start
dating, DST and decimal values. Retry a summary failure while continuing to open
source entries. Rebuild or remove a derived cache and verify identical values with
source content intact. Run the existing device suites on an attached device or
already running emulator:

```sh
bash gradlew :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

## Runtime acceptance follow-up — 2026-10-12

The user launched the app and requested completion of outstanding validation.
ADB found an already running Android 16/API 36 x86_64 emulator (`emulator-5554`).
No emulator was created or launched. The new request authorized device execution,
superseding earlier task-plan deferrals.

To protect the user's installed `com.luma.lifeos` data, a local-only Gradle init
script built the same app with application ID `com.luma.lifeos.validation`:

```groovy
gradle.beforeProject { project ->
    project.plugins.withId('com.android.application') {
        project.androidComponents.finalizeDsl { android ->
            android.defaultConfig.applicationId = 'com.luma.lifeos.validation'
        }
    }
}
```

The script is `/tmp/luma-device-validation.init.gradle`; no application ID change
was committed. The final normal build restores `com.luma.lifeos` in generated APKs.

- PASS: **72/72 Room instrumentation tests**, zero failures. CRUD, all relation
  types, live observations, rollback, deletion cascades, capture/import identities,
  cache freshness, exported v1 schema reopening and unsupported-version protection
  execute successfully. Photo tests close/reopen a disk database and private store
  and verify the committed image remains available without its original source.
- PASS: **29/29 app instrumentation tests**, zero failures. Coverage includes
  primary routes and recreation, all-tab Quick Capture, dates/pickers, Today live
  persistence, mood/journal/memory capture/edit/delete, explicit discard, People
  and Places links/history/deletion, photo rendering/removal/unavailable states,
  mixed Timeline details and 500-entry window traversal, three summary UI flows,
  and simulated usage/health access/import/retry/opt-out.
  Final app suite runtime: 118.277 seconds; data suite: 6.616 seconds.
- PASS: **189 JVM tests** (71 domain, 100 app, 7 data, 5 usage adapter, 6 health
  adapter), zero failures/errors/skips. Test tasks were forced to execute again.
  Debug and app/data instrumentation APKs build; data and usage-adapter lint pass.
  `git diff --check` passes.
- PASS: native Usage access route/consent on API 36, real UsageStats acquisition
  and an initial scheduled WorkManager import. SQLite snapshots contain **502
  native rows**, 11 with positive durations and none with negative durations,
  including foreground use of the validation app. Manual retry preserves all
  child/event/package/native-start identities and the 502-row count; 25 intervals
  update totals or end bounds. After access revocation, process restart and opt-out,
  all rows remain equal to the post-retry snapshot and the successful sync timestamp
  is retained. Settings reflects required access and then imports-off states.
- PASS: timed minimal mood capture with optional access denied: Quick Capture ->
  Mood -> score 4 -> Save takes **4.97 seconds**, including scripted settling and
  UI confirmation overhead. SQLite contains the score with optional values absent;
  Today shows the matching 4/5 average and sample count. This is one measured run,
  not a device-performance benchmark.
- PASS: fresh read-only review of validation fixes confirmed they retain behavior
  assertions and identified the Journal no-op update/error issue. Its JVM regression
  failed with expected TEXT / actual null before the production fix, then passed.
  The previously failing oversized-input/discard device flow and full suites pass.

Failures discovered and corrected during actual execution:

- People history observation inferred a non-void JUnit return type, causing class
  initialization failure. Explicit `Unit` allows all five People cases to run.
- The selected-day text is inside merged button semantics; the test now inspects
  that label in the unmerged tree while still asserting its exact restored date.
- Three older capture flows waited for Today's summary on the dedicated Timeline.
  They now wait for its actual loading state, retaining exact source-row/edit/delete
  assertions. The Places selected chip assertion now allows its appended Remove label.
- Journal rejected oversized text correctly but cleared the visible rejection on
  a redundant callback containing the retained value. No-op draft updates now keep
  the error until a real draft change. A new regression verifies both preservation
  and clearing after corrected input. No detector rules, dependencies or SDKs changed.

Completed acceptance in TASKS.md: **T01, T03–T10, T12 and T14**. T02 was already
complete. Remaining implemented-task acceptance:

- **T11:** smooth scrolling. A three-year, 20,000-entry fixture measured four day
  changes at 122, 115, 117 and 133 ms. A separate plain ActivityScenario probe used
  the real app clock and paced native touch events for 30 swipes, collecting 1,112
  frame reports: p95 21.96 ms and 52.43% missed platform deadlines, with no dropped
  reports. Thus day navigation is fast in this run, but smooth scrolling acceptance
  is not established on this debug emulator. Profile/optimize and verify on
  representative hardware before closing the task. The controlled Compose-clock
  gesture measurements were discarded; they were unsuitable for live frame timing.
- **T13:** obtain a useful actual Health Connect step import through native consent.
  The emulator has the native controller installed, but simulated-provider tests
  do not establish acquisition of real steps.

Native photo-picker interaction, physical/OEM battery behavior, six-hour scheduling,
reboot/clock correction, minimum-API coverage and broad accessibility/visual checks
remain platform follow-ups. Full app/health lint still has the previously documented
bundled-detector compatibility failure; this runtime pass does not claim full lint
acceptance. T15–T20 remain unimplemented and were not marked complete.

Device launcher detail: offline `connectedDebugAndroidTest` could not resolve
`com.android.tools.utp:android-test-plugin-host-additional-test-output:31.7.3`.
The APKs compiled, then ADB ran the same AndroidJUnitRunner directly. ADB's process
exit code alone is insufficient; the recorded instrumentation output explicitly
reports `OK (72 tests)` and `OK (29 tests)`:

```sh
adb -s emulator-5554 shell am instrument -w -r -e timeout_msec 90000 \
  com.luma.lifeos.data.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -r -e timeout_msec 90000 \
  com.luma.lifeos.validation.test/androidx.test.runner.AndroidJUnitRunner
```

Final successful normal build used the T13 mirror and T14 retest init scripts with
the task list from the T14 successful check above; no validation-ID override.
Logs: `/tmp/luma-data-device-tests-final.log`,
`/tmp/luma-app-device-tests-complete.log`, `/tmp/luma-validation-final-build.log`.
Earlier failing runs and the Journal RED/GREEN logs remain under `/tmp/luma-*`.
Native snapshot comparisons and the timed capture record also remain under `/tmp`.
The user's installed app was neither cleared nor replaced. Existing untracked
release artifacts and generated schema remain outside the edited files.

Performance measurement details: the temporary plain-Activity probe is under
`/tmp/luma-live-performance-src`, enabled only through a local init script and
guarded to the validation application ID. It is excluded from the final regular
test APK. Measurements use positive `TOTAL_DURATION`/`DEADLINE` values and exclude
first-layout frames; see [Android FrameMetrics](https://developer.android.com/reference/android/view/FrameMetrics).
The native probe passed its fixture/frame-collection assertions in 34.236 seconds;
that is not a smooth-scrolling pass. Logs: `/tmp/luma-timeline-native-frame-device.log`
and `/tmp/luma-timeline-live-frame-metrics.log`. The reusable day-navigation fixture
remains in TimelineFlowTest. Physical/release profiling remains open.

Session checkpoint — stopped at the user's request.

Final regular suites: 72 Room + 29 app tests pass (101 Android tests); all 189 JVM
tests pass. Native Health Connect read access was granted only to the disposable
validation app. The production adapter/native provider returned GRANTED / NO_DATA
and stored zero activity rows in an in-memory database, without inventing zero
step values. No health-provider data was written. T13 still needs a known native
step-data fixture and consent-flow acceptance. Logs: /tmp/luma-native-health-read.log
and /tmp/luma-native-validation-metrics.log.

Completed: T01, T03–T10, T12, T14; T02 was already complete. T11 remains open for
scrolling performance (native p95 21.96 ms, 52.43% missed deadlines). T13 remains
open for useful native steps. T15–T20 remain pending. App/health lint retain the
existing bundled-detector crashes.

Uncommitted changes include the Journal no-op/error fix and regression, corrected
device test assertions, a reusable 20,000-entry Timeline day-navigation fixture,
and updated task/validation documentation. No commit was created. Suggested message:
`fix: preserve journal validation errors and verify implemented tasks`.

Temporary validation app/test packages remain installed on emulator-5554 at this
checkpoint; they are separate from com.luma.lifeos and can be removed later. The
latest temporary health-probe build used the validation-ID/test-source init scripts.
Before using generated APKs, run a normal build without those temporary scripts
to restore standard build configuration. User app data was not cleared or replaced.
Temporary probes and logs remain under /tmp. No further validation is running or
requested after this checkpoint.

## T12 session views update — 2026-10-12

Implemented the reopened requirements: unused apps are excluded; Settings has a
saved Timeline/Daily totals selector. Native foreground events supply visits,
opening/end/observation times and durations; totals use the same sessions clipped
to the selected civil day. Continued and ongoing visits are labeled. Native
history remains explicitly partial, and orphan events never invent openings.
Positive older aggregate-only totals remain available when retained events are
absent. Today summaries exclude zero-use app rows while retaining a known zero
aggregate. Summary algorithm version 2 invalidates previous derived caches.

Room v2 adds indexed session and query-window tables. The additive 1→2 migration
retains the original graph and marks summary caches stale. Original daily event
identities, links and metadata remain intact. Import transaction includes daily
buckets, session updates, query metadata and checkpoint under the existing
permission, opt-out and clock-safety guards. Include compiler-generated v1 and v2
schema JSON snapshots in the next commit. Schema-copy task ordering now includes
an export generated by the same build, fixing the first-build migration asset gap.

Evidence:

- PASS: all **205 JVM tests** (79 domain, 7 data, 13 usage, 6 health, 100 app).
- PASS: all **76 Room instrumentation tests** on the API 36.1 emulator, including
  v1→v2 migration, journal/session disk reopening, original usage identity/links,
  rollback/disabled writes, session retry identity, history retention, legacy fallback
  and complete/partial/empty windows.
- PASS: **8 focused app checks**: real Android event import/retry, four Usage Settings
  flows, and three summary flows. They verify native positive visits, matching totals,
  labels, optional access/revocation, both views, repeated visits, midnight overlap,
  empty/legacy history, saved selector restoration and zero-use row exclusion.
- PASS: regular debug and Android test APK builds; data and usage lint; diff whitespace.
- Broad app suite: **31/32 passed**, with a 5-second timeout in
  `PlaceCaptureTest.existingPlaceIsReusableAndDeletingItKeepsLinkedMemory` while
  awaiting a place in the picker. The unchanged test passed an isolated retry
  (`OK (1 test)`). No unrelated Places implementation/test changes were made and
  this does not claim a clean full app-suite run. The first broader run also exposed
  an obsolete summary expectation of two apps including a zero-use app; it now
  asserts one used app and the absence of zero-use rows, and all summary flows pass.
- Review: two Important findings reproduced RED, then passed after fixes: a same-class
  pause/resume at equal timestamps lost the later interval; an empty partial event
  window hid retained positive legacy totals. Follow-up review found no remaining
  Important/Critical issue.

Commands used existing offline Gradle dependencies with the Health Connect mirror
init script at `/tmp/luma-t13-mirror.init.gradle`; JVM retests used
`/tmp/luma-t14-retest.init.gradle`. Device builds used the disposable application ID
`com.luma.lifeos.validation` through `/tmp/luma-device-validation.init.gradle`.
The user's installed app/data was never replaced or cleared. Usage access was
briefly granted only to that disposable app for native read validation, then
restored to its prior default. Final normal APK build omits the validation-ID script.

Logs: `/tmp/luma-t12-final-build.log`, `/tmp/luma-t12-data-complete-final.log`,
`/tmp/luma-t12-native-ui-final.log`, `/tmp/luma-t12-app-final.log`,
`/tmp/luma-t12-place-retry.log`; RED logs are also retained as `/tmp/luma-t12-*-red*`.
Native reference: [UsageEvents](https://developer.android.com/reference/android/app/usage/UsageEvents.Event)
and [queryEvents retention](https://developer.android.com/reference/android/app/usage/UsageStatsManager#queryEvents(long,%20long)).
The public API does not expose activity instance IDs or a reliable event-retention
boundary; class-count reconstruction is conservative and native history is marked
partial. Minimum API/OEM behavior, six-hour battery scheduling and reboot/clock
correction remain device follow-ups. Existing full app/health lint analyzer failures
remain documented; this update claims only data/usage lint. No commit was created.

## Today screen-time filtering and event counts — 2026-10-12

Today filters APP_USAGE envelopes before rows, detail maps, counts and empty-state
selection. Usage-only days show Today empty. Displayed event counts in Today,
Timeline and saved summaries omit screen time. Timeline retains all stored rows
and its original paging total, with a separate full-day event count read in the
same transaction. Summary source queries also exclude incomplete usage envelopes;
foreground usage metrics remain available. Summary algorithm version 3 rebuilds
earlier caches, including version 2 caches containing inflated entry counts. No
schema change, deletion, new permission or integration change was introduced.

- PASS: all 209 JVM tests, including mixed/usage-only Today, usage-free event counts,
  unchanged usage totals and a 170-usage-row Timeline paging/count regression.
- PASS: all 78 Room tests, including incomplete usage envelopes, version 2 cache
  rebuilding and full-day counts independent of the current Timeline page.
- PASS: all 15 affected UI tests on API 36.1: Today/summary, mixed Timeline details
  and count labels, 500/20,000-row traversal, day navigation and usage views. The
  new mixed Today test confirms two manual events and no usage card; after deleting
  the manual records, Today shows empty and the stored usage record remains intact.
- PASS: data lint, regular debug and test APK builds, and diff whitespace review.
- Read-only review found no Important correctness issues. No commit was created.

Logs: `/tmp/luma-today-usage-red.log`, `/tmp/luma-event-count-red.log`,
`/tmp/luma-today-usage-green-build.log`, `/tmp/luma-today-usage-data-tests.log`,
`/tmp/luma-today-usage-ui-tests.log`, `/tmp/luma-today-usage-normal-build.log`.
Device tests used only the disposable validation app; the installed user app and
its data were not replaced or cleared. Final APK outputs use the normal application
ID. Earlier unrelated full-suite Places timeout remains recorded above; this
follow-up verifies affected UI flows rather than claiming a new full app-suite run.

## Timeline zero-usage rows — 2026-10-12

Main Timeline excludes APP_USAGE records whose stored duration is exactly zero.
The shared SQL visibility predicate runs before LIMIT/OFFSET and is reused for
the paging total. Raw day counts, persisted records, direct detail lookup and
unavailable usage records remain intact; even a positive 1 ms observation remains
visible. Existing typed-content invalidation updates rows after usage corrections.

- PASS: all 209 JVM tests and 80 Room instrumentation tests. The new fixture has
  160 zero-use rows followed by positive, manual and unavailable records; only four
  visible records page correctly. Tests cover a zero-only day, retained zero-use
  storage and a zero→positive correction.
- PASS: seven Timeline/usage UI flows, including hidden imported zero-use apps,
  a positive→zero correction producing Timeline empty, correct event labels,
  500/20,000-entry traversal, day navigation and retained Settings usage views.
- PASS: data lint, normal debug/test APK builds and whitespace review. Read-only
  review found no Important correctness issue. No commit was created.

The initial new fixtures lacked required end timestamps and failed at validation
rather than the query. After correcting the fixtures, both regressions failed
against the unfiltered query (164 rows instead of four; nonempty zero-only day),
then passed with the filter restored. No production validation was loosened.

Logs: `/tmp/luma-timeline-unused-corrected-red.log`,
`/tmp/luma-timeline-unused-final-checks.log`, `/tmp/luma-timeline-unused-final-data.log`,
`/tmp/luma-timeline-unused-final-ui.log`, `/tmp/luma-timeline-unused-normal-build.log`.
Tests used the already-running API 36.1 emulator and disposable validation app;
the installed user app and its data were not replaced or cleared. Final APKs use
the normal application ID. Earlier full app-suite limitations remain recorded above.

## T15 simple lookup — 2026-10-12

User-approved reduced scope: titles, tags, people, places and dates, prioritizing
speed. Timeline opens Search, whose text uses literal escaped substring matching
on event/journal titles and linked catalog names. Outer whitespace is trimmed;
SQLite LIKE folds ASCII case and compares non-ASCII characters directly. Search
does not match bodies, notes, emotions, mood scores or event types. No FTS, new
product dependencies, permissions, schema changes or migration were introduced.

Date/catalog constraints and text combine before LIMIT/OFFSET. Different categories
use AND; selected tags/people use OR. Count and 50-result pages share a Room
transaction; only matching records' typed relations are loaded. The UI retains
at most 300 rows, debounces typing by 300 ms, cancels obsolete generations and
retains filters and opened-result anchors through recreation/detail return.
Deleted catalog selections remain explicit and do not broaden the query.

Verified:

- PASS: all **222 JVM tests**: 83 domain, 8 data, 112 app, 13 usage, 6 health.
  This includes three civil-date/input-limit domain checks, nine state checks and
  the actual-SQL host regression. The final complete suites ran with test tasks
  forced to execute, not inferred from compilation or UP-TO-DATE output.
- PASS: normal debug APK, app and data instrumentation APK builds and data lint.
  Six Room search cases and two Search UI flows compile. Current schema version
  remains 2, with its existing identity hash unchanged.
- PASS: production SearchSql output executed against the exported Room v2 schema
  using Python 3's SQLite 3.46.1. It verifies literal percent/underscore/backslash/
  quotes, Persian names, ASCII case matching, title/linked-name lookup, exclusion
  of body/catalog-note-only matches, SQL-looking text treated literally, duplicate
  link elimination, combined categories, ID selections, chronological ties,
  count/page agreement, zero-use visibility, title edits/renames and deletion.
- PASS: query-only benchmark with 50,000 journals across 1,825 dates (five years),
  1,300-character bodies, tags, person links and places. Twelve count+page samples
  per profile alternate offsets 0 and 200. Date lookup uses the existing
  occurrence/UUID index without a temporary sorting tree.

| Host query profile | Matching rows | Median ms | p95 ms |
| --- | ---: | ---: | ---: |
| All saved entries | 50,000 | 1.76 | 2.13 |
| Title substring | 1,111 | 31.31 | 44.53 |
| No matches | 0 | 36.79 | 38.32 |
| From date | 336 | 0.17 | 0.25 |
| Text + dates + tag + person + place | 8 | 28.63 | 30.41 |

These are host in-memory SQLite timings on the ThinkBook Linux workstation. They
exclude Room relation mapping, storage reopening, Android hardware and Compose;
they do not establish the target device-page p95 or native UI responsiveness.
The compiled Room fixture also contains 50,000 records and records full repository
timings for subsequent device validation.

Review reproduced three Important defects before fixing them: a 325-row result
set prepended overlapping rows after its short last page; a same-count reordered
source could append duplicate keys before invalidation arrived; returning from a
detail near the end of the retained window lost that result. All three JVM
regressions failed before the fixes, then passed with the six original state
tests. Earlier loads now request only the missing prefix, overlap/count drift
reloads a consistent first page, and opening a result saves its absolute anchor.
Replacement resets scroll while append/prepend keep stable keys. Follow-up
read-only review found no remaining Important issue in the fixes.

Commands:

```sh
bash gradlew --offline -I /tmp/luma-t13-mirror.init.gradle \
  -I /tmp/luma-t14-retest.init.gradle \
  :core:domain:test :core:data:testDebugUnitTest :app:testDebugUnitTest \
  :integration:usage:testDebugUnitTest :integration:health:testDebugUnitTest \
  :app:assembleDebug :app:assembleDebugAndroidTest \
  :core:data:assembleDebugAndroidTest :core:data:lintDebug
```

The host SQL test uses Python 3/standard-library SQLite; no additional Python
packages were installed. APKs were rebuilt after the final accessible picker/test
selector changes. All final artifacts use the normal application ID.

Pending: `adb devices` reports no connected device. No emulator was launched or
reset, and no app was installed/replaced or personal data accessed. Native Room
mapping, source navigation, recreation, filter dialogs, visual behavior and full
repository/UI performance remain unverified. T15 is implemented, validation
pending. Existing full app/health lint analyzer failures and T11/T13 platform
follow-ups remain as documented; this task claims only data lint.

Logs: `/tmp/luma-t15-data-red.log`, `/tmp/luma-t15-state-red.log`,
`/tmp/luma-t15-host-sql.log`, `/tmp/luma-t15-review-red.log`,
`/tmp/luma-t15-review-green.log`, `/tmp/luma-t15-final-checks.log`,
`/tmp/luma-t15-final-apks.log`. The host metrics are also in the data test XML
for `SearchSqlHostTest`. No commit was created.

## T16 — Weekly Review, 2026-10-12

Implemented the approved offline Weekly Review. It uses ISO Monday–Sunday weeks,
saved independent date selection and current-week weekday-matched comparisons.
Today is explicitly partial. Source actions browse exact contributing records in
50-row pages; memories, catalogs and saved usage retain existing source routes.
Report calculations use one transactional metric snapshot, weighted mood scales,
ID-based emotion/catalog frequencies, decimal activity totals and the same saved
session-first usage rules as Settings. No AI, network access, dependencies,
permissions or schema migration were added.

Verified in this session:

- PASS: **239 JVM tests**, forced to execute in the final run: 94 domain, 117 app,
  9 data, 13 usage and 6 health; zero failures, errors or skipped tests.
- PASS: normal debug APK, app instrumentation APK and data instrumentation APK.
  Eight new Room cases and three new Review Compose flows compile. They cover
  weighted statistics, exact sources, catalog identity/correction/deletion, source
  paging, activity units/providers, midnight sessions, displaced native usage
  parents, observed zero, live updates and saved historical-week restoration.
- PASS: app, data, usage and health lint with zero errors. App has eight existing
  warnings (target/dependency versions and older quantity strings); other modules
  have no issues. The earlier documented app/health detector failures did not
  recur with this checkout's configured lint 8.8.2. No lint suppression or
  dependency change was introduced by T16.
- PASS: production-generated metric source SQL against the exported Room v2
  schema via standard-library host SQLite. Checks cover all metric predicates,
  date boundaries, stable ordering, count/page agreement, measured zero activity,
  literal bound values, deletion and correction. A 50,000-entry history fixture
  across approximately five years measured count+page query p95 of 0.006–0.013 ms.
  This deliberately isolates indexed queries with most rows outside the selected
  period; it excludes Room mapping, dense selected weeks, device I/O and UI.
- PASS: generated Room code confirms the report projections exclude journal
  bodies, mood notes, photos and private catalog fields.
- PASS: `git diff --check`. Existing untracked release files, schema exports and
  Search resources were preserved. No commit, installation or emulator change.

A fresh read-only reviewer identified two important problems. The source state
regression reproduced an illegal Flow emission context; `transformLatest` now
cancels obsolete page loads and emits within a valid context. Empty partial usage
windows could mask an explicit zero aggregate; the pure saved-report selection
regressions now preserve known zero while keeping complete-session precedence.
These changes passed the final full suites. The corresponding Room zero fixture
is compiled, not runtime-verified.

Minor deferred: partial-week comparisons currently read up to six unused dates
between the previous comparison and the selected week, then discard them before
calculation. Results are correct; using two explicit date predicates could reduce
work. This remains recorded in the plan.

Pending: `adb devices -l` returned no connected device. The Room/UI tests,
source navigation/recreation, visual/accessibility checks and full repository p95
are unexecuted. A compiled fixture inserts 50,000 records across five years with
2,500 selected mood entries and records 20 full repository load timings; it does
not claim the <300 ms target until run. T16 is implemented, validation pending.
T11/T13/T15 platform follow-ups remain separate.

Final commands:

```sh
bash gradlew --offline -I /tmp/luma-t13-mirror.init.gradle \
  -I /tmp/luma-t14-retest.init.gradle \
  :core:domain:test :app:testDebugUnitTest :core:data:testDebugUnitTest \
  :integration:usage:testDebugUnitTest :integration:health:testDebugUnitTest \
  :app:assembleDebug :app:assembleDebugAndroidTest \
  :core:data:assembleDebugAndroidTest :app:lintDebug :core:data:lintDebug \
  :integration:usage:lintDebug :integration:health:lintDebug
```

Logs: `/tmp/luma-t16-baseline.log`, `/tmp/luma-t16-domain-red.log`,
`/tmp/luma-t16-data-red.log`, `/tmp/luma-t16-sql-red.log`,
`/tmp/luma-t16-state-red.log`, `/tmp/luma-t16-ui-red.log`,
`/tmp/luma-t16-checks.log`, `/tmp/luma-t16-checks-green.log`,
`/tmp/luma-t16-zero-red.log`, `/tmp/luma-t16-zero-green.log`,
`/tmp/luma-t16-app-health-lint.log`, `/tmp/luma-t16-final-checks.log`.

## T21 — App language selection task added, 2026-10-12

Status: pending. Added T21 to [TASKS.md](../TASKS.md) for a saved Settings choice
between English, Persian and French, translated app-owned text, locale-aware
formatting and Persian right-to-left layout. The task includes preserving stored
content, selected dates and unsaved drafts during language changes.

Planned validation: exercise all three languages across primary screens, capture
forms, dialogs and accessibility labels; verify language persistence through
navigation, recreation and restart; check Persian layout and mixed-language text;
verify plurals, date/number formatting, English fallback and content/draft preservation.
No language-selection implementation or runtime checks have been performed.

## T17 — Backup, export and restore, 2026-10-13

Implemented manual Settings export and replacement restore; the format and data
semantics are documented in [BACKUP_FORMAT.md](BACKUP_FORMAT.md). No live user
restore, app installation, commit, schema migration or dependency change occurred.
Existing untracked release files, Room schemas and Search test resources were
preserved. Test stages use isolated temporary/in-memory stores only.

Verified in this session:

- PASS: **260 JVM tests**, forced to execute: 94 domain, 126 app, 21 data, 13 usage
  and 6 health. Zero failures, errors or skipped tests. T17 adds 17 JVM cases.
- PASS: normal debug APK and app/data instrumentation APK builds. Thirteen Room backup
  tests and two Compose tests compile. Native fixtures cover journal/catalog/tag/
  marker/photo round trip, repeated backups of remapped photos, mood/emotions,
  session/windows, stale summaries, explicit replacement, v1 compatibility,
  checksummed malformed rows/references/types, missing photos, injected commit
  failure, health timezone/revision, observed catalog invalidation and abandoned
  staging cleanup. Compose sources cover cancellation and busy/success controls.
- PASS: app/data/usage/health lint with zero errors. App has 11 existing target,
  dependency, locale-config and quantity warnings; the other modules have none.
- PASS: production-generated backup SQL executed with host Python SQLite against
  the compiler-exported Room v2 schema. Tests prove existing typed-child mismatch
  rejection, all-table transaction rollback after a broken FK insertion, and
  replacement without retaining the prior catalog. This does not verify Room's
  Android connection handling or invalidation.
- PASS: archive and TSV tests preserve null/empty/Unicode/numeric values, enforce
  line/expanded-size bounds, reject traversal/unknown/duplicate names, missing or
  modified payloads, malformed types/headers and future format/schema versions,
  and verify caller-owned streams remain open. Pure domain guards reject invalid
  native labels/packages and deep JSON before Android parsing. Photo-file tests
  prove backup publication never overwrites an existing path or acquires a draft
  lease. Settings state tests require confirmation, close cancelled previews,
  suppress duplicate actions, close failing streams and expose failures.
- PASS: `git diff --check`.

An independent read-only review identified missing child/parent graph checks,
missing UsageSession/UsageBucket limits, unbounded native JSON recursion and photo
rename durability. These were addressed with graph/domain guards, a 64-level JSON
precheck and photo directory fsync before replacement. Stream ownership was also
fixed. The stream/session/bucket regressions failed before their fixes and pass
in the full suite; host SQLite and the bounded-depth guard pass. Health restore
retains the saved import zone and advances revision beyond live/imported values.

Compatibility ruling: require child/parent type and source agreement, but retain
native usage bucket dates independently from an older/displaced event envelope.
T16 explicitly supports that distinction. A production-SQL regression failed with
the initially strict bounds-equality check; removing that check preserves the
supported case while validating each interval independently. The final host test
passes; an additional native round-trip fixture compiles but is unexecuted.

Pending: escalated `adb devices -l` listed **no connected device**. Native Room
round trip and rollback, image decoding, SAF provider selection/cancellation,
activity/process recreation, concurrent capture/import/cleanup, actual observed
Room invalidation, visual/accessibility layout and power-loss durability are
**unexecuted**. Compilation and host SQL are not native acceptance evidence.
T17 remains **implemented (validation pending)**. The earlier T11/T13/T15/T16
platform follow-ups and partial T21 status remain unchanged.

Final command (temporary init only forces JVM tests to execute):

```sh
bash gradlew --offline -I /tmp/luma-t17-force-tests.init.gradle \
  :core:domain:test :app:testDebugUnitTest :core:data:testDebugUnitTest \
  :integration:usage:testDebugUnitTest :integration:health:testDebugUnitTest \
  :app:assembleDebug :app:assembleDebugAndroidTest \
  :core:data:assembleDebugAndroidTest :app:lintDebug :core:data:lintDebug \
  :integration:usage:lintDebug :integration:health:lintDebug
```

Logs: `/tmp/luma-t17-baseline.log`, `/tmp/luma-t17-codec-red.log`,
`/tmp/luma-t17-codec-green.log`, `/tmp/luma-t17-room-red.log`,
`/tmp/luma-t17-publication-red.log`, `/tmp/luma-t17-ui-red.log`,
`/tmp/luma-t17-ui-green.log`, `/tmp/luma-t17-review-regressions-red.log`,
`/tmp/luma-t17-json-red.log`, `/tmp/luma-t17-review-green.log`,
`/tmp/luma-t17-native-bounds-red.log`, `/tmp/luma-t17-final-checks.log`.
