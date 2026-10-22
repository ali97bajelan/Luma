# O05 — Optional — saved-data Review charts specification

Date: 2026-10-22  
Status: Optional / deferred; requires a separate scope decision before implementation.  
Depends on: T32-B

## Intent and sources

Add small accessible charts backed by saved daily usage/activity data, with gaps instead of invented observations.

References: Review light/dark sparkline and daily usage examples. Read the [shared reference brief](../../luma-redesign/README.md) and [task index](../../UI_REDESIGN_TASKS.md) with this specification. The brief resolves inconsistencies and provides common tokens; it is normative for every task.

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

- Create a pure `ReviewChartPoint(date: LocalDate, value: Double?)` projection and a `ReviewSignalChart(points: List<ReviewChartPoint>, unitLabel: String, modifier: Modifier = Modifier)` component. Use one point per selected-period date, ascending by date. Null is unknown, not zero.
- For usage use existing saved daily report totals only when known; preserve COMPLETE/PARTIAL/UNAVAILABLE state in adjacent text. For activity, extend WeeklyReview with saved per-day totals grouped by type/unit/source; the current aggregate activity model cannot support a genuine sparkline.
- No interpolation over gaps, smoothing that implies intermediate samples, extrapolated future dates or mixed-unit/source sums. Display isolated points honestly and an absence label when no values exist.
- Keep exact totals, sample counts, source actions and a textual date/value alternative. A chart cannot be the sole way to inspect a metric. No charting/network dependency is required; use Compose Canvas.

## Implementation boundaries

- `app/src/main/java/com/luma/lifeos/ui/review/ReviewOverviewSignals.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/review/ReviewSignals.kt` — existing integration surface.

Existing public/internal composable signatures remain compatible except additions explicitly named above. Existing repositories/view models own data and mutations; decorative components receive values and callbacks. New files named in the contract are proposed implementation files, not files already created by this documentation change. Consult the paired plan for test ownership.

### Additional interfaces and data files

Create `app/src/main/java/com/luma/lifeos/ui/review/ReviewSignalChart.kt` for the chart model/component above and `ReviewChartProjection.kt` for:

```kotlin
internal fun reviewUsagePoints(dates: ReviewDates, usage: ReviewUsage?): List<ReviewChartPoint>
internal fun reviewActivityPoints(dates: ReviewDates, days: List<ReviewActivityDay>): List<ReviewChartPoint>
```

In `core/domain/src/main/kotlin/com/luma/lifeos/domain/review/WeeklyReview.kt`, add `data class ReviewActivityDay(val date: LocalDate, val total: BigDecimal, val samples: Int)` and trailing `days: List<ReviewActivityDay> = emptyList()` to ReviewActivity. Populate days from the same deduplicated selected activities used for aggregate totals, in the report zone, grouped by the existing type/unit/source key. No second query or independent observation is needed. Keep the exact BigDecimal values for labels/source reconciliation; convert to Double only for plot geometry. Absent dates yield null chart points, including future/unmeasured dates; date with an explicit zero sample yields zero. Usage points follow the current known-total rule: empty PARTIAL is unknown; other existing known totals retain their coverage explanation, including legacy UNAVAILABLE totals when already represented as known by the current model. Never relabel legacy coverage as complete.

Create `app/src/test/java/com/luma/lifeos/ui/review/ReviewChartProjectionTest.kt`; extend existing `core/domain/src/test/kotlin/com/luma/lifeos/domain/review/WeeklyReviewTest.kt` for activity-day/aggregate reconciliation.

## Acceptance scenarios

1. Missing dates remain gaps; recorded zero sits at baseline; fractional activity and period boundaries retain precision and grouping.
2. Chart/text values reconcile with source data and existing weekly aggregates; TalkBack can inspect date/value alternatives.

## Verification and completion

Use focused existing regression coverage plus visual/manual checks for the changed surfaces. Add tests for changed behavior and meaningful state/semantics risks; do not add tests that merely assert padding or duplicate token assignments. Required font/contrast/RTL/inset checks are acceptance work, not a reason to add fake data. Record PASS/FAIL/NOT RUN and evidence in the T33 ledger. No checks are claimed to have run in this specification.

[Implementation plan](../plans/2026-10-22-o05-optional-review-charts.md)
