# Luma home-screen widgets — product and technical specification

Prepared: 19 October 2026. Status: implementation-ready proposal; no widget code is implemented or verified by these documents. Requested scope: Android launcher widgets for habit tracking, to-dos, and new mood (interpreting “mod”), memory and journal capture. Assumptions below are explicit defaults for implementation planning, not user-tested findings.

Companion documents: [implementation plan](../plans/2026-10-19-home-screen-widgets.md), [task tracker](../../WIDGET_TASKS.md), [audit backlog](../../ui-ux-audit/IMPROVEMENT_TASKS.md), [design system](../../ui-ux-audit/DESIGN_SYSTEM.md).

## 1. Outcome and boundaries

Users can record a habit repetition or complete a task from the Android home screen, and open the correct new-entry editor with one tap. Widgets must remain useful offline with Luma's UI process absent. An old launcher view must never silently record the wrong day, overwrite a changed task, or duplicate a committed action.

Ship three separately selectable providers: **Luma Habits**, **Luma Tasks**, **Luma Quick capture**. Multiple instances are supported. No account, new permission, network request, notification, exact alarm, foreground service or integration import is required by widget rendering/actions. Existing app foreground import behavior remains governed by its existing preferences when a capture tap opens Luma.

Out of scope: reminders, streaks/rewards, week/month heatmaps, scrolling full lists, chosen-habit subsets, task filters, widget text entry, photo capture inside a widget, previews of journal/memory bodies or mood history, and iOS. Existing editors remain responsible for validation, drafts, context, photos, Save and Cancel.

Success criteria: a capture tap opens its editor without an intervening capture menu; eligible row actions require no Activity; counts follow confirmed storage; failed operations remain recoverable; supported layouts have readable labels and 48 dp actions. Record usability timings during acceptance without inventing a percentage improvement target.

## 2. Current implementation and dependencies

Inspected working tree on 19 October 2026:

| Existing surface | Reuse / consequence |
| --- | --- |
| `app/.../MainActivity.kt`, `ui/LifeOsApp.kt`, `ui/navigation/AppDestinations.kt` | Compose navigation already has `mood`, `memory`, `journal`, `habit_detail/{habitId}/{date}`, `tasks`, `task_editor`; no widget intent handling exists. Validate actual AppRoutes constants before wiring. |
| `HabitRepository`, `RoomHabitRepository`, `HabitDao` | Fixed tracking zone, schedules and idempotent `log(id, input)` exist. Ordinary log permits past dates and does not enforce widget-specific freshness/count guards. |
| `TodoRepository`, `RoomTodoRepository`, `TodoDao` | Revision-checked desired completion and idempotent retries exist; Today means open, dated, due on/before the device date. |
| `LumaStorage`, `LumaApplication` | Shared lazy database/repositories; extend these composition roots, not an additional database/process. |
| `LumaDatabase.VERSION = 4`, manual backup schema 4 | New device-local widget runtime tables require additive database migration 4→5. Manual archive schema remains 4; runtime tables are excluded. Database and archive versions must remain distinct. |
| `AppLanguages.wrap`, `Theme.kt`, three string catalogs | Render using the selected app language and Luma palette, including Persian RTL; RemoteViews cannot reuse app Composables. |
| `RoomBackupService`, `AndroidAppDataReset` | Replacement restore must invalidate widget commands transactionally. OS reset clears the sandbox and stops the process; launcher caches are outside that sandbox. |

Audit relationship: preserve UX-01 date ownership, UX-02/03 retained recovery, UX-04 conflict-safe editor behavior, UX-05/06 draft policies, UX-15 accessible feedback, UX-21 target sizing, UX-27 localization, UX-28 privacy and UX-33 release evidence. These IDs describe existing audit tasks; widget tasks use **WG01–WG08** and do not add invented same-numbered audit findings. Existing incomplete audit acceptance remains incomplete.

There are unrelated pending code changes and a modified audit backlog in the working tree. Implementation must reread current files and preserve those changes. Mood/editor audit repairs may proceed independently; widget launch must preserve their existing safeguards rather than claiming to resolve them.

## 3. Approach decision

| Approach | Benefit | Cost / decision |
| --- | --- | --- |
| Native `AppWidgetProvider` + `RemoteViews` | Uses current API 28–35 stack, no new dependency; small bounded layouts and explicit actions | Separate XML presentation; **selected for v1**. |
| Jetpack Glance | Compose-style widget layout | Separate Glance APIs and dependency compatibility work; consider later, not required for these three widgets. |
| Launch-only shortcuts for every action | Smallest initial effort | Capture uses this approach, but habits/tasks would still require opening the app; insufficient for the requested tracking convenience. |

Use three thin providers, shared renderer/intent builders, one refresh coordinator, and one transactional widget store. Keep widget-only policy outside existing screen ViewModels. Reuse business repository mutations inside the same Room transaction as command validation and completion; do not copy habit/task mutation SQL into an Android receiver.

## 4. Widget experience

### Habits

Header: “Habits”, explicit tracking date, zone when different from the device zone. Show scheduled ACTIVE habits for that date in existing `created_at ASC, id ASC` order, excluding future-start, off-day, paused and archived habits. Skipped and target-reached rows remain visible with their state. Summary: “{completed} of {scheduled} targets reached”; skipped does not count as reached unless the count actually reaches target.

Detailed mode displays up to three rows: full accessible habit name, visible name up to two lines, count/target, and **Log +1** for active scheduled rows with `!skipRemaining && count < target`. Tapping the name/progress block (at least 48dp high) opens the exact habit/date, separately from the mutation target. Target reached or skipped has a textual state and no mutation action. Further repetitions/corrections remain available in app detail. “Open habits” and Refresh remain available; the footer indicates hidden rows when there are more than fit. Empty library offers “Create habit”; an existing library with no eligible rows says “No habits scheduled for this date.”

### Tasks

Header: “Tasks”, explicit device date. Scope is **Overdue and today**, matching `observeToday`, not all open tasks. Undated/future/completed tasks are absent. Use due date, creation instant, canonical UUID ascending ordering. Summary gives the full matching open-task count, not the visible prefix count.

Detailed mode shows up to three title/due-date rows. **Complete** sets `completed = true`; it never toggles blindly. Tapping the title/due-date block (at least 48dp high) opens that task editor, separately from Complete. “Add task” opens a blank editor with device-today due date calculated at launch; “Open tasks” opens the full list. Empty scope: “No tasks due today or overdue” plus Add task/Open tasks, even when undated/future tasks exist.

### Quick capture

Three named buttons in stable order: **Mood**, **Memory**, **Journal**. Choose a measured wide row, two-plus-one grid or vertical stack; reserve 48dp targets, 8dp gaps and localized text height. Open Luma is not a capture tile. Each capture button opens the existing *new* editor directly. Do not carry an event ID, the app's browsed date, or a timestamp frozen when the widget rendered. Initialize time at handling the tap. No entry is saved before the user saves in the editor. Cancel leaves storage unchanged. This provider is immediately usable without configuration or Room reads.

### Detail visibility and configuration

Configuration uses a scrolling body with safe, wrapping Save/Cancel controls outside the scroll area. Fictional summary/details examples follow the unsaved choice without reading records. Discovery and compatible picker previews depict the default summary and three capture actions; existing instances use purpose, mode and local ordinals instead of Android IDs. Quick capture needs no instance settings.

Habits/Tasks configuration has one switch, **Show names and row actions**, default **off**. Explain before enabling: “Names and progress will be visible wherever your launcher shows this widget.” Default summary mode shows date/counts and Open/Add/Refresh actions, but no names, IDs, per-row progress or per-row mutation targets. Quick capture contains no personal data and has no configuration.

Use a required first-placement configuration Activity for Habits/Tasks on all supported APIs; start with `RESULT_CANCELED`, validate the widget ID/provider, and persist/render before returning `RESULT_OK`. Cancel first placement creates no runtime rows. Reconfiguration cancel preserves the previous configuration. Android 12+ host reconfiguration and an in-app Settings → Home-screen widgets → existing instance entry both reach the same configuration screen. Turning details off revokes existing row and Undo tokens in the same transaction before replacing the visible view. A missing configuration after data clear uses safe summary/setup state.

## 5. Responsive and accessible presentation

Provider metadata: home-screen category; horizontal/vertical resize. Habits/Tasks default 4×3 cells and fallback `minWidth=250dp`, `minHeight=250dp`; Quick capture default 3×2 cells and fallback `minWidth=180dp`, `minHeight=160dp`. Habits/Tasks may shrink to `180×160dp`; Quick capture minimum is `180×160dp`. Cell dimensions are launcher-dependent hints, not guarantees. Supply static fictional preview images and a safe initial layout without personal data.

Choose capacity from actual host bounds, font scale and localized measured content. Combine snapshot date/zone and scope in one supporting block; show successful freshness once, using its actual update date when it differs from the snapshot date. Keep a 48dp header Refresh target for both providers, a plain View all action with omitted-row count, and Tasks Add task. Reserve header, scope, footer and retained feedback first; fit 0–3 rows in remaining space. Minimum action size is **48×48dp**. At 200% text or short bounds, reduce rows, then select a complete compact shell instead of clipping controls or shrinking text. Select recovery shells by measured fit at every height. Prioritize state text and its primary Set up, Refresh, Retry, Undo, Dismiss or Open action over optional navigation. Compact section buttons may say Open, with contextual accessibility labels. Preserve snapshot date; the tightest normal summary may omit counts/freshness and use the tracking UTC offset when a full zone identifier cannot fit. The full date/zone/counts/freshness remain in the regular shell. If none of the three capture arrangements fits, render a single “Open quick capture” action that opens the existing capture sheet. The normal supported capture layout retains all three direct buttons.

Use 16sp row text, 14sp supporting/action text, 12dp container padding, 8dp gaps, Luma light/dark semantic colors; errors include text and an icon, not color alone. Use start/end, bidi-safe date/zone identifiers, localized dates/digits/plurals and descriptive action labels such as “Log one repetition for {name}”. Keep full title in accessibility text. Do not duplicate announcements via overlapping clickable containers. Test real launcher TalkBack and keyboard focus, not Compose semantics alone. Theme/language changes must rerender background widgets using `AppLanguages.wrap` and the app's theme preference.

## 6. Actions, consistency and recovery

Every visible mutation points to an opaque persisted command UUID. An explicit immutable PendingIntent contains only widget ID/token; names, notes and raw repository inputs stay private. Distinguish PendingIntents using component/action/data URI, not extras alone. Mutation receiver is non-exported; Activity navigation launches directly through `PendingIntent.getActivity` without a receiver/service trampoline. Validate live widget ownership before dispatch.

Command lifecycle: READY → COMMITTED or REJECTED. A transient failure leaves the same READY command available for Retry. Do not issue a new token just because a launcher requests an identical rerender. Reuse a READY token only while its exact widget/row/action fingerprint is unchanged. Superseded READY commands become REJECTED. Keep terminal records for seven days, then prune; absent tokens always reject, never reconstruct a command from intent fields. Removal deletes all commands for that instance.

Execute token lookup, ownership checks, validation, repository mutation, terminal result and Undo creation in **one Room transaction**. After ownership/token lookup, terminal commands return their recorded outcome without writes; READY commands must additionally pass current configuration and row eligibility checks. Returning an old success does not make its revoked Undo token valid again. Repeated delivery of a terminal command returns its stored outcome without any repository write, including after Undo, deletion, clock change or process death. On a thrown storage failure the transaction rolls back; classify safely outside it and retry the same token. An action is accepted only when the widget kind/configuration still permits it. Do not replay unaccepted mutations automatically on startup.

**Habit +1:** token fixes habit ID, displayed tracking date/zone, schedule fingerprint and expected count. At execution resolve tracking zone again and require displayed date equals actual tracking-today; re-read effective schedule, skip and count. Reject stale/missing/changed/finished/skipped rows and refresh instead of moving a record to a new day. Create the log UUID before writing and persist `occurredAt` from the first accepted execution instant; call `HabitRepository.log(logId, HabitLogInput(...))`. Validation and log creation share the transaction. Two instances acting on the same expected count produce one accepted increment and one stale refresh; after refresh the next intentional repetition is available.

**Task completion:** token fixes task ID, displayed device date, expected task revision and resulting revision. Revalidate actual device date and open/due eligibility; call `TodoRepository.setCompleted(..., completed=true)`. Changed revision → Conflict/Open task; missing → Unavailable/Open tasks, never create; completed by another action → refresh without changing its timestamp. A committed token remains committed even if the task is subsequently reopened.

**Undo:** keep one retained last-action feedback area per instance, independent of whether the row still appears. “Repetition saved” or “Task completed” offers Undo in one compact strip when measured labels fit, stacking when needed. “Undone” keeps Dismiss without an empty action band. Errors may take more space and displace rows. A later accepted mutation replaces that instance's previous feedback/Undo and invalidates its unused Undo token; Refresh/recreation does not dismiss it. Dismiss clears it explicitly. Habit Undo checks the exact saved log ID and complete saved log value in a transaction before `deleteLog`; changed history must be opened for review, not silently removed. A missing log is already undone. Task Undo reopens against the completion's resulting revision using a fixed new revision; a later edit/reopen/recompletion conflicts. Undo never recreates a missing task/log. Failed Undo retains its token and visible Retry/Open. Default summary mode never has an Undo command.

User feedback states: initial loading (unknown count), confirmed empty, saved, updating, read failure, write failure, stale date, conflict, missing item and unavailable configuration. Never substitute zero for a failed read or report success optimistically. During read failure remove row mutation PendingIntents, retain qualified prior counts only when a safe last successful snapshot is available, show “Couldn’t update” with that snapshot’s date/time, and offer Refresh/Open. When no safe snapshot is available, show unknown counts and the error; never invent or persist a second copy of personal rows just for error rendering. Retained write feedback survives prefix changes/process restart; Retry uses its original command. Raw exceptions/IDs/private paths are not UI copy or logs. Long feedback replaces rows as needed so recovery controls fit.

## 7. Refresh and lifecycle

Room is the source of truth; RemoteViews is a dated projection. Providers request one coalesced unique refresh worker; the worker resolves active IDs, reads snapshots off main thread and publishes complete views. App-process Room invalidations request refresh after habit/task changes, including editor saves, schedule/skip changes and deletions; explicitly refresh after widget command/Undo/config changes. Observe content tables, not command issuance, to avoid refresh loops. Stop observers with the process and never rely on them while it is dead.

Set `updatePeriodMillis=0`. Schedule one unique **30-minute** periodic WorkManager refresh while any data widget exists; cancel it when the final data widget is removed. WorkManager persists across reboot. Refresh on provider update, options change, manual Refresh, app foreground return, successful restore and app locale/theme selection; handle system time/timezone/locale/package events that Android permits. Do not rely on a manifest DATE_CHANGED broadcast or minute ticks. Quick capture only needs layout/locale/theme refreshes and must not start a periodic data worker by itself.

Refresh timing is best effort under Doze, standby, force-stop and launcher caching. Always show successful snapshot date/time; execution-time checks protect even an indefinitely stale view. No midnight or real-time delivery guarantee. Coalesce refresh requests without dropping a request received during execution: append one serialized follow-up, with at most one active renderer. Broadcast handlers enqueue short work and return; database work must not block `onReceive`.

On widget removal prune instance state/commands; on host restore/remapped IDs discard old tokens and create default hidden-detail instances for verified new IDs (do not copy consent to an unknown surface). On app update reconcile missing IDs and rerender. On device locked before first unlock, show unavailable/open state; credential-protected storage stays authoritative and direct-boot access is not enabled. App-wide reset cannot guarantee immediate erasure of another process's cached widget image; describe that limitation honestly in Privacy. Reinitialization renders default hidden-detail state and old tokens fail closed.

## 8. Storage, restore and privacy contract

Add database version **5**, with two local runtime tables: `widget_instance` (ID/kind/detail preference/last feedback) and `widget_command` (token/owner/action/immutable payload/status/outcome/created time). These are device-specific records, not user history; no foreign key to a habit/task that would delete a consumed-command tombstone and permit replay. An instance-to-command cascading relation is allowed. No duplicate habit/task record store or body/photo cache.

Manual archive schema remains **4**; preserve fixed inventories/readers for 1–4. Inspect archive validation and staging for assumptions that equate archive version with Room version; preserve the independent archive-version constant already present. Stage archives into the latest database without adding widget tables to exported/required archive inventories. In the existing live replacement transaction, delete all widget commands, reset surviving instance preferences to hidden-detail and clear feedback alongside replacing user data. A failed/cancelled restore must preserve both content and widget runtime state. Notify active widgets after successful publication; periodic/foreground refresh repairs a lost notification. OS reset clears runtime state with other app data.

Widget content is passed to the launcher and can appear on launcher-managed surfaces; `home_screen` is a requested category, not a confidentiality boundary. Default summary still reveals aggregate counts/date, which configuration/help must explain. No widget data in telemetry, screenshots used as previews, shared external files, backup archives or diagnostic exception messages. Add policy/help wording during implementation; do not describe these protections as already shipped.

## 9. Acceptance criteria and traceability

| ID | Observable acceptance | Owner |
| --- | --- | --- |
| WG-AC-01 | All three provider entries have fictional previews, support add/remove/multiple instances, and have safe initial states on API 28 and 35/36. | WG01/WG05/WG06 |
| WG-AC-02 | Mood/Memory/Journal opens the correct new editor cold/warm; Cancel writes nothing; capture uses tap-time and preserves an existing draft. | WG01 |
| WG-AC-03 | Habit eligibility/order/count/skip/zone are correct; a previous-day view cannot log after tracking midnight; off-day/pause/delete/count races reject. | WG03/WG05 |
| WG-AC-04 | Tasks include overdue/today only; correct order/full count; stale revision/missing/reopened task cannot be overwritten or recreated. | WG03/WG06 |
| WG-AC-05 | Duplicate/concurrent delivery and commit-then-process-death change data once; old original token after Undo never replays; next fresh action still works. | WG03 |
| WG-AC-06 | Saved/Undo/failed Retry remains visible when the target leaves preview; Undo acts only on the original unchanged result. | WG03/WG05/WG06 |
| WG-AC-07 | App/widget edits refresh every affected active instance; periodic work exists only with data widgets; delayed work remains safe across time/zone/reboot/resize. | WG04 |
| WG-AC-08 | Hidden-detail default and reconfiguration isolate instances; turning details off rejects old tokens; exported entry points reject malformed input. | WG02/WG03/WG07 |
| WG-AC-09 | Migration preserves existing data; archives 1–4 still restore; successful restore/reset invalidates old commands; failed/cancelled restore does not. | WG02/WG08 |
| WG-AC-10 | English/French/Persian, RTL, light/dark, 100/130/200% text, smallest/large/landscape sizes preserve usable recovery and 48dp actions. | WG05–WG08 |
| WG-AC-11 | Real launcher TalkBack/keyboard focus names actions/results correctly; no private content enters previews/logs/intents/archives. | WG07/WG08 |
| WG-AC-12 | Loading/error/empty remain distinct; UI-process death, force-stop delay and host ID remap recover truthfully without writes to unintended data. | WG04/WG08 |

Release sequence: WG01 is independently shippable capture; WG02–WG04 establish safe data-widget infrastructure; WG05/WG06 expose tracking; WG07 adds discovery/config management; WG08 closes integrated acceptance. Do not expose direct mutation actions before transaction/replay/restore checks pass. No visual-token migration, reminder implementation or unrelated audit completion is required to begin WG01.

## 10. Platform references

Platform claims checked against official Android documentation on 19 October 2026; UI and storage policy above are Luma design decisions.

- [Basic widgets](https://developer.android.com/develop/ui/views/appwidgets): provider/metadata/RemoteViews model and supported views; app Composables are not widget layouts.
- [Advanced widgets](https://developer.android.com/develop/ui/views/appwidgets/advanced): update options, WorkManager alternative, periodic-update limits and short receiver lifetime.
- [Flexible layouts](https://developer.android.com/develop/ui/views/appwidgets/layouts): API 31 responsive sizes with older-version dimension fallback.
- [PendingIntent API](https://developer.android.com/reference/android/app/PendingIntent): explicit/immutable intents and identity matching; extras alone do not distinguish instances.
- [AppWidgetManager API](https://developer.android.com/reference/android/appwidget/AppWidgetManager): supported pin requests and configuration result handling.
