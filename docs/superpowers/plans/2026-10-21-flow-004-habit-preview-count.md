# FLOW-004 — Show the scheduled habit count in Today’s preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for inline implementation. Use superpowers:subagent-driven-development only if delegation is explicitly selected. Steps use checkbox syntax; this documentation request does not start implementation.

**Goal:** Help a user discover scheduled habits beyond Today’s two-row preview without expanding the whole card.

**Architecture:** Compute the scheduled group once, retain the two-row cap, and derive the header count only from current loaded data. Use the same visible ID set for row rendering and retained-action placement.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose/Material 3, Android lifecycle state and existing app modules; existing dependencies.

**Spec:** [FLOW-004 specification](../specs/2026-10-21-flow-004-habit-preview-count-design.md)

**Status:** Draft plan; all implementation and verification steps remain unchecked.

## Global Constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies.
- Localize changed copy in EN/FR/FA with matching resource names and numbered placeholders; preserve RTL and Gregorian storage/display policy.
- Keep semantic light/dark colors, interactive targets of at least 48 dp, and reachable content at 100%/130%/200% text in compact portrait and short landscape.
- Preserve private offline storage, consent boundaries, stable record/command IDs, original retry targets, accepted drafts, and Undo protections.
- Preserve unrelated edits, including the existing two-row limit and 4 dp internal gap in TodayHabitsCard.kt.
- Implement only this task and its declared prerequisites; no database migration, new provider permission, or dependency upgrade is included.

## Review Focus

Each condition maps to the matching spec acceptance case. For presentation-only conditions, its owning step records visual evidence in the same fixture; meaningful state/navigation changes use the named existing regression fixtures.

- **RF-1 → FLOW-004-AC-1:** Use five scheduled habits in a loaded Today fixture. Two rows show and the View all label names five scheduled habits. Evidence owner: Task 1, Step 7.
- **RF-2 → FLOW-004-AC-2:** Mix older unscheduled and scheduled habits. Only scheduled rows and their count contribute to the preview. Evidence owner: Task 1, Step 7.
- **RF-3 → FLOW-004-AC-3:** Load another selected date or show its load error. No previous-date count appears as current. Evidence owner: Task 1, Step 7.
- **RF-4 → FLOW-004-AC-4:** Retain a pending action for the third scheduled habit. It stays in retained actions even though that habit is outside the preview. Evidence owner: Task 1, Step 7.
- **RF-5 → FLOW-004-AC-5:** Activate the counted View all action. The existing full Habits destination opens with the selected date context. Evidence owner: Task 1, Step 7.

## Dependencies and integration

None. Preserve the existing local two-row preview change; FLOW-005 uses this header.

## Task 1: Show the scheduled habit count in Today’s preview

**Files and responsibility:**

- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/TodayHabitsCard.kt`
- Inspect group contract: `app/src/main/java/com/luma/lifeos/ui/habits/HabitBrowseGroups.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-fr/strings.xml`
- Modify: `app/src/main/res/values-fa/strings.xml`
- Existing verification fixture: `app/src/androidTest/java/com/luma/lifeos/habits/HabitCaptureFlowTest.kt`

**Interfaces:**

- Consumes the existing state, callbacks, and prerequisite contracts identified in the spec.
- Produces: Keep TodayHabitsCard’s public signature. Introduce internal const val TODAY_HABIT_PREVIEW_LIMIT = 2 in TodayHabitsCard.kt, compute scheduled once, and reuse scheduled.take(TODAY_HABIT_PREVIEW_LIMIT) for rendering and visible IDs. Add habit_view_all_count (%1$s) and habit_view_all_scheduled_accessibility (%1$s count, %2$s date) in EN/FR/FA. Preserve habits_view_all and existing retained-action tags.
- Does not change repository schemas, stored content, or callback target identity.

- [ ] **Step 1: Update the existing preview regression.** Rename todayPreviewShowsScheduledRowsBeforeOlderUnscheduledHabitsAndCapsAtThree to todayPreviewShowsScheduledRowsBeforeOlderUnscheduledHabitsAndCapsAtTwo. Increase the mixed fixture to five scheduled rows and assert exactly two visible rows, the displayed count of five, and the existing View all callback.

- [ ] **Step 2: Run the focused case before implementation.** Use the command below to record the missing-count mismatch against the existing source. Preserve the known local two-row change; do not restore three rows to make the old assertion pass.

- [ ] **Step 3: Centralize preview row selection.** Add TODAY_HABIT_PREVIEW_LIMIT = 2, compute scheduled once, and reuse its two-row slice for visible rows and visibleHabitIds. Preserve the 4 dp card gap.

- [ ] **Step 4: Add localized counted labels.** Add habit_view_all_count and habit_view_all_scheduled_accessibility with the exact placeholder meanings from the spec in EN/FR/FA. Use localized count/date formatting.

- [ ] **Step 5: Apply the current-data guard.** Show the count only when !state.loading && state.loadedDate == shownDate && state.error != HabitUiError.LOAD && scheduled.isNotEmpty(); otherwise keep generic View all. The accessible label names the shown date and scheduled meaning.

- [ ] **Step 6: Preserve retained feedback.** Keep off-preview and other-date pending actions visible using the same visible-ID set. Update exact-text View all assertions only for the counted loaded Today preview.

- [ ] **Step 7: Verify the focused preview.** Run the single updated preview method and record AC-1–AC-5 results. Open Today with the five scheduled fixture rows and activate View all once; confirm label wrapping and the existing 48 dp target.

The final verification step records a result for each linked acceptance case. An unexecuted condition stays pending; normal-flow evidence does not imply all recovery or adaptive conditions passed.

## Focused verification

Future targeted command (after the listed implementation and any assertion updates):

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.habits.HabitCaptureFlowTest#todayPreviewShowsScheduledRowsBeforeOlderUnscheduledHabitsAndCapsAtTwo
```

Expected: the selected existing test cases complete with zero failures. The command is not evidence until run. Do not create a broad new test harness for spacing or copy alone.

After the integrated UI batch, run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` once to verify call sites and resources; repeat only if new changes or failures require it. Do not run the full app walkthrough again for each small visual task. Reuse the existing five-record fixtures and enter no extra demo data for this plan.

- [ ] **Integration review:** Compare the diff with every behavior item and AC in the spec. Preserve the unrelated dirty tree and record actual build/device evidence and pending checks. Stage only this task's hunks if the selected execution workflow uses commits; suggested message: `fix: flow-004 habit preview count`.

## Handoff

Deliver the changed behavior, focused evidence, and remaining limitations together. Update task status only from actual implementation and verification evidence. This plan is ready for review; no product code or tests were changed by its creation.
