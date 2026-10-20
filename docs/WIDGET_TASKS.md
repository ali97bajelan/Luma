# Luma home-screen widget tasks

Updated: 20 October 2026. Status: **WG01–WG07 implementation complete; API 36 / Pixel Launcher smoke checks and focused widget regressions pass. Broad acceptance remains partial; WG08 is blocked and release is no-go pending the untested device, accessibility and performance matrix.** Room schema 5 is exported. The full cross-module JVM/build/lint gate passed after the storage fix; after the final renderer fix, app unit tests, debug/app-test packaging and app lint passed. The complete `core:data` emulator suite passed 147/147 tests. The full app emulator suite ran 171 tests and had five failures against the preserved app database; a clean-data rerun was not permitted by automatic review. The Android Gradle connected-test task also could not resolve its uncached UTP host-output artifact offline, so APKs were run directly with `am instrument`. See the [dated acceptance record](WIDGET_ACCEPTANCE_2026-10-20.md) and [launcher screenshots](ui-ux-audit/evidence/widgets/quick-capture-api36.png). The pre-existing untracked release APK is not tied to the current source commit and is not acceptance evidence.

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
| WG01 | Implemented; API 36 launcher smoke passed; full acceptance partial | First slice | Direct Quick capture widget; typed cold/warm intent routing; existing draft preservation | — | Medium | AC-01/02 |
| WG02 | Implemented; schema/storage/migration/backup suite passed; restore edge cases partial | Foundation | Pure contracts, local runtime tables, bounded snapshots, migration, archive separation and restore invalidation | — | High | AC-08/09 |
| WG03 | Implemented; habit/task Saved→Undo regressions passed on emulator; race/replay matrix partial | Integrity gate | Atomic +1/completion, duplicate/replay safety, stale validation, exact Undo and retained retry | WG02 | High | AC-03–06/08/09 |
| WG04 | Implemented; live launcher refresh smoke passed; lifecycle matrix partial | Foundation | Refresh/action workers, coalescing, lifecycle/time/locale/theme handling and process-absent execution | WG01–03 | High | AC-07/12 |
| WG05 | Implemented; default summary, +1 and Undo passed on Pixel Launcher; matrix partial | Feature | Habits provider and responsive shared rendering/feedback | WG01–04 | Medium | AC-01/03/06/10 |
| WG06 | Implemented; default summary, Add, Complete and Undo passed on Pixel Launcher; matrix partial | Feature | Tasks provider, overdue/today scope, completion and off-preview recovery | WG05 | Medium | AC-01/04/06/10 |
| WG07 | Implemented; native picker and first-placement summary configuration observed; matrix partial | Release dependency | Required configuration, in-app instance management, discovery/pinning and privacy help | WG01–06 | Medium | AC-08/11 |
| WG08 | Evidence updated; acceptance blocked / release no-go | Acceptance gate | Integrated suites, launcher/device/accessibility/performance evidence and release decision | WG01–07 | High | All |

AC references expand to `WG-AC-NN` in the specification. WG01 can ship independently. WG03 preserves action safety and WG07 provides required configuration. WG03 now issues/reuses opaque row commands, validates and commits repository mutations with their outcomes and Undo records in one Room transaction; emulator tests caught and fixed feedback tokens being discarded after rows disappeared. WG04 serializes refresh rendering through one WorkManager chain, schedules 30-minute work only while data widgets exist, observes Room content invalidations in the app process, and dispatches token-only actions to workers. WG06 reuses the task command pipeline for overdue/today tasks, completion, editor navigation, launch-time Add task dates and retained Undo feedback. The renderer now clears dynamic row children before reapplication and uses launcher-provided sizes to select responsive layouts. WG08 records remaining host/API/accessibility/performance blockers; release stays no-go until those gates are met. The full delivery order is WG01→WG02→WG03→WG04→WG05→WG06→WG07→WG08. Independent planning order does not authorize concurrent edits to shared navigation, strings or composition roots.

## Task completion checklist

- [x] WG01 emulator smoke: Pixel Launcher picker exposes Quick capture at 3×2; Mood, Memory and Journal buttons opened their existing editors. Full cold/warm, draft/recreation and small-font acceptance remains open.
- [x] WG02 test gate: the complete `core:data` instrumentation suite passed 147/147, including the repository migration and backup suites. Full widget restore/remap/detail-revocation edge cases remain open.
- [x] WG03 implementation: command replay is terminal; habit date/zone/schedule/skip/count and task date/revision/eligibility are revalidated; Undo uses the exact saved log or resulting task revision; transient retry keeps the original token.
- [x] WG03 focused acceptance: a real launcher +1 and Complete each persisted, refreshed, exposed an Undo action and successfully reverted on the emulator. Storage regressions also prove Undo survives the snapshot after the task row disappears or the habit reaches its target.
- [ ] WG03 remaining acceptance: concurrent/duplicate delivery, lost-result restart, midnight/zone races, changed revision/config, stale replay after Undo, and retry identity need dedicated tests.
- [x] WG04 emulator smoke: app-created habit/task changes refreshed the live widgets. Worker scheduling, process death, reboot, host remap and delayed-work behavior remain open.
- [x] WG05 emulator smoke: first-placement configuration defaulted to summary mode; detail opt-in showed habit rows; +1 and Undo worked, including at the target. Other locales/themes/font scales, skip/zone races and multi-instance behavior remain open.
- [x] WG06 implementation: Tasks provider, localized due-date rows, Complete, Add task and Open tasks use the existing scope/order/count and revision-safe command pipeline; retained feedback survives when a completed task leaves the preview.
- [x] WG06 emulator smoke: Add task opened the editor with today’s date; a due-today task appeared, Complete removed its row, Saved/Undo stayed visible, and Undo restored the task. Overdue ordering, missing/stale task recovery and midnight behavior remain open.
- [x] WG07 implementation: Habits and Tasks require Save/Cancel configuration with summary mode as the default; Settings lists live data-widget instances and opens the same configuration screen; supported pin requests use Android's success callback and unsupported launchers get picker steps; privacy help covers launcher-managed caching and per-instance detail consent. Quick capture is discoverable and pinnable without configuration.
- [x] WG07 emulator smoke: Pixel Launcher lists all three providers; first placement of Habits and Tasks opened required configuration with summary mode selected, and in-app widget setup added a Habits instance. Reconfigure-cancel, pin denial/fallback and a second launcher remain open.
- [x] WG08 evidence record: [dated acceptance matrix](WIDGET_ACCEPTANCE_2026-10-20.md) now includes the API 36 host, build/test results, screenshots, fixed defects and explicit remaining blockers.
- [ ] WG08 acceptance gate: **BLOCKED / release not ready**. API 28, additional API levels/launcher family, multi-instance/remove/resize, en/fr/fa × RTL/theme/font matrix, TalkBack/keyboard, process/restore/replay races and measured performance remain unverified. The full app suite was 166/171 with five failures while preserving existing emulator data; review rejected clearing Luma data for a clean rerun.

Use the plan's task-specific failing-test → implement → passing-test steps. Commit reviewable slices during implementation; do not mark a task complete for compilation alone. Existing audit tasks retain their own acceptance/evidence and must not be silently closed by these checks.

## Execution readiness and evidence

The planning package fixes provider scope, widget interactions, configuration defaults, typed navigation, data contracts, migration/backup boundary, action lifetime, refresh policy, file ownership and acceptance tests. Product choices are documented defaults; they may be revised without treating user research as completed.

Before WG02, current files and pending changes were reread; unrelated release/test artifacts were preserved. Room schema version 5 was available; the additive migration, source tables and exported schema are in place. The AVD retained its app database throughout testing; the review blocked `pm clear` for a clean full-app rerun. The acceptance record distinguishes observed passes from the broader untested matrix.

WG08 now records the base source commit, current debug APK hash, Pixel Launcher/API 36 environment, exact Gradle and instrumentation outcomes, launcher screenshots, regressions found and remaining blockers. Keep the distinction between planned checks, compiled tests, observed passes and explicit blocks. Storage/renderer tests caught and fixed lost Undo feedback and stale RemoteViews child rows; the final app unit/build/lint gate passed. API, launcher, accessibility, lifecycle and performance acceptance remains open.
