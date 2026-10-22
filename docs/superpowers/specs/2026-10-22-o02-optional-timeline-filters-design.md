# O02 — Optional — Timeline event-type filters specification

Date: 2026-10-22  
Status: Optional / deferred; requires a separate scope decision before implementation.  
Depends on: T32-A

## Intent and sources

Add functional All events, Reflections, Habits and Health filters over the full paged feed.

References: timeline-light.png; timeline-dark PNG/HTML filter chips. Read the [shared reference brief](../../luma-redesign/README.md) and [task index](../../UI_REDESIGN_TASKS.md) with this specification. The brief resolves inconsistencies and provides common tokens; it is normative for every task.

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

- Introduce a single-selection `TimelineFilter` enum: ALL, REFLECTIONS, HABITS, HEALTH. Reflections includes manual mood/journal/memory entries; HABITS standalone habit logs; HEALTH imported Health Connect activity only. Existing app usage remains available only under ALL when its persisted switch is on.
- Apply the filter at repository query/count/window level before paging, not to currently loaded rows. Extend TimelineRepository.feed and implementations using the same enum contract; default ALL keeps every existing caller compatible.
- Store selected filter in SavedStateHandle, retain across day navigation/recreation and reset paging on change. Counts describe the filtered query; keep all-events counts separate if shown, labeled explicitly.
- Type-specific empty/retry states retain visible filter controls and an All events escape. No sample sync event or hydration-specific type is introduced.

## Implementation boundaries

- `app/src/main/java/com/luma/lifeos/ui/timeline/TimelineRoute.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/timeline/TimelineViewModel.kt` — existing integration surface.

Existing public/internal composable signatures remain compatible except additions explicitly named above. Existing repositories/view models own data and mutations; decorative components receive values and callbacks. New files named in the contract are proposed implementation files, not files already created by this documentation change. Consult the paired plan for test ownership.

### Additional interfaces and data files

Create `core/domain/src/main/kotlin/com/luma/lifeos/domain/habit/TimelineFilter.kt` with the enum defined above. Extend the existing interface in `core/domain/src/main/kotlin/com/luma/lifeos/domain/repository/TimelineRepository.kt` to:

```kotlin
suspend fun feed(date: LocalDate, zone: ZoneId, offset: Int, limit: Int,
    showAppUsage: Boolean = true, filter: TimelineFilter = TimelineFilter.ALL): TimelineFeedPage
```

Update `core/data/src/main/kotlin/com/luma/lifeos/data/habit/HabitTimelineSql.kt` and `core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomTimelineRepository.kt`, plus every fake/default implementation. The default feed implementation cannot filter a previously paged result: it must support complete matching paging/counts or explicitly reject unsupported non-ALL filters; production Room supports every enum value. Add `fun setFilter(filter: TimelineFilter)` to TimelineViewModel; save its enum name in `SavedStateHandle["timeline_filter"]`, treating unknown restored values as ALL. Counts eventCount/habitLogCount refer to matched non-usage entries/logs; totalCount also includes usage when ALL and enabled. Retain date-only grouping and all query bounds.

## Acceptance scenarios

1. A matching record outside the initial 150-row window is found by the appropriate filter; total count and page controls are correct.
2. Rapid day/filter changes never show stale results; app-usage preference remains independent and restored correctly.

## Verification and completion

Use focused existing regression coverage plus visual/manual checks for the changed surfaces. Add tests for changed behavior and meaningful state/semantics risks; do not add tests that merely assert padding or duplicate token assignments. Required font/contrast/RTL/inset checks are acceptance work, not a reason to add fake data. Record PASS/FAIL/NOT RUN and evidence in the T33 ledger. No checks are claimed to have run in this specification.

[Implementation plan](../plans/2026-10-22-o02-optional-timeline-filters.md)
