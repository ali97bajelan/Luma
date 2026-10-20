# UX-009 — Past-day summary Check in opens a current-time entry

**Status:** Implemented / acceptance pending. Gradle and emulator acceptance were not run per request. Proposed decisions below remain reviewable defaults, not previously approved product policy.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-009](../../UI_UX_AUDIT-20261020N.md#ux-009--past-day-summary-check-in-opens-a-current-time-entry)
**Priority / effort / confidence:** P2 — Medium / Low / High (copied from the static audit; not a delivery estimate).
**Affected surface:** Today → daily summary
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-009-summary-dated-capture.md)
**Delivery phase:** 2 — recovery and truthful feedback

## Intent and success

Make missing-mood capture from a past-day summary use the same explicit date choice as Today capture. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

The audit is source evidence, not reproduced runtime acceptance. Its recommendations are input to this draft, not executable instructions. Regression tests and runtime exercises remain pending.

## Dependencies

None. Coordinate overlapping files using the index's integration order.

## Global constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies unless a task explicitly requires a reviewed exception.
- All changed user-facing copy must exist in EN/FR/FA with matching resource names and numbered placeholder signatures; preserve RTL and intended Gregorian display/storage behavior.
- Keep semantic light/dark palettes, at least 48 dp interactive targets, and reachability at 100%/130%/200% font, narrow portrait and short landscape.
- Preserve offline/private storage, existing consent boundaries, stable record/command IDs, original retry payloads and Undo protections.
- Never truncate accepted personal content, infer write failure means no commit, or treat an unavailable privacy mode as known.
- Only implement this task and its declared prerequisites; preserve unrelated working-tree changes and existing navigation/draft state.

## Behavior and policy

1. For a non-current selected day, offer the existing Add to [date] and Use now choices. Do not silently use a new default time policy.
2. For the current day in the active display zone, open current-time capture directly, matching existing shortcuts.
3. Date choice and editor initialization use a consistent selected-day/zone snapshot. Dismiss causes no navigation and no write.

## Design and contract

Extract and reuse the existing Today date-choice dialog. Route summary capture through onCapture(EventType.MOOD, LocalDate?) into existing openCapture initialTime handling.

CaptureDateChoice(date: LocalDate, onSelectedDate: () -> Unit, onNow: () -> Unit, onDismiss: () -> Unit): Unit is @Composable. DailySummaryCard's mood action delegates to the same selected-day handler used by TodayCaptureActions; existing openCapture(route: String, date: LocalDate?) owns timestamp initialization.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `pastSummaryOffersDateChoice` | Open Check in on the 2026-10-18 summary while current date is 2026-10-20. | The existing selected-date/now choice appears before editor navigation. |
| AC-2 | `selectedDateInitializesPastEditor` | Choose Add to 2026-10-18. | The editor timestamp falls on 2026-10-18 in the captured display zone. |
| AC-3 | `nowChoiceInitializesCurrentEditor` | Choose Use now. | The editor receives the existing current-time initialization. |
| AC-4 | `cancelDoesNotNavigate` | Dismiss the date chooser. | Summary stays selected and no record is created. |
| AC-5 | `zoneBoundaryIsConsistent` | Device UTC date differs from the selected display zone's date. | Current-day comparison and initialized timestamp use the same display zone. |

## Scope boundary

No new timestamp default, backdating without a choice or changes to persisted time semantics.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.

