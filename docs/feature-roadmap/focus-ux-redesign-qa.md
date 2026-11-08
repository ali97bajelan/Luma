# Focus UX redesign — consolidated emulator QA protocol

Status: NOT RUN. This is the protocol, not evidence of passing tests.
Prerequisites: [spec](../superpowers/specs/2026-11-08-focus-ux-redesign.md), [plan](../superpowers/plans/2026-11-08-focus-ux-redesign.md), [tracker](../FOCUS_UX_REDESIGN_TASKS.md).
Execute only after I01–I10 and G1 are complete. This single QA phase covers the entire redesign, including previously unverified audit branches.

## Campaign setup

1. Record implementation commit, working-tree changes, build variant, Android API/device image, resolution/density, locale/font scale, device time/zone and permission state.
2. Use a disposable QA emulator/profile and synthetic names/tasks. Preserve user emulator data; create a separate AVD or supported test fixture rather than clearing an existing install.
3. Use productionDebug / com.luma.lifeos.qa unless the current build configuration says otherwise.
4. Prepare two datasets: clean install/onboarding and populated synthetic history with named/tagged/task-origin/short/timed/interrupted sessions, an open task, pending goals and reminders. Record how each is seeded. Keep fixtures isolated across instrumentation and manual checks.
5. Use existing fake repositories/clocks and Android test fixtures for hard-to-reach write/checkpoint/generation failures. Do not corrupt real databases, change wall clock on a shared emulator, or introduce release fault switches.
6. Deploy through Android Studio. Capture baseline screen and logcat. Store screenshots under docs/feature-roadmap/evidence/2026-11-08/focus-ux-redesign/ (or actual campaign date).
7. Create docs/feature-roadmap/focus-ux-redesign-qa-report.md with the template below. Never prefill PASS.

## Connected automation batch

Execute the focused suites after deployment/build prerequisites are ready. A representative command for the inspected variant is:

    ./gradlew :app:connectedProductionDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.ui.focus.FocusScreenTest,com.luma.lifeos.ui.focus.FocusLifecycleTest,com.luma.lifeos.ui.focus.FocusReportsTest,com.luma.lifeos.ui.focus.FocusAllowedAppsTest,com.luma.lifeos.focus.FocusRuntimeRecoveryTest,com.luma.lifeos.focus.FocusShortcutHandoffTest

Also include changed navigation/instrumentation suites from implementation and any new Focus tests. Use the current IDE-discovered task if variant names change. Record exact suite/class list, test counts, failures and report path. Do not assume this batch replaces manual matrix checks or OS-level permission/reboot tests.

## Full journey matrix

Each row contains multiple subchecks. Record each subcheck result in the report; a row passes only when every required subcheck passes. Use Pass / Fail / Blocked / Not run. Explain any Not applicable decision with code/platform evidence.

| ID | Preconditions and steps | Expected result | Spec / implementation |
|---|---|---|---|
| Q01 First use and entry | Clean QA profile; complete app onboarding; enter from Today and More. Inspect blank name; enter valid name; open History/Settings and return; compare running/paused Today entries later. | No mandatory task/tag/goal/notification setup; required helper and disabled reason clear; draft survives return; header destinations work in both hosts; state-aware CTA never auto-starts/resumes. | S01,S02 / I02–I04 |
| Q02 Timer setup/expiry | Try count-up, timed presets 15/25/50, Custom, switching modes with authored duration, invalid empty/0/181 and valid 1/180; localized digits; IME Done; run a one-minute target to natural expiry and stop a second timed session early. | Mode change preserves duration, explicit preset changes it; validation correct; IME does not start; explicit Start creates once; target/early-end outcomes distinct; exact target credit and saved acknowledgement. | S02,S04 / I02,I03,I06 |
| Q03 Task selection | No open tasks; search with no match then match; select/change/remove; pagination; Back without selection; simulate task deletion/change and delayed selection after user edits; locked tasks. | Optional task source; preserved draft; results/states distinct; no late overwrite; missing association explained; protected task names hidden; task completion never implicit. | S02 / I03 |
| Q04 Running/pause/Back | Start count-up; pause/resume; Back; dismiss by Keep focusing/outside/system Back; Pause and leave; reopen; End and save; double taps while busy; inject failed leave write. | Distinct action bounds; dismissal leaves timer unchanged; pause stops credit; resume accurate; leave waits for successful command; failed save remains recoverable; one session/command; Today active credit separate. | S01,S03,S08 / I01,I04,I05 |
| Q05 Completion/reflection | End session; inspect primary hierarchy; add/edit outcome/next step, save, cancel, navigate with unsaved edits, cancel discard, discard, save-and-leave with failure/retry; linked task complete/unlink success/failure; repeat/new. | Done only filled primary on summary; correct duration/outcome; no false saved state or lost edits; task action separate; repeat fills setup and confirms replacing draft; New session uses defaults. | S04 / I06 |
| Q06 Break | Start five-minute break; customize valid 1/60 and invalid blank/0/61; Cancel; leave/reopen; end; run a one-minute break to expiry away from app; try new/repeat session during active break. | Break dominates active view; leave behavior explained/preserved; validation stays editable; saved session unaffected; explicit transition for existing runtime rule; no automatic new focus session; expired copy/actions correct. | S05 / I07 |
| Q07 Reports/history | Expand/collapse progress; select all periods; rotate/visit settings/return; all-history vs Today scope; search/tag/no match/clear; paginate; expand/collapse details; view run breakdown; edit metadata; delete synthetic session/cancel deletion. | Period/draft preserved; local report errors isolated; day scope not silently reset; compact rows, meaningful semantics, correct credit and details; list position retained; confirmed delete updates totals once. | S06 / I08 |
| Q08 Settings | Expand all sections; goal target/day validation, Save/Disable, pending effective Monday/zone; tag create/rename/merge/delete/cancel/conflict; search/select shortcuts, duplicate labels, unavailable selected app/remove, no apps/no matches/discovery failure; edit during running. | Status summaries accurate; goal effective date honored; historical tag impact clear; existing conflict safeguards; shortcut states distinct and restrictions explained; timer not interrupted by app failure. | S07 / I09 |
| Q09 Repeat and entry handoffs | Repeat with authored draft, legacy missing name, deleted source, next step; enter from linked task; reminder token current/stale, draft replacement keep/replace; open reminder while active or on break; exercise pending destination. | No unexpected start/draft loss; stale tokens ignored safely; current work prioritized; one session; next-step context authorized; pending destination resolves through guard. | S01,S02,S04,S08 / I03,I04,I06,I08,I09 |
| Q10 Permissions/privacy/shortcuts | Deny notifications, allow, revoke in Android settings, return; reminder enabled with delivery blocked; cancel unlock; revoke protected Focus/task access while expanded; use app shortcut then return; uninstall shortcut fixture. | Timer usable regardless of notification access; request only at intent; status refreshed; locked differs from empty; no cached protected names/reflections; shortcut failure scoped and retry/manage available. | S01,S03,S07,S08 / I04,I05,I09 |
| Q11 Restore/failure/lifecycle | Loading/load failure/retry; start/save/checkpoint/reflection/report/config writes fail and retry; process kill/relaunch and emulator reboot; deterministic clock discontinuity/generation replacement fixtures; recovery choices, emergency exit marker saved/failed; expired target/break while away. | No Start before restored state known; no duplicate writes/session; checkpoint/uncertainty accurately described; recovery commands preserve existing credit rules; stale UI/actions discarded; safe exit honest; target/break settled without auto-start. | S03–S08 / I05–I09 |
| Q12 Accessibility/layout/locales | TalkBack through setup/dialog/timer/summary/break/history/settings; 200% font plus keyboard; English/Persian RTL+digits/French; light/dark; portrait/landscape and tablet; rotate with draft/progress/detail open; measure contrast and hit regions. | Logical traversal/state labels; transition-only announcements; 48dp targets/no overlap; accessible disabled reasons; no clipping/obscured Start/error; draft/period/scroll preserved; locale-safe formatting; active text contrast meets spec. | S09 / I01–I10 |

## Configuration coverage

Run the full functional matrix on the primary phone emulator. Run Q12 on a tablet emulator and a compact phone/landscape layout, with a representative setup → running → dialog → completion → break → history → settings traversal for each configuration. Include current project-supported minimum API 28 and a target/API 35 image for lifecycle/permission compatibility; unsupported OS permission behavior is explicitly Not applicable with reason, never silently passed.

Use real notification settings and emulator reboot for OS behavior. Deterministic injected tests may prove error/gap credit logic but do not prove real alert delivery. Record which evidence is simulated versus system-level.

## Evidence and defects

For each screenshot: case/subcheck ID, device configuration and short caption. Capture setup disabled/IME, timed mode, leave dialog, paused return, saved summary, active/expired break, filtered/expanded history, settings summaries, denied notification state, recovery/error and RTL/large-font states.

For each failure: expected/actual behavior, exact reproduction, baseline commit, severity, screenshot/log/test report, owning I task and rerun scope. Redact private data. Record crashes/ANRs from logcat.

Fixes remain within this QA campaign. After each fix rebuild/redeploy and rerun the failed case plus adjacent impacted cases. Any shared timing/privacy/navigation change requires the affected matrix rows to rerun. Do not count an old screenshot against a new build without confirming behavior remains applicable.

## Report template

    # Focus UX redesign QA report
    Status: Not run / In progress / Failed / Blocked / Passed
    Baseline implementation commit:
    Final tested commit:
    Variant / app ID:
    G1 local-check evidence:
    Devices / API / resolution / density:
    Locales / font scale / theme / time zone:
    Dataset / fixture reset method:
    Connected command / suites / counts / report links:
    Matrix subcheck results: ID | steps/config | result | evidence | defect
    Defects: ID | severity | owner | fix commit | rerun result
    Unverified/blocked cases and reason:
    Data created/removed and environment restored:
    G2 decision and remaining limitations:

G2 passes only when all required subchecks and automation pass, no blocker/high-impact regression remains, and the final tested commit/evidence is identified. Blocked fault injection, missing AVD or untested permission branches mean incomplete sign-off, not a pass. No redesign implementation or QA is performed by creating this protocol.
