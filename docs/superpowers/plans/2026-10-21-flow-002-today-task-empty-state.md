# FLOW-002 — Remove duplicate empty-task messaging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for inline implementation. Use superpowers:subagent-driven-development only if delegation is explicitly selected. Steps use checkbox syntax; this documentation request does not start implementation.

**Goal:** Let a user understand an empty Today task preview with one status sentence and one Add task action.

**Architecture:** Use the existing TodayTasksState.Data fields to choose either the ordinary empty sentence or the positive total count. Keep task rows, Add task, View all, and retained recovery rendering in their current owners.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose/Material 3, Android lifecycle state and existing app modules; existing dependencies.

**Spec:** [FLOW-002 specification](../specs/2026-10-21-flow-002-today-task-empty-state-design.md)

**Status:** Implementation complete; focused instrumentation, build, and emulator verification pending per user request.

## Global Constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies.
- Localize changed copy in EN/FR/FA with matching resource names and numbered placeholders; preserve RTL and Gregorian storage/display policy.
- Keep semantic light/dark colors, interactive targets of at least 48 dp, and reachable content at 100%/130%/200% text in compact portrait and short landscape.
- Preserve private offline storage, consent boundaries, stable record/command IDs, original retry targets, accepted drafts, and Undo protections.
- Preserve unrelated edits, including the existing two-row limit and 4 dp internal gap in TodayHabitsCard.kt.
- Implement only this task and its declared prerequisites; no database migration, new provider permission, or dependency upgrade is included.

## Review Focus

Each condition maps to the matching spec acceptance case. For presentation-only conditions, its owning step records visual evidence in the same fixture; meaningful state/navigation changes use the named existing regression fixtures.

- **RF-1 → FLOW-002-AC-1:** Render the ordinary zero-task Data state. One empty status is shown; no redundant zero count is present. Evidence owner: Task 1, Step 6.
- **RF-2 → FLOW-002-AC-2:** Open the empty Today preview. Exactly one Add task action is present in the card and opens the existing editor. Evidence owner: Task 1, Step 6.
- **RF-3 → FLOW-002-AC-3:** Use a populated preview with more eligible tasks than visible rows. The count remains the full eligible total. Evidence owner: Task 1, Step 6.
- **RF-4 → FLOW-002-AC-4:** Render a positive total with no currently visible rows. The positive count and View all remain; zero-task copy is absent. Evidence owner: Task 1, Step 6.
- **RF-5 → FLOW-002-AC-5:** Render a zero-task preview with retained task feedback. Feedback stays available and its existing callback identity is unchanged. Evidence owner: Task 1, Step 6.

## Dependencies and integration

None. Coordinate TodayTasksCard.kt changes with FLOW-005 after this task lands.

## Task 1: Remove duplicate empty-task messaging

**Files and responsibility:**

- Modify: `app/src/main/java/com/luma/lifeos/ui/todo/TodayTasksCard.kt`
- Inspect; preserve selection: `app/src/main/java/com/luma/lifeos/ui/todo/TodayTasksState.kt`
- Existing verification fixture: `app/src/androidTest/java/com/luma/lifeos/todo/TodayTasksSemanticsTest.kt`
- Existing verification fixture: `app/src/androidTest/java/com/luma/lifeos/todo/TodayTasksFlowTest.kt`

**Interfaces:**

- Consumes the existing state, callbacks, and prerequisite contracts identified in the spec.
- Produces: Preserve TodayTasksCard’s signature and TodayTasksState.Data. Change only rendering guards: show count when state.total > 0 and show empty copy only when state.total == 0 && state.tasks.isEmpty(). Keep today_tasks_empty, today_tasks_add, today_tasks_all, and recovery tags; today_tasks_count exists only for a positive total.
- Does not change repository schemas, stored content, or callback target identity.

- [x] **Step 1: Inspect the ordinary empty fixture.** Reviewed the existing Today flow fixture and card rendering: it expects the empty sentence and Add action, and has no assertion for the zero-count node.

- [x] **Step 2: Guard the total count.** Render today_tasks_count only when state.total > 0. Keep localized positive totals and the existing preview selection.

- [x] **Step 3: Guard the empty sentence.** Render today_tasks_empty only when state.total == 0 and state.tasks.isEmpty(). Preserve the one bottom Add task action and View all.

- [x] **Step 4: Preserve recovery placement.** Reviewed the off-preview pending/error region; it remains independent of count/empty guards. All task IDs and callbacks are unchanged.

- [x] **Step 5: Update stale existing assertions only.** No zero-count assertion exists in the current Today task fixtures, so no test assertion needed updating. Existing positive-total assertions remain unchanged.

- [ ] **Step 6: Verify the card once.** Run the listed semantics suite only if assertions changed, inspect one ordinary empty preview and one populated fixture, and record AC-1–AC-5 results. Use Add task/View all once where needed; do not re-walk all task or failure paths.

The final verification step records a result for each linked acceptance case. An unexecuted condition stays pending; normal-flow evidence does not imply all recovery or adaptive conditions passed.

## Focused verification

Future targeted command (after the listed implementation and any assertion updates):

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.todo.TodayTasksSemanticsTest
```

Expected: the selected existing test cases complete with zero failures. The command is not evidence until run. Do not create a broad new test harness for spacing or copy alone.

After the integrated UI batch, run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` once to verify call sites and resources; repeat only if new changes or failures require it. Do not run the full app walkthrough again for each small visual task. Reuse the existing five-record fixtures and enter no extra demo data for this plan.

- [x] **Integration review:** Compared the implementation with the behavior items and acceptance scenarios. The initial untracked release APK, baseline profiles, and widget evidence archive were preserved. Gradle instrumentation, build, and emulator checks were not run per user request and remain pending.

## Handoff

Deliver the changed behavior and remaining verification status together. The card rendering guards are implemented. The focused instrumentation case, integrated build/lint, and emulator checks remain pending because the user requested implementation only.
