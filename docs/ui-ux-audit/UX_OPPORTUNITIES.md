# Luma UX opportunities

19 October 2026. These are proposals, not implemented features or validated user preferences. Core purpose: private, offline recording and reflection with lightweight habits and one-time tasks. Read [the audit](AUDIT_REPORT.md) for source evidence and [the task backlog](IMPROVEMENT_TASKS.md) for fixes.

## Keep fixes separate from optional product work

Existing-behavior fixes should not need an expansion of product scope: wrong-date habit actions, invisible errors, dead retries, draft recovery, ambiguous action labels, accurate counts, search scope copy, consistent chrome and accessible feedback all belong to the audit backlog. Making current tasks reliable is more valuable than introducing another feature.

Likewise, returning a newly created person/place selected, searchable existing catalog choices and bounded long histories improve current workflows; they are not optional discovery experiments. Do not gate these repairs on onboarding, universal search or a photo viewer.

Optional ideas below should be selected individually after observing the relevant need. Most can be tested with realistic prototypes and small task sessions without adding analytics or collecting private life content.

## Candidate opportunities

| ID | Idea / affected journey | Clear benefit | Main trade-off / cost | Recommendation |
| --- | --- | --- | --- | --- |
| OPP-01 | Qualified last-export history + Backup link from Privacy | Returning users can recall when they explicitly exported before resetting/changing device | Local history cannot prove destination file still exists; lifecycle metadata must be accurate. Medium | Good optional candidate; UX-29 already scopes it |
| OPP-02 | Private full-screen photo viewer | Existing personal photos become useful for recall and inspection | Decode/memory/lifecycle and accessible zoom complexity. Medium | Adopt only if users regularly revisit attached photos; UX-31 |
| OPP-03 | Last-used capture/context shortcuts | Frequent recorders avoid repeating the same people/place/tag choices | Wrong inherited context can silently mislabel private records; clutter can outweigh savings. Medium | Test explicit suggestion chips before automatic defaults |
| OPP-04 | Optional brief first-use guidance | Explains Journal vs Memory, Habits vs Tasks and optional connections at the moment of need | Tutorial fatigue and pressure to configure data sources. Medium | Small contextual tips, no blocking onboarding carousel |
| OPP-05 | Richer local entry search | Users can retrieve a journal they remember by its text rather than its title | Indexing, query performance, database changes and broad-search expectations. High | Separate product/spec decision after UX-10 and UX-18 |
| OPP-06 | Chosen habit reminder | Helps users who actively want a prompt remember an existing habit | Notification permission, timezone/schedule edits, Android delivery constraints and reminder fatigue. High | Reuse/reconcile existing reminder plans; opt-in per habit only |
| OPP-07 | Reflection continuity | A gentle return to the last viewed week/unfinished reflection helps recurring review | Additional “unfinished” state can imply work users never wanted. Medium | Prototype a simple Continue reviewing [week] cue, no completion score |
| OPP-08 | User-controlled Today preview order | Different users can prioritize capture, habits or tasks | Configuration/discovery/restoration complexity; defaults must stay clear. Medium | Defer until the compact default Today is tested |
| OPP-09 | Choose several photos in one picker session | Reduces repeated picker launches when attaching several images | Picker availability, batch partial failure and private-copy progress. Medium | Optional improvement after UX-20/21/32; preserve platform picker fallbacks |
| OPP-10 | Subtle completion haptic | Confirms acknowledged habit/task completion when visual attention is low | Annoyance, false acknowledgment before commit, accessibility preferences. Low | Optional refinement after reliable state/feedback, not release-critical |

### OPP-01 — Qualified backup history

Current evidence: `BackupViewModel.kt` keeps transient message state only; Privacy advises exporting before reset. Existing UX-18 specification in `docs/superpowers/specs/2026-10-18-ux-18-backup-status-design.md` is pending and should be reused/reconciled, not implemented a second time under a different name.

Proposed experience: “Last export completed on this device: [date/time]” and “File availability is not checked.” First use says “No export recorded on this installation.” Provide Open backup settings beside reset advice, without launching a picker automatically.

Boundary: store timestamp only, after successful export close; no automatic background backup, filename surveillance or claim of protection. Restore does not import another installation's export-history metadata. See UX-29 for acceptance/failure matrix.

Validation: export on disposable destination, return after relaunch, then independently remove exported file. Copy must remain truthful and never show a misleading protected/safe status. Interview whether users understand both last action and current uncertainty.

### OPP-02 — Private photo inspection

Current evidence: `PhotoGallery.kt` provides 240 dp previews and retry for unavailable images, not a full viewer. Private-copy and draft-recovery safeguards already exist; inspection is a separate value improvement.

Proposed experience: tap preview → private full-screen image, Fit, zoom, next/previous and explicit Close. No share/upload action in the initial scope. Retain a screen-reader-friendly index and controls independent of gestures.

Boundary: use safely sampled originals for useful zoom, not enlargement of a 640 px preview; avoid unbounded bitmap loading and preserve private-file leases. See UX-31. Validation: task-based “find the detail in this saved photo” comparison, landscape/rotation/large files and TalkBack controls.

### OPP-03 — Explicit recent context suggestions

Current evidence: editors support reusable tags/people/places but selection can require repeated catalog browsing. UX-09/16 should first remove avoidable creation/reselection friction.

Proposed experiment: show at most three explicitly labeled “Recent people” or “Recent places” suggestions when context is expanded. Tap to select; no silent inherited values. Keep selected state visible, ordinary chooser available and suggestions entirely local. Do not infer sensitive relationships from text.

Benefit: fewer steps for repeated daily context without changing the core capture form. Trade-off: suggestions may be stale or expose private names over the shoulder; users should be able to hide them. No new permission or telemetry.

Validation: repeated-capture study with realistic duplicate/old names; compare selection steps, wrong-context selections and time, then ask whether the suggestion source is understandable. Proceed only if it reduces effort without more wrong attachments.

### OPP-04 — Explain record types in context

Current evidence: Today shows Mood/Journal/Memory and More exposes Habits/Tasks. Labels are concise but product distinctions are not established as obvious to new users; this is a hypothesis, not a verified comprehension failure.

Proposed experience: optional What can I record? near first empty capture state; explain “Journal: write about your day,” “Memory: mark something you want to revisit,” “Habit: repeated completions,” and “Task: something to do once.” Explain that marking an existing journal as memory preserves the original entry. Keep direct actions immediately usable and tips dismissible.

Boundary: no forced walkthrough, permission wizard, sample personal record insertion or automatic integration setup. Do not repeat this help on every empty date.

Validation: first-time participants select the right action for four everyday scenarios, then complete offline capture without granting permissions. Measure confusion and intervention count locally in research notes, never collect their actual private text.

### OPP-05 — Expand search only if the need is confirmed

Current evidence: title/catalog-only Search intentionally excludes bodies, notes, habit logs and tasks. The immediate problem is expectation, solved by UX-10. Old performance measurements already raise scale concerns; expanding before profiling risks slower recall.

Possible staged scope:

1. Title/catalog plus journal-body and manual-note text, with explicit scope control and local indexing.
2. Separate optional inclusion of habit logs/tasks with type-specific result cards and destination semantics.

Start with stage 1 only if users repeatedly fail to retrieve known entries by remembered body words. Preserve literal user content, local-only operation, no query/content logging, existing dates/filters, and clear matching snippets that do not expose more sensitive text than necessary. Migration, index size, diacritics/mixed-script behavior, stale-index recovery and query cancellation need design before implementation.

Trade-off: greater retrieval coverage versus implementation cost, storage and privacy of visible snippets. Do not promise semantic/AI search or a universal result model without a new specification. Validation: realistic known-entry retrieval tasks, Persian/French text, 50k-event p95 measurements and deletion/index consistency. Do not deploy a slow universal search merely to remove explanatory copy.

### OPP-06 — Per-habit reminders with deliberate control

Current evidence: habit reminder documents exist under `docs/superpowers/plans/2026-10-17-habit-reminders.md`; README describes reminder/release acceptance work as open. This audit does not establish notification delivery as implemented or tested.

Proposed boundary: user explicitly enables a time for a selected scheduled habit; first explain notification purpose before Android permission. No default daily nag, task-reminder expansion, engagement streaks or reminders for paused/archived/future-offday habits. Timezone/tracking-day behavior must be explicit and follow the habit's model.

Benefit: assists prospective memory for users requesting prompts. Cost: scheduling edits, delivery delay, denied permission, reboot/time changes, notification channels and duplicate suppression. Use platform-appropriate work; do not promise exact delivery without justification. Validate denial, disabled channel, schedule edit, pause/archive, travel/DST, reboot and missed delivery on supported devices before presenting reliability claims.

### OPP-07 — Preserve reflection continuity without scoring it

Current evidence: Review already restores week/section and provides deterministic sources. The task is reflection, not completing a report. Existing state restoration should first be verified under UX-11/14/33.

Optional experiment: when returning to Review, show a small “Continue [localized week range]” cue tied to the remembered section, plus Current week. Avoid a separate unfinished-review object initially. Never label absent health/mood data as an incomplete personal achievement.

Trade-off: a cue can help navigation but adds chrome. Validate whether returning users actually resume older weeks; remove the cue if the saved selection itself is sufficient. No engagement dashboard or daily-open quota is proposed.

### OPP-08 — Today order preference only after default simplification

Current evidence: Today puts capture, Habits, Tasks, Recent entries and summary in a fixed order. O01 showed large empty setup cards; UX-26 should address the default experience first.

Optional scope: one simple “Today sections” preference with up/down actions for Habits/Tasks/Recent entries and Show/Hide optional previews. Capture and date stay at the top; failures cannot be hidden by moving/hiding their source. Avoid drag-only reordering and mandatory personalization setup.

Benefit: supports habitual recorders and task-heavy users without another primary tab. Trade-offs: state persistence, reset/restore semantics and hidden-feature discoverability. Validate a standard default against customized order before adding configuration; defer if compact defaults satisfy both groups.

### OPP-09 — Multi-photo selection with truthful partial results

Current evidence: `PhotoEditor` launches one image selection at a time; journal/memory permit up to 20 private copies. Repeated picker entry can be costly for a small group of related photos. This has not been timed or observed live in this audit.

Proposed scope: use Android's supported multi-image picker when available, cap remaining slots, copy incrementally into private storage and show progress “Adding photo 2 of 4” only when count is known. Keep accepted successful images and name failed items by safe index; offer retry/reselect without duplicating attachments. Permission scope remains picker-only.

Dependencies: correct attachment operation states and indexed actions (UX-20/21), useful typed failures (UX-32). Validate cancellation before/while copying, one bad image among valid ones, remaining-slot limits, provider delay and process restoration. Do not implement batch selection while cleanup/recovery remains ambiguous.

### OPP-10 — Restrained completion haptic

Proposed experiment: one native, preference-respecting haptic after confirmed habit +1 or task completion, paired with the existing updated state and accessible result. No vibration for passive loading, failed actions or simple tab changes. If a mutation is pending, do not signal success early.

Benefit: confirmation without sustained visual attention. Trade-off: repeated habits may make it irritating, and device haptics vary. Validate with accessibility settings and users who disable vibration; feature should follow system preference and remain nonessential. Defer until correctness feedback is reliable.

## Research and decision criteria

Use a small mix of first-time and returning participants, including Persian/RTL, French/long-label use and assistive users where possible. This is a proposed research sample, not a claim of representativeness. Give fictional/disposable data; do not request personal mood/journal disclosure.

Representative tasks:

- Capture a mood and a journal with no account or permissions.
- Create a person while journaling and verify the intended attachment.
- Log yesterday's habit, correct one specific repetition, and recover a failed Undo.
- Complete an overdue task while another device action/fixture changes it; recover without losing the draft.
- Find a known journal remembered by a title versus body word; explain what current Search covers.
- Identify why weekly steps/app usage are missing, and opt out while preserving history.
- Find backup and explain what reset will and will not remove, without confirming reset.

Evaluate completion, wrong-date/wrong-target errors, recovery success, interventions, navigation steps, comprehension of scope/retention and user confidence. Compare to a recorded baseline rather than assigning unsupported percentage-improvement promises. Prefer local research notes containing only fictional task outcomes; no production analytics is required.

Selection rule: adopt an opportunity only when a repeated user problem remains after the audit fixes, its benefit is observable, its privacy/accessibility cost is acceptable, and implementation can preserve existing offline/native behavior. Otherwise keep the simpler product.
