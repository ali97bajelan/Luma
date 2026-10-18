# Secondary Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consistent fixed secondary Back/title bars.
**Architecture:** Extend DetailTopBar and migrate inventoried routes; retain each route's close policy.
**Tech Stack:** Compose Scaffold/TopAppBar, existing NavHost and BackHandler.
**Spec:** [Navigation design](../specs/2026-10-18-ux-13-navigation-design.md)

## Global Constraints

- Keep minSdk 28, compileSdk 35, targetSdk 35, JDK 17, AGP 8.7.3, Kotlin 2.2.10 and Compose BOM 2025.08.01.
- Add no product dependency, database migration, permission, network access, telemetry or AI service.
- English, Persian and French remain available offline; preserve user-authored text, stored timestamps and timezone rules.
- UX-01, UX-02 and UX-12 are excluded; preserve their existing behavior.
- Minimum interactive touch target: 48 × 48 dp; do not reduce configured text scale.

## Review Focus

- Privacy busy reset prevents both visible and system navigation (Task 2).
- Habit dirty correction consumes Back before route pop (Task 2).
- Loading/error route still has a usable Back control (Task 2).
- Outer/inner scaffolds do not double system padding (Task 2).
- Search bar migration preserves all existing UX-11 states (Task 2).

### Task 1: Extend shared bar

**Files:** Modify app/src/main/java/com/luma/lifeos/ui/common/DetailTopBar.kt; extend app/src/androidTest/java/com/luma/lifeos/DetailNavigationTest.kt.
**Interfaces:** Append actions: @Composable RowScope.() -> Unit = {} and backEnabled: Boolean = true to DetailTopBar; preserve current title/backTag/onBack/edit parameters and defaults.

- [ ] Add tests: Back disabled when requested, secondary action visible/clickable, existing Edit retained, long title full semantics, RTL icon resource direction.
- [ ] Run DetailNavigationTest and observe unsupported-interface failures.
- [ ] Implement extended slots/guard with no extra system insets; preserve test tags and 48 dp targets.
- [ ] Run `bash gradlew :app:assembleDebug :app:assembleDebugAndroidTest --offline` and isolated DetailNavigationTest.
- [ ] Commit with `refactor(ui): extend shared secondary top bar`.

### Task 2: Migrate inventoried routes and protect Search states

**Files:** Modify ui/settings/SettingsRoute.kt, ui/search/SearchRoute.kt, ui/privacy/PrivacySettingsRoute.kt, ui/habits/HabitDetailRoute.kt, ui/habits/HabitsScreen.kt (secondary path), ui/people/PeopleRoute.kt, ui/places/PlacesRoute.kt, ui/review/ReviewSourcesRoute.kt and ReviewUsageRoute.kt. Extend DetailNavigationTest.kt, AppNavigationTest.kt, SearchStatesTest.kt, settings/SettingsHubFlowTest.kt, privacy/PrivacySettingsFlowTest.kt, habits/HabitsNavigationTest.kt.
**Interfaces:** Consume Task 1 bar and preserve existing route callbacks. Per-route requestBack(): Unit is the single visible/system close policy, including UX-03 dialog handling.

- [ ] Add route-inventory tests: scroll long content then Back remains displayed; loading/missing/error routes still Back; icon/system pop once; Privacy busy blocks both; dirty habit correction asks once. Reuse all existing SearchStatesTest cases plus 179/180/200 counter boundary checks if absent.
- [ ] Run affected device tests to capture existing scrolling/header inconsistencies.
- [ ] Migrate fixed bars/content padding, preserve catalog create/tab actions and stable tags, deduplicate headings. Keep editor controls and Search scope/copy semantics unchanged. Match BackHandler ordering with correction/picker layers.
- [ ] Run focused app JVM tests, both APK builds and navigation/catalog/Search/privacy/Review classes listed in shared acceptance checklist. Verify RTL and top inset visually on 360/411 dp.
- [ ] Commit with `refactor(navigation): standardize fixed secondary headers`; record UX-11 regression result without rewriting its implementation.

