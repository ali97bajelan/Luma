# F08 — Recurring tasks specification

Date: 26 October 2026 · TI-15 family-scope extension: 15 November 2026 · Priority: **P2** · Status: **DRAFT — NOT IMPLEMENTED / NOT VALIDATED**

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](2026-10-26-roadmap-08-recurring-tasks.md) · [Plan](../plans/2026-10-26-roadmap-08-recurring-tasks.md) · [QA scenarios](../../feature-roadmap/08-recurring-tasks-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Intent and dependencies

Create daily, selected-weekday and monthly task series, including one-level recurring task families, without duplicate occurrences or rewriting completed history.

Dependencies: Tasks 02 and 04; schema/archive work relies on task 01 guarantees. Family recurrence consumes the one-level task-family contracts in the 14 November task-importance/subtasks specification (TI-01 and TI-06–TI-11). Reuse those family IDs, revisions, completion/Undo, archive and restore rules; do not define a second parent/child model. Before implementation or acceptance, reconcile against TI-QA01 results and the then-current F08 source baseline.

Rank and scope come from [the feature proposal](../../FEATURE_IMPROVEMENT_PROPOSAL_2026-10-26.md). Specific new defaults in this document are proposed implementation choices, not claims of user approval. Read the existing implementation at execution time; this document does not certify its current runtime behavior.

## Architecture and alternatives

Recommended approach: Keep immutable root-series occurrence identity separate from task rows and use versioned future schedule segments. A root task series owns versioned one-level child templates; each root occurrence gets its own child task snapshots with distinct task IDs, status and completion history. Materialize a bounded visible range and each family atomically; reuse the canonical task-family persistence, completion, reminder and navigation contracts rather than introducing a parallel recurrence/family subsystem.

Considered alternatives: keep recurring tasks root-only and defer recurring families, or create a separate family recurrence engine. Root-only recurrence leaves users unable to repeat the newly supported one-level task families; a separate engine duplicates task identity, family guards, archive and recovery paths. Extend F08 and the existing task-family contracts together. These are TI-15 proposal choices for later implementation review, not a claim that runtime behavior exists or that every detailed default was separately approved.

## Existing source and handoff material

- [core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoModels.kt](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoModels.kt)
- [core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoRepository.kt](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoRepository.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/todo/TodoEntity.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/todo/TodoEntity.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/todo/TodoDao.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/todo/TodoDao.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomTodoRepository.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomTodoRepository.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/db/LumaDatabase.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/db/LumaDatabase.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupRows.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupRows.kt)
- [app/src/main/java/com/luma/lifeos/ui/todo/TodoEditorRoute.kt](../../../app/src/main/java/com/luma/lifeos/ui/todo/TodoEditorRoute.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomCalendarRepository.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomCalendarRepository.kt)



## Contracts and ownership

Names below are proposed interfaces to create or adapt, not claims that these declarations already exist. Keep existing public callers working or migrate every caller explicitly in the owning change. Resolve exact current types while preserving these behaviors.

- Proposed `RecurrenceRule` is Daily, Weekdays(Set<DayOfWeek>) or Monthly(anchorDay: Int); `TaskSeries` has UUID, start/end inclusive dates, civil dueTime, schedule revisions and title/note.
- Proposed `TaskOccurrenceKey(seriesId: UUID, nominalDate: LocalDate)` remains unique; `ensureOccurrences(from: LocalDate, through: LocalDate)` is idempotent and creates at most the requested 366-day window.
- One-off parent overrides/skips retain the nominalDate key even when dueDate moves; task ID stays stable. `editFuture(seriesId, effectiveDate, expectedRevision, change)` creates a new schedule segment transactionally.
- A root series may own direct-child templates only. Each template has a stable template UUID, sibling position, title/note, its own importance, optional civil due-date offset in days from the root occurrence's nominal date, and optional civil due time. Null offset means undated. A generated child is keyed uniquely by `(parentOccurrenceKey, childTemplateId)` and receives a new stable task UUID for that occurrence; child task IDs and completion state are never shared across occurrences.
- Parent/child recurrence is one-to-many by snapshot: children follow the root series cadence, but cannot own another series or descendants. A child date offset is computed from the root nominal date, not an overridden due date. Child date/time and importance are independent; changing or rescheduling the root never copies those fields into children.
- Every child-template or generated-child edit requires an explicit `This occurrence` or `This and future` scope. `This occurrence` writes a child snapshot override only for the selected root occurrence. `This and future` creates a version effective at a nominal date, updates eligible unmodified open future snapshots transactionally, and is inherited by future materialization. Explicit one-off child overrides, skipped occurrences, completed parent occurrences, and completed child rows are retained.
- Materializing a new parent occurrence copies the effective template version into distinct child task rows. Each child starts open with no completion timestamp, its own copied title/note/importance/position and due date computed from its own optional offset. Completion, edits and overrides from another occurrence are never copied. Historical completed root and child rows—including IDs, content, dates, importance, status, timestamps, revisions, parent links and sibling order—remain byte-for-byte unchanged by later template edits, skip/end, or regeneration.

## Requirements

### F08-R01 — Rules and month end

Daily means each date; weekdays requires >=1 selected weekday; monthly day 1–31 clamps to the last valid date without changing the original anchor. Jan 31 → Feb 28 → Mar 31 in 2027. Start/end inclusive within years 1–9999; no unbounded pre-generation.

### F08-R02 — History and missed work

Each occurrence is an independent task with stable ID and nominal date. Missed open occurrences remain overdue; no auto-completion or catch-up burst. A first series creates no dates before its start; materialization of elapsed valid dates is bounded/paged and must not silently omit overdue work.

### F08-R03 — Edit scopes

Offer This occurrence and This and future. A single override changes only its task; future edits preserve completed/skipped past records and explicit one-off overrides. End series cancels unmodified future open generated tasks; deletion/history removal is a separate explicit confirmation.

### F08-R04 — Cross-surface behavior

Occurrences use existing dated task membership in Today/Calendar/tasks/widgets. Habits stay excluded from Calendar. Civil date/time values do not shift with travel; reminder instants recalculate by task 04 policy; no standalone life event is invented.

### F08-R05 — Persistence and compatibility

Uniqueness, family lifecycle changes and revision checks are transactional under retries/concurrent generators. The current archive schema 14 already carries non-recurring hierarchy, sibling order and importance; restoring that format preserves those families as one-time tasks and never infers recurrence. Supported task-only archive formats predating hierarchy (including v12) restore each task as a root, preserving their established content/status/timestamp behavior. The new recurrence archive version is selected from the implementation baseline and stores series, schedule segments, templates, occurrence overrides/snapshots, lifecycle states and skips. Malformed families reject atomically. Derived generation cursors/runtime stay device-local and are rebuilt; replacement revokes stale commands.

### F08-R06 — One-level recurring task families

A recurring task is a root; its direct children are versioned series templates and each root occurrence owns an independent child snapshot family. Enforce the existing one-root/one-child-level contract at every create, edit, import and restore boundary. Use the canonical task-family IDs, root family revision, atomic completion/reopen, confirmation, idempotent retry, stale-command and Undo rules from the task-importance/subtasks specification. There is no child-owned recurrence, arbitrary nesting, or second family repository.

Every root occurrence is materialized with its effective child-template version in one transaction and within the same bounded paging contract. The unique child key is the parent occurrence key plus template UUID. Root and child task UUIDs remain stable for the life of their one occurrence and differ across occurrences. Child ordering is the template's explicit sibling order. Repeated materialization cannot duplicate a root or any child.

For a template child with an offset, derive its due date from that occurrence's nominal root date plus the signed day offset; null remains undated. Preserve its own time and importance. A root occurrence date override does not shift child dates. Moving one child occurrence changes only that snapshot unless the user explicitly selects a future template edit.

Adding, editing, reordering, or retiring a child requires an explicit scope. `This occurrence` creates/changes/removes only the selected root occurrence's child snapshot and records a one-off override or skip; a child added only to one occurrence receives a fresh occurrence-scoped child-template UUID that is never added to the series template. `This and future` creates a template version effective from the selected nominal date. Apply content, date and importance changes only to eligible open, unmodified snapshots in the same transaction. For sibling-order changes, update a materialized family only when its root and every child are active, open and unmodified. If any root/child is completed, skipped, cancelled or explicitly overridden, preserve the whole family's existing sibling positions; never renumber only some children or create duplicate positions. The new order still applies to future materialization and wholly eligible families. Reject a `This occurrence` reorder for a protected family with a clear protected-state result and no mutation. Completed root occurrences and completed child snapshots are immutable. Never overwrite a per-occurrence child override, skipped root/child, or completed row. A retired template is not reused for a later child; a later re-add gets a fresh template UUID. Reject a date-offset calculation that exceeds supported LocalDate bounds before writing any part of the family.

Persist a recurrence lifecycle separate from task completion for each root occurrence and child snapshot: `ACTIVE`, `SKIPPED`, or `CANCELLED`. A skipped/cancelled row remains stored with its original task ID and history, is not marked completed, and has no fabricated completion timestamp. Keep inactive rows read-only for history and exclude them from actionable open/date/search/Focus/widget/reminder projections. A direct-ID command against an inactive row returns its lifecycle and parent context and makes no mutation; it must never resolve to a different task. Parent progress retains the canonical completed/total count over all child snapshots and reports skipped/cancelled counts separately. Only ACTIVE open children block parent completion or enter a bulk-complete confirmation; a parent can complete once none remain ACTIVE and open. Skipped/cancelled children never count as completed.

Skipping a completed root or completed child is rejected without mutation. Skipping an ACTIVE open root occurrence marks the root SKIPPED and atomically marks its remaining ACTIVE open generated children SKIPPED, including explicit child overrides; preserve completed child history. Its durable nominal-key tombstone prevents regeneration. Skipping one ACTIVE open child marks only that snapshot SKIPPED and leaves the root and siblings actionable. Ending a series stops generation after its effective end boundary. For each already materialized future occurrence, if any root or child has an explicit per-occurrence override, preserve that entire family as an actionable unit with root context; otherwise mark the open root and ACTIVE open child snapshots CANCELLED. Preserve completed rows and prior skipped/cancelled keys unchanged. Cancel or reconcile reminders/widgets after commit. Repeated skip/end/materialize commands are idempotent, and stale commands reject atomically.

Date-window queries use each task's own due date. For a requested due-date window `[d1, d2]`, query roots by their effective root schedule window and by persisted root date overrides whose concrete due dates fall in the window. For each effective template segment and signed child offset `k`, map the child window back to candidate nominal root dates `[d1-k, d2-k]`, intersect with the segment and series bounds, retain only dates that satisfy that segment's recurrence rule, then query the corresponding families; also query child snapshot overrides by their concrete due dates. Use overflow-safe date arithmetic, deduplicate root occurrence keys across these ranges, and page deterministically with the 366-day maximum. Thus a child due today is discoverable even if its root's nominal date lies outside today's window. Queries and all direct-ID mutations must preserve parent context while never surfacing inactive snapshots as actionable.

Family actions cannot leave an actionable child without its root context or complete a root with an ACTIVE open child. Parent completion follows TI-R06 confirmation/atomicity; child completion and reopen follow the same family transaction policy.

A series edit, materialization, occurrence override/skip, child mutation, completion, and end must coordinate through one family-aware transaction/revision guard. Stale previews reject the entire operation and require a fresh preview; never partially update a family. Archives round-trip template versions, child snapshot identity/order, independent fields, overrides, lifecycle states and completion timestamps. Legacy archive compatibility follows F08-R05: hierarchy-capable archives preserve their existing non-recurring families, while task-only formats restore roots; no legacy format invents recurrence or child templates. A future edit, skip, end, or repeated generation must never change any field of an already completed root or child occurrence.

## Two-phase delivery contract

**Phase 1 — Implementation:** implement the source/configuration/resources/documentation described in the plan, review the source and diff, and record the handoff. **No Gradle command, sync, dependency resolution, build, schema generation, lint, automated test execution, emulator launch or device test. Test authoring is deferred to QA.** Source checks do not prove compilation or correctness.

**Phase 2 — QA:** author tests/fixtures, generate and inspect schemas where relevant, run Gradle/build/lint/automated suites, execute native/manual cases, correct discovered defects and record evidence. Begin only after this task’s implementation source handoff; all initial outcomes are NOT RUN. Follow the [shared workflow](../../feature-roadmap/WORKFLOW.md) for isolation, matrix, commands, evidence and completion gates.

## Scope boundaries

No arbitrary cron/RRULE editor, natural-language recurrence, infinite eager generation, replacement of counted habits, child-owned recurrence, or hierarchy deeper than root → direct child. Existing non-recurring families remain one-time families unless explicitly converted through a future reviewed contract.

## Acceptance and traceability

All six requirements must map to implementation steps and QA outcomes. [The QA document](../../feature-roadmap/08-recurring-tasks-qa.md) provides executable scenarios with fixtures/actions/oracles; [the plan](../plans/2026-10-26-roadmap-08-recurring-tasks.md) owns concrete files and implementation/QA checklists. Both phases start pending. A source-complete task is not feature-complete. For assessment/deferred branches, report the delivered investigation separately from feature/runtime acceptance. TI-15 extends this F08 proposal only; F08 runtime implementation and QA remain pending, and no recurrence behavior is certified by this document update.
