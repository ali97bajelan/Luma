# Focus delivery specification

Date: 2026-11-07
Status: READY FOR SOURCE IMPLEMENTATION; implementation and acceptance not performed by this document.

[Product review](../../FOCUS_PRODUCT_REVIEW_2026-11-07.md) · [Task ledger](../../FOCUS_DELIVERY_TASKS_2026-11-07.md) · [Implementation plan](../plans/2026-11-07-focus-delivery.md) · [QA](../../feature-roadmap/focus-delivery-qa-2026-11-07.md) · [Emulator tests](../../feature-roadmap/focus-delivery-emulator-2026-11-07.md)

## Authority, intent and delivery phases

Implement the review's calm, task-connected direction and finish the unimplemented portions of the existing Focus extensions. Help users begin named work, save time honestly, and return to a next step.

The user's current instruction establishes **all source implementation first, QA and emulator tests separately afterward**. This overrides per-task test/build instructions in generic workflows. This request creates planning documents only; it does not start application implementation.

- Phase I: FD01–FD12 source changes and source inspection only. No test/fixture authoring, test execution, Gradle, compilation, lint, schema generation, APK assembly, deployment or device/emulator commands.
- Phase Q: after every FD task is SOURCE COMPLETE — UNVERIFIED, author/repair tests and fixtures, build, generate schemas, run host/static QA and prepare device artifacts.
- Phase E: execute Room/device instrumentation and emulator/manual journeys against the final candidate. Its protocol is a separate document.
- Failures in Q/E are fixed within that validation phase, with affected checks rerun. A material change to requirements reopens the relevant source task.
- Source completion, QA pass, emulator pass and release acceptance are separate states. No historic pass certifies this candidate.

Use this package as the forward execution index. Preserve old ledgers/evidence as history. The [2026-11-04 extension specification](2026-11-04-focus-product-extensions-design.md), [insights specification](2026-11-03-focus-names-tags-insights-design.md) and [F16 specification](2026-10-28-roadmap-16-focus-mode.md) remain authoritative for unchanged behavior. This specification supersedes their obsolete version reservations, source map and the interaction requirements explicitly refined below. Existing QA cases remain required through the new QA crosswalk.

## Baseline and scope

Inspected current source: Room VERSION 14; archive CURRENT_BACKUP_SCHEMA_VERSION 11; production app flavor; minSdk 28, compileSdk/targetSdk 35, JDK 17.

Keep the existing Kotlin/Compose/Room/coroutine stack and dependencies. Add no network, exact-alarm, accessibility, usage-access, device-admin or DND permission. Notifications remain optional through the existing infrastructure.

Already present, to preserve rather than rebuild: count-up/timed sessions, expiry/recovery, explicit breaks, Repeat, reflection, goals, daily/weekly reports, history, metadata corrections and tag merges. These have incomplete acceptance. Finish task linking/provenance, task completion/unlink, invitations, shared persistence/privacy, and integrated replacement handling.

Current product repository is **FocusProductRepository in FocusGoals.kt**, implemented by **RoomFocusProductRepository**, a separate object sharing the same LumaDatabase with RoomFocusRepository. Do not create a second interface with the same name or follow the old suggestion to implement everything on one repository object.

### Scope decisions made for this handoff

These are explicit implementation defaults selected from the review, not research findings:

| Review | Delivery decision |
| --- | --- |
| R1 trust/copy | Update scope copy and delivery docs; carry unresolved release checks to Q/E |
| R2 setup | Required intention, neutral untouched validation, compact optional tag, accessible Start |
| R3 completion | Saved result and Done first; compact optional break/reflection |
| R4 task connection | Finish the existing FP02/FX03 contract |
| R5 recent work | One authorized recent-work card in idle setup only; no Today addition |
| R6 Review | One in-range recent context item inside WeeklyFocusCard; no additional dashboard |
| Conditional ideas | Today recent-work entry, presets, widget, short-duration experiment and richer motivation remain post-release research backlog |

## FD-R01 — Invariants and commands

Keep one open session, pause exclusion, capped target credit, explicit uncertain-gap recovery, guarded Luma navigation and reachable Pause/Stop/emergency controls. Background running time is recorded timer time, never verified attention.

- New names: nonblank, trimmed, one line, no ISO controls, at most **120 Unicode code points**.
- Tags: optional; preserve the current **50 Kotlin String code-unit** validation limit and normalized identity rules. Do not silently reinterpret this as 50 code points.
- Timed target: **1–180 whole minutes**; count-up default; **25-minute** convenience.
- Break: **1–60 whole minutes**, **5-minute** convenience, no credited focus.
- Reflection: optional stable outcome plus trimmed one-line next step up to **280 Unicode code points**; no ISO controls.
- Existing goals: off by default, **1–10,080 minutes**, **1–7 preferred weekdays**, strictly next Monday activation and captured goal zone; unchanged historic intervals.

Capture immutable mutation payloads, command UUID, generation and expected revisions before work. Retry the same payload/identity after ambiguous I/O; changed payload under an existing command ID rejects. Preserve legacy receipt decoding. Start identity adds task ID and origin; metadata/reflection writes cannot change mode, target or runs.

## FD-R02 — Task persistence and authorization

Extend FocusStartInput and FocusSession with taskId: UUID? = null and taskOrigin: Boolean = false. Effective origin is true whenever a task is selected or the intention came from a task, including edited/copied task names. Clearing/deleting/unlinking the task never clears origin. Normalization/import rejects or canonicalizes a linked record to origin=true; never accepts a linked record as standalone.

Persist indexed nullable focus_session.task_id referencing todo_task(id), ON DELETE SET NULL, and task_origin NOT NULL DEFAULT 0. Legacy rows are standalone. A session name is an independent snapshot: task rename does not rewrite it.

A Start transaction rechecks that the selected task exists and is open. A disappeared/completed task returns a distinct task-selection conflict, leaving an editable draft and an explicit Remove task action. Do not silently create an unlinked session or substitute another task. Titles longer than 120 code points remain visibly invalid until edited; no truncation.

All task-origin records require both TASKS and FOCUS reads/writes, even after task deletion or unlink. Derive access from current AppLockCoordinator/LockSnapshot decisions, never from a persisted boolean claiming authorization. Thread a current access/generation token through async work and check before publishing or writing. No grant is stored in the database or archive.

A running protected session always retains generic timer/Pause/Stop/emergency controls. Hide its intention/tag/next step/task labels when either required section is inaccessible. Clear cached private content and drafts on relock; requery after unlock.

Standalone Focus history remains accessible with Focus access. Mixed totals/goals/insights that contain protected task-origin contributions request unlock rather than show a partial total as complete. Tag catalog/suggestions must not reveal labels referenced by inaccessible origin records; conservatively suppress a label that has any protected reference when Tasks access is absent. Apply the same policy to rename/merge authorization across every affected record. Report filtering is not an authorization grant.

## FD-R03 — Task entry, completion and unlink

Offer Focus on this task only for an existing saved open task. Unsaved edits use Save and continue / Discard and continue / Cancel; failed save stays in the editor. Save may continue only after its current operation succeeds. Navigation carries IDs, not task titles in external intents.

Optional task picker: literal title search, 50 rows/page, created_at descending then ID descending, open tasks only. Count/filter before paging; reset paging on search changes and ignore stale responses. Escape literal SQL wildcard characters. Revalidate at Start.

Task detail shows full credited time for linked terminal sessions; show open contribution separately if present. Live invalidation updates totals after session deletion/unlink. Task deletion preserves Focus history and origin. Coordinate unlink/deletion with session/runtime revisions in the same transaction so active Pause/Stop cannot use a stale revision after an auxiliary update.

After saved USER_STOP or TARGET_REACHED, offer Mark task complete when the link remains valid/open and both grants are present. No automatic completion for interrupted recovery. Use existing TodoRepository.setCompleted(id, expectedRevision: UUID, nextRevision: UUID, completed=true). Hold both revision UUIDs across ambiguous retries; failure never retries Stop or erases saved focus. Conflicts reload the task before a newly explicit completion attempt.

Terminal detail may explicitly remove its task link, preserving origin, runs, name and timer. No reassignment to another task and no origin editor.

## FD-R04 — Source schema and archive

Reserve **Room 14→15** for task fields/FK/index plus invitation tables defined below; reserve **archive 11→12** for portable task link/origin. Recheck before editing; if occupied, choose the next unused versions together and update all five new docs before implementation proceeds. Do not edit generated Room JSON by hand; generation is Q phase.

Room rebuilding for the new FK must preserve all session/run data, indexes, constraints and unrelated tables. Keep existing versions 1–14 migration paths. Archive 12 preserves existing v11 portable inventory and adds session columns. Older archives 1–11 retain their actual layouts and adapt to taskId=null/taskOrigin=false, without resetting timer/reflection/goal fields that already exist in their version.

Backup tasks/tags before linked sessions, reject dangling task references and invalid provenance/target invariants in staging before replacement. Export terminal history only. Exclude open sessions, timer/break runtime, selected packages, invitations/delivery/actions, receipts, aliases and generation. Existing portable goals/reflections remain included.

Restore/reset quiesces writes, cancels Focus timers/invitations, ends local breaks, clears caches, rotates generation and rejects old callbacks/edits. Restoring goals does not enable reminders. No source task certifies migrations; preserve known migration failures for diagnosis in Q/E.

## FD-R05 — Invitations

Finish FP06/FX08 without presets or task references:

- One device-local schedule, disabled by default. Initial unsaved UI time is 09:00 with no selected days; enabling requires 1–7 weekdays. Disabled configuration may retain an empty day set. Persist minute_of_day in 0–1439 and a nonnegative revision (initial 0); follow current device timezone. These are handoff defaults, not recommended focus hours.
- Reuse notification permission/channel status and global quiet hours. Ask permission only after enabling, never at Focus arrival. Blocked alerts do not disable Focus.
- Inexact one-shot civil-time alarms plus existing reconciliation. Namespaced identities must not cancel task/habit/timer alarms.
- Reconcile on boot, package replacement, clock/timezone changes, settings/permission return, schedule edit and database replacement.
- DST gap: first valid time after the gap; overlap: earlier offset, once per local date.
- Delivery is eligible at its scheduled instant through **60 minutes late inclusive**; later is skipped. Skip quiet hours, any open Focus (including paused), and running breaks. Consume skipped/attempted occurrences without catch-up bursts.
- Generic notification: Open, Snooze 10 minutes, Disable. Open goes through protected navigation into empty setup; never starts Focus. If an open session now exists, show it rather than replacing it. While a draft exists, ask before discarding that draft.
- Snooze once per occurrence. Deadline is snoozed due time plus 60 minutes; recheck all eligibility. Duplicate/stale actions are safe; Disable advances schedule revision and invalidates actions.
- Validate explicit internal intents and stored occurrence/action tokens. Malformed, missing, wrong-generation, wrong-revision or unrecognized actions do nothing. No private text in intents, notifications or logs.

Local tables: focus_invitation_settings (singleton, enabled, revision, weekday_mask, minute_of_day); focus_invitation_delivery (generation, schedule_revision, local_date, due_at, state, snoozed_due_at nullable, action_token). Unique occurrence key is generation/revision/date. State transitions and claim are atomic. Keep action token local and invalidate on disable/replacement. At-most-once posting attempts are acceptable after a crash between claim and posting; do not duplicate a notification to compensate.

## FD-R06 — Setup

Split setup composition into a focused file instead of continuing to grow FocusScreen.kt. Keep runtime and command authority in the existing ViewModel/repositories.

Order: intention and optional task context → mode/duration → compact optional tag → concise scope explanation and Start → recent work → insights/history.

Intention label: What will you work on? Example: Read chapter 3. Untouched fields are neutral. Maintain touched/attempted state per draft. Show field errors after blur following an edit, an attempted Start/IME submission, or a loaded invalid task/legacy Repeat prefill. Programmatic prefill is not ordinary typing. After an error is shown, update it as the user edits.

Start stays disabled for invalid payload, busy/unresolved command or Repeat load. IME submit can mark attempted state but never bypass validation. Valid submission captures payload once. On success/Start another/new generation clear touched/attempted and private fields. Preserve draft interaction state through rotation; access loss clears private drafts. Reconcile ambiguous Start independently before allowing another Start.

Count-up stays default. Keep Timed and 25-minute shortcut visible; retain custom 1–180 input. No smaller-duration default in this scope.

Tag collapsed by default, shows selected label when authorized. Expand to optional field and at most **3 recent suggestions**, plus View tags opening an accessible selection surface with repository's existing bound of **10**. Do not render ten full-width rows before Start. Null/blank input means no tag; no new tag deletion or multi-tag rules.

Use a scrollable form with a persistent action area above IME/system insets; at 2× font allow vertical scroll rather than clipping essential controls. No fixed-height assumption. Use 48 dp targets (Start at least 52 dp), TalkBack semantics, keyboard ordering and EN/FR/FA resources.

Display near Start: The timer keeps counting outside Luma. Other apps remain available. Preserve fuller settings explanation and delayed-alert guidance. Label shortcut configuration App shortcuts; never device-wide allowlist.

## FD-R07 — Completion

Extract completion composition. Show saved confirmation only after durable terminal success. Order:

1. Session saved, credited duration, authorized context and precise terminal label.
2. Done, independently reachable and enabled even if auxiliary operations fail.
3. Take a 5-minute break plus Customize break. Direct action explicitly starts 5 minutes; customization opens a 1–60 minute dialog with its own Start/Cancel.
4. Optional collapsed Add outcome or next step. Existing reflection summary stays visible; Edit opens its editor.
5. Task completion if eligible; Repeat, Start another, History and pending-destination action as secondary content.

TARGET_REACHED: Target reached. Timed USER_STOP before target: Stopped before target. Count-up USER_STOP: Session ended. INTERRUPTED: Session interrupted with existing uncertainty explanation. Never infer intended-work completion from timer state.

A running break shows its existing countdown/End instead of another break-start action. No automatic Focus/break. Skipping reflection, cancelling break customization, Done and navigation cannot change saved credit. In-flight reflection work follows the existing retained-operation contract; after leaving, no callback may revive a dismissed screen or publish private content.

## FD-R08 — Recent work

Show at most one card in idle setup after Start, before insights, only when Focus is initialized, no open session/recovery exists and grants allow its content. No new Today card.

Candidate: terminal session with positive saved credit; order ended_at descending then UUID string descending. FOCUS_ONLY reads exclude task-origin rows before LIMIT 1; TASKS_AND_FOCUS may include them. Missing, empty and error are distinct internal states. Empty hides card; errors offer compact Retry without disabling Start. Loading shows no stale title.

Card contains authorized intention/fallback, optional next step, and Repeat. Interrupted candidates are labeled as interrupted; no invented completion. Selection only calls the existing authoritative repeatSession(id) reload; editing setup and explicit Start are required. Historical credit/outcome is never copied.

If the draft already contains user input or a task link, Repeat requires Replace setup / Cancel. An untouched new draft may prefill directly. Revalidate task state in Repeat and again at Start. Deleted/completed task clears the copied link with explanation but preserves origin on the copied intention. No eligible accessible record simply hides the card; do not reveal a hidden record count.

Candidate updates on save/delete/edit/unlink/reflection and relevant grant/generation changes. Selection race or deleted source yields inline unavailable/retry, not a stale start. Clear query results on relock/reset/restore; requery, never restore a cached session object from SavedStateHandle.

## FD-R09 — Review context

Keep existing WeeklyFocusCard distribution, goals and Plan another session. Add a single Latest work in this period row using the same authorized candidate query restricted to sessions with a **positive credited-run overlap** with the displayed current report range. It is not simply the latest global session and not based solely on session start date.

Show authorized intention, optional next step and View session. That action opens existing protected terminal detail; Repeat remains available there. Extend FocusHistoryRoute with requestedSessionId: UUID? = null and an optional internal session query argument. When present, load by terminalSession(id) and render the targeted detail using shared history-detail content instead of scanning pages or assuming the selected row is on page one. A missing/deleted/unauthorized record shows the appropriate missing/unlock state. Clear targeted content on access/generation changes. Ordinary history paging is unchanged. Use existing viewing-zone and partial-week range logic. Label recorded timer time neutrally; no productivity, attention, optimal-hour or mood-causation claim.

If a mixed report needs Tasks unlock, suppress all context and totals in that report, not just the row. An empty range has no context item. Failure of this optional query has a compact Retry and does not hide already valid, authorized totals or timer controls. Do not introduce new grouping or time aggregates.

## Architecture and proposed interfaces

These signatures are target decisions for new source, not claims they exist. Extend current interfaces rather than defining duplicates.

| Owner | Interface |
| --- | --- |
| TodoRepository (existing file) | suspend fun searchOpen(query: String, offset: Int, limit: Int = 50): TodoPage |
| FocusProductRepository (currently FocusGoals.kt) | fun observeTaskFocusMillis(taskId: UUID): Flow<Long> |
| FocusProductRepository | suspend fun unlinkTask(sessionId: UUID, expectedRevision: Long, context: FocusProductContext): FocusProductResult |
| FocusRepository | fun observeRecentTerminal(range: TimeRange?, includeTaskOrigin: Boolean): Flow<FocusSession?> |
| FocusViewModel | fun focusOnTask(taskId: UUID); fun clearTaskSelection(); existing repeatSession(sessionId: UUID) retained |
| New FocusTaskCompletionState.kt | data class PendingFocusTaskCompletion(val taskId: UUID, val expectedRevision: UUID, val nextRevision: UUID, val generation: UUID) |
| New FocusRecentWorkState.kt | sealed state: Hidden, Loading, Empty, Ready(session: FocusSession), Error; private in-memory content only |
| New FocusInvitations.kt | data class FocusInvitationConfig(val enabled: Boolean, val revision: Long, val weekdays: Set<DayOfWeek>, val time: LocalTime) |
| New FocusInvitations.kt | data class FocusInvitationSlot(val generation: UUID, val scheduleRevision: Long, val date: LocalDate, val dueAt: Instant) |
| New FocusInvitationRepository | fun observeConfig(): Flow<FocusInvitationConfig>; suspend fun saveConfig(config: FocusInvitationConfig, expectedRevision: Long, context: FocusProductContext): FocusProductResult |
| New FocusInvitationRepository | suspend consume(slot: FocusInvitationSlot, now: Instant): Boolean; suspend snooze(slot: FocusInvitationSlot, now: Instant, token: String): Boolean; suspend disable(slot: FocusInvitationSlot, token: String): Boolean |
| FocusHistoryRoute | Add requestedSessionId: UUID? = null to the existing parameter list; internal navigation passes only the ID |
| New FocusInvitationRules.kt | fun nextFocusInvitation(now: Instant, zone: ZoneId, weekdays: Set<DayOfWeek>, time: LocalTime): Instant? |

Config and delivery methods validate generation/revision within database transactions. consume is called only after current platform eligibility checks and atomically rechecks database open-session/break state; no eligibility check in Compose is sufficient. A claimed slot's token is created within its transaction and loaded only by the trusted notification path. Snooze/disable revalidate the token and current revision. The settings flow owns config mutation; notification actions own only their defined operations.

Use existing LumaStorage composition: add focusInvitations backed by the same database. A separate repository object is fine; generation, transaction and receipt authority must remain database-owned. Product command results retain current Applied/AlreadyApplied/Conflict/Invalid/Unavailable shapes; extend reason codes for task and invitation conflicts explicitly.

## Acceptance and traceability

| Acceptance | Requirements | Source owner | Later QA / emulator |
| --- | --- | --- | --- |
| FD-A01 safe task data/portability | R01–04 | FD01 | Q02–03, E02/E09 |
| FD-A02 task entry/completion/unlink | R02–03 | FD02–03 | Q02, E02/E03 |
| FD-A03 cross-section privacy | R02, R04, R08–09 | FD04/FD11 | Q02–03, E08/E09 |
| FD-A04 invitations | R04–05 | FD05–06 | Q02–04, E06/E07 |
| FD-A05 setup and truthful copy | R01, R06 | FD07 | Q02/Q05, E01/E10 |
| FD-A06 calm completion | R01, R07 | FD08 | Q02/Q05, E03/E04 |
| FD-A07 recent work | R08 | FD09 | Q02–03, E05/E08 |
| FD-A08 actionable Review | R09 | FD10 | Q02–03, E05/E11 |
| FD-A09 integrated preservation | all | FD11–12 | Q01–06, E01–12 |

Every required acceptance needs final-candidate evidence from the separate validation documents. Source review alone never closes this table. Technical usability/accessibility journeys are mandatory; recruiting people for the review's formative study is a separate product activity and not fabricated by an emulator run.

## Explicit exclusions and post-release decisions

Do not implement device-wide blocking, presets, a Focus widget, a Today recent-work card, automatic cycles, social features, ambient audio, AI scores, calendar scheduling, cross-device sync, or a new project hierarchy. Keep these in the task ledger's post-release section with their evidence triggers. No production telemetry is added.

Do not raise versions of dependencies/SDKs to solve unrelated work. Existing user changes in settings.gradle.kts and app/production/ remain outside this scope.
