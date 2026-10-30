# F16 — Focus mode QA scenarios

Date: 30 October 2026 · Status: **FM01 DOMAIN SUBSET PASS; FM02 ROOM/REPOSITORY SUBSET PASS; FM03 SCOPED API36 SUBSET PASS; FM04 SCOPED API36.1 SUBSET PASS; FM05 SCOPED API36.1 SUBSET PASS; FM06 SCOPED API36.1 SUBSET PASS (QA10–11 PARTIAL); FM07 SOURCE REVIEW + SCOPED API36.1 QA PASS (FULL ACCEPTANCE PARTIAL); QA01–13 PARTIAL; QA14 NOT RUN; FULL MATRIX PENDING** · Priority: **P1 (proposed)**

[Tasks](../FOCUS_MODE_TASKS.md) · [Spec](../superpowers/specs/2026-10-28-roadmap-16-focus-mode.md) · [Plan](../superpowers/plans/2026-10-28-roadmap-16-focus-mode.md) · [Roadmap](../FEATURE_ROADMAP_2026-10-26.md) · [Shared workflow](WORKFLOW.md)

## Entry gate and result policy

Read the full specification and shared workflow. Test/fixture authoring, Gradle/schema export/build/lint, native/manual checks and defect corrections are phase 2 work, entered only after the owning FM task's source handoff. All cases were **NOT RUN** at initial handoff; see the execution ledger for current partial results. These are instructions and expected results, not execution evidence.

Use an isolated application/test package, disposable database/photos and dedicated emulator/device. Verify actual package isolation before any install/connected runner, reboot, clock change, reset, restore or force-stop. Do not touch personal Luma data or grant permissions on the user's installation. Record actual SHA/dirty patch hash, APK hash/variant/package, command/exit code, device/API, locale/zone/theme, window/font scale and fixture seed/hash. Store real evidence under `evidence/<actual-run-date>/F16/`, created when QA runs.

FM03 has a scoped API 36.1 run recorded in [its execution evidence](evidence/2026-10-30/F16/fm03-qa.md). That run covers selected lifecycle, navigation and runtime-recovery assertions only. It does not change the full-case `NOT RUN` status for untested OS lifecycle, fault-injection, API28, accessibility, localization or layout matrix steps below.

For every case capture fixture identity, exact steps and independently inspected expected values plus test logs or screenshots/native observations as appropriate. Statuses: NOT RUN/PASS/FAIL/BLOCKED/N/A. A subset or simulated capability never certifies an unexecuted native case. Deferred strict enforcement is an unresolved scope item, not a PASS for blocking. Defect fixes invalidate affected earlier evidence and require retest.

## Deterministic fixtures

- **Clock A:** frozen date Wednesday **2026-10-28**, viewing zone `Asia/Tehran`, known boot `B1`; raw wall and monotonic samples injected independently.
- **Session A:** 2026-10-28 09:00–09:20 RUNNING, 09:20–09:30 PAUSED, 09:30–09:35 RUNNING, then Stop. Expected active **1,500,000 ms (25 min)**; session span 35 min. Before Stop saved reports exclude it.
- **Session B:** 2026-10-27 23:50–2026-10-28 00:20, one terminal run. Expected **600,000 ms** Oct27, **1,200,000 ms** Oct28, **1,800,000 ms** entire session; daily contributor count 1 each, combined distinct count 1.
- **Review fixture:** terminal current Mon/Tue/Wed contributions **30/35/25 min**; previous Mon/Tue/Wed **20/25/15 min**. At Clock A compare **Oct26–28 = 90 min** with **Oct19–21 = 60 min**, delta **+30 min/+50.0%**, today partial. Do not combine this fixture with A/B unless expected totals are recomputed explicitly. Prior Thu–Sun values are excluded.
- **Boundary fixtures:** `America/New_York` 2026-03-08 and 2026-11-01, 23/25-hour local days with UTC-pinned runs; a run crossing Sunday/Monday; a sub-minute run; zero-duration session; a positive interrupted session; paused/open sessions; exact midnight endpoints. Seed IDs and expected overlap milliseconds independently of production helpers.
- **App fixtures:** two disposable launcher packages with the same display label; one package with multiple launchable activities; a disabled/removed package; a non-launchable package; optional separate-profile app. All app inventory screenshots use synthetic apps only.
- **Scale fixture:** 50,000 terminal sessions with deterministic run rows, at least 51 equal `started_at` values, cross-day/week runs and a known interrupted subset. Preserve unrelated mood/journal/memory/task/habit/photo fixtures for isolation assertions.

## Requirement coverage

| Requirement | Cases | Initial result |
| --- | --- | --- |
| F16-R01 — Durable sessions and transitions | QA01–04, QA09–11 | NOT RUN |
| F16-R02 — Timing/lifecycle/recovery | QA02–04, QA06, QA11–12 | NOT RUN |
| F16-R03 — Guard/settings/allowed apps | QA01, QA03–05, QA12–13, QA15 | NOT RUN |
| F16-R04 — Today/weekly/history | QA06–09, QA12–14 | NOT RUN |
| F16-R05 — Portability/privacy/delivery | QA05, QA10–15 | NOT RUN |

## Scenarios

### F16-QA01 — Entry, default guard and Pause/Stop escape

**Preconditions:** Fresh fixture, no open session, empty selected-app set; saved editor draft and a known destination to queue. FM03 source handoff exists.

**Actions:** Start from Today and separately from More; attempt Back/gesture/hardware Back, primary tabs, More/settings, capture, widget launch into Luma, notification route if available, and a restored non-focus route. Pause, navigate, then Resume. Finally Stop. Exercise Android Home/Recents/shade and an incoming-call/system interruption on a dedicated test device.

**Expected:** Running shows timer/Pause/Stop only and no default app shortcuts. Every Luma entry returns to focus; guidance offers reachable Pause/Stop. Pause commits before releasing navigation and shows Resume banner elsewhere. Only one pending destination is offered after pause/stop; no capture/save/mutation auto-executes. Draft survives. Stop saves positive time once. System exits remain available in personal-phone v1 and return restores focus; UI does not describe them as blocked.

**Evidence:** Native navigation recording/screenshots, route/action assertions and database session IDs. **Initial result: NOT RUN.**

### F16-QA02 — Exact timing, repeated actions and concurrency

**Preconditions:** Session A samples; two windows/controllers; command UUIDs/revisions known.

**Actions:** Start twice rapidly; run 20 min, pause 10, resume 5 and Stop. Replay identical commands, reuse UUID with changed payload, race Pause/Stop/checkpoint, replay an older checkpoint after newer, and issue Stop with sample older than committed checkpoint. Create zero/sub-second sessions. Fail a write after commit but before acknowledgment.

**Expected:** One open session; exact 25 min retained, no pause credit or duplicate rows. Identical retained command is AlreadyApplied; changed payload Invalid; stale actions Conflict and resample. Older checkpoints never overwrite newer; checkpoint does not churn action revision; post-Stop writes cannot reopen. Ambiguous success reconciles before retry. Zero-duration session remains visible but contributes no count/time; positive sub-minute time renders `<1 min` and preserves milliseconds.

**Evidence:** Domain assertions, transaction/race tests and independently queried rows/receipts. **Initial result: NOT RUN.**

### F16-QA03 — Recreation, process death, force-stop and sleep

**Preconditions:** Running session on boot B1 with durable start/checkpoint; no wall drift; separate paused fixture.

**Actions:** Rotate, change language/theme, background for ten minutes, lock/sleep/unlock, and return from selected app. Separately kill process and force-stop/relaunch after ten minutes on same boot. Exercise both recovery choices; cancel/dismiss recovery if supported. Reopen a paused session after process death.

**Expected:** Ordinary same-process resume/recreation keeps session ID and monotonic duration including sleep/background, without UI-tick dependence. A new process with RUNNING state shows explicit “Continue including elapsed time” / “End at last saved checkpoint”; no unobserved gap is silently accepted. Continue includes exact elapsed once; End finalizes INTERRUPTED at checkpoint. Recovery dismissal cannot bypass guard or commit a choice. PAUSED stays paused with no extra credit. No service/wake lock/background polling is required.

**Evidence:** Actual lifecycle/native logs plus injected sample assertions; record actual kill mechanism. **Initial result: NOT RUN.**

### F16-QA04 — Reboot, clock correction and storage/emergency exit

**Preconditions:** Durable run checkpoint at known credited time; isolated device; injectable database and preference-write failures.

**Actions:** Change boot, return unknown boot, regress elapsed clock, and separately shift wall time ±1 hour. Test wall-minus-monotonic drift 1,999/2,000/2,001 ms. Change timezone only. Fail Start, Pause, Stop, checkpoint and emergency-marker writes independently; use emergency exit and relaunch.

**Expected:** Changed/unknown boot/regression/>2,000-ms drift finalizes at last checkpoint, labels INTERRUPTED/omitted gap and never credits shutdown or uncertain time. Exactly 2,000 ms does not cross the threshold. Zone-only change does not interrupt. Paused reboot preserves PAUSED. Failed Start creates no success screen; failed Pause/Stop offers retry without claimed save. Emergency exit releases navigation, attempts independent marker and discloses save failure. A retained marker causes checkpoint-only interruption; loss of both markers triggers new-process recovery choice before accepting time, with pre-exit warning. No trap, negative duration or invented saved Stop.

**Evidence:** Fault-injection assertions plus native reboot/time/exit observations, exact retained rows. **Initial result: NOT RUN.**

### F16-QA05 — Allowed-app selection and safe handoff

**Preconditions:** Disposable app fixtures; no Usage access; notifications denied; empty preference set.

**Actions:** Search/select several apps; relaunch; inspect name collisions/multiple activities, non-launchable apps and profile boundary. Resume focus, launch selected app, return, uninstall/disable it and retry. Remove a selection from another window immediately before launch. Inject selection/checkpoint/launch failures; try opening a nonselected package through the shortcut path.

**Expected:** Empty is valid. Package IDs persist; one row per package, distinguish duplicate labels, own/unlaunchable/other-profile apps omitted. Stale saved selection is unavailable/removable. Latest selection and launchability are checked, handoff is explicit and successful checkpoint precedes launch. Timer continues and returns to same focus screen; failures show retry and keep state. Nonselected shortcut launch is rejected. No Usage/notification grant or QUERY_ALL_PACKAGES is required. Nonselected apps remain launchable through Android in v1; this is a documented limitation, not an enforcement pass.

**Evidence:** Picker/intent assertions and native test-app launches/return; synthetic package list only. **Initial result: NOT RUN.**

### F16-QA06 — Midnight, DST, viewing zone and exact boundaries

**Preconditions:** Session B, DST fixtures, UTC-pinned expected values, one paused gap spanning midnight.

**Actions:** Inspect both local dates and combined range; change viewing zone; inspect 23/25-hour dates, Sunday/Monday split and exact day-start/day-end instants. Stop a session after midnight, then inspect prior day.

**Expected:** Session B splits 10/20 minutes and combined count is one. Pauses contribute zero on either date. All half-open boundary contributions sum to original credited time; no fixed-24-hour assumption or double midnight inclusion. Viewing zone re-buckets while preserving whole-range elapsed total. Prior-day time becomes visible when the session finalizes; historical Start still begins now. Open/paused time stays separate beforehand.

**Evidence:** Domain/Room overlap assertions, independent UTC calculation and localized date screenshots. **Initial result: NOT RUN.**

### F16-QA07 — Partial/completed weeks and source ranges

**Preconditions:** Review fixture isolated from A/B; Clock A; historic complete week and cross-week session.

**Actions:** Select current week, completed week, Sunday and Monday boundaries. Open both current/previous focus sources, inspect every row's contribution and return. Attempt a future week.

**Expected:** Clock A shows Oct26–28 90 min versus Oct19–21 60 min, +30 min/+50.0%, current/today partial. Previous comparison is full matching weekdays, not matched time of day. Completed week uses seven real dates; future week unavailable. Cross-week runs contribute only clipped time and session count is distinct per range. Source routes carry exact period/zone and distinguish contribution from full duration; Back preserves week/scroll.

**Evidence:** Independently calculated report and row assertions, screenshots of bounds/partial copy. **Initial result: NOT RUN.**

### F16-QA08 — Zero baselines, rounding and truthful unavailable state

**Preconditions:** Separate 90/60, 30/60, 0/60, positive/0 and 0/0-minute ranges; 59,999-ms and several fractional-minute runs; failed previous-query fixture.

**Actions:** Load each comparison and Today; retry failed query; accumulate sub-minute runs and compare to rounded-row sums.

**Expected:** +50.0%, −50.0%, −100.0% for first three; positive/zero shows absolute increase and “No focus time recorded in comparison period,” no percent/infinity; zero/zero shows no change without percent. Sum milliseconds before display; positive <60,000 ms is `<1 min`. Successful empty history is zero; errors/loading/previous-query failure are unavailable with retry, never zero or prior-period stale data.

**Evidence:** Formatting/report assertions with raw milliseconds and all UI states. **Initial result: NOT RUN.**

### F16-QA09 — History paging, interruption and deletion

**Preconditions:** >50 history rows with equal timestamps, cross-range runs, interrupted/zero/open/paused records.

**Actions:** Page forward/back, inspect range sources/detail, delete a terminal session with cancel then confirm, try deletion of active/paused session, and race deletion against report read.

**Expected:** Stable `(started_at DESC,id DESC)` ordering with no duplicate/missing rows for an unchanged fixture; newest-page refresh after mutation is explicit. Positive interrupted time counts with recovery indicator; zero time does not increase contributor count. Detail explains pause and credited segments. Only confirmed terminal deletion succeeds, cascades runs, writes tombstone and invalidates totals atomically. Open/paused deletion is rejected. Weekly contributor count is not summed daily counts.

**Evidence:** Page ID lists/database assertions and confirmation/report screenshots. **Initial result: NOT RUN.**

### F16-QA10 — Migration and terminal backup round-trip

**Preconditions:** Populated databases for all supported schema paths; supported archive versions; terminal and paused sessions; malformed archive fixtures.

**Actions:** Upgrade, export, inspect table payloads independently, round-trip, restore each supported legacy archive as replacement. Cancel paused exclusion disclosure; choose Stop to include or continue excluding. Feed duplicate UUIDs, orphan run, invalid state, negative duration, end/duration mismatch and overflow to staged validation.

**Expected:** Existing content/photo hashes survive upgrades. Current archive retains exact terminal sessions/runs and totals; no runtime, receipts/tombstones, boot/monotonic values, open sessions or selected packages leak. Paused exclusion is explicit; no silent finalization. Legacy replacement produces empty focus history. Every malformed payload is rejected before replacement. Imported running/paused state never activates a timer/restriction. Schema/archive versions and legacy adapters match actual exported schema, not proposed fixed numbers.

**Evidence:** Migration/backup tests, schema JSON/diff, synthetic archive inspection and retained-hash comparison. **Initial result: NOT RUN.**

### F16-QA11 — Restore/reset, generation races and old replay

**Preconditions:** Running/paused session, queued checkpoint/action with old database generation, >100 command receipts and a terminal session to delete; disposable restore target.

**Actions:** Attempt replacement/reset, cancel, then explicitly stop/discard/confirm. Complete old queued writes after replacement/reset. Delete a session, evict its Start receipt and replay old Start; replay before/after generation rotation. Reopen app and inspect preferences/runtime/history.

**Expected:** Cancel preserves open session/data. Confirmed replacement/reset cancels work and atomically rotates generation; old-generation commands fail even if cancellation races. Within a generation a tombstone prevents evicted Start from recreating a deleted UUID; receipts survive Stop/deletion independently of active singleton. No resurrected data, restored active timer or accidental allowed-app grant. Reset clears all focus preferences/data; restore follows declared device-local preference policy and reloads all focus observers. Unrelated restore/reset behavior matches existing contract.

**Evidence:** Controlled race/receipt tests and post-restart raw table/preferences inspection. **Initial result: NOT RUN.**

### F16-QA12 — Loading, stale responses and existing-feature regressions

**Preconditions:** Delayed/failing repositories, saved draft, unrelated record/count fixtures; injected restore generation change.

**Actions:** Rapidly switch dates/weeks/zones, rotate during action/load, restore while a report is pending, trigger widget/new intent before open-session load completes, and retry individual failed cards. Browse existing Today summary, Timeline, Calendar, habits, tasks and Review.

**Expected:** Initial authoritative state loads before routing; no focus bypass or underlying private-content flash. Stale responses never appear under new headers; cancellation is not a user error. Drafts and selection survive. Independent focus-card failure leaves other cards usable. Entry/journal/memory counts, mood/usage/activity summaries, Calendar scope and task/habit records remain unchanged. Existing widget action behavior is reconciled without auto-replaying a queued mutation. If app lock exists, authenticate first and retain reachable Pause/Stop per resolved lock integration.

**Evidence:** State/Compose assertions, baseline/candidate counts and native entry observations. **Initial result: NOT RUN.**

### F16-QA13 — Accessibility, localization and platform matrix

**Preconditions:** Actual EN/FR/FA resources and all new states; shared workflow matrix; API28 and API36; notification grant denied.

**Actions:** Execute EN/FR/FA × light/dark × shared small/large/portrait/landscape/font-scale cells for setup, running, paused, recovery, settings, history and both report cards. Observe TalkBack, hardware keyboard, predictive/system Back, IME/insets and a theme/language transition. Navigate settings/app list and long labels.

**Expected:** Actual Persian RTL, localized plural/duration/date/copy, readable contrast and no clipped controls. Pause/Stop/recovery choice/emergency exit remain reachable with visible focus and correct spoken names/state; timer does not produce disruptive per-second TalkBack announcements. Package IDs remain legible in RTL. No notification/Usage permission dependency or broad new manifest permission. Core flows work at minimum API; unsupported optional profile/system behavior is recorded with explicit rationale.

**Evidence:** Per-cell screenshots/native notes, spoken/keyboard observations and permission/manifest inspection. **Initial result: NOT RUN.**

### F16-QA14 — Query cost, paging and idle work

**Preconditions:** 50,000-session scale fixture; named representative hardware and comparable baseline; exact raw sample procedure from shared workflow.

**Actions:** Measure repository query+mapping for Today/current/complete Review and first history page: 5 warmups + 30 runs, cold first-open separately. Inspect Room query plans, database write cadence, navigation/frame behavior and app work while backgrounded/screen-off.

**Expected:** Proposed p95 ≤300 ms aggregate query+mapping and ≤500 ms first history page; raw nearest-rank samples recorded. Indexed overlap queries and 50-row pages avoid whole-history Compose allocation. Database writes are not per-second; visible checkpoints are every 5 seconds plus transitions/lifecycle; UI ticks stop when invisible. No periodic usage polling, service or wake lock. Existing Review performance is reported alongside baseline; emulator numbers alone do not certify phone targets.

**Evidence:** Raw timings/query plans/write traces, device conditions and source/build identity; classify target failures honestly. **Initial result: NOT RUN.**

### F16-QA15 — Strict-enforcement capability and scope decision

**Preconditions:** FM08 assessment handoff and stated target distribution/device ownership. No implicit device provisioning authorization.

**Actions:** Verify recommendation against current official Android lock-task/background-launch/package-visibility docs. Identify actual permission/setup/OEM/distribution constraints and unblocked paths. If separately authorized, test a throwaway implementation on a disposable managed device, including selected apps launching other apps, Home/Recents/shade/deep links, reboot, removed allowlisted package, Pause/Stop and emergency recovery.

**Expected:** Personal-phone navigation/shortcut scope is plainly identified as insufficient for enforced device-wide allowlisting. Screen pinning is not represented as non-exitable DPC lock task mode. A deferred/no-change assessment closes only investigation, leaving original enforcement unmet. A chosen managed/stronger branch has revised spec/plan and separately validated implementation; documents or a spike do not certify shipping enforcement. No personal device is reset/provisioned or new privilege granted by running the documentation review.

**Evidence:** Dated primary-source links, capability table, explicit scope decision; authorized spike artifacts only if actually performed. **Initial result: NOT RUN.**

## FM01 domain QA run — 30 October 2026

The FM01 pure-domain subset passed. The exact command, environment, source/test patch identity, defects and evidence files are recorded in [the run report](evidence/2026-10-30/F16/fm01-domain-qa.md). The focused suite covers transitions, pause exclusion, monotonic continuity/recovery, half-open day/week overlap, DST-sized ranges, deduplication, interrupted/zero-time contribution, and comparison rounding/baselines. At that time the integrated cases were still waiting on FM03; the later FM03 scoped run is recorded below.

Full scenario results remain separate from this component pass. QA01–04, QA06–09 and QA11 are **BLOCKED** pending FM02/FM03/FM05/FM06 as appropriate; see the ledger. QA05, QA10 and QA12–15 remain **NOT RUN**. No connected test, emulator scenario, app build, Room schema/migration, native lifecycle, UI matrix, or performance result is claimed. The API 36.1 emulator was not needed for the pure `core:domain` target.

## FM02 Room/repository QA run — 30 October 2026

The FM02 Room schema and repository subset passed. The v7→v8 migration retained populated journal, photo-reference and task rows; the existing v1→v8 migration test passed. Nine focused focus persistence/migration instrumentation tests passed on the isolated API 36.1 AVD, and all 29 data host tests passed. The complete data-module connected run executed 172 tests: 170 passed and two non-FM02 Todo migration/backup tests failed. Exact commands, candidate identity, package isolation, schema correction and scope limits are recorded in the [FM02 run evidence](evidence/2026-10-30/F16/fm02-room-qa.md).

This FM02 component result does not pass the complete integrated scenarios. FM03 lifecycle/UI now has a scoped API36.1 result; FM05 report surfaces and FM06 restore/reset races remain pending their implementation handoffs.

## FM03 lifecycle/navigation QA run — 30 October 2026

The focused app suite passed 293/293 host tests and 6/6 connected tests on the isolated API 36.1 AVD; `focusQaDebug` lint exited 0 with no warnings in changed FM03 production files. The connected suite covers Today/More entry, widget new-intent queueing, recreation, system Back, Pause/Resume/Stop, explicit changed-process recovery and emergency-marker recovery. It simulates a changed runtime owner identity rather than killing the OS process. See the [FM03 execution evidence](evidence/2026-10-30/F16/fm03-qa.md) for candidate/APK hashes, exact package, test reports, corrected defects and unrun cases.

This is a scoped component pass. Actual process death/force-stop, reboot, sleep, storage-fault injection, API28, predictive Back/keyboard/TalkBack and EN/FR/FA theme/window/font/orientation checks remain unrun.

## FM04 settings and selected-app handoff QA run — 30 October 2026

FM04 passed its scoped host, API 36.1 connected and `focusQaDebug` lint checks. The host suite passed 297/297 tests; 20/20 focused connected tests passed on the requested `Medium_Phone_API_36.1` emulator. Coverage includes empty-default and durable preference behavior, cross-instance changes, package discovery invariants, settings search/selection/stale removal, checkpoint-before-launch, revalidation after a selection change, launch failure/retry, the nonselected-package guard and running-state edit lock. See [FM04 execution evidence](evidence/2026-10-30/F16/fm04-qa.md) for commands and exact limits.

QA05 remains **PARTIAL**: no disposable installed external app was used, so actual external launch/return continuity, disabled target behavior, multiple real activities and work-profile separation were not exercised. QA12/13 remain **PARTIAL**: only scoped Compose/regression assertions ran; API28, accessibility, keyboard/predictive Back and EN/FR/FA theme/window/font/orientation coverage are open. No private package inventory, Usage access or notification permission was collected or granted. The focus shortcuts document their personal-phone scope and do not claim device-wide blocking.

## FM05 reports and history QA run — 30 October 2026

FM05 passed its scoped host, API 36.1 connected and lint checks. The app host suite passed 301/301 tests and the data host suite passed 29/29. Three focused FM05 Compose tests and three focused Room persistence/history tests passed on `Medium_Phone_API_36.1`. The checks cover selected-day saved totals versus a running session, partial-week display, no percentage for an empty comparison baseline, clipped cross-midnight/cross-week totals, stable 50-row pages, full duration versus range contribution, credited run detail, cancel/confirm deletion and report invalidation. EN/FR/FA resources parse, and `git diff --check` passes.

The app QA variant is isolated as `com.luma.lifeos.focusqa.qa` (instrumentation package `com.luma.lifeos.focusqa.qa.test`). Candidate base, source/test tree identity and APK SHA-256 are recorded in [FM05 execution evidence](evidence/2026-10-30/F16/fm05-qa.md). App and data lint exited 0. The app lint report contains project-wide warnings but no findings in the changed FM05 focus source files; the data lint report has no issues.

Additional host-screen regression checks passed `TodayPersistenceTest` 2/2 and `WeeklyReviewFlowTest` 16/16 when run by class. `AppNavigationTest` passed 8/9; `dataWidgetCaptureUsesOneCurrentTimeChooserFromEitherProvider` failed during `ActivityScenario` teardown (the activity stayed PAUSED). The teardown failure reproduced with FM05's `focusRepository` argument temporarily omitted, so it is not caused by the report repository wiring. The combined 27-test invocation also had two failures in Today/Review that did not reproduce when those classes ran independently. Logs: [combined run](evidence/2026-10-30/F16/fm05-app-integration-regression.log), [isolated Today](evidence/2026-10-30/F16/fm05-today-integration-retest.log), [isolated Review](evidence/2026-10-30/F16/fm05-review-layout-retest.log), [isolated navigation](evidence/2026-10-30/F16/fm05-navigation-integration-retest.log), and [wiring isolation](evidence/2026-10-30/F16/fm05-navigation-wiring-isolation.log).

QA06–09 now have exercised report/history subsets but remain **PARTIAL**: the emulator run did not execute the full timezone and exact-boundary fixture, all specified week/source/back-stack cases, every comparison rounding/unavailable state, interrupted/open/paused deletion rejection, or concurrent report/deletion race. QA12/13 remain **PARTIAL** because locale, visual, accessibility, API 28 and window/font/orientation matrices were not run. QA14 performance remains **NOT RUN**; the 50,000-session, 5-warm-up/30-measurement target is still open. See [FM05 execution evidence](evidence/2026-10-30/F16/fm05-qa.md) and the linked raw logs.

## FM06 backup, restore and reset QA run — 30 October 2026

FM06 passed scoped host, connected and lint checks on the isolated `focusQaDebug` variant and `Medium_Phone_API_36.1` emulator: 302/302 app host tests, 30/30 data host tests, 4/4 backup UI tests, 24/24 backup/focus persistence tests; app and data lint exited 0. The manual emulator pass exported while a focus session was paused, inspected the export disclosure, restored a synthetic archive while a paused session was open, canceled and repeated restore with explicit Discard, verified no session returned, then used the app's Android data reset and relaunched into an empty Today view. The reset closed the app as requested by Android. The isolated QA package was added alongside the existing personal packages; those packages remained installed and were not targeted.

QA caught two regressions and their fixes. `confirmRestore { ... }` had changed Kotlin trailing-lambda binding when the second callback was added, causing failure completion to call the success callback; a compatibility overload restores the original call behavior. The active restore dialog placed Cancel beside the two vertically stacked destructive actions and its button overlapped Discard; Cancel now stacks below them. The API36.1 Compose bounds regression passed after the fix. Exact commands, candidate/APK hashes, manual screenshots and logs are in [FM06 execution evidence](evidence/2026-10-30/F16/fm06-qa.md).

QA10–11 are **PARTIAL**, not full acceptance. The run does not cover every archive version or malformed fixture, photo/hash preservation, stop-and-include export, queued old-generation writes racing a real restore/reset, reset refusal/reconciliation, every receipt replay case or the full platform/accessibility/locale matrix. QA12/13 remain partial; QA14 performance remains not run. F16 integrated acceptance, FM07 and FM08 remain open.

## FM07 integrated source review and QA — 30 October 2026

FM07 completed a source/resource/manifest review and fixed five defects covering repeated guarded navigation, retry sample/generation stability, history-delete generation authority, seven local-day Review bars, and unfiltered session history access. Final app host suites passed 304/304 in both debug and release; domain/data host runs passed 157/30. Focused app lifecycle/runtime/screen/report regressions passed 13/13 and focused Room/migration/report/backup tests passed 29/29 on isolated `focusQaDebug`. Debug lint, minified `focusQaRelease` assembly and an isolated minified Start/Stop/history smoke passed. The full connected app suite remains unresolved after interruption at 83/262 with ten observed failures. The evidence records package IDs, hashes, commands, run limits and screenshots in [FM07 execution evidence](evidence/2026-10-30/F16/fm07-qa.md).

QA caught a stale `today_focus` selector in `FocusLifecycleTest` after FM05 changed the Today action to `today_focus_open`; updating the selector made both lifecycle tests pass and the full selected Focus app set passed on retest. The complete 262-test app instrumentation run was interrupted after 83 tests and ten observed failures, including the known `AppNavigationTest` ActivityScenario teardown and failures in Journal/Memory/Mood/People/Place/Search/Calendar coverage. It is a partial run, not a regression pass. The R8 minifier emitted 300 Kotlin metadata parser compatibility warnings despite a successful build and smoke launch.

QA01–13 remain **PARTIAL** because native process/clock cases, real external-app launch/return, the required API28 and locale/theme/window/font/accessibility matrix were not completed. QA14 remains **NOT RUN**: no 50,000-session fixture or representative-device 5-warmup/30-sample measurement was collected. Full F16 acceptance and QA14 are still pending; FM08 device-wide enforcement remains separate and unresolved.

## Execution ledger

| Case | Initial result | Current result | Actual run / evidence / blocker |
| --- | --- | --- | --- |
| F16-QA01 | NOT RUN | PARTIAL | FM03 and FM07 cover Today/More, widget intent, repeated Quick Capture guard interception, system Back, Pause/Resume/Stop and explicit post-pause navigation. Other Luma routes, gestures, system interruptions, drafts and the full guard matrix remain unrun; see [FM07 evidence](evidence/2026-10-30/F16/fm07-qa.md). |
| F16-QA02 | NOT RUN | PARTIAL | FM01–03 tests plus FM07 host and connected suites pass for transitions, persistence and reports. Commit-then-lost-ack retry passes with a changed time sample and simulated generation boundary; integrated cross-window write races remain unrun; see [FM07](evidence/2026-10-30/F16/fm07-qa.md). |
| F16-QA03 | NOT RUN | PARTIAL | FM01/FM02 recovery checks and FM03/FM07 recreation plus changed-process-identity simulation pass. Actual process death, force-stop, sleep and OS lifecycle recovery remain unrun. |
| F16-QA04 | NOT RUN | PARTIAL | FM01 drift/recovery assertions, FM02 changed-boot checkpoint handling and FM03 emergency-marker checkpoint recovery pass. Native clock/reboot conditions and injected database/preference failures remain unrun. |
| F16-QA05 | NOT RUN | PARTIAL | FM04/FM07 host and API36.1 checks pass for preference persistence, package discovery, current-selection/launchability recheck, checkpoint-before-launch and retry. Real installed fixture launch/return, disabled/uninstalled target, multiple activities and work-profile boundary remain unrun; see [FM07 evidence](evidence/2026-10-30/F16/fm07-qa.md). |
| F16-QA06 | NOT RUN | PARTIAL | FM01 DST/day arithmetic and FM05 Room cross-midnight clipping plus Today saved/open separation pass in scoped tests. Full zone-change, exact endpoint and localized-device scenarios remain unrun; see [FM05 evidence](evidence/2026-10-30/F16/fm05-qa.md). |
| F16-QA07 | NOT RUN | PARTIAL | FM01 cross-week overlap, FM05 clipped current/previous Room totals and partial-week/empty-baseline Review presentation pass. FM07 Review now shows seven local-day buckets and leaves future dates unavailable. Full deterministic weekday fixture, both source drill-downs and back-stack preservation remain unrun; see [FM07](evidence/2026-10-30/F16/fm07-qa.md). |
| F16-QA08 | NOT RUN | PARTIAL | FM01 comparison rounding, FM05 50.0% host comparison, and API 36.1 empty-previous UI rule pass. Remaining rounding cases and all loading/error/unavailable UI states require the full fixture matrix; see [FM05 evidence](evidence/2026-10-30/F16/fm05-qa.md). |
| F16-QA09 | NOT RUN | PARTIAL | FM02 persistence/deletion invariants plus FM05 stable 50-row pages, clipped/full history duration, run detail, cancel/confirm and aggregate invalidation pass. FM07 adds an unfiltered history route from non-running Focus states and binds delete authority to the displayed page generation; the minified smoke found the saved row. Dedicated zero/paused/interrupted fixtures and concurrent report/delete race remain unrun; see [FM07](evidence/2026-10-30/F16/fm07-qa.md). |
| F16-QA10 | NOT RUN | PARTIAL | FM06 schema-v7 terminal round-trip and FM07 focused migration/backup suites pass. Every legacy/malformed payload, stop-and-include export, archive/photo hash comparison and migration path remain open; see [FM07 evidence](evidence/2026-10-30/F16/fm07-qa.md). |
| F16-QA11 | NOT RUN | PARTIAL | FM06 restore/reset checks and FM07 focused backup/focus persistence suites pass. Retry identity remains stable across a simulated generation change, and history delete uses the displayed page generation. Old queued checkpoint/action races across replacement/reset, full receipt-eviction replay and reset refusal/resume remain open; see [FM06](evidence/2026-10-30/F16/fm06-qa.md) and [FM07](evidence/2026-10-30/F16/fm07-qa.md). |
| F16-QA12 | NOT RUN | PARTIAL | FM07 focused Focus app tests pass 13/13; debug and release app host suites pass 304/304 each. The full app connected suite was interrupted at 83/262 after ten observed failures and was not rerun; all entry paths, drafts and restore races remain open. See [FM07 evidence](evidence/2026-10-30/F16/fm07-qa.md). |
| F16-QA13 | NOT RUN | PARTIAL | FM07 observed one English/light/portrait/normal-font API36.1 cell; lint has no findings in Focus production files. API28, TalkBack/keyboard/predictive Back and EN/FR/FA theme/window/font/orientation matrix remain unrun. |
| F16-QA14 | NOT RUN | NOT RUN | No 50,000-row fixture or representative-device performance measurements were run; FM07 records this as NOT RUN. |
| F16-QA15 | NOT RUN | NOT RUN | FM08 scope/capability assessment has not run. |

The FM01 host-domain subset is complete. F16 acceptance remains pending because the integrated cases and remaining tasks have not passed.
