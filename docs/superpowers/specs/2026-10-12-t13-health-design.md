# T13 — Optional Health Connect steps

Implement the next pending task in TASKS.md. Core capture and browsing remain
permission-free. Reuse the existing ActivityRecord/IntegrationState schema and
typed Timeline/Today/detail/summary rendering; keep database version 1 and API 28/35.

## Acquisition and identity

Use AndroidX Health Connect 1.1.0-beta01 behind `integration:health` and pure
domain contracts. Request READ_STEPS only, after an explanation in Settings. Published stable/RC/beta02 AARs require API 36/AGP 8.9.1; beta01 is the newest inspected compatible SDK.
Use `aggregate(COUNT_TOTAL)` without an origin filter: Health Connect handles
overlapping sources. No write, background-read, history-read, network, sleep or
workout permissions. Foreground manual imports refresh seven calendar dates,
including today through the query time. Respect actual DST day boundaries.

Save the import ZoneId on first enable and retain it across disable/re-enable.
Each day has a source identity `health:steps:daily:<zone>:<date>` independent of
count and partial end time. A timezone change must not create overlapping sets of
step totals. Settings shows the fixed import zone. Timeline dates and existing
summaries continue using event occurrence in the selected display zone; totals
are not repartitioned into a new zone.

## Sync and persistence

Serialize imports and access refreshes; opting out must not wait behind a provider request and cancels the active query before further aggregates. Recheck
permission and enabled state after queries, and check enabled/zone again inside
the Room transaction. Commit all seven observations and last-success checkpoint
atomically. Preserve event/child IDs, creation audit, metadata and unrelated links
on updates; invalidate derived summaries. A null aggregate means unavailable
data, not zero. A successful null observation removes this adapter's previously
saved daily event, reflecting source deletion; a provider error or revocation
must retain all history/checkpoints. Explicit zero remains a known zero.
Refresh the full seven-day window on every import so corrections/deletions inside
it reconcile; older saved history remains a snapshot until future broader sync.

## User experience

Settings adds an optional Health Connect card: durable opt-in, status, last sync,
permission request, provider install/update, manage access, refresh/retry and import.
Disabling stops Luma reads and keeps saved history; revocation is managed through
Health Connect. Explain local retention and seven-day/calendar-zone semantics.
A dedicated exported rationale activity and Android 14 permission-usage alias
show the same on-device privacy explanation. Foreground resume refreshes status.
Unsupported/provider-update-required/denied/no-data/error states keep core routes
usable. No fake health fixtures ship with the application.

## Validation

JVM tests cover daily/DST bounds, stable identity, null/zero, seven-day refresh,
permission checks before/after, opt-out races, errors, cancellation and UI state.
Adapter tests use a fake platform; native client wiring is compiled against the
SDK. Room instrumentation sources cover retry updates, rollback, stable identity,
links/metadata, deletion/zero, disabled writes and state restoration. Compose
sources cover Settings and optional access. Run JVM suites, APK/instrumentation
compilation and data/adapter lint; record real results and device checks pending.
Do not launch an emulator (the task history leaves device validation to the user).

References: [read/aggregate](https://developer.android.com/health-and-fitness/health-connect/read-data),
[permission/rationale setup](https://developer.android.com/health-and-fitness/health-connect/get-started),
[stable release](https://developer.android.com/jetpack/androidx/releases/health-connect).
