# Focus UX redesign implementation specification

Status: Ready for implementation planning. No application changes are authorized by this document-generation task.
Source: [UX audit](../../../FOCUS_UX_REDESIGN.md).
Execution order: **complete all implementation → one consolidated emulator QA campaign → fix findings and recheck affected journeys → final sign-off**.

## Document ownership

- This spec defines the required product behavior.
- [Plan](../plans/2026-11-08-focus-ux-redesign.md) defines implementation order and code boundaries.
- [Task tracker](../../FOCUS_UX_REDESIGN_TASKS.md) records progress.
- [QA protocol](../../feature-roadmap/focus-ux-redesign-qa.md) owns every emulator/device check.
- These documents supersede the audit's interleaved delivery/QA roadmap for this redesign. Existing unrelated Focus plans remain historical context.

## Scope and decisions

Implement the audit's preparation, current-session, completion, break, progress/history, settings, guidance and accessibility improvements together. Reuse Luma Compose components, MaterialTheme tokens, serif headings, sans-serif body, existing navigation and repositories. No new dependency, database migration, mandatory onboarding screen, top-level tab, attention score, streak, or app-blocking capability.

Keep the current guarded Back policy. Improve the dialog; do not implement the audit's optional background-safe Back experiment. User studies and retention analytics are later product research, not release gates for this work. QA must cover formerly unverified flows; the audit's bounded observations do not count as new QA passes.

Name remains required. Count-up remains default. Timed duration remains 1–180 minutes; break duration remains 1–60 minutes; default break remains five minutes. Preserve domain name/tag/reflection limits. No timer starts from repeat, task selection, presets, reminder entry, or break expiry alone.

For invalid fields, retain the disabled Start pattern and show an adjacent accessible explanation of the first blocking reason. Required helper is visible from initial display. Show field errors on blur/attempt; focus/scroll invalid fields when submitting through a keyboard path. IME Done dismisses the keyboard and does not start a session; only explicit Start submits.

Preset buttons 15/25/50 appear only in Timed mode. Selecting a preset intentionally replaces duration. Merely switching modes preserves the authored duration; Custom focuses the existing duration field without resetting it.

“View progress” expands the existing FocusInsightsCard inline, scrolls its heading into view, retains TODAY/THIS_WEEK/ALL_TIME selection and offers Collapse. Default TODAY remains unchanged. Collapsing, rotating or visiting History/Settings must not reset authored setup or selected period. History header opens all sessions; Today history retains its day scope.

## Required behavior and acceptance criteria

### S01 — Entry and navigation

Today Focus card: saved time/count, separate active or paused status, then filled state-aware action and secondary day-history link. Use Start focus when idle, Return to timer when running, and Return to paused session when paused. All navigate to the appropriate screen; none silently resumes or starts.

Focus preparation header exposes History and Settings, directly or through labeled overflow on narrow widths. Extend FocusRoute with onOpenSettings: () -> Unit and pass it through FocusScreen. Wire both FocusRoute call sites in LifeOsApp.kt to the existing Focus settings destination with its existing access gates. Do not create a second settings route.

Hide or guard secondary destinations whenever the current runtime/privacy rules require it. Preserve pending destinations, token validation, task-origin protection, invitation-draft replacement confirmation and Back fallback to Today. Restore draft and list position on return.

### S02 — Preparation and task context

Order: inline benefit → required work name → optional existing-task source/selected-task summary → timer mode/duration → optional tag → compact recent work → compact saved-progress summary. Persistent bottom Start respects IME and navigation insets.

Copy: “Make time for one thing.”; name helper “Required · e.g. Read chapter 3”; task action clearly optional; count-up supporting text “Until you end it”; scope “Tracks time on this device. Other apps stay available.” Localize all copy.

Retain task picker route/search/pagination and separate loading, error, no tasks, no matches and locked states. Empty copy explains a task is optional. Back preserves draft. Change/Remove selected task uses current selection/conflict semantics; asynchronous results cannot overwrite later authored edits. Task completion always requires a separate explicit action.

Tags remain optional. Keep catalog selection and domain safeguards. Recent work “Use settings” pre-fills without starting. Preserve authored-draft replacement confirmation, legacy missing-name guidance, missing source handling and next-step context.

Start disabled reasons distinguish missing/invalid fields from task conflicts, restore/start pending, and unresolved writes. In-progress labels distinguish Starting from Saving. No duplicate command or false success.

### S03 — Active/paused timer and leave dialog

Order: state → work name → elapsed/remaining label and stable timer → main controls → optional shortcuts/help. Omit Untagged when absent. Pause/Resume filled; End and save outlined. Paused helper: “Paused — time isn't counting.” Keep history/leave guidance.

Use a full-width vertical dialog action column: Pause and leave / End and save / Keep focusing. Each hit region is distinct, at least 48dp, and wraps at large fonts. Back/outside dismissal keeps focusing without a timer mutation. Navigate after acknowledged pause/end only; write failure keeps the dialog recoverable.

Emergency exit remains reachable from labeled secondary Help/menu during normal operation and directly in failure/recovery. Preserve warnings and emergency-marker semantics. Shortcuts use app names/icons; package identifiers appear only for ambiguous labels. Failure names the shortcut and offers retry/manage without changing timer state.

### S04 — Completion, reflection and linked tasks

One filled primary Done action. Then secondary Take a break, optional Add outcome or next step, Use session settings, New session and View sessions. Customize break lives inside the break options disclosure, not as another competing primary action.

Show work name, credited duration and correct distinct outcome: count-up end, early timed end, target reached, interrupted. Never claim saved before acknowledgement. Interrupted outcomes explain uncertainty without target-achieved celebration.

Reflection Save/Cancel is explicit. Pending/error/success is announced; navigation must not silently lose an edited reflection. Keep existing missing-session/generation/lock protections. Task completion/unlink feedback is distinct from timer-save success; no automatic task completion.

### S05 — Break

While active, Break heading/countdown and primary End break precede compact saved-session information. Done returns to the originating destination while break continues according to existing runtime behavior; communicate “Break continues when you leave.” Preserve a route back via existing Focus entry.

Custom minutes validate 1–60 and preserve invalid input for correction. Expired: Break finished, secondary Use previous settings and primary Done. No automatic session start. End break returns to saved summary. Use existing runtime rules for a start request during a break; explain replacement/end if required; never display contradictory simultaneous states.

### S06 — Progress and history

Compact summary links to inline insights as defined above; local report errors do not disable a healthy session start. Honor existing visibility/privacy gates.

History rows prioritize name/duration, then date/time and meaningful state/tag. Omit Untagged. Expanding shows reflection and edit/repeat actions before Time breakdown disclosure. Preserve exact run accounting, pagination, search, tag/date filters and requested-session links. Expose expanded/collapsed semantics.

Delete is secondary, confirms session identity and effect on totals, and refreshes results only after success. No unsupported Undo promise. Differentiate initial empty, filtered no matches, locked, loading, error and missing session. Clearing search/tag does not silently clear day scope. Preserve list position and filters on detail return.

### S07 — Settings and configuration

Use the current settings page with disclosures: Focus reminders / Goals / Tags / App shortcuts. Each collapsed section shows current status. “Focus reminders” is the user-facing label for invitations; internal tokens/scheduling semantics remain unchanged.

Goals retain target units, weekday selection, zone, next-Monday effective date, explicit Save/Disable and conflict handling. Show saved/pending feedback. Goal changes must not appear active before their existing effective date.

Reminders show schedule status separately from notification delivery access. Ask permission only after Allow notifications; denial/revocation never blocks starting Focus. Refresh status on return from system settings. Preserve edits after failures.

Shortcuts retain profile scope, running-edit restriction, selected/unavailable apps, search, retry/refresh and remove. Discovery failure differs from no results/no selection. Tags retain rename/merge/delete consequences, privacy and conflict safeguards.

### S08 — Loading, failure, privacy and recovery

Restore loading: “Restoring your session…”; Start unavailable until runtime is known. Restore failure: retry plus safe exit with accurate saved-data warning. Command/checkpoint failures preserve known state, input and operation identity; retry cannot duplicate a session.

Recovery explains checkpoint time and uncertain gap with consequence-specific actions mapped to existing CONTINUE_ELAPSED and END_AT_CHECKPOINT commands. Verify current runtime semantics before writing explanatory copy; do not infer credit from wall clock.

Protected content offers existing unlock path; canceled unlock safely returns. Redaction is never described as no data. Notification caveat stays at timed/break/reminder intent: timer works; background alerts are best effort. Task missing/changed, source deleted and app unavailable have specific recoverable explanations.

### S09 — Accessibility and localization

All hit regions at least 48dp and non-overlapping. Primary controls remain reachable at 200% fonts, keyboard visible, landscape and tablet widths. Respect existing edge-to-edge/IME patterns. Use text and state semantics in addition to color.

TalkBack order: heading, work name, timer label/value, controls, secondary content. Announce transitions/save/errors, not per-second ticks. Explain disabled actions accessibly. Preserve draft, disclosure state, period and scroll through configuration changes without retaining protected content after access is lost.

Use values, values-fa and values-fr resources; verify Persian RTL/numerals and French expansion. Measure normal text contrast ≥4.5:1, large text ≥3:1; disabled controls must remain understandable but are not asserted to meet active-text contrast thresholds. No new arbitrary colors.

## Code boundaries

Existing production area: app/src/main/java/com/luma/lifeos/.
- ui/focus/FocusScreen.kt and FocusRoute.kt: state presentation, header, callbacks, dialogs.
- ui/focus/FocusSetupContent.kt and FocusSetupState.kt: draft presentation/validation; retain reducer and normalization.
- ui/focus/FocusCompletionContent.kt, FocusReflectionEditor.kt, FocusTaskCompletionAction.kt: closure and optional actions.
- ui/focus/FocusHistoryRoute.kt, FocusHistoryViewModel.kt, FocusInsightsCard.kt, FocusGoalReport.kt, WeeklyFocusCard.kt: reporting.
- ui/focus/FocusSettingsScreen.kt, FocusSettingsRoute.kt, FocusGoalSettings.kt, FocusInvitationSettings.kt, FocusTagManager.kt: configuration.
- ui/focus/FocusTaskPicker.kt, TodayFocusCard.kt: entry and task selection.
- ui/LifeOsApp.kt: both navigation call sites.
- focus/FocusViewModel.kt, FocusRuntime.kt, FocusBreakRuntime.kt, FocusNavigationGuard.kt: preserve behavior; modify only when needed for explicit presentation state or safe handoff.
- app/src/main/res/values{,-fa,-fr}/strings.xml: localized copy.

Prefer small private composables in existing files. Split a component only if needed for maintainability; avoid reorganizing domain/data layers.

## Delivery contract

Implementation-ready means every I task and local build/unit check is complete, with device verification pending. It does not mean QA-passed. All emulator/connected instrumentation execution and manual accessibility checks occur together in Q01 after the implementation gate. No per-task deploy, preview-as-device-check, emulator smoke test or connected test execution.

One consolidated QA campaign includes the whole matrix, evidence, defect fixes and focused reruns. It is not one test method or a guarantee of one attempt. A failed/blocked required case prevents a full pass. Never mark an unexercised state passed.
