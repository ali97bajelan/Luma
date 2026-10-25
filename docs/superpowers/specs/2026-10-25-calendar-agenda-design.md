# Calendar & Agenda specification

Date: 2026-10-25  
Status: Scope revised on 2026-10-25: Calendar shows scheduled tasks and saved memories only. Source revision and QA evidence are tracked in the handoff.

## Intent and authorization

Add a calendar section for browsing dates and managing scheduled tasks, following the supplied light/dark PNGs and HTML. The deliverables include the implementation, this specification, a task backlog, an engineering plan and executable QA scenarios. The latest user instruction excludes habits from every Calendar entry, count and marker and replaces journal-only reflection with memories.

The user requires **all implementation tasks first, then a separate QA phase**. Do not run Gradle commands, tests or an emulator during implementation. Author and execute automated tests in QA. Source inspection and diff review are implementation checks, not proof of compilation or behavior. QA may return defects for correction, but testing remains in QA.

[Tasks](../../CALENDAR_TASKS.md) · [Plan](../plans/2026-10-25-calendar-agenda.md) · [QA scenarios](../../CALENDAR_QA.md)

## Evidence and implementation baseline

- References: [light PNG](../../luma-redesign/calendar-light.png), [light HTML](../../luma-redesign/calendar-light.html), [dark PNG](../../luma-redesign/calendar-dark.png), [dark HTML](../../luma-redesign/calendar-dark.html). Both images and both HTML files were inspected locally.
- `TodoTask`/`TodoInput` already support nullable `LocalDate` and minute-precision `LocalTime`. `TodoRepository` provides revision-safe mutations and bounded Today/list queries. `observeToday` includes open tasks due on or before its date; it is not an arbitrary-date agenda query.
- Room is currently version 6. No calendar entities, migration or backup format change is needed for a projection over existing records.
- `LifeOsApp.kt` owns navigation and `openTodoEditor(taskId, dueDate)`. Today Add can prefill a date. `QuickCaptureSheet.kt` currently uses `onTask: (() -> Unit)?`; its record-date chips do not date tasks.
- Before Calendar integration, the app had five primary destinations (Today, Timeline, Habits, Review, More). The four-tab mockup is not the current navigation contract.
- Habits use versioned weekday schedules and a saved tracking timezone; journals are timestamped records, not standalone scheduled notes. Tasks have no tag field. O09 task tags and O01 four-tab navigation remain deferred.

## Approaches and navigation decision

| Approach | Benefit | Cost |
| --- | --- | --- |
| Calendar replaces Habits in the main bar; Habits moves to More — user selected | Direct calendar access while keeping all habit features reachable | Requires migrating Habits from primary to secondary navigation and preserving old links |
| Separate Calendar route from Today and More | Preserves the original main bar | Extra navigation to reach Calendar; superseded by the user’s decision |
| Replace Today | Closest to mockup shell | Removes existing capture, habit progress and recent-entry layout |
| Embed full calendar in Today | Direct access | Makes Today much longer and couples independent date state |

The user explicitly selected Calendar in the main bar instead of Habits, with the full Habits section under More. The new main-bar order is **Today → Timeline → Calendar → Review → More**. This replaces the earlier secondary-Calendar proposal; the latest scope revision retains that navigation. Calendar includes scheduled task management and saved memories only. Habits remain available through More and existing non-Calendar entry points. It is a local projection, not a new event/scheduling subsystem.

## Global constraints

- Native Android/Kotlin/Compose Material 3; preserve minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- No new dependencies, runtime permissions, network services, database tables, schema versions or archive formats.
- Support System/Light/Dark, English/French/Persian, Persian RTL and the Android 12/24-hour preference.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL, IME and system insets.
- Preserve task IDs, title/note limits, due-date years 1–9999, minute precision, revision checks, completion/Undo, confirmed deletion and restore invalidation.
- Main-bar order: Today, Timeline, Calendar, Review, More. Habits is a secondary destination under More; retain its existing route and actions.
- All implementation tasks precede QA. No Gradle commands, test execution or emulator use during implementation; test authoring and execution belong to QA.

## Reference reconciliation

PNG supplies composition; HTML supplies styling details; the behavioral rules here govern native interactions. Keep warm-paper/obsidian surfaces, editorial headings, rounded calendar/task cards, terracotta selection/actions, memory accents, error badges and a floating Schedule action. Use shared theme/fonts/tokens rather than a second palette or remote fonts.

Resolve prototype inconsistencies explicitly:

- All Items badges, task/habit/note summary differences, and October 22 are sample values. Habit and journal-only sections in the references are superseded by the tasks-and-memories scope. Compute every count from saved data.
- The fourth task says “Anytime today”: interpret it as a **dated task without time**, never an undated task assigned to today.
- Task tags such as #Legal and #Work are absent from the model: omit them. Existing task notes may show one preview line in both appearances.
- HTML Month/Week scripts only change styling; native Week must render seven actual dates and period navigation must work.
- Light composition is more compact; dark has note previews and outlines. Both appearances expose the same actions, data and hierarchy; do not reproduce dark's accidental wrapped month/year or light's cramped controls.
- Keep the existing app shell with five main-bar slots, replacing Habits with Calendar in the third slot. Calendar uses a primary-page header without a synthetic Back button; More → Habits uses the existing Habits Back affordance. Do not copy the mockup’s profile avatar, dead filter icon or duplicate bottom navigation.
- The memories section uses saved memory records and source content. Never ship the sample quote. Omit all Calendar habit summaries, progress, counts, markers, legends and data subscriptions.

## Functional requirements

### CAL-01 — Entry and return navigation

Replace `PrimaryDestination.HABITS` with `PrimaryDestination.CALENDAR` in the same third main-bar slot. Use primary route `calendar` and localized label Calendar. Main-bar order is Today, Timeline, Calendar, Review, More. First entry initializes Calendar to device today, Month and All Items. Tab switching/reselection restores Calendar’s own selected date/mode/filter/scroll; it does not inherit or overwrite Today/Timeline dates. Malformed or out-of-range restored date state falls back to today without crashing. Calendar has no page-level Back arrow; system Back follows the existing primary-tab policy (return to Today, then existing app exit behavior). Child editors/details return to the retained Calendar instance.

Add a visible Habits entry in More, before Tasks, routed to the existing `habits` destination registered independently of the primary-destination loop. Pass the existing non-null `HabitsRoute.onBack` callback so toolbar/system Back returns to its caller. More → Habits starts at the current device date; explicitly dated Today/Timeline/Review/widget links keep their requested habit date. Reopening from More starts today; returning from a habit child retains the browsed habit date/scroll. Keep habit browsing date local to the Habits back-stack entry, independent of Calendar and Today/Timeline dates. Existing Today habit previews and habit capture choices remain; this change moves the full Habits destination, not saved habits or Today’s cards.

Preserve `AppRoutes.HABITS`, habit detail/editor/log links, widget launches and restored legacy habit back stacks. Change `openHabitsDate` to ordinary secondary navigation without primary-tab `popUpTo`/restore assumptions. Keep five primary tabs, not six, and no duplicate Calendar row in More. Missing calendar provider in previews/tests has an explicit unavailable state while the Calendar tab and other tabs remain usable.

### CAL-02 — Date and period interaction

On first entry select the current device date, Month mode and All Items; subsequent tab entries retain Calendar state as CAL-01 specifies. Display a Gregorian calendar; localization changes labels, numerals and layout, not stored civil dates or calendar system. Use the locale's `WeekFields.firstDayOfWeek`, including Persian locale; order dates chronologically in logical layout direction.

Month displays complete weeks covering the month (28/35/42 cells); adjacent-month dates are muted and selectable. Week displays seven dates containing selection. Previous/Next move one month in Month mode, clamping the selected day to the destination month's last day, or seven days in Week mode. Example: Jan 31 → Feb 28 → Mar 28 in 2027. Selecting an overflow date changes the shown month. Switching mode preserves selection; the month label follows the selected date, including cross-month weeks. Buttons announce the correct period (Previous week/month).

The Today control selects actual device today and brings it into view without changing mode/filter. Today and selected-date visuals remain distinguishable when different. Use valid dates in years 1–9999; outside-range grid slots are inert blanks and navigation at bounds is disabled. Use date arithmetic, never fixed 24-hour milliseconds.

### CAL-03 — Membership, filters and counts

Only open, dated tasks belong in Calendar. Define `D` = selected civil date and `T` = current device civil date:

| Surface | Membership |
| --- | --- |
| Selected-day scheduled task section | If D = T: open tasks dueDate ≤ T, including overdue carry-in. Otherwise: open tasks dueDate = D only |
| Overdue filter | All open tasks dueDate < T, independent of D. Show explicit “Overdue as of [today]” scope; retain selection |
| Calendar date task marker/count | Open tasks dueDate = that cell's date; no carry-in |
| Memory rows/count/marker | Events with an existing memory row, whose occurrence instant falls in D in device zone; standalone memories and marked moods/journals count once per event, matching MemoryRepository membership |
| Habits and ordinary journals/moods | Excluded from all Calendar rows, totals, markers, legends and accessible date counts |

All Items shows scheduled tasks and saved memories. Tasks hides memories; Overdue shows only global overdue task rows. Chips are single-select. Filters do not alter grid markers.

The date ribbon counts **exact-date** tasks and memories. Tasks chip/section counts the entire scheduled task membership including carry-in on today; All Items counts that task total + memory total. Overdue chip counts global overdue. Counts are full totals, never loaded page lengths. A labeled “Includes N overdue” caption explains today's carry-in. Sections/chips use plural resources.

Show at most two marker dots per date, one per present type (task/memory), with overdue task color taking precedence for the task dot. No pulsating animation. The accessible date label supplies full date, today/selected status, type counts and overdue status. A textual legend names the types; color is not the only signal.

Open tasks sort by due date ascending, timed tasks by minute before date-only tasks, then createdAt and canonical ID ascending (existing comparator). No-date/completed tasks remain accessible through View all tasks but never count here. Fetch 50 task rows initially; Load more adds 50 with stable identity and full totals. On selected date/filter/day/zone change reset task and memory limits to 50. Never filter the first page of the global Tasks list to manufacture an agenda.

### CAL-04 — Date/time semantics and refresh

Reuse the due-time contract: floating civil date/time, null time distinct from 00:00, no UTC conversion, no time-based overdue. A task at 09:00 today is still due today at 10:00 and becomes overdue tomorrow. Date-only badge: “Anytime today” on T, “Anytime on [date]” otherwise. Timed badge: “Due today · [time]” on T, “[date] · [time]” otherwise. Overdue badge includes original due date and time if present. Status uses T, never the selected historical/future date.

Recompute T/device zone on foreground and local midnight using existing CurrentDay infrastructure. Preserve selected date, mode and filter; after midnight, yesterday no longer gets carry-in. Task civil values never shift with zone changes. Memory day grouping uses [start of D, start of D+1) in device zone, respecting 23/25-hour days. Habit schedules, tracking zones and log changes have no Calendar effect. Memory timestamps follow the existing record semantics and are never treated as scheduled tasks.

### CAL-05 — Create, edit, complete and recover

Add task opens the existing task editor with dueDate = D and dueTime = null. This remains true in Overdue mode. Editing opens the original task and never overwrites its existing date/time with D. Overflow menu offers Edit and Delete; Delete enters existing editor confirmation rather than a new unsafe mutation path. A task moved to another date disappears only after successful save.

Schedule opens Quick Capture with a separate explicit task-default date D. Extend `QuickCaptureSheet` with trailing nullable `taskDueDate: LocalDate? = null` and `onScheduledTask: ((LocalDate) -> Unit)? = null`, retaining existing `onTask` compatibility. Calendar provides the scheduled callback; other callers keep today's existing undated task behavior. In Calendar show “Task due [D]” separately from Now/selected-date record chips. Those chips still apply only to Mood/Journal/Memory/Habit capture. Schedule dismissal writes nothing; opening an editor writes nothing; only Save persists. No generic Event capture type is introduced.

Checkbox uses existing `TodayTasksViewModel` revision-safe completion and `TodoActionFeedbackHost`/result handling. Prevent duplicate in-flight writes. A success removes the open row and updates totals/markers; Undo reopens only against the completion revision. Retry uses the same pending mutation identity; conflict/missing shows existing recovery without overwriting newer data. Retain failed-action recovery even if navigation/filter changes hide its original row. Back from edits returns to Calendar selection/filter/scroll and successful save produces one acknowledgement.

Restore invalidates pending calendar task mutations and open editor baselines using the existing restore generation. Clear stale rows before reload; never replay a pre-restore mutation against restored data.

### CAL-06 — Saved memories and habit exclusion

Show saved memories for D in device zone, using the same memory-row membership as the existing MemoryRepository. Include standalone memories and memory-marked moods/journals once per event ID; exclude ordinary journals/moods and unmarked events. Imported saved memories remain visible. Order by occurredAt descending, then canonical event ID ascending. Read at most 50 initially, with Load more adding 50 and an independent full total; never truncate the displayed count to the loaded page length.

Each row shows its saved title (fallback “Memory”) and a short preview of at most 160 Unicode code points with whitespace collapsed. Prefer nonblank memory note, then journal body, mood note and event note; fall back to an empty preview. Open the existing memory route using the original event ID, so marked records retain their existing original-record actions. The empty state says “No memories for this date” with Add memory routed through existing dated memory capture and validation. Saved-state restoration, paging reset and retry follow CAL-07. Memory page rows and total come from one query snapshot.

No Calendar habit section, row, progress, count, marker dot, legend item, habit-zone caption or habit-detail callback is allowed. Calendar does not subscribe to habit data. Creating, logging, skipping, pausing, rescheduling or archiving habits must not change Calendar totals or markers. The full Habits feature under More, Today previews and existing dated links outside Calendar retain their behavior.

### CAL-07 — Loading, failure, lifecycle and access

Each data section exposes Loading / Ready / Empty / Error with Retry. A failed source never becomes a zero count; unavailable totals use a dash and accessible explanation. Healthy sources stay usable. Date change must immediately remove the old date's content before new content appears. Cancel stale requests and ignore late emissions using a request key covering D, T, mode/range, filter, zone and restore generation.

Persist selected date, mode, filter and list position through recreation, process restoration and language change. Reset task and memory paging to 50 on process restoration, clamp scroll to available content and never restore rows from an earlier data generation. Persist action recovery through the existing action controller, not serialized full task rows. Requery Room on resume/mutations/restore; grid and sections must converge to the same saved data.

Use one scroll container, scalable layout and shared LumaReadingPane. On narrow/large-text layouts stack month header, toggles and navigation; wrap count ribbon and badges. At 320 dp, seven 48 dp day targets require a horizontally scrollable calendar viewport at least 336 dp wide, with selected date brought into view and a scroll affordance; never overlap hit targets. At 840 dp cap reading width. Ensure last row, Load more, Retry, snackbar and Schedule remain reachable above bars/IME. Controls have descriptive localized names, roles, selected/checked/pending states and logical TalkBack/keyboard order; moving dates does not steal focus unexpectedly.

## Architecture and scope boundary

Add a read-only `CalendarRepository` projection with bounded date-range aggregates and bounded task/day detail reads over existing Room tables. Calendar writes only through existing task/editor/capture paths. Memory projection joins the existing memory marker to its event and optional source content, counting each saved event once. No habit projection or journal-only reflection remains. Per-source flows allow partial failures. No background worker or persisted calendar cache.

Non-goals: external calendars, Android Calendar provider, event CRUD, time-block durations, appointments, recurring tasks, notifications/reminders/alarms, task tags, projects, drag/drop rescheduling, Jalali conversion, new metrics, cloud sync, additional navigation tabs beyond the five-slot replacement specified in CAL-01. “Scheduled task” means a one-time task with a due date, optionally time. New absolute deadlines or Jalali support would need a separate spec.

## Acceptance and traceability

| Requirement | Implementation | QA scenarios |
| --- | --- | --- |
| CAL-01 | CAL-I05 | CQA-01–03, 24 |
| CAL-02 | CAL-I01/I03 | CQA-04–08, 22 |
| CAL-03 | CAL-I01/I02/I04 | CQA-09–13, 26 |
| CAL-04 | CAL-I01/I03 | CQA-14–16 |
| CAL-05 | CAL-I04/I05 | CQA-17–21, 23–25 |
| CAL-06 | CAL-I02/I04 | CQA-12, 27–28 |
| CAL-07 | CAL-I02/I03/I06 | CQA-22–26, 29–32 |

The 2026-10-25 tasks-and-memories scope supersedes habit/reflection details in older reference assets and handoff snapshots. Implementation completion means source work ready for QA, not verified behavior or release acceptance. Full scenario results and focused revision checks are recorded in the handoff; unexecuted device/matrix checks remain NOT RUN or BLOCKED. Existing release blockers remain separate.
