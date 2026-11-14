# Task importance and one-level subtasks specification

Date: 14 November 2026. Status: **TI-02 importance persistence, TI-05 importance backup compatibility, and TI-06/I02 hierarchy storage/query/transaction source handoffs COMPLETE — QA PENDING; I04–I08 remain in progress; TI-QA01 NOT RUN. TI-06 allocated Room schema 17; KSP generated [schema 17](../../../core/data/schemas/com.luma.lifeos.data.db.LumaDatabase/17.json) during the requested check, but schema/migration verification remains TI-QA01 evidence. The TI-06 `./gradlew check` failed on the same three existing `:core:domain:test` cases recorded in the backlog and plan.**

User decision: **Four importance choices + one-level task hierarchy**. User also requested specifications/plans and **one emulator QA task after all implementation is done**. Detailed behavior below is the implementation recommendation derived from that choice, not an assertion that the user separately chose every edge case. This documentation request does not start implementation.

[Research/backlog](../../TASKS_IMPROVEMENT_BACKLOG_2026-11-14.md) · [Implementation plan](../plans/2026-11-14-task-importance-subtasks.md) · [Single QA task](../../TASK_IMPORTANCE_SUBTASKS_QA.md)

## Intent, scope and alternatives

Let a person express what matters and break a large task into actionable work without losing Luma’s quick capture, offline operation or Calendar/Focus connections.

Chosen approach: optional None/Low/Medium/High importance and real child task records under a root. A star plus checklist would not provide graded importance or independent child dates/Focus. Arbitrary nesting/projects would require more navigation and recovery complexity than this request needs. Keep the existing Kotlin/domain → Room/data → Compose/ViewModel architecture.

Included: importance selection/filter/sort; independent child fields; progress; bounded task-family browsing; safe completion/reopen/Undo/delete; archive/migration support; existing Today/Calendar/widget/reminder/Focus/task-search integrations. Excluded: new task-list text search, projects, deep nesting, moving/promoting existing tasks, tags, recurrence implementation, family Focus-time aggregation, remote analytics and AI decomposition. Existing TI-13–TI-16 remain follow-up proposals.

Anti-goals: no lost drafts, silent mass completion, invisible matching children, orphaned tasks, duplicated time totals, destructive migration, competing visual palette or new mandatory capture fields.

## Current baseline and authority

Current `TodoTask`/`TodoInput`/`TodoEntity` have no importance, parent or child ordering. `TodoRepository` supports revision-checked single-row CRUD, list/Today observation and literal open-title search. Room database version is 15 and portable archive version is 12 at research time; implementation must allocate the next unused versions after inspecting its actual baseline.

**Current Today source differs from older task documents:** `observeTodayTasks` requests all eligible rows and `TodayTasksCard` renders a height-bounded scroll area. Preserve access to all due/overdue work and actual-current-date visibility. Do not reinstate a three-row cap. This change will introduce bounded lazy paging so large task families do not amplify existing eager loading.

Use current code and latest applicable contracts over historical feature handoffs. Preserve unrelated dirty files and personal installations. The existing [source-first workflow](../../feature-roadmap/WORKFLOW.md) supplies the phase responsibilities; this feature overrides its per-task scheduling: finish **every implementation handoff first**, then execute the **single TI-QA01**. Implementation means production/config/resource changes and static source review only: no test authoring, Gradle/sync/build/lint/schema generation or emulator/device actions. QA owns all of those activities and defect fixes. Implementation dependency edges require source handoffs, never earlier QA PASS.

## Requirements

### TI-R01 — Importance

Four stable stored codes: NONE=0, LOW=1, MEDIUM=2, HIGH=3. Do not persist enum ordinal or localized strings. New tasks and legacy imported tasks default to None. None means unclassified and is distinct from Low. Each child has its own value and starts at None; parent changes never propagate. Overdue status never changes importance, due date or notification-channel importance.

Expose an optional editor section labeled Importance with the four localized single-choice options. Clear sets None. Save, draft restoration, pending writes, dirty detection, conflicts and reload all include importance. Existing title-only capture remains valid with current title/note/Unicode/date validation.

### TI-R02 — Relationship and ordering

One root → direct children only. A child has a stable task UUID, its own title/note/importance/date/time/completion and an immutable `parentId`. Creation under a missing, completed or child parent is rejected. Parent existence, root depth and completion are checked inside the write transaction; UI checks alone are insufficient. Parent and child due dates may be unrelated, including a child after its parent. Explain differing dates with parent context, without forcing inheritance or cascades.

Persist explicit sibling order across all children including completed ones. Initially append after the last sibling. Support accessible Move up/Move down actions in the parent’s child list. Reordering changes sibling order only; it must not change dates, status or task IDs. No drag gesture is required. Moving between parents and promoting roots are deferred.

### TI-R03 — Editor, navigation and drafts

New root: existing editor and title-only Save. Add subtask on an unsaved parent offers Save and continue / Cancel; validation or failed save keeps the parent draft. For a dirty saved parent, offer Save and continue / Cancel before child navigation. Do not discard or save silently.

A saved parent shows child progress, Add subtask, an independently paged child list and child status actions. A child editor shows parent title plus an Open parent action; it does not offer Add subtask. New children start undated and with None importance. Reuse the existing task editor via an optional parent creation context; do not create a second editor. Preserve old task-ID deep links and return origins; child ID links open the child itself.

Store parent context in retained create drafts and pending writes. On restore replacement or parent deletion, invalidate pending family navigation/commands; display missing/conflict recovery rather than attaching the draft elsewhere. Navigation to parent must respect the child’s dirty-exit guard. All delete paths retain app-lock checks.

### TI-R04 — List modes, matching and progress

No importance filter: Open/Completed shows roots matching the selected status, grouped by the existing date buckets. Count explicitly refers to parent/standalone tasks. Expanding a root or opening its editor shows all child statuses in explicit sibling order with a completed marker; do not hide unfinished-child context behind a status-tab interpretation. Children have separate 50-row prefix observation and Load more. Expanded IDs, child prefixes, tab, root prefix, filter, sort and per-mode scroll survive recreation/navigation. Missing/deleted expanded IDs are safely dropped.

Selecting any importance filter (including None) switches to a flat matching-task view. Each matching root/child appears once. Children carry a parent breadcrumb, not an additional counted root. Header explicitly says matching tasks including subtasks. Existing `searchOpen` and Focus picker searches also match children independently. Do not add new Tasks text search in this release.

Progress is completed child count / all child count across the full family, not visible rows or current importance filter. Omit it for zero children. Last-child completion offers an explicit Complete parent action and never auto-completes it. A dated root and child can both be independent actionable matches in Today/Calendar/widgets, each counted once; parent context labels never increment totals.

### TI-R05 — Ordering and bounded queries

Default Date sort retains current date buckets and due time/created-at/ID tie-breaks. Completed default retains current completed-at ordering. Opt-in Importance sort is a flat group headed Highest importance first: High → Medium → Low → None, then due date ascending with undated last, timed before date-only for equal dates, due time ascending, created-at ascending, UUID ascending. In unfiltered root mode it sorts roots only; explicit child order stays unchanged. In importance-filter mode it sorts matching individual tasks. Changing sort does not alter membership or status.

Apply status/filter/sort in SQL before paging/counting. Root/child/flat/task-context results must come from consistent transactional snapshots. Root and child prefixes begin at 50 with Load more increments of 50. For Today, replace unbounded eager observation with an initial 50 eligible rows and Load more within its existing scroll area; keep the full total and access to all eligible tasks. Date-first order and current-date visibility stay unchanged. Use lazy composition; no per-row lookup queries. Calendar’s existing date-window semantics and launcher collection limits remain intact.

### TI-R06 — Completion and reopen policy

Invariant: a completed root has no open child. Completing a leaf or a root whose children are already complete changes that task only. Completing a root with open children requires a dialog stating the number of remaining children; Cancel changes nothing, Complete parent and N subtasks changes root and those open children atomically. Previously completed children keep their timestamps and revisions.

Completing the last child does not complete the parent. Reopening a root leaves children unchanged. Reopening a child beneath a completed parent atomically reopens both, with explicit feedback before the action and in its result; opening the confirmation can be cancelled. All mutations keep existing monotonic updated-at behavior and same-state no-op semantics.

A confirmation is based on a revision snapshot of the whole family. If any member or membership changes before submit, do not act on a different set; reject with conflict and ask for a new preview/confirmation. No silent retry against a freshly expanded family. Operations on separate roots can proceed independently.

### TI-R07 — Revision, retry and Undo contract

Use existing per-task UUID revisions for content/status. Add an independent root family revision token updated by every actual member content/status/order/membership mutation. A child change must not make an unrelated unsaved parent content edit fail solely because progress changed; parent content revision and family revision have separate purposes. Every edit reloads the latest family guard inside the transaction; only family previews require an exact earlier family guard.

Completion preview includes root ID, family token, target/status and the exact affected IDs, expected revisions and prior completion timestamps. Commit checks family token and affected rows in one Room transaction, applies changes, updates root family token and writes a device-local idempotency receipt using a caller-retained command UUID. Retry of the same command with identical payload returns the recorded receipt if no intervening family change occurred; mismatched payload or changed family returns conflict without mutation. Record commands and receipts atomically with task writes, so a crash after commit cannot repeat completion with a new timestamp.

Undo takes the original command UUID and a new retained Undo command UUID. It restores only rows changed by that completion command, using previous completion timestamps and new revisions. Require the recorded resulting family token and all affected resulting revisions. Any intervening family write, including addition of a child, invalidates the whole Undo; no partial rollback or timestamp invention. This stricter family rule prevents Undo from recreating a completed root with an open child.

Keep pending commands/receipts recoverable across process recreation without large navigation bundles: store immutable command payloads/receipts in Room; SavedState holds only UUID handles and small UI state. Payloads use IDs/revisions/status/timestamps, not copied task titles. Uncommitted previews become stale after process death and require re-preview/confirmation; committed result handles can reconcile without replay. Cancelled/expired handles show stale feedback. Clear this device-local journal on restore/reset, exclude it from portable archives, and prune terminal/unreferenced records after seven days; never prune a record referenced by an active pending retry/Undo. A missing handle fails safely without replaying mutations.

### TI-R08 — Delete and Focus history

Delete child: confirm existing destructive action and remove only it. Delete root: preview entire family and state total child count (including completed children) in the existing confirmation, respecting app lock. Commit validates family token and deletes all family records atomically. A membership change requires re-confirmation. Missing target is idempotent only for the retained matching delete command; do not accidentally target another record.

Preserve existing Focus deletion behavior for **each** deleted task: unlink session task IDs, advance session/runtime revisions as current code requires, retain Focus history and duration. Cancel/invalidate each affected reminder and widget command. No new delete Undo. Root title/context disappears cleanly from child navigation once deletion finishes.

### TI-R09 — All task surfaces

Today and Calendar independently match a child’s own due date/time; never copy the parent date. Undated High tasks stay outside date-only eligibility. Parent context, importance and progress reuse task presentation wherever space permits, with accessible labels and direct task-ID navigation. No extra task surfaces appear on historical Today dates.

Widgets keep their separate launcher design contract, date order, bounded collection/counts and privacy/app-lock behavior. Any action that needs a family confirmation routes to the app with task ID only; native app re-reads and previews after unlock. Old single-row widget/reminder action payloads must never bypass the new policy. Legacy repository single-row completion/delete entry points reject multi-row changes with a typed confirmation-required outcome, rather than silently cascading. Safe leaf actions may remain direct.

Focus picker can choose children with parent context and reuse literal open-title search. Session time belongs only to its selected task ID; no automatic parent aggregate. Focus completion revalidates session revision, task revision and restore generation, then uses the same family policy. Completion and any Focus state/receipt write that must be atomic must share the existing Room transaction. Do not mark a task complete after its Focus link/session changed.

Reminders retain current approximate scheduling, due-time rules and quiet-hour handling. Reconcile/cancel for every changed/deleted task after commit; failure to schedule does not roll back saved task data and is shown through existing reminder status/retry behavior. Recurrence is not introduced; if F08 exists at execution time, reject unsupported recurring families visibly without discarding either feature’s content.

### TI-R10 — Persistence, migration and portable archives

Extend task storage with importance, nullable self-referencing parent ID, nullable sibling position and family revision token. Roots have null parent/position; children have nonnegative position unique within parent. Enforce foreign keys and transactional root/depth/status rules. Add indexes for parent/status/order and status/importance/date queries based on actual query plans. Migration maps all old tasks to roots with None, preserving IDs/content/dates/timestamps/revisions and Focus/reminder links; generate new family tokens. Do not destructively rebuild user data.

Allocate one coordinated Room increment and one archive increment for the combined feature; source steps share that schema change rather than shipping half-migrated releases. Portable archives include importance/relationships/order; command journal stays local. Retain every currently supported legacy archive version with its exact old header adapters; legacy tasks default to roots/None. Export task rows in parent-before-child order (and stage root rows before children when importing arbitrary payload order), since table-order alone cannot satisfy a self-reference.

Validate importance codes, UUIDs, date/time, parent existence/depth, sibling uniqueness and completed-root invariant in the staging database before live replacement. Reject malformed archives atomically; cancelled or failed preview never changes live data. Preview task total continues to count all task records and explicitly says it includes subtasks; optionally show a separate child count only if wired throughout the preview model/UI/locales. Rotate task and family revisions on successful restore, clear journal/widget stale commands/pending UI results and reschedule reminders through existing replacement callbacks. Reset covers all new tables and retained state.

### TI-R11 — Luma UI and accessibility

Before UI implementation, inspect `app/src/main/java/com/luma/lifeos/ui/theme/`, `LumaUiTokens.kt`, `LumaUiComponents.kt`, `AppPageScaffold.kt`, `docs/luma-redesign/README.md`, `route-coverage.md` and `ui-consistency-checklist.md`. Use current warm-paper/obsidian roles, bundled typography/shapes and LumaUi spacing/radii. Extend shared components for repeated importance/progress/confirmation patterns; do not duplicate feature wrappers or copy competitor colors. Preserve specialized Focus/Calendar visuals and separate launcher contract.

Maintain System/Light/Dark, English/French/Persian, Persian RTL, offline fonts, localized numbers/plurals, at least 48 dp targets, large-text wrapping, semantics/test tags, keyboard/IME/insets/scroll/FAB clearance and existing callbacks/retry/Undo/drafts. Use text plus icon/shape for importance and progress, not color alone. At narrow/large-text widths use breadcrumb wrapping rather than unlimited indentation. Announce expanded state, child count, completion implications and results; restore useful accessibility focus after removal/collapse/dialog dismissal. Existing dual-calendar settings affect labels/pickers only; persisted civil dates do not change.

### TI-R12 — Delivery, verification and acceptance

Execute I01–I08 source tasks in the implementation plan, recording source handoffs. No individual emulator QA tasks and no earlier-QA dependencies. Then execute exactly **TI-QA01**, which owns host test authoring/runs, schema export, build/lint, isolated emulator instrumentation/manual testing, fixes and reruns. The QA document’s numbered scenarios are coverage cases inside that one task, not separately scheduled QA tasks.

All cases initially NOT RUN. Use isolated test application IDs/data and dedicated disposable AVDs after verifying actual runner targets; never overwrite the user’s personal app/data. Record build/source/package/API/fixture/environment identity and evidence. A source handoff is SOURCE COMPLETE — QA PENDING, not verified/release-ready. Acceptance requires mapped required cases passing or explicitly documented user-approved exceptions; do not infer waivers from old handoffs. No merge/release of schema-changing source until generated schemas/migrations and affected acceptance checks are verified.

## Acceptance mapping

| Requirement | Source owner | Final QA coverage |
| --- | --- | --- |
| TI-R01 | I01/I03/I04 | Cases 01, 02, 09 |
| TI-R02 | I01/I02/I05 | Cases 03, 04, 07, 10 |
| TI-R03 | I03/I05 | Cases 02, 04, 08, 09 |
| TI-R04/TI-R05 | I02/I04/I05/I07 | Cases 05, 06, 09, 11 |
| TI-R06/TI-R07 | I01/I02/I06 | Cases 04, 07, 08, 10 |
| TI-R08 | I02/I06/I07 | Cases 07, 08, 10 |
| TI-R09 | I06/I07 | Cases 06, 07, 08, 09, 10 |
| TI-R10 | I02/I08 | Cases 01, 03, 08, 10 |
| TI-R11 | I03–I07 | Cases 02, 04, 05, 06, 07, 09 |
| TI-R12 | All source handoffs | Entire TI-QA01 |

## Builder choices and execution boundary

Storage helper names, SQL batching, shared component placement and journal serialization format can be chosen by the implementer without changing these contracts. Never store secrets in logs or use real user task titles in evidence. Performance targets are baseline-relative as defined in QA; emulator samples do not prove representative phone performance.

The written plan pins cross-task type names/interfaces so later implementers do not invent incompatible commands. The approved approach is recorded; implementation is a future request. Detailed defaults can be corrected during spec review without restarting the market research.

Builder check (14 November 2026): an independent Android engineer reviewed this specification for material unanswered planning questions and reported none. This is a document-readiness review, not compilation, runtime verification or user approval of every detailed default.
