# Focus product extensions specification

Date: 2026-11-04 · Status: **DRAFT — FP01, FP03 and FP05 source implemented but unverified; remaining extensions and final QA are pending**

[Task ledger](../../FOCUS_INSIGHTS_TASKS.md) · [Implementation plan](../plans/2026-11-04-focus-product-extensions.md) · [Final QA](../../feature-roadmap/focus-product-extensions-qa.md) · [Existing insights specification](2026-11-03-focus-names-tags-insights-design.md)

## Intent and scope

Help an individual move from an intention into focused work, resume similar work easily, and understand progress. Cover the minimum scope of FP01–FP08 from the product review: repeat, task linking, timed focus/breaks, reflection, weekly goals, scheduled invitations, classification correction and weekly review. These are proposed design decisions for review, not findings from user research or authorization to release.

The user requested a spec and plan with **one separate QA phase after all implementation tasks**. Implementation may proceed through source dependencies without per-task QA handoffs. All new test authoring/execution, compilation, schema generation, lint, device checks and integrated acceptance belong to the final QA phase. Reading, editing and source review are implementation work; source completion is explicitly unverified. No app implementation or QA execution is part of this documentation request.

Preserve FI01–FI07 and FM01–FM08 historical statuses. This extension replaces neither their evidence nor their unresolved acceptance. Carry their applicable outstanding checks into final QA. This document supersedes the earlier insights spec's exclusions only for the explicitly described extension behavior; its timing, privacy, filtering and compatibility rules otherwise continue to apply.

## Approaches and decision

1. **Extend existing Focus, Tasks and Review (proposed):** shared runtime and repository authority, one task link and one tag, existing period calculations. Smallest coherent product; timed expiry still requires careful lifecycle changes.
2. Separate productivity dashboard/project system: more organization surfaces, duplicated tasks and competing report rules. Deferred.
3. Automatic multi-cycle Pomodoro with device-wide enforcement: more automation and platform authority, more surprising starts and recovery complexity. Deferred.

Use approach 1 for this draft. The product review originally suggested learning between increments; the requested delivery plan instead groups implementation before a single QA phase. That changes delivery sequencing, not evidence of user demand. Repeat ships without reusable presets; reminders open ordinary setup. Presets remain conditional future scope.

## Global constraints

- Keep Kotlin/Compose/Room/coroutines, minSdk 28, compileSdk 35 and targetSdk 35; do not add dependencies or raise SDK levels for this scope.
- Operate offline; add no network, exact-alarm, accessibility, usage-access, device-admin or DND permissions. Reuse existing optional notification permission and settings.
- Keep one open focus session, pause exclusion, monotonic timing, explicit recovery, guarded navigation, emergency exit, generation checks and idempotent command receipts.
- Keep one optional focus tag per session, existing name/tag limits and existing range-clipped terminal-only saved totals.
- Supply English, French and Persian resources, RTL, wrapping, TalkBack labels and minimum 48 dp controls. Pause/Stop remain reachable independently of report or auxiliary-write failures.
- Run no tests, Gradle, schema generation, lint, APK assembly or device tooling during implementation tasks. Author and execute tests in the separate final QA phase after every implementation task is source complete.
- Never describe unexecuted checks as passing; keep source completion, QA results and product acceptance separate.

## Baseline and architecture

The initial design baseline was Room version **10** and archive schema **8**. Before FP05 implementation, source had advanced to Room **11** and archive **9** with the FP03 timer/break slice. FP05 therefore reserves Room **12** and archive **10**. Recheck versions before later extension work and rebase together if another feature advances either. Do not generate schema JSON by hand.

Use `FocusRepository`/`RoomFocusRepository` for timer transactions and a new `FocusProductRepository` interface implemented by the same Room repository for auxiliary mutations/reports. This keeps generation and revision authority inside the existing storage boundary. Expose it through `LumaStorage`. Keep timing policy pure in domain; Compose consumes state, never calculates persisted credit. Split new setup/task/reflection/goal/reminder UI into small files rather than growing the existing screen indefinitely.

All new mutations capture generation, command UUID and expected revision before work. Same command/payload replay returns its original outcome; changed payload under that ID rejects. Preserve legacy receipt decoding; version new payloads. Auxiliary receipts can reuse the existing receipt table with explicit operation kinds. Start identity includes task ID, provenance and timer configuration. No update may mutate timer identity or elapsed fields through a metadata endpoint.

## FP-R01 — Repeat (FP01)

Offer Repeat on completion and terminal-session detail. Load the latest accessible source record; prefill editable name, tag and timed/count-up configuration, never elapsed credit or reflection outcome. Show the prior next-step note as context, not as new saved content. Copy a linked task only if it still exists, is open and both sections are accessible; otherwise clear the link and explain. Keep task-origin privacy on any copied task-derived name. Legacy null name requires valid user input.

Selecting Repeat creates only a generation-bound setup draft. Start is always explicit and uses new session/run/command IDs. If another session is open, offer that session rather than overwriting its state. Reset/restore/relock clears private prefill and context. “Start another” continues to mean empty setup; Repeat is a separate action.

## FP-R02 — Task intention and completion (FP02)

Use the existing task editor/detail surface (`ui/todo/TodoEditorRoute.kt`), not a new task subsystem. Existing saved open tasks gain Focus on this task; unsaved edits must be saved or discarded before navigating. Setup offers an optional paged open-task picker with literal title search, 50 rows per page and stable created-at-descending/ID-descending ordering. Revalidate the selected task at Start; a deleted/completed task prompts selection removal, not silent reassignment.

One nullable task UUID links a session to `todo_task`; FK deletion sets the link null and preserves all focus records. Session name is an independent editable snapshot, validated at the existing 120-code-point limit. Overlong task titles remain visible as invalid drafts until edited; never silently truncate. Task rename does not rewrite session names. Task detail shows the full saved active total for its linked terminal sessions; open/paused contribution stays separate. Session deletion updates this total.

After a successfully saved user-stopped or target-reached session, offer Mark task complete if the linked task is still open. This is a separate explicit operation through `TodoRepository.setCompleted(id, expectedRevision, nextRevision, true)`. Retain the captured task operation across ambiguous retries. A completion failure/conflict does not undo focus or replay Stop; reload the task and ask the user to retry the separate action. Recovered interrupted sessions do not prompt completion automatically.

### Cross-section privacy

Task picker, task-derived setup, task link labels, linked totals and task completion require both `LockSection.TASKS` and `LockSection.FOCUS`. Persist a `task_origin` boolean whenever a task is selected or a name originates from a task, including edited task-prefilled names; enforce true for every non-null task link at Start/import rather than trusting a caller-supplied false flag. Clearing/deleting the link does not clear provenance. Repeat preserves it. Any task-linked or task-origin session remains protected by both sections even if the task is later deleted. Legacy/manual sessions default false.

When TASKS access is absent, do not expose those session names, notes, rows or tag labels; suppress any mixed aggregate that includes them with an unlock-to-view state rather than presenting a partial total as complete. Other standalone Focus history remains available. If a protected session is running, show generic timer/Pause/Stop/emergency controls without exposing labels; locking must never strand the user. Relock clears cached task records, prefills, notes and report results; unlock re-queries. No task title is put in external intents, notifications or logs.

## FP-R03 — Timed focus and explicit breaks (FP03)

Count-up stays default. Timed focus accepts **1–180 whole minutes**, with a **25-minute** shortcut. Optional break accepts **1–60 whole minutes**, with a **5-minute** shortcut. Both shortcuts are conveniences. Store mode/target in the session; fixed at Start. Count-up has null target. Pause freezes remaining time; Resume continues the remaining active duration. No setting change modifies an open session.

At target, finalize FINISHED with new terminal reason `TARGET_REACHED`; allow FINISHED with either USER_STOP or TARGET_REACHED. An early Stop is USER_STOP, even if the user reports “finished work.” When a Pause/Stop/checkpoint arrives after the target, settle target expiry first in the same transaction. Credited active time cannot exceed target. Close the final run at the canonical boundary from its start plus remaining credit; retain the actual observed raw timestamp separately. Increment revision once and release the open slot once. Duplicate expiry/Stop races cannot create a second terminal record.

Example: target 25 minutes; run 10, pause 7, resume. After 15 more running minutes, finish at 25 credited minutes and 32 wall minutes. If the next callback arrives 3 minutes late, still save 25, never 28. Day reports split the canonical runs at local midnight using existing rules.

Use the application-owned runtime for foreground updates and a device-local, generation/revision/session-bound one-shot **inexact elapsed-realtime alarm** as a best-effort expiry wakeup. Pause/Stop cancels it; Resume reschedules remaining time. The callback reconciles repository state before alerting. No background tick loop, foreground service or exact-alarm permission is added. Delayed execution may delay physical persistence/notification; logical credit still ends at the target. On reopening, settle due expiry before displaying current reports.

Same-process continuous samples can settle automatically. A new process with unobserved running time follows existing explicit recovery: Continue including elapsed time may reach the target with capped credit; End at checkpoint remains INTERRUPTED. Unknown/replaced boot, elapsed regression or wall discontinuity retains existing conservative interruption policy; never manufacture a target reached from uncertain time. A checkpoint already durably at target settles as TARGET_REACHED without accepting an extra gap. Notifications may invite opening recovery; notification delivery is never proof of completion.

After durable terminal save, offer Start break, Done and Repeat/Start another. No automatic next block or break. Break is a separate device-local countdown record with duration, monotonic start, boot ID, revision and generation; it creates no session/run or saved time. It has End break and accessible navigation, no Focus guard. Starting Focus explicitly ends any break. Same-boot recreation resumes it; reboot/uncertain continuity ends it without credit. Late callbacks cannot revive a cancelled break.

Alert text stays generic. Denied permission, disabled channel, Doze or force-stop may prevent timely alerts; the UI explains delayed/unavailable background alerts without disabling manual use. Android documents inexact delivery behavior in [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms) and opt-in notifications in [Notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission). These platform constraints motivate best-effort alerts; this is not a promise of exact background timing.

## FP-R04 — Reflection and next step (FP04)

After saving, offer optional outcome MADE_PROGRESS / FINISHED_INTENDED_WORK / GOT_INTERRUPTED and optional next step, **1–280 Unicode code points when present**, trimmed, single line, no ISO control characters; blank becomes null. Both may be absent. Do not persist localized fallback strings. Store on terminal sessions only, with generation/revision checks; allow later edits from detail. Save/skip cannot change timing or task completion. Retry only the reflection command. Deletion removes reflection with the session. Include these fields in archive 9.

## FP-R05 — Weekly goal and consistency (FP05)

Goals default off. Allow a weekly target **1–10,080 whole minutes** and **1–7 preferred weekdays**. Preferred days are planning hints, not excluded contribution days. Any positive saved contribution counts, including marked interrupted contributions; active/paused time stays separate. No punitive streak or productivity score.

New/edited goal versions take effect on the **next ISO Monday** in a captured goal zone. Show the effective date immediately. One scheduled version per effective Monday; edits replace it by revision. Historic versions remain unchanged. Disable hides goal progress immediately and cancels a pending version, while retaining historic versions; re-enable schedules a new version for next Monday. No current-week target is retroactively invented.

Use the saved goal zone for its weekly progress so travel cannot move goal boundaries. Device-zone changes show that zone and offer a next-Monday replacement; ordinary Focus reports retain their existing viewing-zone behavior. Re-enabling or changing zone must never rewrite an already started goal week. A pending replacement atomically replaces the previous pending version even when its zone differs. Persist the visible-from Monday and its zone on enable/re-enable: current-week progress stays hidden until that boundary, so an older historic version cannot accidentally become active again. Preferred-day hints use that goal's zone. Portable goal versions and visibility settings survive restore; reminders are not enabled by them.

A goal version recurs each week from its effective Monday until disabled or replaced. Activation is the instant at that Monday's start of day in the **new version's captured zone**, using the existing first-valid-start-of-day helper. At any instant show only the latest activated version (activation-instant order, UUID ascending for an otherwise equal tie); never sum progress across versions. Zone changes may make adjacent old/new weekly intervals overlap: retain each historical interval and target in its original zone, and switch the live card only at the new activation instant. Do not rescale or relabel the old interval as the new goal's week. Scheduling enforces one pending version globally in its transaction, in addition to the database's zone/week uniqueness.

## FP-R06 — Scheduled invitations (FP06)

One device-local recurring schedule, default off: selected 1–7 weekdays and one hour/minute in the current device zone. No preset/task reference in this version. Reuse existing notification permission/channel/status and quiet-hours settings. Prompt for notification permission only when the user enables reminders. A blocked channel/permission yields Blocked plus settings guidance; Focus remains usable.

Use inexact one-shot RTC alarms and existing reconciliation work; do not rely on a repeating alarm for civil weekdays. Reconcile on boot, package replacement, time/timezone change, schedule edit, permission return and database replacement. For DST gaps choose the first valid time after the gap; for overlaps choose the earlier offset and deliver once per local date. A slot has generation + schedule revision + local date identity and is consumed atomically. Skip past slots outside a **60-minute delivery window** (inclusive through exactly 60 minutes late), slots in quiet hours and slots while any focus session or break is open; never catch up with a burst.

Notification has generic Open, Snooze 10 minutes and Disable actions. Opening resolves the current schedule through protected navigation into empty setup; no automatic Start. Snooze is available once per occurrence, expires after its own 60-minute window and rechecks quiet hours/open state. Disable invalidates pending actions by revision. Notification open/snooze/disable may be replayed safely. Untrusted/malformed intents reject without writes. Alarm identities must be namespaced so reconciliation cannot cancel task/habit or timed-focus alarms.

## FP-R07 — Classification correction (FP07)

Terminal detail can edit name and tag and remove a task link; it cannot attach a different task, change mode/target, clear task-origin provenance, or edit credited time. Name/tag validation matches FI01; legacy null names remain allowed only when not edited. Mutations preserve run bytes and increment session revision. Refresh history/search/suggestions, longest/tag summaries, task totals and Review from repository invalidation.

Tag manager supports rename and explicit merge, not tag deletion. Case/spacing-only normalized identity may rename the same tag's display label. A collision with another normalized label offers merge confirmation naming source and target; never merges silently. Confirmed merge transaction moves all references, including open-session references, into the target, preserves the target display name and removes the source tag. Increment revisions of affected sessions and the catalog; invalidate any conflicting pending commands without changing run time. Restrict tag mutations to both section grants when any affected session has task protection. Reconcile stale Start/tag drafts by current catalog; reject changed payload replay. Untagged stays null, not a catalog row.

## FP-R08 — Actionable Review (FP08)

Extend `WeeklyFocusCard`, not a second dashboard. Show active days (positive saved contribution) and highest saved-time local date within the displayed current range. Equal days choose earliest local date; empty period has no winner. Label it Highest recorded focus day, not Most productive. Partial current week uses matching previous weekdays, never future days. Tap day opens existing ranged history. Plan another session opens empty protected setup and respects an already open session. Apply FP-R02 suppression to mixed private reports. Existing zero/error/unavailable and half-open range rules remain.

## Persistence, portability and replacement

Room 10→11 (subject to recheck) adds session `task_id` (indexed nullable FK to todo_task, SET NULL), `task_origin` (false default), `timer_mode` (COUNT_UP default), `target_millis` (nullable), `outcome` and `next_step` (nullable); session metadata lives in the existing table. Extend all FINISHED invariants/validators for TARGET_REACHED. New `focus_goal_version` stores effective Monday, zone, target, weekday mask and revision with unique zone/effective-week identity; a singleton settings row stores goal visibility and catalog revision. New local tables hold break runtime and reminder configuration/delivery state. Include them in full reset and restore quiescing.

Archive 9 exports terminal sessions/reflections/task links/provenance, referenced tags, goal versions and portable goal settings. Load tasks/tags before linked sessions. Exclude open sessions, breaks, reminder configuration/delivery, alarms, runtime, command receipts, generation, selected packages and transient drafts. Export goal settings through an explicit portable column projection, not a local runtime table dump. Adapt archives 1–8 with COUNT_UP/null target, no task link, false provenance, no reflection and goals off. Preserve their existing focus/no-focus legacy semantics.

Validate imported task FKs, provenance, goal dates/zones/ranges, text, target/terminal consistency and timing in staging before replacement. TARGET_REACHED requires a timed session with total credit exactly equal to target; timed USER_STOP/INTERRUPTED must not exceed target. Other legacy reasons stay valid. Unknown enums or invalid rows reject the archive without touching live data. Export/import only referenced task IDs already present in the full backup; malformed dangling references reject, not silently reassociate.

Confirmed replacement cancels all focus alarms, quiesces mutations, clears local break/reminder state, rotates generation, replaces portable content and discards cached UI. Reconcile existing task/habit reminders through their own policy. Full reset clears all extension data. A stale callback/action/retained edit from the prior generation cannot mutate restored content. Backup failures leave live content intact.

## Acceptance map and final QA boundary

| Acceptance | Requirements | Owning implementation | Final QA cases |
| --- | --- | --- | --- |
| FP-A01 | FP-R01 repeat with explicit, exactly-once Start | FX02 | FX-Q01, FX-Q09 |
| FP-A02 | FP-R02 task linking/completion/privacy | FX01, FX03, FX10 | FX-Q02, FX-Q08, FX-Q09 |
| FP-A03 | FP-R03 target capping, recovery and uncredited breaks | FX01, FX04, FX05 | FX-Q03, FX-Q04, FX-Q09 |
| FP-A04 | FP-R04 independent reflection persistence | FX01, FX06 | FX-Q05, FX-Q09 |
| FP-A05 | FP-R05 versioned goals and zone semantics | FX01, FX07 | FX-Q06, FX-Q09 |
| FP-A06 | FP-R06 safe opt-in invitation delivery | FX08, FX10 | FX-Q07, FX-Q08, FX-Q09 |
| FP-A07 | FP-R07 revision-safe edits and tag merges | FX09 | FX-Q08, FX-Q09, FX-Q10 |
| FP-A08 | FP-R08 accurate actionable Review | FX07, FX10 | FX-Q06, FX-Q08, FX-Q11 |
| FP-A09 | Compatibility, replacement, localization and existing Focus regression | FX01–FX10 | FX-Q08–FX-Q12 |

Final QA begins only after FX01–FX10 are SOURCE COMPLETE — UNVERIFIED. FX-QA is a separate mandatory phase: author fixtures/tests, generate schema, run all layers, fix defects, rerun affected checks and complete integrated regressions. The final QA document is the execution contract. Missing tooling or device coverage is BLOCKED/NOT RUN, never acceptance by substitution. All mandatory cases and applicable carried FI/FM checks must pass before claiming integrated acceptance. Deferred device-wide enforcement remains unmet outside scope.

User-research hypotheses and opt-in measurement suggestions remain in the product review; this specification adds no telemetry. Builder decisions may choose internal UI composition and helper names, but must preserve these values, boundaries and acceptance semantics.
