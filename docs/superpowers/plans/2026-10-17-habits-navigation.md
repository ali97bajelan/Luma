# Habits and Scalable Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Habits a primary destination and group supporting routes under More without losing existing life-record workflows.

**Architecture:** Reuse the existing NavHost, saved date and route identities. Extract focused destination/bar components from LifeOsApp; make People and Settings secondary routes reached from More and existing source links.

**Tech Stack:** Existing Kotlin, Navigation Compose 2.9.3, Compose Material 3, SavedStateHandle and Compose instrumentation.

**Spec:** [Counted habits, reminders and navigation](../specs/2026-10-17-habits-and-navigation-design.md), sections 2–3 and 10.

## Global Constraints

- Keep Kotlin 2.2.10, JDK 17, minSdk 28, compileSdk 35 and targetSdk 35.
- Keep the current Compose/Material 3, Navigation 2.9.3, Room 2.8.5, coroutines 1.10.2 and WorkManager 2.10.0 dependencies.
- Keep core:domain free of Android and Room dependencies; app wires implementations.
- Add no account, network permission, telemetry, cloud service or AI dependency.
- Use English, Persian RTL and French resources for all app-owned text.
- Preserve existing records, selected dates, editor drafts, usage visibility and navigation restoration.
- Never use destructive database migration or replace a user's install/data for validation.
- Reminders are ordinary, approximate local notifications, off by default; no exact-alarm, full-screen-intent or foreground-service permission.
- Keep existing entry/journal/memory counts unchanged; count habit repetitions separately.

## Review Focus

- A restored legacy `people`/`settings` back-stack entry must still resolve after the primary bar changes: T26 legacy-route recreation test.
- Shared selected dates and tracking-zone today must remain distinct without UTC date shifts: T26 travel/date-state test.
- More's additional navigation step must not break direct People/Places links from capture and Review: T26 source-route test.
- Compact French/Persian labels must retain full accessible names at 320 dp/font scale 2.0: T26 measured semantics/layout matrix.
- Returning from nested Settings or an editor must restore its actual origin, scroll position and drafts: T26 Back/language-state test.

## File structure

- Create `app/src/main/java/com/luma/lifeos/ui/navigation/AppDestinations.kt` for the five primary destinations and stable route constants.
- Create `app/src/main/java/com/luma/lifeos/ui/navigation/PrimaryNavigationBar.kt` by moving the existing tested bar composable out of LifeOsApp.
- Create `app/src/main/java/com/luma/lifeos/ui/more/MoreRoute.kt` for the labelled hub.
- Modify `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt` only for graph/wiring.
- Reuse `ui/people/PeopleRoute.kt`, `ui/settings/SettingsRoute.kt` and all existing editors/details; add fixed Back wrappers at their secondary route boundary.

---

### T26: Five destinations and a usable More hub

**Dependencies:** T24's functional Habits routes and T25's Timeline/source routing. Never ship a placeholder Habits tab.

**Files:**
- Create/move/modify the files above.
- Add `app/src/main/res/drawable/ic_nav_habits.xml` and `ic_nav_more.xml`, following the existing vector icon system.
- Modify `app/src/main/res/values/strings.xml`, `values-fa/strings.xml`, `values-fr/strings.xml`.
- Modify `app/src/androidTest/java/com/luma/lifeos/AppNavigationTest.kt`, `DetailNavigationTest.kt`, `language/LanguageFlowTest.kt`, `ui/PrimaryNavigationAccessibilityTest.kt`.
- Create `app/src/androidTest/java/com/luma/lifeos/habits/HabitsNavigationTest.kt`.

**Interfaces:**
- Consumes: T24 `HabitsRoute`, editor/detail routes; the existing shared `selectedDate: String?` and currentDay observation; existing catalog/settings callbacks.
- Produces: `PrimaryDestination` enum entries TODAY("today"), TIMELINE("timeline"), HABITS("habits"), REVIEW("review"), MORE("more") in that order, each with full/compact labels and vector icon. `PrimaryNavigationBar(selectedRoute: String?, onNavigate: (String) -> Unit, modifier: Modifier = Modifier)` retains full-name/tab semantics. `MoreRoute(onPeoplePlaces: () -> Unit, onSettings: () -> Unit)` provides labelled entries. Legacy `people` and `settings` remain secondary route constants.

- [ ] **Step 1: Write failing navigation tests.** Assert exactly the five approved destinations and no sixth item. Today and Habits + Log both work; More opens People & Places and Settings; the existing catalog selector remains. Each secondary page has visible Back returning to More; language/integration/backup/privacy routes return to their settings parent. Existing Review/person/place and capture-selector links still open their source directly and return to the origin.
- [ ] **Step 2: Pin restoration and accessibility regressions.** Select yesterday, switch through all tabs, open a habit log, recreate the activity and switch Persian/French/English: retain selected civil date, habit/log identity, per-tab scroll and user text. Open an editor, change its draft, switch language and return without loss. Restore a legacy people/settings destination. The 320 dp/411 dp × 1.0/1.3/2.0 matrix asserts labels fit, full accessible names, tab role/state, reachable 48 dp actions and RTL direction. Follow the existing PrimaryNavigationAccessibilityTest's measurement strategy without freezing old primary-tab labels.
- [ ] **Step 3: Run red.** `./gradlew :app:assembleDebugAndroidTest :app:testDebugUnitTest --tests 'com.luma.lifeos.language.TranslationCatalogTest'`; run HabitsNavigationTest and PrimaryNavigationAccessibilityTest on a disposable app ID. Expect old tab order/missing More behavior failures.
- [ ] **Step 4: Implement the extracted shell and hub.** Keep `launchSingleTop`, saveState/restoreState and start-destination behavior. Remove the original local enum/bar only after replacing references; do not duplicate route definitions. Make people/settings secondary composables with Back wrappers, preserving content implementations and existing route arguments. More visibly says People & Places and Settings with descriptive subtitles; integrations remains inside Settings. Keep Quick Capture on all five primary destinations and retain its three actions.
- [ ] **Step 5: Promote the functional habits route.** Reuse T24's route and shared civil-date state. `selectedDate == null` continues to mean follow today, with habit-zone today resolved by the habits feature; explicit picked dates remain the same civil date. Keep editor/detail routes outside the primary bar and do not route habit log IDs into mood/journal/memory editors. Retain compact French labels and full localized accessible names.
- [ ] **Step 6: Verify green and manual usability.** Run new/changed instrumentation plus AppNavigationTest, DetailNavigationTest and LanguageFlowTest. Confirm Back and TalkBack traversal from More to both catalogs/settings, scroll retention, native font scale 2.0 and real French/Persian rendering. Record unavailable manual/device checks as NOT RUN/BLOCKED. `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` exits 0.
- [ ] **Step 7: Update current architecture/README screenshots or route descriptions if present, task evidence and commit only this slice.** Suggested message: `feat(navigation): add Habits and More primary destinations`.

## Handoff

Proceed to [T27–T29 reminders and full acceptance](2026-10-17-habit-reminders.md).
Keep future voice/search/narrative features in their existing parent surfaces;
this task does not implement those features. Planned checks above are not results.
