# Luma architecture

## Product contract

The project definition v0.1 and ordered task document supplied by the user define
the product scope. Their readable extracts live in `docs/`. Native Android,
offline capture and browsing, local ownership, and optional integrations are core
constraints. No account, cloud sync, AI dependency, or telemetry is introduced.

## Stack and dependency direction

- Kotlin; JDK 17; Gradle Kotlin DSL with a checked-in Gradle wrapper.
- Android Gradle Plugin 9.0.1 with built-in Kotlin 2.2.10; Gradle 9.1.0.
- Jetpack Compose and Material 3; AndroidX Navigation Compose.
- Minimum Android API 26; compile/target API 36. `java.time` is available natively.
- Coroutines and Flow for asynchronous repository contracts.
- Room/SQLite and KSP in T03; WorkManager for retryable imports in T12/T13.
- JUnit for pure JVM tests; AndroidX Test and Compose testing for device checks.
- Explicit constructor injection initially; no dependency injection framework.

The AGP/Gradle/JDK combination follows the official compatibility matrix:
https://developer.android.com/build/releases/agp-9-0-0-release-notes
Compose compiler uses the matching Kotlin plugin:
https://developer.android.com/develop/ui/compose/compiler

This is a modular monolith, producing one APK:

```text
app                         Android entry point, navigation, UI, composition root
  -> core:domain            pure Kotlin entities, invariants, repository contracts
  -> core:data (T03)         Room, mappings, transactions, repository implementations
integration:usage (T12)      Android usage adapter
integration:health (T13)     Health Connect adapter
```

Package layout follows the same boundaries:

- `com.luma.lifeos`: Android entry point.
- `com.luma.lifeos.ui` and `ui.theme`: navigation, screens, and visual theme.
- `com.luma.lifeos.domain.model`, `.time`, `.repository`: domain values and contracts.
- Planned `com.luma.lifeos.data`: `.db`, `.dao`, `.entity`, `.mapping`, `.repository`.
- Planned `com.luma.lifeos.integration.usage` and `.health`: platform adapters.

The domain uses the Java library plugin and exports Coroutines as an API dependency
because its public repository contracts return `Flow`.

Only `app` wires implementations. `core:domain` depends on neither Android nor
Room. Feature packages under `app/ui/` grow in task order; separate feature Gradle
modules are unnecessary until build or ownership needs justify them. Integration
adapters depend inward on domain contracts. No microservices or backend.

## Planned database schema (T03)

Schema version 1 will use stable text UUIDs, UTC epoch-millisecond timestamps,
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
| app_usage_record | id PK; bucket_start/end; package_name; duration_ms; source; UNIQUE(source, package_name, bucket_start, bucket_end) |
| daily_summary | date + zone_id composite PK; derived metrics; algorithm_version; computed_at; freshness state |
| integration_state | source PK; status; last_sync_at; checkpoint |

Index events on `(occurred_at, id)` and `(type, occurred_at)`; source identities
are unique when present. Index every FK lookup and reverse link lookup. Event
deletion cascades typed details/links; deleting people/places/tags removes links
only. Memory promotion never copies journal content. Related capture/edit/delete
operations and import upserts are transactional. Physical media deletion occurs
after database success with orphan cleanup, avoiding lost references on failure.

Export Room schemas to version control. Add migration tests when version 2 is
introduced; never enable destructive fallback. A backup has a distinct format
version, validates before replacement, and includes only referenced local media.

## Domain rules and time

IDs are UUID values validated at boundaries. Life events have a type, origin,
occurrence instant, optional end instant, optional source reference, content,
and audit timestamps. End cannot precede occurrence; update cannot precede creation.
Source references identify retryable imported events; manual edits are supported
only for manual sources. Equal-time ordering uses the stable ID as a tie breaker.
Repositories expose Flow for observation and suspend operations for mutations.

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
