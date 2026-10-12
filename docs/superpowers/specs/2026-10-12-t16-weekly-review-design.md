# T16 — Weekly Review

Status: approved by the user; implemented with device validation pending.

## Intent and scope

Implement the next pending task in TASKS.md: a useful offline Weekly Review
that describes saved observations, compares weeks, and lets the user inspect
the source of each statistic. Include mood/emotion patterns, notable memories,
linked people/places, foreground app usage and recorded activity when available.
Preserve the current memory browsing and editing routes. No causal claims,
mandatory integrations, AI service, account, telemetry or network access.

The following defaults are proposals: ISO Monday–Sunday weeks, independent
Review date selection, and comparison of the current week so far with the same
elapsed calendar dates in the previous week. Completed weeks compare all seven
dates. These extend the existing Monday-based memory review.

## Approach and alternatives

Recommended: calculate a weekly report from indexed metric projections in one
Room read transaction. Add a pure domain calculator and WeeklyReviewRepository;
reuse the existing source navigation and usage calculation rules. This gives
consistent counts and source identities without loading private journal bodies
or an entire history. No schema migration or stored weekly cache is required.

Alternative: combine fourteen daily summary flows. This reuses daily metrics,
but does not supply emotion/catalog attribution or source navigation and may
temporarily compare different database snapshots.

Alternative: persist weekly summary caches. This adds invalidation and format
maintenance without evidence that a bounded two-week query needs caching.
Measure the recommended implementation before introducing a cache.

## Dates and comparisons

Review initially follows the current local week. Previous/next week and date
selection permit historical review, with a This week action. A selected week
survives recreation, tab changes and source detail navigation. Calendar and
foreground timezone changes recompute the selected dates in the current zone.
Future weeks cannot be selected.

Compute each civil date's bounds with ZoneId; do not assume 24-hour days.
For the current week, include Monday through today and label today as partial.
Compare with Monday through the corresponding weekday of the preceding week,
also labeling the difference as saved observations, not normalized coverage.
For completed weeks, compare seven dates with the preceding seven dates.
Do not treat a partial current day as a full day of measurements.

Counts may be zero when no records exist. Missing measurements are unavailable.
Show absolute changes only when both measurements exist; mood changes are in
scale points and durations in time units. Do not generate percentages against
a zero baseline or imply that missing integration records mean zero activity.

## Statistics and source traceability

- Entry, journal and memory counts: count distinct underlying event identities;
  promotions do not add an entry. APP_USAGE never contributes to entry counts.
- Mood, energy and stress: show sample-weighted averages and individual sample
  counts; never average daily averages with unequal sample counts. Present a
  seven-date mood breakdown and descriptive emotion frequencies by emotion ID.
  An emotion contributes once per linked check-in. No correlation narrative.
- Memories: show a bounded chronological preview list with source routes and
  a way to browse all matching memories. Preserve direct and promoted markers.
- People/places: show linked-event counts by catalog identity, counting each
  event once per person/place; names with distinct identities remain distinct.
  Open existing person/place details. Explain that these are recorded links,
  not measured social time or physical visits.
- Digital behavior: weekly total and per-app foreground duration using the
  existing Settings session-first calculation per civil date. Union same-app
  overlaps and clip sessions at midnight. Dates without sessions retain the
  existing aggregate-only fallback dated by native bucket starts; do not add
  session and aggregate totals for the same date. Preserve partial/unavailable
  history labels and measured zero. Exclude zero-use app rows. Show available
  date count and open the corresponding saved usage view/date for inspection.
- Activity: sum saved values by type, unit and source, preserving explicit zero
  and missing values. Show recorded date/sample counts. Do not sum unlike units
  or treat overlapping providers as independently additive step measurements.
  Existing Health Connect step totals already reflect native aggregation.

Every displayed metric exposes its dates and sample/record coverage. Source
actions browse a bounded list matching exactly the metric's input identities,
or a day-specific saved usage view for session-based usage. Event items open
existing content/detail routes. Week/date membership alone is insufficient for
an emotion or activity source list: constrain it to the contributing records.
Source lists page rather than retain arbitrarily large identity arrays in saved
state. Catalog deletion and source edits refresh reports and source lists.

## Architecture and screen

Add domain review models, a deterministic calculator and repository contract.
Room projections load only required values/relations across the two periods,
including independent native usage bounds and overlapping saved sessions.
Read report inputs consistently under a transaction on Dispatchers.IO. Observe
all contributing tables, including catalog names and usage session windows.
Avoid queries over unrelated historical rows and full journal/photo loading.
Wire the repository through LumaStorage, MainActivity and LifeOsApp.

Replace the Review tab's memory-only presentation with a lazy sectioned review:
date controls, overview/comparison, mood/emotions, memories, people/places,
usage and activity. Keep Quick Capture available. Missing optional sections
explain unavailable stored observations without requesting permission.
Use loading, empty, data and error states with Retry retaining the selected week.
Cancel obsolete week loads and reject stale responses. Keep text scalable and
give controls accessible labels. No generated narrative or AI toggle is added:
AI remains disabled by default and no provider is connected.

## Verification and completion

Domain tests cover deterministic ordering, unequal mood sample sizes, duplicate
identities, memory promotions, catalog identity, missing versus zero, comparison
periods, DST/skipped dates, midnight sessions and aggregate fallback. State tests
cover cancellation, retry and restored week selection.

Room instrumentation covers exact projection-to-source agreement, corrections,
deletion, relationship changes, usage history and transaction consistency.
Compose checks cover week navigation, mixed/missing sections, metric-to-source
navigation, memory actions and recreation. Measure repository loads with a
multi-year fixture concentrated both inside and outside the selected period;
target p95 below 300 ms after initialization, recording device/build details.

Run all existing JVM suites, debug and test APK compilation, applicable lint,
and available device checks. Do not reset an emulator or overwrite the user's
installed app for validation. Record unavailable runtime checks and existing
lint analyzer failures accurately; compiled device tests are not executed tests.

Update README.md, ARCHITECTURE.md, TASKS.md and docs/VALIDATION.md with actual
implementation and evidence. Mark T16 complete only after its acceptance
criteria are verified. Preserve untracked release/schema/test resources and
leave the existing T11/T13/T15 follow-ups assigned to their current tasks.
