# Calendar & Agenda QA scenarios

F07 adds cross-screen accessibility changes and its required matrix in [F07 QA scenarios](feature-roadmap/07-accessibility-qa.md), with source findings in [the F07 handoff ledger](validation/roadmap-07-accessibility-findings.md). Those F07 scenarios remain **NOT RUN**; this document does not certify the changed source.

Prepared: 2026-10-25  
Status: **CAL-Q01 PASS (456 host tests; build, test APK assembly, instrumentation compilation and lint). CAL-Q02 connected checks BLOCKED before tests ran; CAL-Q03 manual matrix BLOCKED. Full scenario acceptance PENDING.** See the [execution record](validation/2026-10-25-calendar-agenda.md) for evidence and unrun checks.

[Specification](superpowers/specs/2026-10-25-calendar-agenda-design.md) · [Plan and commands](superpowers/plans/2026-10-25-calendar-agenda.md) · [Task status](CALENDAR_TASKS.md)

## Entry, environment and evidence

Start only after CAL-I01–I06 are source complete. Test authoring, Gradle/build/lint and emulator/device checks occur only in QA. No result below is pre-certified by source inspection. Use an isolated QA install/AVD and synthetic records; preserve existing personal app data. Connected tests can reinstall/uninstall their target app, so confirm the isolated target before running them.

Record actual date/time, source revision and dirty patch identifier, app/test version and variant, device/emulator/API, device zone, locale, 12/24-hour setting, app/system theme, window dp, orientation and font scale. Retain test logs, screenshots and accessibility/target-bound evidence at `docs/luma-redesign/evidence/<actual-run-date>/calendar/`. Link results in `docs/validation/2026-10-25-calendar-agenda.md`.

Freeze `today = 2026-10-22` using injected clock/current-day fixtures for automation. Manual time-change checks use an isolated AVD or equivalent fixture controls, not the user's daily device. Gregorian date values remain unchanged across locales. If manual clock control is unavailable, shift the entire fixture by the same day offset and record exact dates/expected sets.

The 2026-10-25 follow-up added and passed domain/ViewModel host coverage and authored/compiled focused Room and Compose cases. Connected tests could not install their test APKs because the isolated API 36.1 AVD never started Android's PackageManager; manual CQA cases and matrix cells remain **NOT RUN**. Host coverage is not a pass for the complete CQA scenarios. See the execution record for exact commands and failure output.

## Baseline fixture F1

Use stable distinct IDs/creation timestamps so ordering can be asserted. All task times below are floating civil minutes. Notes include a long English preview and Persian/French titles; optional fields stay absent unless specified.

| Key | Record | Due/occurrence | State |
| --- | --- | --- | --- |
| A | Task “متن قرارداد” | Oct 19, no time | Open; overdue |
| B | Task “Review checklist” | Oct 21 at 09:05 | Open; overdue |
| C | Task “تسک سوم” | Oct 22 at 17:00 | Open |
| D | Task “تسک بدون زمان” | Oct 22, no time | Open; anytime today |
| E | Task “Midnight follow-up” | Oct 23 at 00:00 | Open; upcoming |
| F | Task “Unscheduled” | No date/time | Open; excluded from Calendar |
| G | Task “Completed” | Oct 22 at 08:00 | Completed; excluded from Calendar |
| H1 | Habit “Water”, target 8, all weekdays, active from Oct 1 | Saved tracking zone Asia/Tehran | Oct 22 count 2/8 |
| H2 | Habit “Stretch”, target 5, Thursday only, active from Oct 1 | Same tracking zone | Oct 22 count 1/5 |
| J1 | Journal “Autumn reflection” with saved body | Oct 22 at 10:00 in device zone | Unmarked journal; excluded |
| M1 | Memory “Autumn walk” with saved note | Oct 22 at 11:00 in device zone | Saved standalone memory |

Default device zone Asia/Tehran, Month, All Items, selected Oct 22. Expected baseline:

- Task rows in order A, B, C, D; Tasks = 4; Overdue = 2; “Includes 2 overdue”.
- Exact-date ribbon: 2 tasks and 1 memory. All Items = **5** (4 task memberships + 1 memory), intentionally correcting inconsistent mockup sample badges.
- Oct 22 task marker count = 2; Oct 19 and Oct 21 markers each count 1 and overdue; Oct 23 marker count 1 and not overdue. Completed G and undated F contribute no task marker.
- H1/H2 and J1 contribute no Calendar rows, counts, dots, legend or accessible count text. Memory M1 shows its saved note; no task hashtag chips or sample quote.

Reset F1 between independent cases. Additional fixtures: F2 = 123 open tasks on one date with varied/tied times and stable IDs; F3 = standalone memories, memory-marked journals/moods, unmarked records, tied timestamps, >50 memories and boundary instants; F4 = habits with schedule versions, paused/archived/not-started/skip/target-reached states used to prove complete Calendar exclusion. Each case specifies its extensions.

## Scenarios

Every scenario's initial result is **NOT RUN**. P0 means data/navigation/mutation correctness; P1 means required visual/access/lifecycle acceptance. Both priorities are release requirements.

### CQA-01 — Main-bar Calendar and retained tab state (P0; CAL-01)

Expect exactly five tabs in order Today, Timeline, Calendar, Review, More, with Calendar in the former Habits slot and no Habits tab. Given Today browses Oct 21, tap Calendar for the first time: expect device today Oct 22, Month/All Items and F1 membership. Change Calendar to Oct 23/Week/Tasks and scroll; switch tabs then reselect Calendar: expect its retained state and selected tab indicator, without duplicate Calendar instances. Today retains its own Oct 21 date/scroll. Calendar has no page Back arrow; system Back returns to Today following existing primary-tab behavior.

### CQA-02 — More → Habits, legacy links and fallback state (P0; CAL-01)

Open More: expect a visible Habits row before Tasks and no duplicate Calendar row. Open Habits: expect the full existing section at device today, with working Create/log/history/management actions and a Back affordance; toolbar/system Back returns to More. Browse a date, open a habit detail/editor and return: retain habit date/scroll. Reopen from More: start today. Exercise dated Today/Timeline/Review/widget links and a restored legacy `habits` back stack: expect correct target/date and caller return, not primary-tab stack clearing. Habits browsing must not change Today/Timeline or Calendar dates. With malformed/out-of-range restored Calendar date expect today fallback. Missing calendar provider shows unavailable state within its primary tab; other tabs remain usable. Existing More Tasks/Settings/catalog entries still work.

### CQA-03 — Navigation to existing records (P0; CAL-01/05/06)

From Calendar open task C, memory M1 and View all tasks; Back after each. Expect original IDs and retained Calendar selection/filter/scroll. Open memory-marked journal/mood rows: expect the existing memory route and its original-record actions. Add memory uses the selected date and existing capture validation. The five new primary destinations and existing habit/widget entry targets continue resolving; Today’s existing habit previews still open their dated targets.

### CQA-04 — Month/week behavior (P0; CAL-02)

Select Oct 22, switch Week, Next, Previous, then Month. Expect seven real dates, selection moves +7 then -7 days, Month restores the containing month, mode switch alone preserves date, and arrow names say week/month correctly. Today returns to T without changing current mode/filter.

### CQA-05 — Month arithmetic and leap years (P0; CAL-02)

Select Jan 31, 2027; Next twice then Previous. Expect Feb 28, Mar 28, Feb 28. Repeat Jan 31, 2028 → Feb 29; verify leap century 2000 and non-leap 2100. Grid contains all month dates exactly once with valid weekday alignment and no fixed-millisecond arithmetic drift.

### CQA-06 — Overflow, 4/5/6 weeks and year boundary (P0; CAL-02)

Use a Monday-first fixture for Feb 2021 (28 cells), Oct 2026 (35) and Aug 2020 (42). Tap an adjacent-month date and navigate Dec/Jan. Expect correct selection/month/ribbon and recomputed agenda; each complete row has seven slots and no duplicated date key. Repeat a week spanning Dec/Jan.

### CQA-07 — Minimum/maximum supported dates (P0; CAL-02)

Browse Jan 1, year 1 and Dec 31, 9999 in both modes via controlled state. Expect outside-range padding inert, arrows disabled only when movement would leave supported years, no overflow/crash, valid last-period dates still selectable. Attempt restored state outside range: safe today fallback.

### CQA-08 — Locale week alignment (P1; CAL-02/07)

Switch EN, FR and FA with the same selected civil date. Compare headers and week boundaries with `WeekFields.of(activeLocale).firstDayOfWeek`. Expect actual dates under correct headers, localized month/date labels and Persian RTL, with unchanged stored date and filter. No unintended Jalali conversion.

### CQA-09 — Today carry-in and exact-date counts (P0; CAL-03)

Load F1 on T. Assert exact ordered task IDs A/B/C/D, Tasks 4, All 5, Overdue 2, ribbon 2 tasks/1 memory and carry-in caption 2. Switch Tasks: only task rows remain; grid markers unchanged. Switch All: M1 returns; H1/H2 and unmarked J1 remain absent. Counts are not copied from PNG values.

### CQA-10 — Historical/future and global Overdue scope (P0; CAL-03/04)

Select Oct 21: only B and a real Overdue label relative to T, without A carry-in. Select Oct 23: only E at midnight, no overdue rows carried into scheduled section. In either date choose Overdue: exactly A/B, explicit as-of-T scope, selection unchanged. Return Tasks: exact selected-date membership restored.

### CQA-11 — Completed and undated exclusion (P0; CAL-03)

Verify F and G absent from Calendar task rows/counts/dots. Open View all tasks and find them in the appropriate existing lists. Give F a date equal to D and save: it appears exactly once and counts update. Remove that date: its time clears and Calendar removes it without deleting the task.

### CQA-12 — Markers and accessible date descriptions (P1; CAL-03/06)

With F1 inspect Oct 19/21/22/23 and empty dates. Expect one dot per present type (task/memory), max two; task dot on overdue dates uses error role. Accessible label includes full date, selected/today state, type counts and overdue meaning. Legend supplies text; no pulsation. Completing a task updates only its original due-date marker, not a carried-in date.

### CQA-13 — Ordering and >50-row paging (P0; CAL-03)

Load F2 with 123 tasks on one date, including 00:00, tied 09:05, 23:59 and absent time. Expect 50 then 100 then 123 unique IDs on Load more, stable due-time/creation/ID order, timed before date-only, full count 123 throughout. Filter/date changes reset to 50. Concurrent insert/remove followed by refreshed pages produces no duplicate IDs or hidden records. Tasks on dates beyond the global Tasks first page remain discoverable here.

### CQA-14 — Civil due times and date-based overdue (P0; CAL-04)

At 10:00 set an open task due today 09:00. Expect Due today, not Overdue. Test absent time, 00:00, 09:05 and 23:59 in 12/24-hour formats and all locales; no date-only task gets a fake time. Switch device zone: saved date/minute stays exact. On next civil day it becomes overdue.

### CQA-15 — Midnight/foreground refresh (P0; CAL-04/07)

Keep Oct 22 selected and advance T to Oct 23, including background/resume path. Expect selected date unchanged, exact-day C/D membership (carry-in removed), global Overdue A/B/C/D = 4, E due today on its date, and Today action targets Oct 23. No old-day label under fresh totals. Reset pagination appropriately.

### CQA-16 — Memory DST and device-zone grouping (P0; CAL-04/06)

Use device America/New_York on Mar 8 and Nov 1, 2026. Insert F3 memories immediately before/at next local midnight and through DST gap/overlap. Expect [start, next start) grouping, each actual record once, no assumed 24-hour boundary. Keep habits in Asia/Tehran: they remain completely absent, without a habit-zone caption. Compare memory markers, ribbon and rows after changing device zone; each uses the same local interval. Civil task times in a gap/overlap remain entered minutes.

### CQA-17 — Add task defaults and save/cancel (P0; CAL-05)

Select Oct 23; tap Add task in All, Tasks and Overdue. Expect existing editor due Oct 23, no time, empty title. Cancel/dismiss/Back without Save adds nothing. Save a valid task: exactly one row on its due date, counts/dots update, one acknowledgement. Existing validation and due-time controls remain intact.

### CQA-18 — Dated Schedule versus existing Quick Capture (P0; CAL-05)

On Calendar Oct 23 tap Schedule: see task-specific “Task due [Oct 23]”. Toggle record Now/selected-date chips then choose Task: due date stays Oct 23 with no time. Choose Mood/Journal/Memory/Habit instead: existing record chip date rules apply. Dismiss writes nothing. Repeat Tasks from Timeline/Review Quick Capture: remain undated. Existing task callbacks compile and fire only once.

### CQA-19 — Completion, repeated tap and Undo (P0; CAL-05)

Complete C and repeat tap while pending. Expect one revision-safe write; after success task rows A/B/D, Tasks 3, ribbon task count 1, All 4. Undo restores C with the same original date/time and new revision; all baseline counts return. Filter/date navigation while pending does not duplicate acknowledgement or lose the recovery action.

### CQA-20 — Retry, stale revision and missing task (P0; CAL-05)

Inject write failure; expect visible recoverable error and no false success count. Retry uses the same mutation token. Concurrently edit/delete target before completion or Undo: expect conflict/missing recovery, no overwrite/resurrection, reload of current data and ability to continue. Leave the original filter while failure is pending: recovery remains reachable. Undo after another edit cannot restore stale content.

### CQA-21 — Edit, reschedule, menu and confirmed delete (P0; CAL-05)

Open C through title/menu while another date is selected. Expect original due value untouched. Change C to Oct 23 09:05 and save: removed from Oct 22, appears on Oct 23, existing widget/Tasks view agrees. Menu Delete enters existing confirmation; cancel retains row, confirm removes once. No draft is saved merely by opening/editing/canceling.

### CQA-22 — Recreation, process restoration and language (P0; CAL-02/07)

Select future date, Week, Tasks, scroll; rotate, recreate, simulate process saved-state restoration and switch app language. Expect selected date/mode/filter retained, valid list position restored/clamped, limit resets to 50 on process restoration, no serialized stale rows and no duplicate save/completion. Repeat with open task editor and Schedule sheet using existing draft/date rules.

### CQA-23 — Back, sheet/editor focus and IME (P1; CAL-05/07)

Open Schedule, dated task editor, time picker and delete confirmation. Exercise system Back, outside dismiss, close and cancel, including IME open. Expect only the top surface closes each time; parent Calendar state retained; no save or completion on dismissal. Save/Cancel and validation remain reachable above keyboard. Focus returns to a sensible initiating control.

### CQA-24 — Restore invalidation (P0; CAL-05/07)

Start a pending completion and open a task draft; simulate/archive-restore replacement in isolated QA. Expect generation invalidation, old rows cleared/reloaded, pending pre-restore mutations not replayed, editor baseline conflict/reload behavior preserved. Calendar reflects restored due dates/counts. Existing backup format and database version remain unchanged.

### CQA-25 — Rapid switching and late responses (P0; CAL-07)

With controllable slow/failing flows select A-date → B-date, then switch filter/zone while A's response arrives late. Expect old content cleared immediately and only current request key rendered. No old-date task/memory under new header. Repeat during Load more and restore; cancellation does not become a user-visible data error.

### CQA-26 — Room observation and cross-surface consistency (P0; CAL-03/07)

Edit/complete/reopen tasks externally via existing Tasks/widget path; create/edit/delete/move memories; mark/unmark moods/journals; change habit schedules/logs; return to Calendar. Expect task/memory counts, markers and rows converge to saved data, with no Calendar change from habit mutations, using full query counts and stable IDs without manual refresh. In-memory assumptions cannot mask incorrect SQL results. Check no new schema tables, version or archive changes.

### CQA-27 — Complete habit exclusion (P0; CAL-03/06)

Start with a habit-only day and F4 schedule versions, weekday exclusions, paused/archived intervals, future starts, skipped and target-reached states. Expect zero task/memory totals, no habit section, rows, progress, dot, legend, zone caption or habit count in TalkBack date descriptions. Create/log/skip/pause/reschedule/archive habits and return: Calendar totals/markers do not change. Inspect repository/provider/state wiring: no Calendar habit subscriptions or queries remain. More → Habits, Today habit previews and existing non-Calendar dated links still work.

### CQA-28 — Memory membership, content and paging (P0; CAL-06)

Extend F3 with standalone memories, marked journals/moods, unmarked journals/moods, imported saved memories, tied timestamps and 123 memories on one date. Expect one count per event with a memory row; unmarked records are excluded. Order occurrence descending/ID ascending, pages 50 → 100 → 123 unique IDs with full total 123 throughout. Task and memory Load more controls are independent. Date/filter/day/zone changes reset both limits to 50; restoration does not serialize old rows.

Assert title from the saved event/journal (fallback Memory) and preview preferring nonblank memory note, then journal body, mood note and event note; whitespace collapsed and ≤160 Unicode code points, including emoji. Open exact event ID through the existing memory route. Mark/unmark a journal or mood: its count changes once and source content remains intact. Move/delete memory: old/new date rows, ribbon and markers converge. Zero memories shows the honest empty state and Add memory opens selected-date capture; cancel adds nothing, save adds once, and future-date validation remains explicit. No sample prose appears.

### CQA-29 — Empty, partial failure and Retry (P0; CAL-07)

Use no records, then inject independent failures for tasks, task marker reads, memories and memory marker reads. Expect honest empty states only on successful empty reads; unavailable totals show dash/explanation, not zero. Healthy sections stay useful; Retry targets failed source and recovers without resetting date/mode/filter. All/Tasks/Overdue with zero matches retain usable navigation and Add task. Memory failure does not show “No memories”. Failed Load more preserves the loaded rows and offers Retry; the full count never becomes the page length.

### CQA-30 — Light/dark visual parity (P1; CAL-07)

Capture F1 light and dark at identical date/window. Compare matching PNG plus HTML roles: editorial title, rounded calendar/task cards, warm/obsidian canvas, selected date, error/memory accents, count ribbon, chips, note preview, memory rows and Schedule. Values derive from F1, not sample counts. Habit/reflection blocks and their counts/dots are superseded by tasks and memories; task tags/profile/dead filters/four-tab mockup shell are absent deliberately; the main bar shows Today/Timeline/Calendar/Review/More in both themes and Habits is under More. Measure text ≥4.5:1 and large text/essential indicators ≥3:1. System mode follows system appearance; explicit theme persists on recreation.

### CQA-31 — Narrow/large text/RTL/landscape/expanded (P1; CAL-07)

Run the matrix below, including long Persian/French/mixed-direction task names, notes and dates. Expect no clipped/overlapping actions, wrapped headers/ribbon/badges, seven distinct ≥48 dp date targets in scrollable ≥336 dp viewport at 320 dp width, selected date visible, reachable last row/Load more/Retry/FAB/snackbar above bars. At 840 dp reading width is capped; landscape and IME do not hide required controls.

### CQA-32 — TalkBack, keyboard and regression gate (P1; CAL-01/07)

Traverse grid, toggles, date arrows, chips, task title/checkbox/menu, memory links and Add memory, Retry and Schedule with TalkBack and keyboard. Expect separate named actions, selected/checked/disabled/pending states, meaningful full-date/count announcements, Calendar tab selection and More → Habits entry/Back semantics, logical traversal and no focus jump after data refresh. Execute existing task due-time, Today, Timeline/Review Quick Capture, habits, navigation/language, restore and widget regressions; name any pre-existing failures and retain unresolved acceptance status.

## Required QA matrix

Use the representative fixture consistently. Pairwise coverage is acceptable for ordinary combinations; explicitly run every stress row below. Record each cell rather than implying all combinations passed.

| Axis / mandatory combination | Required evidence |
| --- | --- |
| EN/FR/FA × Light/Dark at 411 × 891 dp, font 1.0 | Six aligned screenshots and core browse/add/complete/Back results |
| System theme, light→dark→recreate | Appearance follows system, explicit overrides still respected |
| 320 dp × font 2.0 × EN/FR/FA × both themes | Six stress captures; date target bounds, scroll/action access |
| 411 dp landscape × font 2.0 × FA × both themes | RTL, insets, IME, final row/action reachability |
| 840 dp width × EN and FA × both themes | Reading-width cap, seven columns and usable keyboard navigation |
| Android 12/24-hour preference × EN/FR/FA | Null/00:00/09:05/23:59 labels and editor return fidelity |
| API 28 and API 36 | Core navigation, Room reads, create/edit/complete/Undo and locale/theme smoke on both; full automated suite on primary QA API |
| Device-zone changes; DST gap/overlap; different habit tracking zone | Civil tasks unchanged, consistent memory day grouping; habits remain absent |
| TalkBack and hardware keyboard | Named/selected states, focus order and all actions reachable |
| Offline airplane-mode smoke | Calendar/capture reads and writes work with no new permission/network prompt |

## Result and defect template

For each CQA ID and matrix cell record: `PASS / FAIL / BLOCKED / NOT RUN`, actual run time, exact fixture/date/IDs, build/patch, steps executed, expected versus actual, evidence path and defect ID. Log skipped automated tests separately. A defect includes severity, reproduction rate, affected locales/themes/APIs, suspected owning CAL-I task, fix revision and regression rerun evidence.

QA exit requires all P0/P1 cases and required matrix checks passing on the final implementation, with no unresolved data loss, stale revision overwrite, false count, inaccessible action or broken navigation. Environment-blocked checks remain BLOCKED; they cannot be relabeled PASS. Existing project release blockers remain independent. Until these conditions are met, Calendar acceptance is **PENDING**.
