# T37 — Task editor redesign specification

Date: 2026-10-23  
Status: Proposed / pending; documentation only, no implementation or acceptance claimed.  
Depends on: T30 visual baseline and existing T32-D editor components.

## Intent and reference ownership

Apply the New task field, due-date card and footer composition while preserving standalone task data and revision-based recovery.

Sources: [PNG](../../luma-redesign/new-task-light.png), [HTML](../../luma-redesign/new-task-light.html), [shared reference brief](../../luma-redesign/README.md) and [task index](../../UI_REDESIGN_TASKS.md). PNG governs composition; HTML supplies styling intent. Current product contracts and T30 role pairs override conflicting prototype behavior, low contrast or clipped controls. Only light references were supplied; dark derives from T30.

This task owns the new reference-specific refinement of its editor. T32-D retains general editor/detail/modal infrastructure and its existing blockers; this task does not restart or declare that earlier work complete.

## Current behavior

`TodoEditorScreen` has title, note and nullable due date only. `TodoTextLimits` requires a trimmed single-line title of 1–200 Unicode code points, note up to 4,000 Unicode code points and due-date years 1–9999. Creation defaults to no due date unless the caller supplies one. Edit mode includes Complete/Reopen and Delete, with pending, conflict/reload and missing-record branches. There is no task-tag relation or scheduled evening-review behavior.

## Global constraints

- Native Android, Kotlin, Compose Material 3 and Navigation Compose; keep minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- Use T30 semantic roles and bundled fonts; support System/Light/Dark and English/French/Persian offline.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL, IME and system insets.
- Preserve route IDs, state/callback signatures, validation, draft persistence, save/delete/retry semantics and test tags.
- Required work adds no dependencies, permissions, schema changes, network services or sample records.
- Optional tasks default to deferred; documentation does not authorize their implementation.

## Visual and behavior contract

1. Use the editorial Close/title header; title input with required/length hint; spacious optional note input with limit hint; low-surface rounded Due date card; existing edit-only status actions; bottom Save (and current Delete when editing).
2. Use 20 dp gutters, 16 dp field radius, 24 dp date-card radius and 24 dp section spacing. Keep the existing focused title border/cursor and auto-focus for creation; do not reproduce the PNG's clipped title label. Save is at least 56 dp high and other actions at least 48 dp.
3. The date card shows the actual localized date or No date. Show Today only when appropriate, Change through the existing picker, and Remove only for a selected date. At narrow/large-text sizes place Change/Remove below the date so they do not overlap.
4. Preserve nullable due date and LocalDate conversion, picker cancel/Back/year range and date validation. Do not assign today from the screenshot or add time-of-day, alarms, recurrence or the unsupported “Scheduled for evening review” caption.
5. Retain exact Unicode code-point validation, title Next-to-note keyboard navigation, error focus/bring-into-view and full note entry without silently truncating input. Do not replace code-point counts with UTF-16 length.
6. Keep editing Complete/Reopen, Delete and dirty-delete confirmation; busy action wording; conflict reload with dirty confirmation; missing record; retry; idempotent save/result consumption. Visual matching must not hide these states.
7. Omit the entire Tags & Context section in base T37. Focus/Personal/Work are sample chips with no current task persistence contract; O09 separately defines real tags.

## Implementation boundaries

Paths abbreviated inside a package above are relative to that package. Existing view models and repositories retain data ownership; presentation consumes their values/callbacks. No new production interface is required.

- Modify `app/src/main/java/com/luma/lifeos/ui/todo/TodoEditorScreen.kt`: retain `TodoEditorScreen` signature, autofocus, field-error focus and all modal branches.
- Reuse `app/src/main/java/com/luma/lifeos/ui/common/EditorTopBar.kt`, `EditorOptionalSection.kt` and `LumaUiComponents.kt`.
- Preserve `TodoEditorRoute.kt`, `TodoEditorViewModel.kt` and `TodoUiState.kt` in the same todo package.
- Preserve `core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoInputs.kt`, `TodoRules.kt` and `TodoRepository.kt`.
- Copy changes belong in all three `app/src/main/res/values{,-fr,-fa}/strings.xml` catalogs.

## Optional differences

O09 adds persistent task tags and is deferred. Time-of-day scheduling, reminders, People/Place links and evening-review automation are outside this visual task; the reference caption is omitted rather than shipped as a promise.

## Acceptance scenarios

- T37-1: A new task remains undated; setting/removing/canceling a due date preserves the actual LocalDate through recreation and Save.
- T37-2: Blank/line-break/201-code-point title and 4,001-code-point note retain validation and focus; 200/4,000-code-point boundaries including supplementary emoji retain current behavior.
- T37-3: Edit Complete/Reopen/Delete and dirty-discard/reload confirmations keep the same record ID, revision checks and single result delivery.
- T37-4: Loading, missing, conflict, save/status/delete failure and retry remain distinguishable; repeated pending actions create no duplicate writes.
- T37-5: Title label, hints, date actions, note, edit-only controls and Save remain reachable in both themes, all locales, IME and the required window/font matrix; no nonfunctional tag chips or scheduling caption appear.

## Verification and handoff

Run the focused existing suites only during implementation/acceptance; add meaningful tests for uncovered state/semantics risks rather than assertions mirroring colors or padding. Record PASS/FAIL/NOT RUN, build SHA, actual date/device, locale/theme/window/font/IME configuration and captures under `docs/luma-redesign/evidence/<actual-run-date>/t37/`. Add the result to T33 and the route ledger. Existing blockers retain their status. No runtime checks were run by this documentation task.

[Implementation plan](../plans/2026-10-23-t37-task-editor-redesign.md)

