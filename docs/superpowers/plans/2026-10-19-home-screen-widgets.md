# Luma Home-screen Widgets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task; superpowers:subagent-driven-development is an alternative when the user selects delegation. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver safe offline home-screen habit logging, task completion and direct mood/memory/journal capture.

**Architecture:** Three native RemoteViews providers share intent routing, rendering and refresh coordination. A Room-backed widget store issues opaque commands and atomically validates/consumes them around existing repository writes. Existing capture editors own drafts and persistence.

**Tech Stack:** Kotlin, Android AppWidgetProvider/RemoteViews, existing WorkManager 2.10.0, Room 2.8.5, coroutines, existing Compose navigation/editors; no new library.

**Spec:** [2026-10-19-home-screen-widgets-design.md](../specs/2026-10-19-home-screen-widgets-design.md)

**Status:** WG01 source implementation is in place. WG02 implementation and Room schema 5 export are complete. WG03 source implementation is complete, and production compiles passed for `:core:data:compileDebugKotlin` and `:app:compileDebugKotlin`. WG04 and WG05 source implementations are complete and `:app:assembleDebug` passed. No Gradle test or emulator/launcher test was run, per request; WG01–WG05 acceptance verification and WG06–WG08 remain pending.

## Global Constraints

- Ship three separately selectable providers: **Luma Habits**, **Luma Tasks**, **Luma Quick capture**.
- Keep minSdk **28**, compileSdk/targetSdk **35** and current dependencies; no network, new permission, foreground service, exact alarm or widget-triggered integration import.
- Add database version **5**; manual archive schema remains **4**; preserve migrations/readers for prior supported versions.
- **Show names and row actions** defaults **off**, separately per Habits/Tasks instance.
- Show at most **three rows**; minimum action size **48×48dp**; never shrink text to fit.
- Data-widget default fallback **250×250dp**, minimum **180×160dp**; Quick capture **180×160dp**; default cell hints **4×3** and **3×2** respectively.
- Use **30-minute** unique periodic WorkManager refresh only while a data widget exists; `updatePeriodMillis=0`; timing is best effort.
- Terminal command retention is **seven days**; missing/expired tokens always reject; never rebuild mutations from intent fields.
- English/French/Persian, app theme/language, RTL, confirmed counts, retained recovery and existing editor draft guards are required.
- Preserve unrelated working-tree changes, including current Todo editor changes and audit status. Do not declare audit tasks complete from widget work.

## Review Focus

1. Delayed original command after Undo/deletion/process death must remain terminal: WG03 exact-token replay tests.
2. Tracking-zone midnight versus device midnight/travel must reject stale writes without silently changing dates: WG03 fixed-clock tests and WG04 delayed-worker checks.
3. Live restore/reconfiguration/remapped host IDs must revoke old commands atomically: WG02 rollback/restore tests and WG03 ownership tests.
4. Warm widget launch into an existing dirty editor must preserve both draft and navigation state: WG01 cold/warm/recreation tests.
5. Large Persian/French content, feedback and 200% text must yield fewer rows rather than hidden recovery: WG05/WG06 hosted RemoteViews tests and WG08 native assistive checks.

## File map and shared contracts

All paths below are relative to repository root. Directory aliases are expanded here so individual tasks identify exact files without repeating long roots:

- `A` = `app/src/main/java/com/luma/lifeos/`
- `R` = `app/src/main/res/`
- `K` = `core/domain/src/main/kotlin/com/luma/lifeos/domain/widget/`
- `D` = `core/data/src/main/kotlin/com/luma/lifeos/data/`
- `AT` = `app/src/test/java/com/luma/lifeos/widget/`
- `AI` = `app/src/androidTest/java/com/luma/lifeos/widget/`
- `DI` = `core/data/src/androidTest/kotlin/com/luma/lifeos/data/`

New source paths are proposed; existing paths were inspected. New files each own one responsibility:

| Slice | Files and responsibility |
| --- | --- |
| Routing/capture | `A/widget/WidgetDestination.kt`, `WidgetIntents.kt`, `WidgetLaunchViewModel.kt`, `QuickCaptureWidgetProvider.kt`: typed targets, immutable PendingIntents, one-time Activity delivery, static capture layout. |
| Domain/runtime | `K/WidgetModels.kt`, `WidgetStore.kt`; `D/widget/WidgetEntities.kt`, `WidgetDao.kt`, `RoomWidgetStore.kt`: snapshot/feedback contracts, runtime storage and command transactions. |
| Refresh | `A/widget/WidgetRefreshCoordinator.kt`, `WidgetRefreshWorker.kt`, `WidgetActionReceiver.kt`, `WidgetSystemReceiver.kt`: scheduling, off-main reads/writes, permitted system events. |
| Presentation | `A/widget/WidgetLayoutPolicy.kt`, `WidgetRenderer.kt`, `HabitsWidgetProvider.kt`, `TasksWidgetProvider.kt`: capacity, shared views, thin per-kind lifecycle handlers. |
| Setup/discovery | `A/widget/WidgetConfigurationActivity.kt`, `A/ui/widgets/WidgetSettingsRoute.kt`: visibility consent, instance management and system pin requests. |

### Interfaces fixed before downstream tasks

WG01 owns `enum CaptureKind { MOOD, MEMORY, JOURNAL }` and `sealed interface WidgetDestination` with `Today`, `QuickCapture`, `Capture(kind: CaptureKind)`, `Habits`, `HabitDetail(id: UUID, date: LocalDate)`, `Tasks`, `Task(id: UUID)`, `NewTask`. Define `WidgetLaunchRequest(id: UUID, destination: WidgetDestination)`; IDs distinguish Activity deliveries, not saved entries. `WidgetIntents.navigation(context: Context, widgetId: Int, destination: WidgetDestination): PendingIntent` directly targets MainActivity. `WidgetIntents.decode(intent: Intent): WidgetDestination?` accepts only this allowlist and validated canonical UUID/date values. Mutation intent builder added by WG04: `mutation(context: Context, widgetId: Int, token: UUID): PendingIntent`.

WG02 owns these pure Kotlin contracts in `K` (all properties `val`):

```kotlin
enum class WidgetKind { HABITS, TASKS }
data class WidgetInstance(val id: Int, val kind: WidgetKind, val showDetails: Boolean = false)
sealed interface WidgetRow {
    data class Habit(val id: UUID, val title: String, val count: Int,
        val target: Int, val skipped: Boolean, val actionToken: UUID?) : WidgetRow
    data class Task(val id: UUID, val title: String, val dueDate: LocalDate,
        val actionToken: UUID?) : WidgetRow
}
enum class WidgetOutcome { SAVED, UNDONE, STALE, CONFLICT, MISSING, INVALID, FAILED }
data class WidgetActionResult(val outcome: WidgetOutcome,
    val retryToken: UUID? = null, val undoToken: UUID? = null)
data class WidgetSnapshot(val instance: WidgetInstance, val date: LocalDate,
    val zone: ZoneId, val total: Int, val completed: Int?,
    val hasAnyItems: Boolean, val rows: List<WidgetRow>,
    val feedback: WidgetActionResult?, val updatedAt: Instant)
interface WidgetStore {
    suspend fun configure(instance: WidgetInstance)
    suspend fun snapshot(widgetId: Int, now: Instant, deviceZone: ZoneId, rowLimit: Int): WidgetSnapshot?
    suspend fun execute(widgetId: Int, token: UUID, now: Instant, deviceZone: ZoneId): WidgetActionResult
    suspend fun dismissFeedback(widgetId: Int)
    suspend fun remove(widgetId: Int)
    suspend fun remap(oldIds: List<Int>, newInstances: List<WidgetInstance>)
    fun observeInvalidations(): Flow<Unit>
}
```

`total` is scheduled habits or eligible tasks; `completed` is target-reached habit count, null for tasks; `hasAnyItems` distinguishes no library from an empty date scope. `rowLimit` must be 0–3. Hidden-detail returns no rows/tokens. Store null means absent configuration; read errors throw to the worker, never produce a fake zero snapshot. `execute` is fully delivered in WG03; no provider exposes it before that task passes.

WG04 owns `WidgetRefreshCoordinator.request(context: Context): Unit`, `reconcileSchedule(context: Context): Unit` and `WidgetRenderer.render(context: Context, widgetId: Int, snapshot: WidgetSnapshot?, failure: Boolean): RemoteViews`. `WidgetLayoutPolicy.rowCapacity(widthDp: Int, heightDp: Int, fontScale: Float, reservedHeightDp: Int, rowHeightDp: Int): Int` returns 0–3; renderer measures localized content first. Null snapshot with failure=false is setup state; failure=true is a read error and removes mutation targets. A safely available previous snapshot may supply qualified prior counts; otherwise counts stay unknown. Do not cache personal rows in another persistent store. Quick capture has its own static render path, never a fabricated data snapshot.

## WG01 — Direct capture widget and safe Activity routing

**Depends on:** none. **Produces:** independently usable Quick capture widget and typed navigation used by later widgets. **Acceptance:** WG-AC-01/02.

**Files:** Create routing/capture files in the map; `R/xml/quick_capture_widget_info.xml`, `R/layout/widget_quick_capture.xml`, `R/layout/widget_quick_capture_compact.xml`, `R/drawable/widget_preview_capture.xml`, `R/drawable/widget_background.xml`. Modify `app/src/main/AndroidManifest.xml`, `A/MainActivity.kt`, `A/ui/LifeOsApp.kt`, `R/values/strings.xml`, `R/values-fa/strings.xml`, `R/values-fr/strings.xml`. Tests: `AT/WidgetIntentsTest.kt`, `AT/WidgetLaunchViewModelTest.kt`, `AI/WidgetCaptureLaunchTest.kt`.

**Interfaces:** consumes existing editor routes; produces the WG01 contracts above and `WidgetLaunchViewModel.enqueue(destination: WidgetDestination)`, `acknowledge(id: UUID)`, `pending: StateFlow<WidgetLaunchRequest?>`, backed by SavedStateHandle. MainActivity supplies pending request/onConsumed callbacks to LifeOsApp.

- [ ] Write failing tests `newCaptureUsesTapTimeNotBrowsedDay`, `warmLaunchPreservesDirtyDraft`, `recreationDoesNotReplayConsumedLaunch`, `malformedTargetIsIgnored`. Assert each kind opens the new editor, has no event ID, makes no repository write before Save, and Cancel returns to the preserved prior draft. A cold launch has Today below the editor; a consumed launch never pushes another entry after recreation. Invalid UUID/date/unknown targets do not crash or mutate storage.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.widget.*'` and the focused `WidgetCaptureLaunchTest` device run; record failures proving missing behavior.
- [x] Implement direct explicit immutable navigation PendingIntents with unique identity data including widget ID and destination. Handle fresh intents in `onCreate`/`onNewIntent`; do not reparse the original intent when restoring saved pending/consumed state. Use single-top delivery without CLEAR_TASK/CLEAR_TOP. Queue requests until NavHost is ready, acknowledge after navigation. Push above an existing editor without popping its draft; if the same new-capture editor is already top, focus/reuse its draft. Never reuse an edit-existing screen as a new entry. NewTask due date and capture time resolve at dispatch. Missing detail entities use existing unavailable states.
- [x] Add the provider and 2×2 labeled grid with the spec's dimensions; minimum-size/font overflow opens the existing QuickCaptureSheet through the typed QuickCapture target. No configuration/database dependency. Register provider exported=false with APPWIDGET_UPDATE metadata and safe preview/initial views. On resize/update rerender using selected app locale/theme.
- [ ] Rerun focused tests and `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`; require PASS. Inspect the widget in a real launcher at default/minimum size and 200% font. Commit this slice only after execution is authorized.

## WG02 — Local runtime storage, snapshot reads and archive separation

**Depends on:** none; shared routing work may precede this. **Produces:** safe configuration, bounded read projection, migration and restore invalidation. **Acceptance:** WG-AC-08/09.

**Files:** Create `K/WidgetModels.kt`, `K/WidgetStore.kt`, `D/widget/WidgetEntities.kt`, `WidgetDao.kt`, `RoomWidgetStore.kt`; modify `D/db/LumaDatabase.kt`, `D/LumaStorage.kt`, `D/habit/HabitDao.kt`, `D/todo/TodoDao.kt`, `D/backup/RoomBackupService.kt`, and backup validation/version checks only where needed. Generate `core/data/schemas/com.luma.lifeos.data.db.LumaDatabase/5.json`. Tests: `DI/WidgetStorageTest.kt`, `WidgetMigrationTest.kt`, `backup/WidgetBackupTest.kt`; extend host backup tests if assumptions change.

**Interfaces:** implements configure/snapshot/dismiss/remove/remap/observeInvalidations above; `RoomWidgetStore(database: LumaDatabase, clock: Clock = Clock.systemUTC()): WidgetStore`, exposed as `LumaStorage.widgets`. Add `LumaDatabase.widgets(): WidgetDao`. WG03 fills execute; until then it rejects every token as INVALID and issues no actionable commands.

- [ ] Write failing tests `defaultDetailsHidden`, `boundedHabitAndTaskSnapshot`, `migration4To5PreservesContent`, `legacyRestoreRevokesCommandsAtomically`. For tasks due yesterday/today/tomorrow/null/completed, assert total=2 and only first two eligible rows; for six scheduled habits assert full total and at most rowLimit rows in created/id order. Hidden details always rows=[] even when rowLimit=3. Test populated migrations 1–4→5 and valid archive versions 1–4, plus failed/cancelled restore preserving runtime state.
- [ ] Run `./gradlew :core:data:testDebugUnitTest :core:data:assembleDebugAndroidTest`; run the three named device classes (see verification commands below), observing expected new test failures.
- [x] Define the two Room tables, command index and cascading instance relation; add explicit 4→5 migration with no destructive fallback. Typed version-1 payload decoding remains with WG03, when actionable command payloads are introduced.
- [x] Implement bounded SQL habit rows/counts with the spec's effective schedule/date predicates and task rows/counts with existing Today eligibility/order, inside the snapshot transaction. `rowLimit=0` returns full summary/count and no rows; hidden details return no rows or tokens. Invalidation observes content tables only.
- [x] Keep archive inventories and `CURRENT_BACKUP_SCHEMA_VERSION` at 4. In the live restore transaction delete commands and reset surviving instances to hidden details with cleared feedback. Host remap drops old runtime state and creates hidden instances. Update `docs/BACKUP_FORMAT.md` for local runtime exclusion.
- [x] Export Room schema 5, pass the non-test `:core:data:compileDebugKotlin` production compile, and document local runtime exclusion in `docs/BACKUP_FORMAT.md`.
- [ ] Run migration/storage/backup verification. No Gradle test or emulator test was run per request, so acceptance verification remains pending. Commit this slice after acceptance review.

## WG03 — Atomic actions, duplicate protection and exact Undo

**Depends on:** WG02. **Produces:** actionable snapshots and complete execute contract. **Acceptance:** WG-AC-03–06/08/09.

**Files:** Modify `D/widget/RoomWidgetStore.kt`, `WidgetDao.kt`; create `D/widget/WidgetCommandPayload.kt` and `WidgetCommandExecutor.kt` if needed to keep transaction policy separate from read projection. Reuse `D/repository/RoomHabitRepository.kt` and `RoomTodoRepository.kt` unchanged unless a transaction-safe helper is necessary. Tests: `DI/WidgetCommandTest.kt`.

**Interfaces:** `WidgetStore.snapshot` now issues/reuses commands for permitted visible rows; `execute` returns WidgetActionResult. Internal payload actions are HABIT_LOG, TASK_COMPLETE, HABIT_UNDO, TASK_UNDO; payload signatures follow spec section 6 and include fixed result log/revision IDs. All later Android code dispatches opaque tokens only.

- [ ] Add tests `duplicateDeliveryWritesOnce`, `lostResultThenRestartUsesTerminalOutcome`, `originalTokenAfterUndoCannotReplay`, `twoInstancesSameHabitCountAcceptOne`, `trackingMidnightRejectsOldDate`, `taskMidnightRejectsOldDate`, `taskRevisionConflictNeverOverwrites`, `undoChangedResultRefusesDelete`, `restoreAndHideDetailsRevokeOldTokens`. Assert exact log count=1 on duplicate delivery, count=0 after Undo plus delayed original delivery; task timestamp/revision unchanged after a terminal replay following reopen; stale/removed/config-disabled commands create no records. Use injected Instants around midnight in Asia/Tehran versus UTC, skip/pause/schedule/deletion races, and a rolled-back storage failure followed by same-token retry.
- [ ] Run the focused `WidgetCommandTest` instrumentation class and confirm the new safety tests fail before issuing actionable tokens.
- [x] Implement same-fingerprint READY token reuse and explicit rejection of superseded commands. Habit fingerprints include date/zone, effective schedule fields, skip and expected count; task fingerprints include date and expected revision. `execute` checks persisted ownership/token identity, returns terminal outcomes without writes, and checks current configuration and live row eligibility before executing any READY command. A returned old success cannot reactivate a revoked Undo token. In one Room transaction perform checks, existing repository mutation, terminal outcome and Undo token/feedback update. Do not use a process mutex as the sole duplicate guard. Fixed result IDs and accepted occurredAt survive ambiguous retry through the transaction record.
- [x] Implement spec Undo checks: exact log value comparison before delete; missing log succeeds as UNDONE; task Undo requires the committed completion revision. Superseding feedback rejects old unused Undo. Dismiss clears feedback/revokes its Undo without uncommitting the original action. Transient failures preserve READY payload and retry token; permanent stale/conflict/missing outcomes remain terminal. Prune records older than seven days and reject absent tokens; never execute data inferred from the URI.
- [ ] Rerun safety cases plus existing HabitPersistenceTest/TodoPersistenceTest and backup replacement tests; require PASS. Record commit-then-lost-callback evidence separately from failures before commit. Commit this slice before enabling mutation receivers/providers.

WG03 production source compilation passed in `:core:data:compileDebugKotlin` and `:app:compileDebugKotlin`. No Gradle test or emulator test was run, per request, so acceptance verification remains pending.

## WG04 — Background refresh and action dispatch

**Depends on:** WG01–WG03. **Produces:** shared lifecycle/refresh path used by data providers. **Acceptance:** WG-AC-07/12.

**Files:** Create coordinator/worker/action/system receiver files from the map, `A/widget/WidgetActionWorker.kt`, plus `A/widget/WidgetRenderer.kt` (safe setup/summary/error skeleton). Modify `A/LumaApplication.kt`, `A/MainActivity.kt`, `A/language/AppLanguages.kt`, existing theme preference selection boundary, and `app/src/main/AndroidManifest.xml`. Tests: `AT/WidgetRefreshPolicyTest.kt`, `AI/WidgetLifecycleTest.kt`. Use existing test facilities; a WorkManager test dependency is unnecessary if policy/dispatcher interfaces can be tested directly.

**Interfaces:** produces WG04 contracts; worker delegates to `WidgetStore.snapshot/execute` on IO. Mutation receiver validates widget ID/token and enqueues unique per-token work; worker stores no payload from intent beyond those identifiers. Define `WidgetActionWorker(context: Context, params: WorkerParameters): CoroutineWorker` alongside refresh worker if separate execution is needed.

- [ ] Write `quickCaptureAloneSchedulesNoPeriodicWork`, `lastDataWidgetCancelsPeriodicWork`, `invalidationDuringRenderQueuesFollowUp`, `actionWorkerRestartDoesNotReplayCommit`, `failedReadRemovesMutationTargets`, `remappedIdsDropOldTokens`. Assert one unique 30-minute periodic request, no overlapping renderer, and no dropped refresh after a write arriving during render. Test actual app process absent for widget actions; simulate delayed work after both relevant midnights and timezone change.
- [ ] Run focused JVM policy/device lifecycle tests and observe new failures.
- [x] Implement serialized APPEND_OR_REPLACE refresh work; periodic work enqueues it instead of rendering concurrently. Reconcile IDs and scheduling on foreground return, removal/remap hooks and permitted system events. Observe Room content tables while the app process lives. Enqueue refresh after command/Undo commits; successful restore invalidates observed content tables. Never query Room in a receiver or start usage/health integration from widget workers.
- [x] Add immutable explicit non-exported mutation dispatch, canonical token/widget-ID validation and generic read errors. Action workers retry the original token only for failed storage execution; stale/missing/conflict outcomes finish. App theme/language selection refreshes data and Quick capture widgets, including pre-33 language selection. Quick capture refresh uses its static provider and does not query widget data.
- [x] Run a fresh non-test `:app:assembleDebug` and `:app:lintDebug`; both passed.
- [ ] Run the focused JVM/device lifecycle checks and verify reboot scheduling, removal, cold-process actions and persisted feedback in a launcher on disposable data. These checks remain pending per request. The user requested a commit message; no commit was created.

WG04 source implementation is complete and `:app:assembleDebug` passed. No Gradle test or emulator/launcher check was run, per request; acceptance verification remains pending.

## WG05 — Habits widget and responsive shared rendering

**Depends on:** WG01–WG04. **Produces:** usable Habits provider. **Acceptance:** WG-AC-01/03/06/10.

**Files:** Create `A/widget/HabitsWidgetProvider.kt`, `WidgetLayoutPolicy.kt`; finish `WidgetRenderer.kt`. Create `R/xml/habits_widget_info.xml`, `R/layout/widget_data.xml`, `widget_habit_row.xml`, `widget_feedback.xml`, `R/drawable/widget_preview_habits.xml` and necessary themed XML colors/backgrounds. Modify manifest and all three string catalogs. Add insert-if-absent default instance initialization to `K/WidgetStore.kt`, `D/widget/WidgetDao.kt` and `RoomWidgetStore.kt` so first refresh cannot overwrite a concurrent user configuration. Tests: `AT/WidgetLayoutPolicyTest.kt`, `AI/HabitsWidgetTest.kt`.

**Interfaces:** consumes WidgetSnapshot/WidgetRow.Habit, WidgetIntents, coordinator; produces complete habit rendering and reusable header/footer/feedback layout. Provider only reconciles instance lifecycle and requests refresh; no business writes in the provider.

- [ ] Add hosted tests `summaryNeverExposesNamesOrActions`, `habitRowLogsAndKeepsUndoOffPreview`, `readErrorRetainsQualifiedCountAndRefresh`, `longRtlFeedbackReducesRowCapacity`. Assert target/skip states have no Log PendingIntent; name opens exact ID/date; a changed date/count token is rejected by real store integration. After a row leaves the prefix, failed Undo still has visible Retry. At 180×160dp/200% text capacity reaches zero before recovery is clipped.
- [ ] Run focused tests; record expected failures. Use `AppWidgetHost`/inflated RemoteViews for wiring/layout checks; Compose tests alone do not exercise the launcher surface.
- [x] Render headers, zone difference, full summary, 0–3 measured rows, empty-library versus empty-scope state, Open/Create and Refresh. Show last successful snapshot date/time; no optimistic count. Reserve retained feedback before rows; replace rows with feedback/summary if necessary. Apply spec typography/palette/targets/RTL, full accessible titles and indexed-safe actions. Publish fictional preview and safe initial layout; do not introduce mutable collection PendingIntents or RemoteViewsService for three rows.
- [ ] Verify repeated intentional +1 after refresh has a fresh token, while identical passive rerenders reuse READY tokens. Exercise default/short/large sizes in English/French/Persian and both themes. Rerun safety/render tests and build/lint; commit this slice.

WG05 source implementation is complete and `:app:assembleDebug` passed. No Gradle tests or emulator/launcher checks were run, per request; acceptance verification remains pending. No commit was created.

## WG06 — Tasks widget

**Depends on:** WG05 for shared layout; WG01–WG04 contracts. **Produces:** usable Tasks provider. **Acceptance:** WG-AC-01/04/06/10.

**Files:** Create `A/widget/TasksWidgetProvider.kt`, `R/xml/tasks_widget_info.xml`, `R/layout/widget_task_row.xml`, `R/drawable/widget_preview_tasks.xml`; extend shared renderer, manifest and three string catalogs. Tests: `AI/TasksWidgetTest.kt`.

**Interfaces:** consumes WidgetRow.Task and existing Task/NewTask/Tasks destinations; reuses command/feedback/refresh pipeline without a second action-state implementation.

- [ ] Write `eligibleScopeExcludesUndatedFutureCompleted`, `completionLeavesUndoVisibleWhenTaskDisappears`, `conflictingCompletionOffersOpenWithoutOverwrite`, `missingTaskNeverOffersCreateRetry`, `addTaskUsesLaunchDateAfterMidnight`. Assert the full eligible count differs correctly from the 3-row prefix; dates remain civil LocalDates; stale Undo cannot reverse a newer task edit/recompletion. Add French long-title/Persian 200% text recovery layout cases.
- [ ] Run the TasksWidgetTest class with existing Todo persistence/editor regressions; observe new widget assertions fail.
- [ ] Render scope/date, eligible ordered prefix, Complete, Add task/Open tasks and truthful empty/error states. Reuse exact command tokens and feedback even after completed rows disappear. Completion never uses a blind checkbox toggle; user opens the app for edit or conflict review. Names and row targets are absent in summary mode.
- [ ] Rerun focused widget and Todo tests, build/lint, and inspect multiple independent Tasks instances after app/widget mutations. Commit this slice.

## WG07 — Configuration, discovery and privacy help

**Depends on:** WG01–WG06. **Produces:** complete setup/reconfiguration and discoverability. Until this task is complete, do not ship Habits/Tasks providers outside development builds; their required configuration Activity is a release dependency. **Acceptance:** WG-AC-08/11.

**Files:** Create `A/widget/WidgetConfigurationActivity.kt`, `A/ui/widgets/WidgetSettingsRoute.kt`; modify `A/ui/settings/SettingsRoute.kt`, `A/ui/LifeOsApp.kt`, provider metadata, manifest and string catalogs. Update `docs/PRIVACY.md`, `README.md`. Tests: `AI/WidgetConfigurationTest.kt`, `AI/WidgetDiscoveryTest.kt`.

**Interfaces:** consumes store.configure/remap/remove and platform live provider ownership; produces Settings → Home-screen widgets with provider descriptions, Add buttons and verified existing-instance links. Activity is externally launchable for the configuration action but cannot configure arbitrary/nonowned IDs; no auto-enable details extra is accepted.

- [ ] Write `firstPlacementCancelLeavesNoConfig`, `reconfigureCancelRetainsExistingChoice`, `detailsOffRejectsOldRowAndUndoTokens`, `invalidProviderOrIdIsRejected`, `pinRequestDoesNotClaimPlacement`. Assert no names before opt-in, only the selected instance changes, and denied/unsupported pin leaves current instances untouched. Recheck classic widget-picker placement and pin-then-config behavior on API 28 and 35/36.
- [ ] Run configuration/discovery tests, confirming new failures.
- [ ] Implement required configuration with explicit Save/Cancel; preserve working draft on rotation, do not commit until Save. Save validates live ownership then configures and requests/renders first view before returning OK with EXTRA_APPWIDGET_ID. For in-app changes, provide the verified instance entry even on API 28 without relying on host long-press reconfigure. On pin-supported launchers requestPinAppWidget; otherwise explain system widget-picker steps. Callback confirms only what Android reports; never say Added from the request's boolean return alone.
- [ ] Explain summary count visibility, launcher-managed surfaces, show-details consent and cache/reset limitations; include no permission wizard or forced setup. Reuse existing privacy wording conventions. Validate app-selected locale for background RemoteViews, including possible remote resource locale differences; set user-facing/accessibility strings explicitly rather than assuming launcher locale.
- [ ] Rerun tests, builds/lint and privacy/intent inspection; require no sensitive content in PendingIntent extras, previews or logs. Commit this slice.

## WG08 — Integrated acceptance and handoff evidence

**Depends on:** WG01–WG07. **Produces:** evidence-backed release decision. **Acceptance:** all WG-AC criteria.

**Files:** Create `docs/WIDGET_ACCEPTANCE_2026-10-19.md` (date can change to actual execution date), `AI/WidgetAcceptanceTest.kt`, `DI/WidgetScaleTest.kt`; update `docs/WIDGET_TASKS.md` and widget section of audit backlog with actual results, not planned passes.

**Interfaces:** no new product API; validates the shipped contracts as a whole.

- [ ] Add/run integrated cases for commit-then-lost-result, interrupted restore, old token after Undo, two instances, process death, stale cached view after midnight, host ID remap and detail toggle. Use fictional data; never clear/restore/reset the owner's installed application. Prove action payloads/revisions/log IDs in persisted storage, not just rendered counts.
- [ ] Profile fixed query/row bounds with 1,000 habits, 10,000 tasks and existing large history fixture. Record cold/warm render duration and payload size on the acceptance device. Budget: no DB work on receiver main thread, at most 3 row projections per instance, no full-list UI scan, and no ANR/TransactionTooLargeException; flag excessive latency with measured evidence rather than changing dependencies arbitrarily.
- [ ] Run required JVM suites, builds, lint and full app/data device suites because navigation, persistence and restore are shared. Commands below are expectations for future execution, not results of this planning turn. Classify each failure against the current baseline; don't waive unrelated failures silently.
- [ ] Perform launcher add/remove/resize/reconfigure and pin acceptance on API 28 and 35/36; use at least one second launcher/device family for host differences. Check en/fr/fa, RTL, 1.0/1.3/2.0 font, light/dark, portrait/landscape, 180×160 minimum and large bounds, delayed/failed reads/writes, reboot/force-stop/unlock and real TalkBack/keyboard traversal. Record any unavailable combination as blocked, not passed.
- [ ] Record build commit/APK hash, device/API/launcher, locale/theme/font/bounds, fixture and expected/actual/evidence for each criterion. Screenshots use disposable data; redact paths/identifiers. Update task statuses only for observed results; retain unverified native accessibility/host/reset-cache limitations. Commit acceptance docs and focused fixes with their own regression checks.

## Verification commands and evidence expectations

Focused JVM/build checks (filter tests to each task while developing):

```sh
./gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.widget.*'
./gradlew :core:domain:test :app:testDebugUnitTest :core:data:testDebugUnitTest :integration:usage:testDebugUnitTest :integration:health:testDebugUnitTest
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest
./gradlew :app:lintDebug :core:data:lintDebug :integration:usage:lintDebug :integration:health:lintDebug
```

For a named app device class, use the class's full package, for example:

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.widget.WidgetCaptureLaunchTest
./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.data.WidgetCommandTest
```

Run full app/data instrumentation at WG08. The current acceptance docs record an uncached UTP host-output artifact preventing Gradle connected runs in a previous environment; if still blocked, use the established disposable direct-ADB APK workflow in [TODO_ACCEPTANCE_2026-10-19.md](../../TODO_ACCEPTANCE_2026-10-19.md), recording exact current commands/results. A built test APK is not a passed test. Keep fresh evidence under `docs/ui-ux-audit/evidence/widgets/` when screenshots are available.

Before beginning WG01, reread current working-tree navigation/editor contracts and preserve existing user changes. Before WG02, confirm no newer migration has claimed version 5; if it has, advance the additive migration and update this spec/plan consistently before editing schema files. WG02 confirmed version 5 was available, implemented its additive migration and exported schema 5. Review implementation-ready documents as a package; no implementation or installation had been performed before WG01.
