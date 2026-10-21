# FLOW-001 — Make Today’s selected-date context explicit

**Status:** Draft specification; implementation has not started. Policy choices are proposed defaults for review.
**Date:** 21 October 2026
**Source:** [App flow and UI/UX review](../../APP_FLOW_UX_REVIEW_2026-10-21.md), Additional live observation 1; visual direction 2.
**Priority:** P2
**Plan:** [Implementation plan](../plans/2026-10-21-flow-001-today-date-context.md)
**Index:** [Task index and integration order](../../APP_FLOW_UX_REVIEW_2026-10-21-PLANS.md)

## Intent and success

Help a returning user distinguish the current day from an intentionally restored historical or future day without losing their browsing selection. Success means the five acceptance scenarios below hold while the existing data and action guarantees remain intact.

## Evidence and current behavior

LifeOsApp.kt already stores a nullable shared selectedDate with rememberSaveable: null follows rememberCurrentDay(), and an explicit date survives tab switches and recreation. CurrentDay.kt refreshes on STARTED and every 30 seconds. The emulator observation does not prove an incorrect clock or initialization bug; the installed binary was not tied to this source revision.

The review contains a light emulator walkthrough, not a reproduction on the checked-out binary. This document does not claim new runtime verification or implementation.

## Dependencies

None. UX-021 and UX-022 must retain this notice during later header migration.

## Global constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies.
- Localize changed copy in EN/FR/FA with matching resource names and numbered placeholders; preserve RTL and Gregorian storage/display policy.
- Keep semantic light/dark colors, interactive targets of at least 48 dp, and reachable content at 100%/130%/200% text in compact portrait and short landscape.
- Preserve private offline storage, consent boundaries, stable record/command IDs, original retry targets, accepted drafts, and Undo protections.
- Preserve unrelated edits, including the existing two-row limit and 4 dp internal gap in TodayHabitsCard.kt.
- Implement only this task and its declared prerequisites; no database migration, new provider permission, or dependency upgrade is included.

## Behavior and design decisions

1. A fresh launch without a saved explicit selection follows the current device-local date and zone. Keep the existing nullable date policy; do not clear a deliberate selection on every resume or tab switch.
2. When Today’s selected date differs from the current device date, show a visible “Viewing %1$s” notice directly below DayNavigation. Format the full selected Gregorian date using the existing app-locale formatter. The notice is absent when the dates match.
3. Keep the existing Today action beside the selected date; activating it clears selectedDate to null and removes the notice. Previous/next and picker actions retain their existing selected-date behavior.
4. The notice is presentation only. Capture still uses the existing selected-date-versus-now choice, and habit tracking-zone dates and current-day-only task selection retain their separate meanings.
5. Use onSurface/onSurfaceVariant with a subtle tonal container and text; do not depend on color alone or announce an unchanged notice on every recomposition.

## Interfaces and data ownership

Add @Composable internal fun TodayDateNotice(selectedDate: LocalDate, currentDate: LocalDate, modifier: Modifier = Modifier): Unit in ui/today/TodayDateNotice.kt. Add required deviceDate: LocalDate to TodayScreen and pass TodayRoute’s existing deviceDate at both call sites. Retain LifeOsApp selectedDate and all date mutation callbacks. Resource today_viewing_date has one %1$s date argument. Keep day_today/day_selected tags and add today_date_notice.

Only paths explicitly marked Create are new. Existing callbacks, field identities, and state owners remain the contract unless this section explicitly changes them. No stored record data is transformed by presentation changes.

## Acceptance scenarios

| ID | Case | Input/action | Required result |
|---|---|---|---|
| FLOW-001-AC-1 | `currentDateHasNoNotice` | Launch with no saved selection. | Current local date is selected and no historical notice appears. |
| FLOW-001-AC-2 | `restoredSelectionIsExplicit` | Restore an explicit earlier date and switch tabs back to Today. | The date is retained and the visible notice names it. |
| FLOW-001-AC-3 | `todayClearsSelection` | Activate the existing Today action while viewing a prior day. | Selection becomes following-current-day and the notice disappears. |
| FLOW-001-AC-4 | `captureRetainsDateIntent` | Use Journal from a selected prior day. | The existing selected-date/now choice still precedes capture; the notice makes no timestamp mutation. |
| FLOW-001-AC-5 | `clockContextRemainsSeparate` | Refresh the device day/zone while an explicit selection remains. | Current-day comparison refreshes; explicit selection and habit tracking-zone semantics remain intact. |

## Scope boundary

No automatic date reset on ordinary resume, separate per-tab date state, clock replacement, timestamp migration, or change to habit tracking-zone rules.

## Completion evidence

Use the matching plan's focused verification. A screenshot demonstrates appearance for its recorded build/configuration, not every device or assistive-technology state. Document unavailable checks as pending. No app tests, build, or implementation were performed while preparing this spec.
