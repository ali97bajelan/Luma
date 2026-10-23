# T38 — Memory editor redesign specification

Date: 2026-10-23  
Status: Proposed / pending; documentation only, no implementation or acceptance claimed.  
Depends on: T30 visual baseline, existing T32-D editor components and T36 shared timestamp refinement.

## Intent and reference ownership

Apply the Record a memory composition to the existing direct-memory and promoted-memory paths without changing their source-record semantics.

Sources: [PNG](../../luma-redesign/new-memory-light.png), [HTML](../../luma-redesign/new-memory-light.html), [shared reference brief](../../luma-redesign/README.md) and [task index](../../UI_REDESIGN_TASKS.md). PNG governs composition; HTML supplies styling intent. Current product contracts and T30 role pairs override conflicting prototype behavior, low contrast or clipped controls. Only light references were supplied; dark derives from T30.

This task owns the new reference-specific refinement of its editor. T32-D retains general editor/detail/modal infrastructure and its existing blockers; this task does not restart or declare that earlier work complete.

## Current behavior

`MemoryEditorScreen` supports optional title for direct memories, required nonblank direct-memory description, optional 1–5 importance, timestamp, people/place/photo slots and edit/remove flows. `MemoryTextLimits` uses the existing String.length policy (title 200, note 10,000), unlike task code-point validation. New importance and context sections currently use disclosures; restored values/edit/promote/photo-error states expand them using existing rules. Promoted memories retain their source event, source preview and locked source timestamp.

## Global constraints

- Native Android, Kotlin, Compose Material 3 and Navigation Compose; keep minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- Use T30 semantic roles and bundled fonts; support System/Light/Dark and English/French/Persian offline.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL, IME and system insets.
- Preserve route IDs, state/callback signatures, validation, draft persistence, save/delete/retry semantics and test tags.
- Required work adds no dependencies, permissions, schema changes, network services or sample records.
- Optional tasks default to deferred; documentation does not authorize their implementation.

## Visual and behavior contract

1. Use editorial Close/title header, timestamp pill, optional direct-memory title, spacious description/marker-note field, restyled importance section, restyled context section, and existing Save/remove footer. The direct-create title can match “Record a memory”; retain distinct edit/promote titles.
2. Use 20 dp gutters, 16 dp field radius, 24 dp card radius, 16 dp internal padding and 24 dp section spacing. Use the shared timestamp treatment owned by T36. Keep editable body sans-serif for readability and Persian shaping, even where the reference uses a serif textarea. Save is at least 56 dp.
3. Importance remains nullable and 1–5; tapping the selected value clears it. Style expanded choices with a terracotta selection and visible/accessibility selected state. Keep current optional disclosure, summary and initialization. Never preselect 4 or attach a subjective “High value” judgment in base scope.
4. Style the existing context disclosure and expanded people/place/photos surface as a rounded group with wrapping content. Keep real picker/search/catalog results and existing photo gallery loading/removal/retry. Use actual selected names; omit Farshad/Maede/Tehran Metro and the fake Add snapshot camera promise. Use the existing photo-import action and supported label.
5. Base T38 retains current disclosure behavior; O11 optionally makes importance/context expanded initially. Do not force two extra large sections open as part of spacing work.
6. Direct create still requires nonblank description; title remains optional. Promotion preserves source content/type/ID/time and uses marker-note semantics. Editing a direct memory deletes the record only through its current confirmation; unmarking a promoted memory preserves the source event.
7. Keep text limits/error focus, photo-error auto-expansion/bring-into-view, disabled timestamp for promotion, busy/error/retry/discard behavior and saved-state restoration.
8. Omit the draft badge, prompt/word-count toolbar and High value badge in base scope. O12 defines optional writing helpers; a Draft badge must never imply durable autosave without evidence from an existing durable state.

## Implementation boundaries

Paths abbreviated inside a package above are relative to that package. Existing view models and repositories retain data ownership; presentation consumes their values/callbacks. No new production interface is required.

- Modify `app/src/main/java/com/luma/lifeos/ui/memory/MemoryEditorScreen.kt`: retain `MemoryEditorScreen` signature and people/place/photos slots.
- Reuse `app/src/main/java/com/luma/lifeos/ui/common/CaptureTimestampRow.kt` after T36, plus existing editor/optional-section/surface components.
- Preserve `MemoryEditorRoute.kt` and `MemoryEditorViewModel.kt` in the memory package and `app/src/main/java/com/luma/lifeos/ui/photos/PhotoGallery.kt` behavior.
- Preserve `core/domain/src/main/kotlin/com/luma/lifeos/domain/model/Memory.kt` and current photo-import limits.
- Copy changes belong in all three `app/src/main/res/values{,-fr,-fa}/strings.xml` catalogs.

## Optional differences

O11 changes initial disclosure behavior; O12 adds a static writing prompt and derived word count. Both are deferred. Draft durability indicators, importance coaching/classification and camera capture require separate product decisions and are not silently introduced.

## Acceptance scenarios

- R38-1: Direct create requires a description, accepts an absent title and saves exactly the entered title/note/importance/links/photos/time without sample defaults.
- R38-2: Importance selection clears on retap; current disclosure defaults and restored/edit/photo-error expansion retain values across recreation.
- R38-3: Promotion shows source content and locked timestamp; Save/unmark retains the source event and ID, while direct-delete follows the existing confirmation.
- R38-4: Title/note limits, photo failures/retry, save/remove failure, busy Save, dirty Close/Back and picker cancellation retain current behavior.
- R38-5: Description, importance, links/photo actions, error recovery and Save remain reachable in both themes, all locales, IME and the required window/font matrix; Journal and Mood timestamp consumers remain compatible.

## Verification and handoff

Run the focused existing suites only during implementation/acceptance; add meaningful tests for uncovered state/semantics risks rather than assertions mirroring colors or padding. Record PASS/FAIL/NOT RUN, build SHA, actual date/device, locale/theme/window/font/IME configuration and captures under `docs/luma-redesign/evidence/<actual-run-date>/t38/`. Add the result to T33 and the route ledger. Existing blockers retain their status. No runtime checks were run by this documentation task.

[Implementation plan](../plans/2026-10-23-t38-memory-editor-redesign.md)

