# Focus product review and improvement proposal

**Date:** 7 November 2026

**Perspective:** Senior product manager

**Status:** Research and recommendations; proposed priorities, not an approved implementation specification.

## 1. Recommendation

Make Luma Focus the easiest way to **start meaningful work, save honest progress, and know where to resume**.

The feature already has considerable depth. Its next advantage should come from a clearer experience and connections to existing Tasks, rather than more timer modes or another analytics dashboard.

Recommended order:

1. Establish release confidence in the existing work and make its control boundaries easy to understand.
2. Simplify setup and completion so starting and leaving a session require less effort.
3. Complete the already planned task connection.
4. Validate recurring use before investing in presets, widgets, additional reminders, or richer motivation.

These are product recommendations, not changes to the current engineering execution agreement. Existing FX work still follows its recorded source-first, separate final-QA sequence unless the owner changes it.

## 2. Scope, evidence and confidence

This review combines current source inspection, project delivery records, and official competitor product/help pages accessed on 7 November 2026. It is a desk review: no new app build, device walkthrough, competitor installation, usability study, or product analytics was performed.

**Working audience hypothesis:** people who already use Luma privately for personal tasks, study, and reflection. This is inferred from the product, not validated segmentation.

Competitors below are recognizable category benchmarks selected for distinct patterns. “Top” is not a verified ranking by downloads, revenue, retention, or regional store position. Vendor pages establish advertised behavior, not effectiveness; platform and subscription differences need checking before a detailed implementation comparison.

### Local evidence inspected

- [Focus screen](../app/src/main/java/com/luma/lifeos/ui/focus/FocusScreen.kt): setup, running, recovery, and completion composition.
- [Setup state](../app/src/main/java/com/luma/lifeos/ui/focus/FocusSetupState.kt): retained drafts and explicit Start.
- [Focus settings](../app/src/main/java/com/luma/lifeos/ui/focus/FocusSettingsScreen.kt) and [English copy](../app/src/main/res/values/strings.xml): controls, scope and disclosures.
- [Session model](../core/domain/src/main/kotlin/com/luma/lifeos/domain/focus/FocusSession.kt): timer, metadata, reflection and terminal state.
- [Current delivery ledger](FOCUS_INSIGHTS_TASKS.md): FI and FX status, prior product recommendations, and 7 November QA evidence.
- [Original Focus delivery ledger](FOCUS_MODE_TASKS.md), [extension design](superpowers/specs/2026-11-04-focus-product-extensions-design.md), and [final QA contract](feature-roadmap/focus-product-extensions-qa.md).

The README still describes Focus as unimplemented. Current source and the dated delivery ledger provide a more accurate baseline. Documentation drift is itself a release-readiness issue.

## 3. What exists today

“Present in source” does not mean fully tested, released, or accepted.

| Capability | Current evidence | Product assessment |
| --- | --- | --- |
| Count-up and timed sessions | Pause/Resume/Stop, fixed 1–180 minute timed target, 25-minute shortcut, recovery and emergency exit | Enough timing flexibility for the next iteration; prioritize comprehension and reliability |
| Explicit breaks | 1–60 minute break countdown, separate from credited focus | Useful foundation; make taking or skipping a break a simple choice |
| Session identity | Required name, one optional tag, recent tag suggestions, retained setup | Useful records, but setup asks for classification before work begins |
| Repeat | Completion and terminal detail prefill editable setup, including timed configuration and next-step context | Already exists in source; improve discovery before building a preset library |
| Reflection | Optional outcome and next-step note after saving | Strong fit with returning to meaningful work; keep it optional and secondary |
| Reports and history | Today totals, weekly comparisons, period insights, tags, search and paged history | Reporting breadth is sufficient; improve the path from report to action |
| Weekly goals and Review distribution | Versioned goals, active days, highest recorded focus day | Present in source; do not propose these again as entirely new features |
| Corrections | Name/tag editing and tag rename/merge | Partial extension delivery; task unlink and related privacy depend on task integration |
| Task-linked Focus | Model inspected has no task link; FX03 is recorded as not started | High-value existing backlog, not a new discovery |
| Scheduled invitations | FX08 recorded as not started | Existing backlog; not necessary to prove the core session flow useful |
| App controls | Selected-app shortcuts and an in-Luma navigation guard | Device-wide blocking remains deferred/unmet; these are different product promises |

### Release confidence deserves priority

The 7 November ledger records targeted FI01/FI02 passes and FI04 targeted evidence, alongside two failing Focus migration cases and broader data-suite failures. The migration journal-row failure has not yet been established as a production defect versus fixture issue.

FX04 has a narrow timer-expiry and deleted-session-cleanup smoke result. That does not certify process death, reboot, clock changes, timer races, or the full extension.

FX01/FX07/FX09 remain partial, FX03/FX08 unfinished, and FX10 blocked on dependencies. Integrated FX-QA remains open. Product messaging should not present these layers as uniformly complete.

## 4. Competitive patterns worth learning from

| Benchmark | Officially documented pattern | Useful idea for Luma | Boundary |
| --- | --- | --- | --- |
| Forest | Growing trees make accumulated focus visible; countdown/stopwatch, Deep Focus and customizable allowlists are advertised | Give a session a calm sense of progress and a satisfying saved result | Do not copy tree-loss punishment, social pressure, or claim equivalent blocking |
| TickTick | Focus can start from a task; Pomodoro and stopwatch serve different work styles; task focus can be analyzed | Make the task the natural starting context and preserve a clear mode choice | No need to duplicate its wider task system or cloud synchronization |
| Focus To-Do | Pomodoro and task management share one workflow; reports connect time with task completion and project distribution | Close the intention → work → explicit completion loop | Avoid expanding Luma Focus into a second project manager |
| Freedom | User-selected distraction blocklists and recurring blocking sessions | Explain what control is active before the session begins; make routines deliberate | Automatic blocking is a different promise from Luma's reminder invitations |
| Opal | Android sessions/time limits and Android screen-time reporting; its help documents platform differences | Make progress understandable and keep feature claims specific to Android | Screen time is not focus time; do not import scores or assume iOS parity |

Sources: [Forest official site](https://www.forestapp.cc/), [TickTick Focus guide](https://help.ticktick.com/articles/7055782010496745472), [Focus To-Do official site](https://focustodo.cn/), [Freedom session guide](https://support.freedom.to/en/articles/1773666-how-to-start-a-block-session), [Opal Android announcement](https://community.opalapp.com/t/opal-now-on-android/8716), and [Opal Focus Report help](https://opalapp.com/help/what-is-focus-report).

**Inference:** these products make one primary value easy to recognize: visual reward, task execution, or distraction control. Luma can be distinctive by connecting private intention, recorded effort and a next step. The comparison does not prove that copying any feature will improve retention.

## 5. Strategic choices

| Direction | Advantage | Cost or tradeoff | Recommendation |
| --- | --- | --- | --- |
| Calm task-connected companion | Fits Luma's local-first Tasks and reflection strengths; mostly builds on existing work | Task/Focus privacy and linked-record behavior need careful completion | **Choose this direction** |
| Dedicated distraction blocker | Clear value for people primarily seeking phone restriction | New platform, permissions, distribution and reliability investigation; current shortcuts cannot deliver it | Treat as a separate future product decision |
| Gamified/social focus world | Strong visible motivation and recognizable identity | Asset production, reward design, accounts/sharing and pressure risks | Defer until user research supports it |

This is the reviewer's recommendation. No strategic direction is recorded as selected by the owner.

## 6. Prioritized improvements

Priorities reflect expected user value and current evidence, not measured demand. Effort is relative: S = narrow interaction/copy change; M = several surfaces or persisted configuration; L = cross-feature data/privacy/lifecycle work. Engineering must validate estimates.

### P0 — Trust and clear expectations

**R1. Bring release claims, evidence and user language into agreement.**

**Type:** existing release work plus documentation/copy improvement. **Effort:** M–L. **Confidence:** high.

Finish the recorded integration and QA obligations before promoting the expanded experience as accepted. Triage migration failures without assuming all are fixture problems. Refresh the README and a single authoritative delivery summary.

Keep the main user promise simple: “Make time for what matters.” Pair it with a short factual statement near Start: “The timer keeps counting outside Luma. Other apps remain available.”

Use “App shortcuts” wherever an allowlist-like label would imply enforcement. Existing settings copy already explains the limitation; preserve that accuracy and test whether users notice it at the decision point.

**Acceptance:** users can explain what leaving Luma does and whether other apps are blocked. Saving, recovery, restore and task privacy have the evidence required by the existing QA contract. Never substitute a copy update for reliability work.

### P1 — Make starting feel easy

**R2. Give setup one clear job: choose an intention and start.**

**Type:** new refinement of existing setup. **Effort:** S–M. **Confidence:** high for the observed UI structure; medium for user benefit.

Current setup marks a blank name invalid immediately. It also renders every available recent-tag suggestion as a full-width row before Start, potentially pushing the action down the scrolling page. These are source observations; their practical severity needs device/usability validation.

Propose:

- Label the required field “What will you work on?” with a short example.
- Keep the required-name contract for this iteration. Show validation after interaction or an attempted submission, not on untouched arrival.
- Keep Count up and Timed visibly selectable. Preserve count-up as default and the custom duration range.
- Make the existing 25-minute option easy to find; test a smaller quick-duration choice only if users ask for help beginning.
- Put optional tag selection behind a compact control, with a bounded recent list.
- Keep Start easy to reach with keyboard open, large fonts and Persian RTL.
- Place period insights and history below the primary setup action; do not require classification or report browsing to proceed.

**Acceptance:** a first-time user can enter a valid intention and start without tagging; a returning user can reach Repeat without searching history. Empty arrival is visually neutral. Invalid submissions remain clearly explained.

Do not silently generate names or remove the name requirement: either would change current data and search expectations and needs a separate decision.

### P1 — Make finishing calm and useful

**R3. Simplify the completion screen's action hierarchy.**

**Type:** new refinement of existing completion/reflection/break features. **Effort:** S–M. **Confidence:** medium.

Current source places the reflection editor and editable break duration before Done, then offers Repeat, Start another, History and sometimes a pending destination. That is a lot of decision-making immediately after work.

Proposed order:

1. “Session saved” with saved duration and a precise state: target reached, stopped early, or interrupted.
2. A clearly reachable Done action and a compact “Take a 5-minute break” action.
3. Optional “Add outcome or next step,” initially compact; show existing saved reflection when present.
4. Repeat and other secondary actions lower down. Customize break duration from the break action rather than showing the field to everyone.

**Acceptance:** Done is reachable without completing reflection or choosing a break. Skipping reflection changes no saved time. An early Stop is not described as finishing the intended work. Reflections remain editable from history.

Preserve explicit starts: no automatic break, next block or task completion.

### P1 — Connect effort to an existing task

**R4. Complete the task-to-Focus loop already specified in FP02/FX03.**

**Type:** existing planned capability. **Effort:** L. **Confidence:** high strategic fit; demand unvalidated.

Use “Focus on this task” in task detail and an optional task picker in setup. Prefill an editable intention, show saved focus on the task, and offer an independent “Mark task complete” action after a saved session.

Keep the existing design's separate task completion operation, task-origin provenance, dual-section access, safe deletion behavior and mixed-report suppression. A renamed/deleted task must not erase the focus record or expose protected content.

**Acceptance:** users distinguish “I worked on this” from “I finished this.” The end-to-end task → Focus → save → optional completion journey works with either section locked and with a task changed during the session.

Do not create a competing project/subtask hierarchy inside Focus.

### P2 — Help users resume, before asking them to plan more

**R5. Surface one recent intention or next step at the point of return.**

**Type:** new discovery surface for existing Repeat/reflection data. **Effort:** M. **Confidence:** medium.

Repeat is currently available after completion and in terminal detail. Test a small idle-setup “Continue recent work” entry that shows the latest authorized intention and next step, with an explicit Repeat action. It creates a new setup draft, not a resumed historical timer.

If the user benefits, consider a compact Today entry later. An open session always takes precedence; lock/reset/restore changes must invalidate displayed private context. Avoid a carousel of historical work.

**Acceptance:** returning users can resume their intended work without remembering a title or searching history. Hidden/locked content stays hidden. Start remains explicit.

Only build saved presets after evidence shows users need several recurring configurations that Recent/Repeat cannot serve.

### P2 — Make Review lead to one useful decision

**R6. Prefer a next action over more statistics.**

**Type:** refinement after existing FP08 acceptance. **Effort:** S–M. **Confidence:** medium–low.

Keep the existing weekly distribution and “Plan another session.” After task linking is dependable, test a compact view of what users worked on and an authorized next-step entry. Start with existing task/name/tag records rather than collecting more reflection fields.

Do not call recorded duration “productive hours,” infer the best focus time from sparse history, or claim mood causes better focus. Separate self-reported progress from timer-derived facts.

**Acceptance:** users can answer both “What did I record?” and “What will I do next?” without opening another dashboard. Any task-related summary respects both sections' access.

## 7. Proposed experience

This is a conceptual flow for review, not a final screen specification.

| Moment | Main content | Main action | Secondary content |
| --- | --- | --- | --- |
| Open Focus, no active session | Intention, timer mode, recent-work entry when authorized | Start | Optional tag, insights and history |
| Enter from a task | Linked task and editable intention | Start | Duration and optional tag |
| Running | Intention, elapsed/remaining time and accurate running status | Pause / Stop, both easy to reach | App shortcuts and concise timer-scope explanation |
| Paused | Saved current progress and paused status | Resume / Stop | Safe navigation |
| Saved | Saved duration and terminal state | Done | Break, optional reflection, Repeat, independent task completion |
| Return later | Last authorized next step | Repeat into editable setup | Fresh session setup |
| Weekly Review | Existing saved totals and distribution | Plan another session | Relevant task/next-step context if validated |

This changes information hierarchy, not timer accounting. Recovery and emergency controls must remain independently reachable.

## 8. Validation and success measures

Begin with consenting usability sessions rather than adding production telemetry. Suggested first round: 6–8 participants across task-based work and bounded study, including returning and first-time users. This is a proposed formative sample, not statistical validation. Include accessibility and Persian RTL coverage.

Compare current and proposed flows using equivalent tasks:

- Start a first named 25-minute session.
- Repeat prior work with a next-step note.
- Pause to use another app, then explain what time counted.
- Finish and leave without reflection; separately take a break.
- Start from an existing task and save without completing that task.
- Find a saved session and explain a partial-week report.
- Explain what Focus can restrict before trying it.

| Question | Proposed measure | Interpretation |
| --- | --- | --- |
| Is starting easier? | Time from setup visible to successful Start; interactions, errors and assistance | Compare matched first-time and returning scenarios separately |
| Is control understandable? | Correct explanation of background counting, app access and Pause | Repeated misunderstanding is a redesign signal |
| Is completion calmer? | Unassisted Done/break success, skipped prompts and hesitation | More reflection submissions alone is not success |
| Does task integration help? | Ability to link work and distinguish saved focus from task completion | Do not use elapsed minutes as evidence of task progress |
| Do people return voluntarily? | Consenting diary study: people saving sessions on at least two distinct days in an observed week | Report denominator and observation window; establish a baseline first |
| Is the experience trustworthy? | Save/recovery failures, accidental starts, unwanted prompts and user-reported pressure | Treat these as guardrails, not acceptable costs of more usage |

For returning-use evaluation, pair repeat-session behavior with “Did this help you make progress on what you intended?” Establish a baseline before numeric uplift targets or launch claims.

No analytics SDK, network event collection, new permission, or diagnostic logging of session names, task titles, tags or notes is proposed. Any later aggregate measurement needs its own explicit privacy design.

## 9. Delivery recommendation and decision gates

| Stage | Focus | Exit evidence |
| --- | --- | --- |
| Current delivery | Finish existing FX source dependencies and integration according to the recorded workflow | Accurate source status; no invented completion claims |
| Release confidence | Run the separate agreed final QA phase, resolve failures and documentation drift | Required acceptance evidence on the final candidate, with residual platform gaps explicit |
| Small UX iteration | R2 setup and R3 completion; retain current data rules | Prototype/usability evidence, followed by scoped engineering verification |
| Task value | R4 existing task-linked work | Integrated private task-to-session journey and independent completion behavior |
| Learn before expansion | R5 recent-work discovery, then R6 if justified | Returning-user usefulness; absence of pressure/privacy regressions |

Do not reopen every current implementation decision merely because this review suggests an ideal product sequence. Fit changes into a separately agreed scope after reviewing existing delivery commitments.

## 10. Defer until evidence justifies the cost

- Device-wide blocking: separate feasibility and product scope; preserve the existing deferred/unmet status.
- Preset libraries and a dedicated Focus widget: test Recent/Repeat first; future launcher content must respect existing widget privacy rules.
- Automatic Pomodoro chains: add lifecycle complexity and weaken deliberate starts.
- Social rooms, leaderboards and punitive streaks: account/sharing scope and pressure without demonstrated demand.
- Ambient audio: content and playback lifecycle cost; prototype only if users identify it as a real need.
- AI coaching, focus scores and mood/focus causal claims: unsupported by the recorded data.
- Calendar scheduling, cross-device synchronization and a new project hierarchy: substantial expansion beyond this local Android experience.

The most valuable unanswered question is whether Luma users primarily need help **starting**, **staying away from distractions**, or **returning to unfinished work**. Validate that distinction before funding the larger second-wave ideas.
