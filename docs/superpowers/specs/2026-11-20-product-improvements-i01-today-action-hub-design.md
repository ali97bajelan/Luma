# IP-I01 — Today action hub specification

**Priority** P1 · **Horizon** Now · **Status** SOURCE COMPLETE — QA PENDING

[Task index](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md) · [Proposal](../../20261118-Proposal.md) · [Implementation plan](../plans/2026-11-20-product-improvements-i01-today-action-hub.md) · [Final emulator QA](../plans/2026-11-20-product-improvements-q01-emulator-qa.md#qa-01)

## Intent and current behavior

Make current commitments and their full lists easy to find while preserving historical-day browsing.

Today already has Tasks and Habits callbacks, a return-to-today notice and a bounded due/overdue task projection with a total. The current query page is 50 tasks and the card uses a height-limited scrollable viewport. The task card already has View all and Load more entry points. Reuse these and inspect the full Tasks list’s bucket contract before changing presentation.

## Scope

Use a single Today action section with Tasks, Habits and an active/paused Focus affordance ahead of retrospective summaries on the current day. Keep the date and capture actions prominent. On past days retain dated history, existing date-scoped habits and an explicit Return to today, with no live current-day task preview. Retain authorized visibility, provider-absent behavior, and the current task and habit list destinations.

Out of scope. This task excludes manual plan-my-day selection, task recurrence, new scheduling model or habit rows in Calendar.

## Requirements

- **IP-I01-R1** Show the authorized due/overdue task total and exact remaining count when results are truncated. Derive remaining from the matching projection’s total minus loaded rows, clamped at zero; never use the number of visually rendered optimistic rows for the total. Keep the existing scroll affordance for loaded rows outside the viewport. Hide the unloaded count at zero and keep the full Tasks link reachable in empty/error states.
- **IP-I01-R2** Make overdue, due today and undated distinguishable in the full Tasks list using its existing date buckets and filters. Future and completed tasks retain their current behavior. View all opens the full list without applying a date or editing a due date.
- **IP-I01-R3** Keep the Habits link date-aware, Today return discoverable, and active/paused Focus details subject to existing privacy gates. Retain family confirmation, stale-write recovery, retry and Undo.

## Implementation approach

Reorder current route sections and use the current bounded page and explicit links. Expanding the dashboard into a full list would duplicate paging and make the summary harder to scan. Navigation destinations and models already support the chosen hierarchy.

## Acceptance

**QA-01** Use 57 matching open tasks and the current 50-row page, plus a completed, future and undated task. Assert 7 unloaded initially, correct updates on paging/completion, and correct full-list buckets. Verify past-day, midnight/device zone, hidden-provider and family/Undo paths.

**Product validation** Observe time and unassisted success finding all due-today work and logging a habit using a large synthetic library.

Apply the [shared implementation contract](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md#shared-implementation-contract). Runtime acceptance is owned by IP-Q01 after all handoffs.
