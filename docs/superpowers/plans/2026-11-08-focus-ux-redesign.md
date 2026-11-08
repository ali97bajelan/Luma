# Focus UX Redesign Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Track the checkboxes. Follow the user's required phase order: all implementation first, then one consolidated emulator QA campaign.

**Goal:** Deliver the Focus redesign while preserving timer accounting, privacy, configuration and session lifecycle behavior.

**Architecture:** Update existing Compose presentation and callbacks around the current FocusScreenState and FocusSetupState. Keep domain/repository commands, guarded Back, recovery and generation checks. Reuse current routes; expand progress inline.

**Tech Stack:** Kotlin, Jetpack Compose/Material 3, current Luma components, lifecycle-aware state, existing Gradle/JUnit/Android instrumentation.

**Spec:** [Focus UX implementation specification](../specs/2026-11-08-focus-ux-redesign.md).
**Tracker:** [Implementation and QA tasks](../../FOCUS_UX_REDESIGN_TASKS.md).
**QA:** [Consolidated emulator protocol](../../feature-roadmap/focus-ux-redesign-qa.md).

## Global constraints

- Complete I01–I10 before any redesign emulator/deploy/connected test execution.
- Build/static analysis/local JVM checks are permitted during implementation; Android test sources may be written and compiled now, executed only in Q01.
- Count-up remains default; name remains required; timed 1–180 minutes; custom break 1–60 minutes; default break five minutes.
- IME Done dismisses keyboard; explicit Start is the only session submission.
- Keep guarded Back, pending destinations, privacy/task-origin/generation checks and one-active-session rules.
- No dependency additions, schema migration, top-level tab, compulsory onboarding, analytics or background-Back experiment.
- Preserve existing user changes; do not clear existing emulator data or interfere with an independently running user test.
- All localized UI copy belongs in values, values-fa and values-fr strings.xml.
- Work through Android Studio studio_ tools; shell/file operations follow workspace instructions.

## Review focus

1. Authored draft plus delayed task/repeat result: preserve subsequent user edits; cover in I03 tests and Q03/Q09.
2. Pending write plus double tap or navigation: one command/session and no false saved state; cover in I05/I06 and Q04/Q11.
3. Privacy loss/database generation change while details are expanded: remove protected content and stale commands; cover in I08/I09 and Q10/Q11.
4. Font/locale growth plus IME: non-overlapping controls and reachable action/error; implement in I10, execute Q12.
5. Timed/break expiry while away: accurate credit and no implicit new session; preserve runtime tests in I07, execute Q02/Q06/Q11.

## Paths and interfaces

Production prefix P = app/src/main/java/com/luma/lifeos/.
UI tests prefix A = app/src/androidTest/java/com/luma/lifeos/.
JVM tests prefix U = app/src/test/java/com/luma/lifeos/.
Resource prefix R = app/src/main/res/.

Existing interfaces to preserve: FocusSetupState.normalizedInput: FocusStartInput?, canStart: Boolean; FocusAction.PAUSE/RESUME/STOP; FocusRecoveryChoice.CONTINUE_ELAPSED/END_AT_CHECKPOINT; FocusPeriod.TODAY/THIS_WEEK/ALL_TIME.

New navigation contract: add onOpenSettings: () -> Unit to FocusRoute and FocusScreen, forwarded without creating a route. Wire both LifeOsApp.kt FocusRoute call sites. Keep existing onOpenHistory, onBack, task, pending-destination and unlock callbacks.

Presentation state: use rememberSaveable for progress and section disclosure booleans; key session-specific disclosures to session identity/generation. Retain existing ViewModel ownership of draft/runtime. Do not persist unlocked content in saveable state. Do not change repository interfaces merely to reorder UI.

Read the current file before editing; paths/line positions reflect the audit and may have changed. Preserve established test fixtures and use existing injected repositories/clocks; do not add production fault switches.

## Phase 1 — Implementation only

### I01 — Leave dialog safety (S03)

Files: P/ui/focus/FocusScreen.kt; A/ui/focus/FocusScreenTest.kt; R/values{,-fa,-fr}/strings.xml.
Consumes existing onLeaveAfterAction, onDismissBackPrompt, busy/runtime state; produces corrected presentation around the same callbacks.

- [x] Inspect the back dialog and current lifecycle/UI test fixtures.
- [x] Add/update connected test definitions: leaveActionsHaveDistinctBounds; dismissLeaveKeepsRunning; leaveWaitsForSuccessfulAction. Assert PAUSE or STOP is issued once, navigation waits for success, failure keeps recovery available. Do not execute these yet.
- [x] Replace dialog confirm/dismiss slot arrangement with one non-overlapping vertical action group; use the three S03 labels and accessible minimum targets.
- [x] Route Back/outside dismissal to keep-focusing callback; retain emergency access on errors.
- [x] Compile affected sources; inspect diff for accidental runtime policy changes. Mark I01 implemented, device verification pending.

### I02 — Validation and copy contract (S02,S08,S09)

Files: P/ui/focus/FocusSetupContent.kt, FocusSetupState.kt; U/ui/focus/FocusSetupStateTest.kt; localized strings.
Consumes normalizedInput/canStart and existing state flags; produces explicit helper/disabled-reason presentation without changing normalization.

- [ ] Inspect domain validation and reducer tests; enumerate current blocking states.
- [ ] Extend meaningful local tests only where behavior changes: modeSwitchPreservesCustomMinutes; pendingStartCannotSubmitAgain; invalidDurationCannotStart. Assert 0/181 invalid and 1/180 valid, named count-up valid, unresolved write blocked.
- [ ] Run changed local tests before implementation when adding behavior, recording expected failures; copy-only edits need compilation rather than mirrored tests.
- [ ] Add initial required helper and adjacent first blocking reason. Field errors retain blur/attempt semantics. IME Done hides keyboard without calling Start.
- [ ] Localize all new messages in three locales. Run local checks and compile; defer keyboard/reader checks to Q01.

### I03 — Preparation, optional context and repeat (S02)

Files: P/ui/focus/FocusSetupContent.kt, FocusRecentWorkCard.kt, FocusTaskPicker.kt, FocusSetupState.kt; P/focus/FocusViewModel.kt only if needed; U/ui/focus/FocusSetupStateTest.kt; A/ui/focus/FocusScreenTest.kt.
Consumes I02 guidance, existing change callbacks/task selection/repeat; produces reordered setup using the same start payload.

- [ ] Reorder fields per S02; retain persistent bottom action and inset handling.
- [ ] Consolidate timer controls: Count up/Timed selection; timed-only presets 15/25/50; Custom focuses duration; switching mode never resets authored duration.
- [ ] Put optional task selection after name; selected task shows Change/Remove. Preserve missing/locked/loading/search/no-match/pagination states and draft return.
- [ ] Keep tags optional; compact recent work, rename repeat presentation to Use settings, preserve replacement/legacy/missing-source guidance.
- [ ] Add local reducer regressions where required for delayed task/repeat result not overwriting new edits. Run local tests.
- [ ] Define connected cases for preset selection, task return and repeat confirmation; compile only. Do not run emulator.

### I04 — Entry points and secondary destinations (S01)

Files: P/ui/focus/TodayFocusCard.kt, FocusRoute.kt, FocusScreen.kt; P/ui/LifeOsApp.kt; A/AppNavigationTest.kt, A/ui/focus/FocusScreenTest.kt; strings.
Produces onOpenSettings: () -> Unit contract consumed by I09.

- [ ] Trace existing settings destination and gates in LifeOsApp.kt; use semantic usages to locate both FocusRoute calls.
- [ ] Wire new Settings callback through both hosts; add labeled History/Settings actions or narrow-width overflow on preparation.
- [ ] Keep running/recovery destination guards; history/header shortcuts must not bypass them.
- [ ] Make Today CTA state-aware using S01 labels; keep saved and active credit separate and day-scoped history intact.
- [ ] Define connected navigation cases for both hosts, canceled unlock, active session and pending destination. Compile; review both call sites.

### I05 — Running, paused, help and failure presentation (S03,S08)

Files: P/ui/focus/FocusScreen.kt, FocusRoute.kt; P/focus/FocusViewModel.kt only for missing UI feedback; A/ui/focus/FocusScreenTest.kt, A/focus/FocusShortcutHandoffTest.kt.

- [ ] Prioritize state/name/labeled timer and Pause/Resume + End and save; omit absent tag.
- [ ] Place shortcuts in a disclosure, disambiguating duplicate labels only when needed.
- [ ] Keep emergency help reachable in normal state and directly on failure/recovery; preserve marker warnings.
- [ ] Provide distinct restore/starting/saving/checkpoint/shortcut errors with scoped retry and known-saved explanation. Preserve retry command identity.
- [ ] Extend connected definitions for busy double tap, failed leave, unavailable shortcut, lock and recovery; use deterministic existing fakes. Compile only.
- [ ] Run any changed local guard/coordinator tests. Do not change background counting or Back policy.

### I06 — Saved summary and optional follow-up (S04)

Files: P/ui/focus/FocusCompletionContent.kt, FocusReflectionEditor.kt, FocusTaskCompletionAction.kt; A/ui/focus/FocusReportsTest.kt, FocusScreenTest.kt.

- [ ] Make Done the only filled primary action on saved summary; group secondary break/reflection/repeat/new/history.
- [ ] Retain outcome-specific copy and credited duration; move alert caveat to relevant break/alert intent.
- [ ] Preserve explicit reflection Save/Cancel and implement an unsaved-change confirmation when navigation would discard edited text. Cancel keeps edits; discard leaves; save leaves only after successful save.
- [ ] Keep linked task completion/unlink explicit with separate busy/success/error state; preserve stale-session and protected-content guards.
- [ ] Define connected cases for early end/target/interrupted, reflection save failure, canceled discard, task complete/unlink failure and repeat draft replacement. Compile only.

### I07 — Active and expired breaks (S05)

Files: P/ui/focus/FocusCompletionContent.kt, FocusScreen.kt; P/focus/FocusBreakRuntime.kt only if presentation needs require it; A/ui/focus/FocusLifecycleTest.kt, A/focus/FocusRuntimeRecoveryTest.kt.

- [ ] Promote active Break heading/countdown and End break above saved summary; communicate leave behavior.
- [ ] Group five-minute/default/custom choice and retain 1–60 input validation.
- [ ] Present expired break with Done and Use previous settings; preserve no-auto-start invariant.
- [ ] Inspect existing runtime start-during-break rule, expose its consequence before committing an incompatible action, and retain that rule.
- [ ] Define deterministic connected tests for expiry away, leave/return, end/custom invalid input and session start during break. Compile; run applicable local timing tests only.

### I08 — Inline progress and history details (S06)

Files: P/ui/focus/FocusScreen.kt, FocusInsightsCard.kt, FocusHistoryRoute.kt, FocusHistoryViewModel.kt, FocusGoalReport.kt, WeeklyFocusCard.kt; A/ui/focus/FocusReportsTest.kt; U/ui/focus/FocusHistoryFilterStateTest.kt.

- [ ] Replace always-expanded setup insights with compact saved-progress summary and saveable inline expansion/Collapse.
- [ ] Keep period selection separate from history filters and draft; scroll expanded heading into view.
- [ ] Compact rows; put reflection/edit/repeat before disclosed run accounting; add expansion semantics.
- [ ] Preserve day scope, pagination/list position, confirmed delete, exact credit and requested-session navigation.
- [ ] Differentiate empty/filtered-empty/locked/error/missing; clearing filters preserves date range.
- [ ] Add local filter tests for changed behavior and run them; define connected generation/privacy-loss cases and compile only.

### I09 — Settings organization and permission guidance (S07,S08)

Files: P/ui/focus/FocusSettingsScreen.kt, FocusSettingsRoute.kt, FocusGoalSettings.kt, FocusInvitationSettings.kt, FocusTagManager.kt; A/ui/focus/FocusAllowedAppsTest.kt, FocusReportsTest.kt.

- [ ] Group reminders/goals/tags/shortcuts into saveable disclosures with concise current-status summaries.
- [ ] Preserve goal effective Monday/zone/revision rules; expose saved/pending status separately.
- [ ] Label invitations Focus reminders; show schedule versus notification access; request only on Allow notifications and refresh after system settings return.
- [ ] Retain edits on failed saves and existing conflict/merge/delete protections.
- [ ] Preserve profile/running restrictions, unavailable selected apps/remove and separate discovery failure/no matches/empty selection.
- [ ] Define connected cases for goal pending activation, permission denied/revoked, stale reminder token, tag conflicts and shortcut failure. Compile only; do not request device permissions now.

### I10 — Integration, accessibility and local gate (S01–S09)

Files: all changed UI/resources/tests; no broad refactor.
Consumes all implementation tasks; produces QA-ready integrated branch.

- [ ] Inspect every new callback and both navigation hosts for consistency; run a spec-to-task coverage review.
- [ ] Implement heading, timer labels, selection/expanded semantics, disabled-reason accessibility and transition-only announcements.
- [ ] Implement responsive stacking, 48dp non-overlapping targets, theme tokens, IME/scroll handling and locale-safe numbers; preserve saveable state without caching protected data.
- [ ] Ensure connected test definitions cover the review-focus conditions and QA matrix. Add debug/test-only fault fixtures where existing injection is insufficient, with no release surface.
- [ ] Run local verification below; fix failures before G1.
- [ ] Complete G1 tracker, record implementation commit and exact local results; hand off to QA without claiming device pass.

## Local verification commands

Run sequentially via Android Studio build/shell tools, from repository root. Build configuration inspected for this plan has production flavor and debug .qa suffix. If configuration changes, obtain current tasks from IDE rather than guessing a new variant.

1. ./gradlew :core:domain:test :app:testProductionDebugUnitTest
2. ./gradlew :app:assembleProductionDebug :app:assembleProductionDebugAndroidTest
3. ./gradlew :app:lintProductionDebug

Expected: successful tasks, no failed tests, no newly introduced lint errors. Record pre-existing failures separately; unresolved relevant failures block G1. Compiling Android tests does not execute them. Never use connectedAndroidTest or deploy during Phase 1.

## Phase 2 — One consolidated emulator QA campaign

- [ ] Confirm G1 completed for all I tasks.
- [ ] Follow the separate QA protocol Q01–Q12 against one identified implementation baseline.
- [ ] Run connected suites once as a campaign, then manual matrix and accessibility/configuration variants.
- [ ] Record defects; fix and rerun affected coverage within this campaign. Rebuild/redeploy after fixes; record updated commit.
- [ ] Mark G2 only after all required cases pass; blocked cases remain visible.

Do not scatter emulator checkpoints between I tasks. Do not interpret one campaign as permission to skip reruns after a fix. Documentation generation does not begin implementation or QA.
