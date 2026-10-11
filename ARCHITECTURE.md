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
cleanup. T10 owns private file copying and physical deletion; it must recheck references
before deleting a returned path. No file is deleted during a database transaction.
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
counts, not T14 mood/usage/activity metrics. T05 extends this feed with typed mood
details and reuses it for the initial daily Timeline; the full T11 timeline remains
separate. Quick Capture opens a scrollable sheet from every primary tab, with
mood capture enabled and journal/memory capture remaining in T06–T07.

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
records with media references require T10 file cleanup and currently reject deletion.

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
from a known zero. Photos will be selected through a system picker and copied
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
end-to-end capture test sources. Full app lint and device execution remain pending;
the current data-module lint and debug/test APK builds pass.

Persistence tooling references:
[Room releases and schema export](https://developer.android.com/jetpack/androidx/releases/room),
[KSP 2.3.5](https://github.com/google/ksp/releases/tag/2.3.5), and
[MigrationTestHelper](https://developer.android.com/reference/androidx/room/testing/MigrationTestHelper).
