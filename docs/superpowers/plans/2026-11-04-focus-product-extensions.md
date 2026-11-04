# Focus Product Extensions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement task-by-task. Steps use checkbox syntax. Follow the user's separate final-QA workflow: do not substitute per-task test runs or request a QA handoff after each implementation task.

**Goal:** Implement FP01–FP08 as one coherent Focus extension, then test all new behavior together in a separate final phase.

**Architecture:** Extend the existing session repository/shared runtime and add a product repository interface on the same Room implementation. Pure domain rules own expiry, goals and reminder slots; existing Tasks, Review, lock and reminder infrastructure host the new surfaces. Keep auxiliary saves independent from timer finalization.

**Tech Stack:** Existing Kotlin/JVM, coroutines, Room, Compose, AlarmManager/WorkManager and JUnit; no new dependencies.

**Spec:** [Focus product extensions](../specs/2026-11-04-focus-product-extensions-design.md) · [Ledger](../../FOCUS_INSIGHTS_TASKS.md) · [Final QA contract](../../feature-roadmap/focus-product-extensions-qa.md)

## Global constraints

- Keep Kotlin/Compose/Room/coroutines, minSdk 28, compileSdk 35 and targetSdk 35; do not add dependencies or raise SDK levels for this scope.
- Operate offline; add no network, exact-alarm, accessibility, usage-access, device-admin or DND permissions. Reuse existing optional notification permission and settings.
- Keep one open focus session, pause exclusion, monotonic timing, explicit recovery, guarded navigation, emergency exit, generation checks and idempotent command receipts.
- Keep one optional focus tag per session, existing name/tag limits and existing range-clipped terminal-only saved totals.
- Supply English, French and Persian resources, RTL, wrapping, TalkBack labels and minimum 48 dp controls. Pause/Stop remain reachable independently of report or auxiliary-write failures.
- Run no tests, Gradle, schema generation, lint, APK assembly or device tooling during implementation tasks. Author and execute tests in the separate final QA phase after every implementation task is source complete.
- Never describe unexecuted checks as passing; keep source completion, QA results and product acceptance separate.

## Phase policy and status

All boxes initially start unchecked; completed implementation work is recorded below. FX01–FX10 produce source and source-review handoffs only; record **SOURCE COMPLETE — UNVERIFIED** when their code/doc steps exist. No QA gate between these tasks. They are source dependencies, not tested release increments. Preserve a single integration branch/workspace and do not launch partially wired schema/timer features. No automatic commits, release, install or personal-data operations are required by this planning request.

FX-QA starts only when every implementation row is source complete. Test/fixture authoring, build fixes discovered by compilation, schema generation, all automated/manual testing and defect retests occur there. A final defect fix may change earlier source; retain defect ownership under the final QA phase and rerun affected plus integrated cases. No requirement to knowingly preserve a defect until the end: source-review corrections are allowed during implementation.

## Review Focus

1. New-process callback after an elapsed deadline: retain explicit recovery and never count a delayed alert as extra focus (FX04; FX-Q03/04).
2. Deleted task, longer-than-120-code-point title or relock during selection: preserve history, require valid Start and hide cross-section content (FX03/10; FX-Q02/08).
3. Ambiguous committed write followed by changed draft, tag merge or restore: replay exact captured payload or reject stale authority (FX01/09/10; FX-Q09/10).
4. DST gap/overlap, travel and goal editing near Monday: one reminder occurrence, fixed goal zone and no rewritten historic target (FX07/08; FX-Q06/07).
5. Notification denied, quiet hours, open timer or cancelled break: keep manual use available and prevent stale/cross-kind callbacks (FX05/08; FX-Q04/07/09).

## Source map and contract ownership

Paths below are relative to the repository root. Shorthand roots: **D** = `core/domain/src/main/kotlin/com/luma/lifeos/domain`, **R** = `core/data/src/main/kotlin/com/luma/lifeos/data`, **A** = `app/src/main/java/com/luma/lifeos`. Use these exact package roots when creating files; test paths are fully specified in the final QA document. Existing files were inspected; new filenames are planned, not present.

- Domain models/commands: create `D/focus/FocusProductModels.kt`, `FocusProductRepository.kt`, `FocusTargetTiming.kt`, `FocusGoalRules.kt`, `FocusReminderRules.kt`; modify `FocusMetadata.kt`, `FocusSession.kt`, `FocusTiming.kt`, `FocusRepository.kt`.
- Storage: modify `R/focus/FocusEntities.kt`, `FocusDao.kt`, `RoomFocusRepository.kt`, `R/db/LumaDatabase.kt`, `R/LumaStorage.kt`; create `R/focus/FocusProductEntities.kt`, `FocusProductDao.kt`, `FocusProductCommands.kt`; modify `R/backup/BackupArchive.kt`, `BackupValidation.kt`, `BackupRows.kt`, `BackupSql.kt`, `RoomBackupService.kt`; create `R/backup/FocusProductArchiveAdapter.kt`.
- App state/execution: modify `A/focus/FocusRuntime.kt`, `FocusViewModel.kt`; create `FocusBreakRuntime.kt` and `FocusExpiryPlatform.kt` (which contains the receiver and worker).
- UI: modify `A/ui/focus/FocusScreen.kt`, `FocusRoute.kt`, `FocusSetupState.kt`, `FocusHistoryRoute.kt`, `FocusHistoryViewModel.kt`, `FocusSettingsScreen.kt`, `FocusSettingsRoute.kt`, `WeeklyFocusCard.kt`, `FocusReportPresentation.kt`; create `FocusTaskPicker.kt`, `FocusReflectionEditor.kt`, `FocusGoalSettings.kt`, `FocusReminderSettings.kt`, `FocusTagManager.kt` in `A/ui/focus/`.
- Integration: modify `A/ui/todo/TodoEditorRoute.kt`, `TodoEditorViewModel.kt`, `A/ui/LifeOsApp.kt`, `A/lock/AppLockCoordinator.kt`, `D/lock/ProtectedRecordClassifier.kt`, `R/repository/RoomTodoRepository.kt`, `D/todo/TodoRepository.kt`; reminder files under `A/reminders/`; `app/src/main/AndroidManifest.xml`; localized `app/src/main/res/values{,-fr,-fa}/strings.xml`.

### Shared proposed interfaces (defined in FX01)

These are new contracts unless identified as extensions. Keep existing signatures and behavior outside this scope. All UUID/Instant/LocalDate/LocalTime/ZoneId/DayOfWeek types use existing Java APIs.

```kotlin
enum class FocusTimerMode { COUNT_UP, TIMED }
data class FocusTimerConfig(val mode: FocusTimerMode, val targetMillis: Long?)
enum class FocusOutcome { MADE_PROGRESS, FINISHED_INTENDED_WORK, GOT_INTERRUPTED }
data class FocusReflection(val outcome: FocusOutcome?, val nextStep: String?)
data class FocusProductContext(val commandId: UUID, val generation: UUID)
data class FocusGoalVersion(
    val id: UUID, val revision: Long, val effectiveMonday: LocalDate,
    val zoneId: String, val targetMinutes: Int, val preferredDays: Set<DayOfWeek>,
)
data class FocusGoalState(
    val enabled: Boolean, val settingsRevision: Long,
    val visibleFromMonday: LocalDate?, val visibleFromZoneId: String?,
    val versions: List<FocusGoalVersion>,
)
data class FocusTagCatalog(val revision: Long, val tags: List<FocusTag>)
data class FocusWeekDistribution(val activeDays: Int, val highestDate: LocalDate?)
```

Extend `FocusStartInput` with `taskId: UUID? = null`, `taskOrigin: Boolean = false`, `timer: FocusTimerConfig = count-up`. Extend `FocusSession` with these values plus `reflection: FocusReflection = empty`; add TARGET_REACHED to terminal reasons. Existing metadata remains `FocusMetadata(name, tag)`, not a duplicate product metadata type. Extend validation and all fakes/callers. `FocusProductResult` is a sealed result with Applied/AlreadyApplied, Conflict, Invalid and Unavailable; successful callers re-query authoritative rows rather than treating their draft as committed data.

New `FocusProductRepository` methods:

```kotlin
suspend fun updateSession(sessionId: UUID, expectedRevision: Long,
    name: String?, tagName: String?, unlinkTask: Boolean,
    context: FocusProductContext): FocusProductResult
suspend fun saveReflection(sessionId: UUID, expectedRevision: Long,
    reflection: FocusReflection, context: FocusProductContext): FocusProductResult
suspend fun renameTag(tagId: UUID, expectedCatalogRevision: Long,
    name: String, context: FocusProductContext): FocusProductResult
suspend fun mergeTags(sourceId: UUID, targetId: UUID, expectedCatalogRevision: Long,
    context: FocusProductContext): FocusProductResult
fun observeTaskTotal(taskId: UUID): Flow<Long>
fun observeTagCatalog(): Flow<FocusTagCatalog>
fun observeGoals(): Flow<FocusGoalState>
suspend fun scheduleGoal(goal: FocusGoalVersion, expectedSettingsRevision: Long,
    context: FocusProductContext): FocusProductResult
suspend fun setGoalsEnabled(enabled: Boolean, expectedSettingsRevision: Long,
    context: FocusProductContext): FocusProductResult
```

Use `FocusGoalState.settingsRevision` and `FocusTagCatalog.revision` for writes, not revisions guessed at write time. `updateSession` null name preserves only an existing legacy null; normal edited names must validate. No method accepts elapsed/target edits. Repository validates generation/revision/shape; app lock coordinator grants section access before sensitive reads/writes and rechecks before display. Auxiliary command receipt identity includes normalized inputs.

## FX01 — Shared models, schema and archive compatibility

**Depends on:** existing FI source. **Produces:** all shared contracts above, Room 11/archive 9 (rebase if occupied), safe defaults for legacy rows and one storage authority.

- [x] Recheck current Room/archive versions and inspect all model constructors, enum validators, receipt decoders and backup column projections with `rg`; record final reserved versions in the ledger.
- [ ] Implement shared types/normalization and session fields. Validate timed 1–180 minutes, count-up null target, next-step 280 code points and TARGET_REACHED exact-target invariant. Update existing constructors/fakes by source review; no test execution.
- [ ] Add migration/entity/DAO definitions for session fields, goals, singleton settings, local reminder state and break runtime, including FK/index/default rules from the spec. Register the migration without destructive fallback. Do not handwrite exported Room JSON.
- [ ] Expose `FocusProductRepository` through `LumaStorage`; add versioned auxiliary receipt encoding and generation/revision helpers. Wire individual mutation implementations in their owning tasks; do not expose UI actions backed by placeholders.
- [ ] Implement archive 9 table/column inventory and adapters for versions 1–8, staging validation, task/tag import order, terminal reasons and portable goal projections. Exclude all local alarms/reminders/breaks/receipts/runtime.
- [ ] Update `docs/BACKUP_FORMAT.md` and `docs/PRIVACY.md`; source-review reset/restore quiescing hooks. Record SOURCE COMPLETE — UNVERIFIED, with schema/runtime checks reserved for FX-QA.

## FX02 — Repeat and retained setup

**Depends on:** existing FI metadata and Start contracts. FP01 Repeat only uses currently supported name/tag fields; the shared FX01 foundation remains required for later task, timer and reflection extensions. **Files:** existing Focus setup/screen/route/ViewModel/history files. **Consumes:** authoritative terminal detail. **Produces:** `FocusViewModel.repeatSession(sessionId: UUID)` and setup event `PrefillRequested(input: FocusStartInput, nextStep: String?, generation: UUID)`.

- [x] Reload and authorize the latest terminal source under the current Focus grant. Prefill editable name/tag; legacy null names stay blank and require valid user input. Task links, timed configuration and next-step context remain unavailable until their owning extension tasks.
- [x] Add Repeat to the completion screen and terminal history detail. Keep Start another as explicit empty setup; if another session is open, Focus returns to that session without replacing it.
- [x] Retain the supported Repeat draft in SavedStateHandle; clear it on successful Start, relock and database-generation replacement. The existing captured Start command keeps its payload and IDs unchanged for retries.
- [x] Source-review that Repeat only requests a prefill and never calls Start; stale/missing terminal details cannot create a timer. FX-Q01/09 remain NOT RUN.

**FP01 source handoff (2026-11-04): SOURCE COMPLETE — UNVERIFIED for the name/tag contract at its original handoff.** FX04 and FX06 subsequently extended Repeat with timed configuration and next-step context; FX03 task-link prefill remains pending. FP01 QA remains NOT RUN in FX-QA.

## FX03 — Task link and independent completion

**Depends on:** FX01–FX02. **Files:** Tasks repository/editor, Focus picker/state/runtime/repository, lock/navigation integration. **Produces:** `TodoRepository.searchOpen(query: String, offset: Int, limit: Int = 50): TodoPage`, `FocusTaskPicker.kt`, linked totals and explicit task-completion action.

- [ ] Add literal case-insensitive title search with filters before count/paging and created-at-descending/ID-descending order. Add picker state cancellation/query identity; expose only open tasks under both grants.
- [ ] Add task editor action only for saved open tasks, resolve unsaved edits first, and navigate with task UUID rather than title. Add optional picker to setup and validate latest task state in the Start transaction.
- [ ] Persist link/provenance in Start receipt identity. Apply FK SET NULL without clearing provenance; implement `observeTaskTotal` using terminal run totals, not wall spans or the current history page.
- [ ] Add separate captured task completion using existing revision/nextRevision API after durable focus save. Show missing/conflict/retry independently; Stop must never auto-complete a task.
- [ ] Implement both-grant privacy on task entry and linked detail; reserve full cached-report/lock sweep for FX10. Record unverified source handoff; FX-Q02/08/09 cover long titles, task deletion, completion races and access changes.

## FX04 — Timed focus, expiry and recovery

**Depends on:** the timer/break portion of FX01. **Files:** `D/focus/FocusTargetTiming.kt`, existing timing/repository/runtime; `A/focus/FocusExpiryPlatform.kt`; manifest and setup/running UI. **Produces:** `remainingFocusMillis(session: FocusSession): Long?` and `capFocusAdvance(session: FocusSession, candidateCreditMillis: Long): Long`; transaction-level due-expiry settlement shared by all timer paths.

- [x] Implement checked active-credit capping and canonical target boundary; remaining returns null for count-up, never uses raw wall span. Extend FINISHED validators and apply/reconcile/recovery/checkpoint paths for TARGET_REACHED.
- [x] Settle due target before Pause/Stop/checkpoint in the same repository transaction; resolve duplicate callbacks by persisted state. Preserve actual raw sample and existing monotonic/discontinuity checks.
- [x] Add timer selector/validation, remaining-time display and terminal reason copy in all locales. Do not permit changing an open session's configuration.
- [x] Schedule one namespaced generation/session/revision-bound inexact elapsed alarm; cancel on Pause/Stop/replacement, reschedule on Resume and ignore stale callbacks. Use bounded receiver-to-worker handoff for storage work; alarms never carry private text.
- [x] Preserve explicit new-process recovery and conservative discontinuity outcomes; settle prior durable target without accepting a gap. Report only after authoritative result; no new service/exact-alarm permission.
- [x] Source-review the 10 run + 7 pause + 15 run example, capped delayed callback, new-process recovery and Stop/expiry transaction race. Defer all execution to FX-Q03/04/09.

**Dependency ruling (2026-11-04):** This FP03-only delivery omits FX03 because task links do not participate in timer credit, expiry, recovery or local breaks. FX01 is partially implemented for timer/break fields, migration and archive compatibility; task, reflection and goal contracts remain pending. Cost if this narrowing is wrong: reconcile task-link Start identity and future shared archive changes with the landed timer payload and archive 9 before FX03 ships.

**FX04 source handoff (2026-11-04): SOURCE COMPLETE — UNVERIFIED.** Runtime/source review only. Room schema generation, compilation, tests, alarm/device behavior and FX-QA remain NOT RUN.

## FX05 — Separate break countdown

**Depends on:** FX04. **Files:** create `A/focus/FocusBreakRuntime.kt`; local break entity/DAO, expiry platform, runtime/UI. **Produces:** `FocusBreakRuntime.start(durationMinutes: Int)`, `end()` and read-only `StateFlow<FocusBreakState>` with Idle/Running/Expired states bound to generation/boot/revision.

- [x] Persist an explicitly started 1–60 minute break separately from focus runs, with default 5-minute shortcut, cancellable best-effort expiry and no navigation guard.
- [x] Add Start break after saved session, End break during countdown and explicit Start another; starting Focus atomically invalidates local break authority before scheduling the focus alarm.
- [x] Restore same-boot countdown on recreation; end uncertain/reboot break, never credit time or revive a cancelled record. Clear local state on replacement/reset.
- [x] Source-review that saved statistics/backup never include breaks and alert scheduling failure does not block manual actions. FX-Q04/09 remain NOT RUN.

**FX05 source handoff (2026-11-04): SOURCE COMPLETE — UNVERIFIED.** Break state is local and excluded from archive/statistics. Runtime/source review only; schema generation, compilation, tests, alarm/device behavior and FX-QA remain NOT RUN.

## FX06 — Reflection and resumption context

**Depends on:** FX01, FX02. **Files:** new reflection editor; completion/detail/ViewModel; `FocusProductCommands.kt`. **Produces:** `saveReflection` implementation and `FocusReflectionEditor` state with independent failure/retry.

- [x] Implement terminal-only revision/generation-safe reflection mutations and normalization (blank to null, no controls, maximum 280 code points).
- [x] Add optional outcome/next-step inputs after durable save and editable detail. Skip/dismiss never rolls back credit; saving reflection never issues a timer or task-completion action.
- [x] Show the prior next step in repeat context, obeying provenance/privacy, and clear private drafts on relock/restore. Source handoff; FX-Q05/09 later prove independence and retry.

**Version ruling (2026-11-04):** The current source is Room 12/archive 10, so reflection columns use the next versions, Room 13/archive 11, and older archives adapt to empty reflection. This advances the design's archive-9 target without changing the portable fields. Cost if wrong: a version collision or an archive reader that expects the older schema-9 inventory.

**FP04 source handoff (2026-11-04): SOURCE COMPLETE — UNVERIFIED.** Terminal sessions support optional stable outcome values and a normalized one-line next-step note (up to 280 Unicode code points), saved through an independent generation/revision-checked command. The completion screen offers save/skip after timer persistence; expanded history details allow edits; Repeat shows the saved next step as context. Reflection columns are included in archive 11, and archives 7–10 adapt to no reflection. Focus access gates the editor and relock/restore clears private drafts. English, French and Persian labels are present. Source changes only; tests, Room schema generation/migration execution, builds, UI/device checks and FX-Q05/09 remain NOT RUN.

## FX07 — Goal versions and weekly distribution

**Depends on:** FX01, FX06. **Files:** new domain goal rules/UI, goal DAO/commands, report presentation and weekly card. **Produces:** `nextGoalMonday(now: Instant, zone: ZoneId): LocalDate`, `focusWeekDistribution(dailyTotals: Map<LocalDate, Long>): FocusWeekDistribution`, goal repository methods.

- [x] Implement strictly next-Monday activation in the new version's captured zone, target 1–10,080, nonempty weekday set, immutable activated versions and one globally pending replacement. Select only the latest activated version by activation instant/UUID; retain original historical versions across zone changes. Store the goal settings read revision for consumers.
- [x] Implement off/re-enable behavior and zone-change next-week replacement without rewriting current or historical targets. Persist visible-from Monday/zone on enable; do not reactivate a historic goal in the current week. Replace any prior pending version atomically across zones. All positive saved contribution counts regardless of preferred weekday.
- [x] Add opt-in settings/effective-date/goal-zone labels and saved-only progress; keep open-session amount separate. Reuse daily range helpers and cancellation/generation identity.
- [x] Add active days/highest-day presentation, day-history action and empty Plan another session action to existing Review (FP08). Source handoff; FX-Q06/11 cover boundaries, tie and sparse data later.

**FP05 source handoff (2026-11-04): SOURCE COMPLETE — UNVERIFIED.** Domain rules, Room goal versions/settings, revision/generation-aware writes, protected settings and Review progress, and archive 10 support are in place. The report uses the captured goal zone, includes terminal saved contributions only, and reports active days without a streak. Goal settings are default-off and display their effective Monday. Room schema generation, compilation, migration/archive execution and FX-Q06/09 remain NOT RUN. FX07 remains partial because FX01 is incomplete.

**FP08 source handoff (2026-11-04): SOURCE COMPLETE — UNVERIFIED.** Weekly Review derives active days and the highest saved-time date from daily totals in the displayed current range, opens a day-filtered history range from each elapsed day row, and offers empty Focus setup when Focus is initialized and idle while preserving any active session. The existing protected Review visibility gate remains in force. Source changes only; tests, builds, platform checks and FX-Q06/11 remain NOT RUN. FX07 remains PARTIAL until FX01 is source complete.

**Version ruling (2026-11-04):** The checked-out source had already advanced from the design baseline to Room 11/archive 9, so FP05 adds Room 12/archive 10. Cost if wrong: a migration or archive-version collision with another feature.

**Dependency ruling (2026-11-04):** At the time the FP05/FP08 handoffs were recorded, reflection task FX06 and the remaining shared FX01 work were incomplete. FX06 is now source complete; FX01 remains partial, so FX07 remains PARTIAL until its shared foundation is integrated. Cost if wrong: a later shared product repository may need interface adaptation before those tasks ship.

## FX08 — Scheduled focus invitations

**Depends on:** FX01, FX04–FX05. **Files:** create `D/focus/FocusReminderRules.kt`, `A/reminders/FocusReminderWork.kt`; local reminder entities/DAO; modify existing reminder platform/intents/work/preferences/settings, protected preview policy and manifest. **Produces:** `nextFocusInvitation(now: Instant, zone: ZoneId, weekdays: Set<DayOfWeek>, time: LocalTime): Instant?`, revision/generation-bound occurrence delivery/consume operations.

- [ ] Implement one disabled-by-default weekday/time schedule, next-slot planning, DST policy, 60-minute delivery expiry and once-only 10-minute snooze; represent occurrence identity by revision/generation/local date.
- [ ] Integrate Focus kind into existing global reminder reconciliation inventory. Prevent existing cancel-except logic or habit fallback branches from cancelling/misrouting Focus alarms and actions; keep timer/break expiry in a separate namespace.
- [ ] Revalidate permission/channel, quiet hours, slot window, open focus/break, revision and generation before atomically consuming a delivery/action. Handle disable and stale/malformed intents without side effects.
- [ ] Reuse notification access settings with generic Open/Snooze/Disable, no task content or Start action. Add local schedule settings and boot/time/zone/package/permission-return reconciliation.
- [ ] Reset local invitation configuration on database replacement, keep exports free of it, and cancel notifications/alarms. Source handoff; FX-Q07/08/09 prove platform behavior and task/habit isolation later.

## FX09 — Session corrections and tag management

**Depends on:** FX01, FX03, FX06. **Files:** product command implementations, Focus DAO/history/detail/new tag manager and settings. **Produces:** `updateSession`, `renameTag`, `mergeTags` implementations from shared contract.

- [ ] Add terminal name/tag/unlink editing with legacy-null preservation only for untouched legacy names; disallow task reassignment, provenance clearing, target/time edits.
- [ ] Implement case/spacing display rename and explicit collision-to-merge confirmation with catalog revision. Preserve target label; move references/delete source atomically and advance affected session revisions without modifying runs.
- [ ] Require both grants if any affected record is task-protected, including open sessions. Reconcile active metadata and pending commands on merge; unchanged legacy receipt replay still returns original outcome without creating a duplicate tag.
- [ ] Invalidate/requery history filters, suggestions, longest/tag reports, linked totals and Review. If a selected filter tag was merged, move it to the target with visible context; do not show stale rows under the old heading.
- [ ] Source-review receipt payload stability and run-byte preservation. FX-Q09/10 later prove concurrency, conservation and restore rejection.

## FX10 — Integrated privacy, replacement and handoff

**Depends on:** FX01–FX09. **Files:** all entry/cached state surfaces, lock classifier/coordinator, `LifeOsApp`, restore/reset, localized resources and docs. **Produces:** source-complete integrated behavior and an explicit FX-QA entry record.

- [ ] Apply the spec's linked/provenance dual-section access to every label, note, picker and aggregate, including Today/Review/history caches. Suppress mixed reports when protected rows are inaccessible; keep generic running controls available.
- [ ] Sweep Start/repeat/task/reminder/day/Review entry routes through lock and running-session guards. Clear private cache/drafts on relock/generation change, reject stale asynchronous results and require explicit actions after unlock.
- [ ] Connect restore/reset quiescing to timer/break/invitation cancellation and generation replacement. Source-review archives/goal settings and all reinitialization paths together with existing task/habit reminders.
- [ ] Complete EN/FR/FA resources, accessibility labels, wrapping/RTL designs, generic notifications and privacy/backup/help wording. Keep device-wide enforcement limitations explicit.
- [ ] Review all FP-A01–09 against source, enumerate changed files and remaining uncertainty, and mark FX01–FX10 source status without checking any QA result. Carry pending FI02 and applicable FI/FM acceptance into the final QA ledger.

## FX-QA — One final QA phase for the whole extension

**Depends on:** every FX01–FX10 source handoff. **Owner contract:** [Final QA scenarios, fixtures, commands and evidence](../../feature-roadmap/focus-product-extensions-qa.md). No QA execution is authorized by the current documentation-only task.

- [ ] Freeze integration revision/dirty-patch hash, verify isolated test package/data, inventory tools/devices and create dated evidence record. Mark unavailable checks BLOCKED with cause.
- [ ] Author domain/JVM/Room/Compose/platform tests and fixtures listed in the QA contract; run them against the full implementation. Where fixing defects, demonstrate failing assertions before fixes and passing results afterward.
- [ ] Generate actual Room schema, run migration/archive suites, compilation/lint/build, all source suites and connected tests, then the full manual/platform/accessibility/locale matrix.
- [ ] Execute FX-Q01–FX-Q12, the integrated cross-feature journey, carried FI/FM gaps and task/habit reminder regressions. Record exact inputs/outputs, package/API/locale/zone and independently inspected database results.
- [ ] Fix defects under FX-QA, rerun affected suites and integrated journeys on the final candidate, then publish case-level PASS/FAIL/BLOCKED/NOT RUN with evidence links. No overall acceptance if mandatory checks remain unresolved.

## Self-review and handoff

Coverage: FP01→FX02; FP02→FX03; FP03→FX04–05; FP04→FX06; FP05→FX07; FP06→FX08; FP07→FX09; FP08→FX07/10; compatibility/privacy/localization→FX01/10. Every Review Focus risk maps to a final QA case. New interfaces are defined above or in their owning task; reuse existing task completion and timing APIs rather than parallel versions.

Unchecked implementation tasks and all QA remain pending. Review the draft before code execution. Reusable presets, habit links, automated cycles, duration editing, new permissions and device-wide blocking remain outside this plan. No per-task QA phase is implied by source dependencies.
