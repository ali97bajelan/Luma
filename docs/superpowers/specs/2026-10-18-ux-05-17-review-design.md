# UX-05, UX-17: Compact weekly habits and discoverable sections

Status: UI and focused regression tests added in working tree; compilation/test execution blocked by sandbox access; broader acceptance pending.
Source: [review](../../UI_UX_REVIEW_2026-10-18.md); [brief](../../UI_UX_IMPLEMENTATION_READY_2026-10-18.md). Consume UX-16 formatting and UX-06 metric presentation. Habit detail improvements are a navigation destination, not a hard implementation dependency.

## Intent and alternatives

Surface available habit evidence and make every Review category discoverable. Keep existing report calculations, source order, partial-week matching and sticky shortcuts. Recommended: compact cards plus all-sections selector. Reordering sections by availability would cause unstable scroll targets; a second report route duplicates navigation and data work.

## UX-05: Overview and habit cards

Review currently shares one observeHabitWeek flow between overview counts and habit section. Keep that single live observation. Pass ReviewHabitsState to ReviewOverview in addition to the existing count; do not start another repository.week request.

When comparison rows exist, show a compact overview habit metric: total current repetitions and current scheduled habit-days (sum across habits), plus a “View habits” section-jump action. Label scheduled habit-days explicitly because it is not a count of unique calendar dates. Do not merge this metric into entry totals or recalculate UX-02 counts. Loading/error stays distinct from zero; habit failure must not hide the rest of Review.

Each card: wrapping habit name → localized repetitions/scheduled-days phrases → current seven-date status strip → concise previous-period context → one View history action. An optional “Day details” disclosure reveals per-day rows with history links; default card has no seven repeated history buttons. Disclosures are saveable by habit ID and week.

The strip has seven logical dates from current.start, chronological in semantic traversal. Dates through current.through use real HabitDay data. Remaining current-week dates say “Upcoming, not included”; they are not missing/missed. A previous-only habit's absent current data says “No records for this period” without manufacturing scheduled targets. Unscheduled/paused/archived dates are neutral; skip states retain their distinct labels. No red missed-goal interpretation for an unscheduled day.

Use noninteractive visual cells with accessible full date + state + count/target where relevant. They may be narrower than 48 dp because they are not controls. At 360 dp, use seven equal cells; at large text use a wrapping strip rather than clipped labels. State is communicated by text/symbol plus semantics, never color alone. Interactive Day details/history actions stay ≥48 dp. Expanded details provide every real per-day history destination.

View history opens on current.through when row.current exists, otherwise previous.through. Expanded day link passes the exact day.date. Comparison shows existing repetition/day counts, never new percent/delta arithmetic. If either period has zero scheduled days show “No comparable scheduled days” and avoid an implied improvement/decline. Keep domain compareHabitWeeks and reviewTarget unchanged.

## UX-17: All-sections selector and week context

Add a visible “Sections” button to sticky navigation. It opens a scrollable dialog listing Overview, Entry counts, Mood, Memories, People & places, App usage, Activity, and Habits when repository is available. Every entry appears even if empty. Retain current horizontal chips as optional shortcuts, selected state and auto-scroll. Overview and Entry counts are explicit selector entries; no need to add more chips.

Use stable keys already used by LazyColumn: overview, counts, mood, memories, catalog, usage, activity, habits. A single shared section list supplies dialog/chips/scroll tracking; do not hand-maintain divergent key arrays. Keep heading/sections/empty in the list index calculation so jumps remain correct.

Sticky context shows the selected current date range and in-progress marker where applicable once the large header is above the viewport. It uses the same full range in semantics and compact Gregorian range as ReviewHeader. Add “Overview” action that returns to overview, not another week. Week change resets stale section targets and selected day/disclosures for that week; same-week navigation/detail return retains saved scroll behavior.

## Acceptance

One short habit's collapsed card occupies ≤280 dp at 411 dp/font 1.0, excluding section heading. Larger text may grow; no fixed-height clipping. All seven states read meaningfully with TalkBack; every real day history remains reachable. Overview remains useful for habits-only data. Partial-week prior window and zero-denominator rules stay intact. Every section and selected week are discoverable without horizontal-gesture knowledge; jumps and restoration work after week change.
