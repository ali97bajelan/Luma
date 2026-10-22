# T32-A — Timeline stream and editorial cards specification

Date: 2026-10-22  
Status: Proposed / pending implementation. This document is a planning artifact, not release acceptance.  
Depends on: T30

## Intent and sources

Present the existing mixed chronological feed with a continuous visual rail, type nodes and readable editorial cards.

References: timeline-light.png; timeline-dark.png + timeline-dark.html; timeline-light.html is invalid for Timeline. Read the [shared reference brief](../../luma-redesign/README.md) and [task index](../../UI_REDESIGN_TASKS.md) with this specification. The brief resolves inconsistencies and provides common tokens; it is normative for every task.

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

- Use timeline-light.png for light structure, timeline-dark.png/HTML for dark structure and T30 for shared roles. Do not use the mislabeled light HTML as a Timeline design.
- Keep title/Search, DayNavigation, entry/habit counts, collapsed View options and visible active app-usage indicator. Preserve persisted usage toggle, timezone and newest-first explanation. New type filters are O02.
- Add a decorative leading rail and type icon node around each existing lazy row, with a distinct end treatment. Rail and node do not become separate focus/click targets; hide duplicate decorative semantics. Mirror placement with layout direction.
- Keep EventCard(record, zone, onOpen, openActionLabel, onMemory) and HabitTimelineCard(item, screenZone, onOpen) source-compatible. Journal text uses serif title, short excerpt, saved place/source/photo-count metadata; photo bitmap preview is O04.
- Preserve actual timestamps, equal-time ordering, date-only habit group, cross-zone labels, stable keys, bounded paging, retry/load-earlier/load-later and capture clearance. A daily aggregate at 00:00 must remain labeled as a saved source, not implied to be a timed walk or invented sync.
- Do not infer hydration volumes, “glass 4 of 6”, completion targets or first-glass labels from a standalone repetition. Render existing log information; omit footer quotations pending O07.

## Implementation boundaries

- `app/src/main/java/com/luma/lifeos/ui/timeline/TimelineRoute.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/timeline/EventCard.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/timeline/HabitTimelineCard.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/timeline/DayNavigation.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/timeline/TimelineViewOptions.kt` — existing integration surface.

Existing public/internal composable signatures remain compatible except additions explicitly named above. Existing repositories/view models own data and mutations; decorative components receive values and callbacks. New files named in the contract are proposed implementation files, not files already created by this documentation change. Consult the paired plan for test ownership.

## Acceptance scenarios

1. A mixed fixture of timestamped entries, equal-time logs, date-only logs and imported activity retains original order and correct open/correction targets.
2. Paging beyond the loaded window creates no duplicate items, disconnected hit targets or hidden retry controls; last item clears the FAB.
3. An empty/error day retains Search, View options and recovery; toggling usage persists across day changes/recreation.
4. Shared EventCard still opens/edits/promotes the correct record in Today, search and detail/history consumers.

## Verification and completion

Use focused existing regression coverage plus visual/manual checks for the changed surfaces. Add tests for changed behavior and meaningful state/semantics risks; do not add tests that merely assert padding or duplicate token assignments. Required font/contrast/RTL/inset checks are acceptance work, not a reason to add fake data. Record PASS/FAIL/NOT RUN and evidence in the T33 ledger. No checks are claimed to have run in this specification.

[Implementation plan](../plans/2026-10-22-t32-a-timeline-redesign.md)
