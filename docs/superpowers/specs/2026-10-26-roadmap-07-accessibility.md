# F07 — Accessibility and adaptive layout gaps specification

Date: 26 October 2026 · Priority: **P1** · Status: **SOURCE COMPLETE — QA PENDING**

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](2026-10-26-roadmap-07-accessibility.md) · [Plan](../plans/2026-10-26-roadmap-07-accessibility.md) · [QA scenarios](../../feature-roadmap/07-accessibility-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Intent and dependencies

Make every existing core action operable with large text, RTL, assistive technology and supported window sizes.

Dependencies: Coordinate task 02 acceptance; task 07 fixes found defects and supplies focused regression evidence.

Rank and scope come from [the feature proposal](../../FEATURE_IMPROVEMENT_PROPOSAL_2026-10-26.md). Specific new defaults in this document are proposed implementation choices, not claims of user approval. Read the existing implementation at execution time; this document does not certify its current runtime behavior.

## Architecture and alternatives

Recommended approach: Improve shared Compose components and semantics first, then feature-specific layouts. Preserve established typography/theme/navigation and use window space to improve reachability rather than introduce a new visual design system.

Considered alternatives: retain the current behavior and document its limitations, or introduce an independent subsystem. Retaining behavior is acceptable only for an explicitly documented investigation/deferred outcome; otherwise it does not meet the intent above. An independent replacement would duplicate existing persistence/navigation and recovery paths; extend the current module contracts unless a specific requirement makes reuse impossible. The recommendation is proposed, not a recorded user selection.

## Existing source and handoff material

- [app/src/main/java/com/luma/lifeos/ui/common/AppPageScaffold.kt](../../../app/src/main/java/com/luma/lifeos/ui/common/AppPageScaffold.kt)
- [app/src/main/java/com/luma/lifeos/ui/common/EditorTopBar.kt](../../../app/src/main/java/com/luma/lifeos/ui/common/EditorTopBar.kt)
- [app/src/main/java/com/luma/lifeos/ui/common/LumaUiComponents.kt](../../../app/src/main/java/com/luma/lifeos/ui/common/LumaUiComponents.kt)
- [app/src/main/java/com/luma/lifeos/ui/common/AccessibleFeedback.kt](../../../app/src/main/java/com/luma/lifeos/ui/common/AccessibleFeedback.kt)
- [app/src/main/java/com/luma/lifeos/ui/calendar/CalendarScreen.kt](../../../app/src/main/java/com/luma/lifeos/ui/calendar/CalendarScreen.kt)
- [app/src/main/java/com/luma/lifeos/ui/todo/TodoRow.kt](../../../app/src/main/java/com/luma/lifeos/ui/todo/TodoRow.kt)
- [app/src/main/java/com/luma/lifeos/ui/theme/LumaTypography.kt](../../../app/src/main/java/com/luma/lifeos/ui/theme/LumaTypography.kt)
- [docs/UI_REDESIGN_TASKS.md](../../UI_REDESIGN_TASKS.md)
- [docs/CALENDAR_QA.md](../../CALENDAR_QA.md)



## Contracts and ownership

Names below are proposed interfaces to create or adapt, not claims that these declarations already exist. Keep existing public callers working or migrate every caller explicitly in the owning change. Resolve exact current types while preserving these behaviors.

- Keep existing editor callbacks and navigation contracts; improvements must not change saved record values.
- Each finding has ID A11Y-nn, affected surface, triggering matrix cell, expected interaction, source fix, and linked QA scenario. Unobserved risk remains a candidate finding, not an asserted runtime defect.

## Requirements

### F07-R01 — Reachability

At 320 dp portrait and font scale 2.0, all save/cancel/retry/confirm/Undo and last-list actions remain reachable above IME/system bars. Allow vertical flow/scroll instead of truncating essential controls.

### F07-R02 — Semantics and focus

Expose role, label, state, selected date, counts and error meaning without duplicate spoken labels. Keep logical traversal, restore focus after dialogs and announce save/error feedback once. Color is not the only status indicator.

### F07-R03 — Input and contrast

Interactive targets are at least 48 dp. Normal text contrast >=4.5:1; large text and essential UI graphics >=3:1. Keyboard/D-pad operations can reach and activate the same actions as touch; destructive confirmation has an accessible cancel route.

### F07-R04 — Locale and adaptation

Verify EN/FR/FA including mixed-direction user text, locale date/time labels, 12/24-hour settings, light/dark, landscape and >=840 dp windows. Date values, selected filters and saved drafts survive rotation/resize/language changes.

### F07-R05 — Coverage and evidence

Cover Today, Timeline, Calendar, Review, More, capture/edit/detail, tasks/habits, search, widgets, backup and privacy. Keep actual launcher output and TalkBack human observation separate from Compose semantics automation. No global warning suppressions substitute for fixes.

## Two-phase delivery contract

**Phase 1 — Implementation:** implement the source/configuration/resources/documentation described in the plan, review the source and diff, and record the handoff. **No Gradle command, sync, dependency resolution, build, schema generation, lint, automated test execution, emulator launch or device test. Test authoring is deferred to QA.** Source checks do not prove compilation or correctness.

**Phase 2 — QA:** author tests/fixtures, generate and inspect schemas where relevant, run Gradle/build/lint/automated suites, execute native/manual cases, correct discovered defects and record evidence. Begin only after this task’s implementation source handoff; all initial outcomes are NOT RUN. Follow the [shared workflow](../../feature-roadmap/WORKFLOW.md) for isolation, matrix, commands, evidence and completion gates.

## Scope boundaries

No new navigation structure, broad visual rebrand or changes to Calendar membership/date systems.

## Acceptance and traceability

All five requirements must map to implementation steps and QA outcomes. [The QA document](../../feature-roadmap/07-accessibility-qa.md) provides executable scenarios with fixtures/actions/oracles; [the plan](../plans/2026-10-26-roadmap-07-accessibility.md) owns concrete files and implementation/QA checklists. Both phases start pending. A source-complete task is not feature-complete. For assessment/deferred branches, report the delivered investigation separately from feature/runtime acceptance.
