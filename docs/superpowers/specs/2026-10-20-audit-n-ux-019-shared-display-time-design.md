# UX-019 — Saved records and capture use different hour-cycle policies

**Status:** Implementation complete; regression and runtime verification remain pending at the user's request.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-019](../../UI_UX_AUDIT-20261020N.md#ux-019--saved-records-and-capture-use-different-hour-cycle-policies)
**Priority / effort / confidence:** P2 — Medium / Medium / High (copied from the static audit; not a delivery estimate).
**Affected surface:** Capture timestamps, Timeline, Search, Review and histories
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-019-shared-display-time.md)
**Delivery phase:** 3 — navigation, scale and consistency

## Intent and success

Use the app locale and Android 12/24-hour preference consistently for capture and saved-record time displays. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

The audit is source evidence, not reproduced runtime acceptance. The scoped implementation follows this specification; tests and runtime exercises remain future work.

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

1. time uses HH:mm or h:mm a with explicit locale and DecimalStyle. dateTime composes localized Gregorian date with the same time formatter rather than a locale-default hour cycle.
2. Inventory all localizedTime/localizedDateTime/ofLocalizedTime display calls across app source, including usage sessions, Search, review, details and history. Replace each with the shared policy or an explicitly equivalent formatter.
3. Locale/hour preference changes while the app is paused update display on resume. Locale/date storage, ISO keys, Instant values, zones and existing timestamp input resolution remain unchanged.
4. Treat decimal/digit policy as display only. Do not convert Gregorian calendar dates to a different calendar as part of Persian localization.

## Design and contract

Create an explicit DisplayTimePolicy at the Compose boundary and pure locale-aware formatter methods. Migrate display call sites while leaving stored instants and Gregorian civil dates intact.

data class DisplayTimePolicy(val locale: Locale, val use24Hour: Boolean) has time(): DateTimeFormatter and dateTime(style: FormatStyle): DateTimeFormatter. @Composable rememberDisplayTimePolicy(): DisplayTimePolicy reads app configuration locale and DateFormat.is24HourFormat(context), refreshing on resume/configuration changes. Remove the implicit true hour-cycle default once all callers migrate.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `sameInstantUsesSameHourCycle` | Display 13:05 in capture, saved card, Search, Review and history under 12-hour preference. | All use 1:05 with localized day period; no surface silently renders 13:05. |
| AC-2 | `explicit24HourPolicy` | Render the same instant with 24-hour preference in EN/FR/FA. | All use the explicit 24-hour cycle and correct locale digits. |
| AC-3 | `resumePicksUpPreferenceChange` | Switch Android hour setting while app is backgrounded. | Existing screens update their time policy on resume without data writes. |
| AC-4 | `zoneAndCalendarStayStable` | Render an instant across midnight in Asia/Tehran and a DST-transition zone. | Displayed civil date uses its intended zone and Gregorian calendar; stored instant is unchanged. |
| AC-5 | `mixedDirectionIdentifiersStayReadable` | Display Persian time beside an IANA zone/package identifier at 200% text. | Localized digits and bidirectional text remain readable with no forced storage transformation. |

## Scope boundary

No changes to persisted timestamps, ISO dates, calendar system, tracking-zone rules or timestamp selection algorithms.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.
