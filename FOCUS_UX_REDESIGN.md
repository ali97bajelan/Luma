# Focus UX redesign specification

Audit date: 2026-11-08. Status: proposal for implementation; application code was not modified.

## A. Executive summary

Focus already supports a substantial, useful workflow: named work, task linking, count-up and timed sessions, pause/resume, durable history, reflection, repeat, breaks, goals, tags, shortcuts, and invitations. The strongest existing qualities are honest timer accounting, explicit saved-duration feedback, optional tagging, a persistent setup action, and a visual language consistent with Today: warm surfaces, serif headings, rounded controls, terracotta actions, and muted green selection.

The experience needs a hierarchy redesign more than new capabilities. Setup mixes preparing work with reviewing past work; completion mixes closure with many next-session decisions. Navigation treats an otherwise background-capable timer as a guarded mode. Requirements and terminology are sometimes implicit. Preserve the timing and privacy machinery while simplifying the presentation.

Highest-value changes:

1. Explain the required session name and make invalid-input recovery explicit.
2. Repair the leave dialog layout and label actions by their consequences.
3. Make session preparation the clear center of Focus; expose history and settings predictably in the header.
4. Establish a single primary action in completion and a distinct break state.
5. Keep recovery, alert limitations, and privacy explanations contextual and actionable.

No evidence here establishes increased retention or improved attention. Those are hypotheses to test, not promises. Focus measures timer time, not concentration, and must continue saying so without making disclaimers the dominant content.

## B. Current experience analysis

### Evidence and limits

Evidence labels throughout: **O** = directly observed on the emulator; **C** = supported by inspected code; **H** = UX hypothesis requiring validation.

Android Studio sync completed successfully; `studio_deploy` built and launched `com.luma.lifeos.qa` on `emulator-5554`. Portrait viewport was 1080 × 2400; English UI; existing populated QA data. The emulator clock displayed approximately 15:32–15:35 during the primary journey. This was not a clean-install onboarding test. No existing records were deleted. The audit added one named `UX audit` session, saved at 36 seconds, and started then ended a five-minute break.

Executed: Today → Focus setup; blank-name disabled action; name entry and draft return; start count-up; Back dialog; Pause-and-leave; Today paused summary; reopen paused session; Stop; saved summary; history list; expand session details; return to completion; start/end break.

Code-reviewed but **not runtime verified**: task picker and task completion, timed expiry, custom break validation, reflection editing/saving, goals, invitations and notification permission, tag administration, app discovery/launch failures, protected-content unlock, runtime loading/failure/recovery and emergency exit. These are explicitly remaining validation work, not passed journeys. App-level onboarding, clean empty history, TalkBack, RTL, French, large fonts, landscape, tablet, reboot/process death, offline/fault injection, and denied permissions were not exercised. Therefore this is a developer-ready proposal with a bounded runtime audit, not exhaustive device certification.

Screenshots were captured and inspected through Android Studio UI-state results for Today, setup, running, and completion. They are session evidence, not persisted image assets in this repository. The following evidence register includes exact visible content and geometry so the findings remain interpretable without those images. No fabricated screenshot links are included.

| Evidence | Screen and observation | Implication |
|---|---|---|
| O1 | Today: “Saved focus: <1 min”, “1 session”, “View sessions for this day”, “Start or resume focus” | Useful aggregate and entry point, but CTA does not identify current state |
| O2 | Setup: intro, Choose a task, blank name, Count up (default)/Timed block, Use 25 minutes, optional tag, recent work, insights; Start focus disabled | Required input is unexplained; several optional choices precede starting |
| O3 | Setup footer: “The timer keeps counting outside Luma. Other apps remain available.” | Correct scope expectation, but repeated explanatory space competes with the task |
| O4 | Running: name, Untagged, large elapsed time, background explanation, Pause/Stop, Emergency exit | Clear controls, but technical escape route looks like a routine third action |
| O5 | Back: “Stay in focus?” / “Pause or stop focus to leave this screen.” | Back unexpectedly becomes a timer decision despite background capability |
| O6 | Dialog Stop bounds [183,1246][897,1372]; Cancel [710,1267][897,1393] | Action rectangles overlap; fix layout before judging visual polish |
| O7 | After Pause-and-leave, Today says “Paused: <1 min · not included in saved totals” | Good accounting explanation, but CTA still says Start or resume focus |
| O8 | Completion: saved 00:36, Done, alert caveat, five-minute break, customize, reflection, repeat, start another, history | Closure and next-step choices have excessive equal visual weight |
| O9 | History: large search/tag controls, rows repeating Untagged and Focus session saved; expansion reveals run timestamps before edit/reflection/repeat/delete | Detail pushes other sessions away; useful actions follow accounting detail |
| O10 | Break appears below Done and saved-session metadata; remaining time “04:53 left in break” replaces break choices | Break succeeds visibly, but current activity remains subordinate to a completed session |

### Architecture and source map

All paths below are relative to the repository root. These are inspected source references; use function names as stable anchors rather than assuming line numbers survive implementation.

- [FocusScreen.kt](app/src/main/java/com/luma/lifeos/ui/focus/FocusScreen.kt): `FocusScreen`, `SetupContent`, `RunningContent`, `PausedContent`, `RecoveryContent`, `FailureContent`; runtime loading → load error → recovery → running → paused → terminal → setup branch priority. `AppPageScaffold` supplies Back; setup has a bottom action; content scrolls. Back and emergency dialogs are here.
- [FocusSetupContent.kt](app/src/main/java/com/luma/lifeos/ui/focus/FocusSetupContent.kt): task selection precedes name; optional tag disclosure and suggestions; count-up default; 25-minute shortcut; timed input 1–180 minutes; `FocusSetupStartAction` disables invalid normalized input; IME Done can submit.
- [FocusRoute.kt](app/src/main/java/com/luma/lifeos/ui/focus/FocusRoute.kt): lifecycle-aware state, app discovery on resume, selected shortcut filtering, protected-content redaction, invitation draft confirmation, runtime callbacks. Discovery exceptions become an empty app list here, unlike explicit failure in settings.
- [FocusCompletionContent.kt](app/src/main/java/com/luma/lifeos/ui/focus/FocusCompletionContent.kt): terminal reason labels, saved duration, Done, break, optional reflection, task completion, repeat/new/history. Custom break accepts 1–60 minutes.
- [FocusHistoryRoute.kt](app/src/main/java/com/luma/lifeos/ui/focus/FocusHistoryRoute.kt): searchable/tag-filtered paginated history, inline expansion, delete confirmation, requested-session route and privacy gates.
- [FocusSettingsScreen.kt](app/src/main/java/com/luma/lifeos/ui/focus/FocusSettingsScreen.kt) and [FocusSettingsRoute.kt](app/src/main/java/com/luma/lifeos/ui/focus/FocusSettingsRoute.kt): invitations, scope/explanation, goals, tags, then searchable shortcut choices. Discovery/loading/write errors and unavailable installed selections exist; shortcut editing disabled during running.
- [FocusGoalSettings.kt](app/src/main/java/com/luma/lifeos/ui/focus/FocusGoalSettings.kt): target/day/time-zone configuration, next-Monday effective date, explicit schedule/disable operations and conflict handling.
- [FocusInvitationSettings.kt](app/src/main/java/com/luma/lifeos/ui/focus/FocusInvitationSettings.kt): enabled state, weekday choices, time picker, notification status/request and save failure.
- [FocusTaskPicker.kt](app/src/main/java/com/luma/lifeos/ui/focus/FocusTaskPicker.kt): authorized open-task search, pagination, loading, no matches, empty and retry. Unauthorized branch provides explanatory text.
- [FocusReflectionEditor.kt](app/src/main/java/com/luma/lifeos/ui/focus/FocusReflectionEditor.kt): optional reflection surface, shared with saved-session workflow.
- [FocusSession.kt](core/domain/src/main/kotlin/com/luma/lifeos/domain/focus/FocusSession.kt): RUNNING/PAUSED/FINISHED/INTERRUPTED, checkpoint credit, task origin, target limit and distinct terminal reasons. Presentation changes must preserve these distinctions.

Related implementation files to consult when changing their surfaces: `focus/FocusViewModel.kt`, `focus/FocusRuntime.kt`, `focus/FocusNavigationGuard.kt`, `ui/focus/FocusSetupState.kt`, `FocusHistoryViewModel.kt`, `FocusInsightsCard.kt`, `FocusTaskCompletionAction.kt`, `FocusTagManager.kt`, `TodayFocusCard.kt`, `WeeklyFocusCard.kt`; all under `app/src/main/java/com/luma/lifeos/`. Their presence was discovered; this audit does not claim full behavioral review of each.

### Evaluation

| Criterion | Assessment grounded in evidence |
|---|---|
| First-time usability | O2: meaningful prompt, but no explicit required-name guidance; H: task button may imply task selection is mandatory |
| Discoverability | C: history follows insights in setup; settings absent from FocusScreen header. H: goals/invitations will be difficult to discover from session intent |
| Cognitive load | O2/O8: setup and closure expose many distinct decisions at once |
| Engagement | O7/O8: useful saved time, repeat and breaks exist; H: less choice and clearer next steps may help return use |
| Motivation | O8: honest time saved; C: reflection/goals can support continuity; avoid scoring attention or penalizing short sessions |
| Navigation | O5/O6: Back changes timer state and dialog action layout conflicts |
| Hierarchy | O4: timer readable; O8: Done and break both filled actions; O9: bookkeeping precedes useful detail actions |
| Feedback | O7/O8/O10: state transitions are visible; C: many retry states exist; H: save/conflict wording needs usability tests |
| Accessibility | O6: overlapping actions; O2: clipped offscreen insight controls flagged NAF in hierarchy, not proof of persistent missing labels; verify after scrolling and with TalkBack |
| Consistency | O1–O4: typography, palette and rounded controls fit Today; improvement should reuse Luma components rather than introduce a new visual theme |

Contrast and target sizes were not instrumentally measured. Large screenshot pixels do not prove dp compliance. Existing 48/52dp minimums on many controls are a strength, not evidence that every control passes.

## C. Proposed user experience

### Direction and tradeoffs

Considered: (1) copy/layout fixes only—low cost, leaves the mixed setup/report structure; (2) focused preparation with secondary progress/settings destinations—recommended, preserves capabilities and reduces competing decisions; (3) a new top-level Focus tab/dashboard—larger navigation impact without evidence it is needed. Recommendation (2) is the auditor's proposal, not an approved product decision.

Keep current global navigation. Within Focus, use a header with Back, title, History and Settings (overflow at narrow widths, with accessible labels). Primary content is preparation or the current session. Progress remains accessible via a compact summary linking to the existing insights/history surfaces. Do not create a mandatory onboarding carousel, account step, app-blocking permission flow, or extra intermediate confirmation screen.

First visit: one short inline explanation, “Make time for one thing. Choose a name, then start a timer.” Preserve a concise scope note: “Tracks time on this device. Other apps stay available.” Show name as required and task linking as an alternative source of the name. Default remains count-up; changing the default requires separate evidence. First session must be possible without tags, goals, reminders, notification permission, or an existing task.

Returning user: same predictable layout; recent work is a compact optional row below preparation. “Use these settings” fills a draft and never starts a timer. Preserve replacement confirmation when the draft contains authored work. Show next-step reflection only when present and authorized. No extra repeat shortcut should bypass validation.

Flow:

```text
Today / More / task / invitation
  → resolve active session, privacy access, recovery and pending draft
  → Focus preparation → Start → Running ↔ Paused
                                      ↓ End and save
                               Saved summary → Done
                                  ↘ optional Break → Break finished
                                  ↘ reflection / task completion
                                  ↘ repeat settings → preparation
History → session detail → edit / reflection / repeat / confirmed delete
Settings → goals / reminders / tags / app shortcuts
```

Keep recovery before normal setup and never create a second active session. Preserve pending-destination resolution and invitation token/draft safeguards.

Navigation choice: ship explicit “Pause and leave”, “End and save”, “Keep focusing” first, preserving the current guard. Separately evaluate allowing ordinary Back while the timer continues, with a persistent return affordance. The latter is a justified product change because the timer already counts outside the app, but requires review of pending destinations, privacy, shortcuts and recovery before rollout. Do not silently change the guard in a layout task.

### State contract

| State | Required presentation and recovery |
|---|---|
| Initial loading | “Restoring your session…” with progress; no enabled Start until runtime is known; explain safe exit |
| Initialization failure | “Couldn't restore Focus.” Retry primary; separate safe-exit action and accurate saved-data warning |
| Blank name | Required helper visible before interaction; Start tap validates/focuses name, or disabled CTA has adjacent explicit reason; implementation must choose one consistent pattern |
| Invalid duration/tag/name | Field-specific message on blur/attempt; keep draft and focus first invalid input; retain current domain limits |
| Start/save pending | “Starting…” / “Saving session…”; prevent duplicate commands; do not display success before durable acknowledgement |
| Save failure | “Couldn't save this change.” Retry same operation, preserve draft/session; explain what time is known saved; emergency action remains available |
| No sessions | “Your sessions will appear here.” Start focus; avoid empty charts or failure styling |
| No search matches | State active filters; Clear search/filters; preserve date range unless explicitly cleared |
| Report load failure | Local retry in report; starting a session remains available if runtime is healthy |
| Locked content | Explain protected Focus/task data and offer unlock; cancellation returns safely; never show redacted data as if it were an empty history |
| Notifications denied | “Timer works; background alerts may be unavailable.” Request only at reminder/alert intent; dismissible path; no timer gate |
| App unavailable | Name affected shortcut; Retry / Manage shortcuts; keep active timer intact |
| Recovery | Explain saved time and uncertain gap; two consequence-labeled choices mapped exactly to existing recovery commands; retain escape route |
| Interrupted end | “Session interrupted” plus saved duration and uncertainty explanation; no target-achieved celebration |
| Target reached | “Time's up — session saved” only after save; show credited duration; alert delivery remains best effort |
| Task missing/changed | Preserve historical session and origin privacy; explain association change; retry/unlink where supported; never silently complete a task |

## D. Screen-by-screen redesign

### 1. Today entry

Current O1/O7: generic start-or-resume text despite known paused state; history precedes main action. Proposed: title, saved total and session count, optional active/paused status, state-specific filled action “Start focus” / “Return to timer” / “Resume focus”; history as secondary link. Resume label should open the paused screen unless a direct-resume behavior is explicitly implemented and communicated. Keep saved totals separate from active time. Rationale: returning users should recognize their current work before choosing an action. Files: `TodayFocusCard.kt`, navigation call sites.

### 2. Focus preparation

Current O2: optional task button precedes required name; duplicate duration affordances; recent work and insights occupy a long column. Proposed hierarchy:

```text
< Focus                       History  Settings
Make time for one thing.
What will you work on? [__________________]
Required · e.g. Read chapter 3
[Choose an existing task]  (optional)
Timer   [Count up] [Timed]
        Until you end it / Duration [25] minutes
        [15] [25] [50] [Custom]  (timed only)
[+ Tag (optional)]
Recent: Read chapter 2       [Use settings]
Today: 35 min saved          [View progress]
----------------------------------------------
Other apps stay available.       [Start focus]
```

Reuse Luma field/buttons and Material single-choice controls. Presets supplement custom 1–180-minute entry and must not silently overwrite authored duration. Omit “default” from the selected label; explain count-up behavior in supporting text. Keep persistent Start with IME/navigation insets; scroll focused input/error into view. IME action should be labeled Start if it submits; otherwise Done only dismisses keyboard. Name remains required in this proposal to preserve current product semantics. Selected task is a compact summary with Change/Remove; edits must follow existing draft/task conflict rules. Tags remain optional disclosure with a selectable catalog; do not introduce a mandatory categorization step. Files: `FocusSetupContent.kt`, `FocusSetupState.kt`, `FocusScreen.kt`.

### 3. Task picker and task-origin entry

Current C: full page, search, open-task pagination, loading/empty/error branches; unauthorized branch has text. Proposed: retain route and search; task title as primary row label, clearly named Select action/row semantics. Empty: “No open tasks. You can still name your focus session.” Back returns to unchanged draft. Locked: explicit unlock route if supported by parent navigation; do not pretend locked tasks are absent. Selected title fills name according to existing state rules; do not overwrite subsequent edits on asynchronous refresh. Completing a timer never completes the linked task automatically. Files: `FocusTaskPicker.kt`, `FocusTaskCompletionAction.kt`, `FocusViewModel.kt`.

### 4. Running and paused

Current O4: large timer is good; “Untagged” and Emergency exit add noise. Proposed: status, work name, elapsed/remaining label and stable large timer; tag only when supplied; Pause/Resume filled, “End and save” outlined. Keep background behavior as short helper. App shortcuts form a secondary expandable section; show app names, package identifiers only for disambiguation. Add active-operation feedback without hiding timer. Keep emergency escape in a labeled secondary menu/help area, always visible in failure/recovery. Do not remove its functionality. Paused says “Paused — time isn't counting”; preserve history access and leave guidance. Files: `FocusScreen.kt`, `FocusRoute.kt`.

### 5. Leave dialog

Current O5/O6: vague Pause/Stop consequences and overlapping action regions. Proposed custom dialog action column with three full-width non-overlapping rows: “Pause and leave”, “End and save”, “Keep focusing”. Body explains that pause preserves work and stops counting. Back/outside dismissal should keep focusing; it must never mutate timer state. Navigate only on successful pause/save. On failure remain in dialog with error and retry; keep emergency route reachable. This is the first P0 layout fix, independent of the larger navigation proposal. File: `FocusScreen.kt` back-prompt block.

### 6. Saved summary and reflection

Current O8: seven follow-up actions, duplicated summary labels, two filled buttons. Proposed:

```text
Session saved
UX audit                    36 seconds
[Done]                         primary
[Take a break]                 secondary
[Add outcome or next step]     optional disclosure
[Use session settings] [New session]
[View sessions]
```

Show linked-task completion only when applicable and authorized; explicit action, success feedback and retry distinct from timer success. Reflection is optional; persistent Save/Cancel, saving/error/saved feedback, no silent loss on navigation. Put alert limitations next to break/alert configuration rather than between saved duration and closure. Preserve distinct target reached, early end and interrupted copy. Repeat opens setup; “New session” opens blank/default setup. No confetti, streak guilt, attention score, or forced rating. Files: `FocusCompletionContent.kt`, `FocusReflectionEditor.kt`, `FocusTaskCompletionAction.kt`.

### 7. Break

Current O10: active break appears beneath Done on saved summary. Proposed: promote “Break” with remaining time to top of the same route while active; compact previous-session saved summary below. Primary “End break”; secondary Done/return behavior explicitly described. Keep 5-minute default with Customize disclosure, custom 1–60 validation and Cancel. Expiry: “Break finished” plus “Use previous settings” and Done; no automatic next session. Starting focus while a break exists must use the existing runtime rule and explain any replacement, never leave two contradictory timers displayed. Files: `FocusCompletionContent.kt`, `FocusScreen.kt`, `FocusBreakRuntime.kt`.

### 8. Progress, history and session details

Current O9/C: search and tag filters, large repeated rows and inline run breakdown. Proposed: compact period summary then history; keep date scope visible, search and filter controls. Rows lead with work name and saved duration, then date/time and meaningful status/tag; omit empty Untagged labels. Use explicit expand/collapse semantics; within detail lead with outcome/next step and edit/repeat, with “Time breakdown” disclosure after them. Delete remains secondary and confirmed, with exact named session and impact on totals; do not promise undo unless implemented. Retain pagination, date ranges, protected-content rules, full run details and exact credited accounting. Empty, filtered-empty and report errors remain different states. Goals appear only when enabled; pending goals show effective date, not misleading zero progress. Files: `FocusHistoryRoute.kt`, `FocusInsightsCard.kt`, `FocusGoalReport.kt`, `WeeklyFocusCard.kt`.

### 9. Settings, goals, tags, shortcuts and reminders

Current C: long mixed page; shortcut list follows invitations/goals/tags. Proposed same settings destination with concise sections/disclosures: Reminders, Goals, Tags, App shortcuts. Show each current value/status before expansion; add direct Settings access in Focus. Rename user-facing “invitations” to “Focus reminders” if localization review confirms intent; retain underlying semantics. Goal editor shows target units, preferred days, zone, effective Monday, Save and Disable plus explicit saved/pending status. Do not change effective-date math merely to make activation look immediate.

Reminder enablement reveals days/time and alert permission explanation; request permission on Allow notifications, not upon opening Focus. Return from Android settings refreshes status. Preserve drafts on denial and save failure; clearly distinguish enabled schedule from deliverable notifications. Shortcut list uses app icon/name and selected state; package text only for ambiguous names. Explain profile scope and absence of OS blocking near shortcut heading. Show selected unavailable apps with Remove and discovery errors with Retry. During a session explain why shortcut edits are unavailable. Tag rename/merge/delete must communicate historical impact and retain existing conflict safeguards. Files: settings files, `FocusGoalSettings.kt`, `FocusInvitationSettings.kt`, `FocusTagManager.kt`.

### Accessibility and design acceptance rules

Use existing theme tokens and Luma components; retain serif display headings and sans-serif body text. Do not introduce arbitrary brand colors. Use text plus selected/state semantics, not color alone. All interactive hit areas at least 48dp and non-overlapping; actions wrap/stack with font growth. Android guidance: [accessible apps](https://developer.android.com/guide/topics/ui/accessibility/apps), [Compose API defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults). Verify text contrast against actual theme colors (4.5:1 normal text, 3:1 large text), including error, disabled/helper and dark theme states; this audit does not claim a measured contrast failure.

TalkBack order: heading → work name → timer label/value → actions → optional content. Announce state/save/error transitions, not every second. Provide meaningful selected timer-mode semantics and expanded/collapsed detail semantics. Validate 200% font, RTL/Persian numerals, French text expansion, keyboard focus/IME and landscape. Keep primary action reachable without covering input or bottom content; preserve scroll/draft on rotation. Do not infer permanent missing semantics from partially clipped UI-state nodes.

## E. Prioritized improvements

P0 = prevent incorrect/ambiguous actions; P1 = core usability improvement; P2 = refinement or behavior requiring validation. Effort is relative, not a delivery estimate.

| Improvement | Problem Solved | Priority | UX Impact | Implementation Effort |
|---|---|---|---|---|
| Repair leave-dialog action layout | O6 overlapping action bounds | P0 | High | Low |
| Explain required name and actionable validation | O2 unexplained disabled Start | P1 | High | Low |
| Consequence-based end/leave labels | O5 timer/navigation ambiguity | P1 | High | Low |
| Simplify setup hierarchy and timer controls | O2 competing decisions | P1 | High | Medium |
| One primary completion action | O8 competing closure/break choices | P1 | High | Low |
| Promote active break state | O10 completed-session frame dominates break | P1 | Medium | Medium |
| Focus header History/Settings access | C buried secondary destinations | P1 | High | Medium |
| State-specific Today CTA | O7 generic return action | P1 | Medium | Low |
| Local failure/unlock/permission guidance | C recovery paths need clear consequences | P1 | High | Medium |
| Non-overlapping scalable accessible layouts | O6 plus unverified device variants | P1 | High | Medium |
| Compact history and disclose accounting detail | O9 low scan density | P2 | Medium | Medium |
| Group settings and improve saved/pending feedback | C mixed configuration concerns | P2 | Medium | Medium |
| Background-safe ordinary Back | O5 inconsistent mental model | P2 | High | High |
| Test motivation and terminology with new users | H engagement/comprehension assumptions | P2 | Medium | Low |

## F. Implementation roadmap

Implement presentation changes before changing navigation semantics or persistence. Each task below can ship separately; use existing runtime commands and state boundaries.

| Order / task | Files / components | Acceptance criteria |
|---|---|---|
| 1. Dialog safety quick win | `FocusScreen.kt`; `FocusScreenTest.kt` | Three actions have distinct bounds at normal/200% fonts; keep/dismiss never changes timer; pause/end navigate only on success; write failure stays recoverable |
| 2. Copy and validation quick win | `FocusSetupContent.kt`, `FocusCompletionContent.kt`, `res/values*/strings.xml` | Required name explained on first view; blank/invalid input has an accessible route to correction; IME semantics match action; duration limits unchanged; translations supplied |
| 3. Entry and completion hierarchy | `TodayFocusCard.kt`, `FocusCompletionContent.kt` | Today distinguishes idle/running/paused; completed summary has one filled primary action; saved credit and terminal reasons remain accurate; task completion remains explicit |
| 4. Prepare-first layout | `FocusSetupContent.kt`, `FocusScreen.kt`, `FocusSetupState.kt` | Name → timer → optional context; keyboard does not hide Start/error; repeat fills draft without starting; replacement confirmation and task conflict handling preserved |
| 5. Secondary navigation | `FocusScreen.kt`, navigation host call sites discovered via usages | History/settings available in header or labeled overflow; back restores draft/scroll; direct links and protected unlock do not leak names/tags |
| 6. Break presentation | `FocusCompletionContent.kt`, `FocusScreen.kt`, `FocusBreakRuntime.kt` | Running/expired/end break distinct; custom invalid values stay editable; no duplicated break/session actions; prior session remains saved; no auto-start |
| 7. History density and details | `FocusHistoryRoute.kt`, `FocusHistoryViewModel.kt`, report components | Search/tag/date/pagination preserved; expansion keeps list position; accounting detail remains available; delete confirms identity and refreshes totals; lock/error/empty distinguishable |
| 8. Settings organization | `FocusSettingsScreen.kt`, goal/invitation/tag components | Every section has visible status; pending goal date/zone explicit; denied alerts do not block timer; failed saves retain edits; app discovery failure differs from empty selection |
| 9. Navigation behavior experiment | `FocusNavigationGuard.kt`, `FocusScreen.kt`, navigation host, runtime tests | Only after explicit product decision: Back can continue timer with return status; pending destination, recovery, privacy lock and process restore tested; no duplicate active session |
| 10. Accessibility and regression pass | Existing focus Android/UI tests plus manual devices | TalkBack, 200% fonts, Persian RTL, French, dark theme, landscape/tablet pass; no overlapping controls or obscured CTA; contrast measured and documented |

Relevant existing verification suites: `app/src/androidTest/java/com/luma/lifeos/ui/focus/FocusScreenTest.kt`, `FocusLifecycleTest.kt`, `FocusReportsTest.kt`, `FocusAllowedAppsTest.kt`; `app/src/androidTest/java/com/luma/lifeos/focus/FocusRuntimeRecoveryTest.kt`, `FocusShortcutHandoffTest.kt`; setup/history/domain tests discovered in the corresponding `src/test` trees. No tests were edited or run for this document-only task. Successful deployment is build evidence, not a test-suite pass.

### Integration decisions

Progress destination: “View progress” expands the existing `FocusInsightsCard` inline beneath the compact summary, scrolls its heading into view, and retains its current period selection and retry/unlock behavior. It does not introduce a new route. Default period remains the existing setup period; changing periods does not alter the draft or history filters. History in the header opens all sessions; Today’s day-history link retains its date scope. Collapse returns to the compact summary without resetting the selected period.

[LifeOsApp.kt](app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt) has two `FocusRoute` call sites (semantic search: lines 1202 and 1843). The inspected primary route supplies history/task-picker navigation, Back fallback to Today, pending-destination, invitation and protected-access callbacks. Tasks 5 and 9 must keep both call sites consistent when adding Settings access. Preserve those callbacks.

### Required runtime follow-through before implementation sign-off

The implementer should use the architecture and acceptance criteria above rather than repeat the initial discovery. Complete the unverified branches explicitly: clean first launch and global onboarding handoff; no tasks/task search/select/remove/missing/locked/completed task; timed 1-minute natural expiry and early end; valid/invalid custom duration and custom break; break expiry and leave/return; tag creation/catalog/rename/merge/delete; reflection save/cancel/failure; repeat with edited draft/deleted source; empty/date-filtered/no-match/paginated history and confirmed delete; goal disabled/pending/active/time-zone change; reminder denied/granted/revoked and stale invitation; shortcut installed/uninstalled/discovery failure; loading/load/write/checkpoint failure; process death/reboot/clock change recovery; emergency exit and pending destinations. Inject failures only in disposable QA data, never by changing production records.

### Product validation

Test with 5–8 first-time participants: explain Focus in their own words, start a named session without a task, choose a timed block, pause/return, finish, locate saved work, and configure a reminder without feeling permission is mandatory. Record task success, time to first start, incorrect taps, and understanding of background counting/saved totals. Compare the existing and proposed setup/completion. Suggested targets: all participants distinguish timer time from attention and blocking; no one interprets Stop as deleting progress; at least 80% start independently and find history. These are proposed targets, not observed outcomes. Avoid collecting session titles or private reflections in analytics.
