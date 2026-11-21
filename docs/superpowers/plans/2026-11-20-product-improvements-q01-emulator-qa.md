# IP-Q01 — Integrated emulator QA plan

**Status** NOT RUN · **Depends on** IP-I01–IP-I09 source handoffs

[QA specification](../specs/2026-11-20-product-improvements-q01-emulator-qa-design.md) · [Task index](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md) · [Shared delivery workflow](../../feature-roadmap/WORKFLOW.md)

## Run checklist

- [ ] Record every source handoff and choose the exact candidate and pre-IP-I03 baseline. Capture Git SHA and any dirty patch identity. Use a reproducible snapshot or exact baseline APK plus its build provenance. Use isolation for baseline builds and the app under test.
- [ ] Verify actual APK/app/runner IDs and use disposable AVD data. Record AVD model/image, API, locale, timezone, calendar/week start, theme, viewport and font scale. Clone/snapshot QA fixtures before testing destructive or cross-install cases.
- [ ] Map QA-01–QA-09 to the owning specifications, extend existing tests for uncovered risks, then run the QA-only commands below. Use the existing built-in Compose, Room, planner and lifecycle tests and record actual commands, exit codes and results.
- [ ] Execute the pre-change draft baseline and the candidate emulator cases. Exercise the [required UI/platform matrix](../../feature-roadmap/WORKFLOW.md#required-uiplatform-matrix) for the affected app surfaces. In particular test EN/FR/FA with actual RTL, System/Light/Dark, 2.0 font scale and narrow/wide/landscape viewports, plus TalkBack, hardware keyboard, visible IME, system insets and 48 dp touch targets. Add direct-entry and relock checks for affected protected flows.
- [ ] Triage failures against the baseline. Correct reproducible in-scope defects in IP-Q01 and rerun the owning cases plus affected cross-feature cases. If a defect changes a feature contract or requires a new persistence/format design, reopen the owning handoff and dependents (IP-I05 and IP-I09 for draft changes) before affected QA resumes. Record any reason required evidence cannot be obtained.
- [ ] Create one dated report with revision/patch, APK identity and checksums, fixture metadata, case-to-requirement evidence, defects/fixes/retests and actual acceptance. Mark every case individually. Keep release acceptance pending for any required missing or failed evidence.

## QA-only commands

Run serially after isolation verification. Use the repository wrapper and current configured variant. The connected tasks run on the verified isolated AVD, with the API 28 and modern-device coverage recorded for applicable cases.

```sh
bash gradlew -PdomainOnly=true :core:domain:test
bash gradlew :app:testDebugUnitTest :core:data:testDebugUnitTest
bash gradlew :app:assembleDebug :app:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest :app:lintDebug :core:data:lintDebug
bash gradlew :app:connectedDebugAndroidTest :core:data:connectedDebugAndroidTest
```

Expected command gate is exit 0, zero unexplained skips/tests missing, zero failed required tests and zero lint errors/fatals. Inspect generated schema artifacts only if schema-affecting work was needed and confirm the existing migration chain. A successful build leaves manual scenarios pending until their evidence is captured.

## Emulator scenarios

### QA-01

**Today action hub** · [IP-I01 requirements](../specs/2026-11-20-product-improvements-i01-today-action-hub-design.md#requirements) · **NOT RUN**

**Actions** Create 57 open tasks matching Today and the current 50-row page, plus at least one task each due later, undated and completed. Add a habit with one log, a prior day and a paused Focus session. Inspect Today, Load more, full Tasks, Habits and return from a historical day. Complete a parent task, cancel once, then confirm and Undo. Repeat across midnight.

**Expected** Initially 7 unloaded, then no unloaded count after loading the remaining rows. Loaded rows outside the viewport remain scrollable. Due today, overdue and undated are distinct in Tasks; no date is changed by navigation. History has explicit date and return control; authorized habits open the chosen day. Family confirmation, error/retry and Undo preserve identity. Protected task/habit/Focus details remain gated.

### QA-02

**Backup and recovery** · [IP-I02 requirements](../specs/2026-11-20-product-improvements-i02-backup-and-recovery-design.md#requirements) · **NOT RUN**

**Actions** On A create a portable fixture with multilingual text, photos, task hierarchy, habits and terminal Focus. Export plaintext and encrypted using explicit format choice; confirm exact password handling with 11/12 code points and leading/trailing spaces. Import both plus every supported legacy form on B without A’s app keys. Inspect preview, cancel, re-authenticate, then confirm. Repeat with wrong password, tampered header/middle/end, truncation, unknown versions, bounded-storage failure and interrupted read/write or failed provider close. Exercise running/paused Focus export choices and App Lock forgotten-credential disclosure.

**Expected** Copy identifies confidentiality, password loss, erase-only App Lock recovery, replacement and exclusions. Preview appears only after valid input; successful restore preserves IDs/relations and photo hashes. All cancellations and rejected inputs preserve a sentinel local record before commit. No private title/password in headers, saved state, logs or evidence. Staging cleans up after failure/restart and stale routes refresh after restore. Record KDF/envelope/library/version and measured cost, cross-install compatibility fixtures, and supported provider cleanup behavior.

### QA-03

**Dependable capture drafts** · [IP-I03 requirements](../specs/2026-11-20-product-improvements-i03-capture-drafts-design.md#requirements) · **NOT RUN**

**Actions** Install and record a pre-IP-I03 baseline on an isolated AVD. In both builds create/edit dated Mood and Memory drafts with optional fields and photos. Rotate, change locale, cancel photo picker, then press Home, terminate only the background process (for example with `adb shell am kill <verified-qa-app-id>`), verify the PID changed and reopen from Recents. Inject save and storage errors where reproducible, retry, race a stale Mood, direct Memory and promoted-marker edit against an update or replacement, and test discard and relock.

**Expected** Fields, date and valid photo order return on supported paths. Missing temp media gives feedback. Saved claims follow actual persistence/commit, retry creates only one record, concurrent edits or replacement are preserved with conflict feedback, and discard removes exclusively owned draft media. A lock/relock reveals no protected draft fields. Record cleared-data or task-removal tests separately when used to probe limits.

### QA-04

**Reminder readiness** · [IP-I04 requirements](../specs/2026-11-20-product-improvements-i04-reminder-readiness-design.md#requirements) · **NOT RUN**

**Actions** On the modern AVD set up automatic timed task, habit reminder and Focus invitation. On Android 13+ test runtime denial and revocation through Settings as app-level blocked states. Test independent channel blocking with app access granted. Save in each state, repair via the offered system control, return and check status. Set overnight quiet hours and exercise a missing/ineligible slot. Request a generic test notification. Test independent app-wide blocking on API 28 and try a stale real reminder action after an edit.

**Expected** Each reason and repair action is accurate and refreshes on return. Saved schedule survives blocked transport. Displayed eligibility agrees with existing planner across quiet hours and context. Test post/blocked feedback makes no promise of seeing it, adds no alarm, includes no protected details and uses the existing channel. Stale actions and protected previews continue following app policy.

### QA-05

**First use and capture terminology** · [IP-I05 requirements](../specs/2026-11-20-product-improvements-i05-first-use-capture-design.md#requirements) · **NOT RUN**

**Actions** On empty data in EN/FR/FA, use the capture sheet and relevant empty actions to choose Mood, Memory and Task. Save Mood with only score and Memory with only description plus their normal timestamp defaults. Exercise optional controls across collapse/retry. From a seeded legacy Journal create/remove its Memory marker and reopen the original.

**Expected** Chooser describes each outcome and routes to the intended dated composer. Save requires only the existing inputs; optional selections survive. Reachable creation uses Memory wording. Legacy identity and access remain, and removing a marker leaves its source intact.

### QA-06

**Findable and understandable Search** · [IP-I06 requirements](../specs/2026-11-20-product-improvements-i06-search-entry-points-design.md#requirements) · **NOT RUN**

**Actions** Seed enough multilingual events to paginate. Include title matches, body-only tokens, linked tag/person/place matches, protected entries and distinctive task titles. Open Search from Today, Timeline and Memory Library. Set filters, open a lower result and Back, remove a chip and clear all filters, exit, reopen from another origin, relock and retry an injected load failure.

**Expected** Fresh launches start empty; detail Back restores authorized criteria/pages/viewport. Clear-all filters preserves text. Search matches verified fields only; task titles use their own route. Empty library, no matches, invalid dates and load errors are distinct. Relock/redaction and stale responses cannot expose protected results.

### QA-07

**Habit dates, counts and schedule changes** · [IP-I07 requirements](../specs/2026-11-20-product-improvements-i07-habit-date-explanations-design.md#requirements) · **NOT RUN**

**Actions** Use a 2-of-3 habit whose tracking zone differs from the device and crosses midnight while its editor is open. Edit target/weekdays, test a save at the tracking-zone boundary, then compare historical and future dates. Correct a log to another valid day, try a future or DST-invalid time, then Undo. Inspect Today, direct capture, detail/history, and paused, archived and unscheduled rows. Smoke-test existing launcher habit capture.

**Expected** Affected dates and differing tracking zone are shown where relevant. Correction distinguishes original and target. A changed effective date prevents a write until the refreshed date is confirmed. The effective date shown agrees with the stored schedule version and earlier days retain their original target. Ineligible days do not read as failed targets. Existing correction, retry and Undo behavior holds.

### QA-08

**Focus boundaries, recovery and completion** · [IP-I08 requirements](../specs/2026-11-20-product-improvements-i08-focus-boundaries-design.md#requirements) · **NOT RUN**

**Actions** Start Focus with a linked task, use Android app switching and the in-app navigation guard, then leave via pause/end/emergency. Simulate process interruption and reboot/clock change using supported QA fixtures; inspect confirmed credit. Finish or expire, start/end a break, then explicitly visit a linked task. Exercise linked open, completed, deleted, protected and family tasks.

**Expected** Setup explains the in-app scope and safe exit. Recovery explains credited vs uncertain time, never silently adds ambiguous time, and break time adds zero credit. Ending never changes a task. Only explicit authorized interaction reaches completion, with family confirmation and Undo. Missing/protected tasks reveal no stale detail.

### QA-09

**Weekly Review reflection loop** · [IP-I09 requirements](../specs/2026-11-20-product-improvements-i09-weekly-reflection-design.md#requirements) · **NOT RUN**

**Actions** Open sparse, true-zero, unavailable and dense partial-week data. Change week-start and calendar system, then browse historical Review. Open an authorized source and return. From What helped? open Memory and save on the displayed current civil date. Repeat through App Lock, trigger a manual Memory retry, and recreate the process before saving. From What would you change? open current Tasks and dated Habits. Repeat with protected source groups.

**Expected** Both ranges and relevant coverage are clear when authorized; protected coverage facts are hidden when aggregate access is denied. Missing observations remain distinct from measured zero. Follow-ups display the date/context before navigation and do not alter task due dates or habit schedules. Historical context and new Memory date are distinct, and retry retains the date. A recreated restricted-capture route resumes its existing draft chooser without creating a second draft. Source privacy and neutral, observational copy hold.

## Execution evidence

Create `docs/validation/product-improvements/<actual-run-date>/acceptance.md` on execution and attach current logs, JUnit XML, screenshots, TalkBack observations and record/photo integrity checks where relevant. Use one row per executed requirement/scenario/matrix cell, with fixture and build identities, expected/observed result, actual status and evidence. Mark status **NOT RUN** until execution. Record external product research and physical-device-only findings separately from the emulator result.
