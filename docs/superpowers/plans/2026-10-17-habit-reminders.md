# Habit Reminders and Acceptance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver optional repeating local habit notifications with truthful permissions/status, safe Log one/Snooze actions, and complete feature acceptance.

**Architecture:** Keep schedule generation in pure domain code, desired configuration/runtime in the schema-3 habit tables, and AlarmManager/notifications/WorkManager in app adapters. Reconcile platform state from persisted configuration and validate every action again at its write boundary.

**Tech Stack:** Existing Kotlin/java.time, Room, Android AlarmManager and Notification APIs, WorkManager 2.10.0, Compose and Android/JVM tests.

**Spec:** [Counted habits, reminders and navigation](../specs/2026-10-17-habits-and-navigation-design.md), especially sections 7–10.

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

- Delayed/replayed yesterday notifications must never add today's log or duplicate an undone notification log: T27/T28 revision/date/consumed-token tests.
- OS permission/channel denial must leave tracking usable and avoid false delivery claims: T28 blocked-state tests/device walkthrough.
- Reboot/clock change after several missed slots must schedule future work without a catch-up burst: T27 grid/expiry and T28 reconciliation tests.
- A restore/delete/off operation racing a receiver must cancel obsolete state and reject callbacks against replaced records: T28 transactional action/restore tests.
- Target attainment, partial skip and deleting one log must change only future eligible reminders and preserve all actual history: T27 eligibility/T28 UI integration tests.

## File structure and contracts

Create `core/domain/src/main/kotlin/com/luma/lifeos/domain/habit/HabitReminderModels.kt`,
`HabitReminderPlanner.kt`, `HabitReminderRepository.kt`.
Create `core/data/src/main/kotlin/com/luma/lifeos/data/habit/RoomHabitReminderRepository.kt`.
Create Android adapters in `app/src/main/java/com/luma/lifeos/habits/reminders/`:
`HabitReminderScheduler.kt`, `HabitNotificationPublisher.kt`,
`HabitReminderReceiver.kt`, `HabitReminderWorker.kt`, `HabitReminderCoordinator.kt`,
`HabitReminderIntent.kt`. Keep configuration UI in `ui/habits/HabitReminderEditor.kt`
and `HabitReminderViewModel.kt`. Add `HabitReminderFeedbackStore.kt` to the Android
package for bounded device-only action-result codes. No new Gradle module/dependency.

Exact domain values:

```kotlin
sealed interface ReminderSchedule {
    data class Times(val minutes: List<Int>) : ReminderSchedule
    data class Interval(val minutes: Int, val startMinute: Int, val endMinute: Int) : ReminderSchedule
}
data class HabitReminderConfig(val habitId: UUID, val enabled: Boolean,
    val schedule: ReminderSchedule, val revision: UUID)
data class ReminderSlot(val habitId: UUID, val date: LocalDate, val at: Instant,
    val expiresAt: Instant, val key: String, val revision: UUID)
data class HabitReminderRuntime(val revision: UUID, val lastDeliveredSlot: String?,
    val consumedActionToken: UUID?, val snoozeSlot: String?, val snoozeUntil: Instant?,
    val needsReconcile: Boolean)
data class ReminderPlanningInput(val config: HabitReminderConfig, val zone: ZoneId,
    val schedules: List<HabitSchedule>, val todayCount: Int, val todaySkip: Boolean,
    val runtime: HabitReminderRuntime?)
```

Runtime holds at most the current slot's consumed token; expired slots fail
eligibility regardless of that token. Slot keys include attributed date, minute,
chosen offset and normal/snooze identity; never use only an hour or habit name.

```kotlin
interface HabitReminderRepository {
    suspend fun configuration(habitId: UUID): HabitReminderConfig?
    suspend fun save(habitId: UUID, enabled: Boolean, schedule: ReminderSchedule): HabitReminderConfig
    suspend fun planningInputs(now: Instant): List<ReminderPlanningInput>
    suspend fun recordDelivery(slot: ReminderSlot): Boolean
    suspend fun consumeLogAction(slot: ReminderSlot, actionId: UUID, at: Instant): HabitLog?
    suspend fun snooze(slot: ReminderSlot, at: Instant): Boolean
    suspend fun markReconciled(habitId: UUID, revision: UUID)
}
fun nextReminderSlot(input: ReminderPlanningInput, now: Instant): ReminderSlot?
fun dueReminderSlot(input: ReminderPlanningInput, requested: ReminderSlot, now: Instant): ReminderSlot?
```

`planningInputs` reads only current/future relevant schedule versions, current-day
count/choice and bounded runtime; it does not materialize all logs. `save` rotates
the UUID revision and clears obsolete runtime in one transaction; no row means
reminders off. `recordDelivery`, consume and snooze revalidate revision, current
habit-day/slot expiry, lifecycle, target and skip transactionally. A consumed
action returns its extant log or null after undo, never reinserts it. Distinct
manual + Log actions continue through T22's repository and may exceed target.

---

### T27: Pure scheduling and durable reminder configuration

**Dependencies:** T22/T23; full UI delivery waits for T26.

**Files:**
- Create domain/data reminder files above.
- Extend `core/data/src/main/kotlin/com/luma/lifeos/data/habit/HabitDao.kt`, `HabitMapping.kt`, `RoomHabitRepository.kt` to mark reconciliation dirty after eligibility changes.
- Modify `core/data/src/main/kotlin/com/luma/lifeos/data/LumaStorage.kt` to expose reminders.
- Create `core/domain/src/test/kotlin/com/luma/lifeos/domain/habit/HabitReminderPlannerTest.kt`.
- Create `core/data/src/androidTest/kotlin/com/luma/lifeos/data/HabitReminderPersistenceTest.kt`.

**Interfaces:**
- Consumes: T22 dates/schedules/log contracts and T23's seven tables.
- Produces: reminder values/repository/planner above and `LumaStorage.habitReminders: HabitReminderRepository` via `RoomHabitReminderRepository(database, clock)`.

- [ ] **Step 1: Write failing grid tests.** At 07:59 with interval 120 and [08:00,20:00), next is 08:00; after 08:00, next is 10:00; 20:00 is excluded. A target of eight never changes this six-slot grid. Sort/deduplicate selected times, validate 1–24 unique minutes, reject interval <15/>720 or not divisible by 15 and reversed/overnight windows. Pin a far-future habit start, a pending resume after a paused segment, DST gap skipping and one earlier-offset overlap delivery.
- [ ] **Step 2: Write failing eligibility/runtime tests.** At target or skip, no future slot today; deleting a log below target returns only a future slot; clearing skip explicitly resumes. A delayed slot expires at next nominal slot/day end, no backlog. Specific-time and interval snoozes use ten minutes, remain within the active date/window and coalesce with an earlier regular slot. Repeated configuration save rotates revision. Duplicate delivery/action/snooze cannot duplicate a completion; replay after per-log undo stays consumed. Off/restore revision mismatch and deleted habit fail without mutation.
- [ ] **Step 3: Run red.** `./gradlew -PdomainOnly=true :core:domain:test --tests 'com.luma.lifeos.domain.habit.HabitReminderPlannerTest'`; compile/run the owning Room class on a disposable device. Missing types/behavior must explain failure.
- [ ] **Step 4: Implement pure planning and repository transactions.** Scan active current/future schedule segments for the next eligible weekday rather than searching an arbitrary seven-day horizon from now; the first start may be months away. Use fixed-zone civil clock grids and actual Instant comparisons. Runtime/configuration mappings use existing schema 3, no schema bump. Stable action IDs are UUIDs derived from revision+slot+action kind; consume marker and log insert share one transaction. Mark obsolete pending slots dirty after logs, undo, choices and schedule edits; unknown precision never becomes a reminder instant.
- [ ] **Step 5: Run green.** Full domain suite and HabitReminderPersistenceTest pass. Verify backup validators accept all legitimate configurations/runtime values and still reject malformed combinations. Repeat HabitBackupTest after adding mappings. `./gradlew :core:data:testDebugUnitTest :core:data:assembleDebugAndroidTest` exits 0.
- [ ] **Step 6: Commit planner/configuration and T27 evidence.** Suggested message: `feat(habits): plan repeating reminders independently of targets`.

### T28: Android reminders, actions and user controls

**Dependencies:** T24/T26/T27.

**Files:**
- Create Android adapters/configuration UI files listed above.
- Modify `app/src/main/AndroidManifest.xml`, `LumaApplication.kt`, `MainActivity.kt`, `ui/LifeOsApp.kt` and existing `ui/habits/HabitEditorRoute.kt`, `HabitDetailRoute.kt`.
- Modify all three app strings bundles; add a monochrome notification vector following the existing icon system.
- Create `app/src/test/java/com/luma/lifeos/habits/reminders/HabitReminderCoordinatorTest.kt`, `HabitReminderActionTest.kt`, `app/src/test/java/com/luma/lifeos/ui/habits/HabitReminderViewModelTest.kt`.
- Create `app/src/androidTest/java/com/luma/lifeos/habits/HabitReminderFlowTest.kt`, `HabitReminderPlatformTest.kt`.

**Interfaces:**
- Consumes: T27 values/repository, injected `Clock`, Android context and existing WorkManager.
- Produces: `HabitReminderScheduler.schedule(slot: ReminderSlot)`, `cancel(habitId: UUID)`, `cancelAll()`; `HabitNotificationPublisher.show(slot: ReminderSlot, habit: HabitDefinition, count: Int, target: Int)` and `cancel(habitId: UUID)`; `HabitReminderCoordinator.reconcile()` and `handle(slot: ReminderSlot, action: HabitReminderAction)` where enum values are DELIVER, LOG_ONE, SNOOZE, DISMISS. `HabitReminderIntent.parse(intent)` produces a validated typed action, never executes arbitrary route text. `HabitReminderEditor(habitId, repository, onBack)` exposes committed/pending/blocked/error states. `HabitReminderFeedbackStore.put(habitId: UUID, code: ReminderFeedbackCode)` and `consume(habitId: UUID): ReminderFeedbackCode?`, with STALE_ACTION, SNOOZE_WINDOW_ENDED, SAVE_FAILED codes, retain only the latest code per habit without user text.

- [ ] **Step 1: Write failing fake-boundary tests.** Fake clock/scheduler/publisher verify initial off, enable schedules only a future slot, config edit/off cancels previous alarm and posted notification immediately, target/skip suppression, late callbacks revalidated, and scheduler failure remains Pending/Error with a persisted dirty marker. Restart reconciliation repairs a failed platform call without duplicate notifications. Delete/restore races cannot create phantom logs or notifications. Receiver action duplicates/replays share one token; old-day Log one never writes today's log.
- [ ] **Step 2: Write failing configuration/permission tests.** Toggle on from explicit intent requests POST_NOTIFICATIONS only on API 33+, denial keeps habits/logging available, repeat opening never prompts automatically, disabled channel has a settings action. Show readable selected-time/interval preview, same-day validation, ten-minute snooze boundary result and actual time-zone labels. Explicit stale action shows "This reminder is no longer current. Nothing was logged."; rejected snooze shows "No reminder time remains today. Nothing was snoozed." via informational Open notification and destination feedback, including after permission revocation. Passive expiry posts nothing. Sound/vibration follow channel settings. Notification Open selects the identified habit/date without changing saved capture drafts; deleted-source Open shows an unavailable state.
- [ ] **Step 3: Run red.** `./gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.habits.reminders.*' --tests 'com.luma.lifeos.ui.habits.HabitReminderViewModelTest' :app:assembleDebugAndroidTest`; run new native flow/platform classes for real permission/notification behavior.
- [ ] **Step 4: Implement scheduling and lifecycle adapters.** Use inexact `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, ...)` with at most one pending alarm per habit, never an exact/foreground service. Use explicit immutable PendingIntents with stable alarm identity and typed revision/slot extras. Keep a private device-only scheduled-ID registry (no habit text, not in portable backups) so delete/restore can cancel alarms whose database rows disappeared. Reconciliation compares that registry with current desired state; callbacks always validate database eligibility. Register non-exported delivery/action receivers; system broadcast receiver accepts only BOOT_COMPLETED, MY_PACKAGE_REPLACED, TIME_SET and TIMEZONE_CHANGED. Add only POST_NOTIFICATIONS and RECEIVE_BOOT_COMPLETED permissions. Handle notifications after unlock; no direct-boot database access.
- [ ] **Step 5: Implement worker/notification/action paths.** Receivers enqueue bounded WorkManager work with immutable typed input; no unbounded work on the broadcast thread. Reconcile foreground/config/log/choice changes and system events. Platform operations are idempotent and clear dirty state only after scheduling/cancellation succeeds for the same revision. Channel ID is `habit_reminders`, default importance; notification uses a generic public lock-screen version and localized private content. Use one notification tag per habit. Open uses an Activity PendingIntent directly; Log one/Snooze use explicit action receivers. Parse Open in MainActivity onCreate/onNewIntent, consume it once and push source detail without clearing editor stacks. Activity Open cannot itself execute logging. Dismiss updates reminder state only. Action success cancels current notification, schedules the next future opportunity and leaves correction available in the app. Explicit rejection writes device-only feedback and replaces the actionable notification with informative Open content when permitted; passive expiry stays silent. Clear feedback after display and on reset/restore.
- [ ] **Step 6: Wire UI and restore/reset behavior.** Add optional reminders disclosure and detail status/actions, default off. Save desired configuration first, then show pending until platform reconciliation succeeds; requested permission/channel blocks are visibly distinct from off. Return from Settings rechecks status. Backup replacement already rotates/disables configs; foreground/worker registry reconciliation cancels obsolete alarms/notifications. Full app-data reset uses the existing OS reset boundary and verify its cleanup on a disposable install. No notification from restored records until explicit enablement.
- [ ] **Step 7: Run green/regression and actual device checks.** Re-run focused tests and HabitCaptureFlowTest/HabitTimelineFlowTest/BackupSettingsFlowTest. On API 28/32 and 33+ where available exercise allow/deny/channel-off, background/Doze, restart/reboot, clock/timezone change, disable, skip, target, stale action, snooze, restore and reset. Assert no fabricated logs and no catch-up burst. Missing image/device is BLOCKED, not a simulated native pass.
- [ ] **Step 8: Commit reminders and T28 evidence.** Suggested message: `feat(habits): add optional local reminders and safe notification actions`.

### T29: Complete integration and release acceptance

**Dependencies:** T22–T28 implemented; does not silently close older release gaps.

**Files:**
- Create `docs/HABITS_ACCEPTANCE_2026-10-17.md` when execution begins, using spec section 10 and the matrix below.
- Update `docs/VALIDATION.md`, `docs/HABITS_TASKS.md`, `TASKS.md`, `REMAINING_TASKS.md`, `README.md`, `ARCHITECTURE.md`, `docs/BACKUP_FORMAT.md`, `docs/PRIVACY.md` with actual implemented state/evidence.
- Change owning implementation/test files only for concrete acceptance defects.

**Interfaces:**
- Consumes: complete T22–T28 behavior, existing release-check conventions and disposable fixtures.
- Produces: dated PASS/FAIL/BLOCKED/NOT RUN entries per automated/manual acceptance case with device/API/package/locale/width/font scale and evidence path; task completion only after required acceptance passes.

- [ ] **Step 1: Run required JVM suites.** `./gradlew :core:domain:test :app:testDebugUnitTest :core:data:testDebugUnitTest :integration:usage:testDebugUnitTest :integration:health:testDebugUnitTest`; exit 0 with counts recorded. Existing failures are reported with their scope rather than attributed to success.
- [ ] **Step 2: Run builds/lint.** `./gradlew :app:assembleDebug :app:lintDebug :core:data:lintDebug :integration:usage:lintDebug :integration:health:lintDebug :app:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest`. Preserve and report any independently reproducible pre-existing analyzer limitation. Check merged manifest has only intended reminder permissions and no network/exact/full-screen/foreground additions.
- [ ] **Step 3: Execute native data/app regression on a disposable package.** Use the isolated application-ID init-script pattern in `docs/UX_ACCEPTANCE_2026-10-14.md`; verify app ID before install. Run all new habit classes plus existing migration/backup/navigation/language/Timeline/Review/capture suites. Use connectedDebugAndroidTest or the documented direct AndroidJUnitRunner fallback. Do not install over the user's main Luma package, reset it, grant unrelated provider access or alter their private records.
- [ ] **Step 4: Run the feature journey.** Fresh install → More/catalogs/settings → create daily habit target 8 → log three times → inspect three Timeline items → correct/delete/undo one → skip remainder → manually log while skipped → explicitly resume reminders → pass target to 9/8 → edit tomorrow's target → recreate/relaunch → Review → archive → restore schema-3 backup with notifications off → restore schema-1/2 fixture → verify legacy records, empty habits and no old notifications. Repeat travel/date-only/DST cases with injected clocks and platform reminder scenarios from T28.
- [ ] **Step 5: Complete accessibility/language/device matrix.** English/French/Persian RTL, 320 dp/normal, portrait/landscape, scales 1.0/1.3/2.0, long names/units, keyboard, light/dark and actual TalkBack. Check selected/full tab names, habit/count/target speech, stable focus after + Log/Undo, error announcements and 48 dp controls. Restore any temporary device settings after validation.
- [ ] **Step 6: Measure representative mixed-history behavior.** Use disposable 50,000-row existing history plus habit logs; record page/Review/scroll behavior against existing T11/T15/T16 targets. Keep separate habit aggregation bounded and avoid degrading old metrics. A measured target failure remains open; do not replace it with a compilation claim.
- [ ] **Step 7: Reconcile documentation and final diff.** Only implemented features move from Planned to Implemented; only verified tasks become complete. Record unavailable API/OS/manual checks explicitly, leave older release work open, run `git diff --check`, review changed files and commit scoped evidence. Suggested message: `docs(habits): record integration and device acceptance`.

## Execution handoff

Read all three plans and the spec before implementation. Recommended execution is
native in the current session, task by task, with an independent review before
integration; the shared contracts and database/feed changes benefit from one
implementer owning their consistency. The earlier PM/designer roles are advisory,
not authorization for unrestricted new implementation subagents.

All tasks are pending. No command or result in this plan is a claim of execution.
