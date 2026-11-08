# Focus Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking. Delegation is not required. The user's explicit phase separation overrides generic per-task TDD/build steps: all test authoring and execution belong to the later QA/emulator plans.

**Goal:** Finish unimplemented Focus extensions and deliver the review's simpler setup, completion, recent-work and Review experience.

**Architecture:** Preserve application-owned runtime and the existing two Room-backed Focus repositories sharing one database. Add task provenance and a local invitation repository, then small Compose/state files; access and database generations govern all asynchronous content.

**Tech Stack:** Existing Kotlin, coroutines/Flow, Room, Compose Material 3, AlarmManager/WorkManager, JDK 17; no new dependencies.

**Spec:** [Focus delivery specification](../specs/2026-11-07-focus-delivery-design.md)
**Ledger:** [Tasks and carry-forward map](../../FOCUS_DELIVERY_TASKS_2026-11-07.md)
**Later validation:** [QA](../../feature-roadmap/focus-delivery-qa-2026-11-07.md) / [Emulator tests](../../feature-roadmap/focus-delivery-emulator-2026-11-07.md)

## Global constraints

- Phase I contains source edits/read-only source review only. No tests or fixture authoring, test execution, Gradle, compilation, lint, generated schemas, APKs or device use in any FD task.
- Every source handoff is SOURCE COMPLETE — UNVERIFIED. All FD01–FD12 handoffs precede Q01. All required Q preparation precedes emulator/device execution.
- Keep minSdk 28, compileSdk/targetSdk 35, JDK 17 and existing dependencies/permissions.
- Preserve one open session, immutable command retries, monotonic timing/recovery, timer cap, pause exclusion, generation safety and emergency controls.
- Preserve 120-code-point names, optional 50-code-unit tags, 1–180 minute targets, 1–60 minute breaks, 280-code-point next steps and existing next-Monday goal rules.
- Reserve Room 15/archive 12 after confirming current Room 14/archive 11. Rebase all new docs together if occupied. Schema generation is QA work.
- Require TASKS + FOCUS for task-origin content forever, including unlink/deletion. No persisted grants, new telemetry or private text in external intents/logs.
- EN/FR/FA, RTL, IME, keyboard, TalkBack and at least 48 dp targets; Start at least 52 dp.
- Preserve user edits outside this scope. Do not commit unrelated settings.gradle.kts or app/production/ artifacts.

## Review focus

1. Task disappears after prefill: reject Start without silently discarding the link; Q02 task-conflict assertions, E02.
2. Relock/restore while a query or completion is suspended: clear content and reject stale writes; Q02/Q03 generation assertions, E08/E09.
3. Stop/expiry races with unlink/tag mutation: one saved result and usable controls; Q02 transaction assertions, E04.
4. Interrupted/legacy/deleted recent source or a dirty draft: no fabricated name/time and no silent draft replacement; Q02 recent-work assertions, E05.
5. DST/quiet-hours/replayed snooze combined with another reminder kind: bounded once-only attempts, no cross-kind cancellation; Q02 invitation assertions, E06/E07.

## File map and ownership

Paths below are exact relative to the repository. New names are proposed files, not files assumed to exist.

- Domain root D = core/domain/src/main/kotlin/com/luma/lifeos/domain/
- Data root C = core/data/src/main/kotlin/com/luma/lifeos/data/
- App root A = app/src/main/java/com/luma/lifeos/
- Resources R = app/src/main/res/
- Current FocusProductRepository lives in D/focus/FocusGoals.kt. Extend it there; extracting it is optional only if all imports remain consistent. No duplicate interface.
- FocusRuntime/RoomFocusRepository own timer mutations. RoomFocusProductRepository owns existing auxiliary commands. New RoomFocusInvitationRepository owns local schedules/occurrences on the same database.
- Read all files before editing through Android Studio tools. New implementation files are created only during the future implementation request.
- Test paths and assertions live in the QA document. Do not create those files as part of any FD task.

## Execution order

FD01 → FD02 → FD03 → FD04 → FD05 → FD06 → FD07 → FD08 → FD09 → FD10 → FD11 → FD12 → Q01–Q06 preparation/host checks → E01–E12 → final Q06 acceptance.

Although some tasks could be independent, use this sequence to stabilize shared data/privacy before new display surfaces. Do not rebuild landed FX02/FX04/FX05/FX06 or goal/distribution implementations.

## FD01 — Shared task/invitation contracts, source migration and archive

**Depends on:** existing Focus source. **Spec:** R01–04, invitation data in R05.

**Files:** modify D/focus/FocusMetadata.kt, FocusSession.kt, FocusGoals.kt, FocusRepository.kt; D/todo/TodoRepository.kt; C/focus/FocusEntities.kt, FocusDao.kt, FocusProductEntities.kt, FocusProductDao.kt, RoomFocusRepository.kt, RoomFocusProductRepository.kt; C/db/LumaDatabase.kt; C/backup/BackupArchive.kt, FocusMetadataArchiveAdapter.kt, BackupValidation.kt, RoomBackupService.kt. Create D/focus/FocusInvitations.kt and C/focus/FocusInvitationEntities.kt.

**Produces:** taskId/taskOrigin fields and normalization; task-selection conflict reason; all R04 source schema/archive changes; invitation config/slot/repository contracts from the spec. Existing Start method signature remains unchanged; its input gains defaulted fields.

- [ ] Read current source versions, migrations and archive projections; record Room 15/archive 12 reservations or rebase the documents if occupied.
- [ ] Add task identity/provenance to input/session/entity mappings and versioned Start receipt payload, preserving old payload readers.
- [ ] Add task FK/index, local invitation settings/delivery schema and all migration registration in source; preserve runtime and unrelated table definitions.
- [ ] Enforce open-task existence during Start in the same transaction as session creation; return task-selection conflict distinctly from I/O failure.
- [ ] Add archive 12 task projection/staged validation and explicit defaults for schemas 1–11 without flattening their timer/reflection/goal differences.
- [ ] Source-review constructor defaults, mapper coverage, receipt identity, FK order and reset exclusions. Record handoff as unverified; leave schema JSON untouched.

**Deferred proof:** Q02/Q03; E02/E09. This task does not diagnose existing migration failures by guessing.

## FD02 — Task entry, paged picker and linked totals

**Depends on:** FD01. **Spec:** R02–03.

**Files:** modify D/todo/TodoRepository.kt; C/todo/TodoDao.kt; C/repository/RoomTodoRepository.kt; D/focus/FocusGoals.kt; C/focus/FocusDao.kt, RoomFocusProductRepository.kt; A/ui/todo/TodoEditorRoute.kt, TodoEditorScreen.kt; A/focus/FocusViewModel.kt; A/ui/focus/FocusSetupState.kt, FocusRoute.kt, FocusScreen.kt; A/ui/LifeOsApp.kt, AppDestinations.kt; A/lock/ProtectedCatalogRepositories.kt, ProtectedAggregateRepositories.kt; R/values/strings.xml, values-fr/strings.xml, values-fa/strings.xml. Create A/ui/focus/FocusTaskPicker.kt and FocusTaskPickerState.kt.

**Consumes:** FD01 task-aware input. **Produces:** searchOpen(query: String, offset: Int, limit: Int = 50): TodoPage; observeTaskFocusMillis(taskId: UUID): Flow<Long>; FocusViewModel.focusOnTask(taskId: UUID), clearTaskSelection().

- [x] Implement literal open-task search/count with escaped wildcards, max 50 and stable created-at/ID descending order.
- [x] Add picker query/page/loading/error/empty state; cancellation and request identity prevent stale results.
- [x] Add saved-open-task Focus action and Save/Discard/Cancel handling for unsaved edits; save failures do not navigate.
- [x] Load current task into editable setup, mark origin, preserve overlong title as invalid and surface missing/completed task conflict at Start.
- [x] Add observed terminal linked totals to task detail; exclude open time from saved total.
- [x] Source-review all call sites/default callbacks and protected navigation; record unverified handoff.

**Deferred proof:** Q02; E02/E08.

## FD03 — Independent completion, unlink and task deletion

**Depends on:** FD02. **Spec:** R03.

**Files:** create A/ui/focus/FocusTaskCompletionState.kt and FocusTaskCompletionAction.kt. Modify D/focus/FocusGoals.kt; C/focus/RoomFocusProductRepository.kt, FocusDao.kt; C/repository/RoomTodoRepository.kt; A/focus/FocusViewModel.kt; A/ui/focus/FocusHistoryRoute.kt and A/ui/LifeOsApp.kt.

**Consumes:** current TodoRepository.setCompleted UUID revisions. **Produces:** PendingFocusTaskCompletion as specified; FocusProductRepository.unlinkTask(sessionId, expectedRevision, context): FocusProductResult.

- [x] Capture task ID, expected/next UUID revision and generation separately from the saved timer operation.
- [x] Offer completion only after eligible terminal save; handle missing/already completed/conflict/I/O independently.
- [x] Add explicit terminal unlink with a product receipt, preserving taskOrigin, timer and runs.
- [x] Coordinate task deletion and affected Focus/runtime revision updates in its transaction, maintaining ON DELETE SET NULL fallback.
- [x] Invalidate linked totals and pending completion drafts; retain immutable operations across ambiguous retries.
- [x] Source-review that no completion path replays Stop and no unlink/deletion clears provenance; record unverified handoff.

**Deferred proof:** Q02/Q03; E02/E03/E09.

## FD04 — Dual-section privacy and report suppression

**Depends on:** FD01–03. **Spec:** R02 and all private surfaces.

**Files:** modify D/lock/ProtectedRecordClassifier.kt; A/lock/LockActionGuard.kt as needed; A/ui/LifeOsApp.kt; A/focus/FocusViewModel.kt; A/ui/focus/FocusInsightsVisibility.kt, FocusHistoryViewModel.kt, FocusHistoryRoute.kt, FocusGoalReport.kt, FocusGoalSettings.kt, TodayFocusCard.kt, WeeklyFocusCard.kt, FocusTagManager.kt; C/focus/FocusDao.kt and repository projections.

**Produces:** current access decisions for Focus-only versus task-origin content; protected aggregate markers and generic active controls.

- [ ] Extend classification using persisted origin, including after link removal and task deletion.
- [ ] Thread current read/write decisions through task flow and auxiliary commands; recheck after suspension before publish/write.
- [ ] Filter inaccessible history before paging; mark mixed reports unavailable-until-unlock rather than return partial totals.
- [ ] Protect tag suggestions/catalog and rename/merge by all affected records, not only the visible current row.
- [ ] Clear private drafts, caches, query requests and completion context on relock/generation change; unlock re-queries.
- [ ] Source-review running locked state to keep generic timer/Pause/Stop/emergency usable; record unverified handoff.

**Deferred proof:** Q02/Q03; E08/E09/E11.

## FD05 — Invitation rules and local persistence

**Depends on:** FD01, FD04; existing timed/break source. **Spec:** R05.
**Status:** SOURCE COMPLETE — UNVERIFIED.

**Files:** create D/focus/FocusInvitationRules.kt and C/focus/RoomFocusInvitationRepository.kt, FocusInvitationDao.kt. Modify C/db/LumaDatabase.kt, C/LumaStorage.kt and source migration from FD01 as needed without another version bump.

**Produces:** FocusInvitationRepository implementation; nextFocusInvitation(now, zone, weekdays, time): Instant?; generation/revision/date occurrence identity.

- [x] Implement default-off config validation and idempotent revision-aware config commands.
- [x] Implement civil-time next-slot calculation, DST gap/overlap policy and inclusive 60-minute deadline.
- [x] Implement atomic consume, one snooze and disable using stored action tokens; recheck open session/break within claim transaction.
- [x] Keep schedule/delivery local; expose focusInvitations through LumaStorage and include local tables in replacement/reset cleanup.
- [x] Source-review replay, concurrent claims, stale generation and no catch-up behavior; record unverified handoff.

**Deferred proof:** Q02/Q03; E06/E07/E09.

## FD06 — Invitation platform/settings/navigation

**Depends on:** FD05. **Spec:** R05.

**Files:** create A/reminders/FocusInvitationPlatform.kt, FocusInvitationWork.kt and A/ui/focus/FocusInvitationSettings.kt. Modify A/reminders/ReminderWork.kt, ReminderIntents.kt, ReminderPlatform.kt; A/ui/focus/FocusSettingsRoute.kt, FocusSettingsScreen.kt; A/ui/LifeOsApp.kt; app/src/main/AndroidManifest.xml.

**Consumes:** FD05 repository and existing quiet-hours/notification permission helpers. **Produces:** namespaced reconciliation, generic notification/actions and protected empty-setup navigation.

- [x] Add off-by-default weekday/time settings with contextual optional notification permission/status.
- [x] Wire inexact one-shot alarms, work reconciliation and all R05 system/settings events without changing task/habit identities.
- [x] Post generic Open/Snooze/Disable only after atomic claim; validate tokens, explicit component and current schedule.
- [x] Route Open through lock/navigation guards; preserve any open session and confirm dirty-draft replacement.
- [x] Cancel only Focus invitation work/actions on disable or replacement; denied permissions leave manual Focus usable.
- [x] Source-review receiver exposure, cancellation namespacing, duplicate actions and private-text absence; record unverified handoff.

**Source handoff:** SOURCE COMPLETE — UNVERIFIED (2026-11-08). Tests, builds, lint, schema generation and device/emulator checks remain deferred to Phase Q/E.

**Deferred proof:** Q02/Q04; E06/E07/E08.

## FD07 — Setup and scope copy

**Depends on:** FD02, FD04, FD06. **Spec:** R06.

**Files:** create A/ui/focus/FocusSetupContent.kt. Modify FocusSetupState.kt, FocusScreen.kt, FocusRoute.kt, A/focus/FocusViewModel.kt, FocusSettingsScreen.kt and R/values/strings.xml, R/values-fr/strings.xml, R/values-fa/strings.xml. These locale paths were confirmed through IDE file discovery; read before editing.

**Produces:** neutral untouched validation, bounded optional tag area and accessible primary action.

- [ ] Add touched/attempted state to the retained setup reducer; clear it with success/new draft/relock/new generation; surface invalid prefills explicitly.
- [ ] Extract setup content, apply intention label/example and preserve all existing start gating/immutable retry behavior.
- [ ] Add compact tag disclosure, three visible suggestions and View tags with existing ten-item suggestion bound.
- [ ] Put Start above recent work/insights/history in an IME-aware action area; keep scroll/large-font fallbacks.
- [ ] Apply concise background/access scope copy and App shortcuts naming in all three locales.
- [ ] Source-review no new timer defaults, no silent names and no report failure disabling controls; record unverified handoff.

**Deferred proof:** Q02/Q05; E01/E10.

## FD08 — Completion hierarchy and compact optional actions

**Depends on:** FD03–04, FD07. **Spec:** R07.

**Files:** create A/ui/focus/FocusCompletionContent.kt. Modify FocusScreen.kt, FocusReflectionEditor.kt, FocusRoute.kt, FocusTaskCompletionAction.kt and localized strings.

**Produces:** durable saved status, reachable Done, explicit 5-minute/custom break and collapsed reflection.

- [ ] Extract terminal composition and distinguish target reached/early Stop/count-up end/interrupted.
- [ ] Place saved duration and Done before auxiliary forms; preserve authorized context and generic locked state.
- [ ] Add explicit five-minute action and custom 1–60 minute dialog; running break shows End/countdown.
- [ ] Collapse empty reflection; show saved summary/Edit; retain independent save/retry behavior.
- [ ] Place task completion, Repeat/new/history/pending destination in secondary hierarchy.
- [ ] Source-review leaving/skipping/cancelling cannot mutate credit or strand Done; preserve deleted-session cleanup; record unverified handoff.

**Deferred proof:** Q02/Q05; E03/E04/E10.

## FD09 — Recent-work query and idle-setup discovery

**Depends on:** FD04, FD07–08. **Spec:** R08.

**Files:** modify D/focus/FocusRepository.kt; C/focus/FocusDao.kt, RoomFocusRepository.kt; A/focus/FocusViewModel.kt; FocusSetupState.kt, FocusSetupContent.kt. Create A/ui/focus/FocusRecentWorkState.kt and FocusRecentWorkCard.kt.

**Produces:** observeRecentTerminal(range: TimeRange?, includeTaskOrigin: Boolean): Flow<FocusSession?> with R08 order/eligibility; one in-memory recent-work state.

- [ ] Implement query with positive saved credit, terminal state, ended-at/ID descending and privacy filter before LIMIT 1; no scan of all history.
- [ ] Implement optional positive-run-overlap range restriction for FD10 using existing half-open boundaries.
- [ ] Observe query only while eligible; guard requests by access and generation; distinguish loading/empty/error without stale title.
- [ ] Render one card after Start, with optional next step and authoritative Repeat reload.
- [ ] Confirm before replacing a dirty draft; handle removed/invalid source and task changes without auto-start.
- [ ] Source-review invalidation dependencies, no SavedStateHandle record caching and no hidden counts; record unverified handoff.

**Deferred proof:** Q02/Q03; E05/E08/E09.

## FD10 — One Review context item

**Depends on:** FD09. **Spec:** R09.

**Files:** modify A/ui/focus/WeeklyFocusCard.kt, FocusReportState.kt, FocusReportPresentation.kt, FocusHistoryRoute.kt, FocusHistoryViewModel.kt; A/ui/review/WeeklyReviewRoute.kt and A/ui/LifeOsApp.kt. Create A/ui/focus/FocusSessionDetailContent.kt by extracting shared terminal-detail composition from history, preserving correction/reflection/Repeat controls.

**Consumes:** FD09 range-aware candidate flow. **Produces:** Latest work in this period row and protected View session route.

- [ ] Use the displayed report range/viewing zone, with positive run overlap, not global latest or started-at filtering.
- [ ] Add one authorized intention/next-step row and View session. Extend FocusHistoryRoute with requestedSessionId: UUID? = null and an internal session query argument; load via existing indexed terminalSession(id), share extracted detail content and show missing/unlock states without scanning pages. Update every route call site with safe defaults.
- [ ] Suppress context with mixed protected report state; keep empty/error distinct and totals usable on optional query failure.
- [ ] Preserve distribution/goals/Plan another session semantics; add no new aggregate/time logic.
- [ ] Source-review current/previous-period headings and all navigation paths; record unverified handoff.

**Deferred proof:** Q02/Q03; E05/E11.

## FD11 — Integrated lifecycle, privacy and localization sweep

**Depends on:** FD01–10. **Spec:** all integration requirements.

**Files:** A/ui/LifeOsApp.kt, A/focus/FocusRuntime.kt, FocusViewModel.kt, FocusExpiryPlatform.kt, FocusBreakRuntime.kt; A/ui/privacy/AppDataReset.kt; A/ui/backup/BackupViewModel.kt; C/backup/RoomBackupService.kt; C/LumaStorage.kt; changed localized resources.

- [ ] Connect restore/reset quiescing to invitation cancellation, break termination and all new caches/action tokens.
- [ ] Audit every Focus entry (Today, More, Tasks, Review, history, reminder, pending destination) for singleton/open-session precedence and current access.
- [ ] Reconcile full FX01/FX07/FX09/FX10 contracts; retain landed timer/reflection/goals/correction behavior.
- [ ] Complete three-locale copy, semantics, logical ordering and source-level IME/inset composition.
- [ ] Record source map and residual concerns for QA; do not claim layout/runtime validation.
- [ ] Mark source handoff only after dependencies exist and all call sites are wired.

**Deferred proof:** Q01–05; E01–12.

## FD12 — Documentation and source-complete handoff

**Depends on:** FD01–11.

**Files:** README.md; docs/BACKUP_FORMAT.md; new task ledger/spec/plan; historical docs/FOCUS_INSIGHTS_TASKS.md only for an additive forward link/status reconciliation, never rewriting historic evidence.

- [ ] Replace obsolete README Focus-unimplemented claim with current source/acceptance distinction and link this package.
- [ ] Document archive 12 portability and local-only invitation data.
- [ ] Reconcile every carry-forward row, recording exact task/source evidence and preserving existing partial QA history.
- [ ] Publish list of changed files, actual schema/archive reservations, known migration/backup/instrumentation failures and unexecuted checks.
- [ ] Complete read-only spec coverage/interface review and mark FD01–FD12 SOURCE COMPLETE — UNVERIFIED.
- [ ] Hand off to Q01. Do not run tests/build/emulator in this task and do not mark acceptance complete.

## Self-review record for this plan

- All FD-R01–09 requirements map to FD tasks and FD-A01–09.
- Unimplemented FX01/FX03/FX08/FX09/FX10 are covered; FX07's dependency closure is FD11. Landed source is preserved.
- API names match the new spec and actual repository ownership; old Todo UUID revisions are preserved.
- Each task lists deferred proof in separate validation documents; no source task contains a hidden test/build/deploy step.
- Conditional research backlog is not included in the mandatory source-complete gate.
- Execution begins with FD01 on a future implementation instruction; no application code was changed by generating this plan.
