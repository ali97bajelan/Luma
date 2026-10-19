# Luma home-screen widget tasks

Prepared: 19 October 2026. Status: **WG01 implemented; acceptance verification pending. WG02 implemented; acceptance verification pending. WG03–WG08 remain pending.** Room schema 5 was exported and the `:core:data:compileDebugKotlin` production compile passed. No Gradle test or emulator/launcher test was run, per request.

Requested outcome: Habits and Tasks widgets support common actions on the Android home screen; Quick capture opens a new Mood, Memory or Journal editor directly. “mod” is interpreted as “mood”. Use `widgets/WGNN` when referring to these tasks to distinguish them from existing habit, task and audit numbering.

Read together:

- [Product and technical specification](superpowers/specs/2026-10-19-home-screen-widgets-design.md)
- [Implementation plan and file/interface contracts](superpowers/plans/2026-10-19-home-screen-widgets.md)
- [Existing improvement backlog](ui-ux-audit/IMPROVEMENT_TASKS.md)
- [Design system](ui-ux-audit/DESIGN_SYSTEM.md)

## Scope fixed for v1

Three providers: **Luma Habits**, **Luma Tasks**, **Luma Quick capture**. Use native RemoteViews with the current Android API 28 minimum / 35 compile and target; no new library. Habits/Tasks show summaries by default; names and row actions require a per-instance opt-in. Details support up to three rows, confirmed actions, retained feedback and exact Undo. Tasks scope is overdue and today; habits use the fixed tracking zone. Quick capture reuses the existing editors and never saves an entry itself.

Keep operation tokens in local runtime tables and consume them transactionally with repository writes. Add Room migration 4→5, keep manual archive schema 4, and invalidate runtime tokens on successful replacement restore. Offline behavior, en/fr/fa, RTL, app theme, 48dp targets, 200% text and stale-view safety are part of the initial feature, not follow-up polish. Reminders, heatmaps, full scrolling lists, widget text entry and arbitrary filters are deferred.

## Prioritized implementation queue

Priority here describes sequencing within widget delivery; it does not redefine the audit's P0–P3 release priorities. Complexity is relative scope, not an hours estimate.

| Task | Status | Priority | Deliverable | Depends on | Complexity | Acceptance |
| --- | --- | --- | --- | --- | --- | --- |
| WG01 | Implemented; verification pending | First slice | Direct Quick capture widget; typed cold/warm intent routing; existing draft preservation | — | Medium | AC-01/02 |
| WG02 | Implemented; acceptance verification pending | Foundation | Pure contracts, local runtime tables, bounded snapshots, migration, archive separation and restore invalidation | — | High | AC-08/09 |
| WG03 | Pending | Integrity gate | Atomic +1/completion, duplicate/replay safety, stale validation, exact Undo and retained retry | WG02 | High | AC-03–06/08/09 |
| WG04 | Pending | Foundation | Refresh/action workers, coalescing, lifecycle/time/locale/theme handling and process-absent execution | WG01–03 | High | AC-07/12 |
| WG05 | Pending | Feature | Habits provider and responsive shared rendering/feedback | WG01–04 | Medium | AC-01/03/06/10 |
| WG06 | Pending | Feature | Tasks provider, overdue/today scope, completion and off-preview recovery | WG05 | Medium | AC-01/04/06/10 |
| WG07 | Pending | Release dependency | Required configuration, in-app instance management, discovery/pinning and privacy help | WG01–06 | Medium | AC-08/11 |
| WG08 | Pending | Acceptance gate | Integrated suites, launcher/device/accessibility/performance evidence and release decision | WG01–07 | High | All |

AC references expand to `WG-AC-NN` in the specification. WG01 can ship independently. Data providers are development-only until WG03 safety and WG07 required configuration are complete; do not expose mutations behind incomplete transaction/restore behavior. WG01 and WG02 source implementation is in place; **WG03** is the next implementation task, with WG01/WG02 acceptance verification still outstanding. The full delivery order is WG01→WG02→WG03→WG04→WG05→WG06→WG07→WG08. Independent planning order does not authorize concurrent edits to shared navigation, strings or composition roots.

## Task completion checklist

- [ ] WG01 acceptance: all three new-entry buttons work cold/warm; Cancel creates nothing; recreation and repeated taps preserve existing drafts; default and compact capture layouts verified. Source implementation is present; behavior remains unverified because automated and launcher checks were not run.
- [ ] WG02 acceptance verification: migrations 1–4→5 retain data; snapshots return bounded rows/full counts; hidden details have no row tokens; archives 1–4 remain compatible and restore cleanup is atomic. Source implementation, Room schema 5 export and production compile are complete; storage/migration/backup tests remain pending because no Gradle tests or emulator tests were run, per request.
- [ ] WG03: duplicate/concurrent/lost-result delivery writes once; midnight/zone/count/revision/config races reject; original command after Undo stays terminal; failed Undo/Retry retains original identity.
- [ ] WG04: one periodic schedule only with data widgets; no dropped refresh arriving during render; app and widget changes update all affected instances; process death/remap/delayed work fail safely.
- [ ] WG05: habits respect tracking date, active schedule, skip/target/order; feedback survives hidden rows; small/large/RTL/scaled layouts preserve recovery.
- [ ] WG06: eligible task scope/order/count is correct; completion never overwrites a changed task; Add task uses launch date; removed rows retain feedback.
- [ ] WG07: first configuration/reconfiguration cancel is safe; per-instance opt-in and revocation work; supported pin request/fallback is truthful; no private content enters previews/intents/logs.
- [ ] WG08: current full suites, migrations/backups, two launcher families, API 28 and 35/36, language/theme/font matrix, real TalkBack/keyboard and measured performance are recorded or explicitly blocked.

Use the plan's task-specific failing-test → implement → passing-test steps. Commit reviewable slices during implementation; do not mark a task complete for compilation alone. Existing audit tasks retain their own acceptance/evidence and must not be silently closed by these checks.

## Execution readiness and evidence

The planning package fixes provider scope, widget interactions, configuration defaults, typed navigation, data contracts, migration/backup boundary, action lifetime, refresh policy, file ownership and acceptance tests. Product choices are documented defaults; they may be revised before implementation without pretending user research has already validated them.

Before WG02, current files and pending changes were reread; the working tree also contains unrelated untracked release and data-test artifacts, which were preserved. Room schema version 5 was available; the additive migration, source tables and exported schema are in place, and production compilation passed. Reuse existing offline test/build tooling and the disposable direct-ADB fallback documented in [Todo acceptance](TODO_ACCEPTANCE_2026-10-19.md) if connected Gradle runs remain blocked. Never reset/restore the owner's installed data.

At WG08 create `docs/WIDGET_ACCEPTANCE_<actual-date>.md` and record build/APK identity, device/API/launcher, dimensions, locale/theme/font, fixture, expected/actual and evidence per WG-AC. Keep the distinction between planned checks, compiled tests, observed passes and explicit blocks. WG01 now has source implementation and static resource-check evidence; no successful build or device evidence exists yet.
