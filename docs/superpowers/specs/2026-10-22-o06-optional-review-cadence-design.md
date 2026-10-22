# O06 — Optional — defined cadence and active-day metrics specification

Date: 2026-10-22  
Status: Optional / deferred; requires a separate scope decision before implementation.  
Depends on: T32-B

## Intent and sources

Replace illustrative coaching claims with explicitly defined optional calculations over saved records and habit schedules.

References: Review “100% streak”, “4 active days”, cadence and consistency copy. Read the [shared reference brief](../../luma-redesign/README.md) and [task index](../../UI_REDESIGN_TASKS.md) with this specification. The brief resolves inconsistencies and provides common tokens; it is normative for every task.

## Global constraints

- Native Android, Kotlin, Compose Material 3 and Navigation Compose; keep minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- Offline operation; no account, cloud sync, AI service, telemetry, new permissions or schema migration in required visual work.
- System/Light/Dark selection and English/French/Persian support remain available offline.
- Preserve existing route IDs, callbacks, saved state, data semantics, loading/error/retry/undo and accessibility tags unless an optional task explicitly changes its own contract.
- Use actual saved data; never ship reference people, dates, percentages, measurements, quotes or charts as user records.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL and system animation scale.
- Optional tasks default to deferred and are not required for redesign acceptance. No optional feature is authorized for implementation by this planning document.

## Screen and behavior contract

- Call the habit metric “Scheduled habit-days completed”, never “streak”. Numerator: eligible non-skipped scheduled habit-days whose stored repetition count meets target; denominator: all non-skipped scheduled habit-days through the selected period end. Exclude upcoming and unscheduled days; no eligible denominator means unavailable, not 100%.
- Define active reflection days as distinct dates containing at least one manual mood/journal/memory record in the review zone. A promoted memory shares its event ID and counts once; imported activity/app usage and standalone habit logs do not create reflection days.
- Create pure domain review projection functions for the two metrics, expose their definitions in methodology and retain existing repetition totals. Aggregate daily cadence uses the same eligible-day rule; skipped/unscheduled/upcoming/unknown remain distinguishable.
- Do not add all-time streaks, motivational diagnoses or “on track”/“4 days running” claims. This option supplies truthful period metrics only; a future streak feature requires its own product rules.

## Implementation boundaries

- `app/src/main/java/com/luma/lifeos/ui/review/ReviewOverview.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/review/ReviewHabits.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/review/HabitWeekPresentation.kt` — existing integration surface.

Existing public/internal composable signatures remain compatible except additions explicitly named above. Existing repositories/view models own data and mutations; decorative components receive values and callbacks. New files named in the contract are proposed implementation files, not files already created by this documentation change. Consult the paired plan for test ownership.

### Additional interfaces and data files

Create `core/domain/src/main/kotlin/com/luma/lifeos/domain/review/ReviewCadence.kt` with:

```kotlin
data class ReviewCadence(val completed: Int, val eligible: Int)
fun reviewCadence(days: List<HabitDay>): ReviewCadence
fun activeReflectionDays(dates: ReviewDates, zone: ZoneId, records: List<ReviewRecord>): Int
```

The first function accepts the already-selected current-period days, de-duplicates `(habit.id, date)` and rejects conflicting duplicates. Eligible means schedule exists, lifecycle ACTIVE, weekday scheduled and `skipRemaining == false`. Completed means eligible and count >= schedule.target. Unknown/unavailable habit-day state never becomes a missed or successful day; if a repository observation is loading/error, suppress the metric until its actual rows are available. Show unavailable when eligible == 0. Display percentage rounded to nearest integer with numerator/denominator alongside, never a streak.

Add trailing `source: EventSource = EventSource.MANUAL` to ReviewRecord in `core/domain/src/main/kotlin/com/luma/lifeos/domain/review/WeeklyReview.kt`. Extend ReviewEnvelope in `core/data/src/main/kotlin/com/luma/lifeos/data/entity/ReviewSource.kt` with the existing event source, select `source` alongside `id, occurred_at` in LifeEventDao.reviewSources (`core/data/src/main/kotlin/com/luma/lifeos/data/dao/LifeEventDao.kt`), and map it in RoomWeeklyReviewRepository.snapshot. Imported rows must never silently use the compatibility default MANUAL. Add trailing `activeReflectionDays: Int = 0` to ReviewStatistics, computed during calculateWeeklyReview from the current/previous selected records. Active dates require source MANUAL and `(mood != null || journal || memory)`; count distinct in-range local dates. Never add source data to a database table for this projection.

Create `core/domain/src/test/kotlin/com/luma/lifeos/domain/review/ReviewCadenceTest.kt`; extend WeeklyReviewTest for integration of both periods and actual source mapping.

## Acceptance scenarios

1. Multiple repetitions of a single scheduled habit-day never inflate the numerator; skipped/future/unscheduled days and denominator zero are handled by the defined rules.
2. Active days de-duplicate entries by date in review zone, exclude imports and remain correct across midnight/timezone boundaries.

## Verification and completion

Use focused existing regression coverage plus visual/manual checks for the changed surfaces. Add tests for changed behavior and meaningful state/semantics risks; do not add tests that merely assert padding or duplicate token assignments. Required font/contrast/RTL/inset checks are acceptance work, not a reason to add fake data. Record PASS/FAIL/NOT RUN and evidence in the T33 ledger. No checks are claimed to have run in this specification.

[Implementation plan](../plans/2026-10-22-o06-optional-review-cadence.md)
