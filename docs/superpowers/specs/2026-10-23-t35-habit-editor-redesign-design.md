# T35 — Habit editor redesign specification

Date: 2026-10-23  
Status: Proposed / pending; documentation only, no implementation or acceptance claimed.  
Depends on: T30 visual baseline and existing T32-D editor components.

## Intent and reference ownership

Apply the supplied New habit composition to creation and compatible edit states while keeping the existing count-based schedule contract.

Sources: [PNG](../../luma-redesign/new-habit-light.png), [HTML](../../luma-redesign/new-habit-light.html), [shared reference brief](../../luma-redesign/README.md) and [task index](../../UI_REDESIGN_TASKS.md). PNG governs composition; HTML supplies styling intent. Current product contracts and T30 role pairs override conflicting prototype behavior, low contrast or clipped controls. Only light references were supplied; dark derives from T30.

This task owns the new reference-specific refinement of its editor. T32-D retains general editor/detail/modal infrastructure and its existing blockers; this task does not restart or declare that earlier work complete.

## Current behavior

`HabitEditorRoute.kt` already supplies name, unit, target, start date, tracking-zone text/mismatch guidance, Daily/Weekdays/Custom presets and seven weekday choices. `HabitEditorViewModel` owns validation and saved state. Names are required and limited to 100 Unicode code points; unit labels are optional and limited to 30; target is an integer from 1 through 999. Creation rejects a start before the current tracking day; start is immutable in edit mode. Existing effective-date and tracking-zone disclosures are required even though absent from the reference.

## Global constraints

- Native Android, Kotlin, Compose Material 3 and Navigation Compose; keep minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- Use T30 semantic roles and bundled fonts; support System/Light/Dark and English/French/Persian offline.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL, IME and system insets.
- Preserve route IDs, state/callback signatures, validation, draft persistence, save/delete/retry semantics and test tags.
- Required work adds no dependencies, permissions, schema changes, network services or sample records.
- Optional tasks default to deferred; documentation does not authorize their implementation.

## Visual and behavior contract

1. Order the form as tracking context; name; counting unit with the existing one-tap explanation; daily target; start date; schedule heading/presets; weekday choices; existing effective-date and tracking-zone explanations.
2. Use the shared editorial header with Close, warm low-surface rounded input groups, a large target value, full-width date pill and sage selected presets/weekdays with check/selected semantics. Use a terracotta Save action in the existing footer. Derive a unit caption from actual unit data or the existing generic repetition wording; do not imply duration measurement or a new daily reminder.
3. Use 20 dp gutters, 16 dp field radius, 24 dp section radius, 8 dp related gaps and 24 dp section gaps from the shared brief. Save is at least 56 dp high; all weekday/preset/date actions are at least 48 dp. The PNG's five-plus-two weekday arrangement is a baseline composition; wrap at narrow/large-text sizes so all seven days remain reachable.
4. Daily means all seven days; Weekdays means Monday–Friday; Custom retains the existing preset helper behavior. Toggling the final selected day must still produce the existing required-weekday validation. Do not infer a different weekend from locale or add a new frequency model.
5. Render the real tracking zone and mismatch/effective-date guidance. Use localized dates, weekday names and numbers; never hard-code Asia/Tehran or October 23. Keep loading/retry, field errors, pending disablement, dirty-close confirmation and single completion callback.
6. Apply the composition to edit mode with its existing title and disabled start-date control. Keep repository schedule versioning and effective-date calculations unchanged.

## Implementation boundaries

Paths abbreviated inside a package above are relative to that package. Existing view models and repositories retain data ownership; presentation consumes their values/callbacks. No new production interface is required.

- Modify `app/src/main/java/com/luma/lifeos/ui/habits/HabitEditorRoute.kt`: `HabitEditorScreen` presentation only; preserve its signature and route wiring.
- Reuse `ui/common/EditorTopBar.kt`, `EditorOptionalSection.kt` and `LumaUiComponents.kt` under `app/src/main/java/com/luma/lifeos/`; change a shared component only if its other consumers remain compatible.
- Preserve `app/src/main/java/com/luma/lifeos/ui/habits/HabitEditorViewModel.kt` and `core/domain/src/main/kotlin/com/luma/lifeos/domain/habit/HabitInputs.kt`.
- Copy changes, if needed, belong in all three `app/src/main/res/values{,-fr,-fa}/strings.xml` catalogs.

## Optional differences

The reference's fields and schedule controls already exist; no new habit feature is needed. A tracking-zone selector, custom increments, duration timer or changed edit effective date would require a separate optional proposal and is outside this task.

## Acceptance scenarios

- H35-1: A new form starts with the actual zone, target 1 and all seven days selected; only Save creates a habit.
- H35-2: Daily/Weekdays/Custom and manual day toggles preserve exact saved sets across recreation; no selected days, blank/101-code-point name, 31-code-point unit and target 0/1000 retain the existing errors.
- H35-3: Start-date selection preserves the chosen LocalDate; edit mode cannot change it and still explains effective dates and zone mismatch.
- H35-4: Loading, load/save failure, retry, busy Save and dirty Close/Back remain usable; no duplicate write or success callback.
- H35-5: All seven days, explanations, errors and Save are reachable in both themes, all locales, IME and the required window/font matrix.

## Verification and handoff

Run the focused existing suites only during implementation/acceptance; add meaningful tests for uncovered state/semantics risks rather than assertions mirroring colors or padding. Record PASS/FAIL/NOT RUN, build SHA, actual date/device, locale/theme/window/font/IME configuration and captures under `docs/luma-redesign/evidence/<actual-run-date>/t35/`. Add the result to T33 and the route ledger. Existing blockers retain their status. No runtime checks were run by this documentation task.

[Implementation plan](../plans/2026-10-23-t35-habit-editor-redesign.md)

