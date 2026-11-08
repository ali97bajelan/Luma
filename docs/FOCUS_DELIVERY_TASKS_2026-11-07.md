# Focus delivery — tasks and execution index

Date: 2026-11-07 · Status: PLANNING COMPLETE; FD01–FD08 SOURCE COMPLETE — UNVERIFIED; FD09–FD12 NOT STARTED; Q/E NOT RUN.

**Start here:** [Implementation plan](superpowers/plans/2026-11-07-focus-delivery.md) · [Specification](superpowers/specs/2026-11-07-focus-delivery-design.md)
**After implementation:** [QA plan](feature-roadmap/focus-delivery-qa-2026-11-07.md) · [Emulator/device plan](feature-roadmap/focus-delivery-emulator-2026-11-07.md)
**Input:** [Product review](FOCUS_PRODUCT_REVIEW_2026-11-07.md) · [Previous Focus ledger](FOCUS_INSIGHTS_TASKS.md)

## Execution agreement

The user requested implementation-ready documents covering the product review and unfinished past tasks, with QA/emulator work separated from **all** implementation tasks.

1. Implement FD01–FD12 in source dependency order. Read/edit/source-review only; no test/fixture authoring, Gradle, build, lint, schema generation, APK, emulator or device operation.
2. Mark a completed source task **SOURCE COMPLETE — UNVERIFIED**, never QA PASS or accepted.
3. When **every** FD task is source complete, enter Q01–Q05 for test authoring/repair, host/static checks, builds and artifacts.
4. Then execute E01–E12 for Room/device instrumentation and emulator/manual testing.
5. Q06 reconciles final candidate acceptance after E evidence and any fixes/retests.

No per-task QA gate blocks a later source task once its source dependencies exist. Known validation failures remain in the separate QA backlog. Do not guess fixes or hide possible migration data loss to make a source task appear complete.

This new index consolidates unfinished work without resetting historic evidence or implying already-landed source must be rebuilt. Source versions inspected: **Room 14, archive 11**, proposed additions **Room 15/archive 12**. Existing app flavor: **production**, debug ID suffix **.qa**. Recheck these at FD01; old plan reservations and variant assumptions are obsolete.

The planning package changed documentation only when created. FD01–FD07 source delivery is tracked below; QA, builds, emulator/device checks and release remain unperformed.

## Implementation task ledger

The checkboxes below track current source delivery only; source completion is separate from QA and acceptance.

| ID | Priority | Source deliverable | Depends on | Review / carried scope | Acceptance / later proof |
| --- | --- | --- | --- | --- | --- |
| FD01 | P0 | Task/provenance and invitation contracts; Room migration source; archive compatibility | Existing Focus source | R1/R4, unfinished FX01 | FD-A01; Q02/Q03, E02/E09 |
| FD02 | P1 | Task editor entry, paged literal-search picker, editable task setup, linked saved totals | FD01 | R4, FX03 task entry | FD-A02; Q02, E02 |
| FD03 | P1 | Independent task completion, terminal unlink, safe task deletion/invalidation | FD02 | R4, remaining FX03/FX09 | FD-A02; Q02/Q03, E03 |
| FD04 | P0 | Dual-section origin privacy, private catalog/query rules, mixed-report suppression | FD01–FD03 | R1/R4, FX07/FX09/FX10 privacy | FD-A03; Q02/Q03, E08 |
| FD05 | P2 carried | Pure invitation planning and local occurrence/config persistence | FD01/FD04 | unfinished FP06/FX08 | FD-A04; Q02/Q03, E06/E07 |
| FD06 | P2 carried | Invitation settings, inexact alarms/actions, protected navigation and reconciliation | FD05 | unfinished FP06/FX08 | FD-A04; Q04, E06/E07 |
| FD07 | P1 | Simpler intention setup, neutral validation, bounded tags, accessible Start and truthful copy | FD02/FD04/FD06 | R1/R2 | FD-A05; Q02/Q05, E01/E10 |
| FD08 | P1 | Saved-result/Done hierarchy, compact break customization and optional reflection | FD03/FD04/FD07 | R3 | FD-A06; Q02, E03/E04 |
| FD09 | P2 | One authorized recent-work card, deterministic query, dirty-draft confirmation and Repeat | FD04/FD07/FD08 | R5 | FD-A07; Q02/Q03, E05/E08 |
| FD10 | P2 | One recent context row in existing weekly Review, in-range positive-run selection | FD09 | R6; preserve FP08 | FD-A08; Q02/Q03, E05/E11 |
| FD11 | P0 | All-entry integration, restore/reset cancellation, generation/privacy and localization sweep | FD01–FD10 | remaining FX01/FX07/FX09/FX10 | FD-A09; Q01–Q05, E01–E12 |
| FD12 | P0 | README/archive docs and accurate source-complete handoff | FD01–FD11 | R1 documentation/release confidence | FD-A09; Q05/Q06 |

- [x] FD01 source complete — UNVERIFIED
- [x] FD02 source complete — UNVERIFIED
- [x] FD03 source complete — UNVERIFIED
- [x] FD04 source complete — UNVERIFIED
- [x] FD05 source complete — UNVERIFIED
- [x] FD06 source complete — UNVERIFIED
- [x] FD07 source complete — UNVERIFIED
- [x] FD08 source complete — UNVERIFIED
- [ ] FD09 source complete
- [ ] FD10 source complete
- [ ] FD11 source complete
- [ ] FD12 source complete

### FD01 source handoff

- Status: SOURCE COMPLETE — UNVERIFIED.
- Changed source: `core/domain/.../focus/FocusMetadata.kt`, `FocusSession.kt`, `FocusGoals.kt`, `FocusRepository.kt`, new `FocusInvitations.kt`; `core/domain/.../todo/TodoRepository.kt`; `core/data/.../focus/FocusEntities.kt`, `FocusDao.kt`, `FocusProductDao.kt`, new `FocusInvitationEntities.kt`, `RoomFocusRepository.kt`, `RoomFocusProductRepository.kt`; `core/data/.../db/LumaDatabase.kt`; `core/data/.../backup/BackupArchive.kt`, `FocusMetadataArchiveAdapter.kt`, `BackupValidation.kt`; existing Focus result handlers in `FocusGoalSettings.kt`, `FocusReflectionEditor.kt`, `FocusSessionMetadataEditor.kt` and `FocusTagManager.kt`.
- Implemented: task ID/origin normalization and mapping; Start-time open-task validation with a distinct, receipt-stable selection conflict; Room 15 task link and device-local invitation tables; archive 12 task projection with explicit task defaults for archives 7–11 and staged reference/provenance validation.
- Interfaces/version decisions: kept the existing Start signature with defaulted input fields; new Start receipts use `START_V4` while preserving `START_V2`/`START_V3`; the invitation config, occurrence and repository contracts are ready for FD05. Room 14 and archive 11 were confirmed, reserving Room 15 and archive 12.
- Ruling: added the nullable task foreign-key column using `ALTER TABLE` instead of rebuilding `focus_session`. Migration 9→10 already adds the tag foreign key this way; the nullable reference preserves existing rows and child/runtime relationships. Cost if wrong: migration/schema validation can fail; Q03/E09 own that proof.
- Source review covered model/entity mappings, Start receipt identity and replay, migration order, archive defaults, task references and invitation exclusion from the portable table inventory. Tests, builds, schema generation, lint and device/emulator checks were NOT RUN in this task, as required by Phase I.
- Later proof: Q02/Q03 and E02/E09.

### FD02 source handoff

- Status: SOURCE COMPLETE — UNVERIFIED.
- Changed source: `core/domain/.../todo/TodoRepository.kt`; `core/data/.../todo/TodoDao.kt`, `repository/RoomTodoRepository.kt`; `core/domain/.../focus/FocusGoals.kt`, `core/data/.../focus/FocusDao.kt`, `RoomFocusProductRepository.kt`; `app/.../ui/focus/FocusTaskPicker.kt` (new), `FocusTaskPickerState.kt` (new), `FocusSetupState.kt`, `FocusScreen.kt`, `FocusRoute.kt`, `app/.../focus/FocusViewModel.kt`, `app/.../ui/LifeOsApp.kt`, `AppDestinations.kt`, `app/.../lock/ProtectedCatalogRepositories.kt`, `ProtectedAggregateRepositories.kt`, `ui/todo/TodoEditorRoute.kt`, `TodoEditorScreen.kt`; localized EN/FR/FA strings.
- Implemented: escaped literal open-task search/count with a transactional count/page snapshot, stable created-at/ID descending order and a 50-row cap; picker paging/loading/error/empty handling with cancellation and request identity; saved-task Focus entry with Save/Discard/Cancel behavior; editable task-name setup and Start-time task-selection conflict handling; terminal linked Focus totals that exclude open sessions.
- Interfaces: `TodoRepository.searchOpen(query, offset, limit = 50)`, `FocusProductRepository.observeTaskFocusMillis(taskId)`, and `FocusViewModel.focusOnTask(taskId)` / `clearTaskSelection()`.
- Privacy and navigation: task search is routed through the protected task repository; task picker, task-origin Focus and linked totals require both TASKS and FOCUS; task-origin presentation is redacted and setup title/draft content is cleared on either lock while the persisted origin survives unlink.
- Ruling: FD02's original file map omitted `FocusScreen.kt`, `AppDestinations.kt`, `ProtectedCatalogRepositories.kt`, `ProtectedAggregateRepositories.kt` and localized resources. Added these for reachable setup controls, route registration, guarded task/aggregate reads and EN/FR/FA UI. Cost if this scope is wrong: a few additional integration files in the task-linked Focus surface.
- Source review covered SQL wildcard escaping and page ordering, count/page transaction scope, stale picker/task loads, permission checks around reads and Start, callback defaults, Save Continue failure behavior, and terminal-session-only totals. Tests, builds, lint, schema generation and device/emulator checks were NOT RUN as required by Phase I.
- Later proof: Q02 and E02/E08.

### FD03 source handoff

- Status: SOURCE COMPLETE — UNVERIFIED.
- Changed source: `core/domain/.../focus/FocusGoals.kt`; `core/data/.../focus/FocusDao.kt`, `RoomFocusProductRepository.kt`; `core/data/.../repository/RoomTodoRepository.kt`; new `app/.../ui/focus/FocusTaskCompletionState.kt` and `FocusTaskCompletionAction.kt`; `app/.../focus/FocusViewModel.kt`, `app/.../ui/focus/FocusHistoryRoute.kt`, and `LifeOsApp.kt`.
- Implemented: revision-paired task completion after eligible saved USER_STOP/TARGET_REACHED sessions; distinct missing/completed/conflict/I/O states; retained immutable completion revisions for ambiguous retries; terminal unlink through a generation-scoped product receipt; task deletion detaches every linked session in its transaction, advances session/runtime revisions, and preserves task origin. Room invalidation updates linked totals after unlink or deletion.
- Interfaces: `FocusProductRepository.unlinkTask(sessionId, expectedRevision, context)` and `PendingFocusTaskCompletion(taskId, expectedRevision, nextRevision, generation)`.
- Ruling: rendered completion/unlink actions in expanded terminal history detail and delegated mutations to `FocusViewModel`; this matches the spec's terminal-detail entry point and keeps Stop separate. Cost if wrong: users must open a saved history detail to complete a task until FD08 adds the action to the completion hierarchy.
- Source review covered eligibility, task/session revision checks, immutable retry identity, generation/access invalidation, product receipt replay, provenance preservation and running-runtime revision coordination. Tests, builds, lint, schema generation and device/emulator checks were NOT RUN as required by Phase I.
- Later proof: Q02/Q03 and E02/E03/E09.

### FD04 source handoff

- Status: SOURCE COMPLETE — UNVERIFIED.
- Changed source: `core/domain/.../lock/ProtectedRecordClassifier.kt`; Focus domain contracts and projections in `FocusGoals.kt`, `FocusHistoryQuery.kt`, `FocusInsights.kt`, `FocusRepository.kt`, and `FocusStatistics.kt`; `core/data/.../focus/FocusDao.kt`, `RoomFocusProductRepository.kt`, and `RoomFocusRepository.kt`; `app/.../LumaApplication.kt`, `focus/FocusViewModel.kt`, `lock/ProtectedAggregateRepositories.kt`, `lock/ProtectedCatalogRepositories.kt`, `ui/LifeOsApp.kt`, `ui/todo/TodoEditorRoute.kt`, `TodoEditorScreen.kt`, `ui/focus/FocusGoalReport.kt`, `FocusHistoryRoute.kt`, `FocusHistoryViewModel.kt`, `FocusInsightsCard.kt`, `FocusInsightsState.kt`, `FocusReportState.kt`, `FocusRoute.kt`, `FocusScreen.kt`, `FocusSettingsRoute.kt`, `FocusSettingsScreen.kt`, `FocusSetupState.kt`, `FocusTagManager.kt`, `TodayFocusCard.kt`, `WeeklyFocusCard.kt`, `ui/review/WeeklyReviewRoute.kt`, and `ui/today/TodayRoute.kt` / `TodayScreen.kt`.
- Implemented: persisted `taskOrigin` classifies sessions independently of the current task link or task name; task-origin content reads and edits require both FOCUS and TASKS. The protected Focus repository applies admission at Start and product writes, while generic active-session timer state and Pause/Resume/Stop controls remain available when protected content is locked. The Focus presentation redacts intention, tag, reflection, task ID and completion context on either lock. Repeated-session loads recheck authorization before publishing task-derived drafts.
- History pages filter task-origin rows before the page limit and total are computed. Tag suggestions and catalogs hide labels referenced by task-origin sessions when TASKS is locked; rename and merge require both sections and stale merge prompts are cleared. Mixed task-origin reports return an unavailable-until-unlock state rather than partial totals, with unlock actions wired from Focus insights, Today, Review, history and Focus settings. Task deletion also requires both section writes because deleting a task detaches its linked Focus rows; the task editor requests unlock when either section is unavailable.
- Interfaces: added `FocusHistoryQuery.includeTaskOrigin`, task-aware overloads for Focus tag suggestion/catalog flows, and `FocusRepository.sessionHasTaskOrigin` / `FocusProductRepository.sessionHasTaskOrigin` authorization hints. `ProtectedRecordClassifier.requiredSections(session)` centralizes the durable provenance rule.
- Ruling: `LumaApplication` now supplies the protected Focus repository to `FocusCoordinator`, so Start and writes are checked at the repository boundary as well as through UI access state. Generic timer actions retain a narrowly scoped fallback for the current open session. This boundary is needed to close races between lock changes and commands. Task deletion is also guarded for both sections because Room detaches linked Focus rows as part of task deletion. If either guard proves too broad, Q02/Q03 will identify the affected write paths and E08 will validate locked timer controls.
- Source review covered durable origin classification after unlink/delete, mixed-section grants, filtering before paging, mixed-report suppression, tag label privacy, post-suspension draft publication, active timer controls and relock presentation. Tests, builds, schema generation, lint and device/emulator checks were NOT RUN in this task, as required by Phase I.
- Later proof: Q02/Q03 and E03/E08/E09/E11.

### FD05 source handoff

- Status: SOURCE COMPLETE — UNVERIFIED.
- Changed source: new `core/domain/.../focus/FocusInvitationRules.kt`, `core/data/.../focus/FocusInvitationDao.kt`, and `RoomFocusInvitationRepository.kt`; modified `core/domain/.../focus/FocusInvitations.kt`, `core/data/.../db/LumaDatabase.kt`, `LumaStorage.kt`, and `backup/RoomBackupService.kt`.
- Implemented: default-off config with whole-minute validation; revision-checked, receipt-backed config writes; civil-time scheduling with first-valid-time DST-gap handling and earlier-offset overlap handling; inclusive 60-minute delivery windows; one local-date occurrence row; atomic skip/claim, open-session/break eligibility recheck, one snooze, local action-token access, and disable with revision advancement.
- Interfaces: exposed `focusInvitations` through `LumaStorage`; added generation observation/access, occurrence preparation, quiet-hour skip, trusted token lookup and snoozed-slot lookup for FD06. Config writes carry the observed revision and increment it once when values change.
- Persistence/privacy: uses the FD01 Room 15 tables and existing generation/receipt authority; no schema-version bump or archive projection. Restore clears invitation deliveries, rotates generation and resets the local schedule to disabled 09:00 defaults.
- Ruling: extended the initial repository contract with slot preparation, skip and token lookup methods because FD06 needs durable alarm identity, explicit quiet-hour consumption and locally stored action tokens. Cost if wrong: a wider invitation interface for FD06 to maintain.
- Ruling: restore resets invitation settings to disabled defaults instead of retaining an enabled local schedule, so restore reconciliation cannot reactivate reminders. Cost if wrong: users must reselect their invitation days and time after restore.
- Source review covered revision replay/payload mismatch, concurrent claim serialization, one-snooze state transition, deadline boundaries, stale generation/revision rejection, current-zone revalidation, claim-time session/break checks, and replacement cleanup. Tests, builds, schema generation, lint and device/emulator checks were NOT RUN as required by Phase I.
- Final review: fresh read-only review found no FD05 source-level blockers; alarm, notification, permission, quiet-hours and navigation integration are deferred to FD06.
- Final: Ruling: platform delivery remains owned by FD06 because FD05 owns invitation rules and persistence — cost if wrong: reminders stay incomplete until FD06.
- Final: Ruling: tests, builds and schema generation remain deferred to Phase Q as the plan requires — cost if wrong: integration defects may remain undetected until Q.
- Later proof: Q02/Q03; E06/E07/E09.

### FD06 source handoff

- Status: SOURCE COMPLETE — UNVERIFIED.
- Changed source: new `app/.../reminders/FocusInvitationPlatform.kt`, `FocusInvitationWork.kt`, and `ui/focus/FocusInvitationSettings.kt`; modified `reminders/ReminderIntents.kt`, `ReminderWork.kt`, `ReminderPlatform.kt`; `ui/focus/FocusSettingsRoute.kt`, `FocusSettingsScreen.kt`, `FocusRoute.kt`, `FocusScreen.kt`; `ui/LifeOsApp.kt`, `MainActivity.kt`, `app/src/main/AndroidManifest.xml`; and EN/FR/FA localized strings.
- Implemented: default-off weekday/time settings with a weekday requirement, existing notification permission/status and shared quiet-hours behavior; inexact one-shot alarm scheduling and reconciliation; generic Open/Snooze/Disable notification actions with explicit private components, schedule/token validation and atomic repository operations; protected Open navigation into Focus setup without starting; preservation of an open session and confirmation before replacing a dirty draft; reconciliation after schedule changes, app/settings resume and the existing boot/package/time/timezone hooks.
- Interfaces: `enqueueFocusInvitationReconciliation(context)` owns Focus invitation work; `enqueueReminderReconciliation(context)` now delegates to it. Open intents carry only the slot identity and local action token; UI validation checks current repository state and access before navigation.
- Ruling: Focus invitations use an independent platform registry and PendingIntent/notification identity namespace. Generic reminder cancellation now targets tracked task/habit IDs only, so a task or habit reset cannot cancel Focus invitation alarms. Cost if wrong: duplicate registry and cancellation plumbing across reminder kinds.
- Source review covered explicit/non-exported receiver boundaries, stale generation/revision/token handling, one-shot cancellation, quiet-hour and permission skips, generic notification content, session preservation and draft replacement. Tests, builds, lint, schema generation and device/emulator checks were NOT RUN in this task, as required by Phase I.
- Later proof: Q02/Q04; E06/E07/E08.

### FD07 source handoff

- Status: SOURCE COMPLETE — UNVERIFIED (2026-11-08).
- Changed source: new app/.../ui/focus/FocusSetupContent.kt; modified ui/focus/FocusSetupState.kt, FocusScreen.kt, FocusRoute.kt, focus/FocusViewModel.kt, ui/focus/FocusSettingsScreen.kt, ui/common/AppPageScaffold.kt; EN/FR/FA strings.
- Implemented: per-field touched and attempted state stored with the setup draft; neutral validation until an edited field blurs, Start/IME submission is attempted, or a task/Repeat prefill is invalid; interaction state resets on success, new draft, access loss and generation change. Extracted setup content uses the intention label/example, preserves Count-up and the existing 1–180 minute timed range, and keeps the 25-minute choice visible. Tags are collapsed by default, show at most three recent suggestions, and open an accessible picker capped at ten. Start remains gated by the existing normalized input, busy/unresolved state, Repeat load and operation state; it now lives in an inset-aware persistent action area with concise scope copy. Settings retain the fuller profile/enforcement explanation and delayed-alert guidance, and label the app choices “App shortcuts” in all locales.
- Interfaces: added reducer events for blur, attempted Start and tag disclosure; encoded the corresponding flags with SavedStateHandle; wired them through FocusViewModel, FocusRoute and FocusScreen. Added an optional bottomBar slot to AppPageScaffold, preserving existing call defaults.
- Ruling: added the shared scaffold slot to anchor Start above IME/system insets while keeping other pages source-compatible. Cost if wrong: a shared page scaffold API now carries a slot used by Focus setup.
- Source review covered untouched validation, invalid task/Repeat prefill, reducer and restore resets, stale-generation clearing, immutable Start gating, three/ten-item tag bounds, accessibility targets, IME/system inset behavior, unchanged timer defaults and settings scope copy. Tests, fixtures, builds, schema generation, lint and device/emulator checks were NOT RUN as required by Phase I.
- Final review: author self-review; delegation was disabled by the active task instructions.
- Later proof: Q02/Q05; E01/E10.

### FD08 source handoff

- Status: SOURCE COMPLETE — UNVERIFIED (2026-11-08).
- Changed source: new `app/.../ui/focus/FocusCompletionContent.kt`; modified `FocusScreen.kt`, `FocusReflectionEditor.kt`, `FocusRoute.kt`, `FocusTaskCompletionAction.kt`; EN/FR/FA strings.
- Implemented: extracted the terminal composition and ordered terminal-specific status, authorized session context, saved duration and enabled Done before auxiliary actions. Target-reached, timed early Stop, count-up end and interruption receive distinct copy. Added an immediate five-minute break and a custom 1–60 minute dialog with its own Start/Cancel; the existing running-break countdown/End control remains the only break control while a break is active. Empty reflection stays collapsed; saved reflection remains summarized with Edit and keeps its existing independent save/retry operation. Task completion and unlink actions are now reachable in the terminal hierarchy below reflection, alongside Repeat, Start another, History and pending destination.
- Interfaces/privacy: wired existing FocusViewModel task-completion and unlink methods through FocusRoute/FocusScreen. Passed current task-content readability into completion so the reflection editor does not reload task-origin text when TASKS is locked; task actions also require current Focus and TASKS readability in addition to the ViewModel grant.
- Review follow-up: an independent read-only review found that a stale ViewModel permission could briefly show “Task link removed” during relock. The current access gate now suppresses task actions immediately; this fix received source-only review, with tests/builds deferred under Phase I.
- Ruling: added the existing task-completion action to the completion surface while retaining it in history detail — the FD08 plan requires terminal task completion and both entry points reuse the same revision/generation-checked ViewModel contract — cost if wrong: the action is available in two terminal-detail locations.
- Source review covered terminal-reason mapping, Done reachability and ordering, protected context/reflection re-read, 1–60 input handling, no-start behavior during active breaks, retained reflection retries, task-action eligibility and unlink state. Tests, fixtures, builds, schema generation, lint and device/emulator checks were NOT RUN as required by Phase I.
- Later proof: Q02/Q05; E03/E04/E10.

Priority represents product/risk value, not permission to reorder source dependencies. Invitations are included because they are unfinished previously specified work, even though the new review ranks basic usability above adding reminders. No new reminder/preset expansion is implied.

## Unfinished past-task reconciliation

Baseline from the existing 7 November ledger; no old source-complete flag means accepted.

| Existing work | Baseline at planning | Forward owner |
| --- | --- | --- |
| FM01–FM07 baseline timer/storage/report/backup | Landed with scoped evidence; full platform/performance/integrated gaps remain | Preserve source; Q/E carried regression, not a duplicate implementation task |
| FM08 device-wide restriction | Deferred and unmet | Remains excluded; no task claims blocking |
| FI01 name/tag rules | Targeted 34/34 domain result recorded | Preserve; Q02 regression on final candidate |
| FI02 metadata/Start identity | Targeted Room/app/emulator passes; broader module not green | FD01 extends identity; Q02/Q03/E02/E09 revalidate |
| FI03 migration/archive | Adapter target passed, stale runner, two Room migration failures | Q01/Q03/Q04/E09 diagnose/repair; FD01 adds forward source compatibility |
| FI04 insights | Targeted result; direct Room observeInsights invalidation gap | Q03 authors coverage; E11 executes |
| FI05 setup | Source delivered; Android layout/lifecycle acceptance incomplete | FD07 refines; E01/E10 verify |
| FI06 history | Source delivered; Room/Compose/access lifecycle acceptance incomplete | FD04/FD11 integrate; E05/E08/E11 |
| FI07 privacy/resources | Source delivered; Android lock/replacement acceptance incomplete | FD04/FD11; Q05/E08/E09/E10 |
| FX01 shared foundation | Timer/break/goal/reflection/correction and invitation source delivered; integration proof remains | FD01 + FD05/FD06; integration FD11; Q/E carry regression |
| FX02 Repeat | Name/tag/timer/next-step source complete; task-copy missing | FD02/FD09 add task-aware discovery; Q/E carry Repeat checks |
| FX03 task integration | Not started | FD02/FD03/FD04 |
| FX04 timed expiry/recovery | Source complete; narrow 7 Nov smoke/cleanup only | Preserve source; Q02/Q03/E04 carry full lifecycle/races |
| FX05 break | Source complete, unverified | FD08 changes presentation only; Q02/E03/E04 validate |
| FX06 reflection | Source complete, unverified | FD08/FD09 presentation/context; Q02/Q03/E03/E05 |
| FX07 goals/distribution | Landed source; overall task partial on shared foundation | FD04/FD11 close dependencies; Q02/Q03/E11 |
| FX08 invitations | FD05/FD06 source delivered, unverified | Q02/Q04; E06/E07/E08 |
| FX09 corrections/tag merge | Metadata/tag paths landed; task unlink and dual access pending | FD03/FD04; Q03/E03/E08 |
| FX10 integrated handoff | Blocked by unfinished dependencies | FD11/FD12 after FD01–FD10 |
| FX-QA | Not run as integrated final phase | Consolidated Q01–Q06 and E01–E12; all old mandatory cases retained |

Do not mark any old task accepted merely because a new task references it. At FD12 add forward links/status reconciliation only; preserve dates/results in historic records.

## Separate QA task ledger

Every row currently NOT RUN. Test/fixture writing belongs here, never in FD tasks.

| ID | Deliverable | Entry |
| --- | --- | --- |
| Q01 | Candidate/tool/version inventory, known-failure baseline and evidence manifest | All FD source complete |
| Q02 | Author/run domain and app state tests, immutable retry/privacy/scheduling rules | Q01 |
| Q03 | Author persistence/archive/invalidation fixtures and tests; run host archive/SQL checks | Q01; coordinate Q02 fixtures |
| Q04 | Full host suites, compile/lint, schema generation, instrumentation build and isolated minified artifact | Q02/Q03 authored |
| Q05 | Resource/permission/query-plan/document checks and E-phase readiness | Q02–Q04 prerequisites pass |
| Q06 | Final candidate evidence reconciliation and acceptance, after emulator phase | E01–E12 evidence and affected reruns |

Known issues explicitly assigned here: migration seeded-journal loss versus fixture diagnosis, stale table inventory, backup Long/String casts, stale archive runner dependency discovery, app instrumentation API drift and direct Room insights invalidation coverage. These are not silently folded into feature source-complete claims.

## Separate emulator/device task ledger

Every row currently NOT RUN; entry is all FD source complete plus Q01–Q05 prerequisites.

| ID | Journey |
| --- | --- |
| E01 | Setup, neutral validation, Start and scope understanding |
| E02 | Task entry/picker/draft handling, conflict, totals, deletion |
| E03 | Saved completion, reflection, break, task completion/unlink/corrections |
| E04 | Timer expiry, screen off/background, pause/resume, process death, force-stop, reboot and uncertainty |
| E05 | Recent work/Repeat, dirty drafts, deleted sources and Review range |
| E06 | Invitation permission/status/actions and reminder-kind isolation |
| E07 | DST, delivery window, system events, Doze and stale notification actions |
| E08 | Cross-section locks, marker absence and relock races |
| E09 | Room migration, legacy/current archives, native pickers, restore/reset and stale generations |
| E10 | EN/FR/FA, RTL, IME, font/window/theme, TalkBack and keyboard |
| E11 | Goals/reports/invalidation, bounded queries, 50k performance and errors |
| E12 | Final integrated journey/regression and isolated minified runtime smoke |

Do not run on a personal installation. Verify actual app/test target isolation and candidate hashes. Coverage requirements and independent expected results live in the emulator plan.

## Post-release and conditional backlog

These are recorded so “unfinished post tasks” are not lost. They are **not** FD implementation dependencies or approved additions; each requires its trigger and a new scoped design.

| ID | Idea | Evidence/decision needed before implementation |
| --- | --- | --- |
| POST01 | Recent-work entry on Today | Idle-setup Recent/Repeat demonstrably helps return to work; confirm Today density and dual-section privacy |
| POST02 | Saved reusable presets | Users repeatedly need multiple stable configurations that Recent/Repeat cannot serve; specify rename/delete/backup |
| POST03 | Dedicated Focus widget | Entry friction persists; design launcher caching, summary defaults, lock-safe names/actions |
| POST04 | Smaller quick-duration option | Usability evidence users struggle to begin; compare a convenience choice without changing custom range |
| POST05 | Richer Review grouping by task/tag | One context item insufficient; agree aggregation and task-privacy semantics first |
| POST06 | Calmer visual progress/reward | Participants want it and it helps without pressure; avoid punitive streaks or attention claims |
| POST07 | Ambient audio | Users identify a need worth playback/content/lifecycle scope |
| POST08 | Strict device-wide blocking | Separate platform/permission/distribution feasibility and product decision; FM08 stays unmet |
| POST09 | Automatic multi-cycle Pomodoro | Explicit demand and a separate auto-start/recovery contract |
| POST10 | Social rooms/leaderboards | Evidence of need plus consent/account/sharing design; no mandatory competition |
| POST11 | Calendar planning/cross-device sync/project hierarchy | Separate product scope, data ownership and local-first/privacy decision |
| POST12 | AI coaching or focus/mood scores | Reliable interpretation and explicit privacy design; no causal claims from timer data |

Separate product research: recruit 6–8 consenting first-time/returning task/study users, including accessibility/RTL needs, to compare start/completion/recent-work flows. Record success/assistance/timing/comprehension and voluntary return-to-work feedback. No production telemetry or invented uplift target. This study is NOT RUN; it is not replaced by technical emulator acceptance.

## Source handoff template

For each FD task record:

- Status: NOT STARTED / IN PROGRESS / BLOCKED / SOURCE COMPLETE — UNVERIFIED.
- Changed source paths and implemented requirement IDs.
- Interface/version decisions and dependent call-site wiring.
- Read-only source review performed; unresolved source dependencies.
- Explicit statement: tests/build/schema generation/emulator NOT RUN in this task.
- Later Q/E owner IDs; no copied historic PASS.

## Completion criteria

Planning package: new specification, implementation plan, this ledger, separate QA plan and separate emulator plan, with dependency/acceptance crosswalks.

Source delivery: every FD source checkbox complete and spec contracts wired. This does not imply tests pass.

Technical acceptance: required Q/E cases and applicable FI/FX/F16 regressions pass on the final candidate, with no unexplained failures or missing mandatory coverage. Report physical-device/platform limits and product research separately. Device-wide enforcement remains outside scope.
