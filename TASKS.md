# Ordered implementation tasks

Based on the supplied Word task document and product definition v0.1.
Original source documents remain unchanged; readable extracts are in `docs/`.

Status: pending, in progress, implemented (validation pending), or complete.
A task is complete only after its acceptance criteria have been verified.


## T01 — Bootstrap the Android project

Status: complete

Validation (2026-10-12): application build/launch and all primary navigation,
route/date recreation and Quick Capture checks pass on the existing API 36 emulator.
All 189 JVM tests and 101 Android instrumentation tests pass. Foundation scope and
architecture remain documented. The separate app/health lint compatibility issue
is still recorded in docs/VALIDATION.md.


### Scope

- Choose and document the concrete Android stack.
- Create the application, build configuration, package structure, CI-friendly test setup, and baseline navigation.
- Create ARCHITECTURE.md and keep this task document reflected in TASKS.md.

### Acceptance criteria

- Project builds and launches.
- Unit/instrumentation test commands run successfully.
- No product feature is implemented prematurely.

## T02 — Establish architecture and core domain

Status: complete

Validation: 13 JVM tests passed, including identity, audit/occurrence invariants, deterministic ordering, timezone offsets, and DST boundaries.


### Scope

- Implement the modular-monolith boundaries and domain layer.
- Define LifeEvent and shared identifiers/timestamps/source concepts.
- Create repository interfaces so domain code is not coupled to Android integrations.

### Acceptance criteria

- Domain layer has no unnecessary Android dependencies.
- Core entities have unit tests.
- Architecture decisions are documented.

## T03 — Implement local persistence

Status: complete

Implementation: added the `core:data` Android library, Room version 1 schema for all
13 records and four link tables, indexed relations, DAOs, domain envelope mapping,
transactional event/catalog/cache repositories, source-identity reconciliation,
atomic import/checkpoint writes, and lazy application storage wiring. Persistence
and migration baseline instrumentation test sources are included.

Validation (2026-10-12): all 72 Room instrumentation tests pass on API 36,
including CRUD, batched/indexed relations, foreign keys, rollback, source retry
identity, cache invalidation and exported-schema reopening. Version 1 reopens with
content retained; unsupported versions fail without destructive replacement. Debug
and test APKs build and data lint passes. The generated v1 schema is present and
used by the tests; include the existing untracked JSON in the next commit before
changing schema versions. See docs/VALIDATION.md.


### Scope

- Add Room/SQLite schema for LifeEvent, MoodEntry, Emotion, JournalEntry, Memory, Person, Place, MediaAttachment, Tag, ActivityRecord, AppUsageRecord, DailySummary, and IntegrationState.
- Implement relations, indexes, DAOs/repositories, migrations baseline, and transaction boundaries.

### Acceptance criteria

- CRUD tests pass.
- Relations can be loaded efficiently.
- Schema versioning and migration testing exist.

## T04 — Build application shell and Today screen

Status: complete

Implementation: Room-backed Today feed with current/selected-day navigation,
calendar picker, deterministic rows, event counts by type, distinct loading/empty/
error states with retry, saved selection, and foreground day/timezone refresh.
Quick Capture is a persistent action on every primary tab and opens an explanatory
sheet; capture forms remain assigned to T05–T07. Core browsing requires no permissions.

Validation (2026-10-12): API 36 tests pass for loading/empty/error/retry,
stored values/times, selected-day Room updates, picker dates outside the default
year range, date/route recreation and Quick Capture on every primary tab. Core
navigation and capture run without optional integration access. Foreground
midnight/zone changes and broad visual/accessibility audits remain follow-ups in
T19; full app lint retains its documented analyzer compatibility issue.


### Scope

- Create primary navigation and Today screen.
- Show empty/loading/data states and daily event summary.
- Add persistent Quick Capture entry point.

### Acceptance criteria

- App remains useful with zero permissions.
- Today screen renders records for selected/current day.
- Quick Capture is reachable in one obvious action.

## T05 — Mood and emotion check-ins

Status: complete

Implementation: permission-free local mood capture/edit/delete with an explicit
1–5 score, reusable multi-select emotions, optional energy/stress/note, and date/
time controls. Saved drafts survive recreation and loading errors; stable capture
IDs reconcile committed writes after process death. Edits preserve unrelated links
and metadata. Typed mood rows update on Today and the initial daily Timeline.

Validation (2026-10-12): API 36 capture/recreation/edit/delete and Room
mood/emotion persistence cases pass. A minimal Quick Capture -> Mood -> score 4 ->
Save flow completed in 4.97 seconds, including UI confirmation overhead, with
optional access denied. SQLite contained the saved score and Today displayed the
matching average. Multiple emotions, optional scales, retained drafts and timeline
updates have passing device/JVM coverage. See docs/VALIDATION.md.


### Scope

- Implement mood score, multiple emotions, optional energy, stress, note, and timestamp.
- Provide fast create/edit/delete flows.
- Render mood events on Today and Timeline.

### Acceptance criteria

- A check-in can be recorded in seconds.
- Multiple emotions are supported.
- Edits persist and timeline updates correctly.

## T06 — Journal and tags

Status: complete

Implementation: offline journal create/edit/delete with optional title, required
body, timestamp and reusable tags. Case/whitespace variants share tag identities;
body formatting survives edits. Drafts and capture IDs survive recreation,
failed saves retain inputs, and navigation confirms discard. Visible size limits
protect saved drafts; confirmed deletes reconcile committed results after restoration.
Transactional edits
preserve other links and metadata. Today/Timeline previews open a full detail view.

Validation (2026-10-12): API 36 journal capture/recreation/detail/edit/delete,
explicit discard, oversized-input retention, tag reuse and transactional persistence
checks pass. Device execution exposed an error cleared by redundant text callbacks;
a new JVM regression failed before the fix and passes afterward, together with the
Journal device flow and full suites. Body formatting and source/link preservation
are verified. The app has no Internet permission. See docs/VALIDATION.md.


### Scope

- Implement journal create/edit/delete with optional title, body, timestamp, and tags.
- Support reusable tags and text validation.
- Render entries on timeline and detail view.

### Acceptance criteria

- Journal works fully offline.
- Tags can be reused and filtered later.
- No data loss on edit/navigation.

## T07 — Memories

Status: complete

Implementation: direct offline memory capture/edit/delete with optional title,
importance and timestamp; complete manual moods and journals support reversible
memory markers. Marker changes preserve source content, identity, metadata and
links. Bounded saved drafts survive recreation and errors; ambiguous saves and
confirmed removals reconcile committed results. Today/Timeline and journal detail
show memory status; Review lists the current ISO calendar week’s stored memories.
Full review statistics remain T16.

Validation (2026-10-12): direct memory capture/recreation/review/edit/delete,
journal promotion and reversal pass on API 36. Room cases verify source content,
identity, metadata, links, marker visibility and rollback without duplicate events.
All JVM and Android suites pass. Full weekly statistics remain T16; full app lint
retains its documented compatibility issue. See docs/VALIDATION.md.


### Scope

- Allow direct creation of a memory.
- Allow an eligible existing event/journal entry to be promoted/linked as a Memory without duplicating the underlying content.
- Expose notable memories in timeline and reviews.

### Acceptance criteria

- Promotion is reversible where appropriate.
- Underlying event remains intact.
- Memory status is clearly visible.

## T08 — People

Status: complete

Implementation: offline people list/create/edit/delete and detail with chronological
linked event history. Mood, journal, standalone-memory and promoted-memory forms
support multiple saved person selections and creating a person without losing the
parent draft. Typed feeds, journal detail and memory Review show linked names.
Catalog identities remain distinct even for equal names; deleting a person removes
only their links and retains all event content. Existing capture calls preserve
links unless they provide an explicit replacement selection. Writes remain atomic.

Validation (2026-10-12): all five People Room cases and both capture/history
flows pass on API 36. Tests verify multiple selections, retained parent drafts,
recreation, live chronological history, explicit link removal and person deletion
without deleting linked source content. The previously invalid JUnit return type
was corrected, allowing the history observation test to execute. See docs/VALIDATION.md.


### Scope

- Implement Person list/create/edit/detail.
- Allow supported events, journals, and memories to reference multiple people.
- Show a person's linked life events chronologically.

### Acceptance criteria

- People can be attached during capture/edit.
- Deleting a person does not delete life events.
- Person detail shows correct linked history.

## T09 — Manual Places

Status: complete

Implementation: permission-free local place list/create/edit/delete/detail,
reachable from Settings and capture forms. Mood, journal, standalone-memory and
promoted-memory editors retain one optional place selection and can create a place
without losing the parent draft. Existing coordinates survive name/note edits.
Place links save atomically with entry content; explicit clearing removes only the
place link, and deleting a place preserves all life events. Typed browsing shows
linked place names. No location tracking or permission is added.

Validation (2026-10-12): all five Places Room cases and both capture/reuse/
rename/clear/deletion UI flows pass on API 36. Optional selection persists; deleting
a place retains source entries and other links. Core place capture needs no location
permission; none is declared. All JVM and Android suites pass. See docs/VALIDATION.md.


### Scope

- Implement Place list/create/edit/detail.
- Allow manual selection/attachment of a place to supported events.
- Do not implement continuous location tracking.

### Acceptance criteria

- Place is optional.
- Existing places can be reused.
- No location permission is required for core MVP.

## T10 — Photo attachments

Status: complete

Implementation: journal, direct-memory and promoted-memory forms support local
image selection, bounded previews, retry, removal and unavailable-photo states.
Images copy into app-private storage before transactional reference/metadata
writes. Stable pending identities and durable draft leases protect restored,
unsaved and concurrent-editor selections; cleanup checks current references and
leases only after database success. Promotion shares source photos and unmarking
preserves them. Journal detail renders photos; browsing shows attachment counts.
No broad media permission, network upload, automatic backup or schema migration
is added.

Validation (2026-10-12): all seven photo Room cases and both rendering/removal/
unavailable-photo UI flows pass on API 36. A disk-database/private-store close and
reopen preserves the referenced image without its original source. Removal keeps
entry content and links intact; rollback and draft protection pass. No Internet
permission or automatic backup exists. The native system-picker interaction and
broader visual/accessibility audit remain platform follow-ups. See docs/VALIDATION.md.


### Scope

- Allow locally stored image attachments on journal/memory events.
- Persist durable references and metadata safely.
- Handle missing/deleted media gracefully.

### Acceptance criteria

- Photo survives app restart.
- Removing an attachment does not corrupt the event.
- Sensitive media is never uploaded automatically.

## T11 — Complete Life Timeline

Status: implemented (validation pending)

Implementation: dedicated chronological day Timeline with calendar/date navigation,
indexed transactional pages and deterministic occurrence/UUID ordering. A bounded
150-record initial window loads 50 at a time and retains at most 300; stable rows,
earlier/later controls, automatic later loading, saved window restoration and
foreground refresh support long days. Shared previews show stored activity and
app-usage values alongside manual records. Complete manual records retain their
content routes; imported/incomplete records open live read-only details with
missing/error states, linked metadata and photos. Integration adapters remain
T12/T13. No schema, SDK, permissions or dependencies changed.

Validation (2026-10-12): five Timeline Room cases and mixed-detail/day navigation,
500-entry window traversal and a 20,000-entry/three-year navigation fixture pass
on API 36. Day changes measured 115–133 ms. A real-time native-input frame probe
captured 1,112 frames: p95 21.96 ms, 52.43% missed platform deadlines, no dropped
reports. Remaining acceptance: smooth scrolling is not established on this debug
emulator; profile/optimize and verify on representative hardware. Functional paging,
ordering, restoration and cancellation pass. Full app lint retains its analyzer
compatibility issue. See docs/VALIDATION.md.

Follow-up (2026-10-13): the full app instrumentation suite passes after updating
the English activity-label assertion for T21 localization. The 20,000-record
navigation fixture measured 116–150 ms across four day changes. Smooth-scroll
frame performance remains unverified, so T11 stays open.


### Scope

- Build day-based chronological timeline with date/calendar navigation.
- Merge manual events and imported signals into a coherent ordered feed.
- Add event detail/edit routes and appropriate type-specific rendering.

### Acceptance criteria

- Ordering is deterministic.
- Multi-day navigation is fast.
- Large sample datasets scroll smoothly.

## T12 — Screen-time integration

Status: complete

Update requested (2026-10-12): reopen T12 for the reported issue where unused apps
also appear in the usage list, and add two selectable views for the selected day:
an app-session timeline and cumulative usage per app. This update is implemented
and verified as described below; the original baseline remains documented afterward.

Update implementation: Settings offers a saved Timeline/Daily totals selector.
Native foreground events reconstruct chronological visits with opening/end times,
durations, continued-from-previous-day and ongoing labels. Both views use the same
recorded sessions, clipped at civil-day boundaries, with same-app overlaps unioned.
Zero-use apps are excluded from usage and summary lists. Native event retention
and missing boundaries are explicitly partial/unavailable; no visits are inferred
from daily aggregates. Aggregate-only days retain their older totals. Separate
session/window tables use an additive Room v1→v2 migration; daily records, metadata,
links and summaries remain intact. Buckets/sessions/checkpoint commit atomically
under existing access/clock/opt-out guards; retries retain identities and history.

Update validation (2026-10-12): all 205 JVM tests and 76 Room instrumentation tests
pass on the API 36.1 emulator, including migration/reopening, retry/rollback,
retention fallback, complete/partial windows and opt-out. Eight focused native,
usage Settings and summary UI checks pass, including real Android session import,
view switching/restoration and midnight splitting. Normal debug/test APKs build;
data/usage lint passes. The broader app run passed 31 of 32 tests, with an unrelated
Places picker timeout that passed isolated retry; this is not a claim of a clean
full app-suite run. Public native event identity/retention cannot guarantee complete
session history, and OEM/reboot/six-hour-cycle follow-ups remain. See docs/VALIDATION.md.

Implementation: opt-in Android daily foreground-usage adapter, native Usage access
setup/status UX, manual imports and unique six-hour background work. Incremental
overlapping imports preserve native bounds/totals, original event/child identities,
links and metadata, with atomic checkpoints. Settings shows saved daily app totals
and package/label fallbacks shared by Timeline/Today/details. Empty, locked,
unsupported, revoked and error states retain prior data; opt-out remains available
during a query. Persisted clock/boot checks reject rewritten native history and
resume with fresh intervals, conservatively leaving affected intervals incomplete.

Validation (2026-10-12): native Android Usage access setup/status, real
UsageStats imports and an initial scheduled WorkManager import were exercised on
API 36 using a separate validation app. Imports saved 502 native rows, including
positive foreground time. A retry retained all event/child/package/start identities
and row count while 25 intervals updated bounds or totals. Revocation, restart and
opt-out retained saved rows and the successful sync timestamp. Core capture still
worked without access. Six Room and two UI flows plus JVM suites pass. OEM battery
restrictions, a full six-hour refresh cycle and reboot/clock-correction acceptance
remain platform follow-ups. See docs/VALIDATION.md.


### Scope

- Integrate Android app-usage/screen-time APIs behind an adapter.
- Implement permission/status UX, incremental/idempotent imports, package/app labeling, and daily aggregation.
- Fix the usage list so apps with no recorded foreground usage on the selected day are excluded.
- Show at most the three highest-usage apps over one minute on Today, with the displayed count matching the shown rows.
- Keep Timeline app-usage rows hidden by default behind a persisted switch; when enabled, show only records over one minute and apply the same filter before paging and counting.
- Provide two selectable views for the selected day: "Timeline" and "Daily totals".
- In Timeline, show each recorded app session chronologically with the app name, opening/start time, end time, and duration; keep separate visits to the same app visible.
- In Daily totals, show the cumulative foreground usage duration for each used app on the selected day.
- Use recorded foreground-session events for Timeline; do not infer opening times or individual sessions from daily aggregates. Represent unavailable session history explicitly.
- Gracefully handle unsupported/revoked permission states.

Display update (2026-10-12): screen-time records are excluded from the Today feed
and from displayed event counts on Today, Timeline and daily summaries. A usage-only
day shows Today empty. Timeline retains usage rows and a separate total for paging;
summary algorithm version 3 rebuilds older event counts while retaining usage metrics.
Validation: all 209 JVM tests, 78 Room tests and 15 affected Today/summary/Timeline/
usage UI tests pass; data lint and normal debug/test APK builds pass.

Timeline visibility update (2026-10-12): known zero-duration app records are hidden
before Timeline paging, including saved imports and later corrections. The visible
row total uses the same filter; positive millisecond values and unavailable details
remain visible, and stored records remain intact. All 209 JVM, 80 Room and seven
Timeline/usage UI tests pass; data lint and normal debug/test APK builds pass.

Usage display update (2026-10-13): Today shows at most the three highest-usage
apps strictly over one minute. Timeline app usage is hidden by default; a saved
switch enables rows strictly over one minute, with filtering applied before page
and count queries. Stored usage and raw day counts remain unchanged. Validation
passed: 278 JVM tests, 108 Room instrumentation tests, all 53 app instrumentation
tests, debug/release and instrumentation APK builds, and app/data/usage/health
lint. Full details are in `docs/VALIDATION.md`.

Duration display update (2026-10-13): Today and Timeline app-usage durations show
whole hours and minutes. Seconds and milliseconds are omitted; leftover partial
minutes are truncated. Other usage views retain their existing precision. All
four DailySummaryFlow and five TimelineFlow UI tests pass; app unit tests, debug
and release builds, instrumentation APK build and app lint pass.

### Acceptance criteria

- No fabricated usage data.
- Repeated imports do not duplicate records.
- Apps with zero recorded foreground usage on the selected day do not appear in either view; a day with no usage shows an empty state.
- User can switch between Timeline and Daily totals for the same selected day.
- Screen-time records do not appear as Today events or contribute to displayed event counts; imported usage and its views remain available.
- Today shows no more than three apps, all with strictly more than one minute of usage, and counts only displayed apps.
- Timeline app usage defaults to hidden; users can persistently show only rows with strictly more than one minute of usage. Hidden/short rows do not create empty pages or incorrect paging totals.
- Today and Timeline app-usage duration labels show hours and minutes only, without seconds or milliseconds.
- Timeline shows when each app was opened and how long each recorded visit lasted, including repeated visits to the same app in chronological order.
- Daily totals correctly combine all recorded usage for each app within the selected day's boundaries; visits spanning midnight contribute only their overlap with that day.
- When session history is complete, session durations and per-app daily totals agree; unavailable or incomplete session history is clearly indicated without fabricated visits.
- User can use the rest of the app without permission.

## T13 — Health/activity integration

Status: implemented (validation pending)

Implementation: opt-in read-only Health Connect steps behind an Android adapter,
permission/provider/status UX, native privacy-rationale routes and foreground
manual sync. Native aggregation avoids overlapping-source double counts. A durable
import timezone and calendar-date identities retain one set of step totals across
travel and retries. Seven-date atomic refreshes update corrections and reconcile
removed data, preserving IDs/audit/metadata/links during updates. Null is unavailable,
explicit zero is retained; errors/revocation keep history. Immediate opt-out cancels
pending queries and rejects stale commits even across disable/re-enable. Existing
Today/Timeline/details and saved summaries consume activity values. Sleep/workouts
and background health reads remain deferred; schema and API 28/35 are unchanged.

Validation (2026-10-12): six health Room cases, two Health Settings UI flows
and all JVM tests pass, covering simulated imports/corrections/deletion, identity,
rollback, durable controls, revocation and unsupported states. A read-only native probe on the
API 36 emulator returned GRANTED / NO_DATA with zero stored activity rows. Remaining
acceptance: import a known step-data fixture through native consent on a supported
device; an empty provider does not establish that useful native signal. App/health
lint retain their documented detector crashes. See docs/VALIDATION.md.

Follow-up (2026-10-13): Health Settings UI cases pass in the 50-case app suite.
No Health Connect read permission or synthetic step fixture was granted during this
run; useful native step import remains unverified.


### Scope

- Integrate supported Android health APIs, preferably Health Connect, behind an adapter.
- Start with steps; add sleep/workouts only when API/data availability is reliable.
- Implement permission/status and idempotent sync.

### Acceptance criteria

- At least one useful activity signal imports correctly on supported devices.
- Duplicate imports are prevented.
- Unsupported data types do not break the app.

## T14 — Daily summaries

Status: complete

Implementation: deterministic saved daily metrics on Today with entry/journal/
memory counts, mood/energy/stress averages and sample counts, foreground usage
totals and labeled per-app durations, and activity totals grouped by type/unit/
source. Promotions retain one underlying entry. Civil-day occurrence bounds and
native usage-start dates remain distinct. Missing signals are unavailable while
recorded zero is retained. Today omits mood, energy, or stress mean rows when
their values are unavailable. Versioned derived-only Room caches rebuild after
source changes, collection restart, corruption or incompatible versions; reads and cache
writes are transactional. Summary errors retain source browsing and expose retry.

Validation (2026-10-12): all eight summary Room cases and three Compose
flows pass on API 36. Tests verify source-matching metrics, deterministic cache
rebuilds, corruption/version fallback, transactional invalidation, missing versus
zero, native-start dating and labeled per-app durations. All 189 JVM tests pass.
Known minor: summary Retry resets feed/scroll state; this does not affect metric
correctness. Full app/health lint remain blocked by their existing analyzers.
See docs/VALIDATION.md.


### Scope

- Compute reproducible daily metrics from raw/manual records.
- Include useful mood, journal/memory, screen-time, and activity summaries when available.
- Cache only derived values that can be rebuilt.

### Acceptance criteria

- Summary matches source records.
- Recalculation is deterministic.
- Missing sources are represented as unavailable, not zero unless zero is known.

## T15 — Search and filters

Status: implemented (validation pending)

Scope revised by the user (2026-10-12): prioritize app performance and simple
lookup by titles, tags, people, places and dates. Body/note search, mood/emotion
and event-type filters and full-text indexing are outside this stage.

Implementation: Timeline opens local Search with literal title/linked-name
substring matching and optional date/tag/person/place filters. AND between
categories and OR within tag/person selections run in SQL before paging. Bound
arguments preserve literal wildcard input; catalog IDs preserve distinct names.
Newest-first 50-row pages retain at most 300 rows. Typing is debounced, obsolete
queries are cancelled, filters/date zones and opened-result anchors survive
restoration, and source changes refresh matches. Known zero-use app records follow
Timeline visibility. Results reuse the existing source routes. No schema migration,
new product dependency, permission or body-text index was introduced.

Validation (2026-10-12): all 222 JVM tests pass; normal debug/test APKs compile and
data lint passes. Host SQLite executes the production SQL against the exported v2
schema, checking combined filters, Persian/literal names, body exclusion,
count/pages, edits/deletion and a 50,000-entry/five-year fixture. Query-only p95 is
0.25–44.53 ms across broad/title/no-match/date/combined profiles on the host; this
excludes Room relation mapping and device UI. Six Room and two Search UI device
tests compiled. At that initial run no device was connected, so native result
navigation, recreation, visual checks and end-to-end page timing remained pending.
Review paging/anchor
findings were reproduced with failing JVM regressions and fixed. See docs/VALIDATION.md.

Follow-up (2026-10-13): both Search UI flows and the 50,000-row Room fixture pass
on API 36. Device page measurements show about 2.0–2.2 seconds for title-substring
and no-match queries; broad and date-filtered pages take about 10–25 ms. Large-fixture
substring responsiveness is not acceptable yet, so T15 remains open for query
optimization and representative-device retesting.

### Scope

- Implement local lookup on titles and linked tag/person/place names.
- Combine optional date, tag, person and place filters.
- Keep queries and retained results bounded; measure realistic multi-year data.

### Acceptance criteria

- Combined filters work.
- Results link to source entries.
- Search remains responsive on a realistic large fixture dataset.

## T16 — Weekly Review

Status: implemented (validation pending)

Implementation: permission-free offline Review with saved ISO week/date selection,
matched previous-period comparisons, weighted mood/energy/stress averages, daily
mood samples, recorded emotion frequencies, notable memory previews, linked
people/place counts, saved foreground app usage and activity grouped by unit and
source. Current-week reviews compare matching weekdays and label today partial.
Metric-only Room projections read one transaction; exact metric predicates drive
50-row source pages and existing content routes. Memories retain original content
and marker actions. Saved per-date usage evidence preserves session precedence,
midnight clipping, native-start aggregate dating and history labels; recorded zero
survives empty partial windows. No AI, schema change, dependency or permission added.

Validation (2026-10-12): all 239 JVM tests pass; normal debug and both test APKs
build, and app/data/usage/health lint passes with zero errors. Actual generated
metric-source SQL executes against the exported v2 schema, including a 50,000-record history fixture. Eight
Room and three Review UI tests compile, including source traceability, corrections,
paging, restoration and a repository timing fixture. At that initial run no device
was connected; native Room/UI execution, visual checks and end-to-end repository
p95 remained pending. Host query timings exclude Room/device costs. Previously documented
app/health analyzer crashes did not recur in this run. Minor deferred: partial
comparisons read a few excluded previous-week dates before discarding them. See docs/VALIDATION.md.

Follow-up (2026-10-13): the three Review UI flows and Room suite pass on API 36.
The 50,000-row/five-year repository fixture measured median 330 ms and p95 364 ms
with 2,500 selected mood entries, above the 300 ms target. T16 remains open for
optimization and retesting.


### Scope

- Generate deterministic weekly statistics and comparisons with the previous period.
- Surface mood/emotion patterns, notable memories, people/places, digital behavior, and activity when data exists.
- Keep AI narrative optional and disabled by default.

### Acceptance criteria

- Review is useful without AI.
- Every statistic can be traced to stored data.
- No causal claims are generated from correlations.

## T17 — Backup, export, and restore

Status: implemented (validation pending)

Implementation (2026-10-13): Settings exports a versioned ZIP with all application
records and referenced private photos, and validates a chosen backup before a
replacement confirmation. Typed TSV preserves IDs, nulls, Unicode, timestamps,
relations, metadata and usage history. Inventory/hash/path/size checks, generated
Room staging, row/domain invariants, graph constraints and image validation reject
invalid backups. Schema 1 and 2 are supported explicitly; the live schema stays 2.
Shared photo serialization, fresh paths, file/directory sync and a single replacement
transaction protect existing records/photos on failure. Imported observations remain;
imports turn off, device usage cursors reset, health timezone survives and health
revision advances. Derived summaries are recalculated. See docs/BACKUP_FORMAT.md.

Validation: all 260 JVM tests pass, including archive/row bounds, malicious paths,
duplicates, checksum/version failures, JSON-depth/domain validation, photo
publication and Settings state transitions. Production backup SQL against the
exported Room schema verifies graph mismatch detection and transaction rollback.
Debug and both instrumentation APKs build; app/data/usage/health lint has zero errors.
Thirteen Room backup tests and two Compose flows compiled. At initial implementation
validation no device was connected, so native round trip/replacement, images, file
picker, concurrency, lifecycle and power-loss acceptance remained pending. See
docs/VALIDATION.md.

Follow-up (2026-10-13): the API 36 Room instrumentation suite passes 107/107,
including native backup round-trip, replacement and rollback cases. Backup Settings
flows also pass. On the isolated validation install, Android's actual create-document
picker exported a ZIP to Downloads; the open-document picker loaded that generated
backup, showed a two-event/zero-photo replacement preview, and confirmed restore.
Both synthetic events then appeared in Timeline. Backing out of the restore picker
also returned without starting a restore. Process lifecycle, concurrent cleanup and
power-loss acceptance remain open.


### Scope

- Define a versioned portable backup format.
- Export database content and referenced photos safely.
- Implement restore validation, conflict/replacement behavior, and migration compatibility.

### Acceptance criteria

- Round-trip backup/restore preserves supported data.
- Invalid backups fail safely.
- Backup format contains an explicit version.

## T18 — Privacy, permissions, and settings

Status: implemented (validation pending)

Implementation (2026-10-13): Settings opens Privacy and local data with local
storage, optional permissions, private photo copies, backup and retention policy.
The native Health Connect rationale reuses the read-only policy. Usage Access
management remains reachable while imports are off, and Usage/Health permission
launches require an explanation and explicit Continue. Existing foreground and
before/after-read access guards remain; a pending Usage access check now exits
before querying if the user turns imports off. Opt-out wording distinguishes
in-progress native queries from future import requests and retained history.

Delete and reset Luma explicitly confirms a full Android app-data reset, including
records, catalogs, imported copies, private/draft photos, caches, backup staging and
preferences. Android owns process shutdown and erasure; the app reports only
request acceptance, suppresses duplicates and offers retry/native Settings for
rejection. Exported backups and original provider data are outside this reset.
See docs/PRIVACY.md for boundaries and native acceptance steps.

Validation: all 266 JVM tests pass, including reset confirmation/cancellation,
rejection/retry, lifecycle and the RED→GREEN delayed-access opt-out regression.
Debug and both instrumentation APKs build; app/data/usage/health lint has zero
errors. Five new/extended Compose cases compile. Built manifest/source/dependency
audit finds no Internet permission, telemetry SDK or production content logging;
backup and device-transfer extraction remain disabled. The initial implementation
validation had no connected device, so actual reset/termination/reopen, provider
permission dialogs, native lifecycle,
external-copy preservation and visual acceptance remain pending. No real data was
reset, app installed, permission granted or Git mutation performed in this task.

Follow-up (2026-10-13): the privacy and permission-explanation UI cases pass in the
API 36 app suite. The Android reset boundary remains mocked; no app data was erased,
and no real provider permission was granted. Native reset/provider lifecycle checks
remain open.


### Scope

- Create privacy/integration settings.
- Explain sensitive permissions before requesting them.
- Support revocation-aware states and deletion of locally stored data.
- Ensure no sensitive telemetry is emitted.

### Acceptance criteria

- Core app works without optional permissions.
- Permission changes are reflected correctly.
- User can delete their stored life data.

## T19 — Reliability, performance, accessibility

Status: pending

Validation follow-up (2026-10-13): all 277 JVM tests, 107 Room instrumentation tests
and 50 app instrumentation cases completed without failures (one usage-access test
was skipped because special access was absent). Debug/release builds and module lint
passed. Large Timeline day navigation measured 116–150 ms, but Search text scans
measured 2.0–2.2 seconds and Weekly Review p95 measured 364 ms against 300 ms. Smooth
scrolling, broad TalkBack/screen-size audit and the performance targets remain open.


### Scope

- Add migration, aggregation, repository, import-idempotency, and backup tests.
- Test multi-year fixture datasets and optimize indexes/queries.
- Audit scalable text, content descriptions, and basic accessibility.

### Acceptance criteria

- No known destructive migration path.
- Timeline/search performance is acceptable on large fixtures.
- Critical flows are covered by automated tests.

## T20 — MVP release readiness

Status: pending

Validation follow-up (2026-10-13): the isolated fresh-install app navigation and
capture flows pass on API 36; debug APK, instrumentation APKs and unsigned release
APK assemble. The release manifest has no INTERNET permission, and README/architecture
limitations now include current validation evidence. End-to-end Health Connect
fixture, native backup picker and reset, plus Search/Review performance follow-ups
remain before release readiness is complete.


### Scope

- Perform end-to-end pass from onboarding through capture, integrations, timeline, search, review, and backup.
- Remove debug-only behavior, document known Android/API limitations, and prepare release configuration.
- Update README, ARCHITECTURE.md, and TASKS.md.

### Acceptance criteria

- A fresh install can complete the full MVP journey.
- App remains functional offline.
- No mandatory account or AI service exists.
- Known limitations are documented.

## T21 — App language selection

Status: complete

Requested (2026-10-12): let the user choose English, Persian or French as the app
language.

Implementation (2026-10-13): completed offline English/Persian/French bundles,
including plurals, validation, privacy, integration and accessibility text.
Settings saves the app language through native Android 13+ LocaleManager or a
committed preference on API 28–32; activity context wrapping applies formatting
and layout before creation. Built-in emotion and native step labels translate at
display time, preserving canonical identities and arbitrary user names/content.
Localized dates retain the existing Gregorian calendar and stored ISO values.

Validation: all 277 JVM tests pass, normal debug/test APKs build and all module
lint checks have zero errors. Five native cases pass on a temporary API 36.1
emulator, covering every language, primary navigation, recreation, date and journal
draft/tag/content retention, save/detail, plurals, accessibility and pickers.
Separate force-stop/relaunch probes verify language persistence independently of
device language, supported Persian default and unsupported German → English.
Screenshots verify Persian RTL, native calendar labels/digits, mixed-language text
and restored LTR. Settings remains operable at 130% text size; French navigation
labels wrap awkwardly, recorded for T19. Legacy API 28–32 native execution and
exhaustive TalkBack/device-size audits remain platform follow-ups. See docs/VALIDATION.md.

### Scope

- Add a language selector in Settings with English, فارسی (Persian) and Français (French).
- Save the selected app language and apply it across navigation, screens, dialogs,
  validation messages and accessibility labels.
- Provide English, Persian and French translations for app-owned text, including
  plural forms, and locale-aware formatting for displayed dates and numbers.
- Support right-to-left layout for Persian and left-to-right layout for English
  and French, including navigation icons and mixed-language content.
- Preserve saved records, user-written content, selected dates and unsaved drafts
  when switching languages; user-written content is not automatically translated.
- Before an explicit choice, use a supported device language, falling back to English.

### Acceptance criteria

- User can select each of the three languages in Settings and see app-owned text
  update throughout the app.
- The selection survives navigation, activity recreation and app restart,
  independently of the device language.
- Persian text and right-to-left navigation render correctly; switching to English
  or French restores left-to-right layout without clipped controls.
- Plurals, date/number formatting and accessibility labels match the selected language.
- Language changes preserve stored data, selected dates and in-progress capture drafts.
- Unsupported device languages use English before the user makes an explicit choice.

## Future backlog — do not implement during MVP

- Voice notes and transcription
- Goals and habit tracking
- Ask My Life conversational retrieval
- Semantic/vector search
- Monthly/yearly AI narratives
- Encrypted cloud/multi-device sync
- Automatic/coarse location and additional context integrations
