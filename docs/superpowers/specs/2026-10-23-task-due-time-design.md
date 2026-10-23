# Task due date with optional hour and minute

Date: 2026-10-23  
Status: Implementation requested and source changes are in the working tree; behavioral validation remains pending.  
User request: Add hour and minute to a task's due date; generate a spec and plan.

## Intent and scope

Let someone express a task deadline such as 23 October at 14:30, save it, and see it consistently in Tasks, Today and the Tasks widget. Keep undated and date-only tasks available.

The user requested hour and minute support. Optional time, default picker time, ordering and timezone behavior below are proposed product decisions, not previously approved requirements. Review these before implementation.

This extends the existing task feature and editor. It supersedes only the time-of-day exclusion and affected signature/schema restrictions in [T37](2026-10-23-t37-task-editor-redesign-design.md) for this feature. It does not select the deferred task-tags proposal, change navigation, or close existing release blockers.

## Current implementation

- `TodoTask`, `TodoInput` and `TodoDraft` carry nullable `LocalDate` values. New tasks are undated unless a caller supplies a date; Today Add supplies today.
- `todo_task.due_date` stores an ISO civil date. Room is version 5; portable backup schema is version 4. Backups read strict TSV headers against the current staging schema, so changing columns requires a legacy import adapter.
- Open tasks sort by due date, creation time and ID. Today and widgets include incomplete tasks whose date is today or earlier. Overdue is currently a calendar-day grouping.
- The editor retains draft, baseline and pending mutation inputs in `SavedStateHandle`; revision checks and input equality support conflict recovery and idempotent retries.
- `DisplayTimePolicy` and `localizedTime` already implement localized dates, minutes and the Android 12/24-hour preference. `HabitLogCorrectionDialog` already uses Material 3 `TimeInput` inside `LumaAlertDialog`.

## Approaches considered

| Approach | Benefit | Cost / limitation |
| --- | --- | --- |
| **Optional `LocalTime` beside `LocalDate` (recommended draft)** | Preserves all existing date-only tasks and expresses an explicit hour/minute with a small additive model change | Does not represent an absolute instant; precise time-based overdue status would need a separate contract |
| Require date and time together | Every dated task has a precise clock value | Forces arbitrary times on old and new date-only tasks |
| Store a zoned deadline or UTC instant | Supports absolute deadlines and future alarm scheduling | Adds timezone/DST conversion and migration choices beyond the requested field addition |

The recommended optional `LocalTime` approach is selected for implementation by the user's request to work from this spec and plan.

## Global constraints

- Native Android/Kotlin/Compose Material 3; preserve minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- No new dependencies, runtime permissions or network services.
- Support System/Light/Dark, English/French/Persian, Persian RTL and the Android 12/24-hour preference.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL, IME and system insets.
- Preserve existing title/note limits, due-date years 1–9999, task IDs, revision checks, completion/Undo, delete and restore invalidation.
- Implementation and validation status are tracked in the linked plan. The current session did not run Gradle tests or emulator acceptance at the user's request.

## Product contract

### DUE-01: Valid due values

Support exactly three states: no date/no time; date without time; date with hour and minute. Time cannot exist without a date. Use `dueTime: LocalTime? = null`, appended to the existing constructors of `TodoTask`, `TodoInput` and `TodoDraft` to preserve positional call compatibility. Accept `00:00` through `23:59`, with seconds and nanoseconds both zero. Midnight is a real selected time, distinct from absent time. Reject non-minute input rather than silently truncating it.

`normalizeTodoInput` must preserve valid time, report `TodoField.DUE_DATE / REQUIRED` for time without date, and `TodoField.DUE_TIME / OUT_OF_RANGE` for nonzero seconds/nanoseconds. Add `DUE_TIME` to `TodoField`; reuse existing validation reasons. `TodoTask` and persisted entities enforce the same invariants.

### DUE-02: Editor interaction

Keep the current Due date card and date picker. When a date is selected, add a Due time row below the date controls, displaying No time or the localized selected time. Show Add time when absent, Change time and Remove time when present. Hide the time row until a date exists. Do not assign any time when a date is selected or prefilled.

Add/Change opens a Material 3 `TimeInput` in `LumaAlertDialog`, following the existing habit correction pattern. The picker starts at the existing time or **09:00** when absent. This initial value affects only the picker; Cancel, Back or outside dismissal must leave the draft and dirty state unchanged. Confirm with Select time commits `LocalTime.of(hour, minute)` to the draft. Validate input before enabling confirmation. Reopening a canceled picker starts from the committed draft/default, never from canceled input.

Changing a date preserves its selected time. Remove time retains the date. Remove date clears both fields in one draft mutation and closes any time dialog. A busy, missing or conflicted editor cannot commit picker values. A picker must close when its task/draft is invalidated or fields become noneditable, and must recheck editability before confirmation.

Retain existing date/action test tags. Add `todo_editor_due_time_value`, `todo_editor_add_due_time`, `todo_editor_change_due_time`, `todo_editor_clear_due_time`, `todo_editor_time_dialog`, `todo_editor_time_input`, and `todo_editor_select_time`. Controls wrap or stack at narrow widths; errors have accessible text and focus/bring-into-view treatment.

### DUE-03: Save, recovery and lifecycle

Save/reopen must retain the selected date and minute exactly. Time-only edits count as dirty; restoring the original value clears dirty state using existing normalized equality behavior. Include time in task/input/draft conversions, baseline and pending-operation serialization, pending-key cleanup, repository matching, conflict reconciliation and replay checks. Old saved state lacking time reads as null.

Draft recreation and process recovery retain confirmed time. An unconfirmed picker may close on recreation; it must never write its temporary value into the draft. Existing retry rules determine whether a failed save may be retried. Reusing a mutation token with different time is a conflict, while replaying an identical successful mutation is idempotent. Complete, reopen and Undo preserve date/time. Archive replacement continues to invalidate pre-restore drafts and rotate revisions.

### DUE-04: Display, ordering and date semantics

Display a selected time alongside the due date in the editor, open task rows, completed rows' original due value, Today task rows and Tasks widget detail rows. Preserve the completed-at label. Compose screens use `rememberDisplayTimePolicy()`; widgets use their localized context, Android's hour-format preference and the existing bidi wrapping. A localized date/time template composes the two values. Date-only labels retain their current text.

Open lists, Today and widget pages use the same ordering: due date ascending with undated tasks last; within a date, timed tasks ascending by minute, then date-only tasks; then creation time ascending and canonical ID ascending. Completed list ordering stays completion time descending then ID. Preserve bounded paging, total counts and widget summary privacy; time must not be exposed in summary-only mode.

Today eligibility and Overdue/Today/Upcoming/No date buckets stay calendar-day based. A task due today at 09:00 remains in Today at 10:00. Time does not hide it before 09:00 or schedule a move between buckets. The task becomes Overdue on the next local calendar date if still open. Keep existing day/zone refresh behavior; no minute ticker is required.

The date/time is a floating civil deadline: changing timezone keeps the same date, hour and minute. Store no offset or zone and perform no UTC conversion. A selected local time in a DST gap or overlap remains the entered civil value; no instant is inferred. Past dates/times are allowed.

### DUE-05: Persistence and archive compatibility

Add nullable SQLite `INTEGER due_time_minutes`, with null meaning no time and values 0–1439 meaning minutes since midnight. Domain ↔ entity conversion must be lossless (`hour * 60 + minute`; reverse with hour/minute division). Reject wrong types, out-of-range minutes and time without date in entity/archive validation. Append the entity property with a null default.

At the current baseline, add Room migration **5→6** with `ALTER TABLE todo_task ADD COLUMN due_time_minutes INTEGER`. Existing tasks retain every value and get null time. Replace the open-task index with `(completed_at, due_date, due_time_minutes, created_at, id)`; retain the completed-task index. Export schema 6 and keep prior schema JSON files. DAO projections, page rows, widget rows, insert/update and all mappings carry the field. SQL ordering explicitly places null times after timed tasks to match the domain comparator.

Export portable archive schema **5**, keeping format version 1 and the schema-4 table inventory. The `todo_task.tsv` header adds `due_time_minutes` in the generated current column order. Schema-4 archives must still require their exact original eight-column task header, bind those legacy columns into the current staging table, and supply null time for validation/current rows. Do not relax strict header checking or accept a new header under an old schema number. Schemas 1–3 still contain no tasks. Schema 5 requires the time column even when every value is null.

Malformed new archives fail during staging, before replacement; live tasks and other records remain intact. New archives round-trip null, midnight, 09:05 and 23:59. Preserve current identity/timestamps and revision rotation on restore. Existing local-data reset already removes this column with its parent table.

Room and archive versions are independent. If another implemented feature advances either baseline, rebase to the next free version and retain already supported formats. In particular, the deferred O09 task-tags draft also proposes Room 6/archive 5; neither draft reserves those numbers or selects the other feature.

## Acceptance scenarios

| ID | Required evidence during implementation |
| --- | --- |
| DUE-01 | Domain cases for absent time, midnight, 09:05, 23:59; reject time without date and nonzero seconds/nanoseconds |
| DUE-02 | Add/change/remove time; change date retains time; remove date clears both; Cancel/Back/dismiss do not mutate; confirmation disabled for invalid input; read-only state cannot commit |
| DUE-03 | Time-only dirty/revert, Save/reopen, saved-state recovery, identical replay/different-time conflict, confirmed reload, restore invalidation and completion/Undo preservation |
| DUE-04 | Editor/Tasks/Today/completed original due/widget details show the selected minute with EN/FR/FA and 12/24-hour preferences; summary widgets reveal no due details; date-only labels remain intact |
| DUE-05 | Domain and SQL orders match for mixed timed/date-only/undated tasks, stable ties and >50-row paging; time does not change date buckets or Today eligibility |
| DUE-06 | Populated Room 5→6 and older migration paths preserve records, metadata and null legacy times; schema validation succeeds |
| DUE-07 | Schema-5 backup round trip; schema-4 exact legacy header import with null times; schemas 1–3 keep zero tasks; malformed values/headers reject before replacing live data |
| DUE-08 | Time stays 09:05 across timezone/DST changes and language recreation; time input, validation, Save and Remove remain reachable in the required window/font/RTL/IME matrix |

## Exclusions and implementation discretion

Reminders, notifications, alarms, recurrence, seconds, absolute/zoned deadlines, minute-based overdue status, tags and new task navigation are outside this change. Keep existing permissions and background behavior.

The implementer may choose private helper decomposition and exact translated wording consistent with Luma's catalogs. The field types, minute precision, picker default, mutation rules, ordering and archive compatibility above are fixed within this proposed design.

[Implementation plan](../plans/2026-10-23-task-due-time.md)
