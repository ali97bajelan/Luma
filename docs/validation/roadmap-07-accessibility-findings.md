# F07 — Accessibility and adaptive layout source findings

Date: 26 October 2026 · Phase 1: **SOURCE COMPLETE — QA PENDING** · Runtime validation: **NOT RUN**

[Roadmap](../FEATURE_ROADMAP_2026-10-26.md) · [Specification](../superpowers/specs/2026-10-26-roadmap-07-accessibility.md) · [Implementation plan](../superpowers/plans/2026-10-26-roadmap-07-accessibility.md) · [QA scenarios](../feature-roadmap/07-accessibility-qa.md) · [Shared workflow](../feature-roadmap/WORKFLOW.md)

These findings record source observations and the corresponding source changes. They do not assert that TalkBack, keyboard focus, rendered contrast or window behavior has passed. Every F07 QA scenario and required runtime matrix cell remains **NOT RUN**.

## Findings

| ID | Requirement and affected surface | Trigger / expected interaction | Source change | QA mapping / result |
| --- | --- | --- | --- | --- |
| A11Y-01 | F07-R01/R03 — shared editor action rows in Todo, Mood, Journal, Memory, Person and Place editors | 320 dp portrait at 200% text, and 411 dp portrait at 200% text: Save and secondary actions stay visible, separate and reachable above the IME. | `EditorActionRow` now stacks its actions when font scale is at least 1.5, as well as at narrow widths. Existing editor footers already apply IME padding. | F07-QA01, Q03, Q06 · **NOT RUN** |
| A11Y-02 | F07-R02/R04/R05 — Calendar date cells | EN/FR/FA, both themes, narrow and expanded widths: expose full date, task and memory counts, overdue meaning, today and selection without repeating the same selection label. | Date content descriptions now include localized overdue status. Today is carried in `stateDescription`, selection in the selected property, and those states are no longer repeated in the content description. | F07-QA02, Q04, Q05, Q08 · **NOT RUN** |
| A11Y-03 | F07-R02/R04 — task completion controls and habit progress rows | EN/FR/FA with mixed-direction names: each control announces its item and action once, then exposes its native checked or button state. | Task checkbox labels now use localized format strings and rely on the Checkbox's checked state. Habit progress summaries no longer repeat the habit name and use a locale-specific separator. | F07-QA02, Q03, Q04, Q08 · **NOT RUN** |
| A11Y-04 | F07-R03/R05 — Calendar task, memory and overdue markers | Light/dark screenshots and contrast review: marker categories remain distinguishable when color is unavailable. | Task markers are rounded squares, memory markers circles and overdue markers diamonds; the visible text legend remains. | F07-QA05, Q08 · **NOT RUN** |

## Shared component consumers and review decisions

- `EditorActionRow` is shared by `TodoEditorScreen`, `MoodEditorScreen`, `JournalEditorScreen`, `MemoryEditorScreen`, `PersonEditorScreen` and `PlaceEditorScreen`. All retain their existing callbacks, enabled conditions, save state and draft behavior.
- `EditorTopBar` is used by the Todo, Mood, Journal, Memory, Habit, Person and Place editors. Its close target already has a 48 dp minimum, a localized label and a scaled two-line title. These declarations were reviewed and left unchanged.
- `TodoRow` is used in Today tasks and the full Todo list. Calendar has its own task row; both completion controls now use the same localized action-label resource.
- `HabitRow` is shared by the Habits screen and Today habit card. Its log action already has an accessible label and minimum target. The progress action had a repeated habit name; its summary now avoids that repetition.
- `CalendarGrid`, `CalendarTaskRow` and memory result surfaces are composed by `CalendarScreen`. Clickable surfaces and dropdowns rely on Compose's native roles and focus handling; actual TalkBack traversal and focus return remain QA obligations.
- `AppPageScaffold`, `PageHeader`, `LumaReadingPane`, `LumaSectionHeader` and `AccessibleFeedback` were reviewed. They already provide scaled headings, constrained reading width, wrapped section actions, minimum-size navigation targets, and live feedback with error semantics. No source adjustment was warranted for those declarations in this handoff.

## Required QA and evidence

All eight cases in [F07 QA scenarios](../feature-roadmap/07-accessibility-qa.md) remain **NOT RUN**. QA must author the owning regressions and execute the applicable shared matrix in [WORKFLOW.md](../feature-roadmap/WORKFLOW.md), including:

- Screenshots/target bounds at 320 dp portrait and 200% text in EN/FR/FA and light/dark, 411 dp landscape at 200% in Persian, and at least 840 dp in EN/FA.
- Human TalkBack observation for date counts, checked/selected/disabled states, action names and focus return after menus/dialogs.
- Hardware keyboard traversal and activation for each changed editor, Calendar control and task/habit action family.
- Contrast measurement, IME visibility, action reachability, and actual launcher coverage for widgets.

Semantic source changes do not establish spoken behavior. No Gradle command, test authoring/execution, build, lint, emulator or device action was performed in this implementation phase.

## Source handoff

- Baseline Git SHA: `a955a4a2a3c1fdd6b068ce81001947692157f70e`.
- F07 tracked-source/document patch fingerprint: `5bf974c7ba64480de55d966692d03d5d08ce6d16a99712baefdc01ae059bbc75` (excludes this ledger and the pre-existing APK/evidence worktree changes).
- Existing worktree changes preserved: modified `app/release/app-release.apk` and the six untracked 25 October Calendar evidence logs under `docs/luma-redesign/evidence/`.
- No Room, archive, manifest, permission, navigation or saved-record contract changed. EN/FR/FA accessibility resources were updated together.
- QA handoff: source review and static checks only; all F07 runtime outcomes remain **NOT RUN**.
