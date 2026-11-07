# Focus delivery — tasks and execution index

Date: 2026-11-07 · Status: PLANNING COMPLETE; FD01–FD12 NOT STARTED; Q/E NOT RUN.

**Start here:** [Implementation plan](superpowers/plans/2026-11-07-focus-delivery.md) · [Specification](superpowers/specs/2026-11-07-focus-delivery-design.md)
**After implementation:** [QA plan](feature-roadmap/focus-delivery-qa-2026-11-07.md) · [Emulator/device plan](feature-roadmap/focus-delivery-emulator-2026-11-07.md)
**Input:** [Product review](FOCUS_PRODUCT_REVIEW_2026-11-07.md) · [Previous Focus ledger](FOCUS_INSIGHTS_TASKS.md)

## Execution agreement

The user requested implementation-ready documents covering the product review and unfinished past tasks, with QA/emulator work separated from **all** implementation tasks.

1. Implement FD01–FD12 in source dependency order. Read/edit/source-review only; no test/fixture authoring, Gradle, build, lint, schema generation, APK, emulator or device operation.
2. Mark a completed source task **SOURCE COMPLETE — UNVERIFIED**, never QA PASS or accepted.
3. When **every** FD task is source complete, enter Q01–Q05 for test authoring/repair, host/static checks, builds and artifacts.
4. Then execute E01–E12 for Room/device instrumentation and emulator/manual testing.
5. Q06 reconciles final candidate acceptance after E evidence and any fixes/retests.

No per-task QA gate blocks a later source task once its source dependencies exist. Known validation failures remain in the separate QA backlog. Do not guess fixes or hide possible migration data loss to make a source task appear complete.

This new index consolidates unfinished work without resetting historic evidence or implying already-landed source must be rebuilt. Source versions inspected: **Room 14, archive 11**, proposed additions **Room 15/archive 12**. Existing app flavor: **production**, debug ID suffix **.qa**. Recheck these at FD01; old plan reservations and variant assumptions are obsolete.

Generating this package changes documentation only. Application implementation, test execution, emulator use and release were not performed.

## Implementation task ledger

All checkboxes below track source delivery only. All initial states are NOT STARTED.

| ID | Priority | Source deliverable | Depends on | Review / carried scope | Acceptance / later proof |
| --- | --- | --- | --- | --- | --- |
| FD01 | P0 | Task/provenance and invitation contracts; Room migration source; archive compatibility | Existing Focus source | R1/R4, unfinished FX01 | FD-A01; Q02/Q03, E02/E09 |
| FD02 | P1 | Task editor entry, paged literal-search picker, editable task setup, linked saved totals | FD01 | R4, FX03 task entry | FD-A02; Q02, E02 |
| FD03 | P1 | Independent task completion, terminal unlink, safe task deletion/invalidation | FD02 | R4, remaining FX03/FX09 | FD-A02; Q02/Q03, E03 |
| FD04 | P0 | Dual-section origin privacy, private catalog/query rules, mixed-report suppression | FD01–FD03 | R1/R4, FX07/FX09/FX10 privacy | FD-A03; Q02/Q03, E08 |
| FD05 | P2 carried | Pure invitation planning and local occurrence/config persistence | FD01/FD04 | unfinished FP06/FX08 | FD-A04; Q02/Q03, E06/E07 |
| FD06 | P2 carried | Invitation settings, inexact alarms/actions, protected navigation and reconciliation | FD05 | unfinished FP06/FX08 | FD-A04; Q04, E06/E07 |
| FD07 | P1 | Simpler intention setup, neutral validation, bounded tags, accessible Start and truthful copy | FD02/FD04/FD06 | R1/R2 | FD-A05; Q02/Q05, E01/E10 |
| FD08 | P1 | Saved-result/Done hierarchy, compact break customization and optional reflection | FD03/FD04/FD07 | R3 | FD-A06; Q02, E03/E04 |
| FD09 | P2 | One authorized recent-work card, deterministic query, dirty-draft confirmation and Repeat | FD04/FD07/FD08 | R5 | FD-A07; Q02/Q03, E05/E08 |
| FD10 | P2 | One recent context row in existing weekly Review, in-range positive-run selection | FD09 | R6; preserve FP08 | FD-A08; Q02/Q03, E05/E11 |
| FD11 | P0 | All-entry integration, restore/reset cancellation, generation/privacy and localization sweep | FD01–FD10 | remaining FX01/FX07/FX09/FX10 | FD-A09; Q01–Q05, E01–E12 |
| FD12 | P0 | README/archive docs and accurate source-complete handoff | FD01–FD11 | R1 documentation/release confidence | FD-A09; Q05/Q06 |

- [ ] FD01 source complete
- [ ] FD02 source complete
- [ ] FD03 source complete
- [ ] FD04 source complete
- [ ] FD05 source complete
- [ ] FD06 source complete
- [ ] FD07 source complete
- [ ] FD08 source complete
- [ ] FD09 source complete
- [ ] FD10 source complete
- [ ] FD11 source complete
- [ ] FD12 source complete

Priority represents product/risk value, not permission to reorder source dependencies. Invitations are included because they are unfinished previously specified work, even though the new review ranks basic usability above adding reminders. No new reminder/preset expansion is implied.

## Unfinished past-task reconciliation

Baseline from the existing 7 November ledger; no old source-complete flag means accepted.

| Existing work | Baseline at planning | Forward owner |
| --- | --- | --- |
| FM01–FM07 baseline timer/storage/report/backup | Landed with scoped evidence; full platform/performance/integrated gaps remain | Preserve source; Q/E carried regression, not a duplicate implementation task |
| FM08 device-wide restriction | Deferred and unmet | Remains excluded; no task claims blocking |
| FI01 name/tag rules | Targeted 34/34 domain result recorded | Preserve; Q02 regression on final candidate |
| FI02 metadata/Start identity | Targeted Room/app/emulator passes; broader module not green | FD01 extends identity; Q02/Q03/E02/E09 revalidate |
| FI03 migration/archive | Adapter target passed, stale runner, two Room migration failures | Q01/Q03/Q04/E09 diagnose/repair; FD01 adds forward source compatibility |
| FI04 insights | Targeted result; direct Room observeInsights invalidation gap | Q03 authors coverage; E11 executes |
| FI05 setup | Source delivered; Android layout/lifecycle acceptance incomplete | FD07 refines; E01/E10 verify |
| FI06 history | Source delivered; Room/Compose/access lifecycle acceptance incomplete | FD04/FD11 integrate; E05/E08/E11 |
| FI07 privacy/resources | Source delivered; Android lock/replacement acceptance incomplete | FD04/FD11; Q05/E08/E09/E10 |
| FX01 shared foundation | Timer/break/goal/reflection/correction parts present; task/reminder foundation incomplete | FD01 + FD05; integration FD11 |
| FX02 Repeat | Name/tag/timer/next-step source complete; task-copy missing | FD02/FD09 add task-aware discovery; Q/E carry Repeat checks |
| FX03 task integration | Not started | FD02/FD03/FD04 |
| FX04 timed expiry/recovery | Source complete; narrow 7 Nov smoke/cleanup only | Preserve source; Q02/Q03/E04 carry full lifecycle/races |
| FX05 break | Source complete, unverified | FD08 changes presentation only; Q02/E03/E04 validate |
| FX06 reflection | Source complete, unverified | FD08/FD09 presentation/context; Q02/Q03/E03/E05 |
| FX07 goals/distribution | Landed source; overall task partial on shared foundation | FD04/FD11 close dependencies; Q02/Q03/E11 |
| FX08 invitations | Not started | FD05/FD06 |
| FX09 corrections/tag merge | Metadata/tag paths landed; task unlink and dual access pending | FD03/FD04; Q03/E03/E08 |
| FX10 integrated handoff | Blocked by unfinished dependencies | FD11/FD12 after FD01–FD10 |
| FX-QA | Not run as integrated final phase | Consolidated Q01–Q06 and E01–E12; all old mandatory cases retained |

Do not mark any old task accepted merely because a new task references it. At FD12 add forward links/status reconciliation only; preserve dates/results in historic records.

## Separate QA task ledger

Every row currently NOT RUN. Test/fixture writing belongs here, never in FD tasks.

| ID | Deliverable | Entry |
| --- | --- | --- |
| Q01 | Candidate/tool/version inventory, known-failure baseline and evidence manifest | All FD source complete |
| Q02 | Author/run domain and app state tests, immutable retry/privacy/scheduling rules | Q01 |
| Q03 | Author persistence/archive/invalidation fixtures and tests; run host archive/SQL checks | Q01; coordinate Q02 fixtures |
| Q04 | Full host suites, compile/lint, schema generation, instrumentation build and isolated minified artifact | Q02/Q03 authored |
| Q05 | Resource/permission/query-plan/document checks and E-phase readiness | Q02–Q04 prerequisites pass |
| Q06 | Final candidate evidence reconciliation and acceptance, after emulator phase | E01–E12 evidence and affected reruns |

Known issues explicitly assigned here: migration seeded-journal loss versus fixture diagnosis, stale table inventory, backup Long/String casts, stale archive runner dependency discovery, app instrumentation API drift and direct Room insights invalidation coverage. These are not silently folded into feature source-complete claims.

## Separate emulator/device task ledger

Every row currently NOT RUN; entry is all FD source complete plus Q01–Q05 prerequisites.

| ID | Journey |
| --- | --- |
| E01 | Setup, neutral validation, Start and scope understanding |
| E02 | Task entry/picker/draft handling, conflict, totals, deletion |
| E03 | Saved completion, reflection, break, task completion/unlink/corrections |
| E04 | Timer expiry, screen off/background, pause/resume, process death, force-stop, reboot and uncertainty |
| E05 | Recent work/Repeat, dirty drafts, deleted sources and Review range |
| E06 | Invitation permission/status/actions and reminder-kind isolation |
| E07 | DST, delivery window, system events, Doze and stale notification actions |
| E08 | Cross-section locks, marker absence and relock races |
| E09 | Room migration, legacy/current archives, native pickers, restore/reset and stale generations |
| E10 | EN/FR/FA, RTL, IME, font/window/theme, TalkBack and keyboard |
| E11 | Goals/reports/invalidation, bounded queries, 50k performance and errors |
| E12 | Final integrated journey/regression and isolated minified runtime smoke |

Do not run on a personal installation. Verify actual app/test target isolation and candidate hashes. Coverage requirements and independent expected results live in the emulator plan.

## Post-release and conditional backlog

These are recorded so “unfinished post tasks” are not lost. They are **not** FD implementation dependencies or approved additions; each requires its trigger and a new scoped design.

| ID | Idea | Evidence/decision needed before implementation |
| --- | --- | --- |
| POST01 | Recent-work entry on Today | Idle-setup Recent/Repeat demonstrably helps return to work; confirm Today density and dual-section privacy |
| POST02 | Saved reusable presets | Users repeatedly need multiple stable configurations that Recent/Repeat cannot serve; specify rename/delete/backup |
| POST03 | Dedicated Focus widget | Entry friction persists; design launcher caching, summary defaults, lock-safe names/actions |
| POST04 | Smaller quick-duration option | Usability evidence users struggle to begin; compare a convenience choice without changing custom range |
| POST05 | Richer Review grouping by task/tag | One context item insufficient; agree aggregation and task-privacy semantics first |
| POST06 | Calmer visual progress/reward | Participants want it and it helps without pressure; avoid punitive streaks or attention claims |
| POST07 | Ambient audio | Users identify a need worth playback/content/lifecycle scope |
| POST08 | Strict device-wide blocking | Separate platform/permission/distribution feasibility and product decision; FM08 stays unmet |
| POST09 | Automatic multi-cycle Pomodoro | Explicit demand and a separate auto-start/recovery contract |
| POST10 | Social rooms/leaderboards | Evidence of need plus consent/account/sharing design; no mandatory competition |
| POST11 | Calendar planning/cross-device sync/project hierarchy | Separate product scope, data ownership and local-first/privacy decision |
| POST12 | AI coaching or focus/mood scores | Reliable interpretation and explicit privacy design; no causal claims from timer data |

Separate product research: recruit 6–8 consenting first-time/returning task/study users, including accessibility/RTL needs, to compare start/completion/recent-work flows. Record success/assistance/timing/comprehension and voluntary return-to-work feedback. No production telemetry or invented uplift target. This study is NOT RUN; it is not replaced by technical emulator acceptance.

## Source handoff template

For each FD task record:

- Status: NOT STARTED / IN PROGRESS / BLOCKED / SOURCE COMPLETE — UNVERIFIED.
- Changed source paths and implemented requirement IDs.
- Interface/version decisions and dependent call-site wiring.
- Read-only source review performed; unresolved source dependencies.
- Explicit statement: tests/build/schema generation/emulator NOT RUN in this task.
- Later Q/E owner IDs; no copied historic PASS.

## Completion criteria

Planning package: new specification, implementation plan, this ledger, separate QA plan and separate emulator plan, with dependency/acceptance crosswalks.

Source delivery: every FD source checkbox complete and spec contracts wired. This does not imply tests pass.

Technical acceptance: required Q/E cases and applicable FI/FX/F16 regressions pass on the final candidate, with no unexplained failures or missing mandatory coverage. Report physical-device/platform limits and product research separately. Device-wide enforcement remains outside scope.
