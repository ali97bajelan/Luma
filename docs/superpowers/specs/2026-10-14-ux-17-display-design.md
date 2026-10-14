# UX 17: consistent duration precision and visible ordering

Status: design approved; implementation complete on working tree based on HEAD `b52b9a7`; manual acceptance remains pending. Related: [handoff](../../UX_DEVELOPMENT_READY_2026-10-14.md), [plan](../plans/2026-10-14-ux-17-display.md), [acceptance](../../UX_ACCEPTANCE_2026-10-14.md).

## Intent and scope

Related browsing surfaces should present the same duration predictably and explain their chronological order. Keep raw saved values, usage union/clipping, coverage, thresholds, query ordering and pagination unchanged. This is presentation work, not a correction to recorded totals.

## Current behavior and approaches

Today and event cards call `appUsageDuration`: fractional seconds below a minute, hours/minutes above. `reviewDuration` shows hours/minutes/fractional seconds everywhere, including saved usage, weekly totals and overview. Timeline/Search are newest first; Review sources and People/Places histories are oldest first. LatestMemory depends on ascending Review sources and requests their last matching row.

Recommended: one summary formatter and clearly labeled existing order. Keeping fractional seconds everywhere adds reading noise. Changing all lists to newest first is possible but affects source paging and the latest-memory algorithm; it is unnecessary when the review explicitly permits visible order labels. Do not add a user-configurable sort mode.

## Duration policy

A nonnegative duration is formatted identically on Today, Timeline cards, saved usage, weekly Review, per-app values and magnitude of Review changes:

| Raw milliseconds | English summary | Rule |
| --- | --- | --- |
| 0 | 0 sec | Explicit recorded zero |
| 1–999 | <1 sec | Preserve positive subsecond value |
| 1,000–59,999 | N sec | Whole seconds, floor; 59,999 → 59 sec |
| 60,000–3,599,999 | N min | Whole minutes, floor; 60,001 → 1 min |
| 3,600,000 and above | H h M min | Whole minutes, floor; 3,600,001 → 1 h 0 min |
| null in nullable presentation | Existing unavailable label | Missing is never coerced to zero |

Flooring avoids showing 60 sec/60 min at a boundary without promoting units. Larger positive values never collapse to zero. Derive parts using integer division/modulo; do not convert the full value to Double or add a rounding offset that could overflow. Negative raw durations are invalid input to the formatter; Review signs remain outside the nonnegative magnitude formatter, as today.

Exact values remain visible in **existing full usage details**: `EventCard.SignalContent(full = true)` and each saved-day usage session card in `ReviewUsageRoute.kt` display a secondary `Exact duration: …` using hours/minutes and up to three fractional second digits. For example 1,001 ms → `Exact duration: 0 h 0 min 1.001 s`. Use the existing `usage_duration` template and NumberFormat on only the seconds remainder. No new modal or toggle is needed. Keep interval endpoints, continuation/ongoing markers and coverage explanation. Summary values are display precision, never stored or used for comparisons.

Today’s >60,000 ms app display threshold and Timeline's visibility policy remain unchanged. A formatter's ability to represent a short duration does not imply changing which rows are shown. Positive subminute Review changes remain visible (`+<1 sec` where applicable); recorded zero changes use `0 sec`, without a sign.

## Components and contracts

Create `ui/common/DurationDisplay.kt`:

- `internal sealed interface DurationDisplay` with `Zero`, `UnderSecond`, `Seconds(val value: Long)`, `Minutes(val value: Long)`, `HoursMinutes(val hours: Long, val minutes: Long)`.
- `internal fun durationDisplay(millis: Long): DurationDisplay`: `require(millis >= 0)`, pure integer projection.
- `@Composable fun humanReadableDuration(millis: Long): String`: localized resource rendering of the projection, formatting numeric parts with NumberFormat integer instance.
- `@Composable fun exactDuration(millis: Long): String`: nonnegative input; localized existing `usage_duration` output with at most three fractional second digits, no thousands of total seconds conversion.

Keep public `appUsageDuration(millis: Long): String` as a delegating wrapper, preserving existing Today/EventCard callers. `reviewDuration(millis: Long?): String` keeps its existing unavailable handling and delegates nonnull values to `humanReadableDuration`. Exact labels are supplied by caller using `usage_exact_duration` (`Exact duration: %1$s`). Add `app_usage_duration_minutes` (`%1$s min`) and `app_usage_duration_hours_minutes` (`%1$s h %2$s min`), passing preformatted integer strings; keep placeholder types consistent across all language bundles. Reuse `app_usage_duration_seconds` with preformatted whole seconds and `app_usage_duration_under_second` unchanged. Existing duration resource keys may remain if other callers need them; do not delete translations speculatively.

No domain/repository contract changes. Existing `ReviewSignals` signs and `abs(delta)` consume the common nonnegative summary formatter. All sums and changes are still calculated from raw milliseconds.

## Ordering labels

| Surface | Existing order | Visible localized label |
| --- | --- | --- |
| Timeline | Descending occurrence, stable ID tie-break | Newest first |
| Search | Descending occurrence, stable ID tie-break | Newest first |
| Review sources, including memory source lists | Ascending occurrence, stable ID tie-break | Oldest first |
| Person and place linked history | Ascending occurrence, stable ID tie-break | Oldest first |

Add `entry_order_newest_first` and `entry_order_oldest_first` in English, Persian and French. Put the label once near the list heading/result count and before entries; it participates in ordinary accessibility reading. Use test tags `timeline_order`, `search_order`, `review_source_order`, `person_history_order`, `place_history_order`. Do not change button chronological meanings, SQL, repositories, selected dates, tie-breakers or the latest-memory offset. People/Places labels should be in history content, allowing unit 18 to move only navigation bars.

## Acceptance and verification

- **D17.1:** The same raw duration produces the same summary on Today, saved usage and Review; exact values appear only as labeled secondary full details.
- **D17.2:** 0, null, 1, 999, 1,000, 59,999, 60,000, 3,599,999, 3,600,000 and Long.MAX_VALUE have specified nonoverflowing behavior; a positive duration never shows zero.
- **D17.3:** English/French/Persian values use localized numbers/decimal separators; RTL preserves understandable units and sign/magnitude relationship.
- **D17.4:** Order labels match actual fixture order across each list; equal timestamps keep stable ordering and source-page boundaries have no duplicates/gaps.
- **D17.5:** Review's latest-memory preview still opens the newest saved memory after these presentation changes; no source query direction is reversed.
- **D17.6:** Missing/partial history and aggregate zero distinctions, ongoing sessions, day clipping and usage thresholds remain unchanged.

Unit-test integer projection boundaries; Compose-test localized rendered summaries/precise details and null handling. Use existing Review/Timeline/Search/catalog fixtures to verify order and latest-memory selection. Reuse existing usage/domain tests rather than duplicate calculation tests. Manual acceptance covers RTL speech and narrow/large-text wrapping.
