# To-do list interaction specification

Prepared: 19 October 2026. Status: draft for review; documentation only. The user
confirmed **a dedicated Tasks list plus a small Today section**. Detailed scope,
copy and interaction decisions below are proposals reconciled by the senior
product manager and UI/UX designer; they are not recorded as user approval.
Assumed purpose: everyday personal one-time tasks.

Read alongside the [product specification](2026-10-19-todo-product-spec.md).
Product owns lifecycle, limits and persistence rules; this document owns their
visible behavior. Implementation sequencing and task status belong to the
parent-authored implementation plan and handoff.

## 1. Repository evidence and design fit

Paths below are relative to the repository root; these are inspected source
patterns, not claims that Tasks already exists.

| Evidence | Consequence for Tasks |
| --- | --- |
| `README.md`; `docs/HABITS_TASKS.md` | Native Compose, offline/local-first Room, English/Persian/French; no account or permission is needed for this feature. Habits represent counted repetitions, so tasks need a distinct identity. |
| `app/src/main/java/com/luma/lifeos/ui/navigation/AppDestinations.kt`; `ui/navigation/PrimaryNavigationBar.kt` | Keep Today · Timeline · Habits · Review · More. Add a secondary Tasks route, retaining existing routes and full localized accessible tab names. |
| `app/src/main/java/com/luma/lifeos/ui/more/MoreRoute.kt` | Add a Tasks entry using the existing outlined entry-card pattern, before People & Places and Settings. Existing private `MoreEntry` can be reused within that file. |
| `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt` | Secondary routes hide primary navigation; Back returns to origin. Shared `selectedDate` survives tabs/recreation. Task navigation must not overwrite it. Quick Capture FAB currently appears on Timeline and Review only. |
| `app/src/main/java/com/luma/lifeos/ui/today/TodayScreen.kt`; `ui/habits/TodayHabitsCard.kt` | Today is a padded lazy list: date/capture, Habits, Recent entries, summary. Insert a separate compact Tasks card after Habits and before Recent entries. Reuse the card/header/view-all and three-row preview language. |
| `app/src/main/java/com/luma/lifeos/ui/today/CurrentDay.kt` | Current device day/zone refreshes on foreground and every 30 seconds while visible. Use this day for due buckets and Today eligibility, not the habits tracking zone. |
| `app/src/main/java/com/luma/lifeos/ui/QuickCaptureSheet.kt`; `ui/today/TodayCaptureActions.kt` | Entry capture's current-time/browsed-date choice is an occurrence concept. A Task choice must be separated and explicitly start with no due date. Today task Add uses a visible Due today default. |
| `app/src/main/java/com/luma/lifeos/ui/common/EditorTopBar.kt`; `ui/common/AccessibleFeedback.kt`; `ui/journal/JournalEditorScreen.kt`; `ui/habits/HabitEditorRoute.kt` | Reuse full-screen editors, fixed Save above keyboard, scrollable fields, discard/delete dialogs, localized field errors and polite live feedback. Do not copy journal occurrence timestamps or habit schedules into task forms. |
| `app/src/main/java/com/luma/lifeos/ui/theme/Theme.kt`; `language/LocalizedFormats.kt`; `app/src/main/res/values{,-fa,-fr}/strings.xml` | Use existing Material typography, surfaces, primary/error colors and localized Gregorian formatting. No new palette, dependencies or image assets. |
| `docs/superpowers/specs/2026-10-18-ux-07-08-19-capture-design.md`; `2026-10-18-ux-16-formatting-design.md` | Respect current capture/inset decisions and established Gregorian/bidi display policy; do not assume every earlier proposal is implemented. |

No nested `AGENTS.md` was found during repository inspection. Existing code and
documents are left untouched by this planning artifact.

## 2. Scope and information architecture

**UX-TODO-01 — One accessible home.** More → Tasks opens the full list in Open.
Today → View all opens the same Open list. List Back returns to the originating
Today or More screen with its date/scroll state intact. Task rows open an editor;
editor Back/Cancel/Save return to their originating list or Today card. Quick
Capture → Task returns to its originating primary screen on save/cancel. Avoid
building duplicate list stacks on repeated activation. Restore route, list mode,
loaded scroll anchor and editor draft on recreation; failed/missing task IDs
must not become new-task drafts silently.

The list uses visible Open and Completed filter controls with selected semantics,
not a sixth bottom tab. Add task is a labeled top-bar action. The entire Open
list is always available; Today is a preview, not a separate task collection.
No priority, tags, projects, custom lists, reminders, recurrence, subtasks,
drag-ordering, attachments or collaboration in this proposed MVP. Task completion
does not create a life event or contribute to Timeline, Review, daily summary,
habit counts or entry counts.

Alternatives considered:

| Option | Tradeoff and recommendation |
| --- | --- |
| Sixth primary Tasks tab | Adds permanent navigation pressure to a five-tab shell already tested at 320 dp/large text. Defer a broader navigation redesign until actual use warrants it. |
| Tasks only inside Today | Fast daily access but loses upcoming/no-date/completed discovery and conflates date browsing with mutable task status. User selected a dedicated list plus preview. |
| Shared Habits/Tasks list | Reuses a destination but mixes repeat-count/day tracking with one-time open/completed work. Keep separate headings, identity and actions. |
| More home + Today preview + Task in existing Quick Capture | Proposed choice: discoverable daily work with one canonical list and five unchanged primary destinations. |

## 3. List and row behavior

**UX-TODO-02 — Predictable organization.** Open has these headings in order:
Overdue, Today, Upcoming, No date. Include only nonempty groups. Overdue means
open with due date before actual device today; Today means equal; Upcoming means
after; No date means null. Groups use due date ascending, then created-at
ascending and stable task UUID as ties; No date uses created-at/UUID. Sorting is
deterministic, not drag controlled. Completed is separate, newest completion
first with stable UUID tie-break. A change of locale changes presentation only.

Initially observe the first 50 tasks per selected mode, with a matching total
count; use stable identity-based row keys. A labeled Load more grows the observed
prefix by 50 and reobserves that prefix, avoiding offset shifts after mutations.
Continue groups across prefix boundaries without suggesting that the loaded
prefix is a complete group. Show loading/retry in the footer. Open/Completed
choice preserves its own scroll anchor. Return after edit restores the anchor if
it still exists, otherwise the nearest remaining item. Newly moved groups must
not steal focus.

**UX-TODO-03 — Separate Open and Complete actions.** A row has a leading
checkbox with a minimum 48 × 48 dp target and a separately focusable/clickable
text area with minimum 48 dp height. Clicking the title/date area opens the editor;
only the checkbox changes status. Do not make a parent click handler swallow or
duplicate the child action. A wrapping title, full localized due date and explicit
Overdue text communicate meaning; color and strikethrough are supplementary.
List titles may wrap; editor always exposes full text. No swipe-only/long-press
actions are required. Notes are readable/editable in the editor, without full
notes expanding every list row.

Completed rows retain the task title and original due date when present, plus
localized Completed on [date]; checking state communicates completion. Activating
the checked control reopens the same task, preserving title/notes/due date and
clearing current completed-at. Re-completion uses its actual new completion time.
There is no completion history or date backdating UI.

```text
Back                     Tasks                 Add task
[ Open (selected) ] [ Completed ]

Overdue
[ ]  Renew library book                 ← open editor
     Overdue · 18 Oct 2026
Today
[ ]  Call the repair shop
     Due today · 19 Oct 2026
Upcoming
[ ]  Submit expense report
     Due 22 Oct 2026
No date
[ ]  Compare desk lamps
                         Load more
```

Dates are examples, not mandated English formats. At large text the content
expands vertically; checkbox and open target remain distinct.

## 4. Today and Quick Capture

**UX-TODO-04 — Small actionable preview.** Show the Tasks card only when
Today's selected date equals the actual current device date. Hide it entirely
while browsing another date, including future dates; no mutable task state is
presented as a historical snapshot. Returning via Today restores it. Task list
buckets always reference actual today, independent of the globally selected date.

Eligible preview rows are current open tasks with due date ≤ today, ordered
overdue first then oldest due date, created-at and UUID. Show at most three and
the full eligible count, with View all always available. No-date and future tasks
remain available in Tasks. The card Add task opens a draft with due date today
visibly prefilled; the user can clear or change it. Completing a preview row
removes it after committed success and promotes the next eligible row.

If no preview is eligible, say “No open tasks due today or earlier” with View all
and Add task. The same accurate copy handles zero tasks or only future/no-date/
completed tasks without an extra all-task query; do not claim the user has no
tasks or has completed everything. Completed tasks are not mixed into the preview.
A failure in this card leaves Habits and Recent entries usable.

```text
Tasks                                     View all
4 open tasks due today or earlier
[ ] Renew library book     Overdue · [date]
[ ] Call the repair shop   Due today · [date]
[ ] Pick up parcel         Due today · [date]
Add task
```

**UX-TODO-05 — Capture dates stay unambiguous.** Add Task to the existing
Quick Capture sheet in its own labeled area, with helper copy “Starts without a
due date.” Scope current-time/browsed-date chips visibly to Mood/Journal/Memory
record capture; tapping Task must never convert those choices into a due date.
Place those three record choices together and Task immediately after their group.
More → Tasks → Add and Quick Capture → Task both default to No date. Do not add a
new global FAB or a task occurrence timestamp. The fourth choice and Close must
remain reachable with scrolling at narrow widths, landscape and large text.

## 5. Create, edit and dates

**UX-TODO-06 — One editor.** Use the shared editor frame with Cancel/Close,
title New task or Edit task, and fixed Save above keyboard/insets. Field order:
Title (required), Notes (optional multiline), Due date (No date or localized full
date, Change, and clear/remove action). Existing task status and Complete/Reopen
action follow the fields; Delete is a visually secondary destructive action.
Do not show status actions for an unsaved new draft. Autofocus Title for a new
task, while an existing task opens for reading without forcing the keyboard.

Title is trimmed, required, single-line and 1–200 Unicode code points. Notes
are plain multiline text up to 4,000 Unicode code points after trim; blank notes
normalize to null. Preserve authored text/case/direction. Keep rejected input
visible with inline field error; do not silently truncate pasted content. Reject
embedded title line breaks through validation instead of silently joining them.
Allow editing duplicates. Save validates and focuses/scrolls the first invalid
field; errors expose semantics and readable limits. Blank/invalid title cannot
produce a stored task. Save a valid clean existing editor by returning without
an unnecessary write; new drafts require an actual explicit Save.

**UX-TODO-07 — Optional civil due date.** Past, current and future dates are
valid, and past dates visibly become Overdue for an open task. Due date is a
Gregorian `LocalDate`, not midnight UTC, an occurrence timestamp or a deadline
time. It keeps the same calendar date on travel. Current device day determines
overdue status; habits' fixed tracking zone is not reused. No time/timezone
selector or reminder permission is needed. Recompute groups/card on foreground,
midnight and timezone changes using existing current-day behavior (currently
within 30 seconds while visible); recomputation must not alter editor fields.

Use the existing Material date dialog pattern: localized Gregorian picker, range
1900–2100 expanded to include an existing saved outlier within supported years
0001–9999, explicit Select date and Cancel. The picker uses its own draft;
Cancel/outside-tap/Back changes nothing in
the editor. Confirm changes the due field only; clearing dates is explicit and
separate from Cancel. UTC milliseconds used by Material DatePicker are transport
only and must decode to the same selected civil date without device-zone shifts.
Picking the existing date or canceling a picker does not mark the editor dirty.

```text
Cancel                         New task
Title *
[ Call the repair shop                         ]
Notes (optional)
[ Ask about the replacement part               ]
Due date
[ Today · 19 Oct 2026              Change ] [Remove]

                              [ Save ]
```

**UX-TODO-08 — Independent saved status.** Edits preserve completion status
and completed-at. An existing clean editor exposes Mark complete or Reopen task.
When field edits are unsaved, disable that action and show “Save changes before
completing this task” or the equivalent reopening instruction; Save changes
fields only and returns to origin. Row status actions remain the fastest path.
Do not silently save or discard fields when changing status. A successful status
action returns to origin and exposes its result there, with Undo for completion
only. Save, status and delete mutations are serialized; repeated taps while
pending cannot submit again.

## 6. Cancellation, destructive actions and concurrent changes

**UX-TODO-09 — Protect drafts.** Close, system Back and navigation-away share
one policy: pristine draft exits; dirty draft asks Discard changes? with Keep
editing and Discard. Normalized unchanged fields are clean; Today's prefilled due
date alone is the new draft baseline. Dialog dismissal keeps editing. Picker Back
closes the picker first; Back with an open IME follows normal keyboard dismissal
before leaving the editor. Save failure preserves all fields. Preserve draft,
baseline/revision, input errors and date-dialog state through recreation and app
language change using existing saved-state patterns. This is restoration of the
active draft, not a new draft library or durable autosave promise after force-stop.

Busy Save/status/delete visibly labels progress, disables duplicate/conflicting
actions and temporarily blocks editor exit until the result is known, matching
existing editor behavior. Do not claim success on a timeout or cancellation.

**UX-TODO-10 — Deliberate deletion.** Delete is available only for a saved
task, including completed tasks. Ask “Delete this task?” with the title and plain
copy “This permanently deletes the task from this device. Exported backups are
unchanged.” If edits are dirty, add that unsaved edits are discarded too. Cancel,
outside-tap and Back retain saved task and draft. Confirm deletes once; only
committed success closes the editor/removes its row. Failure retains task/draft
and shows action-specific Retry. No archive or task-delete Undo in this MVP.

**UX-TODO-11 — Safe conflicts and missing records.** Save/status/delete use
the loaded task revision. If a revision changed, retain typed fields, show “This
task changed. Reload the latest version to continue,” and disable further stale
mutations. Offer Reload latest and Back. Dirty Reload first asks whether to
discard these unsaved edits; Keep editing retains them for reading/copying but
does not overwrite the newer task. On confirmed reload replace baseline and
fields with current stored data. If the task was deleted elsewhere, show “This
task is no longer available,” keep any draft text readable/copyable, and offer
Back; never recreate the missing ID. For list action conflicts refresh the row
and show feedback; require a deliberate new action against its current state.

## 7. Loading, failures and successful feedback

**UX-TODO-12 — Every state explains its next action.**

| State | Required behavior |
| --- | --- |
| Initial list load | Localized loading label/progress, no premature empty-state copy. Add remains available when repository is ready for creation. |
| List load failed, no rows | Explain tasks could not be loaded; Retry and Back remain usable. |
| Refresh failed with saved rows | Keep last loaded rows, mark them as potentially out of date, show Retry; disable their mutations until a successful reload rather than acting on an unverified stale snapshot. Opening a row may attempt a fresh load. |
| Open empty | “No open tasks” with Add task and Completed control; do not imply all tasks have been deleted. |
| Completed empty | “No completed tasks yet” with Open control; no achievement/streak copy. |
| Pagination failed | Keep loaded rows and scroll position, replace loading footer with Retry loading more; no duplicate rows on retry. |
| Existing editor initial load | Loading label; keep restored draft text visible. Disable writes until valid baseline/revision is loaded. |
| Mutation pending | Preserve the affected row/editor with Saving/Completing/Reopening/Deleting status and disabled conflicting actions. Unrelated rows remain usable unless the whole data source is unavailable. |
| Mutation failed | Keep original saved state and pending intent beside row/editor, show concrete failure and Retry. Reconcile actual saved state before repeating an outcome whose result was uncertain. |
| Completion/reopen succeeded | Remove/move row only after commit, announce “Task completed”/“Task reopened.” Completion exposes a labeled Undo in the current surface; reopen has no Undo contract. Completed remains a durable route to reopen after temporary Undo disappears. |
| Undo failed/conflicted | Explain why it was not reversed; retain current saved state and offer refresh/retry only if its recorded revision is still eligible. Never undo a later edit or status change. |

Completion Undo reopens only that completion against its exact resulting revision;
it does not delete a task or undo a later edit/status change. Reopen has no Undo.
Keep the latest result visible until dismissed, replaced by the
next status result or leaving the surface, rather than relying on a short timeout.
This permits assistive-technology and slow readers to reach it. Announce feedback
once per result with polite live semantics; never announce entire list refreshes
as errors. Focus after a disappearing row moves to the next/previous row, or to
the group/list heading when none remains; it must not jump to another screen.

## 8. Accessibility, localization and adaptation

**UX-TODO-13 — Accessible by touch, speech and keyboard.** Every interactive
checkbox, text/open target, chip, date/clear, dialog, retry and status control is
at least 48 × 48 dp, with no overlapping target bounds. Use existing Material
contrast roles in light/dark themes; overdue has text as well as color. Headings
have heading semantics; Open/Completed expose selection; the checkbox exposes
checkbox role/state with a localized action naming the task. The title target
has “Open task [title]” semantics and speaks the due/overdue label once. Decorative
icons have no spoken label. Avoid one merged semantic node that hides the second
action. Completed titles stay readable rather than low-contrast disabled text.

Reading/focus order: screen title/actions, mode controls, group heading, checkbox,
open task content; then footer/result actions. RTL mirrors layout logically but
retains meaningful reading order. Tab/Shift+Tab traverse controls once; Enter or
Space activates focused controls without duplicate status actions. Title IME Next
moves to Notes; Notes accepts line breaks; Save remains a visible keyboard
reachable button and Enter in Notes never submits. Date/confirmation dialogs
trap traversal until dismissed and restore focus to their launcher. Validation
focuses the first error and brings it above the IME; dismissing an error does not
erase user input. TalkBack/keyboard tests must check focus after completion and
language change, not only static labels.

At 320/360/411 dp and font scales 1.0/1.3/2.0, allow labels, buttons and titles to
wrap/stack, maintain independent targets and vertical scrolling, and keep the last
row and editor Save above system bars/keyboard. Do not copy fixed-height rows or
single-line ellipsis into title entry. No essential action depends on hover,
swipe, drag, color, icon recognition or a disappearing toast.

**UX-TODO-14 — Three offline languages, unchanged data.** Add all Tasks copy,
errors, semantics and plurals to `values`, `values-fa` and `values-fr` together;
use positional resource arguments, localized numbers and quantity resources.
Translate complete phrases, not concatenated fragments. Suggested terms below
are draft copy requiring ordinary language review, not new app functionality:

| English | فارسی | Français |
| --- | --- | --- |
| Tasks | کارها | Tâches |
| Add task | افزودن کار | Ajouter une tâche |
| Open | باز | À faire |
| Completed | انجام‌شده | Terminées |
| Due date | تاریخ سررسید | Date d’échéance |
| No date | بدون تاریخ | Sans date |
| Overdue | سررسید گذشته | En retard |
| Reopen task | بازگشایی کار | Rouvrir la tâche |

Follow the repository's Gregorian calendar policy in every language. Persian
display uses localized Persian digits without changing stored ISO dates or
switching to Jalali; French uses localized month/date text. Show a full localized
date alongside Today/Overdue when ambiguity is possible. Use logical start/end
alignment and bidi isolation for generated mixed-direction fragments; never
transliterate user titles/notes or persist presentation bidi controls. Mixed
Persian/Latin user text and numbers remain authored content. Switching language
keeps task IDs, status, due date, current list selection and unsaved fields.

**UX-TODO-15 — Explain backup replacement.** Existing manual export privacy
copy must include task titles and notes among private archive contents; no task
text appears in logs, notifications or preview diagnostics. Restore preview shows
the archive's total task count alongside existing counts. For a schema-1/2/3
archive, show zero tasks and explicitly explain that replacing current data also
removes current tasks. Keep the existing confirmed full-replacement flow; cancel
or failed validation/publication keeps current tasks. Successful restore reloads
task lists and invalidates pre-restore editors/actions, including when a restored
task has the same ID. A retained draft shows the changed-data/reload explanation
from UX-TODO-11 rather than writing silently into the restored dataset. Existing
full local-data reset includes tasks and drafts; exported archives remain outside
its scope. Do not add a new per-feature reset control.

## 9. Acceptance and handoff

These are planned checks, **not executed results**. Add source-level tests for
meaningful state/date/identity behavior and Compose/instrumentation checks for
navigation, interaction and adaptation. Native TalkBack speech and actual keyboard
acceptance need a disposable device/emulator; existing Compose semantics coverage
does not establish that speech/manual acceptance has passed.

| Acceptance ID | Observable pass condition |
| --- | --- |
| UX-TODO-01 | More and Today open the same list; five tabs unchanged; Back/Save/recreation preserve origin and selected global date; Quick Capture origin restores. |
| UX-TODO-02 | Overdue/today/upcoming/no-date fixtures group/sort deterministically as the observed prefix grows by 50; Completed sorts separately and can load older rows. |
| UX-TODO-03 | Checkbox and text target are separate ≥48 dp actions; title never toggles; completed row reopens the same task with due/content preserved. |
| UX-TODO-04 | Today shows first three eligible rows and full count; future/no-date excluded; browsed past/future dates hide card; midnight/zone change updates eligibility. |
| UX-TODO-05 | Task choice ignores occurrence-date chips, defaults to No date, and remains reachable with record choices/Close at large text. Today Add visibly defaults today. |
| UX-TODO-06 | Empty/whitespace/newline/201-code-point titles and 4,001-code-point notes cannot save; valid Unicode/200/4,000 boundaries work; draft errors remain editable and announced. |
| UX-TODO-07 | Picker confirm/cancel/clear, same-date selection and timezone travel preserve correct civil date/dirty state; past dates valid; no off-by-one UTC conversion. |
| UX-TODO-08 | Saving edits to a completed task preserves completion; dirty status action explains required Save; rapid repeated status taps submit once. |
| UX-TODO-09 | Clean close, dirty Discard/Keep editing, picker/IME Back, failed Save and process recreation preserve the specified draft behavior without duplicate creation. |
| UX-TODO-10 | Delete cancel keeps saved/dirty data; failed delete retains it; success deletes once and returns to origin; copy covers dirty edits/exported backups. |
| UX-TODO-11 | Concurrent edit/status/delete yields retained draft and explicit reload/missing state; a stale action cannot overwrite or resurrect data. |
| UX-TODO-12 | Initial/refresh/page/action failures show scoped retry; no premature success or empty copy; completion/reopen/Undo feedback and focus match committed state. |
| UX-TODO-13 | EN/FA/FR × RTL/LTR × 320/360/411 dp × fonts 1.0/1.3/2.0 keep readable content/actions in both themes; keyboard and native TalkBack traversals are recorded separately. |
| UX-TODO-14 | Translation catalog/plurals/format signatures pass; locale change preserves drafts and date; Persian digits/Gregorian dates and mixed-direction text remain correct. |
| UX-TODO-15 | Export/privacy copy and restore preview include tasks; legacy archive warns current tasks will be removed; cancel/failure retains current data; successful restore invalidates old editors; isolated full reset removes tasks/drafts. |

Suggested implementation handoff slices (parent plan assigns final task IDs):

1. Presenter/domain contracts: mode/group sort/page state, title/note validation,
   civil-date bucketing, revision-aware action/Undo and editor baseline/draft rules
   for UX-TODO-02/06/07/08/09/11/12; coordinate with product persistence work.
2. Native Tasks list/editor/routes and More access for UX-TODO-01/02/03/06–13,
   using shared editor/error/theme patterns and bounded queries.
3. Today card and explicit Quick Capture separation for UX-TODO-04/05, with
   origin/selected-date preservation and no impact on habit/event counts.
4. EN/FA/FR catalog and accessibility/large-text/keyboard completion for
   UX-TODO-13/14; focused regressions followed by device acceptance.
5. Existing backup/restore/privacy surface updates for UX-TODO-15, coordinated
   with product migration/archive work and tested on disposable data only.

Reuse regression evidence targets, rather than assuming their past results cover
the new feature: `app/src/test/java/com/luma/lifeos/language/TranslationCatalogTest.kt`,
`LocalizedFormatsTest.kt`, `app/src/androidTest/java/com/luma/lifeos/AppNavigationTest.kt`,
`DetailNavigationTest.kt`, `TodayPersistenceTest.kt`, `JournalEditorScreenTest.kt`,
`habits/HabitCaptureFlowTest.kt`, `habits/HabitsNavigationTest.kt`,
`ui/PrimaryNavigationAccessibilityTest.kt` and `language/LanguageFlowTest.kt`.

## 10. Peer reconciliation record

The designer and product manager exchanged proposals and challenged each other
before writing: retain five tabs and add a More home; show Today preview only for
actual today instead of ambiguous historical/future task status; use floating
civil due dates/device day rather than habit tracking zone; use No date in global
capture and visible Due today in contextual capture; keep completion separate
from dirty field saves; require revision-safe retry and retained conflict drafts.
The PM accepted explicit dirty-delete copy and separate row/open checkbox targets;
the designer accepted bounded reads and no Timeline/Review/history expansion.
Written cross-review then aligned the observable prefix (50 then +50), completion-
only Undo, status-action return to origin, and a single accurate Today empty copy
without an all-task query. PM accepted mutation disabling on failed refresh and
the dirty-reload confirmation. Quick Capture is restricted to existing locations
in Timeline/Review; these drafts do not add a FAB to other destinations. This
reconciliation records team recommendations, not additional user approval.
