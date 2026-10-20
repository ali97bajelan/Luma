# Scrollable Habits and Tasks Widgets — WD17, WD13, WD14 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every eligible habit/task reachable in a fixed-size widget through native scrolling, then add state regressions and honest previews/setup guidance.

**Architecture:** Keep the current fixed widget shell and replace only its static row children with a bounded `ListView` backed by a per-instance `RemoteViewsService` collection. Page records from the existing Room store with stable identity and persisted action tokens; route child actions through the collection template while retaining the existing worker command path and privacy configuration. Update the configuration/settings examples after scrolling is live.

**Tech Stack:** Kotlin, Android `RemoteViews`/`RemoteViewsService`, Room, WorkManager, Android XML layouts, Jetpack Compose, localized Android resources.

**Spec:** `docs/WIDGET_VISUAL_REVIEW_2026-10-20.md` (WD17, WD13, WD14; sections 3–6).

## Global Constraints

- “Both Habits and Tasks must support **native vertical scrolling inside the item area** whenever their eligible content exceeds that area.”
- “Keep the header, Capture, Refresh, scope/date/freshness and persistent Undo/Retry strip fixed outside the list. Only item rows scroll.”
- “Retain the current habit schedule scope, due-today/overdue task scope and existing order.”
- “Keep summary mode free of a real collection before consent.”
- “Use stable record identity; do not deliberately scroll to the top on every update.”
- “Never dispatch by recycled row position.”
- “Update collection contents after refresh, mutation, Undo, Retry, date rollover, consent change, restore and widget removal.”
- “No new dependency or app-wide navigation redesign is required.”
- User constraint: do not run Gradle or emulator tests; leave acceptance evidence open where it requires those runs.
- Preserve the active working tree and do not create a commit; provide a commit message only.

## Review Focus

- A stale fill-in action after privacy mode changes must not reveal names or mutate a hidden row; configuration invalidates that instance’s actions and collection.
- Duplicate widgets of the same provider must not share adapter rows, scroll identity, or persisted command tokens.
- A row removed or reordered after a refresh must not redirect an old fill-in action to the record now occupying its former position.
- Empty/loading/read-error/Undo/Retry states must keep recovery and utility actions outside the scrolling viewport.
- Collections over 500 items must remain page-backed and must not truncate at the previous eight-row bound or create an oversized `RemoteViews` payload.

---

### Task 1: Page all eligible collection rows from Room

**Files:**
- Modify: `core/domain/src/main/kotlin/com/luma/lifeos/domain/widget/WidgetStore.kt`
- Modify: `core/data/src/main/kotlin/com/luma/lifeos/data/habit/HabitDao.kt`
- Modify: `core/data/src/main/kotlin/com/luma/lifeos/data/todo/TodoDao.kt`
- Modify: `core/data/src/main/kotlin/com/luma/lifeos/data/widget/RoomWidgetStore.kt`
- Test source: existing widget store test files under `core/data/src/test` and `core/data/src/androidTest`

**Interfaces:**
- Consumes: `WidgetSnapshot`, `WidgetRow`, `WidgetInstance`, existing transaction-backed command issuer.
- Produces: `suspend fun collectionPage(widgetId: Int, now: Instant, deviceZone: ZoneId, offset: Int, limit: Int): WidgetSnapshot?`. The returned snapshot carries current summary metadata and only the requested rows; hidden-details instances return no real rows. Bound `limit` to `WidgetStore.COLLECTION_PAGE_SIZE = 32`. Keep the existing snapshot contract for shell state and pass `rowLimit = 0` when the shell only needs metadata.

- [x] Add DAO page queries with the same task due-date/created-time/id order and habit created-time/id order as existing widget queries.
- [x] Add store-level page parameters and validate nonnegative offset/positive bounded page size; issue/reuse command tokens for each row using record identity and revision, without invalidating commands belonging to other pages.
- [x] Add source regressions for offsets, stable ordering, the 8/9th and 500th eligible rows, privacy-hidden instances, and stale revisions. Do not run Gradle tests.

### Task 2: Add per-instance native collection adapter and routing

**Files:**
- Create: `app/src/main/java/com/luma/lifeos/widget/WidgetCollectionService.kt`
- Create: `app/src/main/java/com/luma/lifeos/widget/WidgetCollectionFactory.kt`
- Modify: `app/src/main/java/com/luma/lifeos/widget/WidgetIntents.kt`
- Modify: `app/src/main/java/com/luma/lifeos/widget/WidgetActionReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test source: `app/src/androidTest/java/com/luma/lifeos/widget/WidgetIntentsTest.kt` and focused collection adapter tests

**Interfaces:**
- Consumes: Task 1 page API; `WidgetActionWorker` and persisted operation tokens.
- Produces: an explicit service intent whose data URI includes provider kind and widget ID; a collection fill-in route carrying widget ID, action kind, record ID/date and persisted token as applicable.

- [x] Implement `RemoteViewsFactory` with per-widget identity, bounded page reads, stable collision-resolved item IDs, correct view type count, and no rows when consent is off.
- [x] Register the service as non-exported with `android.permission.BIND_REMOTEVIEWS`; route collection mutation through the existing receiver/worker and navigation through the existing explicit MainActivity destination allowlist after validating the live widget instance and record identity.
- [x] Notify the adapter after refresh and privacy reconfiguration; existing mutation, Undo, Retry, date/scope, restore and widget-removal flows still use the serialized refresh/host lifecycle path. Confirm host scroll preservation on supported launchers before acceptance.
- [x] Add intent and adapter source regressions for malformed/stale fill-ins, navigation versus mutation, duplicate instances and row-type count. No Gradle or emulator tests were run.

### Task 3: Replace static row children with a bounded native ListView

**Files:**
- Modify: `app/src/main/res/layout/widget_data.xml`
- Modify: `app/src/main/res/layout/widget_data_stacked_header.xml`
- Modify: `app/src/main/java/com/luma/lifeos/widget/WidgetRenderer.kt`
- Modify: `app/src/main/java/com/luma/lifeos/widget/WidgetCompactRenderer.kt`
- Modify: `app/src/main/java/com/luma/lifeos/widget/WidgetLayoutPolicy.kt`
- Modify: `app/src/main/java/com/luma/lifeos/widget/WidgetRefreshWorker.kt`
- Modify: `app/src/main/java/com/luma/lifeos/widget/WidgetConfigurationActivity.kt`
- Test source: `app/src/test/java/com/luma/lifeos/widget/WidgetLayoutPolicyTest.kt` and `app/src/androidTest/java/com/luma/lifeos/widget/WidgetRendererAcceptanceTest.kt`

**Interfaces:**
- Consumes: Task 1 metadata/page API and Task 2 service/fill-in route.
- Produces: fixed shell views around a native ListView; optional localized scroll hint only when eligible count exceeds the usable collection viewport; compact recovery when no usable row viewport fits.

- [x] Replace `widget_data_rows` with a weighted/bounded `ListView`, keeping shell, metadata and feedback outside it; retain 8dp row spacing and existing fallback priorities.
- [x] Set the per-instance remote adapter and pending-intent template, hide the collection in summary mode, notify it after refresh/reconfiguration, and keep recovery controls outside the list.
- [x] Remove the eight-row rendering/fetch assumption while measuring a bounded sizing page for the viewport. Add source regressions for overflow hints, collection use, hidden details and reapplication clearing stale content. Do not run Gradle tests.

### Task 4: Cover WD13 action, recovery, privacy and Capture states

**Files:**
- Modify: `app/src/test/java/com/luma/lifeos/widget/WidgetLayoutPolicyTest.kt`
- Modify: `app/src/androidTest/java/com/luma/lifeos/widget/WidgetRendererAcceptanceTest.kt`
- Modify: `app/src/androidTest/java/com/luma/lifeos/widget/WidgetIntentsTest.kt`
- Modify: relevant widget configuration/Capture acceptance tests

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces: regression source coverage for collection totals/routes/reapplication, both layout directions, Complete/+1, final-row removal, target reached, Undo/Retry persisted tokens, stale/conflict/missing/read failure/loading/setup/empty, per-instance isolation, summary privacy, and shared Capture semantics.

- [x] Extend focused test source with explicit row identity/token routes and collection reapplication/privacy states; existing shared Capture destination/flow coverage remains in place.
- [x] Leave all new test sources unexecuted per user instruction; record that WD13 acceptance remains unverified.

### Task 5: Update WD14 previews and placement guidance

**Files:**
- Modify: `app/src/main/java/com/luma/lifeos/ui/widgets/WidgetExample.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/widgets/WidgetSettingsRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/widget/WidgetConfigurationActivity.kt`
- Modify: `app/src/main/res/layout/widget_preview_habits.xml`
- Modify: `app/src/main/res/layout/widget_preview_tasks.xml`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml`, `app/src/main/res/values-fa/strings.xml`
- Test source: relevant widget settings/configuration acceptance tests

**Interfaces:**
- Consumes: Task 3’s actual list/scroll behavior and the existing `showDetails` consent flow.
- Produces: fictional summary/details previews, compact scroll-aware example, localized resize/scroll guidance, and unchanged Save/Cancel and legacy capture behavior.

- [x] Show two fictional rows rather than a fixed “N more” cue; include scroll affordance only in details examples and keep personal records out of previews.
- [x] Explain that overflow items remain reachable inside either widget and that launcher resizing changes the viewport; keep current provider minimum size because no launcher-fit evidence can be collected in this task.
- [x] Add source assertions for localized guidance, summary privacy, Save/Cancel visibility and preview content; do not run Gradle or emulator tests.

### Task 6: Record status and handoff

**Files:**
- Modify: `docs/WIDGET_VISUAL_REVIEW_2026-10-20.md`

- [x] Mark WD17, WD13 and WD14 `Partial`; check only source implementation items actually completed and state that Gradle/emulator and launcher/accessibility acceptance remain open by user instruction.
- [x] Inspect the focused diff and whitespace without running tests; preserve unrelated working-tree changes.
- [x] Provide a commit message suggestion; do not commit.
