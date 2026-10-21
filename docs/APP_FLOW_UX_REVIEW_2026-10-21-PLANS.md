# App flow review — task specifications and implementation plans

**Date:** 21 October 2026
**Source:** [APP_FLOW_UX_REVIEW_2026-10-21.md](APP_FLOW_UX_REVIEW_2026-10-21.md)
**Scope:** 24 task pairs: the review’s 17 remaining numbered findings and 7 focused tasks for uncovered live observations/design work.
**Status:** FLOW-001 and FLOW-006 implementation complete; focused verification remains pending at the user's request. FLOW-002 implementation complete; focused runtime verification pending at the user's request. FLOW-003 implementation complete. FLOW-004 implementation complete; focused instrumentation, build, and emulator verification remain pending at the user's request. FLOW-005 scoped implementation complete; prerequisite integration remains pending. FLOW-007 scoped implementation complete; UX-012/UX-021 integration and focused verification remain pending at the user's request. Other task statuses are listed below. UX-028 remains optional and unselected.

## Coverage and reuse

The 17 numbered findings already had individual specifications and plans in the 20 October audit package. Those remain the canonical pairs and are linked below. The UX-014 and UX-020 pairs were clarified with complete callable signatures during this documentation pass. Seven new pairs cover the remaining actionable observations and visual work. Overlapping observations map to an existing task rather than duplicating its implementation. The review’s demo tasks, people, places, and entries are fixtures, not software implementation tasks.

The new specifications distinguish source behavior from the installed emulator build. In particular, Today already preserves an explicit shared selected date, and HabitProgressContent already draws a full-width indicator. FLOW-001 keeps the intended date policy and makes it visible; FLOW-003 verifies the matching build and fixes only confirmed progress presentation. No fresh emulator walkthrough, app test, or build was run to prepare this package.

## Task catalog

| Task | Deliverable | Specification | Implementation plan | Status |
|---|---|---|---|---|
| UX-002 | Unknown widget privacy mode is reported as Summary | [Spec](superpowers/specs/2026-10-20-audit-n-ux-002-widget-mode-status-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-002-widget-mode-status.md) | Draft / not started |
| UX-003 | The widget privacy switch has no accessible name | [Spec](superpowers/specs/2026-10-20-audit-n-ux-003-widget-privacy-switch-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-003-widget-privacy-switch.md) | Draft / not started |
| UX-010 | Deleting an entry can return to its missing detail page | [Spec](superpowers/specs/2026-10-20-audit-n-ux-010-deleted-detail-navigation-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-010-deleted-detail-navigation.md) | Draft / not started |
| UX-012 | Context selection requires scanning the entire catalog | [Spec](superpowers/specs/2026-10-20-audit-n-ux-012-searchable-context-selection-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-012-searchable-context-selection.md) | Draft / not started |
| UX-013 | A historical habit link does not locate the requested repetition | [Spec](superpowers/specs/2026-10-20-audit-n-ux-013-habit-targeted-history-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-013-habit-targeted-history.md) | Draft / not started |
| UX-014 | Habit management sits after all loaded history | [Spec](superpowers/specs/2026-10-20-audit-n-ux-014-habit-detail-management-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-014-habit-detail-management.md) | Draft / not started |
| UX-017 | Successful integration refresh leaves obsolete error text | [Spec](superpowers/specs/2026-10-20-audit-n-ux-017-integration-error-recovery-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-017-integration-error-recovery.md) | Draft / not started |
| UX-018 | Several important asynchronous results lack announcement semantics | [Spec](superpowers/specs/2026-10-20-audit-n-ux-018-accessible-async-feedback-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-018-accessible-async-feedback.md) | Draft / not started |
| UX-019 | Saved records and capture use different hour-cycle policies | [Spec](superpowers/specs/2026-10-20-audit-n-ux-019-shared-display-time-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-019-shared-display-time.md) | Draft / not started |
| UX-020 | Entry card accessibility actions promise Edit but open details | [Spec](superpowers/specs/2026-10-20-audit-n-ux-020-entry-card-action-labels-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-020-entry-card-action-labels.md) | Draft / not started |
| UX-021 | Page chrome and inset ownership vary across screen families | [Spec](superpowers/specs/2026-10-20-audit-n-ux-021-page-header-insets-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-021-page-header-insets.md) | Draft / not started |
| UX-022 | Visible headings are missing semantic heading roles | [Spec](superpowers/specs/2026-10-20-audit-n-ux-022-semantic-headings-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-022-semantic-headings.md) | Draft / not started |
| UX-023 | Expanded disclosures lose their subject and state | [Spec](superpowers/specs/2026-10-20-audit-n-ux-023-named-stateful-disclosures-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-023-named-stateful-disclosures.md) | Draft / not started |
| UX-024 | Leaving during backup has no explicit operation outcome | [Spec](superpowers/specs/2026-10-20-audit-n-ux-024-backup-departure-outcome-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-024-backup-departure-outcome.md) | Draft / not started |
| UX-025 | Restore confirmation has an unbounded warning body | [Spec](superpowers/specs/2026-10-20-audit-n-ux-025-restore-dialog-reachability-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-025-restore-dialog-reachability.md) | Draft / not started |
| UX-026 | Weekly activity can render a nonzero value as zero | [Spec](superpowers/specs/2026-10-20-audit-n-ux-026-activity-number-precision-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-026-activity-number-precision.md) | Draft / not started |
| UX-028 | Attached images cannot be inspected beyond their previews | [Spec](superpowers/specs/2026-10-20-audit-n-ux-028-optional-photo-viewer-design.md) | [Plan](superpowers/plans/2026-10-20-audit-n-ux-028-optional-photo-viewer.md) | Optional / not selected |
| FLOW-001 | Make Today’s selected-date context explicit | [Spec](superpowers/specs/2026-10-21-flow-001-today-date-context-design.md) | [Plan](superpowers/plans/2026-10-21-flow-001-today-date-context.md) | Implementation complete / focused verification pending at the user's request |
| FLOW-002 | Remove duplicate empty-task messaging | [Spec](superpowers/specs/2026-10-21-flow-002-today-task-empty-state-design.md) | [Plan](superpowers/plans/2026-10-21-flow-002-today-task-empty-state.md) | Implementation complete / focused runtime verification pending |
| FLOW-003 | Make habit progress visually legible | [Spec](superpowers/specs/2026-10-21-flow-003-habit-progress-track-design.md) | [Plan](superpowers/plans/2026-10-21-flow-003-habit-progress-track.md) | Implementation complete / runtime verification pending |
| FLOW-004 | Show the scheduled habit count in Today’s preview | [Spec](superpowers/specs/2026-10-21-flow-004-habit-preview-count-design.md) | [Plan](superpowers/plans/2026-10-21-flow-004-habit-preview-count.md) | Implementation complete / focused runtime verification pending |
| FLOW-005 | Refine Today’s information hierarchy | [Spec](superpowers/specs/2026-10-21-flow-005-today-information-hierarchy-design.md) | [Plan](superpowers/plans/2026-10-21-flow-005-today-information-hierarchy.md) | Scoped implementation complete / prerequisite integration and runtime verification pending |
| FLOW-006 | Unify populated card hierarchy | [Spec](superpowers/specs/2026-10-21-flow-006-content-card-hierarchy-design.md) | [Plan](superpowers/plans/2026-10-21-flow-006-content-card-hierarchy.md) | Implementation complete / focused verification pending at the user's request |
| FLOW-007 | Standardize capture and editor form layout | [Spec](superpowers/specs/2026-10-21-flow-007-capture-form-anatomy-design.md) | [Plan](superpowers/plans/2026-10-21-flow-007-capture-form-anatomy.md) | Scoped implementation complete / UX-012 and UX-021 integration plus focused verification pending |

## Live observation mapping

Every row in the review’s “Additional live observations” table has an owner.

| Observation in review order | Owning task | Boundary |
|---|---|---|
| 1. Today resumes with a previously selected day | FLOW-001 | Preserve intentional selected-date restoration; make historical/future context explicit. |
| 2. Duplicate empty-task messages | FLOW-002 | One ordinary empty message, with count retained for positive totals. |
| 3. Different Today and Timeline title treatments | UX-021 | Shared headers, type hierarchy, gutters, and inset ownership. |
| 4. Isolated progress dot | FLOW-003 | Confirm matching build; make the existing progress track legible. |
| 5. Two habit rows without a hidden-item/count cue | FLOW-004 | Keep two rows and expose the scheduled total without hiding recovery. |
| 6. People/place option chips have no search | UX-012 | Searchable staged selector; preserve creation and selected IDs. |

## Visual direction mapping

These eight directions are covered by the task pairs above; they are not eight additional competing redesigns.

| Review direction | Owners | Result |
|---|---|---|
| 1. One page rhythm | UX-021, UX-022 | Shared chrome/insets, stable type roles, meaningful headings. |
| 2. Clear Today first glance | FLOW-001, FLOW-002, FLOW-004, FLOW-005 | Explicit date, stable capture position, compact daily previews, recent entries before summary. |
| 3. Content-led cards | FLOW-003, FLOW-006 | Legible habit progress and stable title/body/metadata roles. |
| 4. Consistent type scale | UX-021, FLOW-006 | Material roles for pages/sections/cards; no new brand type system. |
| 5. Clear habit/task actions | FLOW-003, FLOW-004, FLOW-006, UX-020 | Separate open/log/complete actions with useful counts and honest names. |
| 6. Standard form anatomy | FLOW-007, UX-012, UX-019, UX-023 | Primary input first, compact timestamps, searchable context, named optional groups. |
| 7. Designed empty/success/error states | FLOW-002, UX-017, UX-018 | Clear empty state and scoped, accurately announced operation feedback. |
| 8. Adaptive behavior | UX-021 and every task’s Global Constraints | EN/FR/FA, RTL, semantic light/dark, 48 dp controls, wrapping content, wide reading width, and IME ownership. |

FLOW-005 keeps the three direct capture choices in one coherent capture area. This is the proposed resolution of the review’s generic-primary-action suggestion: one tap still reaches each capture form, and the empty-entry sentence moves to Recent entries so capture does not shift after the first save.

## Prerequisites and integration order

Only the “requires” entries below are hard dependencies. Other ordering avoids editing the same surface repeatedly.

| Work | Prerequisite / coordination |
|---|---|
| UX-002 and UX-003 | No hard dependency; coordinate widget configuration files. |
| UX-010, UX-017–UX-020, UX-022–UX-026 | Individually scoped corrections; preserve their existing spec contracts and targeted regressions. |
| UX-014 then UX-013 | Recommended integration order for lazy management/history before targeted-log arrival; UX-013 can use the existing getLog API independently. |
| UX-012 | No new hard prerequisite; preserve the already-present catalog creation result behavior. |
| UX-021 | Pilot Today, Timeline, and Mood first, then migrate remaining page families using recorded geometry. |
| FLOW-001–FLOW-004 | No hard prerequisites. Preserve their date/count/progress behaviors during later Today/header edits. |
| FLOW-006 | No hard prerequisite; coordinate EventCard changes with UX-019/UX-020 and shared style choices with UX-021. |
| FLOW-005 | Requires UX-021 and FLOW-001–FLOW-004. It integrates the refined Today overview. |
| FLOW-007 | Requires UX-021 and UX-012. Retain UX-019/UX-023 formatting and disclosure behavior when present. |
| UX-028 | Separate optional proposal; documentation does not select it for implementation. |

Suggested batches are: privacy/status/recovery corrections; navigation/selectors/formatting; shared page pilot and focused Today/card fixes; then FLOW-005/FLOW-007. Use one integration owner for shared TodayScreen, EventCard, editor, and resource changes. This index does not choose an execution method for the remaining tasks.

## Preserved decisions and open product choices

- Preserve the current local TodayHabitsCard limit of two rows and 4 dp internal gaps. The old HabitCaptureFlowTest still expects three rows; FLOW-004 explicitly updates that stale expectation to two during its implementation.
- Date selection remains null-to-follow-current-day or an explicit selected date across tabs/recreation. FLOW-001 does not silently reset a deliberate selection on every resume.
- FLOW-003 retains the standard Material indicator and exact numeric progress. It does not infer a missing source component from the unmatched emulator screenshot.
- FLOW-006 changes only nonconforming presentation sites. Existing compliant card styling and independently clickable actions remain intact.
- The 840 dp wide reading width in UX-021 and counted View all label in FLOW-004 remain draft policy choices. FLOW-007's form layout and disclosure choices were implemented as specified for this task.
- UX-028’s photo decode/zoom proposal remains optional. No cloud, account, social, new primary tab, or palette replacement is added by these plans.

## Proportionate execution checks

The original documentation pass ran no prospective commands and left implementation checkboxes unchecked. FLOW-001, FLOW-003, and FLOW-006 have since been implemented. FLOW-005's capture styling and empty-message ownership have also been implemented; its UX-021 and FLOW-001/002/004 prerequisite integration remains pending. FLOW-007's scoped form work is implemented while UX-012/UX-021 integration remains pending. The linked plans record implementation steps and pending focused verification. The 17 canonical pairs retain their issue-specific acceptance cases, including failure paths where those are the issue being corrected. The new visual plans reuse existing fixtures and avoid unit tests that merely restate spacing or copy.

When implementation is selected, use one ordinary visit per affected section/form and the existing five-record dataset. Add only the targeted regression needed for a changed state/navigation contract; do not repeat the full walkthrough per cosmetic change or create five more records per form. Run the integrated compile/resource/lint checks once per changed batch, repeating only for new changes or failures. Record any specialized release checks not executed as pending.

## Documentation verification

- Verified 24 unique task IDs, 24 specifications, 24 plans, and five scenarios per specification (120 total).
- Checked 166 local Markdown links and 220 source-path references; proposed additions are explicitly marked.
- Confirmed all six live observations and eight visual directions have owners, and the review still contains only its 17 remaining numbered findings.
- Confirmed reciprocal spec/plan links, required plan fields, unchecked implementation steps, no unfinished placeholders, and clean whitespace checks.
- No app build, automated tests, or emulator walkthrough was run during this documentation task. These checks establish document consistency.
