# UX-021 — Page chrome and inset ownership vary across screen families Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use superpowers:subagent-driven-development only if the user selects delegation. Steps use checkbox syntax for tracking. This documentation request does not start implementation or delegate work.

**Goal:** Give primary, secondary and editor pages one explicit inset owner and predictable pinned navigation.

**Architecture:** Keep the app shell as system-bar inset owner, consume its applied padding, and make nested page scaffolds use zero system-bar insets. Extend existing header components and pilot Today, Timeline and Mood before migrating remaining pages.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose/Material 3, lifecycle StateFlow and the existing Android/Room modules; existing dependencies.

**Spec:** [UX-021 specification](../specs/2026-10-20-audit-n-ux-021-page-header-insets-design.md)

**Status:** Shared header/inset implementation complete. Focused Android emulator suite (29 tests), debug builds and lint pass. Manual locale, font-scale, resized-window and IME matrix remains pending. Relative effort: Medium.

## Global Constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies unless a task explicitly requires a reviewed exception.
- All changed user-facing copy must exist in EN/FR/FA with matching resource names and numbered placeholder signatures; preserve RTL and intended Gregorian display/storage behavior.
- Keep semantic light/dark palettes, at least 48 dp interactive targets, and reachability at 100%/130%/200% font, narrow portrait and short landscape.
- Preserve offline/private storage, existing consent boundaries, stable record/command IDs, original retry payloads and Undo protections.
- Never truncate accepted personal content, infer write failure means no commit, or treat an unavailable privacy mode as known.
- Only implement this task and its declared prerequisites; preserve unrelated working-tree changes and existing navigation/draft state.

## Review Focus

These five concrete failure modes are pinned to the regression cases owned by this task. The assertions below are requirements for tests to add, not claims that those tests exist or currently pass.

- **RF-1 → AC-1:** Measure pilot content with gesture and three-button navigation. System bars are accounted for once; header/content geometry has no duplicate system-bar gap. Covered by `singleSystemInsetOwner` in the regression step below.
- **RF-2 → AC-2:** Open Mood keyboard at 200% text, edit then Close/Back. Save remains reachable; dirty confirmation and busy protections still apply. Covered by `editorImeAndCloseStaySafe` in the regression step below.
- **RF-3 → AC-3:** Scroll Privacy and saved usage to the bottom. Back and title remain accessible outside scrolling body content. Covered by `secondaryBackIsPinned` in the regression step below.
- **RF-4 → AC-4:** Switch EN/FR/FA, rotate and resize from compact to wide. Directional icons/focus remain correct; selected tab/date and list position survive. Covered by `rtlAndWindowChangesPreserveState` in the regression step below.
- **RF-5 → AC-5:** Compare screenshots/bounds for Today, Timeline and Mood at 100/130/200% font. No clipping or duplicate insets; wider rollout waits for recorded pilot acceptance. Covered by `pilotGatesBroaderMigration` in the regression step below.

---

## Task 1: Deliver UX-021

**Files and responsibilities:**

- Modify: `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/common/EditorTopBar.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/common/DetailTopBar.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/today/TodayScreen.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/timeline/TimelineRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorScreen.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/settings/SettingsRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/privacy/PrivacySettingsRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/more/MoreRoute.kt`
- Create: `app/src/main/java/com/luma/lifeos/ui/common/AppPageScaffold.kt`
- Create: `app/src/main/java/com/luma/lifeos/ui/common/PageHeader.kt`
- Create test: `app/src/androidTest/java/com/luma/lifeos/ui/PageInsetsContractTest.kt`
- Extend test: `app/src/androidTest/java/com/luma/lifeos/CaptureAccessibilityTest.kt`
- Extend test: `app/src/androidTest/java/com/luma/lifeos/AppNavigationTest.kt`
- Localized resources, only when copy changes: `app/src/main/res/values/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fr/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fa/strings.xml`

Keep changes in their current screen/state/repository responsibility. New files own only the interface or component named in the specification. If a listed existing file has changed since this plan, reconcile with the live code before editing; do not revert user changes to match the audit.

**Interfaces:**

- Consumes the current route/ViewModel/repository contracts in the files listed above; no new cross-task API is required.
- Produces: @Composable AppPageScaffold(title: String, navigation: PageNavigation, onNavigate: (() -> Unit)?, actions: @Composable RowScope.() -> Unit, content: @Composable (PaddingValues) -> Unit): Unit. enum PageNavigation { NONE, BACK, CLOSE }. Nested Scaffold contentWindowInsets = WindowInsets(0, 0, 0, 0); app shell applies and consumes system-bar padding once. Editor content owns IME avoidance once.
- Other consumers: migrate every caller affected by these interface changes; preserve callback target identity and existing readiness/busy guards.

- [ ] **Step 1: Read the spec and confirm prerequisites.** Inspect each listed file and the working-tree diff; preserve unrelated edits. No implementation prerequisite is required. Classify missing tests as additions, not evidence of an already passing regression.

- [ ] **Step 2: Add the behavior regressions and manual checks.** Use the listed test suites, keeping state/repository assertions in JVM tests where provided and navigation/Compose assertions in device tests. Use the named cases, fixtures and assertions below; extend existing fixtures instead of introducing a parallel harness. For speech, gesture comfort and visual reachability, automate the semantics/bounds that can be checked and record actual manual device evidence under the same case ID. A test cannot certify TalkBack speech merely by finding a live-region node. For copy-only changes, extend existing UI assertions.

| Case | Regression / evidence case | Fixture / action | Assertions |
|---|---|---|---|
| AC-1 | `singleSystemInsetOwner` | Measure pilot content with gesture and three-button navigation. | System bars are accounted for once; header/content geometry has no duplicate system-bar gap. |
| AC-2 | `editorImeAndCloseStaySafe` | Open Mood keyboard at 200% text, edit then Close/Back. | Save remains reachable; dirty confirmation and busy protections still apply. |
| AC-3 | `secondaryBackIsPinned` | Scroll Privacy and saved usage to the bottom. | Back and title remain accessible outside scrolling body content. |
| AC-4 | `rtlAndWindowChangesPreserveState` | Switch EN/FR/FA, rotate and resize from compact to wide. | Directional icons/focus remain correct; selected tab/date and list position survive. |
| AC-5 | `pilotGatesBroaderMigration` | Compare screenshots/bounds for Today, Timeline and Mood at 100/130/200% font. | No clipping or duplicate insets; wider rollout waits for recorded pilot acceptance. |

- [ ] **Step 3: Establish the regression.** Run the relevant targeted command below before implementation. New-interface tests may initially fail to compile; existing-behavior tests must fail on the specified mismatch. Record the actual failure. For a runtime-risk finding, first exercise its constrained fixture; if it already passes, record that result and retain the reachability regression without claiming a reproduced failure.

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.ui.PageInsetsContractTest,com.luma.lifeos.CaptureAccessibilityTest,com.luma.lifeos.AppNavigationTest
```

Device commands require a connected, configured test device/emulator. Locale/font/window and TalkBack settings are deliberate validation inputs; the command alone does not configure or certify that matrix. Do not install dependencies or alter the execution environment as part of this documentation task.

- [ ] **Step 4: Implement the owning contract.** Document and implement shell padding consumption, zero-inset nested scaffold and shared pinned header APIs.

- [ ] **Step 5: Connect the behavior.** Migrate Today, Timeline and Mood as the pilot; validate no doubled top/bottom insets, editor IME action reachability and dirty-close behavior before proceeding.

- [ ] **Step 6: Exercise the complete flow.** Inventory remaining hand-built headers and migrate route families to the validated contract, preserving route-local actions and saved scroll.

- [ ] **Step 7: Verify the task.** Rerun the targeted commands above and require zero failures. Then run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` to catch all-call-site, resource and lint regressions. Exercise the relevant acceptance scenarios in EN/FR/FA, semantic light/dark, 100%/130%/200% font and narrow/landscape where layout or semantics changed. Record app/build revision, device/API, locale, font/window inputs and results; label missing checks pending. Expected outcome: AC-1 through AC-5 hold and affected compilation/lint checks pass.

- [ ] **Step 8: Review and integrate this task alone.** Inspect the diff against the spec and confirm every AC/RF has evidence. Stage only this task's hunks in existing modified files and its new files; do not stage the pre-existing dirty tree wholesale. If the execution workflow uses commits, commit with `fix: ux-021 page header insets`. Update the index status only from actual evidence.

## Handoff

Deliver this task's code changes, five scenario results and remaining device limitations together. No implementation has been performed by generating this plan. Do not mark the finding fixed solely because a plan, test name or historical screenshot exists.
