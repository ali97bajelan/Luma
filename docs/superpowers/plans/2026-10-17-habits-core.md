# Counted Habits Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver offline counted habits, individual completion history, safe persistence/backup, and truthful Timeline/weekly reflection.

**Architecture:** Add a standalone habit aggregate to core:domain/core:data and feature UI to app. Add a mixed Timeline read model without fabricating life_event rows or changing existing entry counters. Preserve historical schedules and optional occurrence precision.

**Tech Stack:** Existing Kotlin, Room/SQLite, coroutines/Flow, Compose Material 3, SavedStateHandle, JUnit and Android instrumentation.

**Spec:** [Counted habits, reminders and navigation](../specs/2026-10-17-habits-and-navigation-design.md)

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

- Retrying a queued +1 after process recreation must not duplicate it or discard other intentional taps: T22/T23/T24 retry tests.
- An unknown occurrence time must stay null, including after correction/restore, without entering timed ordering: T22/T23/T25 precision tests.
- A target/weekday edit made today must not change yesterday or today's target: T22/T23/T24 effective-date tests.
- Mixed-source invalidation at a page boundary must preserve app-usage visibility and bounded windows: T25 500-row paging tests.
- Restoring an older archive must clear new habit tables, retain legacy data and stop existing reminder configuration: T23 compatibility/rollback tests.

## File structure and shared interfaces

New domain package: `core/domain/src/main/kotlin/com/luma/lifeos/domain/habit/`.
Use UUID for HabitId and LogId at this boundary, matching existing catalog APIs.
Split values (`HabitModels.kt`), date/count rules (`HabitRules.kt`), input contracts
(`HabitInputs.kt`), repository (`HabitRepository.kt`) and weekly projections
(`HabitReview.kt`). No single file owns UI and persistence rules.

New data package: `core/data/src/main/kotlin/com/luma/lifeos/data/habit/` with
`HabitEntities.kt`, `HabitDao.kt`, `HabitMapping.kt`, `RoomHabitRepository.kt`.
Keep backup changes in existing `data/backup/`. New `ui/habits` files separate
list, editor, detail, history, UI state and view-model responsibilities.

Domain values produced by T22:

```kotlin
enum class HabitLifecycle { ACTIVE, PAUSED, ARCHIVED }
enum class HabitDayState { NOT_SCHEDULED, PAUSED, ARCHIVED, NO_RECORDS, PARTIAL, TARGET_REACHED }
data class HabitDefinition(val id: UUID, val name: String, val unitLabel: String?,
    val start: LocalDate, val createdAt: Instant, val updatedAt: Instant)
data class HabitSchedule(val id: UUID, val habitId: UUID, val effectiveFrom: LocalDate,
    val target: Int, val weekdays: Set<DayOfWeek>, val lifecycle: HabitLifecycle)
data class HabitLog(val id: UUID, val habitId: UUID, val date: LocalDate, val zone: ZoneId,
    val occurredAt: Instant?, val recordedAt: Instant, val updatedAt: Instant)
data class HabitDay(val habit: HabitDefinition, val date: LocalDate, val zone: ZoneId,
    val schedule: HabitSchedule?, val count: Int, val skipRemaining: Boolean)
data class HabitHistoryPage(val logs: List<HabitLog>, val offset: Int, val total: Int)
data class HabitSaveInput(val name: String, val unitLabel: String?, val start: LocalDate,
    val target: Int, val weekdays: Set<DayOfWeek>, val zone: ZoneId)
data class HabitScheduleInput(val target: Int, val weekdays: Set<DayOfWeek>,
    val lifecycle: HabitLifecycle)
data class HabitLogInput(val habitId: UUID, val date: LocalDate, val zone: ZoneId,
    val occurredAt: Instant?)
```

`HabitWeek` contains `start`, `through`, `zone`, `partial` and ordered
`HabitWeekRow(habit, days: List<HabitDay>)`; it never combines counts across units.
`HabitDay` exposes derived `state: HabitDayState` without modifying count or the
independent skip flag. `scheduleFor` returns the latest version even when paused,
archived or not a selected weekday; eligibility/state is a separate calculation.

```kotlin
interface HabitRepository {
    fun observeDay(date: LocalDate): Flow<List<HabitDay>>
    fun observeChanges(): Flow<Unit>
    suspend fun trackingZone(): ZoneId?
    suspend fun get(id: UUID): HabitDefinition?
    suspend fun schedules(id: UUID): List<HabitSchedule>
    suspend fun getLog(id: UUID): HabitLog?
    suspend fun create(id: UUID, input: HabitSaveInput): HabitDefinition
    suspend fun rename(id: UUID, name: String, unitLabel: String?)
    suspend fun schedule(id: UUID, input: HabitScheduleInput): HabitSchedule
    suspend fun cancelPendingSchedule(id: UUID)
    suspend fun log(id: UUID, input: HabitLogInput): HabitLog
    suspend fun updateLog(id: UUID, input: HabitLogInput): HabitLog
    suspend fun deleteLog(id: UUID)
    suspend fun setSkipRemaining(id: UUID, date: LocalDate, skip: Boolean)
    suspend fun history(id: UUID, date: LocalDate?, offset: Int, limit: Int = 50): HabitHistoryPage
    suspend fun week(start: LocalDate, through: LocalDate): HabitWeek
    suspend fun deleteHabit(id: UUID)
}
```

Construct RoomHabitRepository with `Clock`; it supplies audit times and computes
today/tomorrow in the stored zone. `updateLog` cannot change habitId. `history`
uses 1..50 rows and stable date/recordedAt/ID ordering. `week` accepts an inclusive
range of at most seven dates and caps future dates using the stored habit zone.
Observe methods emit committed snapshots; clear cancelled coroutines normally.

---

### T22: Domain and deterministic habit rules

**Files:**
- Create the five domain files named above.
- Create `core/domain/src/test/kotlin/com/luma/lifeos/domain/habit/HabitRulesTest.kt`.
- Create `core/domain/src/test/kotlin/com/luma/lifeos/domain/habit/HabitTimeTest.kt`.

**Interfaces:**
- Consumes: `Clock`, `Instant`, `LocalDate`, `ZoneId`, `DayOfWeek`, UUID.
- Produces: shared types/contract above; `scheduleFor(date: LocalDate, versions: List<HabitSchedule>): HabitSchedule?`, `habitLogTime(date: LocalDate, time: LocalTime?, zone: ZoneId, now: Instant, preferredOffset: ZoneOffset? = null): Instant?`, and `nextHabitEffectiveDate(now: Instant, zone: ZoneId): LocalDate`.

- [x] **Step 1: Write failing domain tests.** Pin these assertions:

```kotlin
assertEquals(8, scheduleFor(today, versionsWithTomorrowTarget10)!!.target)
assertEquals(10, scheduleFor(today.plusDays(1), versionsWithTomorrowTarget10)!!.target)
assertNull(habitLogTime(yesterday, null, zone, now))
assertEquals(HabitDayState.TARGET_REACHED, dayWithNineLogsAgainstEight.state)
assertEquals(9, dayWithNineLogsAgainstEight.count) // no cap at target
assertTrue(partialDayWithSkip.skipRemaining) // still retains all three logs
```

Also reject target 0/1000, blank/overlong Unicode labels, no weekdays, mismatched
habit IDs, future date/instant and DST gaps. Pin earlier-offset default and an
explicit valid overlap offset. A pending tomorrow version supersedes only another
version with the same effective date, and cancelling it restores the prior rule.
Use fixed 17 October 2026 in Asia/Tehran for count/effective-date fixtures.
Pin Europe/Berlin's 29 March 2026 02:30 gap, and 25 October 2026 02:30 overlap
with earlier +02:00/later +01:00 offsets and a clock after each tested date.
For future-start habits, edits preserve the start/initial effective date,
Pause/Resume rejects, and pre-start Archive cannot be reversed by an old ACTIVE
version. Blank unit normalizes to null; a supplied label remains user text.
- [x] **Step 2: Run red.** `./gradlew -PdomainOnly=true :core:domain:test --tests 'com.luma.lifeos.domain.habit.*'`; expect missing new types/rules, not an unrelated toolchain failure.
- [x] **Step 3: Implement values/rules and repository contracts.** Resolve the latest effective schedule even when inactive, then separately derive eligibility/state by weekday/lifecycle; Monday is weekday-mask bit 0 in persistence. Blank unit becomes null, explicit unit length uses Unicode code points. A scheduled date with no logs has count zero and missing evidence, not inferred failure. `habitLogTime` returns null only for unknown time; invalid time raises validation failure. A skip flag never overwrites count/target or derived progress state.
- [x] **Step 4: Run green.** Repeat the focused command and full `./gradlew -PdomainOnly=true :core:domain:test`; both exit 0.
- [x] **Step 5: Commit only domain implementation/tests and update T22 evidence.** Suggested message: `feat(habits): define counted logs and historical schedules`.

T22 evidence (2026-10-17): focused `HabitRulesTest`/`HabitTimeTest` and the full
domain-only JVM suite pass. See [the acceptance log](../../HABITS_ACCEPTANCE_2026-10-17.md).

### T23: Room schema 3, repository and complete backup

**Files:**
- Create four data/habit files named above.
- Modify `core/data/src/main/kotlin/com/luma/lifeos/data/db/LumaDatabase.kt`.
- Modify `core/data/src/main/kotlin/com/luma/lifeos/data/LumaStorage.kt`.
- Modify `core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupArchive.kt`, `BackupValidation.kt`, `BackupSql.kt`, `RoomBackupService.kt`.
- Modify `core/domain/src/main/kotlin/com/luma/lifeos/domain/backup/BackupService.kt` and `app/src/main/java/com/luma/lifeos/ui/backup/BackupSettingsCard.kt` for separate habit/log preview counts.
- Export `core/data/schemas/com.luma.lifeos.data.db.LumaDatabase/3.json`; retain/review 1.json and 2.json.
- Create `core/data/src/androidTest/kotlin/com/luma/lifeos/data/HabitPersistenceTest.kt`, `HabitMigrationTest.kt`, `HabitBackupTest.kt`.
- Add archive inventory regression in `core/data/src/test/kotlin/com/luma/lifeos/data/backup/BackupCodecTest.kt`.
- Update `docs/BACKUP_FORMAT.md` only when schema 3 implementation exists.

**Interfaces:**
- Consumes: T22 models/contracts, existing Room/backup boundaries.
- Produces: `RoomHabitRepository(database: LumaDatabase, clock: Clock = Clock.systemUTC()) : HabitRepository`, `LumaStorage.habits: HabitRepository`, `LumaDatabase.habits(): HabitDao`. `BackupPreview` appends `habitCount: Long = 0` and `habitLogCount: Long = 0` without changing eventCount/photoCount.

- [ ] **Step 1: Write failing Room/archive tests.** One retry UUID inserts once; two different UUIDs at the same instant insert twice. Concurrent writes give the correct count. Deleting one of three logs leaves two, including after reopening. Pending tomorrow schedule edits preserve current/past targets. Skip then another log preserves the skip flag. Inject a failed mutation and assert logs, choice and runtime state all roll back. Reject log-zone/schedule mismatches and reminder runtime without matching config. Verify code-point label limits and ZoneId fields are not mistakenly UUID-validated.
- [ ] **Step 2: Run red/build tests.** `./gradlew :core:data:testDebugUnitTest :core:data:assembleDebugAndroidTest`; run the new Room classes on a disposable device as described in T29. Expect missing schema/entities/behavior. If no device exists record BLOCKED; compilation alone is not a Room pass.
- [ ] **Step 3: Implement all seven schema-3 tables and additive migration.** Use the spec's columns, indexes and FK cascades. Keep UUIDs/date strings canonical, nullable occurrence intact, Monday-bit-0 masks, no destructive fallback. A stable create ID with identical input reconciles; changed payload conflicts. Repository writes use transactions and UUID uniqueness; counts use indexed SQL and paged histories. Preserve habit.start_date and first-schedule agreement; pre-start edits replace the initial version without early opportunities. No future day rows are generated. Expose reminder rows as disabled until T27/T28 rather than scheduling anything prematurely.
- [ ] **Step 4: Extend archive inventory and validation.** Explicit schema-1 list is the original 17 tables; schema 2 adds the two usage tables; schema 3 adds the seven habit tables. Validate settings, ZoneIds, schedule/log graphs, modes and runtime values. Extend `BackupPreview` and all three translations for new separate counts. For a schema-1/2 replacement clear all habit tables; for schema 3 preserve history/configuration but disable reminders, rotate revision UUIDs and clear runtime. Preserve the existing photo mutex/transaction rollback. Mark reminder runtime reconciliation dirty in the same replacement transaction; T28 owns cancellation of OS state on reconciliation.
- [ ] **Step 5: Verify archive/migration behavior.** Tests cover 1→3 and 2→3 with journals, links, photos and usage intact; schema-3 round trip with time-null logs; old-archive replacement removing live habits; unknown version/missing tables/invalid zones/graph rejected before writes; injected replacement failure leaves both old habits and old records intact. Run `./gradlew :core:data:testDebugUnitTest :core:data:assembleDebugAndroidTest :app:assembleDebug`; Room classes must pass on device before completion.
- [ ] **Step 6: Commit only schema/repository/backup implementation, schema exports and evidence.** Suggested message: `feat(habits): persist schedules and logs with portable backup`.

### T24: Functional capture, history and Today entry

**Files:**
- Create `app/src/main/java/com/luma/lifeos/ui/habits/HabitsRoute.kt`, `HabitsScreen.kt`, `HabitsViewModel.kt`, `HabitEditorRoute.kt`, `HabitEditorViewModel.kt`, `HabitDetailRoute.kt`, `HabitHistory.kt`, `HabitUiState.kt`.
- Modify `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt`, `app/src/main/java/com/luma/lifeos/MainActivity.kt`.
- Modify `app/src/main/java/com/luma/lifeos/ui/today/TodayRoute.kt`, `TodayScreen.kt`.
- Create `app/src/main/java/com/luma/lifeos/ui/habits/TodayHabitsCard.kt`.
- Modify all three `app/src/main/res/values*/strings.xml` bundles (values, values-fa, values-fr only).
- Create `app/src/test/java/com/luma/lifeos/ui/habits/HabitsViewModelTest.kt`, `HabitEditorViewModelTest.kt`.
- Create `app/src/androidTest/java/com/luma/lifeos/habits/HabitCaptureFlowTest.kt`.

**Interfaces:**
- Consumes: `HabitRepository`; the shell's shared nullable selected date and current device-day observation.
- Produces: `HabitsRoute(repository, date: LocalDate?, onSelectDate: (LocalDate) -> Unit, onToday: () -> Unit, onCreate: () -> Unit, onHabit: (UUID, LocalDate) -> Unit)`; null date follows tracking-zone today. `HabitEditorRoute(repository, habitId: UUID?, onDone: (UUID) -> Unit, onBack: () -> Unit)`; `HabitDetailRoute(repository, habitId: UUID, date: LocalDate, logId: UUID?, onEdit: (UUID) -> Unit, onBack: () -> Unit)`. Add routes `habits`, `habit_editor?habitId={habitId}`, `habit_detail/{habitId}/{date}?logId={logId}`. Detail's logId is optional; default no selected log.

- [ ] **Step 1: Write failing state/UI tests.** Three accepted activations produce three request UUIDs; retrying a failed one retains its UUID, and recreation reconciles committed requests. Date changes while a write completes retain its captured date. Assert 9/8, partial+skip, undo one selected log, future input disabled with explanation, no habit empty state distinct from none scheduled. Pin create-draft restoration, duplicate-save blocking, Unicode/field validation and failures retaining drafts. Blank unit remains null and displays localized default after a language switch. Moving a log preserves ID/recordedAt, updates both dates and leaves failed-edit drafts intact. Pin pending-tomorrow preview/cancellation and enabled today controls; future-start edits/archive follow the spec's exception and archived history stays readable.
- [ ] **Step 2: Run red.** `./gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.ui.habits.*' :app:assembleDebugAndroidTest`; run HabitCaptureFlowTest on a disposable package for expected behavior failures.
- [ ] **Step 3: Implement the routes/view models.** Keep accepted per-tap IDs/payloads in SavedStateHandle, serialize queued writes and reconcile a retained ID through getLog/create before retry. Do not move/re-sort rows after logging. Use saved drafts, independent loading/error states, explicit effective-date labels, confirmation for discard/permanent deletion, per-log correction and committed feedback/Undo. Construct actual instants or null precision through T22 rules; never convert unknown time to midnight. Setup the single tracking zone once, clearly labelled; retain explicit civil-date selection and render tracking-day differences during travel.
- [ ] **Step 4: Wire Today and composition root.** Supply `storage.habits`; add compact preview after the existing summary with at most three rows and View all. Keep the existing feed usable if habit observation fails. A secondary habits route makes this slice functional before T26 promotes it into the primary bar. Preserve all Quick Capture actions/drafts and old route contracts. Localize all controls, plural/state/error copy and TalkBack progress; arbitrary user unit text is not machine-inflected or translated.
- [ ] **Step 5: Run green and relevant regression.** Repeat focused tests; run TranslationCatalogTest, AppNavigationTest and LanguageFlowTest with HabitCaptureFlowTest. Verify actual Persian/French resources, 320 dp/2.0 text, separate name/open and + Log targets, 48 dp hit areas, retained focus. Record manual speech separately from semantics tests.
- [ ] **Step 6: Commit the functional habit UI/Today slice and update T24 evidence.** Suggested message: `feat(habits): add counted capture and editable history`.

### T25: Unified mixed Timeline and weekly habits reflection

**Files:**
- Create `core/domain/src/main/kotlin/com/luma/lifeos/domain/habit/HabitTimeline.kt`.
- Modify `core/domain/src/main/kotlin/com/luma/lifeos/domain/repository/TimelineRepository.kt`.
- Create `core/data/src/main/kotlin/com/luma/lifeos/data/habit/HabitTimelineSql.kt`.
- Modify `core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomTimelineRepository.kt`.
- Modify `app/src/main/java/com/luma/lifeos/ui/timeline/TimelineViewModel.kt`, `TimelineRoute.kt`; create `HabitTimelineCard.kt` alongside them.
- Create `app/src/main/java/com/luma/lifeos/ui/review/ReviewHabits.kt`; modify `WeeklyReviewRoute.kt`, `ReviewSection.kt`, `LifeOsApp.kt` for a separate observed habit section.
- Create `core/domain/src/test/kotlin/com/luma/lifeos/domain/habit/HabitReviewTest.kt`, `core/data/src/androidTest/kotlin/com/luma/lifeos/data/HabitTimelineTest.kt`, `HabitReviewPersistenceTest.kt`.
- Extend `app/src/test/java/com/luma/lifeos/ui/timeline/TimelineViewModelTest.kt`; create `app/src/androidTest/java/com/luma/lifeos/habits/HabitTimelineFlowTest.kt`, `HabitReviewFlowTest.kt`.

**Interfaces:**
- Consumes: T22/T23 log/history/week projections and existing TimelineRepository.
- Produces: `sealed interface TimelineFeedItem` with `Entry(record: LifeEventRecord)` and `Habit(log: HabitLog, name: String, unitLabel: String?)`; keys `event:<uuid>` / `habit:<uuid>`. `TimelineFeedPage(items: List<TimelineFeedItem>, offset: Int, totalCount: Int, eventCount: Int, habitLogCount: Int)`. Add `suspend fun feed(date: LocalDate, zone: ZoneId, offset: Int, limit: Int, showAppUsage: Boolean = true): TimelineFeedPage` to TimelineRepository; provide a legacy-page default adapter for existing fakes/callers and override in Room. Keep page/get unchanged. `ReviewHabits(repository: HabitRepository, start: LocalDate, through: LocalDate, onHabit: (UUID, LocalDate) -> Unit)` independently observes committed habit changes and queries week.

- [ ] **Step 1: Write failing mixed-source tests.** Fixture 500 mixed entries/logs, including coincident UUID strings across source kinds, date-only logs, identical timestamps, and >300 visible rows. Assert every log appears once, the unknown-time group precedes timed rows, actual-time order is stable and every page/fallback honors hidden app usage. Edit/delete during paging and verify count/window/anchor coherence. A date with no instant day range still returns date-only logs. Test travel with saved habit-date attribution and visible time-zone labels.
- [ ] **Step 2: Write failing Review tests.** Yesterday target 8 remains 8 after tomorrow target 10; skip with three logs retains count 3 and expected target 8; paused/future dates contribute no target; above-target repetitions remain actual. A habits-only week is meaningful rather than globally empty. Current week caps through habit-today and previous comparison uses the matching elapsed civil weekdays. Opening evidence selects the source habit/date without changing unrelated Review state.
- [ ] **Step 3: Run red.** `./gradlew -PdomainOnly=true :core:domain:test --tests 'com.luma.lifeos.domain.habit.*'`; `./gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.ui.timeline.*' :core:data:assembleDebugAndroidTest :app:assembleDebugAndroidTest`. Run the owning new native classes for real SQL/UI failures.
- [ ] **Step 4: Implement the feed API and SQL.** Use one transaction and a UNION ALL keys projection across legacy rows and habit_log, ordered by precision-group, actual/recorded sort instant, source kind and ID before offset/limit; hydrate only selected IDs. Explicitly typed group metadata prevents treating recorded-at as occurred-at. Add habit/settings/schedule invalidations, prefix LazyColumn keys, preserve 50/150/300 windows and filter-aware fallback. Legacy eventCount excludes habits and app usage; habitLogCount is separate. Open habit source detail with its log ID and keep existing entry/memory actions.
- [ ] **Step 5: Implement separate Review projection and UI.** T23's week method reads bounded counts/schedules, never full journal bodies or unbounded histories. Add Habits to Review's section keys/scroll navigation, empty-state decision and independent retry/loading state. Preserve existing WeeklyReviewRepository statistics/cache algorithm and life-entry counts. Use per-habit counts and explicit partial/missing/skipped labels, no causal inference or completion percentages.
- [ ] **Step 6: Run green/regression.** Repeat owning tests and existing TimelinePagingRouteTest, TimelineFlowTest, WeeklyReviewFlowTest, DailySummaryFlowTest. Assert unchanged summary entry counts and usage metrics on the same fixture, mixed paging bounded at 300, no all-history materialization. Record representative large-fixture query/scroll evidence; do not mark existing performance gaps closed without measurements.
- [ ] **Step 7: Commit only the mixed-feed/Review slice and T25 evidence.** Suggested message: `feat(habits): include each completion in timeline and review`.

## Handoff

After T22–T25 pass, execute [T26 navigation](2026-10-17-habits-navigation.md),
then [T27–T29 reminders and acceptance](2026-10-17-habit-reminders.md).
Update [HABITS_TASKS](../../HABITS_TASKS.md) and TASKS.md with actual results.
These commands are planned checks, not evidence they have run.
