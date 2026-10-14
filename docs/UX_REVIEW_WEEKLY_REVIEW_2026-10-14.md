# Weekly Review — UX review

Date: 14 October 2026  
Scope: Weekly Review overview, navigation, comparisons, empty states and accessibility.  
Status: UI changes applied; compile and device acceptance remain pending.

## Review basis

I inspected the current Compose implementation in `WeeklyReviewRoute.kt`, `ReviewOverview.kt`, `ReviewValues.kt` and `ReviewSignals.kt`, along with the existing UX review. The earlier walkthrough already identified the Weekly Review as a long report with weak overview hierarchy (finding 08, observed in `01-start` and `41-review-digital` in [UX_REVIEW_2026-10-13.md](UX_REVIEW_2026-10-13.md)). The current computer-use session had no open app or emulator surface, so I could not reproduce those screenshots or make fresh visual observations. The recommendations below distinguish source-confirmed behavior from design judgment.

## What is working and should remain

- Week selection, previous/next navigation, and a clear return to the current week.
- Current-versus-previous comparisons and links from metrics to their source records.
- Honest handling of missing measurements versus a known recorded zero.
- A latest-memory shortcut and daily evidence/methodology disclosures for users who want detail.
- Local-only reporting and explicit context for linked people and places.

These are valuable foundations. The redesign should make them easier to scan without removing access to evidence or changing how metrics are calculated.

## Main findings and required changes

### 1. Give the first screen a clear summary hierarchy — High priority

**Evidence:** The header stacks the title, explanation, week controls, comparison range, raw time-zone ID and partial-week label (`WeeklyReviewRoute.kt:52–74`). The overview then lists mood, latest memory, usage, usage coverage, activity, people and places in one card (`ReviewOverview.kt:44–72`). Several of these values appear again in the detailed sections. This matches the earlier finding that the review reads as a long report rather than a quick overview.

**Change:** Condense the header into a compact period control with one visible comparison statement. Follow it with a short “Week at a glance” area that gives visual priority to a few useful signals. Keep each signal factual and directly tied to the saved data; do not infer a positive or negative judgment about the user's week. Move secondary explanation and repeated coverage detail into the relevant section.

**Why:** A user should understand which week they are viewing and see the most useful changes before scrolling through the evidence. Repeated summaries add length without adding new information.

### 2. Replace the repeated full-width metric cards with compact comparisons — High priority

**Evidence:** `ReviewValue` renders every metric as a full-width card with a label, current value, previous value, change text and sometimes a source control (`ReviewValues.kt:26–54`). Counts, mood scales, people/places, apps and activity all use this pattern, so a single report can become a long sequence of visually identical cards.

**Change:** Group related values into clearly titled section panels. Within each panel, use a compact comparison row or small metric tile that keeps the current value, previous value and change together. Reserve larger cards for genuinely notable content, such as a memory highlight. Keep “view sources” available, but make it a secondary action rather than another repeated visual block.

On narrow screens, preserve a readable stacked comparison inside each metric; do not squeeze three values into tiny columns. Use neutral change styling, with a spoken or visible “up/down by” label so meaning never depends on color alone.

**Why:** This reduces vertical scrolling and makes week-to-week differences easier to compare at a glance.

### 3. Make section navigation persist and follow the user's position — High priority

**Evidence:** The five section chips are a wrapping `FlowRow` before the report body (`ReviewSignals.kt:27–37`). They scroll away with the list. Their selected state changes only when a chip is tapped (`WeeklyReviewRoute.kt:90–94`); manual scrolling does not update it.

**Change:** Keep a compact section selector available while browsing, using a horizontally scrollable row on narrow screens. Tapping a section should continue to move to its heading. Also update the selected section as the user scrolls, and ensure the active heading is not hidden behind the persistent selector. Keep all labels localized and the touch targets accessible.

**Why:** Users can move through a long review without returning to the top, and the selected chip continues to reflect where they are.

### 4. Simplify the period and data-coverage language — Medium priority

**Evidence:** The current period and prior comparison dates appear as separate lines; the time-zone value is rendered as a raw `zone.id`; partial weeks receive another standalone line (`WeeklyReviewRoute.kt:61–74`). The overview adds usage coverage, and the digital section repeats it (`ReviewOverview.kt:52–54`, `ReviewSignals.kt:47–54`).

**Change:** Present the selected week and its comparison in one compact, localized period block. When the selected week is partial, say which days are included and make the matching comparison window clear. Replace the raw zone identifier with a user-readable local-time label; keep technical zone details in secondary information if needed. Show signal coverage next to the affected signal, once, and explain whether a value is unavailable, incomplete or a known zero.

**Why:** Users should be able to interpret a change without piecing together several small labels or mistaking missing data for zero. Coverage belongs with the measure it qualifies.

### 5. Make empty and unavailable states specific to each section — Medium priority

**Evidence:** One `empty` condition is calculated from current-week records, usage total and activity (`WeeklyReviewRoute.kt:82–98`). The report then continues to render all sections. Generic “no patterns” and “unavailable” labels are also used for several distinct situations.

**Change:** Keep the overall empty state only for a week with no useful recorded signals. Give each section its own concise state: no entries, no linked people or places, no saved usage history, incomplete coverage, or a known zero. When the previous week has data, include that comparison in the empty-state context rather than making the whole review look blank. Do not offer an action that implies a permission or data source is required unless that is actually the reason.

**Why:** A sparse week can still contain useful information, and a missing measurement has a different meaning from a measured zero.

### 6. Strengthen accessibility and localization of comparisons — Medium priority

**Evidence:** The main title and several section titles have heading semantics, while subheadings produced by `CatalogPatterns` are plain text (`WeeklyReviewRoute.kt:103–117`). Comparison values are presented as separate text nodes inside a repeated visual card (`ReviewValues.kt:27–40`).

**Change:** Mark every navigable section and subsection as a heading. Ensure assistive technology reads a comparison as one understandable group: metric name, current period/value, previous period/value, change and unit. Give any compact visual encoding an equivalent text label. Verify Persian right-to-left ordering, date layout, chip navigation and long localized labels; support large text without clipping or reducing the user's font scale.

**Why:** The report's meaning must remain clear when heard, enlarged or read in another supported language.

## Suggested reading order

1. **Period:** selected week, whether it is partial, and the comparison window.
2. **At a glance:** a small set of factual highlights with clear availability/coverage.
3. **Sections:** compact comparisons for entries, mood, memories, people/places, digital usage and activity.
4. **Evidence:** source records, daily breakdowns and calculation details on demand.

This order keeps the overview useful while preserving the existing ability to inspect the underlying records.

## Acceptance criteria for a redesign

- The opening viewport identifies the selected week and comparison window without a raw time-zone string or several competing date lines.
- The overview is shorter than the current multi-signal card and does not repeat detailed section content verbatim.
- A user can compare current and previous values without scanning a stack of visually identical full-width cards.
- Section navigation remains available during long scrolling, its selected state follows manual scrolling, and tapping a section lands with its heading visible.
- Partial weeks, incomplete coverage, unavailable values and recorded zeroes have distinct, localized labels.
- Empty current-week sections remain understandable when the prior week contains data.
- Persian RTL, French, English, narrow width and large text preserve readable order, legible values and reachable controls.
- TalkBack announces section headings, comparison periods, units, changes and actionable source links in a sensible order.

## Implementation update — 14 October 2026

Applied the recommendations to the existing Weekly Review screen:

- Combined the comparison window and local-time label and removed the raw zone ID.
- Tightened the overview card, removed its repeated linked-context explanation, and kept coverage beside the usage summary.
- Grouped metric rows into shared panels, shortened the change label, and kept source actions reachable with larger touch targets.
- Made section navigation horizontally scrollable and sticky. Its selected chip now follows vertical scrolling and scrolls into view when selection changes.
- Added prior-week context to the empty state and clearer usage/activity no-data copy. Added heading semantics to people, place and emotion subheadings.
- Localized the new and revised copy in English, French and Persian.

Validation status: `git diff --check` passed. The debug build did not start: Gradle could not create its wrapper lock file under the read-only `/home/bajelan/.gradle` directory. No tests or live emulator walkthrough were run, so the acceptance criteria above still need device and accessibility verification.

## Source references

- [WeeklyReviewRoute.kt](../app/src/main/java/com/luma/lifeos/ui/review/WeeklyReviewRoute.kt): period header, report ordering, empty state and section-scroll handling.
- [ReviewOverview.kt](../app/src/main/java/com/luma/lifeos/ui/review/ReviewOverview.kt): overview contents and usage-coverage summary.
- [ReviewValues.kt](../app/src/main/java/com/luma/lifeos/ui/review/ReviewValues.kt): repeated comparison-card structure.
- [ReviewSignals.kt](../app/src/main/java/com/luma/lifeos/ui/review/ReviewSignals.kt): section chips and digital/activity sections.
- [UX_REVIEW_2026-10-13.md](UX_REVIEW_2026-10-13.md): prior walkthrough finding 08 and its evidence labels.
