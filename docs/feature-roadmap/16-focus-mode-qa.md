# F16 — Focus mode QA scenarios

Date: 30 October 2026 · Status: **FM01 DOMAIN SUBSET PASS; FM02 ROOM/REPOSITORY SUBSET PASS; FM03 SCOPED API36 SUBSET PASS; FULL MATRIX/INTEGRATED CASES PENDING** · Priority: **P1 (proposed)**

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

## Execution ledger

| Case | Initial result | Current result | Actual run / evidence / blocker |
| --- | --- | --- | --- |
| F16-QA01 | NOT RUN | PARTIAL | FM03 connected checks cover Today/More, system Back, widget intent queue and explicit post-pause open. Other Luma paths, gestures, system interruptions, drafts and the full guard matrix remain unrun; see [FM03 evidence](evidence/2026-10-30/F16/fm03-qa.md). |
| F16-QA02 | NOT RUN | PARTIAL | FM01 transition tests, FM02 exact timing/command replay/stale checkpoint/two-client tests, and FM03 coordinator sample/action host tests pass. Integrated write races and ambiguous-ack fault injection remain unrun; see [FM02](evidence/2026-10-30/F16/fm02-room-qa.md) and [FM03](evidence/2026-10-30/F16/fm03-qa.md). |
| F16-QA03 | NOT RUN | PARTIAL | FM01/FM02 recovery checks and FM03 recreation plus changed-process-identity simulation pass. Actual process death, force-stop, sleep and OS lifecycle recovery remain unrun. |
| F16-QA04 | NOT RUN | PARTIAL | FM01 drift/recovery assertions, FM02 changed-boot checkpoint handling and FM03 emergency-marker checkpoint recovery pass. Native clock/reboot conditions and injected database/preference failures remain unrun. |
| F16-QA05 | NOT RUN | PARTIAL | FM04 host/API36.1 checks pass for preference persistence, duplicate-label identity, stale removal, current-selection/launchability recheck, checkpoint-before-launch, retry after injected failure and nonselected rejection. Real installed fixture launch/return, disabled/uninstalled target, multiple actual activities and work-profile boundary remain unrun; see [FM04 evidence](evidence/2026-10-30/F16/fm04-qa.md). |
| F16-QA06 | NOT RUN | BLOCKED | FM01 half-open overlap, midnight, and DST assertions pass; Room/report/UI verification requires FM02/FM05. |
| F16-QA07 | NOT RUN | BLOCKED | FM01 cross-week overlap assertions pass; Review ranges and source drill-down require FM05. |
| F16-QA08 | NOT RUN | BLOCKED | FM01 comparison baseline/rounding assertions pass; Today/Review empty states and report sources require FM05. |
| F16-QA09 | NOT RUN | BLOCKED | FM02 stable 50-row paging, range clipping, zero-duration exclusion, cascade deletion and tombstone replay protection pass. Report UI, confirmation and concurrent report invalidation require FM05. |
| F16-QA10 | NOT RUN | NOT RUN | Backup/legacy restore integration requires FM06. |
| F16-QA11 | NOT RUN | BLOCKED | FM02 stale-generation rejection, receipt eviction and tombstone replay protection pass. Restore/reset orchestration and stale-write races require FM06. |
| F16-QA12 | NOT RUN | PARTIAL | FM03 navigation/lifecycle checks and FM04 host + connected settings/retry assertions pass; FM04 lint exits 0. Existing-feature connected regressions, all entry paths, drafts and restore races remain unrun. See [FM03](evidence/2026-10-30/F16/fm03-qa.md) and [FM04](evidence/2026-10-30/F16/fm04-qa.md). |
| F16-QA13 | NOT RUN | PARTIAL | FM04 settings Compose assertions pass on API36.1 and changed FM04 production files have no lint warnings. API28, TalkBack/keyboard/predictive Back, EN/FR/FA, theme/window/font/orientation matrix remains unrun. |
| F16-QA14 | NOT RUN | NOT RUN | Room/query and representative-device performance work requires FM02/FM05. |
| F16-QA15 | NOT RUN | NOT RUN | FM08 scope/capability assessment has not run. |

The FM01 host-domain subset is complete. F16 acceptance remains pending because the integrated cases and remaining tasks have not passed.
