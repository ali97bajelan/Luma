# T36 — Mood check-in redesign specification

Date: 2026-10-23  
Status: Proposed / pending; documentation only, no implementation or acceptance claimed.  
Depends on: T30 visual baseline and existing T32-D timestamp/editor components.

## Intent and reference ownership

Apply the Mood check-in hierarchy, score scale and emotion chips to the existing mood editor without turning a mood record into a journal record.

Sources: [PNG](../../luma-redesign/new-mood-light.png), [HTML](../../luma-redesign/new-mood-light.html), [shared reference brief](../../luma-redesign/README.md) and [task index](../../UI_REDESIGN_TASKS.md). PNG governs composition; HTML supplies styling intent. Current product contracts and T30 role pairs override conflicting prototype behavior, low contrast or clipped controls. Only light references were supplied; dark derives from T30.

This task owns the new reference-specific refinement of its editor. T32-D retains general editor/detail/modal infrastructure and its existing blockers; this task does not restart or declare that earlier work complete.

## Current behavior

`MoodEditorScreen` already supports a required 1–5 score, repository-provided multi-select emotions, optional energy/stress/note and people/place slots behind a More details disclosure. Notes retain `MoodNotePolicy.LIMIT = 5_000` and its existing counting/validation policy. Editing/restored links initialize the disclosure using current rules. Save requires a selected score and a durable draft with no persistence pending; draft storage/delete recovery has dedicated visible states.

## Global constraints

- Native Android, Kotlin, Compose Material 3 and Navigation Compose; keep minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- Use T30 semantic roles and bundled fonts; support System/Light/Dark and English/French/Persian offline.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL, IME and system insets.
- Preserve route IDs, state/callback signatures, validation, draft persistence, save/delete/retry semantics and test tags.
- Required work adds no dependencies, permissions, schema changes, network services or sample records.
- Optional tasks default to deferred; documentation does not authorize their implementation.

## Visual and behavior contract

1. Order the required surface as editorial Close/title header; timestamp pill; “How do you feel?” with endpoint descriptions; five score choices; emotions heading/help; wrapping emotion chips; restyled existing More details/context section; Save footer.
2. Scores are numeric 1–5 with localized verbal descriptions and single-selection semantics. The selected choice gets a terracotta fill/outline cue and an accessible selected state. Do not initialize score 4 from the mockup or clear a required score on a second tap.
3. Emotion labels and IDs come from `state.emotions` and selected IDs from the draft. Use wrapping outlined pills with a visible check for selected values. Use one consistent selected role pair; Calm and Grateful do not receive inferred emotional categories or permanent per-emotion colors.
4. Restyle the existing timestamp component as a low-surface rounded pill with clock cue and Change. Keep both existing timestamp actions, date/time/timezone dialog, local 12/24-hour formatting, seconds-preservation behavior and time error/recovery.
5. Keep the current More details default, restoration and toggle semantics. Style its expanded note/energy/stress/people/place content as a rounded context card. Do not replace structured energy/stress/links with free text. The mockup's always-visible note placement is optional O10.
6. Keep the 5,000-character note policy, recovery preview, draft-storage error/retry, delete preparation/reconciliation, Save durability checks and discard confirmation. An editable note remains sans-serif body text. Omit “Reflective Journal entry” and its decorative edit affordance; Save creates only the current mood record.
7. Use the common 20 dp gutter, 24 dp section spacing, 16 dp input/score radius and minimum 56 dp Save. Wrap score descriptions/actions as needed at large text; preserve all five values and 48 dp targets.

## Implementation boundaries

Paths abbreviated inside a package above are relative to that package. Existing view models and repositories retain data ownership; presentation consumes their values/callbacks. No new production interface is required.

- Modify `app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorScreen.kt`: `MoodEditorScreen`, `OptionalScale` and recovery presentation.
- Modify `app/src/main/java/com/luma/lifeos/ui/common/CaptureTimestampRow.kt` only for compatible presentation shared with Memory/Journal; T36 owns this shared pill refinement.
- Reuse `app/src/main/java/com/luma/lifeos/ui/mood/MoodUi.kt`, existing top bar/action/field/surface components and `MoodEditorRoute.kt` slots.
- Preserve `MoodEditorViewModel.kt`, `FileMoodDraftStore.kt` and `MoodDraftStore.kt` in the same mood package.
- Copy changes belong in all three `app/src/main/res/values{,-fr,-fa}/strings.xml` catalogs.

## Optional differences

O10 alone moves the existing note outside the disclosure. Base T36 retains the current disclosure. New journal creation/shortcut behavior, custom emotion categories and extra photo support are not required by these references and are not included.

## Acceptance scenarios

- M36-1: No mock score/emotion is preselected; selecting score 4 and two actual emotion IDs saves exactly those values, preserving the same timestamp.
- M36-2: More details opens/closes without losing note, energy, stress or links; editing/recreation follows current expansion rules.
- M36-3: Missing score, note limit, loading/time/save/delete failures and draft durability/recovery retain current validation and actionable feedback.
- M36-4: Change/Cancel timestamp and dirty Close/Back preserve the original record/draft; repeated pending Save writes once and never creates a journal entry.
- M36-5: All scores/chips, optional scales, link pickers, errors and Save remain accessible in both themes, all locales, IME and the required window/font matrix. Shared timestamp changes also preserve Journal/Memory behavior.

## Verification and handoff

Run the focused existing suites only during implementation/acceptance; add meaningful tests for uncovered state/semantics risks rather than assertions mirroring colors or padding. Record PASS/FAIL/NOT RUN, build SHA, actual date/device, locale/theme/window/font/IME configuration and captures under `docs/luma-redesign/evidence/<actual-run-date>/t36/`. Add the result to T33 and the route ledger. Existing blockers retain their status. No runtime checks were run by this documentation task.

[Implementation plan](../plans/2026-10-23-t36-mood-editor-redesign.md)

