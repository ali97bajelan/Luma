# T32-D — Editors, details, sheets and dialogs specification

Date: 2026-10-22  
Status: Proposed / pending implementation. This document is a planning artifact, not release acceptance.  
Depends on: T30; consume T32-A EventCard treatment

## Intent and sources

Make capture/edit/detail surfaces and modal UI consistent while retaining drafts, validation, editing and recovery.

References: Corrected timeline-light.html contains a hidden Quick Capture modal; editor/detail screens extrapolate shared T30 tokens and editorial saved-content treatment. Read the [shared reference brief](../../luma-redesign/README.md) and [task index](../../UI_REDESIGN_TASKS.md) with this specification. The brief resolves inconsistencies and provides common tokens; it is normative for every task.

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

- Use shared editor headers, text-field roles, rounded optional sections and consistent Save/Cancel/Back actions. Keep required fields, limits, validation timing and disabled/pending behavior.
- Quick Capture remains a scrollable modal sheet with existing life-record/task grouping and now/selected-date choice; Task remains undated unless the existing caller supplies its due date. Use the corrected Timeline HTML’s rounded surface, serif title and subdued cancel treatment, but retain full-width native choices and existing Close/Back/outside dismissal. Its Water/Stretch/Thought grid does not replace Mood/Journal/Memory/Task: Thought maps only to the existing Journal action, and a new habit entry point is optional O08. Omit “Encrypted automatically” and preserve actual privacy disclosures.
- Journal/event/habit/person/place details use serif content headings, clear metadata and existing photo galleries. Keep read-only imported records distinct from editable manual records.
- Keep draft persistence, unsaved-change prompts, save-result consumption, deletion reconciliation, memory unmark semantics, retry and undo. No autosave policy or new step in capture is introduced.
- Restyle correction, selection, date/time, methodology, destructive/reset and restore dialogs through shared roles. Keep sheet scroll, IME/insets and current modal focus/dismissal behavior.
- Existing photo gallery loading/failure/retry and privacy controls remain. Feed photo previews are separately optional; detail photos are already supported and required to remain functional.

## Implementation boundaries

- `app/src/main/java/com/luma/lifeos/ui/QuickCaptureSheet.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/journal/JournalEditorScreen.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/journal/JournalDetailRoute.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorScreen.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/memory/MemoryEditorScreen.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/todo/TodoEditorScreen.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/habits/HabitEditorRoute.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/habits/HabitDetailRoute.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/habits/HabitLogCorrectionDialog.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/people/PersonEditorRoute.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/people/PeopleRoute.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/places/PlaceEditorRoute.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/places/PlacesRoute.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/timeline/EventDetailRoute.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/common/EditorTopBar.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/common/DetailTopBar.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/common/ContextOptionPicker.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/common/CaptureTimestampRow.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/common/EditorOptionalSection.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/photos/PhotoGallery.kt` — existing integration surface.

Existing public/internal composable signatures remain compatible except additions explicitly named above. Existing repositories/view models own data and mutations; decorative components receive values and callbacks. New files named in the contract are proposed implementation files, not files already created by this documentation change. Consult the paired plan for test ownership.

## Acceptance scenarios

1. Mood/journal/memory/task/habit/person/place create and edit preserve saved values, validation and drafts through recreation and Back/Cancel.
2. Imported/read-only/missing/deleted details retain correct actions and return paths; deleting a detail does not leave a stale screen.
3. Long text, IME, Persian RTL and 2.0 fonts keep Save/Cancel, photo retry, picker results and dialog confirmation reachable.
4. Quick Capture retains date selection across rotation and emits no record on dismissal; successful saves retain single existing feedback.

## Verification and completion

Use focused existing regression coverage plus visual/manual checks for the changed surfaces. Add tests for changed behavior and meaningful state/semantics risks; do not add tests that merely assert padding or duplicate token assignments. Required font/contrast/RTL/inset checks are acceptance work, not a reason to add fake data. Record PASS/FAIL/NOT RUN and evidence in the T33 ledger. No checks are claimed to have run in this specification.

[Implementation plan](../plans/2026-10-22-t32-d-editors-details-modals.md)
