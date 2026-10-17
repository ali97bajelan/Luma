# Counted habits, reminders and navigation

Date: 17 October 2026. Status: agreed product direction; implementation handoff
prepared. Implementation has not started. The user's approval to create the spec,
plans and tasks authorizes preparing this complete handoff together.

## 1. Intent and source of truth

Help a person repeat an action, record each occurrence quickly, and see those
occurrences within their private life history. A habit may require several
repetitions per day. Habits has its own primary destination. Optional repeating
reminders support the action without becoming evidence that it happened.

This is the approved extension to the October v0.1 definition, whose original
future-phase list included lightweight habits. It supersedes the earlier proposal
of binary-only habits, no reminders, and access only through Today. It does not
replace Luma's life-record purpose or close outstanding MVP validation.

Approved decisions:

- Primary destinations: **Today, Timeline, Habits, Review, More**, in that order.
- A positive whole-number daily repetition target, with a user-written unit label.
- One intentional logging action records one repetition; every saved repetition
  has its own Timeline item, correction and deletion.
- Optional normal notifications at selected times or intervals during active hours.
- Local-only storage, offline behavior, complete backup/restore and reset support.

The user's final agreement accepts ordinary notifications as the working scope.
Persistent ringing, exact alarm-clock behavior, variable quantities (for example
millilitres), timers, weekly quotas, streaks, rewards, automatic completion, AI
coaching, and medical target suggestions are excluded. Unit labels describe counts;
they do not introduce unit conversion. No new Gradle module or dependency upgrade.

## 2. Global implementation constraints

- Keep Kotlin 2.2.10, JDK 17, minSdk 28, compileSdk 35 and targetSdk 35.
- Keep the current Compose/Material 3, Navigation 2.9.3, Room 2.8.5, coroutines 1.10.2 and WorkManager 2.10.0 dependencies.
- Keep core:domain free of Android and Room dependencies; app wires implementations.
- Add no account, network permission, telemetry, cloud service or AI dependency.
- Use English, Persian RTL and French resources for all app-owned text.
- Preserve existing records, selected dates, editor drafts, usage visibility and navigation restoration.
- Never use destructive database migration or replace a user's install/data for validation.
- Reminders are ordinary, approximate local notifications, off by default; no exact-alarm, full-screen-intent or foreground-service permission.
- Keep existing entry/journal/memory counts unchanged; count habit repetitions separately.

## 3. Information architecture and screens

| Destination | Responsibility |
| --- | --- |
| Today | Existing daily overview plus a compact habits preview and direct logging |
| Timeline | Existing records and each habit completion, with truthful time precision |
| Habits | Selected-day progress, creation, history, schedule and reminder management |
| Review | Existing weekly reflection plus a separate habits section |
| More | Prominent People & Places and Settings entries using existing content routes |

More is a labelled list with descriptions, not an unlabeled icon grid. Its Settings
entry retains language, integrations, backup and privacy controls. Existing
People/Places links from capture, Review and entries remain direct. Their secondary
routes have Back and return to the actual origin; editors/details keep their
focused navigation. Preserve legacy `people`, `settings` and nested route names
as working secondary destinations rather than discarding saved destinations.

Keep Quick Capture's mood, journal and memory actions. Future voice capture can
extend it; future retrieval extends Search; longer reviews extend Review; sync
and new integrations extend Settings. Such features do not each require a tab.

Habits list: date controls, Add habit, stable rows with name, `3 / 8 glasses`,
status and a separate `+ Log` button. The name opens details and must not toggle a
log. Keep row order stable after logging, initially creation order (oldest first,
UUID tie-breaker). Archive management is accessible from the list. Today shows
at most three scheduled habits in the same order, then View all. A persistent
Habits link remains when none are scheduled. Preserve room for the existing feed.

Create: name, daily target, optional unit label, start date, repeat weekdays;
reminders are a separate optional disclosure delivered by T28. Default target 1,
unit label displayed as localized "times", all weekdays selected, start today.
Blank unit input persists null and renders through the localized default plural
resource; it must not save the current language's word as user text. Name is
trimmed, 1–100 Unicode code points; an explicit unit label is 1–30 after trimming;
target 1–999; at least one weekday. User text is never translated. Start is today
or a later date and is retained independently of later schedule versions.
Save only dismisses after durable success; failures retain fields. Dirty Back
confirms discard. Stable create IDs reconcile a save after recreation.

Detail shows selected-date progress, per-log history, reminder status and explicit
Edit, Pause/Resume, Archive and Delete actions. Archive is the ordinary way to
retire a habit. Permanent deletion confirms deletion of that habit, all logs,
day choices and Timeline items; exported backup copies are unaffected.

## 4. Counted logs and daily states

A log has a stable UUID, habit ID, attributed civil date, saved tracking zone,
optional occurrence instant, recorded-at instant and updated-at instant. Amount
is always one. Count is the number of surviving logs, not a mutable counter.

- Every accepted + Log activation creates a fresh action/log UUID. Distinct
  activations, including rapid ones, remain distinct; queue writes per habit.
- Retrying the same UUID with the same payload returns the existing log. A
  different payload for that UUID is a conflict, not a second completion.
- Only committed logs increase confirmed progress. Keep failed queued actions
  identifiable and retryable; a failure must not erase an earlier committed log.
- Undo deletes exactly the indicated log, idempotently. Persistent history edit
  and delete remain after transient feedback disappears.
- Log editing changes only attributed date and optional occurrence time, using
  the same historical-schedule, future and DST validation as creation. Keep ID,
  habit ID and recorded-at; moving dates updates both daily totals and relocates
  the same Timeline item transactionally. Failed edits retain the committed
  original and the entered correction draft.
- Targets do not cap logging: nine repetitions against a target of eight shows 9/8.
- Logging requires a scheduled date from the start through habit-today. Correcting
  or deleting existing logs remains available while paused/archived. No future log.

| Condition | Display meaning |
| --- | --- |
| Scheduled, zero logs | No check-in recorded; today remains in progress |
| Scheduled, count below target | Partial progress, with count/target |
| Scheduled, count at least target | Target reached, retaining actual count |
| Not a selected weekday / before start | Not scheduled; no expected target |
| Paused / archived period | No expected target; history remains accessible |
| Skip remaining selected | Existing count/target plus "Remaining skipped" |

Skip remaining is an explicit dated choice, not synthetic completions. It
suppresses that day's remaining reminders and preserves its target/logs. A later
intentional log is allowed and keeps the skip choice; "Resume reminders today"
clears it explicitly. With zero logs show "Skipped"; with partial logs show both
partial progress and the skip label. Missing logs never assert failure or prove
that the action did not happen. There is no overdue queue or carried-forward debt.

## 5. Dates, history and lifecycle edits

Choose one app-wide Habits tracking `ZoneId` at first creation, defaulting to the
device zone and explaining it in setup. It is fixed for this release and included
in backup. Preserve civil dates across travel. When following today, the habits
section uses that zone's today; when different from device context, display its
full date and zone. An explicitly browsed civil date applies unchanged to Today,
Timeline and Habits; do not convert a date through UTC to select a different date.

Log now captures the action's instant/date/zone before starting its write. Date
navigation during that write cannot move it. Past-date logging starts date-only;
the user may explicitly supply a time. Validate time against the attributed date
in the tracking zone, reject DST gaps and future instants, and preserve a selected
valid offset on overlaps (default the earlier offset for a new ambiguous time).
Date-only logs keep occurrence null; never manufacture midnight as an occurrence.
Detail labels the attributed date, time/"Time not recorded", zone, and recording
time. Edits preserve original recorded-at and advance updated-at.

Weekday/target edits and pause/resume/archive apply **tomorrow in the tracking
zone**, with an explicit preview. Today and earlier schedule versions survive.
Multiple pending edits for the same effective date update one pending version;
allow cancelling that pending change. Pause/archive labels state their effective
date; today's rows/logging remain until then. Rename/unit-label edits apply
immediately; labels are current catalog text, while historical numeric targets
and date eligibility are preserved. New reminders/off/skip controls act immediately.

For a habit whose start is still future, target/weekday edits instead revise its
initial version at the original start date; they cannot bring the start forward.
Pause/Resume is unavailable before start. Archive is immediate before start and
replaces the initial ACTIVE state with ARCHIVED, preventing automatic activation;
Delete remains available. These initial-configuration edits do not use the
started-habit Cancel pending change action. Once started, the tomorrow rule applies.
Show effective date and Cancel pending change on detail and a compact pending
label on today's row. While pause/archive is pending, today's log/edit/skip and
reminder-off controls remain enabled. Another lifecycle/target edit replaces the
pending version with an explicit preview; cancelling removes only that version.

## 6. Timeline and Review

Habit records are a separate domain aggregate, not fake journal/activity entries.
Persist logs without requiring a `life_event` row. This avoids changing existing
non-null occurrence invariants and entry counters solely for date-only logs.

Extend Timeline's read model with a sealed item: existing entry or habit log.
Every log appears exactly once on its attributed habit date and opens its source
log detail. Timed logs merge with existing timed rows by actual instant, newest
first, then source kind and stable ID. Display the habit-zone time/zone when it
differs from the screen zone. Date-only logs form a labelled "Time not recorded"
group before the timed group, ordered by recorded-at then ID, newest first.
No invented occurrence is used for ordering or display.

Use one indexed UNION projection before LIMIT/OFFSET, not independent limited
queries merged in memory. Include logs by saved civil date, existing entries by
the selected zone's instant bounds. Preserve page size 50, initial 150, maximum
300 visible items, app-usage filtering, coherent counts, invalidation and scroll
anchors. A skipped civil day with no instant range can still contain date-only
habit logs. Prefix keys (`event:`, `habit:`) to avoid identity collisions.
Existing TimelineRepository.page/get remain available to existing callers; add a
feed API returning the mixed page. No habit global-search or memory-promotion
support in this slice. Habit names/log history remain accessible through Habits.

Review adds a separate bounded per-habit section for the selected ISO week, with
per-date count/target and reached/partial/no-record/skipped annotations. Totals
show repetitions and scheduled days; skipped days retain their targets. Future,
not-scheduled, paused and archived dates have no expected target. Identify today
as partial. Compare matching civil weekdays using historical targets in the saved
habit zone; do not mix unit totals across different habits. Source opens habit
history on that date. A habits-only week must not show an overall empty Review.
No percentage, streak, causal claim or new mood/health inference. Existing review
calculations/caches keep their definitions; habits are queried separately.

## 7. Normal notification reminders

Default off per habit. Configuration is independent of daily target:

- Specific times: 1–24 distinct minute-of-day values, canonical sorted order.
- Interval: 15–720 minutes in 15-minute increments, anchored at active-window
  start; same-day window start inclusive, end exclusive (end may be 24:00).
- Reject overnight windows in this release. Show the zone and readable schedule.
- Generate local clock-time slots in the fixed tracking zone. Skip DST-gap slots;
  use only the earlier offset for overlap slots. Interval means the displayed
  clock grid, not an elapsed-time promise during a DST transition.

Example: every 120 minutes from 08:00 to 20:00 yields 08, 10, 12, 14, 16, 18;
it does not infer a target of six. Normal approximate delivery can be delayed by
Android. No exact-alarm permission, full-screen takeover, DND override, continuous
ringing or continuous background service. Request Android 13+ notification
permission only after explicit enablement. Show blocked/channel-disabled status
and a settings action without blocking tracking or repeatedly prompting.

Stop remaining reminders once target is reached or remaining is skipped; deleting
a log can re-enable only future eligible slots. Disable/edit/off cancels obsolete
pending alarms and posted notifications immediately. Schedule/target/status
changes invalidate delivery eligibility on their effective date. No catch-up
burst after reopening, reboot, clock change or delayed delivery: deliver at most
one still-current eligible slot, otherwise schedule the next future slot. A
slot expires at the next nominal slot or tracking-day end, whichever comes first.

Use one pending inexact AlarmManager alarm per habit, selecting the earlier of
its next regular slot and a snooze. Snooze is ten minutes from the action; if it
would exceed the current active window/day, do not schedule it and explain in
the app. Specific-time mode's window is that civil day. A snooze is separately
identified but replaces the current posted reminder. Coalesce with an earlier
regular slot rather than delivering two nearby notifications.

Notification actions: Log one, Snooze, Open. Only Log one adds a repetition.
Use the actual action time, not the scheduled reminder time. Validate habit ID,
date, unique reminder revision, slot identity and current eligibility inside the
write boundary. Reject stale-day/disabled/deleted/replaced actions without writes;
Open still reaches the identified habit/history when it exists. Replayed Log one
uses the same deterministic action UUID and cannot create a second log, including
after undo of that notification-created log; retain its consumed token in runtime
state until the slot expires. Permission-blocked users can always open the app
and log manually. One private notification per habit; generic lock-screen public
text, with habit name in unlocked content. Let the OS/channel control sound and
vibration; do not add a custom sound picker.

An explicitly invoked stale/invalid action cancels its actionable notification
and stores a bounded device-local feedback code for the destination. When allowed,
replace it with informational text and an Open action: "This reminder is no
longer current. Nothing was logged." An out-of-window snooze similarly says
"No reminder time remains today. Nothing was snoozed." Show the same feedback
when the app next opens that destination if notifications are blocked. Passive
expiry/delivery rejection does not create an informational notification. Deleted
sources open an unavailable state with this feedback. Feedback has no habit text
in the device-local registry and is excluded from portable backup.

Reconcile desired schedules after app foreground, configuration/eligibility
changes, boot, app replacement, and clock/timezone broadcasts. Device timezone
changes never rewrite the saved tracking zone or history. Receivers delegate
bounded work to the existing WorkManager runtime. Persist desired configuration
and runtime tokens before scheduling; startup/worker reconciliation repairs an
interrupted scheduler call. Expose Pending/Error rather than asserting delivery
success before scheduling succeeds. Force-stopped/powered-off devices cannot be
promised delivery; resuming rebuilds future slots without a backlog.

Official implementation references, checked 17 October 2026:
[alarm scheduling](https://developer.android.com/develop/background-work/services/alarms),
[notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission),
[notification creation/actions](https://developer.android.com/develop/ui/compose/notifications/create-notification).

## 8. Persistence, migration and portability

Additive Room schema **2 → 3**, preserving all original tables and migrations.
Export and retain generated schemas 1, 2 and 3; the existing untracked 1/2 exports
must be reviewed and included before making the migration. No destructive fallback.

| New table | Key fields and constraints |
| --- | --- |
| habit_settings | singleton key 1; tracking_zone_id (ZoneId string) |
| habit | UUID PK; name; unit_label nullable (localized default); start_date ISO date; created_at; updated_at |
| habit_schedule | UUID PK; habit_id FK cascade; effective_from ISO date; target 1–999; weekday mask 1–127; state ACTIVE/PAUSED/ARCHIVED; UNIQUE(habit_id,effective_from) |
| habit_log | UUID PK; habit_id FK cascade; habit_date ISO date; zone_id; occurred_at nullable epoch millis; recorded_at; updated_at |
| habit_day_choice | composite PK(habit_id,habit_date); habit FK cascade; skip_remaining 0/1; updated_at |
| habit_reminder | habit_id PK/FK cascade; enabled 0/1; mode TIMES/INTERVAL; times_json; interval_minutes nullable; start_minute/end_minute nullable; revision_token UUID |
| habit_reminder_runtime | habit_id PK/FK cascade; revision_token; last_delivered_slot nullable; consumed_action_token nullable; snooze_slot/snooze_until nullable; needs_reconcile 0/1 |

Use indexes on all FKs, `(habit_date, occurred_at, id)` for logs,
`(habit_id, habit_date, id)` for history and `(habit_id, effective_from)` for
schedule lookup. SQL/domain validation enforces null combinations, lengths,
dates, zones, UUIDs, boolean ranges, reminder modes and time/date consistency.
All target resolution, log mutations, skip choices and reminder revision/runtime
changes commit transactionally. Observe Room invalidations, never poll histories.
No pre-materialized row for every future date. Derived counts can be rebuilt.

Persist Monday as weekday-mask bit 0 through Sunday as bit 6. The first schedule
effective date equals habit.start_date, and later versions cannot precede it.
For reminder TIMES rows, interval/start/end are null; for INTERVAL rows,
times_json is the empty array and interval/start/end are present. Disabled rows
retain a valid configuration. Snooze slot/until are either both null or both
present; runtime requires a matching reminder revision. Audit updated-at cannot
precede its record's original created/recorded-at.

Backup format remains 1; schema-version inventory becomes explicit for 1, 2 and
3 (17, 19 and 26 tables). Do not derive the v1 list by dropping the last two from
the now-expanded master list. Old archives restore with empty habit tables;
schema-3 archives include all seven new tables and validate all graph invariants.
Singleton settings must exist whenever habit rows exist; every log's zone must
match it, every log/choice date must resolve to an active historical schedule,
and reminder runtime/config revisions must agree. Runtime data is validated as
untrusted input even though it is cleared on restore.

After replacement, retain habit history and desired reminder times, force
reminders off, assign fresh revision tokens and clear runtime/snooze/action
state. Cancel old pending work/notifications, then reconcile to none. Re-enable
only through user action on this install. No restored callback can log against
new data. Backup preview adds separate habit/log counts; existing event/photo
counts retain their meaning. Unknown schema versions still fail before writes.
App-data reset removes all new tables and OS-scheduled app reminders/notifications.
Test replacement rollback, earlier-archive compatibility and no-reminder restore.

## 9. Boundaries and contract ownership

Keep feature packages within the current modules:

- `core/domain/.../habit`: immutable habit/schedule/log/day/review and reminder
  values, pure date/count/schedule calculations and repository contracts.
- `core/data/.../habit`: Room entities/DAO/repository and mixed Timeline queries;
  backup code remains in its existing backup package.
- `app/.../ui/habits`: list/editor/detail/history and reminder configuration.
- `app/.../habits/reminders`: Android alarms, notification publisher, receiver,
  WorkManager worker and reconciliation. Inject Clock and platform boundaries.
- `app/.../ui/navigation` and `ui/more`: shell destinations and More hub; split
  focused navigation concerns out of the large LifeOsApp file.

Authoritative signatures and file ownership are specified in the plans. Keep the
existing Timeline API alongside its mixed-feed extension; habit logs are not
passed into the existing journal/mood editor routing. Weekly habit observations
are separate from WeeklyReviewRepository's life-entry projections.

## 10. Verification and release conditions

Required automated cases: multiple intentional logs and idempotent retry;
concurrent/recreated writes; per-log undo; partial skip/resume; above-target
progress; future/DST/date-only input; pending schedule edits; rollover/travel;
mixed Timeline paging at 500+ rows with app-usage filtering and mid-page writes;
historical Review targets; 1→3/2→3 preservation; schema-1/2/3 archive replacement,
rollback and invalid graph rejection; stale/replayed notification actions;
permission/channel denial, reboot/reconcile and no catch-up; pending/off controls;
More navigation and selected-date/draft restoration.

Verify English/French/Persian RTL, 320 dp and normal widths, portrait/landscape,
font scales 1.0/1.3/2.0, long user labels, light/dark and TalkBack. Controls have
48 dp targets, distinct open/log actions, full habit/count/target semantics,
stable focus and polite committed-save/error feedback. Manual TalkBack, OS
notifications, background/Doze, boot and API-specific permissions require actual
device evidence; unavailable devices are BLOCKED, never inferred passed.

Run the existing JVM suites, debug/test APK builds and lint; run affected Room
and app instrumentation on a disposable package. Record exact results in
docs/VALIDATION.md. Existing release/performance gaps remain separately open.
The feature is complete only when T22–T29 acceptance passes, including migrated
and restored records, not merely when screens compile.

## 11. Implementation order and traceability

| Task | Deliverable | Plan |
| --- | --- | --- |
| T22 | Domain, counted logs, date/history rules | habits-core |
| T23 | Schema 3, persistence, archive compatibility | habits-core |
| T24 | Functional habit list/editor/history and Today access | habits-core |
| T25 | Mixed Timeline feed and weekly habit review | habits-core |
| T26 | Five primary destinations and More hub | habits-navigation |
| T27 | Pure reminder schedule and durable configuration | habit-reminders |
| T28 | Android delivery, actions, permissions and configuration UI | habit-reminders |
| T29 | Full integration/device/accessibility acceptance | habit-reminders |

See [task handoff](../../HABITS_TASKS.md),
[core plan](../plans/2026-10-17-habits-core.md),
[navigation plan](../plans/2026-10-17-habits-navigation.md), and
[reminder plan](../plans/2026-10-17-habit-reminders.md).
