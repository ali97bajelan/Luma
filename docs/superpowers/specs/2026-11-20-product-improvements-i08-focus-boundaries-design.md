# IP-I08 — Focus boundaries, recovery and completion specification

**Priority** P2 · **Horizon** Later · **Status** SOURCE COMPLETE — QA PENDING

[Task index](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md) · [Proposal](../../20261118-Proposal.md) · [Implementation plan](../plans/2026-11-20-product-improvements-i08-focus-boundaries.md) · [Final emulator QA](../plans/2026-11-20-product-improvements-q01-emulator-qa.md#qa-08)

## Intent and current behavior

Explain in-app Focus navigation, show time recovery clearly and offer explicit next steps after finishing.

FocusNavigationGuard reads the existing runtime guard. FocusScreen already has setup, recovery, emergency exit and completion flows. Session time, separate break timing and task association already have durable state and protection mechanisms.

## Scope

Use the existing setup, recovery and completion surfaces. Briefly explain the guard and safe exit before start. In recovery use runtime/persisted values to show credited time and why intervening time is uncertain. Present explicit task, reflection and break actions at completion where eligible.

Out of scope. This task excludes OS-wide blocking, new timer engine, automatic task completion or permission expansion.

## Requirements

- **IP-I08-R1** Setup defines Focus as in-app navigation control and identifies the established pause/end/emergency-exit path. Existing Android app-switching and external handoff behavior remain available as appropriate.
- **IP-I08-R2** Recovery shows confirmed credited duration and states when intervening time is uncertain. Options use the existing resolution callbacks and clock rules, including reboot/time changes. Keep safe exit available and avoid a delivery promise if notifications are denied.
- **IP-I08-R3** Ending or expiring a session does not mutate its linked task. An explicit linked-task action routes to the existing completion-capable workflow only for a valid, authorized link. Existing task-family confirmation, conflict/retry and Undo stay authoritative. Deleted/completed/protected tasks render appropriate available or redacted states. Reflection and break remain separate, and break adds zero credited Focus time.

## Implementation approach

Treat persisted runtime time and existing protected route callbacks as the source of truth. A second timer or task mutation path would conflict with recovery and family completion rules.

## Acceptance

**QA-08** Start, switch apps, pause/leave and emergency-exit. Recreate required recovery with process/reboot/clock simulation, then verify credited duration and zero break credit. Finish with an open, completed, deleted and protected linked task. Exercise explicit family completion, cancel and Undo.

**Product validation** Observe unassisted safe exit and whether people can explain credited time and linked-task status after interruption.

Apply the [shared implementation contract](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md#shared-implementation-contract). Runtime acceptance is owned by IP-Q01 after all handoffs.
