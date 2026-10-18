# Tasks product specification

Date: 19 October 2026. Status: proposed implementation brief; no feature code changed.
Owner: senior product manager, reconciled with the senior UI/UX designer.

## Intent and decision status

The user confirmed **a dedicated Tasks list plus a small Today section**. Enable a
person to capture an everyday one-off commitment, find it later, complete it and
reopen it offline within Luma. The everyday-personal-task audience, fields,
navigation placement and detailed rules below are recommendations, not additional
user-approved requirements. Success means a reliable small task list that fits
Luma's local ownership and life-record model without confusing intentions with
observations or counted habits.

Companion documents: [UX specification](2026-10-19-todo-ux-spec.md),
[delivery plan](../plans/2026-10-19-todo-list.md), and
[task handoff](../../TODO_TASKS.md). Product rules and acceptance IDs live here;
the plan owns proposed interfaces, file paths and execution checks.

## Repository evidence

- `README.md`, `docs/HABITS_TASKS.md` and current navigation describe five primary
  destinations: Today, Timeline, Habits, Review and More. `AppDestinations.kt` and
  `LifeOsApp.kt` confirm that More hosts secondary routes. Do not infer the live
  schema/navigation from the stale introduction in `ARCHITECTURE.md`.
- `core/domain` is pure Kotlin; `LumaStorage.kt` wires Room repositories into the
  Compose app. `LumaDatabase.VERSION` is **3** with 26 application tables. No task
  model, table or screen currently exists in the inspected feature boundaries.
- Today browses a selected date shared with other screens. Habits use a saved
  tracking zone and have their own log/count/history rules. A task's due date
  therefore must not inherit event occurrence time or habit tracking rules.
- `HabitRepository.kt` already distinguishes identity, explicit actions and
  retryable writes. `HabitTimeline.kt` explicitly merges events and habit logs;
  adding task completion there would be a separate product scope decision.
- `docs/BACKUP_FORMAT.md`, `BackupArchive.kt` and `BackupService.kt` support archive
  format 1 with schema 1–3, staged validation and confirmed full replacement.
  `BackupPreview` currently counts events, photos, habits and habit logs only.
- `AppDataReset.kt` delegates confirmed reset to Android's full app-data clear.
  Localized formatting and EN/FA/FR resources already exist, including Persian RTL.

## Scope choices

| Option | Value and cost | Recommendation |
| --- | --- | --- |
| A. Title-only checklist | Small persistence/UI surface; weak date-based Today relevance | Too limited for the requested Today section |
| B. One-off tasks with optional notes and due date | Useful capture, daily focus and completed recovery; one standalone task table | **Recommended MVP** |
| C. Task planner | Projects, priorities, recurrence, reminders and historical completion reporting | Later, after evidence that B is useful |

MVP includes create/edit, optional due date, open/completed views, completion,
reopening, confirmed deletion, a Today preview, the existing Quick Capture and complete
backup/reset coverage. Deliberate exclusions: recurrence, subtask/checklist bodies,
projects or multiple named lists, priorities, drag/manual ordering, reminders or
notifications, task sharing/sync, attachments, tags/people/place links, conversion
between tasks and habits, search integration, Timeline completion items, Review
statistics and completion history. No permission, network, telemetry or AI work.

## Recommended product contract

### Identity, content and lifecycle

One task has a stable UUID, title, nullable notes, nullable due date, nullable
completion instant, created/updated instants and a UUID revision token. Status is
derived exclusively from completion instant: absent means open, present means
completed. Do not persist a second status that can disagree. A task is independent
of `life_event` and habit tables. Creation always starts open.

- Title: trim surrounding whitespace; require 1–200 Unicode code points; reject
  line breaks. Notes: plain multiline text, trim surrounding whitespace, blank
  becomes absent, maximum 4,000 Unicode code points. Duplicate titles are allowed.
  Preserve invalid draft text and show a field error; never silently truncate.
- Saving edits keeps identity, creation instant and completion instant. Completed
  tasks remain editable. Changes become visible across list/Today only after commit.
- Complete sets the actual current instant once. Repeating the same accepted action
  must not move that instant. Reopen clears it and preserves title, notes and due
  date; completing again stores a new actual instant. No backdating or audit log.
- The existing-task editor permits complete/reopen only while fields are clean;
  dirty fields explain that changes must first be saved. Save changes only edits
  fields and returns to origin after commit; a committed status action also returns
  to origin with its result. Dirty Back requires Keep editing or Discard. Delete remains available,
  with confirmation that the saved task and any unsaved edits will be removed.
- Delete is permanent after successful confirmation, with no archive/trash or
  delete Undo. It never removes a habit or life record. A missing task opens a
  clear missing state and cannot be recreated by an edit/status action.
- Row completion has Undo while its feedback is present; Undo is a revision-checked
  reopen of that completion. An intervening edit/status change must prevent stale
  Undo from overwriting newer state. Reopen otherwise needs no Undo contract.

### Dates and clock behavior

The field is named **Due date**. It is an optional ISO civil `LocalDate`, never an
instant, alarm time or event occurrence date. It remains the same civil date when
the device travels. Due today means the device's current calendar date in its
current zone; an open task becomes overdue when its due date is earlier. Refresh
on foreground return, date rollover and device-zone changes. Habit tracking zone
does not control tasks. Completion uses actual now even when an overdue task is
completed; it does not inherit the due date.

No date is the default for More/Tasks and existing Quick Capture creation. The Task
action in Quick Capture ignores the existing event capture date chips and states
its separate due-date behavior. Only Add task in the current Today section visibly
prefills Due today; the editor can change or clear it. Past dates are valid. Use
the existing picker convention of 1900–2100 expanded to include a saved outlier;
domain dates in supported years 0001–9999 must round-trip without locale parsing
or truncation. Reject dates outside that range at the domain/storage boundaries.

Wall-clock correction must not reject an otherwise valid completion merely because
its instant precedes a saved creation instant. Revisions, not timestamp order,
decide which mutation may overwrite a row. Preserve actual completion time; keep
updated-at monotonic relative to that row's previous updated-at value.

### Ordering and bounded reading

Open Tasks is the default view, grouped in this order: **Overdue, Today, Upcoming,
No date**. Omit empty headings. Dated tasks sort by due date ascending, then
created-at ascending, then canonical UUID ascending; undated tasks use the same
creation/UUID tie-breakers. Completed is a separate view ordered by completion
instant descending, then UUID ascending. There is no hidden cap or manual order.
Initially request the first 50 rows; a visible Load more requests an observed
prefix larger by 50. Fetch only that requested prefix, with a matching total count;
group headings remain coherent. Revalidate the prefix after mutations so there are
no stale duplicates, omissions or old status rows. Failed expansion retains loaded
content and offers Retry.

### Reliability and drafts

Use a stable task UUID and initial revision per create draft. Never turn a retry
into a second task or overwrite an existing row with a retried create. Mutations
declare the desired completion state, expected revision and a new revision/action
UUID; they never issue an unqualified toggle. Repeating the current accepted
action with matching payload succeeds without another mutation. A retry after a
newer action conflicts and asks the user to reload; preserve dirty field text for
comparison. Missing edit/status targets fail clearly. Repeated deletion of an
already absent target succeeds; deletion of a newer revision conflicts.

Keep an affected row visible and its control pending until persistence succeeds.
On failure retain content/status with Retry; announce success after commit. Do not
queue background create replay. Arbitrarily delayed create retries after deletion
cannot be recognized without tombstones; the MVP limits retries to the current
visible draft and reconciles its UUID before writing. Rotations/language changes
preserve drafts, selected view and loaded context through existing app patterns.
Restoring a backup invalidates task lists and editors; stale drafts cannot be saved
into the replacement dataset without starting a fresh user action.

## Relationship to Luma surfaces

| Surface | MVP behavior |
| --- | --- |
| More | Add a Tasks entry; use the existing secondary-navigation pattern. Keep the five primary destinations. |
| Tasks | Full Open/Completed lists; Add task and editor; explicit list loading/empty/error states. |
| Today | Show a small Tasks section only when selected date equals actual device today. Eligible tasks are open with due date ≤ today. Show first 3 in list order, full eligible count and View all; no-date/future tasks remain in Tasks. Empty says “No open tasks due today or earlier” with Add task and View all, without inferring total task existence. |
| Browsed past/future Today date | Hide Tasks section completely. Never present the current task state as historical evidence. Tasks remains reachable through More; capture remains reachable on its existing Timeline/Review surfaces. |
| Quick Capture | Add Task to the existing sheet on Timeline/Review, leading to the same editor independently of mood/journal/memory occurrence-date choices. No new capture FAB on other tabs. |
| Timeline / summaries / Search | No task rows, task indexing, event-count changes or task-derived summary cache changes. |
| Habits | Keep counted recurrence, schedules, logs, tracking zone and reminders independent. No automatic conversion or completion. |
| Review | Existing saved-observation and habit sections retain their meanings; no task productivity score or historical trend. |

## Privacy, backup and reset

Store task text only in the app-private Room database; keep private text out of
logs, crash messages, analytics and notifications. Existing explicit exports remain
unencrypted and user-selected; explain that tasks are included alongside existing
private records. No new permission or automatic transfer.

Recommend additive Room schema **4**, one task table and explicit migrations from
supported earlier versions, without destructive fallback. Retain archive format 1
if its envelope remains unchanged; add schema-4 table inventory/validation.
Export and restore open and completed tasks with stable identity, dates and timestamps.
Rotate task revisions on restore publication to reject pre-restore edit/status
commands. Add total task count to restore preview. Schema 1–3 archives
contain zero tasks; confirmed replacement therefore clears current tasks, explicitly
disclosed in preview/copy. Unknown schema versions and malformed task data fail
before any live replacement. Cancelled/failed restore retains current tasks.

Confirmed full local-data reset removes saved tasks and their drafts with the
existing Android reset. Exported files remain under the user's control. Validate
this only on a disposable installation; do not clear the user's Luma data.

## Acceptance criteria

| ID | Observable acceptance |
| --- | --- |
| TODO-AC-01 | Offline create/edit validates exact title/notes rules, permits duplicate titles, retains invalid drafts, survives reopen and never changes task identity/creation time. |
| TODO-AC-02 | No date is the normal default; contextual Today Add visibly defaults to today. Date clearing, past/future dates, travel, midnight and locale changes preserve civil due-date meaning independently of event chips/habit zone. |
| TODO-AC-03 | Complete/reopen follow the exact timestamp rules; repeated completion does not move completion time. Completed edits preserve status; dirty editor cannot issue a status action. |
| TODO-AC-04 | Open grouping and completed ordering match the deterministic rules; >50-row fixtures reach every row by increasing the observed prefix in steps of 50, with no stale duplicate/omitted rows after mutations. |
| TODO-AC-05 | More, Today and Task in existing Quick Capture reach the same Tasks/editor flow; five primary destinations and existing navigation/back stacks remain usable. |
| TODO-AC-06 | Today shows at most 3 eligible rows with full eligible count, excludes future/undated/completed tasks, and hides its Tasks section on every non-current browsed date. |
| TODO-AC-07 | Checkbox/body actions are distinct. Pending/failed writes retain the correct row; success/Undo feedback is announced after commit. Stale retries/Undo conflict, and create retry does not duplicate or overwrite. |
| TODO-AC-08 | Cancelled delete changes nothing; confirmed delete removes only that task. Dirty Back prompts discard, missing targets fail clearly, and an absent delete retry is harmless. |
| TODO-AC-09 | Existing records survive schema-3→4 upgrade and all supported migration paths. Open/completed tasks round-trip through schema-4 backup; old-schema replacement explicitly clears tasks; invalid/cancelled/failed restore retains live data. |
| TODO-AC-10 | Restore preview shows total tasks; restore invalidates task editors/readers. Confirmed local reset clears tasks/drafts on an isolated install; export privacy copy covers task text. |
| TODO-AC-11 | EN/FA/FR strings, localized dates/numerals, RTL, 320dp width and 200% font scale remain usable; screen-reader labels expose title/status/due date and every control has ≥48dp target. Status/overdue/error are understandable without color. |
| TODO-AC-12 | Rotation/language change retains draft/view state; load errors have Retry; empty Open and Completed views have appropriate actions without implying lost history. |
| TODO-AC-13 | Task actions leave existing event/journal/memory/habit counts, Timeline, summaries, search results and Review source semantics unchanged; feature requires no network/account/permission. |

## Local success measures and remaining risks

Before release, record reproducible pass/fail evidence for all criteria; automated
semantics are not proof of native TalkBack speech. Run a local usability walkthrough
for capture with no date, capture due today, completing an overdue task, finding and
reopening a completed task, and recovery from failed save. Each must succeed without
data loss in EN/FA/FR; report any friction rather than inventing usage or adoption.
Use a 10,000-task fixture to verify bounded first/subsequent pages and Today preview;
record repository p50/p95 and device/API, with a proposed p95 target of 300 ms for
the initial 50-row repository prefix. Treat an unmet target as a documented release decision,
not a claim of verified performance. Do not add telemetry to collect these measures.

Open decisions are limited to user review of this proposed MVP, final translated
copy and native usability/performance evidence. If the user instead needs a work
project planner or reminders, revisit scope before implementation. Existing habits
T27–T29 and earlier release/accessibility/performance work remain independently open;
Tasks must not silently replace or mark them complete.

PM/UX reconciliation: retained the five-tab shell with More access; added existing-sheet
capture for discovery; replaced ambiguous planned/due naming with Due date; rejected
historical Today task previews; agreed a three-row preview, deterministic groups,
separate Completed view, one editor, explicit field limits and clean-editor status
actions. Detailed recommendations still require review before feature execution.
