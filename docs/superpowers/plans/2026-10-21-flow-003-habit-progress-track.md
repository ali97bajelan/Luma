# FLOW-003 — Make habit progress visually legible Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for inline implementation. Use superpowers:subagent-driven-development only if delegation is explicitly selected. Steps use checkbox syntax; this documentation request does not start implementation.

**Goal:** Make a habit’s progress look like progress at zero and at partial completion while keeping the exact numeric count readable.

**Architecture:** Retain the existing Material 3 LinearProgressIndicator and numeric progress contract. Confirm the observed build, then make the unfilled track contrast explicit without changing fraction calculations or dependencies.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose/Material 3, Android lifecycle state and existing app modules; existing dependencies.

**Spec:** [FLOW-003 specification](../specs/2026-10-21-flow-003-habit-progress-track-design.md)

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

- **RF-1 → FLOW-003-AC-1:** Inspect an ordinary 0/8 habit. A full horizontal track is visible, rather than only an isolated dot. Evidence owner: Task 1, Step 5.
- **RF-2 → FLOW-003-AC-2:** Inspect the demo Drink water value 1/8. The numeric count and visible filled fraction agree. Evidence owner: Task 1, Step 5.
- **RF-3 → FLOW-003-AC-3:** Inspect a complete habit fixture. The track is full and numeric count/target remain readable. Evidence owner: Task 1, Step 5.
- **RF-4 → FLOW-003-AC-4:** Inspect the same fixture under semantic light and dark palettes. Track/card contrast meets 3:1 and progress does not rely on color alone. Evidence owner: Task 1, Step 5.
- **RF-5 → FLOW-003-AC-5:** Open/log the same habit once and inspect semantics. Progress range, readable count, row-open action, and +1 action retain distinct meaning. Evidence owner: Task 1, Step 5.

## Dependencies and integration

None. FLOW-005 consumes the resulting compact progress presentation.

## Task 1: Make habit progress visually legible

**Files and responsibility:**

- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/HabitProgress.kt`
- Inspect palettes; do not rebrand: `app/src/main/java/com/luma/lifeos/ui/theme/Theme.kt`
- Inspect consumers: `app/src/main/java/com/luma/lifeos/ui/habits/HabitsScreen.kt`
- Existing verification fixture: `app/src/androidTest/java/com/luma/lifeos/habits/HabitCaptureFlowTest.kt`

**Interfaces:**

- Consumes the existing state, callbacks, and prerequisite contracts identified in the spec.
- Produces: Keep internal HabitProgressContent(day: HabitDay) and habitProgressFraction(count: Int, target: Int): Float. Add testTag("habit_progress_track_${day.habit.id}") to the existing indicator. Configure its supported color and trackColor parameters explicitly; do not require a new Material dependency or introduce a custom drawing implementation. Preserve parent habit_progress_<id> identity and count description.
- Does not change repository schemas, stored content, or callback target identity.

- [ ] **Step 1: Compare a matching build.** Inspect HabitProgressContent in a build from the checked-out source using the existing component fixture. Record whether the observed isolated dot persists and whether the full track is already present.

- [ ] **Step 2: Set explicit semantic track colors.** Keep the existing fraction and 4 dp height; set filled color to primary and unfilled trackColor to outline using supported current Material APIs. Preserve all numeric text.

- [ ] **Step 3: Keep one progress identity.** Add habit_progress_track_<id> to the indicator. Retain Material’s determinate range semantics and the existing readable count description without a duplicate conflicting range on the parent.

- [ ] **Step 4: Measure the same visual fixture.** Inspect zero, partial, and complete rows together in the light/dark palettes and record at least 3:1 track/card contrast. Do not create unit tests for color constants.

- [ ] **Step 5: Verify action separation.** Check the existing open/log targets and numeric progress in the same fixture and record AC-1–AC-5 outcomes. Run an existing habit action/48 dp assertion only if that behavior changed; no broad app walkthrough is required.

The final verification step records a result for each linked acceptance case. An unexecuted condition stays pending; normal-flow evidence does not imply all recovery or adaptive conditions passed.

## Focused verification

Use the single component/visual fixture in Step 3. No automated test is added merely to restate static colors, spacing, or the existing fraction function. Run an existing action/semantics case only if that behavior was changed.

After the integrated UI batch, run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` once to verify call sites and resources; repeat only if new changes or failures require it. Do not run the full app walkthrough again for each small visual task. Reuse the existing five-record fixtures and enter no extra demo data for this plan.

- [ ] **Integration review:** Compare the diff with every behavior item and AC in the spec. Preserve the unrelated dirty tree and record actual build/device evidence and pending checks. Stage only this task's hunks if the selected execution workflow uses commits; suggested message: `fix: flow-003 habit progress track`.

## Handoff

Deliver the changed behavior, focused evidence, and remaining limitations together. Update task status only from actual implementation and verification evidence. This plan is ready for review; no product code or tests were changed by its creation.
