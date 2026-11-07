# Focus names, tags, and insights tasks

Date: 2026-11-07 · Status: **FI01 and FI02 targeted QA complete; FI02 module-wide data instrumentation remains not green; FI03–FI07 Android/Room acceptance remains pending**

[Specification](superpowers/specs/2026-11-03-focus-names-tags-insights-design.md) · [Implementation plan](superpowers/plans/2026-11-03-focus-names-tags-insights.md)

These extend F16 rather than replacing its existing task status. P0 scope assumes one optional tag per session. Product acceptance remains pending; a source test pass is not Android integration acceptance.

| ID | Priority | Deliverable | Depends on | Acceptance | State |
| --- | --- | --- | --- | --- | --- |
| FI01 | P0 | Name/tag rules, metadata models and test fixtures | — | FI-A01 | DONE (targeted QA: 34/34 focus-domain tests) |
| FI02 | P0 | Transactional metadata persistence, command identity and caller updates | FI01 | FI-A02 | TARGETED QA PASS (7/7 Room persistence; 13/13 app unit; emulator flow); data module suite not green (see QA log) |
| FI03 | P0 | Room migration, portable backup/legacy adapters and privacy format docs | FI02 | FI-A03 | DONE (source checks; Room validation pending) |
| FI04 | P0 | Range-correct tag totals, average and longest-session insight source | FI01, FI02 | FI-A04 | DONE (source checks; Room observation/runtime validation pending) |
| FI05 | P0 | Setup fields, retained drafts, metadata display and focus insight cards | FI02, FI04 | FI-A05 | DONE (source checks; Android UI/lifecycle acceptance pending) |
| FI06 | P0 | Name search and tag-filtered history with stable paging | FI02, FI04 | FI-A06 | DONE (source checks; Room/Compose validation pending) |
| FI07 | P0 | Privacy/resource review and code-only verification evidence | FI03, FI05, FI06 | FI-A07 | DONE (source checks; Android access/lifecycle acceptance pending) |

### FI01–FI02 targeted QA — 2026-11-07

- **FI01 — PASS:** `python3 scripts/run_focus_domain_tests.py` — 34/34 JUnit tests.
- **FI02 — targeted PASS:** `:core:data:connectedDebugAndroidTest --offline -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.data.focus.FocusPersistenceTest` — 7/7 instrumentation tests, including persisted name/tag across pause/resume/stop and database reopen, replay/changed-metadata identity, duplicate tag-row checks, and metadata preservation after stale-generation rejection. `:app:testDebugUnitTest --offline --tests com.luma.lifeos.focus.FocusCoordinatorTest --tests com.luma.lifeos.ui.focus.FocusSetupStateTest` — 13/13 tests.
- **Emulator walkthrough — PASS (`emulator-5554`):** created a temporary named/tagged session, verified metadata in running, paused, resumed, saved, history, and detail screens; confirmed both credited runs; deleted the temporary session and verified the original three history rows remained.
- **Broader data suite — NOT GREEN:** `:core:data:connectedDebugAndroidTest --offline` reported 148 passed and 31 failed. Failures include `BackupValidation` Long/String casts and `FocusMigrationTest` schema expectation assertions. This does not negate the focused FI-A02 result, but module-wide Room/migration acceptance is still pending.
- Updated the [Room persistence tests](../core/data/src/androidTest/kotlin/com/luma/lifeos/data/focus/FocusPersistenceTest.kt) with direct tag-count and stale-generation metadata assertions. Updated the [backup persistence fixture](../core/data/src/androidTest/kotlin/com/luma/lifeos/data/backup/BackupPersistenceTest.kt) to pass valid metadata while checking stale-generation rejection.

## Suggestions outside this implementation

### Product review — 2026-11-04

**Recommendation:** prioritize easier starts and meaningful task progress, then support a sustainable routine. More time charts alone will not close the gap between deciding what to do and doing it.

This is a proposed product backlog, not approved implementation scope. It replaces the earlier short suggestion list; FI01–FI07 priorities and acceptance rules remain unchanged. FI01/FI02 states were updated with targeted QA evidence on 2026-11-07 (see the QA log above). Task linking and timed sessions explicitly extend beyond the current insights specification's exclusions and need their own specification before implementation.

**Working audience assumption:** individuals using Luma privately to study, work or make progress on personal tasks. There is no user research, adoption baseline or retention evidence supplied here. Value and effort rankings below are product hypotheses based on the current source and feature fit, not measured demand or engineering estimates.

### What the current experience already offers

| Area | Current evidence | Product implication |
| --- | --- | --- |
| Session execution | Count-up timer; Pause/Resume/Stop; recovery and emergency exit; selected-app shortcuts. | Preserve dependable timing and reachable exit controls. A timed mode should be optional. |
| Session identity | Required name, one optional tag, recent tag suggestions and retained setup drafts. | Re-entering the same name remains friction; reusable setup is a natural next step. |
| Saved reports | Today totals, weekly comparison and daily bars; period total/count/average, longest contribution and tag breakdown. | Reuse existing calculations. A second seven-day chart is not a major new feature. |
| History | Name search, tag filters, paged session detail and confirmed deletion; metadata is immutable after Start. | Findability exists; correcting classification and restarting similar work are the next useful actions. |
| Wider Luma | Tasks, habits, moods, Timeline and weekly Review already exist. | Connecting focus to an existing task is a stronger fit than building another task manager inside Focus. |
| Delivery confidence | FI02 runtime verification and broader Room/UI/platform acceptance remain pending. | Validate the existing experience before releasing an expanded one. Source completion does not establish product acceptance. |

Source review: [focus screen](../app/src/main/java/com/luma/lifeos/ui/focus/FocusScreen.kt), [weekly focus card](../app/src/main/java/com/luma/lifeos/ui/focus/WeeklyFocusCard.kt), [focus settings](../app/src/main/java/com/luma/lifeos/ui/focus/FocusSettingsScreen.kt), [session model](../core/domain/src/main/kotlin/com/luma/lifeos/domain/focus/FocusSession.kt), [task model](../core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoModels.kt), and [F16 delivery ledger](FOCUS_MODE_TASKS.md). These are source/document findings, not a hands-on usability review. The older README's “not implemented” Focus description is inconsistent with the current source; use the task ledgers for delivery status.

### Ranked additions

Priority here applies only to future additions: **P1 = next candidates**, **P2 = subsequent candidates**. Rank expresses recommended order; it does not imply every P1 item belongs in one release. Relative effort: S = narrow interaction using existing data; M = new persisted settings/content or cross-feature wiring; L = timer lifecycle or broad data-contract changes. All estimates need engineering review.

| Rank / ID | Priority | Feature | User problem and expected value | Relative effort | Dependency |
| --- | --- | --- | --- | --- | --- |
| 1 / FP01 | P1 | Repeat session, then reusable presets | “I do similar work every day.” Reduce repeated typing and make returning to Focus easier. High expected value, smallest initial scope. | S for repeat; M for presets | Existing metadata/start flow |
| 2 / FP02 | P1 | Start Focus from a task | “What did this session help me finish?” Connect intention, effort and an explicit completion action. High expected value and strong fit with Luma. | L | Task access/privacy rules; linked-record persistence and backup design |
| 3 / FP03 | P1 | Optional timed focus and breaks | “Help me begin a manageable block and know when to rest.” Add structure while preserving count-up. High expected value; higher reliability cost. | L | Timer/recovery design; Android background and alert feasibility |
| 4 / FP04 | P2 | Optional outcome and next step | “What changed, and where should I resume?” Capture progress beyond elapsed time. Medium expected value; complements task linking. | M | Terminal-session metadata and archive extension; task link optional |
| 5 / FP05 | P2 | Flexible weekly goal and consistency | “Am I making room for important work?” Support a user-chosen routine without encouraging longer sessions for their own sake. Medium expected value. | M | Saved-report aggregates; local goal settings |
| 6 / FP06 | P2 | Scheduled invitations to focus | “I intend to focus but forget to start.” Offer an opt-in prompt at a chosen time. Medium expected value; validate repeat usage first. | M | Reminder settings/delivery; optional preset reference |
| 7 / FP07 | P2 | Edit saved names/tags and manage tags | “I labeled that wrong.” Keep history and category reports useful as the record grows. Medium expected value. | M | Revision/generation-safe edits; catalog and archive rules |
| 8 / FP08 | P2 | Actionable weekly review | “What should I change next week?” Add a small summary of distribution and a path back into work. Incremental value; lowest priority because daily bars already exist. | S–M | Existing daily aggregates; goal context optional |

### Minimum scope and acceptance for each addition

**FP01 — Repeat session; presets only after validation.** Add “Repeat” to terminal-session detail and the completion screen. Prefill an editable name and tag in setup, then require explicit Start. Do not reopen the old session or copy its elapsed time. Handle legacy unnamed sessions by requiring a valid name. Respect Focus lock, the singleton open-session rule and database replacement. If repetition proves useful, add user-saved presets with rename/delete; do not silently create a library from every session.

Acceptance: selecting Repeat leaves the old record unchanged, creates no timer until Start, and creates exactly one new session on success even after retries. Suggested measure: setup-to-successful-start time and repeat-to-start conversion among returning users.

**FP02 — Focus on an existing task.** Offer “Focus on this task” from task detail and an optional task picker in setup. Prefill an editable session name; keep the task link separate from tags. First version links one task per session and shows saved focus time on task detail. After Stop, offer an explicit “Mark task complete” action; stopping the timer must never imply task completion. Keep habit linking for a later extension.

Acceptance: task rename/deletion does not delete focus history; unlinking or a missing task has a clear fallback; both task and Focus access rules govern cross-feature labels and totals. Task-derived titles must satisfy Focus's existing name limit before Start. Completing a task and saving focus must have independently recoverable outcomes without duplicate credit. Suggested measure: adoption of task-linked sessions and explicit task completions after those sessions; elapsed time alone is not an outcome measure.

**FP03 — Optional timed mode with a deliberate break.** Keep count-up as the default. Offer editable focus duration with a proposed 25-minute shortcut, plus an optional break with a proposed 5-minute shortcut. These are convenience defaults, not recommended durations for every user. At the target, end and save the focus block at its intended boundary; invite the user to start a break or another block explicitly. Break time never counts as focus. Defer automatic multi-cycle programs.

Acceptance: specify pause behavior, screen-off/background expiry, process death, reboot and delayed alert reconciliation before building. Credit the intended running duration rather than the late alert-delivery time. Never auto-start a new focus block or break, and never make saving depend on notification delivery. Denied notifications leave the timer usable with clear alert limitations. Suggested measure: timed-mode reuse and user-reported usefulness; distinguish target reached, early stop and interruption rather than calling every stopped session a completed block.

**FP04 — Optional reflection after saving.** On the completion screen, offer a skippable outcome such as Made progress / Finished intended work / Got interrupted, plus a short optional “Next step” note. Save the timer first; leaving or skipping the reflection loses no credited time. Show the next step when repeating the session. Do not equate a self-reported outcome with verified task completion.

Acceptance: dismissal, app recreation and failed reflection saves cannot duplicate or roll back the session. Notes follow Focus access, export, restore and deletion rules. Suggested measure: voluntary reflection reuse and usefulness in resuming work; high skip rate is a reason to reduce or remove the prompt.

**FP05 — Optional weekly goal.** Let users choose a weekly saved-time target and preferred focus days, or keep goals off. Show saved progress and days with saved activity; identify in-progress time separately. Prefer neutral consistency feedback over a punitive daily streak. Avoid leaderboards, default targets and “longest is best” messaging.

Acceptance: use existing local-day/ISO-week boundaries and paused-time exclusion; define goal changes and timezone changes explicitly. Do not retroactively label past weeks successful against a newly chosen goal. Suggested measure: repeat weekly use and self-reported manageability among people who opt in, alongside goal-disable rate.

**FP06 — Opt-in focus reminders.** Allow one simple recurring schedule first, with selected weekdays, local time, snooze and disable. Tapping a reminder opens protected setup, optionally with a chosen preset; it never starts a session. Keep notification text generic by default. Reuse the existing reminder settings and permission experience rather than creating a second notification center.

Acceptance: handle denied/revoked permission, disabled channels, timezone changes and duplicate delivery; avoid prompting during an already open session. Notifications reveal no task/name/tag behind a lock. Suggested measure: reminder-to-start conversion and disable/dismiss feedback; do not optimize notification volume.

**FP07 — Correct classification without rewriting time.** Allow terminal-session name/tag edits, plus focus-tag rename/merge after defining collision behavior. Refresh history, suggestions and all affected reports. Preserve single-tag semantics so category totals remain additive. Keep manual duration changes and adding fabricated historical sessions outside this proposal.

Acceptance: edits use revision/generation checks, cannot overwrite a restored database, and round-trip through backup. Tag merges preserve session links and total milliseconds; deletion semantics must be specified before exposing tag deletion. Suggested measure: correction success and feedback about report accuracy; editing frequency alone is not a success target.

**FP08 — Extend the existing Review card.** Add active-day count and highest saved-time day to the current week view, with clear wording that these describe recorded timer activity. Reuse existing day bars and range drill-down; no separate dashboard. Provide “Plan another session” into editable setup. Sparse or empty history should produce neutral guidance, not claims about an optimal focus hour.

Acceptance: partial weeks compare matching weekdays; midnight-spanning sessions split using existing report rules; zero and unavailable remain distinct. Do not infer productivity, distraction or causation from timer duration. Suggested measure: Review-to-next-session use and whether users can explain what the summary means.

### Delivery order and evidence needed

1. **Establish trust:** resolve FI02 runtime verification and the documented Room/UI/privacy/lifecycle gaps before expanded release acceptance. This review does not mark any of them complete and does not authorize running prohibited tooling.
2. **First increment:** FP01 repeat only. Validate whether it reduces setup effort without accidental starts. Avoid bundling presets, reminders and goals before learning whether repeat helps.
3. **Next increment:** FP02 task linking. Evaluate FP03 separately because it changes lifecycle and background timing contracts. If user interviews identify bounded study sessions as the primary need, move FP03 ahead of FP02; that ordering is currently an assumption.
4. **Then:** choose FP04–FP08 based on observed problems. Reflections support resuming work; goals/reminders address routine; corrections address inaccurate categories. Do not ship all three routine features merely to fill a roadmap.

Suggested primary outcome: **people returning to save focus sessions on multiple days in a week**, accompanied by whether they made progress on intended work. This is a proposed evaluation measure, not an existing tracked metric. Longer time and longer sessions are not success by themselves; background running time is not verified attention. Guardrails: accidental starts, save failures, uncertain recovery, unwanted prompts and user-reported pressure.

Before committing the larger items, walk through repeat work, task-based work and bounded study scenarios with representative users. Compare setup effort and comprehension against the current flow, then validate the smallest useful release. Establish a baseline before setting numeric targets; do not invent uplift estimates. Use consenting usability sessions and voluntary feedback first. No network telemetry or new permissions are authorized by this review; any later on-device measurement needs an explicit design and privacy review, and session names/tags/notes must not enter analytics or diagnostic logs.

### Defer deliberately

- **Strict device-wide blocking:** FM08 remains deferred/unmet. Do not imply selected-app shortcuts enforce an Android-wide allowlist. As an optional help item, explain how users can configure their phone's own Digital Wellbeing controls where available; this is user-managed system behavior. Google's [Digital Wellbeing guide](https://support.google.com/android/answer/9346420?hl=en) documents app pausing and scheduling and notes device differences; it does not establish that Luma can control them.
- **AI coaching, inferred productivity scores and mood/focus causation:** no demonstrated need or validated interpretation; deterministic summaries fit the current local-first product better.
- **Social competition, leaderboards and mandatory streaks:** add pressure and account/sharing scope without evidence that they help Luma's audience.
- **Multiple tags, a new project hierarchy, ambient audio and a dedicated Focus widget:** reconsider after the core start-to-progress flow is validated. Multi-tag totals need an allocation policy; task links and one tag are sufficient for the proposed first additions.

External pattern check (2026-11-04): TickTick documents [focus timers](https://help.ticktick.com/articles/7055782010496745472) and [Pomodoro/Stopwatch widget controls](https://help.ticktick.com/articles/7055780404896202752). These support considering structured timing and easier entry as established interaction patterns; they are not evidence of demand, expected retention or feasibility in Luma. The ranking above is a product judgment based on Luma's current gaps.

## Product extension delivery — FP01–FP08

Planning date: 2026-11-04 · Status: **FP01, FP03, FP04, FP05 and FP08 SOURCE COMPLETE — UNVERIFIED; FP07 PARTIAL — METADATA/TAG CORRECTION SOURCE COMPLETE, TASK UNLINK PENDING FX03; FX01 PARTIAL; FX07 PARTIAL (FX01 dependency pending); FX03 and FX08 NOT STARTED; FX09 PARTIAL; FX10 BLOCKED — DEPENDENCIES INCOMPLETE; final QA NOT RUN**

[Extension specification](superpowers/specs/2026-11-04-focus-product-extensions-design.md) · [Extension implementation plan](superpowers/plans/2026-11-04-focus-product-extensions.md) · [Separate final QA](feature-roadmap/focus-product-extensions-qa.md)

The requested spec/plan covers the minimum FP01–FP08 scope. Reusable presets remain deferred; repeat works without them and invitations open ordinary setup. Draft defaults, contracts and acceptance cases live in the specification. FP01 Repeat, the standalone FP03 timed-focus/break slice, the FP05 weekly-goal slice, and the FP08 Review distribution slice are source complete for their current contracts; QA remains deferred to FX-QA.

**Delivery sequence requested by the user:** implement all FX01–FX10 source tasks first, then enter **one separate FX-QA phase to test all new behavior together**. No per-task QA gate or test/build/device execution during implementation. Tests/fixtures, compilation, Room schema generation, lint, platform checks, integrated regression and defect retests belong to FX-QA. Source dependencies still apply; a source-complete task is unverified, not accepted. This sequencing replaces the earlier recommendation to validate between product increments for this extension only.

| ID | Source deliverable | Depends on | Product scope | Implementation | Final QA |
| --- | --- | --- | --- | --- | --- |
| FX01 | Domain contracts, Room migration, command identity, portable archive and legacy adapters | Existing FI source | Shared FP01–FP08 foundation | PARTIAL — timer/break, FP05 goal and FP04 reflection foundation; task/reminder/session-correction contracts pending | NOT RUN |
| FX02 | Editable Repeat prefill and retained setup | Existing FI metadata/start contract; shared FX01 extensions remain prerequisites for later metadata | FP01 | SOURCE COMPLETE — UNVERIFIED (name/tag, timed config and next-step context; task link pending) | NOT RUN |
| FX03 | Task picker/link, saved task totals and independent completion | FX01, FX02 | FP02 | NOT STARTED | NOT RUN |
| FX04 | Timed focus, capped expiry, background wakeup and explicit recovery | FX01 timer/break slice | FP03 | SOURCE COMPLETE — UNVERIFIED | PARTIAL — timer expiry smoke and deleted-session cleanup retest passed; full FX-Q03/Q04 NOT RUN |
| FX05 | Explicit uncredited break countdown | FX04 | FP03 | SOURCE COMPLETE — UNVERIFIED | NOT RUN |
| FX06 | Optional reflection and next-step resumption context | FX01, FX02 | FP04 | SOURCE COMPLETE — UNVERIFIED | NOT RUN |
| FX07 | Versioned weekly goals and actionable Review distribution | FX01, FX06 | FP05, FP08 | PARTIAL — FP05 and FP08 SOURCE COMPLETE — UNVERIFIED; FX01 foundation pending | NOT RUN |
| FX08 | Opt-in scheduled invitations and safe reminder reconciliation | FX01, FX04, FX05 | FP06 | NOT STARTED | NOT RUN |
| FX09 | Session corrections, tag rename/merge and report invalidation | FX01, FX03, FX06 | FP07 | PARTIAL — session name/tag edits and tag rename/merge source complete; task unlink/privacy awaits FX03 | NOT RUN |
| FX10 | Integrated privacy/navigation/restore/reset, localization and source handoff | FX01–FX09 | All FP01–FP08 | BLOCKED — dependency review only; FX01, FX03, FX07, FX08 and FX09 incomplete | NOT RUN |
| FX-QA | Test authoring, all-layer execution, integrated journeys, fixes/retests and evidence | ALL FX01–FX10 source complete | FP-A01–09; FX-Q01–12; applicable FI/FM gaps | NOT STARTED | NOT RUN |

The earlier FI-only source-check restrictions below describe the existing handoff. The new extension has the distinct two-phase workflow above; the final QA document specifies future Gradle/device work after the source-complete gate. At drafting time, no extension tests or device/build tooling had run; the targeted FX04 emulator check is recorded separately below and does not alter the integrated QA gate. Existing FI/FM evidence and unresolved enforcement/platform limitations remain unchanged; no historic PASS carries automatically to the future extension candidate.

**FX10 dependency review (2026-11-06): BLOCKED — NOT SOURCE COMPLETE.** The integrated sweep requires the unfinished task-link/provenance contracts and flow (FX01/FX03), invitation implementation (FX01/FX08), and the remaining FX07/FX09 work. In particular, dual-section privacy and mixed-report suppression cannot be integrated or reviewed against task-origin records that do not exist yet, and invitation cancellation/reconciliation cannot be connected before invitations exist. Existing Focus replacement/reset hooks quiesce Focus writes, end breaks and cancel the current timer alarm; their complete cross-feature behavior still requires the FX10 sweep after dependencies land. No implementation or QA is claimed by this review. FX-QA remains NOT RUN and its entry gate remains closed; FI02 and applicable FI/FM limitations remain carried forward.

**FX04 targeted QA and cleanup retest (2026-11-07): PARTIAL — TIMER EXPIRY SMOKE AND DELETED-SESSION CLEANUP PASS, FULL ACCEPTANCE PENDING.** On the already-installed Luma app on emulator-5554, a synthetic 1-minute timed session expired during a 60-second screen-off wait. On wake, the app showed target reached and saved `01:00`; history showed one run from 11:02:06–11:03:06 and total `01:00`. Initial cleanup exposed a stale completion/reflection load error after deleting that row. The fix now clears the matching cached terminal session when an authorized, stable-generation lookup confirms the row is gone, then returns from Focus. `:app:compileProductionDebugKotlin` passed and Android Studio deployed `com.luma.lifeos.qa` to emulator-5554. A second synthetic count-up session saved `00:55`; after confirmed deletion, backing out returned directly to Today, preserved the pre-existing one-session total, and showed no stale completion/error. Instrumentation compilation remains blocked by API mismatches in unrelated app-lock, Focus UI and widget test sources. Pause/resume, early Stop, process death/force-stop, reboot, clock changes, expiry races, Room validation, and full FX-Q03/Q04 remain NOT RUN. Evidence: [FX04 targeted emulator check](feature-roadmap/evidence/2026-11-07/focus-product-extensions/FX04-targeted-emulator.md). This partial run does not complete FX-QA; integrated cases remain NOT RUN.

**FP01 delivery (2026-11-04): SOURCE COMPLETE — UNVERIFIED.** Repeat reloads the latest terminal session under Focus access and prefills its editable name/tag; a legacy unnamed session requires a valid name, Start another clears the draft, and no timer starts before explicit Start. Completion and terminal history detail both offer Repeat. FX04 and FX06 have since added timed configuration and next-step context to Repeat; task links remain pending with FX03. Source review only; FX-Q01/09 and the rest of FX-QA remain NOT RUN. The broader FX01 schema/archive work remains partial for task, reminder and session-correction contracts.

**Ruling:** FP01 Repeat does not depend on the full FX01 shared foundation because the existing FI session and Start contracts already provide the fields needed for this increment. This narrows the FX02 dependency in the implementation plan; cost if wrong: later metadata work may require revisiting Repeat before those fields ship.

**FP03 delivery (2026-11-04): SOURCE COMPLETE — UNVERIFIED.** Count-up remains the default; timed sessions persist a fixed 1–180 minute target, cap credited focus at the target and settle expiry through Pause/Stop/checkpoint/recovery paths. A best-effort inexact elapsed alarm wakes a repository-authoritative worker; alerts contain generic text. Explicit 1–60 minute breaks persist locally, resume only on the same boot, remain outside focus totals and backup, and can be ended manually. Timer and break controls, recovery/terminal copy and English/French/Persian strings are wired. Source review only; FX-Q03/04/09 and the rest of FX-QA remain NOT RUN. Room schema generation, builds and tests were not run.

**FP04 delivery (2026-11-04): SOURCE COMPLETE — UNVERIFIED.** Terminal sessions support an optional self-reported outcome and a trimmed, single-line next-step note of up to 280 Unicode code points. Reflection saves are independent generation/revision-checked updates; they cannot start a timer, complete a task or change credited time. The completion screen offers the optional prompt after the session is saved, expanded history details allow later edits, and Repeat shows the saved next step as context. Room 13 adds nullable reflection columns; archive 11 includes them and archives 7–10 restore with empty reflection. Focus access gates reflection content, drafts are in-memory only and cleared on access/generation changes, and English/French/Persian labels are present. Source review only; FX-Q05/09 and integrated FX-QA remain NOT RUN. Room migration/schema generation, builds and tests were not run.

**FP05 delivery (2026-11-04): SOURCE COMPLETE — UNVERIFIED.** Weekly goals default off, accept 1–10,080 minutes and 1–7 preferred days, and schedule edits for the strictly next Monday in a captured goal timezone. Goal progress counts saved terminal contributions only, shows active days, and keeps in-progress time separate. Disable hides progress immediately; re-enable starts at a new next-Monday boundary. Goal versions and visibility settings are included in archive 10; command receipts remain device-local. Source review only; FX-Q06/09 and the rest of FX-QA remain NOT RUN. FX07 remains PARTIAL because its FX01 foundation is not complete.

**FP08 delivery (2026-11-04): SOURCE COMPLETE — UNVERIFIED.** The existing Weekly Review card shows active days and the highest saved-time local date within the displayed current range, with earliest-date tie resolution and neutral empty-period wording. Day rows open single-day history. “Plan another session” clears setup only when no session is open and Focus is initialized and idle, then opens Focus; a running or paused session remains authoritative. The existing protected Review visibility gate is retained. English, French and Persian labels are present. Source review only; FX-Q06/11 and integrated FX-QA remain NOT RUN. FX07 remains PARTIAL until its FX01 dependency is complete.

**FP07 delivery (2026-11-05): PARTIAL — METADATA/TAG CORRECTION SOURCE COMPLETE, UNVERIFIED.** Terminal history detail can revise a saved session name/tag with generation, session-revision and tag-catalog-revision checks; legacy null names remain unchanged unless a valid name is supplied. Focus Settings can rename tags and presents a named confirmation before a colliding rename merges the source into the target. Session/run writes are transactional; tag changes bump affected session revisions, keep running timer runtime revisions synchronized, and leave credited runs untouched. Local aliases reconcile stale tag names and history filters; Room 13→14 stores the local catalog revision/aliases, which are discarded on database replacement. Existing archive 11 session/tag fields carry corrected history. English, French and Persian labels are present. **Task-link removal and the required TASKS+FOCUS authorization remain pending FX03**, because the current model has no task link or task-origin fields. FP-A07 and FX09 therefore remain partial; FX-Q08/09/10 and integrated FX-QA remain NOT RUN.

**Ruling:** Deliver the independent name/tag correction and tag-catalog paths now; do not invent task-link or cross-section privacy contracts inside FP07. The checked-out source has no `task_id`/`task_origin` session fields or task-linked Focus flow, and FP07 requires both grants for protected linked records. Cost if this dependency call is wrong: FX09 must be revisited after FX03 before FP-A07 can be source complete.

**Ruling:** This FP03 slice narrows FX04’s planned FX03 prerequisite because task linking is not required for timer credit, expiry, recovery or local breaks. FX01 is only partially complete: timer/break and goal foundations plus FP04 reflection fields, Room 13 migration and archive 11 compatibility are present; task, reminder and session-correction contracts remain pending. Cost if the dependency assessment is wrong: later task-link or shared-contract work may need to reconcile with the landed Start receipt version and archive columns before those features ship.

**Ruling:** Reflection fields use Room 13/archive 11 because the current source already occupies Room 12/archive 10; archives 7–10 retain their existing layouts and receive empty reflection during restore. Cost if wrong: a version collision or archive compatibility mismatch.

## Completion rules

The following rules and evidence apply to the existing **FI01–FI07** implementation. For FX01–FX10 and FX-QA, use the extension phase policy above.

- Keep tasks unchecked until code and specified source tests exist and pass.
- Run standalone Kotlin/JVM tests directly, plus static and archive/SQL fixture checks as applicable.
- Do not invoke Gradle, emulator/device tooling, instrumentation, lint or APK assembly.
- Keep runtime/UI/Room generated-schema validation marked NOT RUN; do not mark end-to-end acceptance PASS from source checks.
- Preserve existing F16 unresolved enforcement and platform QA limitations.

FI01 source verification: `python3 scripts/run_focus_domain_tests.py` passes 23 tests. Android runtime/UI and Room generated-schema validation remain NOT RUN; product acceptance remains pending.

FI02 source progress: required Start input now reaches the repository transaction; session name and normalized tag persist with session/run/runtime/receipt writes, and command payload identity includes normalized metadata. Database version 10 adds the focus tag catalog and nullable session metadata through migration 9→10. Standalone domain tests pass 23 tests. Emulator, Room migration execution, and generated-schema validation remain NOT RUN because `adb`/Android CLI are unavailable in this environment; FI-A02 remains pending runtime verification.

FI03 source verification: `python3 scripts/run_focus_metadata_archive_tests.py` passes 16 JUnit tests, including 6 metadata adapter tests. `python3 scripts/check_focus_metadata_sql.py` passes 4 migration fixtures for RUNNING, PAUSED, FINISHED and INTERRUPTED v9 sessions plus a stale `EXCLUDE_PAUSED` export fixture; it executes the SQL extracted directly from `LumaDatabase.kt` and verifies existing focus rows, indices, foreign keys and receipts survive. Room-generated schema 10 and Room migration execution remain NOT RUN; FI-A03 remains pending Room/runtime validation.

FI04 source verification: the standalone focus-domain plus insight-state suite passes 38/38 JUnit tests, covering range clipping, tag conservation, deterministic longest-session ties, exact HALF_UP rounding, duplicate/conflicting snapshots, overflow, same-query retry, cancellation, stale query/generation rejection, and empty/error separation. A SQLite smoke check of the DAO suggestion query confirms newest-session ordering, normalized-name tie order, omission of unused tags, and the 10-row limit. Room query compilation, invalidation behavior and Android/runtime integration remain NOT RUN; FI-A04 product acceptance remains pending those checks.

FI05 source verification (2026-11-04): `python3 scripts/run_focus_setup_tests.py` passes 17/17 JUnit tests for valid Start enablement, saved-draft recreation, immutable retries, successful/reset clearing, terminal-screen session identity, stale insight suppression/cancellation, period/zone selection and day rollover. `python3 scripts/run_focus_domain_tests.py` passes 34/34 JUnit tests. EN/FR/FA resource XML parity passes across 1,447 named resources, and `git diff --check` passes. Actual Compose layout, IME/RTL/large-font interaction, Android lifecycle and Room integration remain NOT RUN; FI-A05 product acceptance remains pending those checks.

FI06 source verification (2026-11-04): `python3 scripts/run_focus_history_tests.py` passes 44/44 JUnit tests, covering the focus domain plus filter matching, >50-row filtered paging, filter restoration, page-offset reset and stale query/generation rejection. `python3 scripts/check_focus_history_sql.py` executes the DAO candidate SQL against SQLite and confirms 60 terminal range matches, stable batched ordering and half-open range boundaries. EN/FR/FA string key parity passes across 1,434 keys, and `git diff --check` passes. Room query compilation, Compose interaction/lifecycle and relock invalidation of cached history remain NOT RUN; FI-A06 source evidence is complete while product acceptance remains pending those checks.

FI07 source verification (2026-11-04): `python3 scripts/run_focus_setup_tests.py` passes 24/24 tests, including six pure privacy visibility/reducer tests. `python3 scripts/run_focus_history_tests.py` passes 44/44 and includes the extracted pure history UI state. All supported standalone source suites pass: focus domain 34/34, metadata archive 16/16, setup/privacy 24/24, and history 44/44. Focus migration/history SQLite fixtures pass; EN/FR/FA each contain the same 1,434 string keys; focus-document links and `git diff --check` pass. Focus access clears cached setup, insights, suggestions, history rows, and search state on relock; restore generation suppresses stale reports/tag suggestions and resets history. Today and Review reports follow the existing Focus access decision. Compose/app-lock lifecycle, Room migration/schema/invalidation and Android runtime acceptance remain NOT RUN. FI02 command identity and transaction verification remains pending. See [FI07 verification evidence](validation/2026-11-04-focus-insights.md).
