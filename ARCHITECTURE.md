# Luma architecture

## Product contract

The project definition v0.1 and ordered task document supplied by the user define
the product scope. Their readable extracts live in `docs/`. Native Android,
offline capture and browsing, local ownership, and optional integrations are core
constraints. No account, cloud sync, AI dependency, or telemetry is introduced.

## Stack and dependency direction

- Kotlin; JDK 17; Gradle Kotlin DSL with a checked-in Gradle wrapper.
- Android Gradle Plugin 8.7.3 with the Kotlin Android/Compose 2.2.10 plugins;
  Gradle 9.1.0 (current user-supplied build configuration).
- Jetpack Compose and Material 3; AndroidX Navigation Compose.
- Minimum Android API 28; compile/target API 35. `java.time` is available natively.
- Coroutines and Flow for asynchronous repository contracts.
- Room 2.8.5/SQLite and KSP2 2.3.5 for persistence; WorkManager for retryable imports in T12/T13.
- JUnit for pure JVM tests; AndroidX Test and Compose testing for device checks.
- Explicit constructor injection initially; no dependency injection framework.

The foundation originally used AGP 9.0.1 and API 26/36. The current checkout's
user-supplied AGP 8.7.3 and API 28/35 settings are preserved. Build verification
and tool compatibility limitations are recorded in `docs/VALIDATION.md`.
Compose compiler uses the matching Kotlin plugin:
https://developer.android.com/develop/ui/compose/compiler

This is a modular monolith, producing one APK:

```text
app                         Android entry point, navigation, UI, composition root
  -> core:domain            pure Kotlin entities, invariants, repository contracts
  -> core:data              Room, mappings, transactions, repository implementations
integration:usage (T12)      Android usage adapter
integration:health (T13)     Health Connect adapter
```

Package layout follows the same boundaries:

- `com.luma.lifeos`: Android entry point.
- `com.luma.lifeos.ui` and `ui.theme`: navigation, screens, and visual theme.
- `com.luma.lifeos.domain.model`, `.time`, `.repository`: domain values and contracts.
- `com.luma.lifeos.data`: `.db`, `.dao`, `.entity`, `.mapping`, `.repository`.
- Planned `com.luma.lifeos.integration.usage` and `.health`: platform adapters.

The domain uses the Java library plugin and exports Coroutines as an API dependency
because its public repository contracts return `Flow`.

Only `app` wires implementations. `core:domain` depends on neither Android nor
Room. Feature packages under `app/ui/` grow in task order; separate feature Gradle
modules are unnecessary until build or ownership needs justify them. Integration
adapters depend inward on domain contracts. No microservices or backend.

## Database schema (T03)

Schema version 1 uses stable text UUIDs, UTC epoch-millisecond timestamps,
and explicit foreign keys. Day ranges are computed from the selected `ZoneId`,
not by adding 24 hours. Keep event occurrence separate from creation/update times.

| Table | Fields and constraints |
| --- | --- |
| life_event | id PK; type; occurred_at; ended_at nullable; source; source_ref nullable; title/note nullable; metadata_json; created_at; updated_at |
| mood_entry | id PK; life_event_id UNIQUE FK; mood_score 1–5; energy/stress nullable 1–5; note nullable |
| emotion / mood_emotion | id PK, unique name / composite mood_id + emotion_id PK |
| journal_entry | id PK; life_event_id UNIQUE FK; optional title; body |
| memory | id PK; life_event_id UNIQUE FK; optional importance and note; promotion references the existing event |
| person / event_person | id PK, name, note, photo_uri / event_id + person_id composite PK |
| place / event_place | id PK, name, note, optional latitude/longitude / event_id UNIQUE FK + place_id FK (one optional place per event initially) |
| media_attachment | id PK; life_event_id FK; type=image; app-private relative path; metadata_json |
| tag / event_tag | id PK, normalized unique name / event_id + tag_id composite PK |
| activity_record | id PK; life_event_id FK; activity_type; value; unit; source; external source identity |
| app_usage_record | id PK; life_event_id UNIQUE FK; bucket_start/end; package_name; duration_ms; source; UNIQUE(source, package_name, bucket_start, bucket_end) |
| daily_summary | date + zone_id composite PK; derived metrics; algorithm_version; computed_at; freshness state |
| integration_state | source PK; status; last_sync_at; checkpoint |

Index events on `(occurred_at, id)` and `(type, occurred_at)`; source identities
are unique when present. Index every FK lookup and reverse link lookup. Event
deletion cascades typed details/links; deleting people/places/tags removes links
only. Memory promotion never copies journal content. Related capture/edit/delete
operations and import upserts are transactional. Physical media deletion occurs
after database success with orphan cleanup, avoiding lost references on failure.

The Room Gradle plugin exports versioned schemas to `core/data/schemas` and supplies
them to the data module's instrumentation tests. Compiler-generated version 1
JSON is now present in the working tree following the user's build setup;
generation was not run during T03. Include it in version control before evolving
version 1. `SchemaMigrationTest` covers opening the exported v1 schema,
preserving journal content across the registered migration chain, and rejecting an
unsupported database version without erasing records. There is no migration before
the initial version; add a real upgrade to `LumaDatabase.MIGRATIONS` for version 2
and extend data-preservation cases. Never enable destructive fallback.

A backup has a distinct format
version, validates before replacement, and includes only referenced local media.

## Persistence boundaries

`LumaApplication` lazily owns one `LumaStorage` with an app-private `luma.db`.
Room opens it on demand through asynchronous queries; main-thread queries are not
enabled. `RoomLifeEventRepository` implements the existing domain contract, keeps
creation time and source identity stable, and reconciles repeated source imports to
the original event ID. Updates use SQL UPDATE rather than deleting/replacing the
parent row, so typed content survives envelope-only edits. Event types remain stable
after insertion; memory promotion attaches a marker to the original event.

`RoomEventStore.save` writes a **complete snapshot**: omitted typed content or links
are removed. It updates the parent, replaces details/links, and invalidates cached
summaries in one transaction. Invalid catalog references or uniqueness conflicts
roll back the whole write. Catalog objects must exist first. Child IDs are validated
as UUIDs; imported retries remap their parent links to the canonical event ID.
`saveImportBatch` also commits the integration checkpoint in that same transaction.
Feature-specific domain models and capture UI remain assigned to their later tasks;
the initial typed storage DTOs belong only to `core:data`.

`EventWithDetails` loads indexed relations in batched queries under `@Transaction`.
People/emotions/tags are reusable catalog rows. Each event has at most one place.
Tag uniqueness uses trimmed, locale-independent lowercase names. Activity source
references and usage package/bucket tuples have unique indexes. Numeric ranges,
coordinate pairs, and app-private media paths are validated at storage boundaries.
Enum values are persisted by name; renaming a value needs a migration.

Event writes and catalog edits conservatively mark all fresh daily summaries stale.
`RoomDerivedDataRepository.rebuildSummary` reads a day's event graph and stores the
caller-derived summary in the same transaction, preventing a concurrent event write
from making an outdated cache appear fresh. T14 supplies the actual algorithm and
can narrow invalidation when its full dependency set is known. No fabricated metrics
or permission-state assumptions are introduced.

Media operations here affect database references only. Aggregate saves/deletes return
removed relative paths after commit, and `referencedMediaPaths` supports later orphan
cleanup. T10 implements private file copying and physical deletion, rechecking current
references and durable draft leases before collecting managed files. No file is deleted during a database transaction.
Instant values are stored at millisecond precision. Range bounds round upward to
preserve half-open inclusion rules for queries with submillisecond endpoints.

## Domain rules and time

IDs are UUID values validated at boundaries. Life events have a type, origin,
occurrence instant, optional end instant, optional source reference, content,
and audit timestamps. End cannot precede occurrence; update cannot precede creation.
Source references identify retryable imported events; manual edits are supported
only for manual sources. Equal-time ordering uses the stable ID as a tie breaker.
Repositories expose Flow for observation and suspend operations for mutations.

## Application shell and Today (T04)

`MainActivity` supplies the application's `LifeEventRepository` to `LifeOsApp`.
The shell retains primary navigation and owns saveable ISO date selection and
Quick Capture sheet visibility. A null selection follows the system's current
day; an explicit selection survives navigation and recreation. Foreground date/
zone refresh runs immediately on resume and every 30 seconds to notice midnight
and timezone changes without background polling.

`TodayRoute` collects `observeToday` with Lifecycle Compose only while started.
The adapter queries `TimeRange.forDay`, emits loading/empty/data/error states,
preserves cancellation, and sorts by the domain comparator. Changing day, zone,
or retry resets collection state so previous-day rows cannot appear under a new
header. Room commits update the feed through the existing repository Flow.
The Material calendar represents UTC dates; convert its timestamps using UTC,
then query the selected date in the system zone. Its year range includes the
selected year even after navigation outside the default range.

Today displays occurrence time, type, optional envelope title/note and imported
source, with counts derived directly from available events. These are record
counts, not T14 mood/usage/activity metrics. T05 extended this feed with typed mood
details for the initial daily Timeline; T11 replaces that reuse with a dedicated
bounded Timeline route described below. Quick Capture opens a scrollable sheet from every primary tab, with
mood, journal and memory capture enabled.

## Mood check-ins (T05)

Domain `MoodInput`, `MoodDetails`, and `Emotion` validate numeric scales and keep
Android/Room types out of UI-facing contracts. `MoodRepository` owns catalog
preparation and check-in CRUD. `LifeEventRepository.observeRecords` provides typed
`LifeEventRecord` snapshots; its Room implementation observes indexed relations
under one transaction, keeping mood values and the envelope consistent. Existing
envelope-only repository implementations retain a default observation adapter.

`RoomMoodRepository` uses the existing v1 mood/emotion tables without a migration.
It initializes twelve reusable emotion names only when both the name and stable
seed ID are absent, preserving renamed/existing catalog rows. Create retries with
an identical caller-retained capture ID and payload are idempotent. Update/delete
require a complete manual mood; missing/imported records cannot be changed.
Updates target the mood row and emotion junctions, retaining child IDs, audit
creation time, metadata, memory markers and unrelated links. Note text is trimmed
and synchronized into the envelope. Writes invalidate derived summaries in the
same transaction. Delete cascades event references but preserves catalog objects;
T10 now permits media-bearing manual deletion and performs file cleanup after commit.

The navigation-scoped `MoodEditorViewModel` uses `SavedStateHandle` for draft
values, dirty state, initial time and a stable capture ID. It loads typed check-ins,
blocks duplicate saves, retains inputs after errors, and permits completion only
after the write succeeds. Restored create drafts reconcile the retained capture ID
against storage before unlocking the form; already committed captures become edits.
Save retries also reconcile an ambiguous committed result. Dirty state is available
before loading so cancellation still confirms discard if loading fails.

The editor requires one explicit score selection; emotions are optional multi-select
chips. Energy, stress and note are collapsible. Date/time controls use UTC calendar
picker dates and the current system zone. `moodInstant` rejects DST gaps and keeps
an existing valid offset during overlaps. Back/cancel confirm changed drafts;
delete confirms before mutation. Today/Timeline manual mood rows show score,
emotions and optional values and open editing; imported/incomplete rows stay read-only.
Saving selects the occurrence day on return so a backdated capture can be seen.
No runtime permission, network dependency or schema-version change is introduced.

## Journals and tags (T06)

Domain `JournalInput`, `JournalDetails`, `Tag` and `JournalRepository` expose
journal content without Android/Room dependencies. `LifeEventRecord` now carries
journal details and tags with its envelope in the same transactional observation.
Bodies must contain non-whitespace text; body formatting is retained verbatim.
Draft text is bounded at 50,000 body characters, 200 title characters, and
50 tags of up to 50 characters each. Oversized input is visibly rejected before
entering saved state, retaining the previous draft. Existing larger stored content
remains readable in detail; the editor rejects loading it into a saved-state draft.
Optional titles and tag names are trimmed. Tag identity uses locale-independent
lowercase normalization; case/whitespace variants reuse existing catalog IDs.

`RoomJournalRepository` uses the existing v1 tables. Tag creation, envelope,
journal content, junction changes and summary invalidation share one transaction.
Create IDs survive retries, and identical creates return the existing capture.
Edits update the existing journal row and tag links, preserving child/audit IDs,
metadata, memory markers, people, places and media. Missing, imported or incomplete
journals cannot be edited. Deleting an already absent record is idempotent;
existing imported/non-journal records retain deletion protection.
Event deletion removes links but preserves reusable catalog entries; T10 performs
physical cleanup for media-bearing manual journals after commit.

Quick Capture enables journals. The navigation-scoped journal editor retains
body/title/time/tags, dirty state and capture identity through `SavedStateHandle`.
Restored captures reconcile previously committed IDs before enabling editing.
Save retries reconcile ambiguous create results. Confirmed delete intent is saved
before mutation; a restored editor reconciles a committed deletion to completion.
Changing a draft after a delete error clears that intent, retaining the newer text.
Failed operations retain
inputs. Cancel/back confirm changed drafts; deletion confirms before writing.
The existing timezone-aware date/time controls also serve journal timestamps.
Today and the daily Timeline show typed body previews and tags; rows open a full
journal detail view, with editing available for manual records. Detail reloads
when its navigation entry starts again after edits; successful deletion returns
to the feed. Saving selects the journal occurrence day. No schema migration,
permission or external dependency is introduced. Full filters remain T15.

## Memories (T07)

`MemoryDetails` adds optional importance (1–5) and a marker note to the existing
`LifeEventRecord`. Domain `MemoryInput`, `MemoryMarkerInput` and `MemoryRepository`
keep persistence types out of UI contracts. Direct capture creates one manual
`MEMORY` envelope and one memory row, with optional title, required note and
occurrence time. A complete manual mood or journal can instead be promoted:
`canPromoteToMemory` excludes imported, incomplete and unsupported event types.
Promoted records keep their original type, ID, occurrence time and content.

`RoomMemoryRepository` uses v1 tables without a migration. Marker edits target
only the memory row and preserve its ID; unmarking removes only that row, leaving
the envelope, journal/mood, metadata, tags, people, place and attachments intact.
Existing journal/mood edits preserve markers. Direct memory edits retain audit
creation time and unrelated links; direct deletion is separate from unmarking and
permits attached media with T10 post-commit cleanup. Identical retained-ID creates
and repeated removals are idempotent. Writes and summary invalidation share one
transaction. `observeMemories` uses the indexed event range and a unique indexed
memory lookup; typed graph observations also react to marker-only changes.

The navigation-scoped memory editor retains draft title/note/importance/time,
capture identity and dirty state in `SavedStateHandle`. Draft limits are 200 title
characters and 10,000 note characters; oversized changes are visibly rejected
before entering saved state, preserving previous text. Promoted source content and
timestamp are read-only. Save retries reconcile ambiguous direct creates; marker
saves retain the original event ID. Confirmed deletion/unmark intent is saved before
mutation and reconciled on restoration if the resulting memory is already gone.
Changes after removal errors clear that pending intent and retain the newer draft.
Back/cancel confirm changed drafts, and remove/delete confirms the appropriate
operation. Saving selects the occurrence day on return.

Today/Timeline show memory status and optional importance alongside the original
event, plus promotion/edit actions for eligible records. Journal detail exposes the
same status and action. Review lists stored direct and promoted memories for the
current ISO calendar week (Monday through Sunday) in the system zone, showing
source previews and marker notes. Week boundaries use local start-of-day instants,
including DST, rather than fixed 24-hour durations. Its lifecycle-aware Flow has
loading/empty/error/retry states and stable ordering. T16 still owns statistics,
comparisons and a full weekly review; no derived metrics are invented here.

## People (T08)

Domain `Person`, `PersonInput`, `PersonHistory` and `PersonRepository` provide
catalog CRUD and linked history without Android/Room types. Names are required
and bounded at 200 characters; optional notes are bounded at 10,000. Stable UUIDs
allow distinct people with equal names. Identical retained-ID creates are
idempotent, missing edits fail, and confirmed deletions are safe to retry. Editing
preserves an existing photo URI; photo workflows remain outside T08.

`RoomPersonRepository` reuses v1 `person`/`event_person` tables. Catalog mutations
and summary invalidation share a transaction. Foreign-key cascade removes a
deleted person's links, preserving all life events, typed content and other links.
`PersonWithEvents` loads a person and complete linked graphs in one transaction;
history uses `LifeEvent.chronologicalOrder`. Generated Room observations include
catalog, junction and typed-content tables, so renames, source edits, markers,
link changes and deletion refresh the history. Original entries open their
existing detail/editor routes. Missing people have an explicit detail state.

`LifeEventRecord` includes linked people in the same committed graph. Mood,
journal, direct-memory and marker inputs accept nullable `personIds`: null
preserves links for existing callers; an explicit empty set clears them. Supplied
IDs replace only people links inside the same content/envelope write transaction.
A missing-person foreign key rolls back the entire capture, including tags and
summary changes. Promotion keeps the original event ID and source content;
explicit selections change its shared person links, while unmarking preserves them.

People has a primary tab with list, create, edit and detail. Saved person editors
retain name/note, dirty state and capture identity, recover ambiguous committed
creates and confirmed deletes, and keep drafts after errors. Capture editors
retain up to 100 selected UUIDs through `SavedStateHandle`; the shared picker
observes the local catalog and can open person creation while keeping the parent
editor on the back stack. On return, the new person is available for selection.
Deleted selections remain visible and removable rather than silently dropped;
failed writes retain the draft. Today/Timeline, journal detail and memory Review
show attached names. No schema migration, contacts import, permission, network
or new dependency is introduced. Full searching/filtering remains T15.

## Manual Places (T09)

`Place`, `PlaceInput`, `PlaceSelection` and `PlaceRepository` expose the local
catalog without Android/Room types. Names are required and bounded at 200
characters; optional notes at 10,000. UUID identities allow equal names, and
identical retained-ID creates are idempotent. Existing optional paired coordinates
are validated and preserved on name/note edits; detail can display them. T09 adds
no coordinate acquisition, map, contacts/location integration or tracking.

`RoomPlaceRepository` reuses the v1 `place`/`event_place` tables. Name/note edits
and deletion invalidate summaries in the same transaction. Deletion cascades only
the place links, keeping envelopes, typed content, people, tags, markers and other
relations intact. Place detail observes its catalog row live, with distinct
loading/missing/error states and retry; catalog lists have loading/empty/error
states. Schema version and dependencies remain unchanged.

`LifeEventRecord` carries at most one `Place` from its committed graph. Capture
inputs append nullable `placeSelection`: null preserves any existing link;
`PlaceSelection(null)` clears it; a selected UUID replaces it. Mood, journal,
standalone-memory and promoted-memory writes replace the place link inside their
existing content/envelope/people/tag/cache transaction. Missing place references
roll back the entire write. Promotion modifies shared links on the original event;
unmarking retains its place. Identical create retries also compare explicitly
supplied place IDs.

Places is reachable through Settings and each capture picker. The saved place
editor follows the existing dirty-draft, retained-ID and committed-result recovery
pattern, including confirmed deletion intent and bounded input. Capture editors
retain one nullable UUID and submit an explicit selection. Creating a place keeps
the parent editor on the back stack; the new row appears for explicit selection
on return. Missing selections remain visible/removable, and failed saves retain
the draft. Today/Timeline, journal detail, memory Review and person history show
linked names. Core capture remains optional-place and permission-free.

## Photo attachments (T10)

`PhotoAttachment` carries a UUID, validated relative path and optional
`PhotoMetadata` (actual MIME type, original byte count and dimensions).
`LifeEventRecord.photos` maps the existing v1 `media_attachment` relation.
Journal/direct-memory/marker inputs append nullable photo snapshots: null keeps
existing references, an explicit empty list clears them, and a supplied list
replaces only attachment rows in the existing content/link/cache transaction.
Explicit photo identities and paths participate in create retry matching.
Existing attachment metadata JSON is preserved verbatim on updates, including
unknown legacy fields. No migration is needed. Promoted memories share photos
on the original event; unmarking keeps them.

The application-owned `PhotoRepository` copies a selected content URI into
`filesDir/photos/<UUID>.img` before references can be saved. `ManagedPhotoFiles`
writes a durable owner lease before opening the source, bounds the copy at
25 MiB, syncs a temporary file and publishes it atomically. `AndroidPhotoCodec`
validates a decoded image, rejects sources over 40 million pixels, handles image
orientation through `ImageDecoder`, and produces previews with a maximum edge
of 640 pixels. Original bytes remain local. Copying, decoding and cleanup run
on an IO dispatcher. A repository mutex serializes imports/leases/previews and
cleanup reference queries. Writers of managed media must acquire a draft lease
before committing references, including future import/restore adapters.

Drafts retain at most 20 images. `PhotoDraftSession` stores only paths/metadata
and a stable pending source/UUID in `SavedStateHandle`, never image bytes.
Restoring an editor retains both selected and pending paths before allowing
cleanup. A completed copy is recovered without reopening the source grant.
Interrupted copies can retry while source access remains available; otherwise
the user can select a photo again. Failed imports/writes keep the draft and
previous selections. Photo changes clear uncertain removal intents just like
text changes, so restoring after an ambiguous unmark cannot discard newer
photos. Successful saves/deletes and explicit discard release the editor lease;
a cleanup failure cannot report a committed save as failed.

Lease files under `photos/.drafts` survive process death and protect concurrent
editors. Physical collection happens outside Room transactions, rechecks current
committed references and all leases, and deletes only canonical managed UUID
files. Startup and capture completion retry collection. Abandoned leases remain
protected until explicit release; this favors restorable drafts but can retain
unused private files. Future draft lifecycle/storage controls can reconcile them.
Arbitrary legacy relative paths display as unavailable and are never resolved or
deleted by the managed-file engine; paths and symlinks cannot escape its root.

Journal and memory forms use AndroidX `PickVisualMedia` with image-only selection
and its older-device document-picker fallback, repeated Add photo, previews,
retry and removal. No broad media permission or persistent provider grant is
required for a finished private copy. Journal detail shows read-only photos;
Today/Timeline, Review and person history show photo counts. Missing, corrupt or
unsupported images show an unavailable state with retry while keeping the entry
and its removable reference. Previews reload when their screen starts again.
No network/upload path or automatic backup is introduced. Picker reference:
[Android photo picker](https://developer.android.com/training/data-storage/shared/photo-picker).

## Complete Life Timeline (T11)

`TimelineRepository` is an Android-free contract for committed day pages, change
notifications and individual full records. `RoomTimelineRepository` uses half-open
day bounds and the existing `(occurred_at, id)` index, with `LIMIT/OFFSET` in oldest
first order. Each page's envelope, typed relations and total count share a Room
transaction. Equal timestamps use the same UUID ordering as the domain comparator.
No schema or build-tool version changes are required.

`TimelineViewModel` initially loads 150 records, appends/prepends 50 at a time and
retains at most 300. Stable event keys in `LazyColumn` retain visible rows when
offscreen pages leave the window. Near-end scrolling loads later pages; earlier
and later controls also allow explicit navigation. The selected date/zone and
absolute window offset/size survive recreation. Changing date resets both saved
window values immediately, cancels old reads and resets scrolling. Sequence guards
reject late results. Failed loads preserve existing rows and expose retry.
Deleting records beyond the old anchor recenters the window; an empty day resets
its offset. DST days use actual local midnights, and skipped civil days are empty.

Observation registers before the first query. Room invalidations include all
envelope, typed, link and referenced catalog tables, so changes to body, signals,
people, places, tags and photos refresh the current window. If a page observes a
new count before invalidation arrives, it reloads one coherent window rather than
combining mismatched counts. Observation stops with the screen and refreshes on
foreground return. Generic detail uses lifecycle collection and distinguishes
loading, missing/deleted records and retryable failure.

`LifeEventRecord` carries actual `ActivityDetails` and `AppUsageDetails` alongside
manual content and existing links. Shared cards and date controls serve Today and
Timeline. Signal cards show stored activity type/value/unit or package/duration;
full detail includes bucket bounds, source, local timestamps, content, links,
memory status and photos. Absent typed values are shown as unavailable. Only
complete editable manual moods, journals and direct memories open their existing
content routes. Imported/incomplete records open read-only detail, including from
Today and person history. Promotion remains limited to eligible manual records.
Health/usage acquisition, app labeling and aggregation remain T12–T14; no runtime
sample records, new permissions or dependencies are introduced.

## Implementation phases

1. T01–T03: runnable project, tested domain contracts, relational persistence.
2. T04–T10: Today and capture, moods, journals, memories, people, manual places,
   durable private photo attachments. Each task adds a tested usable vertical slice.
3. T11–T14: complete timeline, opt-in usage/health adapters, reproducible summaries.
4. T15–T18: local search, deterministic weekly review, backup/restore, privacy settings.
5. T19–T20: migration/reliability/performance/accessibility checks and release readiness.

See `TASKS.md` for acceptance criteria and honest completion status. No future
backlog features are implemented during these phases.

## Privacy and platform limitations

No network permission or analytics SDK. Android system backup is disabled so
sensitive records cannot enter automatic remote backups. Portable backups in T17
require a user action through the system document picker. Optional integrations
will explain scope before consent and reflect permission revocation. Usage access
is special system access, not an ordinary runtime permission. Health Connect
availability and supported records vary by OS/provider; unavailable is distinct
from a known zero. Photos are selected through a system picker and copied
into private storage, without requiring broad media access. Core flows require
neither location nor health/usage permission. AI remains absent until explicitly
authorized; deterministic reviews remain sufficient.

## Validation

Run JVM domain tests independently, Android lint and debug/unit builds in CI,
and instrumentation tests on an API 36 emulator. Instrumentation verifies that
the shell launches and primary routes are reachable; subsequent phases extend
coverage to persistence and critical flows. Device-only checks must be marked
pending when no emulator or physical device is available.

T03 adds persistence CRUD, indexed relations, Flow updates, transactional rollback,
source retry identity, atomic checkpoints, cache invalidation, deletion cascades,
and schema baseline test sources. No code, Gradle, test, or emulator commands were
run for T03, as requested; all acceptance checks remain pending user validation.

During T04, the current debug app and both instrumentation APKs compile, all 19
JVM tests pass, and data-module lint reports no issues. App lint crashes inside
bundled Lifecycle/Compose detectors with `IncompatibleClassChangeError` against
the current AGP lint runtime. Full app lint and device tests remain pending.
See `docs/VALIDATION.md` for exact results and commands.

T05 increases verified JVM coverage to 33 tests, with compiled Room mood and
end-to-end capture test sources. T06 increases this to 47 JVM tests and compiles
journal persistence and capture/detail/edit/delete device sources. T07 brings
verified coverage to 63 JVM tests, with compiled memory persistence and capture/
promotion/review/removal device sources. T08 increases this to 75 JVM tests and
compiles People CRUD/link/history/live-update persistence cases and two capture/
history/removal/deletion device flows. T09 brings coverage to 91 JVM tests with
compiled place persistence and capture/reuse/rename/clear/deletion device sources.
T10 brings coverage to 110 JVM tests (29 domain, 74 app and 7 data file-engine
cases). Photo Room and Compose device sources compile, including disk database/
file reopening, rollback, draft protection, removal and unavailable-media cases.
T11 brings coverage to 126 JVM tests (32 domain, 87 app, 7 data), including bounded
windows, restoration/startup ordering, deletion, cancellation, lifecycle refresh
and detail-state regressions. Five Room cases and two Compose timeline flows
compile; deterministic indexed mixed pages, navigation and long-day scrolling
still require device execution. Smooth scrolling and multi-day timing are pending
acceptance checks, not inferred from compilation.
Full app lint and device execution remain pending;
the current data-module lint and debug/test APK builds pass.

Persistence tooling references:
[Room releases and schema export](https://developer.android.com/jetpack/androidx/releases/room),
[KSP 2.3.5](https://github.com/google/ksp/releases/tag/2.3.5), and
[MigrationTestHelper](https://developer.android.com/reference/androidx/room/testing/MigrationTestHelper).
