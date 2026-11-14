# Task Importance and One-Level Subtasks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Subagent-driven development is an alternative only if the user selects it. This document does not start execution.

**Goal:** Add four importance choices and one-level real subtasks across Luma’s existing task surfaces, with one final emulator QA task.

**Architecture:** Extend the existing pure Kotlin Todo contracts and Room repository. Keep child content in `todo_task`, serialize family operations transactionally with a separate family revision, and use a device-local command journal for reliable multi-row recovery. Reuse Compose task editors/rows and existing navigation; every mutation entry point shares one family policy.

**Tech Stack:** Existing Kotlin, coroutines/Flow, Room, SavedStateHandle, Jetpack Compose/Material 3 and Luma UI system; no new external dependency assumed.

**Spec:** [Task importance and subtasks](../specs/2026-11-14-task-importance-subtasks-design.md)

## Global Constraints

- Four stable stored codes: NONE=0, LOW=1, MEDIUM=2, HIGH=3. Do not persist enum ordinal or localized strings.
- One root → direct children only. No moving/promoting existing tasks, projects, recurrence implementation or new Tasks text search.
- Finish every implementation handoff first, then execute the single TI-QA01. No emulator/device actions in I01–I08.
- Implementation follows the repository source-first boundary: no test authoring, Gradle/sync/build/lint/schema generation; QA owns these and defect fixes. Static source/diff checks are allowed.
- Use current warm-paper/obsidian roles, bundled typography/shapes and LumaUi spacing/radii. Maintain System/Light/Dark, English/French/Persian, Persian RTL, at least 48 dp targets and existing drafts/recovery/insets.
- Preserve unrelated dirty files and personal installations. Inspect current Room/archive versions before allocating one coordinated increment each. No destructive migrations.
- core:domain remains free of Android/Room dependencies. Keep existing SDK/toolchain floors; this feature does not authorize upgrades.

## Review Focus

Each condition below is assigned to an implementation owner and an assertion in the final QA task:

1. A child is created between confirmation and parent completion: reject stale preview without partial writes (I02; QA case 07).
2. Undo after another child was reopened/added: fail atomically rather than recreate an invalid completed parent (I02/I06; QA case 07).
3. Process dies after commit but before UI receives success: reconcile command UUID without duplicate timestamps (I02/I06; QA case 08).
4. Child row arrives before parent in an archive: stage/import successfully if valid; reject missing/deep/cyclic parents before live replacement (I08; QA case 10).
5. High child under a None/undated parent: child remains discoverable in its own matching/date view with accurate counts and context (I04/I07; QA cases 05/06).

## Execution boundary and task board

Status for every source task: **NOT STARTED**. QA: **NOT RUN**. Source dependencies below do not require predecessor test results. This schedule supersedes the research backlog’s suggestion of a release between importance and subtasks: complete the combined implementation, then one QA task. No schema-changing intermediate release.

| Task | Scope | Depends on source handoff | Research IDs |
| --- | --- | --- | --- |
| I01 | Domain models and family interfaces | None | TI-01/TI-02/TI-06 |
| I02 | Room storage, query and family transaction engine | I01 | TI-02/TI-06/TI-09 |
| I03 | Editor importance and shared presentation | I01/I02 | TI-03 |
| I04 | Task-list filter/sort and bounded family modes | I02/I03 | TI-04/TI-08 |
| I05 | Child creation/edit/navigation/order | I02/I03/I04 | TI-07/TI-08 |
| I06 | Unified completion, confirmation, Undo and delete UI | I02/I05 | TI-09 |
| I07 | Today/Calendar/widget/reminder/Focus integration | I04/I05/I06 | TI-10 |
| I08 | Archives/restore/reset and final source handoff | I02–I07 | TI-05/TI-11 |
| **TI-QA01** | **All validation, including emulator tests and fixes** | **I01–I08 SOURCE COMPLETE** | TI-12 |

TI-13–TI-16 remain follow-ups outside this execution package. No second QA task or per-source-task device gate is introduced.

## File map and cross-task interfaces

All paths below are relative to the repository root. New names are planned additions, not claims that files already exist. Confirm actual callsites at execution time and record renamed files in handoff.

**Domain:** modify `core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/{TodoModels,TodoRepository,TodoRules}.kt`; create `TodoFamilyModels.kt` for family previews/receipts, `TodoListQuery.kt` for matching/ordering/context. Keep content normalization separate from hierarchy transactions.

**Data:** modify `core/data/src/main/kotlin/com/luma/lifeos/data/todo/{TodoEntity,TodoMapping,TodoDao}.kt`, `data/repository/RoomTodoRepository.kt` and `data/db/LumaDatabase.kt`; create `data/todo/{TodoCommandEntity,TodoCommandDao,RoomTodoFamilyCommands}.kt`. Query and command responsibilities stay separate; repository delegates using the same database transaction scope. Modify backup/widget/calendar/Focus repository consumers in their owning tasks.

**App:** existing `app/src/main/java/com/luma/lifeos/ui/todo/` models, routes, screens, ViewModels and row/feedback; create `TodoFamilyActionState.kt`, `TodoFamilyActions.kt` and `TodoChildList.kt` to avoid further expanding existing large screens. Repeated importance display/selection belongs in `ui/common/LumaTodoImportance.kt`, consuming existing shared Luma wrappers. Modify navigation, Today, Calendar and Focus callers. Resource files: `app/src/main/res/{values,values-fr,values-fa}/strings.xml`.

### Contracts produced by I01

Types in `com.luma.lifeos.domain.todo`:

- `TodoImportance` with stable code mapping and rejecting decoder; `TodoSort { DATE, IMPORTANCE }`.
- `TodoListQuery(completed: Boolean, importance: TodoImportance? = null, sort: TodoSort = DATE)`. Null filter means All; enum NONE means explicitly unclassified.
- Extend `TodoTask` with `importance: TodoImportance = NONE`, `parentId: UUID? = null`, `siblingPosition: Long? = null`, `familyRevision: UUID`. Existing fixture constructors must explicitly receive deterministic family revisions during QA migration.
- Extend `TodoInput` with `importance: TodoImportance = NONE`; relationship/order are not editable content fields.
- `TodoParentContext(id: UUID, title: String)`; `TodoChildProgress(completed: Int, total: Int)`; `TodoTaskRow(task: TodoTask, parent: TodoParentContext?, progress: TodoChildProgress?)`; `TodoRowPage(items: List<TodoTaskRow>, total: Int)`.
- `TodoCompletionPreview(planId: UUID, targetId: UUID, completed: Boolean, changedTaskCount: Int, openChildCount: Int, reopensParent: Boolean, confirmationRequired: Boolean)`; exact private revision snapshots remain in journal storage, not UI bundles.
- `TodoCompletionReceipt(commandId: UUID, targetId: UUID, completed: Boolean, changedTaskCount: Int, undoAvailable: Boolean)`; `TodoDeletePreview(planId: UUID, targetId: UUID, childCount: Int)`; `TodoCommandOutcome { APPLIED, STALE, MISSING }` for reconciliation; typed conflict/missing/confirmation-required errors extend existing Todo error handling.

Add to `TodoRepository` (all suspend except observed Flows):

- `observeRows(query: TodoListQuery, limit: Int = 50): Flow<TodoRowPage>` — roots for All, individual matches for explicit importance.
- `observeChildren(parentId: UUID, limit: Int = 50): Flow<TodoRowPage>` — all statuses ordered explicitly, total is all children.
- `observeTodayRows(date: LocalDate, limit: Int = 50): Flow<TodoRowPage>` — all individually eligible dated tasks with parent context.
- `searchOpenRows(query: String, offset: Int, limit: Int = 50): TodoRowPage` — preserves literal-title search behavior, adds batched context.
- `createChild(id: UUID, initialRevision: UUID, parentId: UUID, input: TodoInput): TodoTask`.
- `moveChild(id: UUID, expectedRevision: UUID, nextRevision: UUID, direction: TodoMoveDirection): Unit`, where direction is UP/DOWN within the same parent only; validate adjacent sibling from a transactional snapshot and rotate family guard.
- `prepareCompletion(id: UUID, completed: Boolean): TodoCompletionPreview`.
- `applyCompletion(planId: UUID, commandId: UUID): TodoCompletionReceipt`.
- `undoCompletion(commandId: UUID, undoCommandId: UUID): TodoCompletionReceipt`.
- `completionReceipt(commandId: UUID): TodoCompletionReceipt?` for post-crash reconciliation, returning null if absent/expired; callers must not recreate an absent mutation automatically.
- `prepareDelete(id: UUID): TodoDeletePreview`; `applyDelete(planId: UUID, commandId: UUID): Unit`.
- `releaseCommandHandle(id: UUID): Unit` to unpin obsolete preview/command/Undo handles after the owning UI or widget no longer offers them.

Keep existing get/create/update/searchOpen/list/Today APIs for compatibility while migrating consumers. Legacy `setCompleted`/`delete` may perform only safe single-row changes; a family-affecting change throws confirmation-required. They must delegate to the same transaction policy and cannot provide an escape hatch. Do not remove old APIs until all consumers and fixtures are accounted for. New query/context APIs do not change existing Focus domain types unless its current atomic completion path needs the preview/command handle contract.

## I01 — Domain contract and invariants

**Files:** domain Todo files and new types above. **Consumes:** existing normalization, UUID revisions, civil dates. **Produces:** all cross-task contracts above. **Requirements:** TI-R01/02/06/07.

- [ ] Inspect active source, AGENTS.md and git status; record baseline SHA and unrelated edits. Establish execution isolation per the worktree skill if needed; do not commit unrelated files.
- [ ] Add importance codes/decoder and model/input extensions. Keep existing title/note/year/time validation; validate parent/position shape locally without pretending model construction can prove parent existence.
- [ ] Define row/query/progress types and explicit DATE/IMPORTANCE comparator contract matching TI-R05; null filter differs from NONE.
- [ ] Define family preview/receipt/error types and repository signatures; document root family-token ownership, command retry identity, confirmation-required legacy fallback and mutation no-op semantics.
- [ ] Review source against TI-R01/02/06/07 and update all production constructor/copy callsites mechanically. Reserve test-fixture updates for TI-QA01 under source-first workflow.
- [ ] Record I01 SOURCE COMPLETE — QA PENDING with touched paths, interface decisions and required case assertions; no runtime claims.

## I02 — Room schema, queries and family commands

**Files:** task data files/new command files, RoomTodoRepository, LumaDatabase. **Consumes:** I01 contracts. **Produces:** concrete repository support and shared transactional command engine. **Requirements:** TI-R02/04/05/06/07/08/10.

- [ ] Allocate one next unused Room version, document allocation, add importance/parent/order/family token and local journal table(s); extend reset/table inventory. Write migration source preserving old task IDs/timestamps/revisions/links and generating family guards. Schema JSON generation remains pending TI-QA01.
- [ ] Add self-reference/indexes, unique sibling position index and root/child shape checks. Foreign-key cascade alone does not implement Focus unlink semantics; family deletion must enumerate members first.
- [ ] Implement `observeRows`, `observeChildren`, `observeTodayRows` and `searchOpenRows` with SQL-side membership/order/count and batched parent/progress joins; no N+1 queries. Count from the same snapshot as rows. Preserve existing legacy projections.
- [ ] Implement `createChild` with root/open validation, normalized independent fields, append ordering and atomic family-token rotation. Implement `moveChild`; use a collision-safe transactional temporary position when swapping indexed ranks and rotate both changed task revisions plus family guard. No move at edge is a no-op.
- [ ] Implement preview storage using immutable snapshots; include every member revision and family membership guard. Previews are not transferable across restore/process restart. Implement `applyCompletion` with revalidation, monotonic timestamps, command receipt insertion and per-row reminder revision invalidation in one transaction.
- [ ] Implement `undoCompletion` against recorded result guard/revisions, restoring exact prior completed-at fields with fresh revisions. No-op commands have no Undo. Detect retry command UUID/payload mismatch and post-commit intervening family changes; never replay a stale preview.
- [ ] Implement family delete preview/commit with prior member enumeration and existing Focus session/runtime unlink rules for every member, plus command/reminder/widget invalidation. Make legacy mutations reject confirmation-requiring cases.
- [ ] Define journal payload version, pin ownership and pruning. Pin/unpin on UI/widget result lifecycle; seven-day pruning removes only unreferenced terminal records. Process recreation reconciles committed UUID handles; process death invalidates uncommitted preview handles. Stale/missing handles never mutate.
- [ ] Static-review transaction races from Review Focus, nested Room transaction use, cancellation boundaries and query null semantics. Record I02 source handoff and actual allocated versions; list QA cases 01/03/05/07/08/10/11.

## I03 — Importance editor and reusable presentation

**Files:** `ui/todo/{TodoUiState,TodoEditorViewModel,TodoEditorRoute,TodoEditorScreen,TodoRow}.kt`, new shared importance component, three locale catalogs; relevant shared theme/scaffold files are read before edits.

**Consumes:** TodoImportance, extended TodoInput. **Produces:** `setImportance(value: TodoImportance): Unit` on editor VM; optional `rowContext: TodoTaskRow?` on existing row; shared labeled selector/badge. **Requirements:** TI-R01/03/11.

- [ ] Inspect all shared UI definitions and redesign ledgers named in the spec, plus current editor saved-input/pending serializers.
- [ ] Add importance to draft/baseline/pending serialization and dirty comparison; old saved drafts default to None. Add VM setter without bypassing current busy/conflict/missing guards.
- [ ] Add localized importance section with four single-choice actions, and render accessible text/icon metadata in shared task row presentation using Luma wrappers/tokens. Keep primary Save reachable with IME/large text.
- [ ] Preserve legacy row test tags and date/status semantics; use additional tags `todo_importance_none`, `todo_importance_low`, `todo_importance_medium`, `todo_importance_high`. Do not combine row open and importance selection into an ambiguous tap target.
- [ ] Review catalog format/plural parity and retained-draft failure branches. Record I03 source handoff; QA cases 01/02/09 prove behavior later.

## I04 — List matching, ordering and bounded family state

**Files:** `ui/todo/{TodoListViewModel,TodoListScreen,TodoListRoute}.kt`, new `TodoChildList.kt`; locale catalogs.

**Consumes:** row/query APIs and importance presentation. **Produces:** list query state and independent child-prefix/expansion state; `setImportanceFilter(value: TodoImportance?): Unit`, `setSort(value: TodoSort): Unit`, `setExpanded(id: UUID, expanded: Boolean): Unit`, `loadMoreChildren(parentId: UUID): Unit` on the list VM. **Requirements:** TI-R04/05/11.

- [ ] Migrate root list observation to `observeRows`; retain bounded 50-row root prefix and loading/refresh/off-page retry/result feedback behavior.
- [ ] Add All/High/Medium/Low/None and Date/Importance controls with saved query/scroll state. All uses roots with expansion; explicit filters use flat matches with parent context and no duplicates. Header labels distinguish roots from matching task records.
- [ ] Implement independent 50-row child-prefix flows keyed by expanded root, cancelled when collapsed; compute progress from full query aggregate. All child statuses remain visible in sibling order. Retain stable IDs and accessibility expansion semantics.
- [ ] Render Date default buckets or the explicit importance ordering, matching completed-mode contract. Avoid regrouping rows after SQL limit; keep deterministic fallback/ties and focus recovery after rows leave the result set.
- [ ] Record I04 source handoff and assertions for child-only matches, NONE/null distinction, page boundaries, collapse/recreation and completed sort in QA cases 05/09/11.

## I05 — Child editor, parent links and ordering

**Files:** task editor/route/VM and child list; `ui/navigation/AppDestinations.kt`, `ui/LifeOsApp.kt`; locale catalogs.

**Consumes:** createChild/moveChild/observeChildren and existing dirty-save navigation. **Produces:** optional `parentId` creation context on existing task editor route/VM, parent navigation callbacks and saved continuation intent. **Requirements:** TI-R02/03/04/11.

- [ ] Extend navigation’s task editor destination with optional parentId for creation while retaining old task-ID/date URI compatibility. Never allow navigation arguments to overwrite an existing task’s parent.
- [ ] Implement Save and continue / Cancel for new or dirty parents, preserving continuation intent through recreation and clearing it on failed/invalid save until successful retry. Successful create transitions to child creation under returned root ID, not a duplicate root.
- [ ] Add saved parent child-list section/progress/Add subtask and child parent breadcrumb/Open parent; guard child dirty exit. New children use no date and None importance regardless of parent.
- [ ] Add Move up/down actions wired to `moveChild`, disabled at true full-list edges and protected from stale visible-neighbor assumptions. Child delete uses I06 pipeline.
- [ ] Handle completed/missing/deleted parent and restore replacement with explicit recovery while retaining drafts where possible; never attach to another task. Record I05 handoff; QA cases 03/04/08/09.

## I06 — Shared family action state and recovery

**Files:** new `ui/todo/{TodoFamilyActionState,TodoFamilyActions}.kt`; existing editor/list/Today action VMs, `TodoUiState.kt`, `TodoActionFeedback.kt`, task routes/screens; locale catalogs.

**Consumes:** prepare/apply/undo/delete/receipt APIs. **Produces:** shared action coordinator with preview/confirmation/result states and UUID-only retained handles. Extend `TodoActionResult` with optional `completionCommandId: UUID?` so existing return callbacks can carry group Undo without member lists. **Requirements:** TI-R06/07/08/11.

- [ ] Define `Idle/Previewing/AwaitingConfirmation/Applying/Applied/RetryableFailure/Conflict/Missing` states in the shared action model. Save only target/plan/command handles and lightweight UI state, not full family members.
- [ ] Replace direct app status/delete writes with prepare→confirmation-if-required→apply. Child reopen preview explicitly says parent will reopen. Preserve dirty-field status guards and existing app-lock delete authorization.
- [ ] Add shared localized completion/delete dialogs with affected counts and explicit Cancel; revalidate at commit. Conflict drops confirmation, retains field drafts and offers refresh/re-preview; do not silently expand the affected set.
- [ ] Route completion feedback/Undo through command receipt; stale group Undo rejects atomically. Keep older single-row result handling safe during migration and invalidate all old pending handles on restore.
- [ ] Reconcile already-committed commands after process recreation and failed responses; cancelled/expired/missing handles yield recoverable stale state. Release journal pins when retry/Undo/navigation feedback is superseded; keep pending references pinned.
- [ ] Review action accessibility focus and off-page retry visibility. Record I06 source handoff and QA case 07/08 assertions for confirmation cancel, atomic failure, double taps and crash-after-commit.

## I07 — Surface integration without policy bypasses

**Files:** `ui/todo/{TodayTasksState,TodayTasksViewModel,TodayTasksCard}.kt`; `ui/calendar/{CalendarAgenda,CalendarScreen}.kt`; `core/data/src/main/kotlin/com/luma/lifeos/data/{calendar/CalendarDao,repository/RoomCalendarRepository,widget/WidgetCommandExecutor,widget/WidgetCommandPayload,widget/RoomWidgetStore,focus/RoomFocusProductRepository}.kt`; app `ui/focus/FocusTaskPicker.kt`, `FocusTaskCompletionAction.kt` and owning Focus VM located from usages; `ui/LifeOsApp.kt`; task reminder/launcher rendering consumers and locale catalogs.

**Consumes:** shared actions, row context, observeTodayRows/searchOpenRows. **Produces:** all current task consumers honor importance/hierarchy and common command policy. **Requirements:** TI-R05/09/11.

- [ ] Replace Today’s `Int.MAX_VALUE` observation/eager Column with initial 50-row bounded observation, lazy scrolling and Load more while retaining all-item total/date eligibility/current-day-only contract. Preserve existing scroll height, recovery and bottom clearance; no three-row regression.
- [ ] Add batched parent/importance/progress context to Calendar data/rendering without changing date-window membership or marker semantics. Use shared completion actions and direct child-ID navigation.
- [ ] Extend Focus picker’s existing title search through `searchOpenRows`; show parent context and select exact task ID. Migrate Focus completion implementation, preserving atomic session/task/generation checks and established terminal-session rules; parent completion requiring confirmation stays pending until native dialog returns.
- [ ] Extend widget row snapshots/context using launcher contract. Route actions requiring multi-row changes/confirmation to native app after unlock. Old issued payloads are revalidated through the repository and cannot cascade. Safely retain simple leaf action/retry/Undo behavior with new receipt handles where needed.
- [ ] Reconcile reminders/widget refresh for every changed/deleted task after transaction completion; preserve scheduling failures as status/retry, not rolled-back saved content. Verify reset/restore callbacks invalidate every surface’s old handles.
- [ ] Record all discovered direct Todo DAO/status writes and their migration to the common policy. Record I07 source handoff; QA cases 06/07/08/09/10/11.

## I08 — Portable archives and complete source handoff

**Files:** `core/data/src/main/kotlin/com/luma/lifeos/data/backup/{BackupArchive,BackupValidation,BackupSql,RoomBackupService,FocusMetadataArchiveAdapter}.kt`; create `TodoHierarchyArchiveAdapter.kt`; backup preview model/UI found by `BackupPreview` usages; DB/reset inventories; task docs and locale catalogs.

**Consumes:** final I02 task columns, family policy and I07 restore invalidation. **Produces:** coordinated archive increment/adapters and implementation handoff ready for TI-QA01. **Requirements:** TI-R10/12.

- [ ] Allocate one next unused portable archive version independently of Room. Add strict old-header adapters for every supported older schema; default old tasks to roots/None, generate family tokens and preserve existing due-time compatibility.
- [ ] Export parent-before-child; import into staging with a root-first task pass then child pass irrespective of payload row order. Validate graph depth/status/sibling order/importance and existing foreign keys before replacement. Device-local command records are excluded.
- [ ] Clarify preview task count as including subtasks in all locales without double-counting; retain existing preview model total unless intentionally adding a separately wired child count.
- [ ] On successful replacement rotate content/family guards, clear command journal and pending UI/widget handles, refresh contexts and schedule reminders through existing callbacks. Cancelled/malformed replacement preserves live data and drafts; reset covers journal/schema additions.
- [ ] Reconcile spec requirement mapping, all production constructor/serializer/repository consumers and user-visible error/retry states. Record SHA, dirty patch identity, changed paths, allocated schema versions, known uncertainties and static checks in `docs/validation/2026-11-14-task-importance-subtasks-implementation.md` when execution actually occurs.
- [ ] Mark I01–I08 SOURCE COMPLETE — QA PENDING only when their source checklists are satisfied. Keep generated schema/test/runtime evidence pending; begin **TI-QA01** only after this feature-wide handoff.

## TI-QA01 — Single final QA task

Execute [the consolidated QA document](../../TASK_IMPORTANCE_SUBTASKS_QA.md) after every source task above. It owns test/fixture authoring, schema export/inspection, focused and required affected host checks, build/lint, dedicated emulator tests/manual observations, defects and retests. Scenario numbers in that document are not separate tasks. No emulator action belongs in I01–I08.

## Plan self-review and handoff

Coverage: TI-R01–TI-R12 map to I01–I08 and QA cases in the spec. Query/command names and types are defined once above and consumed by later steps. Review Focus conditions are included in QA. Existing research remains linked but this plan’s scope/scheduling takes precedence over its obsolete intermediate-release suggestion. Current Today behavior was reconciled against source.

Documentation creation executes no app/source changes, Gradle or emulator tests. Review this spec/plan before a future implementation request. Use the native execution method unless the user chooses delegation; this documentation pass does not select or begin implementation.
