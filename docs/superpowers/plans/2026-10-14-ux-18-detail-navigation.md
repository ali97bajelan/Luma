# Persistent Detail Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep Back and valid Edit actions visible while long record/catalog details scroll.

**Architecture:** Reuse one small fixed DetailTopBar in each route's Scaffold. Existing Column/LazyColumn bodies, state observations, BackHandlers and navigation callbacks remain responsible for their current content behavior.

**Tech Stack:** Kotlin, Compose Material 3 TopAppBar/Scaffold, Compose instrumentation and current navigation.

**Spec:** [UX 18 design](../specs/2026-10-14-ux-18-detail-navigation-design.md).

**Status:** Implementation complete on working tree at HEAD `b52b9a7`. Unit tests, debug app/test APK builds and lint pass; 45 affected isolated-emulator tests pass. Manual acceptance remains pending; no commit was requested.

## Global Constraints

- Keep `minSdk = 28`, `compileSdk = 35`, `targetSdk = 35`, JDK 17, AGP 8.7.3, Kotlin 2.2.10 and Compose BOM 2025.08.01.
- Add no product dependency, database migration, permission, network access, telemetry or AI service.
- Keep English, Persian and French available offline; localize all new labels, dates, numbers and semantic descriptions.
- Preserve user-authored text, stable IDs, raw durations, occurrence timestamps and timezone rules.
- Preserve unsaved-change confirmations, rejected pending-tag save blocking, photo retention, read-only imported records and retry behavior.
- Missing observations stay unavailable; recorded zero stays zero; positive durations never display as zero.
- Every interactive control has an accessible name and at least a 48 dp touch target. Text wraps or ellipsizes without reducing the configured font scale.
- Automated semantics checks do not certify TalkBack speech. Manual acceptance remains pending until evidence is recorded.

## Review Focus

- Missing/loading/error detail must retain Back and hide Edit: Task 1 route state cases.
- Imported records must never gain Edit just because a bar exists: Task 1 read-only event case.
- A live catalog deletion must hide Edit rather than leave a stale callback: Task 2 observation case.
- Long translated labels, scale 2.0 and RTL must not push Back/Edit offscreen: Task 1 bar geometry + Task 2 routes.
- Nested Scaffolds/insets and detail return must not overlap content or lose invoking filters/dates: both tasks flow/inset checks.

---

### Task 1: Fixed bar plus journal/event reading routes

**Files:**
- Create: `app/src/main/java/com/luma/lifeos/ui/common/DetailTopBar.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/journal/JournalDetailRoute.kt`, `app/src/main/java/com/luma/lifeos/ui/timeline/EventDetailRoute.kt`.
- Modify: all three `app/src/main/res/values*/strings.xml` bundles.
- Create: `app/src/androidTest/java/com/luma/lifeos/DetailNavigationTest.kt`.
- Test/modify as needed: existing `JournalCaptureTest.kt`, `TimelineFlowTest.kt`, `SearchFlowTest.kt` in app androidTest.

**Interfaces:**
- Consumes: existing detail state, `onBack: () -> Unit`, journal `onEdit: () -> Unit`, event `onEdit: (LifeEventRecord) -> Unit` and current record capability.
- Produces: `@Composable fun DetailTopBar(title: String, backTag: String, onBack: () -> Unit, editTag: String? = null, editLabel: String? = null, onEdit: (() -> Unit)? = null, modifier: Modifier = Modifier)`. No changed detail route signature.

- [x] **Step 1: Add detail-bar and route regressions.** Added shared-bar action/geometry checks, long journal/mood-event/person/place scrolls, imported/missing edit visibility, deleted-catalog edit removal, and Search/People/Place/Review return-flow coverage. A full fake retry/loading/error matrix and long direct-memory route test remain pending; see the acceptance checklist. Core assertions after relevant scroll:

```kotlin
compose.onNodeWithTag("journal_detail_back").assertIsDisplayed()
compose.onNodeWithTag("journal_edit").assertIsDisplayed().performClick()
compose.onNodeWithTag("screen_journal").assertExists()
// In the imported-event case:
compose.onNodeWithTag("event_detail_back").assertIsDisplayed()
compose.onNodeWithTag("event_edit").assertDoesNotExist()
```

Use 320 dp/scale 2.0/RTL in the shared bar test and assert click callbacks once plus ≥48 dp bounds; verify first content bounds are below bar bounds. Fake failure/missing repositories may return Loading/Error/Missing; test callbacks in each state. Keep current memory-mark action reachable after scrolling.

- [x] **Step 2: Run initial red checks.** The Android test compile failed before implementation because `DetailTopBar` was absent; the completed isolated route suites now pass.
- [x] **Step 3: Implement DetailTopBar and journal/event Scaffolds.** Use existing `ic_chevron` only after confirming its autoMirrored behavior; it is the previous-direction drawable. Add event_detail_title/person_detail_title/place_detail_title/detail_edit resources with the spec's exact English copy and French/Persian translations; reuse event_journal and EventType labels. Give compact visible Edit copy its full semantic label. Preserve current Edit conditions and existing action tags. Apply Scaffold content padding to bodies and add journal_detail_content/event_detail_content tags. Keep BackHandler, retries, photos and memory actions. No scroll behavior on the bar.
- [x] **Step 4: Run builds/lint and targeted instrumentation.** Normal app/test APK builds, app unit tests and lint pass; isolated route/browsing suites pass. Actual route behavior is covered for long scrolls and conditional actions; physical insets, keyboard, TalkBack and extreme-text checks remain pending.
- [x] **Step 5: Record finding 18 implementation and pending manual acceptance.** Leave the working-tree changes uncommitted; no commit was requested.

### Task 2: Fixed person/place history navigation and live updates

**Files:**
- Modify: `app/src/main/java/com/luma/lifeos/ui/people/PeopleRoute.kt` (`PersonDetailRoute` only).
- Modify: `app/src/main/java/com/luma/lifeos/ui/places/PlacesRoute.kt` (`PlaceDetailRoute` only).
- Modify: `app/src/androidTest/java/com/luma/lifeos/DetailNavigationTest.kt`, `app/src/androidTest/java/com/luma/lifeos/PeopleCaptureTest.kt`, `app/src/androidTest/java/com/luma/lifeos/PlaceCatalogFlowTest.kt`.
- Test: existing `app/src/androidTest/java/com/luma/lifeos/PlaceCaptureTest.kt`, `app/src/androidTest/java/com/luma/lifeos/review/WeeklyReviewFlowTest.kt`.

**Interfaces:**
- Consumes: Task 1 DetailTopBar, current PersonHistoryState/PlaceHistoryState and route callbacks.
- Produces: fixed bars with same edit/ready/header tags and `person_detail_content`, `place_detail_content`, `person_detail_back`; existing `place_detail_back` stays. No repository changes.

- [x] **Step 1: Add person/place route regressions.** Seed 60 linked events in reverse chronological insertion order, assert actual oldest-first layout and persistent actions at the end, and verify deletion/missing state hides Edit while Back remains. Existing catalog and Review flows cover return context; a full error/retry/loading route matrix remains pending.

```kotlin
compose.onNodeWithTag("person_detail_back").assertIsDisplayed()
compose.onNodeWithTag("person_edit").assertIsDisplayed()
compose.onNodeWithTag("place_detail_back").assertIsDisplayed()
compose.onNodeWithTag("place_edit").assertIsDisplayed()
```

These assertions run separately on each detail after scrolling; an existing node alone is insufficient. Seed/cleanup only in the disposable package.

- [x] **Step 2: Run the person/place route regressions in the isolated app.** Long-history order/actions and deletion-state cases pass.
- [x] **Step 3: Wrap each history route in Scaffold using DetailTopBar.** Move ready/header tags to the bar, remove old navigation item, retain LazyColumn event keys/history items and remembered state. Pass onEdit only for current Data. Apply content padding/insets without changing catalog list functions. Keep type titles distinct from full names in body.
- [x] **Step 4: Run build/lint and affected feature suites.** `DetailNavigationTest`, `PeopleCaptureTest`, `PlaceCatalogFlowTest`, `PlaceCaptureTest` and `WeeklyReviewFlowTest` passed as part of the isolated run set. Manual large-text/RTL/TalkBack/landscape and detailed inset checks remain pending.
- [x] **Step 5: Record finding 18 implementation and pending manual acceptance separately.** Leave the working-tree changes uncommitted; no commit was requested.
