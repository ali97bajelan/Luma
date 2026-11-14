# Tasks improvement research and proposed backlog

Prepared: 14 November 2026. Role: senior product manager.
Status: **Approach selected by user — four importance choices + one-level task hierarchy. I01 domain contract SOURCE COMPLETE — QA PENDING; TI-02 importance persistence SOURCE COMPLETE — QA PENDING; remaining I02 hierarchy/query/command work and I03–I08 NOT STARTED. Room schema 16 generated and archive schema 13 allocated. User-requested TI-02 `./gradlew check`: FAILED on 3 existing `:core:domain:test` cases (`MemoryTest` and two `ProtectedRecordClassifierTest` cases); `:core:data` KSP completed. [Generated Room schema](../core/data/schemas/com.luma.lifeos.data.db.LumaDatabase/16.json). TI-QA01 NOT RUN.**

Execution package: [specification](superpowers/specs/2026-11-14-task-importance-subtasks-design.md) · [implementation plan](superpowers/plans/2026-11-14-task-importance-subtasks.md) · [single final QA task TI-QA01](TASK_IMPORTANCE_SUBTASKS_QA.md). The selected scope and scheduling below supersede the original research proposal; these execution documents own detailed contracts and task status.

## Product recommendation

Help people answer two questions: “What matters most?” and “What is the next manageable action?” Add optional importance and one level of real subtasks in two implementation increments, followed by one consolidated QA task after all implementation is done. Preserve fast title-only capture and Luma’s offline personal-life workflow.

Recommended importance choices: **None, Low, Medium, High**. None means not classified; it is distinct from Low. Importance expresses personal value; due date/time expresses urgency. A high-importance task must not acquire a deadline automatically.

Recommended subtasks: independent task records under one parent, each with its own title, note, completion, optional due date/time and importance. Include progress and parent context. Avoid introducing projects, collaboration or deeper nesting in this release.

The user selected this approach on 14 November 2026 and requested specifications/plans with one final emulator QA task. The linked spec records detailed implementation defaults; this research backlog remains the rationale and follow-up inventory.

## Current feature audit

Findings below come from current source, not a fresh device walkthrough. “Present” means visible in source, not verified as shipped or passing.

| Capability | Current evidence | Product implication |
| --- | --- | --- |
| Create/edit tasks | `TodoTask`/`TodoInput` contain title, note, optional civil due date and minute-precision time; time requires a date | Preserve title-only capture and existing Unicode limits |
| Open/Completed lists | `TodoListScreen` groups open work as Overdue, Today, Upcoming and No date; list observation defaults to 50 rows | Add importance without removing date context or breaking pagination |
| Completion and recovery | Revision-checked writes, idempotent retries, conflicts, missing-item handling and completion Undo | Hierarchy operations require equally strong atomicity and recovery |
| Today | Current source requests all eligible tasks and renders a height-bounded scroll area; historical Today dates do not show mutable task state | Preserve all-item access/date eligibility; the new spec replaces eager observation with bounded lazy paging, not a three-row cap |
| Calendar | `CalendarAgenda` renders tasks and exposes completion callbacks; data queries read `todo_task` | Child dates need independent eligibility and direct navigation |
| Reminders | Due-time reminder planner/repository and editor reminder status are present | Child reminder scheduling must reuse existing rules |
| Widgets | Task provider and DAO provide due/overdue rows, counts and revision tokens | Hierarchy changes must reach widget commands, not only app screens |
| Focus | Task picker, editor Focus action, per-task focus totals and task-completion action are present | Extend child context rather than build a second task-to-Focus system |
| Search | `TodoRepository.searchOpen` and DAO title search already exist; Tasks list itself exposes Open/Completed, not a query control | Reuse existing search capability when adding task-list discovery |
| Importance | No importance field in `TodoTask`, `TodoInput` or `TodoEntity` | Requires domain, persistence, archive, draft and presentation work |
| Subtasks | No parent ID or sibling ordering in those task models | Requires a real relationship, transaction rules and query changes |
| Recurrence and task tags | Existing F08 recurrence and O09 task-tag proposals; no fields for these in the inspected task models/editor | Reconcile existing owners; do not count planned work as delivered |
| Backup baseline | Current database version is 15; portable backup schema is 12 | Recheck versions at implementation time; do not copy old schema-6/schema-5 assumptions |
| Acceptance debt | Older TODO and redesign records contain unresolved/pending checks | Reconcile against current evidence before release; old failures are not proof of present failure |

### Source anchors

- [Domain task model](../core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoModels.kt), [repository contract](../core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoRepository.kt), [Room entity](../core/data/src/main/kotlin/com/luma/lifeos/data/todo/TodoEntity.kt), [task queries](../core/data/src/main/kotlin/com/luma/lifeos/data/todo/TodoDao.kt).
- [Tasks list](../app/src/main/java/com/luma/lifeos/ui/todo/TodoListScreen.kt), [editor](../app/src/main/java/com/luma/lifeos/ui/todo/TodoEditorScreen.kt), [Today state](../app/src/main/java/com/luma/lifeos/ui/todo/TodayTasksState.kt), [Calendar agenda](../app/src/main/java/com/luma/lifeos/ui/calendar/CalendarAgenda.kt).
- [Focus task picker](../app/src/main/java/com/luma/lifeos/ui/focus/FocusTaskPicker.kt), [Focus completion](../app/src/main/java/com/luma/lifeos/ui/focus/FocusTaskCompletionAction.kt), [task reminders](../core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TaskReminderPlanner.kt).
- [Database](../core/data/src/main/kotlin/com/luma/lifeos/data/db/LumaDatabase.kt), [archive contract](../core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupArchive.kt), [existing task handoff](TODO_TASKS.md).

## Established-app research

Official documentation reviewed on 14 November 2026. This is documentation research, not hands-on comparative usability testing. The lessons in the last column are our product judgment.

| App | Documented pattern | Lesson for Luma |
| --- | --- | --- |
| Todoist | Four priority levels, priority sorting/filtering; subtasks with names/dates and collapse/expand | Importance needs discovery and ordering; subtasks need context and progressive disclosure. Sources: [priorities](https://www.todoist.com/help/todoist/features/set-a-priority-in-todoist-Wy82Jp), [subtasks](https://www.todoist.com/help/todoist/get-started/get-started-with-todoist-OgNNJR) |
| Microsoft To Do | Starred importance, Important smart list, importance sorting; steps with completed/total counter | A simple model lowers capture effort; progress is useful even without project machinery. Source: [steps and importance](https://support.microsoft.com/en-us/todo/add-steps-importance-notes-tags-and-categories-to-your-tasks) |
| TickTick | High/Medium/Low/None priorities; separate text/check-item mode; nested subtasks with individual attributes | Distinguish a checklist from an independent task; avoid offering both abstractions immediately. Sources: [task details](https://help.ticktick.com/articles/7055782408586526720), [subtask attributes](https://ticktick.com/public/changelog/en.html) |
| Google Tasks | Subtasks cannot repeat; tasks with subtasks cannot repeat | Recurrence and hierarchy need an explicit compatibility policy before launch together. Sources: [repeat restrictions](https://support.google.com/tasks/answer/12132599?hl=en), [task editing](https://support.google.com/tasks/answer/7675838?co=GENIE.Platform%3DDesktop&hl=en) |

### Options considered

| Approach | Benefit | Cost / limitation |
| --- | --- | --- |
| Star + checklist steps | Smallest interaction and persistence scope | No importance degree; steps cannot be scheduled or focused independently |
| Four importance choices + one-level task hierarchy — selected by user | Meets both requested capabilities and fits Calendar/Focus | Requires transactional parent/child rules and updates across surfaces |
| Multi-level hierarchy + projects + dependencies | Supports complex project management | Larger navigation, query and recovery burden; no evidence that Luma users need it now |

## Proposed behavior to settle before implementation

The following research defaults informed the selected approach. The linked specification now owns precise behavior, including root versus flat matching views, bounded Today paging, family guards and command recovery. Detailed defaults are implementation recommendations, not separately attributed user decisions.

1. **Importance:** None by default for new and migrated tasks. Persist stable domain values, not translated labels or color values. Display localized text plus an icon/shape cue; never color alone. No automatic escalation when overdue and no additional notification permission or channel importance changes.
2. **Hierarchy:** Root → child only. A child is a real task and may have a different date or importance from its parent. New children start undated with importance None; parent edits do not cascade these fields. Existing child-free tasks behave exactly as before. Reject missing parents, self-parenting, cycles and grandchildren at every write/import boundary.
3. **Creation and editing:** Save a new parent before entering a child editor. Offer Save and continue when parent fields are dirty. Each child uses the existing editor/draft/retry model with a parent breadcrumb. Children cannot be created under a completed parent until it is explicitly reopened. Do not silently save or discard parent drafts.
4. **Progress:** Show completed children / total children, e.g. 2/5, on the parent. Empty parents have no progress label. Completing the last child does not auto-complete the parent; offer an explicit parent completion action.
5. **Completion:** A completed parent cannot contain open children. Completing a parent with open children offers Cancel or an explicitly labeled Complete parent and remaining subtasks action showing the affected count. Commit the affected records atomically. Reopening a child under a completed parent also reopens its parent in the same transaction, with feedback. Reopening a parent alone leaves child states unchanged.
6. **Undo and concurrency:** Group completion Undo restores only records changed by that command and their previous completion timestamps. If any affected revision changed, reject the whole Undo with recovery guidance; never partially undo or overwrite intervening edits. Adding/reopening/deleting a child must coordinate with concurrent parent completion and progress reads.
7. **Deletion:** Deleting a child removes only that child. Parent deletion names the number of children that will be deleted and requires confirmation. Delete the family atomically, cancel affected reminders, and preserve existing Focus-history deletion semantics. No new delete Undo is promised.
8. **List and counts:** Default Tasks view shows roots with expandable children, independent of child dates. Root count is labeled as parent/standalone tasks; progress gives child counts. Child lists load separately in bounded pages; progress is computed from all children, not the loaded prefix. Sibling order is explicit and stable, initially insertion order with accessible Move up/down actions.
9. **Matching views:** Today, Calendar, importance filters and search match each task independently. A matching child appears once with its parent title; a parent shown only as context is not an extra matching result. A dated parent and dated child may both be actionable matches and both count; label counts accordingly. Opening a child opens that exact ID and offers navigation to its parent. Completed parents may appear as context in completed search, but never as an open result.
10. **Sorting:** Preserve the current date-first default and existing tie-breaks. Add an opt-in Importance sort in the Tasks list: High → Medium → Low → None, then due date/time with undated last, then stable creation/ID tie-breaks. In root view this sorts roots and leaves explicit sibling order intact. Filters/search evaluate children independently. Sort/filter the full dataset before limiting/paging. Keep Today, Calendar and widget date ordering in the first release.
11. **Focus:** Users can choose a child directly with parent context. Session time belongs to the selected task ID; do not add it to both parent and child. Preserve existing task totals; any family aggregate is separately labeled and deferred. Focus completion follows the same parent/child completion rules as every other entry point.
12. **Recurrence:** F08 remains its existing workstream. Before it ships alongside hierarchy, define occurrence-scoped child identity, resets and overrides. Initial hierarchy release supports one-time families; if F08 lands first, visibly prevent creating an unsupported recurring family rather than silently stripping recurrence or children.

## Ordered delivery backlog

The selected scope has an approved spec/plan. TI-02’s importance persistence source handoff is complete and QA pending; the remaining I02 hierarchy/query/command work and I03–I08 remain not started. TI-13–TI-16 remain follow-up proposals. P0 = data/release safety gate; P1 = core requested value; P2 = follow-up. Size is relative uncertainty/effort (S/M/L), not a delivery-date commitment. Test authoring and full feature validation still belong to the one final TI-QA01 after all implementation; this handoff includes only the user-requested `./gradlew check`. Historical TI-12 maps to that same task, not an additional QA task.

| ID | Priority / size | Task and user value | Dependencies | Acceptance criteria |
| --- | --- | --- | --- | --- |
| TI-01 | P1 / S | Agree importance and hierarchy contracts | None | Record scope choice and examples covering independent child dates, completion/reopen/Undo/delete, counts, None vs Low, and recurrence boundary; revise proposed defaults above and produce the approved implementation spec |
| TI-02 | P0 / M | Persist importance safely — **SOURCE COMPLETE; QA PENDING** | TI-01 | Domain/input/entity/mapping and writes round-trip all four values; migrated tasks are None; invalid stored/imported values reject; revisions and idempotent retries remain correct; generated migration/schema evidence recorded |
| TI-03 | P1 / M | Importance selection and row metadata | TI-02 | Existing editor selects/clears importance; drafts survive process recreation and failed saves; title-only creation stays available; task rows expose a localized accessible importance label; dirty/conflict/reload flows preserve selection |
| TI-04 | P1 / M | Importance filter and sorting | TI-02, TI-03 | Tasks supports All/High/Medium/Low/None and named Date/Importance sort; combined filtering applies before bounded queries; all matching tasks are discoverable beyond 50 rows; selection/scroll restore; no-results has a clear reset action; historical Today eligibility unchanged |
| TI-05 | P0 / M | Importance backup compatibility | TI-02 | New archives round-trip importance; supported legacy archives import None; restore preview and malformed/cancelled restore are covered; revision rotation invalidates stale drafts/actions; no destructive migration fallback |
| TI-06 | P0 / L | Persist one-level hierarchy and transactional commands | TI-01, TI-02 | Parent relationship and sibling order survive restart; no orphans/cycles/grandchildren; paged child reads and full progress counts agree; concurrent completion vs child create/reopen/delete cannot violate invariants; delete/complete/Undo are atomic and retry safe |
| TI-07 | P1 / L | Create and manage subtasks | TI-03, TI-06 | Parent editor adds/opens/renames/deletes/reorders children with stable IDs; unsaved parent requires Save and continue; parent/child drafts and Back origins survive process recreation; child editor shows parent context and independent date/importance; move actions work without dragging |
| TI-08 | P1 / M | Expandable task families and honest progress | TI-04, TI-06, TI-07 | Parent shows real completed/total count across all pages; expand/collapse restores across navigation/recreation; completing last child offers parent completion without doing it automatically; matching filters reveal children with parent context; no duplicate result IDs or ambiguous root/child totals |
| TI-09 | P0 / L | Unified completion, Undo and deletion recovery | TI-06, TI-07 | Every entry point uses the same family command policy; bulk confirmation lists affected count; Undo preserves previously completed children and timestamps; stale family Undo fails atomically; child reopen reopens completed parent; failed commands retain retry and drafts; app-lock delete safeguards remain effective |
| TI-10 | P1 / L | Integrate Today, Calendar, widgets, reminders and Focus | TI-08, TI-09 | Dated child appears independently with correct parent context and direct ID navigation; preview limits/counts stay correct; reminder schedules/cancellation reconcile; widget stale commands cannot bypass bulk confirmation (route to app); Focus selection/completion works on children without double-counting time; launch widgets retain their separate design contract |
| TI-11 | P0 / L | Hierarchy migration, archive and restore safety | TI-05, TI-06 | All supported old data imports as roots; new archive round-trips relationships, child order, importance and statuses; missing parents/cycles/deeper nesting/completed-parent-open-child states reject before replacement; restore is all-or-nothing; stale family commands/drafts invalidate; reset clears all new state |
| TI-12 (execution ID: TI-QA01) | P0 / L | Single final QA task: acceptance and emulator evidence | TI-03–TI-11 | Evidence covers source/domain/data/app checks, EN/FR/FA, RTL, Light/Dark/System, TalkBack spoken output/focus, keyboard, 320 dp at 2.0 font scale and wide windows, offline restart, process death, date/zone changes, dual-calendar display, populated migration/backup and 10,000-task pagination; reconcile existing TD07 debt against current results and record unresolved exceptions explicitly |
| TI-13 | P2 / M | Task-list search using existing repository search | TI-08, TI-12 | Title search in Tasks includes child matches with parent context and importance filters; match counts and pagination correct; literal wildcard characters handled; query survives return/recreation; completed search is explicitly scoped if added |
| TI-14 | P2 / M | Promote/move existing tasks | TI-06–TI-12 | Convert standalone task to child, move to another root or promote to root without changing task ID, reminder identity or Focus links; reject cycles/depth violations and invalid completed-parent moves; preserve drafts and recover stale conflicts |
| TI-15 | P2 / L | Extend existing F08 recurrence proposal for families | TI-12 plus F08 scope review | Amend F08 rather than create duplicate recurrence implementation; define per-occurrence child snapshots/reset, edit scopes, skip/end, overrides, independent child dates and importance; prove old completed history remains unchanged |
| TI-16 | P2 / S | Validate usefulness before expanding scope | TI-01; repeat after TI-12 | Observe at least five consenting target users prioritizing ten tasks and breaking one goal into children; target at least four completing both journeys without help; investigate None/Low confusion, accidental family completion and missed child discovery; document findings without task-title telemetry |

### Delivery sequence

- **Implementation first:** execute I01–I08 in the linked plan, covering importance, hierarchy, recovery, all task surfaces and archives. Dependencies are source-handoff dependencies only; no earlier QA PASS is needed.
- **One final QA task:** after every implementation handoff, execute TI-QA01 (the execution identity of research TI-12), including test authoring, host/schema/build/lint, emulator/manual tests, fixes and retests. No intermediate release or separate importance/emulator QA task.
- **Later:** TI-13–TI-16 remain outside this implementation package.

Dependencies express capability order, not permission to omit tests or expose unfinished data. Select schema/archive version numbers from the current baseline when implementing each increment.

## Design and engineering constraints

Use the current warm-paper/obsidian theme, `MaterialTheme` roles/typography/shapes, `LumaUi` tokens, shared components and existing editor/page scaffolds. Do not copy competitor priority colors into feature-local constants or revive the older green palette. Shared importance presentation belongs in the shared system if reused across task rows, editors and pickers.

Preserve navigation/callbacks, existing test tags, title/note/date validation, drafts, retries, Undo, offline fonts, EN/FR/FA, RTL, at least 48 dp targets, large text, inset/IME/scroll/FAB clearance, dual-calendar preferences and app-lock boundaries. Child indentation must not crowd controls at narrow widths. Progress and importance need localized semantics and cannot depend on color alone. Launcher widgets follow their separate contract.

Before UI implementation, recheck [theme](../app/src/main/java/com/luma/lifeos/ui/theme/), [tokens](../app/src/main/java/com/luma/lifeos/ui/common/LumaUiTokens.kt), [components](../app/src/main/java/com/luma/lifeos/ui/common/LumaUiComponents.kt), [scaffold](../app/src/main/java/com/luma/lifeos/ui/common/AppPageScaffold.kt), [redesign brief](luma-redesign/README.md), [route ledger](luma-redesign/route-coverage.md) and [consistency checklist](luma-redesign/ui-consistency-checklist.md). Current source/latest applicable contracts resolve older document differences.

## Success measures and scope boundaries

Primary hypothesis: people can identify important work and complete a concrete next action with less planning friction. TI-16 supplies an initial qualitative check; its sample is directional, not statistical evidence.

Proposed post-release measures: proportion of weekly task users applying importance, proportion creating and returning to a child task, and median time from opening Tasks to starting a selected action. Set numeric adoption targets after obtaining a baseline. Use consenting research or local-only diagnostics; adding remote analytics is outside scope. Guardrails: title-only capture remains as fast as baseline, no confirmed lost drafts or orphaned tasks, no new task-surface regressions, and large datasets remain bounded in UI and database work.

Do not bundle AI decomposition, attachments, collaboration, arbitrary nesting, dependency graphs, a new primary tab, an Eisenhower board or project management into these increments. Reuse O09 if task tags are selected later. Reconcile F08/TD07 with their existing owners and evidence rather than marking historical tasks complete from this report.

## Review questions

The user selected real one-level subtasks with the four-choice importance approach. The linked spec defines independent fields, family completion, count labels and recovery; the plan supplies I01–I08 and exactly one final TI-QA01. Review those documents before implementation. No app code, tests, Gradle commands or emulator actions were changed/run while generating this documentation.
