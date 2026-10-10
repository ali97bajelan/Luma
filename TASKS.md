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


### Scope

- Add Room/SQLite schema for LifeEvent, MoodEntry, Emotion, JournalEntry, Memory, Person, Place, MediaAttachment, Tag, ActivityRecord, AppUsageRecord, DailySummary, and IntegrationState.
- Implement relations, indexes, DAOs/repositories, migrations baseline, and transaction boundaries.

### Acceptance criteria

- CRUD tests pass.
- Relations can be loaded efficiently.
- Schema versioning and migration testing exist.

## T04 — Build application shell and Today screen

Status: pending


### Scope

- Create primary navigation and Today screen.
- Show empty/loading/data states and daily event summary.
- Add persistent Quick Capture entry point.

### Acceptance criteria

- App remains useful with zero permissions.
- Today screen renders records for selected/current day.
- Quick Capture is reachable in one obvious action.

## T05 — Mood and emotion check-ins

Status: pending


### Scope

- Implement mood score, multiple emotions, optional energy, stress, note, and timestamp.
- Provide fast create/edit/delete flows.
- Render mood events on Today and Timeline.

### Acceptance criteria

- A check-in can be recorded in seconds.
- Multiple emotions are supported.
- Edits persist and timeline updates correctly.

## T06 — Journal and tags

Status: pending


### Scope

- Implement journal create/edit/delete with optional title, body, timestamp, and tags.
- Support reusable tags and text validation.
- Render entries on timeline and detail view.

### Acceptance criteria

- Journal works fully offline.
- Tags can be reused and filtered later.
- No data loss on edit/navigation.

## T07 — Memories

Status: pending


### Scope

- Allow direct creation of a memory.
- Allow an eligible existing event/journal entry to be promoted/linked as a Memory without duplicating the underlying content.
- Expose notable memories in timeline and reviews.

### Acceptance criteria

- Promotion is reversible where appropriate.
- Underlying event remains intact.
- Memory status is clearly visible.

## T08 — People

Status: pending


### Scope

- Implement Person list/create/edit/detail.
- Allow supported events, journals, and memories to reference multiple people.
- Show a person's linked life events chronologically.

### Acceptance criteria

- People can be attached during capture/edit.
- Deleting a person does not delete life events.
- Person detail shows correct linked history.

## T09 — Manual Places

Status: pending


### Scope

- Implement Place list/create/edit/detail.
- Allow manual selection/attachment of a place to supported events.
- Do not implement continuous location tracking.

### Acceptance criteria

- Place is optional.
- Existing places can be reused.
- No location permission is required for core MVP.

## T10 — Photo attachments

Status: pending


### Scope

- Allow locally stored image attachments on journal/memory events.
- Persist durable references and metadata safely.
- Handle missing/deleted media gracefully.

### Acceptance criteria

- Photo survives app restart.
- Removing an attachment does not corrupt the event.
- Sensitive media is never uploaded automatically.

## T11 — Complete Life Timeline

Status: pending


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
