# FLOW-005 — Refine Today’s information hierarchy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for inline implementation. Use superpowers:subagent-driven-development only if delegation is explicitly selected. Steps use checkbox syntax; this documentation request does not start implementation.

**Goal:** Give Today a stable first glance for capture and daily actions, with personal entries before the collapsed daily summary.

**Architecture:** Refine Today’s existing stable lazy sections after the page/date/preview prerequisites land. Keep three direct capture choices and move entry-empty copy into Recent entries so the capture group stays in a stable position.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose/Material 3, Android lifecycle state and existing app modules; existing dependencies.

**Spec:** [FLOW-005 specification](../specs/2026-10-21-flow-005-today-information-hierarchy-design.md)

**Status:** Scoped implementation complete on 21 October 2026. Static source review complete; prerequisite integration, build, emulator, and visual verification remain pending. No Gradle commands or emulator tests were run at the user's request.

## Global Constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies.
- Localize changed copy in EN/FR/FA with matching resource names and numbered placeholders; preserve RTL and Gregorian storage/display policy.
- Keep semantic light/dark colors, interactive targets of at least 48 dp, and reachable content at 100%/130%/200% text in compact portrait and short landscape.
- Preserve private offline storage, consent boundaries, stable record/command IDs, original retry targets, accepted drafts, and Undo protections.
- Preserve unrelated edits, including the existing two-row limit and 4 dp internal gap in TodayHabitsCard.kt.
- Implement only this task and its declared prerequisites; no database migration, new provider permission, or dependency upgrade is included.

## Review Focus

Each condition maps to the matching spec acceptance case. For presentation-only conditions, its owning step records visual evidence in the same fixture; meaningful state/navigation changes use the named existing regression fixtures.

- **RF-1 → FLOW-005-AC-1:** Compare empty and populated Today fixtures with identical habit/task state. Header and capture group occupy the same position; empty copy belongs to Recent entries. Evidence owner: Task 1, Step 6.
- **RF-2 → FLOW-005-AC-2:** Activate each direct capture choice once in the fixture. The existing type callback and selected-date choice are preserved. Evidence owner: Task 1, Step 6.
- **RF-3 → FLOW-005-AC-3:** Inspect the five-record populated fixture. Capture, habits, tasks, recent entries, and summary follow the specified order. Evidence owner: Task 1, Step 6.
- **RF-4 → FLOW-005-AC-4:** Open ordinary Today content. At least the first recent-entry preview precedes the collapsed daily summary; the existing viewport acceptance remains valid. Evidence owner: Task 1, Step 6.
- **RF-5 → FLOW-005-AC-5:** Inspect existing recovery content and a wrapping capture row. Feedback is not hidden, targets remain at least 48 dp, and labels wrap without reducing type size. Evidence owner: Task 1, Step 6.

## Dependencies and integration

Requires UX-021 and FLOW-001–FLOW-004. Keep UX-022 headings and UX-020 action labels when present.

## Task 1: Refine Today’s information hierarchy

**Files and responsibility:**

- Modify: `app/src/main/java/com/luma/lifeos/ui/today/TodayScreen.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/today/TodayCaptureActions.kt`
- Inspect data ownership: `app/src/main/java/com/luma/lifeos/ui/today/TodayRoute.kt`
- Existing verification fixture: `app/src/androidTest/java/com/luma/lifeos/TodayScreenTest.kt`
- Existing verification fixture: `app/src/androidTest/java/com/luma/lifeos/TodayPersistenceTest.kt`
- Existing verification fixture: `app/src/androidTest/java/com/luma/lifeos/todo/TodayTasksSemanticsTest.kt`

**Interfaces:**

- Consumes the existing state, callbacks, and prerequisite contracts identified in the spec.
- Produces: Change internal TodayCaptureActions to TodayCaptureActions(onDateCapture: (EventType) -> Unit): Unit by removing its empty parameter. TodayScreen owns the existing empty sentence in the Empty branch. Retain all capture test tags, requestCapture logic, onCapture/onHabit/onTask callbacks, current-day-only task behavior, and the lazy section keys. No new screen state or repository interface is required.
- Does not change repository schemas, stored content, or callback target identity.

- [ ] **Step 1: Review the integrated prerequisites.** Source review found FLOW-003 present, but UX-021 and FLOW-001/002/004 are still absent. Their shared header/gutter, date notice, counted two-row habit preview, and single task-empty sentence remain pending integration. Existing two-row/4 dp habit presentation and legible track are retained.

- [x] **Step 2: Remove empty-state ownership from capture actions.** Remove the empty parameter from TodayCaptureActions and update its caller. Preserve onDateCapture, requestCapture, and all three type-specific action tags.

- [x] **Step 3: Style the direct-action group.** Use equal-priority FilledTonalButton choices in the existing wrapping FlowRow, keeping at least 48 dp targets and the existing palette. Add no modal or preferred capture type.

- [x] **Step 4: Move entry-empty copy to its section.** Render today_no_entries under Recent entries in TodayUiState.Empty and retain the today_empty tag. Keep the capture group free of entry-state-dependent introductory text.

- [ ] **Step 5: Retain the section rhythm.** Existing 12 dp item gaps, 4 dp metadata gaps, section keys, recent entries before the collapsed daily summary, route-owned current-day-only tasks, and recovery content are retained. Added a stable `empty` item key under Recent entries. Shared header/gutter integration awaits UX-021.

- [ ] **Step 6: Verify the overview integration.** Run TodayScreenTest once, preserving currentDayShortcutsOpenEachCaptureTypeWithoutADatePrompt and the existing entry-before-summary assertion. Inspect empty/populated states in one fixture for capture-position stability and record AC-1–AC-5; add no tests that restate padding constants.

The final verification step records a result for each linked acceptance case. An unexecuted condition stays pending; normal-flow evidence does not imply all recovery or adaptive conditions passed.

## Focused verification

Future targeted command (after the listed implementation and any assertion updates):

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.TodayScreenTest
```

Expected: the selected existing test cases complete with zero failures. The command is not evidence until run. Do not create a broad new test harness for spacing or copy alone.

After the integrated UI batch, run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` once to verify call sites and resources; repeat only if new changes or failures require it. Do not run the full app walkthrough again for each small visual task. Reuse the existing five-record fixtures and enter no extra demo data for this plan.

- [x] **Integration review (static only):** Compared the diff with the behavior items and ACs below; preserved unrelated untracked artifacts. An independent read-only code review found no concrete issues in the two-file product diff. `git diff --check` passed. Prerequisite integration and build/device checks remain pending. No files staged or committed; suggested message: `fix: flow-005 today information hierarchy`.

## Handoff

Deliver the changed behavior, focused evidence, and remaining limitations together. Update task status only from actual implementation and verification evidence. Product changes are limited to TodayCaptureActions.kt and TodayScreen.kt; no tests were added, changed, or run.

## Execution notes and evidence — 21 October 2026

- Ruling: Apply the FLOW-005-specific presentation changes in the current checkout and generate a commit message without creating a commit. The user requested implementation only; no branch, worktree, or unrelated prerequisite task is created. Cost: full overview integration remains pending until UX-021 and FLOW-001/002/004 land.
- Ruling: Skip Gradle, emulator, app tests, and visual checks as requested. Preserve the existing regression fixtures; do not add tests that duplicate presentation constants. Cost: compile and runtime acceptance are unverified.
- Static source review: TodayCaptureActions has one callback parameter and one updated caller. The three existing event types, localized labels, capture tags, wrapping FlowRow, and minimum 48 dp button heights remain. All choices now use FilledTonalButton's semantic default palette.
- Static source review: today_no_entries and today_empty now belong only to TodayUiState.Empty, after recent_entries and before daily_summary. Capture no longer depends on TodayUiState. Existing loading/error/retry and entry-opening paths, requestCapture/date-choice logic, TodayRoute task gating, and habit recovery content are unchanged.
- Static resource review: existing EN/FR/FA capture labels and empty copy are reused; no resource changes are needed.

| Acceptance case | Static evidence | Runtime/visual outcome |
|---|---|---|
| AC-1 | Capture has no empty parameter or state-dependent introductory text; empty copy follows Recent entries. | Empty/populated position comparison pending. |
| AC-2 | Event types, callback, requestCapture, date-choice handling, and action tags are retained. | Existing shortcut/date-choice tests not run. |
| AC-3 | Existing capture, habits, tasks, recent entries, and daily summary order and keys retained. | Five-record fixture inspection pending. |
| AC-4 | Entry items still precede the existing collapsed summary disclosure. | Existing entry-before-summary viewport assertion not run. |
| AC-5 | Recovery paths unchanged; wrapping FlowRow and minimum 48 dp capture heights retained. | EN/FR/FA, RTL, light/dark, compact/landscape, and 100%/130%/200% text inspection pending. |
