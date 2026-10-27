# F04 — Optional habit and task reminders QA scenarios

Date: 26 October 2026 · Status: **NOT RUN** · Priority inherited from task: **P1**

[Roadmap](../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../superpowers/specs/2026-10-26-roadmap-04-reminders.md) · [Plan](../superpowers/plans/2026-10-26-roadmap-04-reminders.md) · [QA scenarios](04-reminders-qa.md) · [Shared workflow](WORKFLOW.md)

## Entry gate and scope

This document is **phase 2** of a two-phase task. Begin only after F04-I01–I05 are source complete with a recorded handoff. Test authoring, Gradle/sync/build/lint/schema generation, automated execution, emulator/device and manual checks occur here only. The documentation generation session did not execute these scenarios.

Read [WORKFLOW.md](WORKFLOW.md) for isolation, exact result semantics, required locale/theme/window/API matrix, automated command catalogue and performance protocol. Read the entire specification before deriving test assertions. All cases require task-specific preconditions; do not mark irrelevant branch scenarios PASS—use N/A only with a source-supported scope rationale, and do not turn a deferred investigation into shipped-feature acceptance.

## Fixture

F04: frozen now 2026-10-26 07:59 Asia/Tehran; active daily habit target 8 with 2 logs, paused and skipped habits, future schedule, task at 09:00, date-only task and completed task. DST fixtures use Europe/Paris 2026-03-29 and 2026-10-25. Include denied permission/channel and stale restored revisions.

Freeze clocks through injection for automation; use dedicated AVDs for manual time/zone/credential changes. Record fixture seed/hash and expected IDs/values independently from production predicates. Reset baseline between independent scenarios. Never substitute real personal records, credentials or health data.

## Coverage obligations

The mapping below identifies the scenarios responsible for each requirement; cases may cover several requirements. During QA add assertion-level mappings and authored test names without dropping any requirement or shared matrix obligation.

| Requirement | Scenario coverage set | Initial result |
| --- | --- | --- |
| F04-R01 — Delivery promise | F04-QA01, F04-QA06, F04-QA08 | NOT RUN |
| F04-R02 — Task schedule | F04-QA03, F04-QA06 | NOT RUN |
| F04-R03 — Habit eligibility | F04-QA02, F04-QA04, F04-QA05 | NOT RUN |
| F04-R04 — Quiet hours and privacy | F04-QA04, F04-QA07 | NOT RUN |
| F04-R05 — Safe actions and restore | F04-QA05, F04-QA06, F04-QA08 | NOT RUN |

## Scenarios

### F04-QA01 — Default and permission

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Fresh install, open editors and Reminders settings without enabling; use Allow notifications in Reminders settings on API 33+, deny once, retry with rationale, then exercise permanent denial and return from system settings after granting. Separately block just the Reminders channel, recover through the same settings control, and revoke app access while the page is backgrounded. Repeat task/habit enable controls and cover API 28–32 app/channel settings. Check EN/FR/FA and RTL.

**Expected:** No unsolicited prompt; off default; explicit permission flow only; denial leaves tracking usable and shows blocked status. Reminders settings displays current app/channel access after permission callbacks and on resume. Rationale is dismissible; permanent denial opens system settings on a subsequent explicit tap. App-level blocking opens app notification settings; otherwise the control opens the Reminders channel. Granting global access leaves individual reminders off; already enabled future reminders reconcile without past-time catch-up. Task/habit controls retain opt-in after denial.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F04-QA02 — Habit grid and target

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** At 07:59 plan 08:00–20:00 every 120 minutes, then reach target, skip, undo one log and resume.

**Expected:** 08:00 then 10:00 are valid, 20:00 excluded; target does not define cadence; only future eligible slots resume.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F04-QA03 — Task civil times

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Change zone before a future alert; exercise Paris DST gap/overlap, no-time task and enabling after due time.

**Expected:** One resolved slot by specified policy; task saved civil values remain unchanged; no date-only or catch-up alert.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F04-QA04 — Quiet and snooze

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Schedule in 22:00–08:00, customize overnight window, snooze 10 minutes near day/window end.

**Expected:** Quiet slots are skipped without later burst; snooze respects expiry and reports rejection without mutation.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F04-QA05 — Action replay and races

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Tap completion/Log one twice; replay after Undo, source edit/delete, replacement restore and yesterday rollover.

**Expected:** At most one allowed mutation; stale revision/date/token fails; no phantom task/log or reinserted undone completion.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F04-QA06 — Restart and platform failure

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Fail scheduler publication, restart process, reboot isolated AVD, alter clock and revoke channel/access. Round-trip DELIVER, COMPLETE, LOG_ONE and SNOOZE events through broadcast PendingIntents and the receiver/worker; check the event action extra separately from the qualified Intent action.

**Expected:** Dirty persisted state reconciles future eligible work; no duplicate notifications or false delivered claim. Alarm/action broadcasts preserve the short event action accepted by the decoder; scheduled alarms reach the delivery handler, and notification actions reach their intended command handler.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F04-QA07 — Private previews

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Post reminders with names hidden, opt into details, then enable task-06 lock and use notification navigation and Complete/Log actions.

**Expected:** Generic default contains no private title; locked content stays hidden; no mutation before successful unlock, then source revision/date/expiry/token are checked again and consumed at most once.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

### F04-QA08 — Restore and native delivery

**Precondition:** isolated task fixture reset to its declared baseline; prerequisites and selected implementation branch recorded.

**Actions:** Round-trip configurations and observe inexact notifications on physical API 33+ hardware with battery restrictions.

**Expected:** Restored reminders stay off; explicit re-enable works; observed delays are recorded as approximate delivery, not exact timing compliance.

**Evidence:** exact fixture/build identity, action/command log and independently inspected values; add screenshots/native observations when behavior is visual or platform-owned. Initial result: **NOT RUN**.

## Automated and manual ownership

Automated command/test-file ownership is in [the plan’s phase 2](../superpowers/plans/2026-10-26-roadmap-04-reminders.md); no tests are asserted to exist before that phase. Add domain/state cases for pure rules, real Room cases for migration/atomicity, and UI/native cases for interaction. Fakes cannot establish native permission, biometric, provider, launcher, file-picker or shutdown behavior.

Manual/platform obligations: Native notification permissions/channel controls, reboot, battery restrictions and physical-device delivery.

Apply every relevant shared UI/platform matrix cell, not only the default-English emulator. For benchmarks use the shared 5-warmup/30-measurement protocol and report raw samples/p95; for task-specific failures retain the before-fix assertion and after-fix evidence.

## Execution ledger

Evidence directory during execution: `docs/feature-roadmap/evidence/<actual-run-date>/F04/`. Record source/build/environment identity once per run and link each row to its actual artifacts. Add matrix-cell rows as execution proceeds.

| Case | Result | Actual observation | Evidence | Defect/retest |
| --- | --- | --- | --- | --- |
| F04-QA01 | NOT RUN | — | — | — |
| F04-QA02 | NOT RUN | — | — | — |
| F04-QA03 | NOT RUN | — | — | — |
| F04-QA04 | NOT RUN | — | — | — |
| F04-QA05 | NOT RUN | — | — | — |
| F04-QA06 | NOT RUN | — | — | — |
| F04-QA07 | NOT RUN | — | — | — |
| F04-QA08 | NOT RUN | — | — | — |

## Exit gate

- [ ] All F04-R01–R05 assertions and scenarios mapped to actual tests/manual observations.
- [ ] Required host/build/lint/schema/instrumented gates pass for exact candidate; warnings and skips classified.
- [ ] Required native/manual/matrix/performance obligations complete with reproducible evidence.
- [ ] Defects corrected and affected results rerun; remaining blockers and invalidated old evidence explicit.
- [ ] Acceptance decision states exact completed scope. No unrun or blocked scenario is described as passing. For investigation-only outcome, feature implementation/runtime QA remains deferred explicitly.
