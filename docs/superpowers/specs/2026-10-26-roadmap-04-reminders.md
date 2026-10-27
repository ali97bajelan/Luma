# F04 — Optional habit and task reminders specification

Date: 26 October 2026 · Priority: **P1** · Status: **SOURCE COMPLETE — QA PENDING / NOT VALIDATED**

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](2026-10-26-roadmap-04-reminders.md) · [Plan](../plans/2026-10-26-roadmap-04-reminders.md) · [QA scenarios](../../feature-roadmap/04-reminders-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Intent and dependencies

Deliver private, optional approximate local reminders with safe completion and snooze actions.

Dependencies: Task 02; reuse the existing T27/T28 habit reminder specification and planner contracts.

Rank and scope come from [the feature proposal](../../FEATURE_IMPROVEMENT_PROPOSAL_2026-10-26.md). Specific new defaults in this document are proposed implementation choices, not claims of user approval. Read the existing implementation at execution time; this document does not certify its current runtime behavior.

## Architecture and alternatives

Recommended approach: Implement existing habit planner/repository contracts first. Share Android scheduling/notification adapters with a separate task planner; validate revision, date, expiry and consumed action token inside each mutation transaction.

Considered alternatives: retain the current behavior and document its limitations, or introduce an independent subsystem. Retaining behavior is acceptable only for an explicitly documented investigation/deferred outcome; otherwise it does not meet the intent above. An independent replacement would duplicate existing persistence/navigation and recovery paths; extend the current module contracts unless a specific requirement makes reuse impossible. The recommendation is proposed, not a recorded user selection.

## Existing source and handoff material

- [docs/superpowers/specs/2026-10-17-habits-and-navigation-design.md](2026-10-17-habits-and-navigation-design.md)
- [docs/superpowers/plans/2026-10-17-habit-reminders.md](../plans/2026-10-17-habit-reminders.md)
- [core/data/src/main/kotlin/com/luma/lifeos/data/habit/HabitEntities.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/habit/HabitEntities.kt)
- [core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoRepository.kt](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoRepository.kt)
- [app/src/main/java/com/luma/lifeos/LumaApplication.kt](../../../app/src/main/java/com/luma/lifeos/LumaApplication.kt)
- [app/src/main/AndroidManifest.xml](../../../app/src/main/AndroidManifest.xml)



## Contracts and ownership

Names below are proposed interfaces to create or adapt, not claims that these declarations already exist. Keep existing public callers working or migrate every caller explicitly in the owning change. Resolve exact current types while preserving these behaviors.

- Retain the existing proposed HabitReminderRepository/ReminderSlot contracts in the linked plan; do not fork a second incompatible habit planner.
- Proposed `TaskReminderConfig(taskId: UUID, enabled: Boolean, revision: UUID)` and `nextTaskReminder(task: TodoTask, config: TaskReminderConfig, now: Instant, zone: ZoneId): TaskReminderSlot?`.
- `TaskReminderSlot` carries task ID, source task revision, config revision, due civil date/minute, scheduled Instant, expiry and action token. TaskReminderStore consumes completion/snooze exactly once transactionally.

## Requirements

### F04-R01 — Delivery promise

Ordinary approximate notifications, off by default; use no exact-alarm, full-screen or foreground-service permission. Request POST_NOTIFICATIONS only from explicit enable on API 33+, including the Allow notifications button on the Reminders settings page. That page shows current app/channel access, supports recovery through system notification settings after permanent denial or channel blocking, and refreshes access/reconciles future eligible reminders on return. Granting notification access does not enable individual task/habit reminders. Distinguish configured, pending, blocked and scheduled; never claim delivery from scheduling alone.

### F04-R02 — Task schedule

A task alert requires an open task with dueDate and dueTime; no date-only default time. Resolve floating civil values in the current device zone, move gap times to the first valid instant after the gap, and use the earlier offset once during overlap. Never create a catch-up alert when enabling after due instant. Reconcile on reboot/time/zone/edit.

### F04-R03 — Habit eligibility

Follow T27/T28 saved tracking zone, schedule grid, target/skip/lifecycle rules and consumed-token behavior. Ten-minute snooze may not cross the attributed date/window. Preserve actual counts/history; late yesterday actions do not log today.

### F04-R04 — Quiet hours and privacy

Proposed default quiet window 22:00–08:00, user-editable and disableable; crossing midnight is supported, equal bounds disable quiet hours. Skip slots inside quiet hours, with an explanation, rather than send a burst later. Generic content by default; private names/details are explicit opt-in. When task 06 is installed, locked navigation and mutation actions must authenticate before rendering/writing, then revalidate source/date/expiry/token.

### F04-R05 — Safe actions and restore

Task snooze is 10 minutes and expires at local day end; actions fail safely after edit/delete/complete/restore/revocation. Restore keeps configurations disabled and rotates runtime revisions. Completing from notification uses TodoRepository revision/idempotence rules and never writes a life event.

## Two-phase delivery contract

**Phase 1 — Implementation:** implement the source/configuration/resources/documentation described in the plan, review the source and diff, and record the handoff. **No Gradle command, sync, dependency resolution, build, schema generation, lint, automated test execution, emulator launch or device test. Test authoring is deferred to QA.** Source checks do not prove compilation or correctness.

**Phase 2 — QA:** author tests/fixtures, generate and inspect schemas where relevant, run Gradle/build/lint/automated suites, execute native/manual cases, correct discovered defects and record evidence. Begin only after this task’s implementation source handoff; all initial outcomes are NOT RUN. Follow the [shared workflow](../../feature-roadmap/WORKFLOW.md) for isolation, matrix, commands, evidence and completion gates.

## Scope boundaries

No exact alarm promise, daily catch-up burst, task recurrence, or background health permission.

## Acceptance and traceability

All five requirements must map to implementation steps and QA outcomes. [The QA document](../../feature-roadmap/04-reminders-qa.md) provides executable scenarios with fixtures/actions/oracles; [the plan](../plans/2026-10-26-roadmap-04-reminders.md) owns concrete files and implementation/QA checklists. Both phases start pending. A source-complete task is not feature-complete. For assessment/deferred branches, report the delivered investigation separately from feature/runtime acceptance.
