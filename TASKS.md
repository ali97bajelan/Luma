# Ordered implementation tasks

Based on the supplied Word task document and product definition v0.1.
Original source documents remain unchanged; readable extracts are in `docs/`.

Status: pending, in progress, implemented (validation pending), or complete.
A task is complete only after its acceptance criteria have been verified.


## T01 — Bootstrap the Android project

Status: implemented (validation pending)

Validation: foundation debug APK and instrumentation APK built; Android lint reported zero issues; 13 domain tests passed before T03. Device launch/navigation checks remain pending and will be run by the user; no further emulator work is requested.


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

Status: implemented (validation pending)

Implementation: added the `core:data` Android library, Room version 1 schema for all
13 records and four link tables, indexed relations, DAOs, domain envelope mapping,
transactional event/catalog/cache repositories, source-identity reconciliation,
atomic import/checkpoint writes, and lazy application storage wiring. Persistence
and migration baseline instrumentation test sources are included.

Validation: not run, at the user's request. Gradle sync/build, schema JSON generation,
CRUD/relation/rollback/import tests, and schema reopening checks are pending user
validation. Commit Room's generated version 1 schema JSON after the first build.

Follow-up during T04: the user-generated version 1 JSON is present; the data
library and its instrumentation APK compile and data lint reports no issues.
Persistence and migration tests still require device execution.


### Scope

- Add Room/SQLite schema for LifeEvent, MoodEntry, Emotion, JournalEntry, Memory, Person, Place, MediaAttachment, Tag, ActivityRecord, AppUsageRecord, DailySummary, and IntegrationState.
- Implement relations, indexes, DAOs/repositories, migrations baseline, and transaction boundaries.

### Acceptance criteria

- CRUD tests pass.
- Relations can be loaded efficiently.
- Schema versioning and migration testing exist.

## T04 — Build application shell and Today screen

Status: implemented (validation pending)

Implementation: Room-backed Today feed with current/selected-day navigation,
calendar picker, deterministic rows, event counts by type, distinct loading/empty/
error states with retry, saved selection, and foreground day/timezone refresh.
Quick Capture is a persistent action on every primary tab and opens an explanatory
sheet; capture forms remain assigned to T05–T07. Core browsing requires no permissions.

Validation: 19 JVM tests passed (13 domain + 6 daily-state tests). Debug and test
APK verification and lint results are recorded in `docs/VALIDATION.md`. Device
navigation, rendering, picker, and Room/live-update tests remain pending user
execution; no emulator was launched. Full app lint is pending because bundled
Lifecycle/Compose detectors crash against the current AGP lint runtime.


### Scope

- Create primary navigation and Today screen.
- Show empty/loading/data states and daily event summary.
- Add persistent Quick Capture entry point.

### Acceptance criteria

- App remains useful with zero permissions.
- Today screen renders records for selected/current day.
- Quick Capture is reachable in one obvious action.

## T05 — Mood and emotion check-ins

Status: implemented (validation pending)

Implementation: permission-free local mood capture/edit/delete with an explicit
1–5 score, reusable multi-select emotions, optional energy/stress/note, and date/
time controls. Saved drafts survive recreation and loading errors; stable capture
IDs reconcile committed writes after process death. Edits preserve unrelated links
and metadata. Typed mood rows update on Today and the initial daily Timeline.

Validation: 33 JVM tests pass (16 domain + 17 app), debug and instrumentation APKs
build, and data lint reports no issues. The existing app lint detector compatibility
failure remains. Five new Room test sources and a capture/recreation/edit/delete
device flow are included; device execution and acceptance timing remain pending
user validation. No emulator was launched. Details are in `docs/VALIDATION.md`.


### Scope

- Implement mood score, multiple emotions, optional energy, stress, note, and timestamp.
- Provide fast create/edit/delete flows.
- Render mood events on Today and Timeline.

### Acceptance criteria

- A check-in can be recorded in seconds.
- Multiple emotions are supported.
- Edits persist and timeline updates correctly.

## T06 — Journal and tags

Status: implemented (validation pending)

Implementation: offline journal create/edit/delete with optional title, required
body, timestamp and reusable tags. Case/whitespace variants share tag identities;
body formatting survives edits. Drafts and capture IDs survive recreation,
failed saves retain inputs, and navigation confirms discard. Visible size limits
protect saved drafts; confirmed deletes reconcile committed results after restoration.
Transactional edits
preserve other links and metadata. Today/Timeline previews open a full detail view.

Validation: JVM, APK and lint results are recorded in `docs/VALIDATION.md`.
Room and capture/recreation/detail/edit/delete device test sources compile;
device execution remains pending user validation. No emulator was launched.


### Scope

- Implement journal create/edit/delete with optional title, body, timestamp, and tags.
- Support reusable tags and text validation.
- Render entries on timeline and detail view.

### Acceptance criteria

- Journal works fully offline.
- Tags can be reused and filtered later.
- No data loss on edit/navigation.

## T07 — Memories

Status: implemented (validation pending)

Implementation: direct offline memory capture/edit/delete with optional title,
importance and timestamp; complete manual moods and journals support reversible
memory markers. Marker changes preserve source content, identity, metadata and
links. Bounded saved drafts survive recreation and errors; ambiguous saves and
confirmed removals reconcile committed results. Today/Timeline and journal detail
show memory status; Review lists the current ISO calendar week’s stored memories.
Full review statistics remain T16.

Validation: 63 JVM tests pass (22 domain + 41 app), debug and instrumentation APKs
build, and data lint reports no issues. Five Room test cases and two capture/
promotion/recreation/review/removal device flows compile; device execution remains
pending user validation. No emulator was launched. See `docs/VALIDATION.md` for
commands and the app lint analyzer compatibility limitation.


### Scope

- Allow direct creation of a memory.
- Allow an eligible existing event/journal entry to be promoted/linked as a Memory without duplicating the underlying content.
- Expose notable memories in timeline and reviews.

### Acceptance criteria

- Promotion is reversible where appropriate.
- Underlying event remains intact.
- Memory status is clearly visible.

## T08 — People

Status: implemented (validation pending)

Implementation: offline people list/create/edit/delete and detail with chronological
linked event history. Mood, journal, standalone-memory and promoted-memory forms
support multiple saved person selections and creating a person without losing the
parent draft. Typed feeds, journal detail and memory Review show linked names.
Catalog identities remain distinct even for equal names; deleting a person removes
only their links and retains all event content. Existing capture calls preserve
links unless they provide an explicit replacement selection. Writes remain atomic.

Validation: 75 JVM tests pass (24 domain + 51 app), debug and instrumentation APKs
build, and data lint reports no issues. Five Room cases and two People capture/
history/removal/deletion device flows compile. Device execution remains pending
user validation; no emulator was launched. Full app lint still fails inside the
existing bundled analyzers. See `docs/VALIDATION.md`.


### Scope

- Implement Person list/create/edit/detail.
- Allow supported events, journals, and memories to reference multiple people.
- Show a person's linked life events chronologically.

### Acceptance criteria

- People can be attached during capture/edit.
- Deleting a person does not delete life events.
- Person detail shows correct linked history.

## T09 — Manual Places

Status: implemented (validation pending)

Implementation: permission-free local place list/create/edit/delete/detail,
reachable from Settings and capture forms. Mood, journal, standalone-memory and
promoted-memory editors retain one optional place selection and can create a place
without losing the parent draft. Existing coordinates survive name/note edits.
Place links save atomically with entry content; explicit clearing removes only the
place link, and deleting a place preserves all life events. Typed browsing shows
linked place names. No location tracking or permission is added.

Validation: 91 JVM tests pass (27 domain + 64 app), debug and instrumentation APKs
build, and data lint reports no issues. Five Room cases and two place capture/
reuse/rename/clear/deletion flows compile; device execution remains pending user
validation. No emulator was launched. Full app lint retains its bundled analyzer
compatibility failure. See `docs/VALIDATION.md`.


### Scope

- Implement Place list/create/edit/detail.
- Allow manual selection/attachment of a place to supported events.
- Do not implement continuous location tracking.

### Acceptance criteria

- Place is optional.
- Existing places can be reused.
- No location permission is required for core MVP.

## T10 — Photo attachments

Status: implemented (validation pending)

Implementation: journal, direct-memory and promoted-memory forms support local
image selection, bounded previews, retry, removal and unavailable-photo states.
Images copy into app-private storage before transactional reference/metadata
writes. Stable pending identities and durable draft leases protect restored,
unsaved and concurrent-editor selections; cleanup checks current references and
leases only after database success. Promotion shares source photos and unmarking
preserves them. Journal detail renders photos; browsing shows attachment counts.
No broad media permission, network upload, automatic backup or schema migration
is added.

Validation: 110 JVM tests pass (29 domain + 74 app + 7 data), debug and both
instrumentation APKs build, and data lint reports no issues. Seven photo Room
cases and two Compose capture/removal/unavailable-media flows compile; device
execution, native picker behavior and restart acceptance remain pending user
validation. Independent review found no remaining critical/important defects.
Full app lint retains the bundled analyzer compatibility failure. No emulator was
launched. See `docs/VALIDATION.md` for evidence and private-file retention limits.


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

Validation: 126 JVM tests pass (32 domain + 87 app + 7 data), debug and both
instrumentation APKs build, and data lint reports no issues. Independent review
findings have regression coverage and fixes for startup restoration, interrupted
date selection and empty-window controls. Five Room cases and two Compose flows
compile. Device execution, calendar/visual acceptance, multi-day timing and smooth
large-fixture scrolling remain pending user validation; no emulator was launched.
Full app lint retains the bundled analyzer compatibility failure. See
`docs/VALIDATION.md` for evidence and commands.


### Scope

- Build day-based chronological timeline with date/calendar navigation.
- Merge manual events and imported signals into a coherent ordered feed.
- Add event detail/edit routes and appropriate type-specific rendering.

### Acceptance criteria

- Ordering is deterministic.
- Multi-day navigation is fast.
- Large sample datasets scroll smoothly.

## T12 — Screen-time integration

Status: pending


### Scope

- Integrate Android app-usage/screen-time APIs behind an adapter.
- Implement permission/status UX, incremental/idempotent imports, package/app labeling, and daily aggregation.
- Gracefully handle unsupported/revoked permission states.

### Acceptance criteria

- No fabricated usage data.
- Repeated imports do not duplicate records.
- User can use the rest of the app without permission.

## T13 — Health/activity integration

Status: pending


### Scope

- Integrate supported Android health APIs, preferably Health Connect, behind an adapter.
- Start with steps; add sleep/workouts only when API/data availability is reliable.
- Implement permission/status and idempotent sync.

### Acceptance criteria

- At least one useful activity signal imports correctly on supported devices.
- Duplicate imports are prevented.
- Unsupported data types do not break the app.

## T14 — Daily summaries

Status: pending


### Scope

- Compute reproducible daily metrics from raw/manual records.
- Include useful mood, journal/memory, screen-time, and activity summaries when available.
- Cache only derived values that can be rebuilt.

### Acceptance criteria

- Summary matches source records.
- Recalculation is deterministic.
- Missing sources are represented as unavailable, not zero unless zero is known.

## T15 — Search and filters

Status: pending


### Scope

- Implement local text search and filters for date, event type, tags, mood/emotion, person, and place.
- Design query/index strategy for multi-year data.

### Acceptance criteria

- Combined filters work.
- Results link to source entries.
- Search remains responsive on a realistic large fixture dataset.

## T16 — Weekly Review

Status: pending


### Scope

- Generate deterministic weekly statistics and comparisons with the previous period.
- Surface mood/emotion patterns, notable memories, people/places, digital behavior, and activity when data exists.
- Keep AI narrative optional and disabled by default.

### Acceptance criteria

- Review is useful without AI.
- Every statistic can be traced to stored data.
- No causal claims are generated from correlations.

## T17 — Backup, export, and restore

Status: pending


### Scope

- Define a versioned portable backup format.
- Export database content and referenced photos safely.
- Implement restore validation, conflict/replacement behavior, and migration compatibility.

### Acceptance criteria

- Round-trip backup/restore preserves supported data.
- Invalid backups fail safely.
- Backup format contains an explicit version.

## T18 — Privacy, permissions, and settings

Status: pending


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


### Scope

- Perform end-to-end pass from onboarding through capture, integrations, timeline, search, review, and backup.
- Remove debug-only behavior, document known Android/API limitations, and prepare release configuration.
- Update README, ARCHITECTURE.md, and TASKS.md.

### Acceptance criteria

- A fresh install can complete the full MVP journey.
- App remains functional offline.
- No mandatory account or AI service exists.
- Known limitations are documented.

## Future backlog — do not implement during MVP

- Voice notes and transcription
- Goals and habit tracking
- Ask My Life conversational retrieval
- Semantic/vector search
- Monthly/yearly AI narratives
- Encrypted cloud/multi-device sync
- Automatic/coarse location and additional context integrations
