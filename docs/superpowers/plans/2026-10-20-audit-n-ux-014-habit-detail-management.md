# UX-014 — Habit management sits after all loaded history Implementation Plan

> **For agentic workers:** Implementation is complete. Use the Status field below for remaining verification work. Steps use checkbox syntax for tracking. No Gradle or emulator checks were run at the user's request.

**Goal:** Keep habit management near the start of detail and render long history as individual lazy records.

**Architecture:** Place Edit in the existing pinned detail bar and a compact schedule/Manage disclosure before history. Convert HabitHistory content into LazyListScope items with stable keys.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose/Material 3, lifecycle StateFlow and the existing Android/Room modules; existing dependencies.

**Spec:** [UX-014 specification](../specs/2026-10-20-audit-n-ux-014-habit-detail-management-design.md)

**Status:** Implementation complete; Gradle and emulator verification pending at the user's request. Regression/evidence and commit steps remain unchecked. Relative effort: Medium.

## Global Constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies unless a task explicitly requires a reviewed exception.
- All changed user-facing copy must exist in EN/FR/FA with matching resource names and numbered placeholder signatures; preserve RTL and intended Gregorian display/storage behavior.
- Keep semantic light/dark palettes, at least 48 dp interactive targets, and reachability at 100%/130%/200% font, narrow portrait and short landscape.
- Preserve offline/private storage, existing consent boundaries, stable record/command IDs, original retry payloads and Undo protections.
- Never truncate accepted personal content, infer write failure means no commit, or treat an unavailable privacy mode as known.
- Only implement this task and its declared prerequisites; preserve unrelated working-tree changes and existing navigation/draft state.

## Review Focus

These five concrete failure modes are pinned to the regression cases owned by this task. The assertions below are requirements for tests to add, not claims that those tests exist or currently pass.

- **RF-1 → AC-1:** Load and scroll through 500 historical repetitions. Edit remains reachable in the pinned header without scrolling to the end. Covered by `editStaysPinned` in the regression step below.
- **RF-2 → AC-2:** Open detail with at least 50 logs. Manage/schedule appears before the historical list. Covered by `managePrecedesHistory` in the regression step below.
- **RF-3 → AC-3:** Load another history page while a row has focus. Existing row identities/position stay stable and off-screen rows are not all composed eagerly. Covered by `historyUsesStableLazyRows` in the regression step below.
- **RF-4 → AC-4:** Open Manage then Delete/Pause/Archive. Existing confirmation and busy guards remain; pinned Edit does not bypass them. Covered by `destructiveActionsStayConfirmed` in the regression step below.
- **RF-5 → AC-5:** Correct a record after loading older pages. The original log UUID is used; paging counts and Undo behavior remain correct. Covered by `correctionStillTargetsExactLog` in the regression step below.

---

## Task 1: Deliver UX-014

**Files and responsibilities:**

- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/HabitDetailRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/HabitHistory.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/HabitManagementSection.kt`
- Extend test: `app/src/androidTest/java/com/luma/lifeos/habits/HabitsNavigationTest.kt`
- Extend test: `app/src/androidTest/java/com/luma/lifeos/habits/HabitCaptureFlowTest.kt`
- Localized resources, only when copy changes: `app/src/main/res/values/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fr/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fa/strings.xml`

Keep changes in their current screen/state/repository responsibility. New files own only the interface or component named in the specification. If a listed existing file has changed since this plan, reconcile with the live code before editing; do not revert user changes to match the audit.

**Interfaces:**

- Consumes the current route/ViewModel/repository contracts in the files listed above; no new cross-task API is required.
- Produces: Extract fun LazyListScope.habitHistoryItems(logs: List<HabitLog>, total: Int, selectedLogId: UUID?, screenZone: ZoneId, onEdit: (HabitLog) -> Unit, onDelete: (UUID) -> Unit, onLoadMore: () -> Unit, historyError: Boolean = false): Unit. It replaces the eager history Column with a heading item, per-log UUID-keyed items, an error item, and a load-more item; retain existing row callbacks and tags. HabitDetailScreen supplies onEdit: () -> Unit to DetailTopBar actions and keeps management commands in HabitManagementSection.
- Other consumers: migrate every caller affected by these interface changes; preserve callback target identity and existing readiness/busy guards.

- [x] **Step 1: Read the spec and confirm prerequisites.** Inspect each listed file and the working-tree diff; preserve unrelated edits. No implementation prerequisite is required. Classify missing tests as additions, not evidence of an already passing regression.

- [ ] **Step 2: Add the behavior regressions and manual checks.** Use the listed test suites, keeping state/repository assertions in JVM tests where provided and navigation/Compose assertions in device tests. Use the named cases, fixtures and assertions below; extend existing fixtures instead of introducing a parallel harness. For speech, gesture comfort and visual reachability, automate the semantics/bounds that can be checked and record actual manual device evidence under the same case ID. A test cannot certify TalkBack speech merely by finding a live-region node. For copy-only changes, extend existing UI assertions.

| Case | Regression / evidence case | Fixture / action | Assertions |
|---|---|---|---|
| AC-1 | `editStaysPinned` | Load and scroll through 500 historical repetitions. | Edit remains reachable in the pinned header without scrolling to the end. |
| AC-2 | `managePrecedesHistory` | Open detail with at least 50 logs. | Manage/schedule appears before the historical list. |
| AC-3 | `historyUsesStableLazyRows` | Load another history page while a row has focus. | Existing row identities/position stay stable and off-screen rows are not all composed eagerly. |
| AC-4 | `destructiveActionsStayConfirmed` | Open Manage then Delete/Pause/Archive. | Existing confirmation and busy guards remain; pinned Edit does not bypass them. |
| AC-5 | `correctionStillTargetsExactLog` | Correct a record after loading older pages. | The original log UUID is used; paging counts and Undo behavior remain correct. |

- [ ] **Step 3: Establish the regression.** Run the relevant targeted command below before implementation. New-interface tests may initially fail to compile; existing-behavior tests must fail on the specified mismatch. Record the actual failure. For a runtime-risk finding, first exercise its constrained fixture; if it already passes, record that result and retain the reachability regression without claiming a reproduced failure.

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.habits.HabitsNavigationTest,com.luma.lifeos.habits.HabitCaptureFlowTest
```

Device commands require a connected, configured test device/emulator. Locale/font/window and TalkBack settings are deliberate validation inputs; the command alone does not configure or certify that matrix. Do not install dependencies or alter the execution environment as part of this documentation task.

- [x] **Step 4: Implement the owning contract.** Move existing Edit into pinned detail actions and relocate compact management/schedule before history.

- [x] **Step 5: Connect the behavior.** Convert history rendering to LazyListScope entries with stable prefix keys and the current history callbacks.

- [ ] **Step 6: Exercise the complete flow.** Validate long-history reachability, correction/Undo and management confirmation behavior after paging.

- [ ] **Step 7: Verify the task.** Rerun the targeted commands above and require zero failures. Then run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` to catch all-call-site, resource and lint regressions. Exercise the relevant acceptance scenarios in EN/FR/FA, semantic light/dark, 100%/130%/200% font and narrow/landscape where layout or semantics changed. Record app/build revision, device/API, locale, font/window inputs and results; label missing checks pending. Expected outcome: AC-1 through AC-5 hold and affected compilation/lint checks pass.

- [ ] **Step 8: Review and integrate this task alone.** Inspect the diff against the spec and confirm every AC/RF has evidence. Stage only this task's hunks in existing modified files and its new files; do not stage the pre-existing dirty tree wholesale. If the execution workflow uses commits, commit with `fix: ux-014 habit detail management`. Update the index status only from actual evidence.

## Handoff

Deliver this task's code changes, five scenario results and remaining device limitations together. No implementation has been performed by generating this plan. Do not mark the finding fixed solely because a plan, test name or historical screenshot exists.
