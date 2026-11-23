# IP-Q01 — Integrated emulator QA plan

**Status** BLOCKED — QA-01–QA-03 partial execution; release acceptance pending · **Depends on** IP-I01–IP-I09 source handoffs

[2026-11-23 QA-01–03 follow-up record](../../validation/product-improvements/2026-11-23/acceptance.md) · [2026-11-22 integrated acceptance record](../../validation/product-improvements/2026-11-22/acceptance.md)

[QA specification](../specs/2026-11-20-product-improvements-q01-emulator-qa-design.md) · [Task index](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md) · [Shared delivery workflow](../../feature-roadmap/WORKFLOW.md)

## Run checklist

- [ ] Record every source handoff and choose the exact candidate and pre-IP-I03 baseline. Capture Git SHA and any dirty patch identity. Use a reproducible snapshot or exact baseline APK plus its build provenance. Use isolation for baseline builds and the app under test.
- [ ] Verify actual APK/app/runner IDs and use disposable AVD data. Record AVD model/image, API, locale, timezone, calendar/week start, theme, viewport and font scale. Clone/snapshot QA fixtures before testing destructive or cross-install cases.
- [x] Follow the [repository QA workflow](../../../AGENTS.md#qa-workflow-required). Map QA-01–QA-09 to the owning requirements and existing tests. Run targeted JVM tests for logic. Record commands, exit codes and results.
- [ ] Execute the isolated pre-change draft baseline before the candidate runtime checks. Reuse running isolated AVDs and installed builds when their identities and configurations match. Seed a known state for each scenario and wait for specific UI states or events.
- [ ] Run targeted candidate Compose tests for app behavior. Add coverage for required risks that existing tests miss, then complete the broader gates below. Focused QA-01–QA-03 evidence is recorded in the 2026-11-23 follow-up; full app/data/domain JVM gates, app/data assemble/lint gates and the full core:data connected suite pass. The committed-candidate app suite reported 29 failures and disconnected before all 325 tests completed. The latest working-tree app-suite attempt timed out while the runner was in `TodoAccessibilitySemanticsTest#manyFailuresDoNotHideTasks`; that isolated case then ended with an instrumentation process crash and no assertion message. Keep the broad connected gate and platform matrix outstanding.
- [ ] Use Compose semantics and stable test tags for app interactions. For uncovered exploratory steps, query only relevant accessibility nodes. Use UI Automator for system dialogs, SAF and launcher interactions where applicable. A test can cover a scenario step when it exercises the same behavior, fixture and configuration and records the expected result.
- [ ] Complete outstanding emulator checks and the [required UI/platform matrix](../../feature-roadmap/WORKFLOW.md#required-uiplatform-matrix) for affected surfaces. Include EN/FR/FA with actual RTL, System/Light/Dark, 2.0 font scale, narrow/wide/landscape viewports, TalkBack observation, hardware keyboard, visible IME, system insets and 48 dp touch targets. Add direct-entry and relock checks for affected protected flows.
- [ ] Triage observed failures against available baseline/source evidence. Correct reproducible in-scope test expectation defects and rerun owning cases and affected cross-feature checks. Focused QA-01 hierarchy, Today action, habit-route, and Todo recovery-identity checks pass; QA-02 credential checks and QA-03 capture-draft checks also pass. Search Clear all was corrected to clear both query and structured filters. The large task-recovery test still crashes the instrumentation process and the broad app connected run remains incomplete; the remaining platform and scenario evidence is pending.
- [x] Create one dated report with revision/patch, APK identity and checksums, fixture metadata, case-to-requirement evidence, defects/fixes/retests and actual acceptance. Mark every case individually. Keep release acceptance pending for required missing evidence.

## QA-only commands

Run serially after isolation verification. Use the repository wrapper and current configured variant. The connected tasks run on the verified isolated AVD, with the API 28 and modern-device coverage recorded for applicable cases.

After the targeted JVM checks and isolated draft baseline, start candidate instrumentation with a relevant test class or method. This example runs QA-02’s backup settings flow. Confirm the test assertions before crediting any requirements.

```sh
bash gradlew :app:connectedProductionDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.backup.BackupSettingsFlowTest
```

Complete the following required gates after focused checks. Use the variant tasks exposed by the candidate.

```sh
bash gradlew -PdomainOnly=true :core:domain:test
bash gradlew :app:testProductionDebugUnitTest :core:data:testDebugUnitTest
bash gradlew :app:assembleProductionDebug :app:assembleProductionDebugAndroidTest :core:data:assembleDebugAndroidTest :app:lintProductionDebug :core:data:lintDebug
bash gradlew :app:connectedProductionDebugAndroidTest :core:data:connectedDebugAndroidTest
```

Expected command gate is exit 0, zero unexplained skips/tests missing, zero failed required tests and zero lint errors/fatals. Inspect generated schema artifacts only if schema-affecting work was needed and confirm the existing migration chain. Record a scenario step as verified only when its expected result has direct evidence from the required fixture and configuration.

## Emulator scenarios

### QA-01

**Today action hub** · [IP-I01 requirements](../specs/2026-11-20-product-improvements-i01-today-action-hub-design.md#requirements) · **BLOCKED — partial execution** · [2026-11-23 follow-up evidence](../../validation/product-improvements/2026-11-23/acceptance.md)

**Actions** Create 57 open tasks matching Today and the current 50-row page, plus at least one task each due later, undated and completed. Add a habit with one log, a prior day and a paused Focus session. Inspect Today, Load more, full Tasks, Habits and return from a historical day. Complete a parent task, cancel once, then confirm and Undo. Repeat across midnight.

**Expected** Initially 7 unloaded, then no unloaded count after loading the remaining rows. Loaded rows outside the viewport remain scrollable. Due today, overdue and undated are distinct in Tasks; no date is changed by navigation. History has explicit date and return control; authorized habits open the chosen day. Family confirmation, error/retry and Undo preserve identity. Protected task/habit/Focus details remain gated.

**2026-11-23 result** IP-I01-R1 passes. R2 is partial: buckets and completion behavior pass, but the Today “View all” route and unchanged due-date invariant remain unverified. R3 is partial: historical return, habit date return, family confirmation/Undo identity, parent hierarchy navigation, and one-session Focus lifecycle pass. The parent hierarchy test initially tapped priority and delete controls while they were offscreen; both are now brought into view before interaction. Latest focused reruns also pass `TodayScreenTest` (9), `HabitsNavigationTest` (2), `HabitCaptureFlowTest#historyPrecedesCollapsedManagementAndDeleteRemainsConfirmed` (1), `CalendarSemanticsTest` (1), and Todo recovery-action identity (1). The separate large-recovery accessibility case crashed the instrumentation process and remains unverified. Hidden-provider behavior, protected-detail gates and a real midnight crossing remain unverified. Keep QA-01 blocked pending those cases and the UI/platform matrix. See the [dated acceptance record](../../validation/product-improvements/2026-11-23/acceptance.md#qa-01-acceptance).

### QA-02

**Backup and recovery** · [IP-I02 requirements](../specs/2026-11-20-product-improvements-i02-backup-and-recovery-design.md#requirements) · **BLOCKED — partial execution**

**Actions** On A create a portable fixture with multilingual text, photos, task hierarchy, habits and terminal Focus. Export plaintext and encrypted using explicit format choice; confirm exact password handling with 11/12 code points and leading/trailing spaces. Import both plus every supported legacy form on B without A’s app keys. Inspect preview, cancel, re-authenticate, then confirm. Repeat with wrong password, tampered header/middle/end, truncation, unknown versions, bounded-storage failure and interrupted read/write or failed provider close. Exercise running/paused Focus export choices and App Lock forgotten-credential disclosure.

**Expected** Copy identifies confidentiality, password loss, erase-only App Lock recovery, replacement and exclusions. Preview appears only after valid input; successful restore preserves IDs/relations and photo hashes. All cancellations and rejected inputs preserve a sentinel local record before commit. No private title/password in headers, saved state, logs or evidence. Staging cleans up after failure/restart and stale routes refresh after restore. Record KDF/envelope/library/version and measured cost, cross-install compatibility fixtures, and supported provider cleanup behavior.

**2026-11-23 result** Partial: the exact 12-code-point passphrase, Unicode/space preservation, encrypted service round trip, wrong/missing-password sentinel preservation, pre-commit preview and confirm-only replacement pass in host and API 36 checks. Backup confirmation UI and the 18-case persistence suite pass; the latest `LockCredentialIntegrationTest` rerun passes 8 tests. Independent-install compatibility, native providers, App Lock disclosure, all corruption/size/interruption cases and a controlled KDF benchmark remain unverified. See the [dated acceptance record](../../validation/product-improvements/2026-11-23/acceptance.md#qa-02-acceptance).

### QA-03

**Dependable capture drafts** · [IP-I03 requirements](../specs/2026-11-20-product-improvements-i03-capture-drafts-design.md#requirements) · **BLOCKED — partial execution**

**Actions** Install and record a pre-IP-I03 baseline on an isolated AVD. In both builds create/edit dated Mood and Memory drafts with optional fields and photos. Rotate, change locale, cancel photo picker, then press Home, terminate only the background process (for example with `adb shell am kill <verified-qa-app-id>`), verify the PID changed and reopen from Recents. Inject save and storage errors where reproducible, retry, race a stale Mood, direct Memory and promoted-marker edit against an update or replacement, and test discard and relock.

**Expected** Fields, date and valid photo order return on supported paths. Missing temp media gives feedback. Saved claims follow actual persistence/commit, retry creates only one record, concurrent edits or replacement are preserved with conflict feedback, and discard removes exclusively owned draft media. A lock/relock reveals no protected draft fields. Record cleared-data or task-removal tests separately when used to probe limits.

**2026-11-23 result** Partial: Mood/Memory survive ActivityScenario recreation; Mood saves, reopens and edits; Memory edits/removes its marker while retaining a legacy Journal body; photo attach/remove and missing-file feedback pass. The latest `PlaceCaptureTest` rerun passes 2 tests after matching the combined place/memory context and current direct Memory editor route. The baseline AVD could not be provisioned because SDK repository access failed. Process death/Recents, locale/picker, failure retry, duplicate/stale conflict, owned-draft discard and relock cases remain unverified. See the [dated acceptance record](../../validation/product-improvements/2026-11-23/acceptance.md#qa-03-acceptance).

### QA-04

**Reminder readiness** · [IP-I04 requirements](../specs/2026-11-20-product-improvements-i04-reminder-readiness-design.md#requirements) · **NOT RUN**

**Actions** On the modern AVD set up automatic timed task, habit reminder and Focus invitation. On Android 13+ test runtime denial and revocation through Settings as app-level blocked states. Test independent channel blocking with app access granted. Save in each state, repair via the offered system control, return and check status. Set overnight quiet hours and exercise a missing/ineligible slot. Request a generic test notification. Test independent app-wide blocking on API 28 and try a stale real reminder action after an edit.

**Expected** Each reason and repair action is accurate and refreshes on return. Saved schedule survives blocked transport. Displayed eligibility agrees with existing planner across quiet hours and context. Test post/blocked feedback makes no promise of seeing it, adds no alarm, includes no protected details and uses the existing channel. Stale actions and protected previews continue following app policy.

### QA-05

**First use and capture terminology** · [IP-I05 requirements](../specs/2026-11-20-product-improvements-i05-first-use-capture-design.md#requirements) · **BLOCKED**

**Actions** On empty data in EN/FR/FA, use the capture sheet and relevant empty actions to choose Mood, Memory and Task. Save Mood with only score and Memory with only description plus their normal timestamp defaults. Exercise optional controls across collapse/retry. From a seeded legacy Journal create/remove its Memory marker and reopen the original.

**Expected** Chooser describes each outcome and routes to the intended dated composer. Save requires only the existing inputs; optional selections survive. Reachable creation uses Memory wording. Legacy identity and access remain, and removing a marker leaves its source intact.

### QA-06

**Findable and understandable Search** · [IP-I06 requirements](../specs/2026-11-20-product-improvements-i06-search-entry-points-design.md#requirements) · **BLOCKED**

**Actions** Seed enough multilingual events to paginate. Include title matches, body-only tokens, linked tag/person/place matches, protected entries and distinctive task titles. Open Search from Today, Timeline and Memory Library. Set filters, open a lower result and Back, remove a chip and clear all filters, exit, reopen from another origin, relock and retry an injected load failure.

**Expected** Fresh launches start empty; detail Back restores authorized criteria/pages/viewport. Clear-all filters preserves text. Search matches verified fields only; task titles use their own route. Empty library, no matches, invalid dates and load errors are distinct. Relock/redaction and stale responses cannot expose protected results.

### QA-07

**Habit dates, counts and schedule changes** · [IP-I07 requirements](../specs/2026-11-20-product-improvements-i07-habit-date-explanations-design.md#requirements) · **NOT RUN**

**Actions** Use a 2-of-3 habit whose tracking zone differs from the device and crosses midnight while its editor is open. Edit target/weekdays, test a save at the tracking-zone boundary, then compare historical and future dates. Correct a log to another valid day, try a future or DST-invalid time, then Undo. Inspect Today, direct capture, detail/history, and paused, archived and unscheduled rows. Smoke-test existing launcher habit capture.

**Expected** Affected dates and differing tracking zone are shown where relevant. Correction distinguishes original and target. A changed effective date prevents a write until the refreshed date is confirmed. The effective date shown agrees with the stored schedule version and earlier days retain their original target. Ineligible days do not read as failed targets. Existing correction, retry and Undo behavior holds.

### QA-08

**Focus boundaries, recovery and completion** · [IP-I08 requirements](../specs/2026-11-20-product-improvements-i08-focus-boundaries-design.md#requirements) · **BLOCKED**

**Actions** Start Focus with a linked task, use Android app switching and the in-app navigation guard, then leave via pause/end/emergency. Simulate process interruption and reboot/clock change using supported QA fixtures; inspect confirmed credit. Finish or expire, start/end a break, then explicitly visit a linked task. Exercise linked open, completed, deleted, protected and family tasks.

**Expected** Setup explains the in-app scope and safe exit. Recovery explains credited vs uncertain time, never silently adds ambiguous time, and break time adds zero credit. Ending never changes a task. Only explicit authorized interaction reaches completion, with family confirmation and Undo. Missing/protected tasks reveal no stale detail.

### QA-09

**Weekly Review reflection loop** · [IP-I09 requirements](../specs/2026-11-20-product-improvements-i09-weekly-reflection-design.md#requirements) · **BLOCKED**

**Actions** Open sparse, true-zero, unavailable and dense partial-week data. Change week-start and calendar system, then browse historical Review. Open an authorized source and return. From What helped? open Memory and save on the displayed current civil date. Repeat through App Lock, trigger a manual Memory retry, and recreate the process before saving. From What would you change? open current Tasks and dated Habits. Repeat with protected source groups.

**Expected** Both ranges and relevant coverage are clear when authorized; protected coverage facts are hidden when aggregate access is denied. Missing observations remain distinct from measured zero. Follow-ups display the date/context before navigation and do not alter task due dates or habit schedules. Historical context and new Memory date are distinct, and retry retains the date. A recreated restricted-capture route resumes its existing draft chooser without creating a second draft. Source privacy and neutral, observational copy hold.

## Execution evidence

Create `docs/validation/product-improvements/<actual-run-date>/acceptance.md` on execution. Keep full logs, JUnit XML and other evidence as artifacts. Include concise failure excerpts and paths in the report. Capture screenshots only at visual checkpoints such as clipping, RTL layout or IME clearance, when requested, or when an image helps diagnose a failure. Prefer local screenshot comparisons for recurring visual checks and inspect failed diffs. Attach TalkBack observations and record/photo integrity checks where relevant. Use one row per executed requirement/scenario/matrix cell, with fixture and build identities, expected/observed result, actual status and evidence. Mark status **NOT RUN** until execution. Record external product research and physical-device-only findings separately from the emulator result.
