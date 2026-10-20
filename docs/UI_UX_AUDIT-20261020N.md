# Luma Android UI/UX Audit — Static Code Review

**Review date:** 20 October 2026  
**Baseline:** current working tree at HEAD 826427d, including existing local changes.  
**Method:** static source and resource inspection only. No Gradle commands, builds, tests, dependency installation, emulator/device use, or application execution. No implementation changes were made. This report is the sole created deliverable.

## 1. Executive Summary

Luma has a sound native Android foundation for a private, offline life record. Jetpack Compose and Material 3 provide a restrained interface with semantic light/dark colors, reusable entry cards, protected capture flows, and appropriately optional integrations. The five primary destinations are coherent. The application needs targeted corrections and shared interaction standards rather than a broad redesign.

The most consequential remaining weaknesses concern the meaning of actions and recovery: a habit Retry can conceal its original date; widget settings can describe an unknown privacy mode as Summary; a privacy-related switch lacks an accessible name; and Mood has weaker draft/deletion recovery than other editors. Other gaps concern navigation after deletion, reaching historical evidence, accessible announcements, growing personal catalogs, and consistent time presentation.

**Top five improvements by expected benefit:**

1. Show the original habit/date/zone on every retained retry and expose cross-date pending actions separately (UX-001).
2. Represent failed widget configuration reads as unknown, without asserting that private details are hidden (UX-002).
3. Give the widget privacy choice one clearly named, accessible switch interaction (UX-003).
4. Bring Mood draft size, restored-content visibility, and interrupted deletion up to the other editors' recovery standards (UX-004–006).
5. Keep task conflict recovery actionable and prevent retained recovery cards from consuming the task viewport (UX-007–008).

**Priority distribution:** 4 P1, 23 P2, 1 P3; no P0 established. P1 includes preventive reliability/privacy work whose harmful outcome has not been reproduced. UX-028 is explicitly an optional capability enhancement. Effort estimates are relative engineering scope, not delivery commitments.

**Strengths to preserve:**

- Direct Mood/Journal/Memory actions on Today; a fully expanded, scrollable Quick Capture chooser elsewhere.
- Distinct entry and habit-log counts; missing measurements remain distinguishable from recorded zero.
- Dirty-close confirmation, sticky IME-aware editor actions, retained accepted text, and pending-tag submission in Journal.
- Stable task identities, revision-aware conflicts, Undo, protected drafts, and retained off-preview task feedback.
- Habit generation/date guards, explicit tracking zones, and persistent Undo recovery.
- Default-hidden widget names, explicit per-instance opt-in, fictional previews, and responsive widget layouts.
- Separate integration access/import controls, backup replacement preview, and explicit irreversible-reset/external-copy explanations.
- English, French and Persian resources, RTL support, and semantic Material controls.

**Limits:** this review establishes source structures and reachable state paths, not rendered or observed usability outcomes. No actual crash, incorrect save, privacy disclosure, clipped dialog, failed TalkBack announcement, performance regression, or successful destructive flow was reproduced. Screenshots and previous acceptance documents are historical artifacts and do not certify this working tree. No visual usability score is assigned.

## 2. Application UI Overview

### Stack and design approach

The app module contains Compose routes/screens and Material 3 components. ViewModels and lifecycle-aware Flow collection manage UI state. The pure Kotlin domain defines records and action contracts; Room-backed data implementations supply persistence, search and review. Usage and Health Connect live in separate integration modules. Home-screen widgets use Android RemoteViews/XML with dedicated layout, rendering, intent and action coordination.

The primary theme is defined in [Theme.kt](../app/src/main/java/com/luma/lifeos/ui/theme/Theme.kt#L17). It supplies full light/dark semantic palettes and system-bar styling; Material's default type and shape systems remain in use. Shared components include EditorTopBar, DetailTopBar, EventCard, EmptyStateCard, AccessibleFeedback, DetailsDisclosure, capture timestamp controls, task rows and habit progress/feedback.

### Screens and navigation reviewed

| Area | Screens/states and principal flow | Assessment |
|---|---|---|
| App shell | Today / Timeline / Habits / Review / More; secondary routes hide primary navigation | State-saving primary navigation already exists; preserve five destinations |
| Today | Selected-day navigation, direct capture, habit/task previews, recent entries, expandable daily summary; loading/empty/error | Useful action-first hierarchy; remaining date CTA and task recovery gaps |
| Timeline | Selected-day paged feed, usage visibility switch, entry/habit cards, Search, capture FAB | Bounded feed and stale-day protection exist; action semantics/header consistency need work |
| Mood | Create/edit, score/emotions, optional energy/stress/note, timestamp picker, context, deletion/discard | Clear scales and verbal score descriptions; weaker restoration and deletion recovery |
| Journal | Create/edit/detail, body/title/tags, pending tag validation, context/photos | Field errors and photo recovery already implemented; shared context/navigation gaps |
| Memories | Direct capture, promotion/marker editing, unmark versus delete, review highlights | Preserves underlying source entry; preserve this distinction in navigation fixes |
| People & Places | Combined catalog tabs, create/edit/detail, linked entry histories, capture selectors | Core CRUD flows exist; catalog growth and creation return results need attention |
| Photos | System selection, private copy/import recovery, remove, unavailable image retry, previews | Draft leasing and private storage are strengths; no full-image review destination |
| Tasks | Today due preview, Open/Completed lists, due buckets, paging, editor/date picker, conflict/retry/Undo | Extensive safeguards; visible conflict opening and unbounded recovery layout remain |
| Habits | Scheduled/other groups, selected-day progress, creation/editing, detail/history, correction, management | Old stale-date action issue is fixed; pending-action identity and historical targeting remain |
| Weekly Review | Independent week selection, overview, sections chooser, mood/activity/usage/habit evidence, sources | Sticky section navigation and partial-period explanations already exist |
| Saved usage sessions | Daily app totals, chronological sessions, exact durations, continuation/ongoing/partial states | Distinct evidence states exist; included in navigation/time-format review |
| Search | Query, dates/type/catalog filters, removable criteria, initial/empty/no-match/error, bounded pages | Recovery and active criteria are implemented; generic title understates scope |
| Settings | Hub, System/Light/Dark theme, language, privacy/reset, backup/restore, integrations, widgets | Sensible grouping; page chrome and operation feedback vary |
| Usage / Health | Access/provider state, import preference, manual import/refresh, retained-history explanations | Correct consent boundaries; local stale-error state needs repair |
| Widgets | Discovery/add, instance configuration, summary/details, scrollable Habits/Tasks rows, Capture, loading/empty/error/Undo/Retry | Responsive layout work is present; two configuration/privacy feedback issues remain |

The navigation source is [AppDestinations.kt](../app/src/main/java/com/luma/lifeos/ui/navigation/AppDestinations.kt#L8) and [LifeOsApp.kt](../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt#L172). More exposes Tasks, People & Places, and Settings. Today/Timeline/Habits share selected-day context; Review selects its own week. Search and review sources open existing entry routes. Widgets enter the existing chooser/editors or task/habit destinations.

### Coverage and evidence discipline

Current UI implementations, state holders, relevant repository/domain contracts, navigation, theme, widget layouts/resources and the English/French/Persian string files were inspected. README and existing audit/acceptance documents supplied product context only; their instructions and earlier observations were not treated as current test results.

The locale files have matching resource-name sets and numbered format-placeholder signatures in the inspected source. This is structural parity, not a translation-fluency assessment. Persian RTL is declared in AndroidManifest; it does not by itself prove correct mixed-direction reading order.

Source-color calculation gives the following contrast ratios, using opaque sRGB values in Theme.kt. These are reassuring palette checks, not rendered contrast certification:

| Foreground / background | Light | Dark |
|---|---:|---:|
| onSurface / surface | 16.07:1 | 14.23:1 |
| onSurfaceVariant / surface | 8.78:1 | 10.84:1 |
| primary / surface | 7.05:1 | 10.92:1 |
| onPrimary / primary | 7.51:1 | 7.75:1 |
| error / surface | 6.06:1 | 10.85:1 |

Do not infer actual contrast over photographs, alpha-blended/disabled states or launcher surfaces from this table. No evidence supports replacing the brand palette.

**Historical findings explicitly not carried forward:** Habits now clears stale rows synchronously, increments selection generations, guards late emissions and rejects stale log callbacks ([HabitsViewModel.kt:58](../app/src/main/java/com/luma/lifeos/ui/habits/HabitsViewModel.kt#L58), dateForRow/log at line 120). Persistent habit Undo failure recovery exists in HabitUndoFeedback. Search already differentiates initial/empty/no-match states. Review already has section navigation. Current widgets already implement shared Capture, scrolling collections and compact recovery. These should not be reimplemented from older audit descriptions.

### Finding conventions

- **Confirmed:** a source-level omission, contradiction, or reachable state path. Downstream harm may still require runtime reproduction.
- **Runtime risk:** the layout/lifecycle structure is confirmed, but its user-visible failure has not been observed.
- **Optional enhancement:** an absent capability that is not required to correct existing behavior.
- **P0:** essential functionality blocked or serious usability/accessibility harm; **P1:** significant harm in an important flow; **P2:** meaningful friction/inconsistency; **P3:** polish or optional enhancement.
- **Confidence** measures support for the stated finding, not successful runtime reproduction. Low effort means a focused component/state/copy change; Medium spans coordinated UI/state/navigation work; High requires substantial capability/data/lifecycle work.

## 3. Detailed Findings

### UX-001 — Habit retries conceal the date they will affect

**ID:** UX-001  
**Title:** Show original date on retained habit actions  
**Priority:** P1 — High  
**Category:** Feedback / Date context  
**Affected Screen:** Habits, Today habit preview, Habit detail  
**Source:** [HabitsScreen.kt:159](../app/src/main/java/com/luma/lifeos/ui/habits/HabitsScreen.kt#L159), HabitsScreen/HabitRow; [TodayHabitsCard.kt:86](../app/src/main/java/com/luma/lifeos/ui/habits/TodayHabitsCard.kt#L86); [HabitDetailRoute.kt:174](../app/src/main/java/com/luma/lifeos/ui/habits/HabitDetailRoute.kt#L174); [HabitsViewModel.kt:145](../app/src/main/java/com/luma/lifeos/ui/habits/HabitsViewModel.kt#L145), retryLog/enqueueLog  
**Confidence:** High  
**User Impact:** High  
**Implementation Effort:** Medium

**Problem:** Confirmed: pending actions are associated with displayed rows by habit ID alone. Date changes retain pending logs. Generic Saving/Retry text omits the original input date/zone, while retry correctly reuses that original input. A failure on A can therefore show Retry inside the same habit's row on B and still affect A.

**Why It Matters:** Users can reasonably interpret the action as belonging to the day currently shown. This is a misleading presentation of a preserved command, not evidence that retry corrupts its stored date.

**Recommended Solution:** Show habit, original localized date and relevant tracking zone on pending/failed actions. Present cross-date actions separately with Open date; preserve action ID and original payload.

**Expected Improvement:** Users can retry deliberately without confusing historical and current logging.

### UX-002 — Unknown widget privacy mode is reported as Summary

**ID:** UX-002  
**Title:** Distinguish unavailable widget configuration from summary mode  
**Priority:** P1 — High  
**Category:** Privacy / Status accuracy  
**Affected Screen:** Settings → Home-screen widgets  
**Source:** [WidgetSettingsRoute.kt:81](../app/src/main/java/com/luma/lifeos/ui/widgets/WidgetSettingsRoute.kt#L81), WidgetSettingsRoute; InstanceCard at line 188; configurationOrNull at line 203  
**Confidence:** High  
**User Impact:** High  
**Implementation Effort:** Low

**Problem:** Confirmed: read exceptions become null, which becomes a synthetic WidgetInstance with showDetails=false. InstanceCard subsequently describes the mode as Summary. An existing details-enabled widget can thus receive a reassuring Summary label when its configuration could not be read.

**Why It Matters:** This screen explains whether personal names/progress/actions appear on the launcher. A fallback default is insufficient evidence of the current privacy setting. Actual disclosure was not reproduced.

**Recommended Solution:** Model Known, Unconfigured and Unavailable separately. On read failure show “Mode unavailable” and Retry/Configure, retaining the live widget identity. Assert Summary only after a successful read or acknowledged configuration save.

**Expected Improvement:** Privacy status remains truthful during storage failures.

### UX-003 — The widget privacy switch has no accessible name

**ID:** UX-003  
**Title:** Associate widget details disclosure with its switch  
**Priority:** P1 — High  
**Category:** Accessibility / Privacy controls  
**Affected Screen:** Widget configuration  
**Source:** [WidgetConfigurationActivity.kt:253](../app/src/main/java/com/luma/lifeos/widget/WidgetConfigurationActivity.kt#L253), ConfigurationContent  
**Confidence:** High  
**User Impact:** High  
**Implementation Effort:** Low

**Problem:** Confirmed: “Show names and row actions” and its explanation are sibling text to a Switch. Neither the row nor switch associates this text with the control through semantics. Its native checked state exists, but the application supplies no accessible name.

**Why It Matters:** A screen-reader user must understand the privacy consequence at the focused control. Exact spoken output remains unverified.

**Recommended Solution:** Make the labelled row one toggleable control with Role.Switch and merged label/description; use a child Switch with a null change callback to avoid duplicate interactive targets. Preserve checked/disabled state and the current opt-in default.

**Expected Improvement:** The privacy choice is understandable and operable as one labelled action.

### UX-004 — Unbounded Mood notes are copied into saved instance state

**ID:** UX-004  
**Title:** Bound new Mood note input and protect large drafts  
**Priority:** P1 — High  
**Category:** Form reliability / Draft preservation  
**Affected Screen:** Mood editor  
**Source:** [MoodEditorViewModel.kt:102](../app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorViewModel.kt#L102), setNote/change/persist at lines 160–184; [MoodEditorScreen.kt:157](../app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorScreen.kt#L157); [Mood.kt](../core/domain/src/main/kotlin/com/luma/lifeos/domain/model/Mood.kt)  
**Confidence:** Medium  
**User Impact:** High  
**Implementation Effort:** Medium

**Problem:** Confirmed: arbitrary note text is accepted and the entire string is placed in SavedStateHandle on every edit. Neither UI nor MoodInput supplies a bound. Runtime risk: very large pasted text can exhaust saved-state capacity during backgrounding/restoration; no failure threshold or crash was measured.

**Why It Matters:** A user can lose access to an important personal draft through a routine paste/background operation.

**Recommended Solution:** Define a documented new-input limit, consistent with domain validation, with retained accepted text and inline feedback. A 5,000 UTF-16-unit limit is a reasonable proposed starting policy, not an existing requirement. Preserve older longer records for reading/copying and shortening; store oversized/restored drafts outside the instance-state Bundle.

**Expected Improvement:** Predictable editing and resilient restoration without silent truncation.

### UX-005 — Interrupted Mood deletion has no reconciliation path

**ID:** UX-005  
**Title:** Reconcile committed Mood deletion after interruption  
**Priority:** P2 — Medium  
**Category:** Destructive-action recovery  
**Affected Screen:** Mood editor  
**Source:** [MoodEditorViewModel.kt:81](../app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorViewModel.kt#L81), load; delete/write at lines 136–149; [RoomMoodRepository.kt:85](../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomMoodRepository.kt#L85)  
**Confidence:** High  
**User Impact:** Medium  
**Implementation Effort:** Medium

**Problem:** Confirmed recovery gap: deletion completion is saved only after repository.delete returns. If deletion commits before the process loses the result, restoration requires the now-missing record and enters LOAD error. Retry cannot reconstruct a deleted record. This interruption sequence was not executed.

**Why It Matters:** Successful deletion can appear to be a persistent failure, leaving the user uncertain about their private data.

**Recommended Solution:** Persist deletion intent/identity before the write and reconcile existence on restoration. A missing record after that intent should complete the deletion flow; unrelated missing records need an honest missing state. Reuse the stronger reconciliation patterns in other editor families.

**Expected Improvement:** Clear outcomes after an interrupted destructive operation.

### UX-006 — Restored Mood text disappears behind a load error

**ID:** UX-006  
**Title:** Keep restored Mood draft content visible during recovery  
**Priority:** P2 — Medium  
**Category:** Draft recovery / Form states  
**Affected Screen:** Mood editor  
**Source:** [MoodEditorViewModel.kt:66](../app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorViewModel.kt#L66), initialization/readDraft/load; [MoodEditorScreen.kt:116](../app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorScreen.kt#L116), MoodEditorScreen  
**Confidence:** High  
**User Impact:** Medium  
**Implementation Effort:** Low

**Problem:** Confirmed: the ViewModel reconstructs saved draft text before loading, but all draft fields sit inside if(state.ready). A failed record/emotion preparation sets ready=false, leaving only error/Retry even when personal text is retained in memory. Journal explicitly keeps restored text visible on load failure.

**Why It Matters:** Users cannot inspect or copy their retained note and may believe it has vanished.

**Recommended Solution:** Render restored values in a disabled/read-only recovery form while dependencies load or fail. Keep mutation/Save unavailable until safe, retain Retry, and expose note copying without requiring successful catalog preparation.

**Expected Improvement:** Recovery preserves both data and the user's access to it.

### UX-007 — A conflicted Today task cannot be opened for review

**ID:** UX-007  
**Title:** Separate task opening from mutation eligibility  
**Priority:** P2 — Medium  
**Category:** Error recovery / Tasks  
**Affected Screen:** Today tasks  
**Source:** [TodayTasksCard.kt:103](../app/src/main/java/com/luma/lifeos/ui/todo/TodayTasksCard.kt#L103), TodayTasksCard; [TodoRow.kt:91](../app/src/main/java/com/luma/lifeos/ui/todo/TodoRow.kt#L91), TodoRow; [strings.xml:891](../app/src/main/res/values/strings.xml#L891)  
**Confidence:** High  
**User Impact:** Medium  
**Implementation Effort:** Low

**Problem:** Confirmed: CONFLICT makes the visible row enabled=false. TodoRow applies that flag to both its checkbox and task-opening button. The message asks users to review current state, but only Dismiss remains. Off-preview conflicts already expose Open.

**Why It Matters:** The visible recovery instruction cannot be followed from the affected row.

**Recommended Solution:** Split canOpen from canMutate. Allow opening an existing conflicting task while disabling completion; preserve the missing-task restriction. Use the same Open recovery action on visible and off-preview conflicts.

**Expected Improvement:** Users can inspect and resolve a conflict directly.

### UX-008 — Retained task errors can crowd out the task list

**ID:** UX-008  
**Title:** Bound or scroll the task recovery region  
**Priority:** P2 — Medium  
**Category:** Responsive layout / Error recovery  
**Affected Screen:** Full Tasks list  
**Source:** [TodoListScreen.kt:99](../app/src/main/java/com/luma/lifeos/ui/todo/TodoListScreen.kt#L99), TodoListScreen; recovery cards at lines 165–188; weighted list at line 245  
**Confidence:** Medium  
**User Impact:** Medium  
**Implementation Effort:** Low

**Problem:** Runtime risk grounded in confirmed layout: every off-page pending/error card is an unbounded child of a non-scrollable Column above the weighted LazyColumn. Multiple retained failures after a filter/tab change can consume the available height, particularly with large text.

**Why It Matters:** The recovery design can make tasks and some recovery controls hard to reach precisely when users need help.

**Recommended Solution:** Render retained actions as stable items in the scrollable list or use a bounded, independently scrollable recovery region with a compact summary. Keep Undo discoverable without reserving unbounded height. Preserve all retained action identities.

**Expected Improvement:** Tasks and recovery remain reachable with multiple failures.

### UX-009 — Past-day summary Check in opens a current-time entry

**ID:** UX-009  
**Title:** Preserve time context on the summary capture action  
**Priority:** P2 — Medium  
**Category:** Navigation / Date context  
**Affected Screen:** Today → daily summary  
**Source:** [DailySummaryCard.kt:51](../app/src/main/java/com/luma/lifeos/ui/summary/DailySummaryCard.kt#L51), DailySummaryCard; [LifeOsApp.kt:240](../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt#L240); [MoodEditorViewModel.kt:60](../app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorViewModel.kt#L60)  
**Confidence:** High  
**User Impact:** Medium  
**Implementation Effort:** Low

**Problem:** Confirmed: a selected day's missing-mood action calls generic navigation to mood without initialTime. The editor therefore initializes now, even when the summary belongs to a past day. Nearby Today capture shortcuts already offer explicit selected-date versus now behavior.

**Why It Matters:** A user attempting to fill a historical gap can save under today if they overlook the timestamp. No wrong-date save was executed.

**Recommended Solution:** Route the action through the existing dated-capture choice used by TodayCaptureActions. Alternatively, if current-time capture is intentional, label it “Check in now” and explain that it does not fill the selected day.

**Expected Improvement:** Capture intent and initialized date agree.

### UX-010 — Deleting an entry can return to its missing detail page

**ID:** UX-010  
**Title:** Remove deleted entry detail destinations from the back stack  
**Priority:** P2 — Medium  
**Category:** Navigation / Destructive-action feedback  
**Affected Screen:** Event detail → Mood/Memory/Journal editor  
**Source:** [LifeOsApp.kt:480](../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt#L480), editor completion callbacks at lines 491, 511 and 528; [EventDetailRoute.kt:62](../app/src/main/java/com/luma/lifeos/ui/timeline/EventDetailRoute.kt#L62)  
**Confidence:** High  
**User Impact:** Medium  
**Implementation Effort:** Medium

**Problem:** Confirmed: Mood and Memory completion pop only the editor. Deleting an entry opened from generic Event Detail exposes the missing source beneath it. Journal cleanup handles journal_detail but does not cover generic event_detail.

**Why It Matters:** Successful deletion ends on a dead-end message rather than the originating collection.

**Recommended Solution:** Return an explicit saved/deleted/unmarked result with event identity. Pop a matching deleted detail destination before returning to the source list. Unmarking a promoted memory must retain its source detail.

**Expected Improvement:** Destructive actions end on a useful, predictable destination.

### UX-011 — Creating a person/place during capture does not attach it

**ID:** UX-011  
**Title:** Return created context to the calling draft  
**Priority:** P2 — Medium  
**Category:** Capture workflow  
**Affected Screen:** Mood, Journal and Memory context creation  
**Source:** [PersonPicker.kt:41](../app/src/main/java/com/luma/lifeos/ui/people/PersonPicker.kt#L41); [PlacePicker.kt:38](../app/src/main/java/com/luma/lifeos/ui/places/PlacePicker.kt#L38); [LifeOsApp.kt:454](../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt#L454), person/place completion and capture routes  
**Confidence:** High  
**User Impact:** Medium  
**Implementation Effort:** Medium

**Problem:** Confirmed interaction gap: New person/place opens creation, but completion returns only a deletion Boolean. The new ID is never passed to the calling capture draft. Users must return, reopen selection, find the new item and select it.

**Why It Matters:** The user's context-attachment task appears complete before the actual link exists.

**Recommended Solution:** Return Created(id) through navigation saved state and consume it once in the calling editor. Attach it automatically, respect the people-selection limit, and announce the result. Creation from a catalog should retain its normal catalog behavior.

**Expected Improvement:** In-context creation completes the original task with fewer steps.

### UX-012 — Context selection requires scanning the entire catalog

**ID:** UX-012  
**Title:** Replace eager catalog chips with searchable selection  
**Priority:** P2 — Medium  
**Category:** Scalability / Form usability  
**Affected Screen:** Capture person/place selectors; People & Places  
**Source:** [PersonPicker.kt:58](../app/src/main/java/com/luma/lifeos/ui/people/PersonPicker.kt#L58), PersonPicker; [PlacePicker.kt:55](../app/src/main/java/com/luma/lifeos/ui/places/PlacePicker.kt#L55), PlacePicker; [PeopleRoute.kt:84](../app/src/main/java/com/luma/lifeos/ui/people/PeopleRoute.kt#L84); [PlacesRoute.kt:63](../app/src/main/java/com/luma/lifeos/ui/places/PlacesRoute.kt#L63)  
**Confidence:** High  
**User Impact:** Medium  
**Implementation Effort:** Medium

**Problem:** Confirmed: the expanded selectors eagerly compose all available names as FlowRow chips, with no query. Catalog lists also lack filtering. A growing personal library creates a long visual scan and pushes later capture controls farther away. Performance magnitude is unmeasured.

**Why It Matters:** Reusing familiar context becomes slower as the product succeeds at retaining history.

**Recommended Solution:** Use a searchable sheet/dialog with a lazy result list, persistent selected items, stable IDs, available duplicate-name context, Clear/Done and integrated creation. Reuse SearchOptionPicker patterns where practical without importing its search-filter limits indiscriminately.

**Expected Improvement:** Fast, predictable selection in established libraries.

### UX-013 — A historical habit link does not locate the requested repetition

**ID:** UX-013  
**Title:** Anchor habit detail to the selected log  
**Priority:** P2 — Medium  
**Category:** Navigation / Evidence drill-through  
**Affected Screen:** Timeline → Habit detail/history  
**Source:** [LifeOsApp.kt:445](../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt#L445); [HabitHistory.kt:47](../app/src/main/java/com/luma/lifeos/ui/habits/HabitHistory.kt#L47), HabitHistory; [HabitsViewModel.kt:368](../app/src/main/java/com/luma/lifeos/ui/habits/HabitsViewModel.kt#L368), refreshDetail  
**Confidence:** High  
**User Impact:** Medium  
**Implementation Effort:** Medium

**Problem:** Confirmed: selectedLogId changes only a testTag. There is no visible/semantic selection or scroll target. Initial history loads the newest 50 lifetime logs, without loading around the selected ID; an older linked repetition may be absent.

**Why It Matters:** Opening a specific record does not reliably reveal the evidence users intended to inspect or correct.

**Recommended Solution:** Resolve the selected record and load/anchor its history window, or show it explicitly above paged history. Scroll it into view and add a visible Selected repetition label, selected semantics and a restrained arrival announcement. Handle a deleted target explicitly.

**Expected Improvement:** Historical links identify the exact repetition rather than merely the habit.

### UX-014 — Habit management sits after all loaded history

**ID:** UX-014  
**Title:** Keep habit editing reachable as history grows  
**Priority:** P2 — Medium  
**Category:** Information hierarchy / UI architecture  
**Affected Screen:** Habit detail  
**Source:** [HabitDetailRoute.kt:203](../app/src/main/java/com/luma/lifeos/ui/habits/HabitDetailRoute.kt#L203), HabitDetailScreen; schedule/management at lines 204/227; [HabitHistory.kt:45](../app/src/main/java/com/luma/lifeos/ui/habits/HabitHistory.kt#L45)  
**Confidence:** High  
**User Impact:** Medium  
**Implementation Effort:** Medium

**Problem:** Confirmed: up to 50 initial log cards, and all additional loaded cards, precede schedule/management. They are eagerly composed inside one LazyColumn item. Edit/Pause/Archive/Delete consequently move farther away as history expands.

**Why It Matters:** A common editing action becomes a lengthy scroll; a large history also defeats per-row lazy composition.

**Recommended Solution:** Expose Edit in the pinned detail bar and place a compact Manage/schedule disclosure before the long history. Keep destructive commands inside the disclosure with confirmation. Emit individual history records as stable lazy items.

**Expected Improvement:** Management stays discoverable without displacing progress and recent history.

### UX-015 — Habit failures display the wrong operation's message

**ID:** UX-015  
**Title:** Use operation-specific habit errors and recovery  
**Priority:** P2 — Medium  
**Category:** UX writing / Error states  
**Affected Screen:** Habit detail  
**Source:** [HabitDetailRoute.kt:195](../app/src/main/java/com/luma/lifeos/ui/habits/HabitDetailRoute.kt#L195), HabitDetailScreen; [HabitsViewModel.kt:255](../app/src/main/java/com/luma/lifeos/ui/habits/HabitsViewModel.kt#L255), loadMoreHistory; deleteHabit at line 491; [strings.xml:803](../app/src/main/res/values/strings.xml#L803)  
**Confidence:** High  
**User Impact:** Medium  
**Implementation Effort:** Low

**Problem:** Confirmed: every detail.actionError renders habit_correction_error. History-loading and whole-habit deletion failures also set this field. The message incorrectly says a repetition update failed and a correction remains.

**Why It Matters:** Users cannot tell which operation failed or what to retry.

**Recommended Solution:** Replace the untyped shared error with operation identity and relevant target. Use “Couldn't load older repetitions” beside Load older, and a deletion-specific message beside management. Preserve actual correction state/copy for correction failures; do not claim a failed delete definitely left data untouched without reconciliation.

**Expected Improvement:** Accurate, actionable recovery guidance.

### UX-016 — Review source page labels reverse chronological direction

**ID:** UX-016  
**Title:** Match pagination wording to source order  
**Priority:** P2 — Medium  
**Category:** Navigation / UX writing  
**Affected Screen:** Weekly Review → contributing sources  
**Source:** [ReviewSourcesRoute.kt:58](../app/src/main/java/com/luma/lifeos/ui/review/ReviewSourcesRoute.kt#L58), ReviewSourcesRoute, next page at line 66; [ReviewSql.kt:37](../core/data/src/main/kotlin/com/luma/lifeos/data/review/ReviewSql.kt#L37), pageSql; [strings.xml:503](../app/src/main/res/values/strings.xml#L503)  
**Confidence:** High  
**User Impact:** Medium  
**Implementation Effort:** Low

**Problem:** Confirmed: sources are ordered oldest first. Decreasing offset uses Search's “Show newer matches,” while increasing offset uses “Show older matches.” Both labels describe the opposite temporal direction. This applies when sources exceed one 50-entry page.

**Why It Matters:** Users exploring evidence navigate in the wrong direction.

**Recommended Solution:** Add source-specific Previous page/Next page labels, or correctly mapped older/newer wording in all three languages. Preserve Search's existing descending-order wording.

**Expected Improvement:** Predictable chronological exploration with a small isolated change.

### UX-017 — Successful integration refresh leaves obsolete error text

**ID:** UX-017  
**Title:** Clear the relevant error after successful retry  
**Priority:** P2 — Medium  
**Category:** Integration feedback / State recovery  
**Affected Screen:** Usage access and Health Connect settings  
**Source:** [UsageSettingsViewModel.kt:24](../app/src/main/java/com/luma/lifeos/ui/usage/UsageSettingsViewModel.kt#L24), observe/retryConfiguration; [HealthSettingsViewModel.kt:23](../app/src/main/java/com/luma/lifeos/ui/health/HealthSettingsViewModel.kt#L23); [UsageSettingsRoute.kt:104](../app/src/main/java/com/luma/lifeos/ui/usage/UsageSettingsRoute.kt#L104); [HealthSettingsCard.kt:105](../app/src/main/java/com/luma/lifeos/ui/health/HealthSettingsCard.kt#L105)  
**Confidence:** High  
**User Impact:** Medium  
**Implementation Effort:** Low

**Problem:** Confirmed: successful configuration emissions preserve actionError. Refresh calls retryConfiguration plus shared availability refresh; neither clears that card's error flag. A recovered status can coexist with old “could not update” feedback.

**Why It Matters:** Users cannot trust whether access/configuration recovery succeeded.

**Recommended Solution:** Distinguish observation, access-refresh and import-action errors. Clear each only when its relevant retry succeeds, and expose an appropriate retry for the actual remaining failure.

**Expected Improvement:** Status and feedback describe the same current outcome.

### UX-018 — Several important asynchronous results lack announcement semantics

**ID:** UX-018  
**Title:** Apply accessible feedback consistently  
**Priority:** P2 — Medium  
**Category:** Accessibility / Feedback states  
**Affected Screen:** Backup, Language, Today, Timeline, Daily summary, Event detail  
**Source:** [BackupSettingsCard.kt:71](../app/src/main/java/com/luma/lifeos/ui/backup/BackupSettingsCard.kt#L71), BackupCardContent; [LanguageSettingsCard.kt:49](../app/src/main/java/com/luma/lifeos/language/LanguageSettingsCard.kt#L49); [TodayScreen.kt:130](../app/src/main/java/com/luma/lifeos/ui/today/TodayScreen.kt#L130); [TimelineRoute.kt:99](../app/src/main/java/com/luma/lifeos/ui/timeline/TimelineRoute.kt#L99); [DailySummaryCard.kt:40](../app/src/main/java/com/luma/lifeos/ui/summary/DailySummaryCard.kt#L40); [AccessibleFeedback.kt](../app/src/main/java/com/luma/lifeos/ui/common/AccessibleFeedback.kt)  
**Confidence:** High  
**User Impact:** Medium  
**Implementation Effort:** Low

**Problem:** Confirmed: these results/errors use ordinary Text where the app already supplies an accessible feedback component with polite live-region and error semantics. Text is still readable when focused; spontaneous result announcements are not established.

**Why It Matters:** Users may miss export success, a failed language change, or a loading failure without revisiting the result area.

**Recommended Solution:** Use AccessibleFeedback or equivalent scoped semantics for asynchronous changes. Pass isError=false for success; avoid repeated announcements on every refresh. Keep Retry adjacent and verify actual TalkBack speech/focus later.

**Expected Improvement:** More consistent nonvisual awareness of operation outcomes.

### UX-019 — Saved records and capture use different hour-cycle policies

**ID:** UX-019  
**Title:** Centralize display time formatting  
**Priority:** P2 — Medium  
**Category:** Localization / Consistency  
**Affected Screen:** Capture timestamps, Timeline, Search, Review and histories  
**Source:** [LocalizedFormats.kt:13](../app/src/main/java/com/luma/lifeos/language/LocalizedFormats.kt#L13), localizedTime; [EventCard.kt:44](../app/src/main/java/com/luma/lifeos/ui/timeline/EventCard.kt#L44); [CaptureTimestampRow.kt:56](../app/src/main/java/com/luma/lifeos/ui/common/CaptureTimestampRow.kt#L56); [HabitHistory.kt:53](../app/src/main/java/com/luma/lifeos/ui/habits/HabitHistory.kt#L53)  
**Confidence:** High  
**User Impact:** Medium  
**Implementation Effort:** Medium

**Problem:** Confirmed: capture reads Android's 12/24-hour preference, while localizedTime defaults to 24-hour and many saved-record displays call it without a preference. Other histories use locale-default formatters rather than the same explicit hour/digit policy.

**Why It Matters:** The same timestamp can look different before and after saving; locale-specific number handling varies between surfaces.

**Recommended Solution:** Provide one shared display formatter policy receiving the app locale and Android hour preference, including date/time combinations. Migrate saved records and history to it. Keep persisted instants, ISO/civil dates and intended Gregorian calendar behavior unchanged.

**Expected Improvement:** Familiar, consistent localized times across journeys.

### UX-020 — Entry card accessibility actions promise Edit but open details

**ID:** UX-020  
**Title:** Describe the actual entry-card destination  
**Priority:** P2 — Medium  
**Category:** Accessibility / Navigation clarity  
**Affected Screen:** Timeline, Search and Review sources  
**Source:** [EventCard.kt:33](../app/src/main/java/com/luma/lifeos/ui/timeline/EventCard.kt#L33), EventCard action label; [LifeOsApp.kt:148](../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt#L148), openEvent; [SearchRoute.kt:98](../app/src/main/java/com/luma/lifeos/ui/search/SearchRoute.kt#L98)  
**Confidence:** High  
**User Impact:** Medium  
**Implementation Effort:** Low

**Problem:** Confirmed: editable Mood/Memory cards derive Edit action labels from record type, independent of the supplied callback. Timeline/Search/Review route the card to details, where a separate Edit action exists. Today sometimes opens an editor directly, so one unconditional label cannot fit all callers.

**Why It Matters:** The action announced to an assistive-technology user does not match the result.

**Recommended Solution:** Let callers supply the actual Open/Edit action label, or standardize card opening to details and keep explicit edit commands. Preserve separately labelled bookmark/memory actions.

**Expected Improvement:** Spoken interaction matches the navigation outcome.

### UX-021 — Page chrome and inset ownership vary across screen families

**ID:** UX-021  
**Title:** Adopt one shared page/header inset contract  
**Priority:** P2 — Medium  
**Category:** Visual consistency / Navigation / Architecture  
**Affected Screen:** Editors, secondary pages, Today/Timeline/Habits/More  
**Source:** [LifeOsApp.kt:202](../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt#L202), outer Scaffold/NavHost; [EditorTopBar.kt:23](../app/src/main/java/com/luma/lifeos/ui/common/EditorTopBar.kt#L23); [DetailTopBar.kt:70](../app/src/main/java/com/luma/lifeos/ui/common/DetailTopBar.kt#L70); [SettingsRoute.kt:69](../app/src/main/java/com/luma/lifeos/ui/settings/SettingsRoute.kt#L69); [PrivacySettingsRoute.kt:64](../app/src/main/java/com/luma/lifeos/ui/privacy/PrivacySettingsRoute.kt#L64)  
**Confidence:** Medium  
**User Impact:** Medium  
**Implementation Effort:** Medium

**Problem:** Confirmed variation: details use a shared zero-inset bar; editors retain default app-bar/nested Scaffold insets even though the outer shell applies padding without consuming insets. Other pages hand-build Back/title columns, and Privacy/saved usage place Back inside scrolling content. Today uses titleLarge while Timeline/More use headlineLarge. Duplicate inset space is a runtime risk, not a measured gap.

**Why It Matters:** Header position, navigation availability and content start can feel inconsistent; nested inset handling can waste compact-screen space.

**Recommended Solution:** Define one owner for system insets and consume applied padding appropriately. Create common primary/secondary/editor page patterns with consistent pinned Back/title/actions. Pilot Today, Timeline and one editor before wider migration; preserve editor close/discard semantics.

**Expected Improvement:** Predictable navigation and more consistent use of vertical space.

### UX-022 — Visible headings are missing semantic heading roles

**ID:** UX-022  
**Title:** Make key page and section headings navigable  
**Priority:** P2 — Medium  
**Category:** Accessibility / Information hierarchy  
**Affected Screen:** Today, Timeline, Search, Habit history, Daily summary  
**Source:** [TodayScreen.kt:86](../app/src/main/java/com/luma/lifeos/ui/today/TodayScreen.kt#L86), title and Recent entries at line 112; [TimelineRoute.kt:76](../app/src/main/java/com/luma/lifeos/ui/timeline/TimelineRoute.kt#L76); [SearchRoute.kt:49](../app/src/main/java/com/luma/lifeos/ui/search/SearchRoute.kt#L49); [HabitHistory.kt:43](../app/src/main/java/com/luma/lifeos/ui/habits/HabitHistory.kt#L43); [DailySummaryCard.kt:36](../app/src/main/java/com/luma/lifeos/ui/summary/DailySummaryCard.kt#L36)  
**Confidence:** High  
**User Impact:** Medium  
**Implementation Effort:** Low

**Problem:** Confirmed: these typographic headings lack heading semantics, while More, Settings, editor/detail bars and Review sections already provide them.

**Why It Matters:** Screen-reader heading navigation cannot mirror the visual hierarchy consistently, especially in long daily/history views.

**Recommended Solution:** Mark page titles and meaningful sections as headings using a shared heading component. Do not mark every metric, date or card label as a heading; retain useful granularity.

**Expected Improvement:** Efficient nonvisual navigation through the same structure sighted users can scan.

### UX-023 — Expanded disclosures lose their subject and state

**ID:** UX-023  
**Title:** Preserve disclosure subject and expanded state  
**Priority:** P2 — Medium  
**Category:** Accessibility / Component consistency  
**Affected Screen:** Usage/Health explanations and Daily summary details  
**Source:** [DetailsDisclosure.kt:22](../app/src/main/java/com/luma/lifeos/ui/common/DetailsDisclosure.kt#L22), DetailsDisclosure; [UsageSettingsRoute.kt:106](../app/src/main/java/com/luma/lifeos/ui/usage/UsageSettingsRoute.kt#L106); [HealthSettingsCard.kt:109](../app/src/main/java/com/luma/lifeos/ui/health/HealthSettingsCard.kt#L109); [DailySummaryCard.kt:77](../app/src/main/java/com/luma/lifeos/ui/summary/DailySummaryCard.kt#L77)  
**Confidence:** High  
**User Impact:** Medium  
**Implementation Effort:** Low

**Problem:** Confirmed: default keepTitle=false turns an expanded control into generic “Hide details” and omits stateDescription. The same helper already supports named subject/state when keepTitle=true, but callers differ.

**Why It Matters:** Multiple identical Hide details controls are ambiguous in assistive navigation and after scrolling.

**Recommended Solution:** Make named, stateful disclosure the shared default. Use “Hide: Import details” or equivalent concise localized copy, preserve the subject both ways, and expose expanded/collapsed state consistently.

**Expected Improvement:** Users know what will expand or collapse at every control.

### UX-024 — Leaving during backup has no explicit operation outcome

**ID:** UX-024  
**Title:** Define navigation and cancellation during backup work  
**Priority:** P2 — Medium  
**Category:** Lifecycle / Backup feedback  
**Affected Screen:** Backup and restore  
**Source:** [SettingsRoute.kt:71](../app/src/main/java/com/luma/lifeos/ui/settings/SettingsRoute.kt#L71), SettingsSectionPage; [BackupViewModel.kt:24](../app/src/main/java/com/luma/lifeos/ui/backup/BackupViewModel.kt#L24), export; protected restore at line 65; [RoomBackupService.kt:36](../core/data/src/main/kotlin/com/luma/lifeos/data/backup/RoomBackupService.kt#L36)  
**Confidence:** Medium  
**User Impact:** Medium  
**Implementation Effort:** Medium

**Problem:** Confirmed lifecycle gap: page/system Back remains active while export runs in the route ViewModel scope. Popping the route can cancel work after the output document is opened, without a retained completion/cancellation result. An incomplete destination file is a timing-dependent risk. Confirmed restore uses a different protected NonCancellable commit boundary.

**Why It Matters:** Users may leave believing a backup is being produced, then cannot determine whether it completed.

**Recommended Solution:** Expose operation state to page navigation. Either explicitly confirm cancellable export departure and report/clean up abandoned output where supported, or retain operation ownership/results outside the route. Preserve restore's commit protection and explain its navigation behavior.

**Expected Improvement:** Leaving the page has a clear, trustworthy backup outcome.

### UX-025 — Restore confirmation has an unbounded warning body

**ID:** UX-025  
**Title:** Keep restore warnings and actions reachable with large text  
**Priority:** P2 — Medium  
**Category:** Accessibility / Responsive dialogs  
**Affected Screen:** Restore replacement preview  
**Source:** [BackupSettingsCard.kt:76](../app/src/main/java/com/luma/lifeos/ui/backup/BackupSettingsCard.kt#L76), BackupCardContent restore AlertDialog  
**Confidence:** Medium  
**User Impact:** Medium  
**Implementation Effort:** Low

**Problem:** Runtime risk: the dialog body is an ordinary non-scrollable Column containing counts/date, the replacement warning and sometimes the zero-task warning. It has no explicit available-height/scroll treatment. Large text, long translations or a short landscape viewport can challenge the warning's reachability. Clipping has not been observed.

**Why It Matters:** Users need the complete replacement consequences before confirming a destructive restore.

**Recommended Solution:** Use a constrained scrollable body with separately reachable confirm/cancel actions. Preserve all warning content and verify narrow/landscape, 200% text and Persian/French cases before declaring the dialog accessible.

**Expected Improvement:** Informed confirmation remains possible across supported configurations.

### UX-026 — Weekly activity can render a nonzero value as zero

**ID:** UX-026  
**Title:** Preserve meaningful activity precision in Review  
**Priority:** P2 — Medium  
**Category:** Data presentation / Consistency  
**Affected Screen:** Weekly Review activity overview and comparisons  
**Source:** [ReviewValues.kt:18](../app/src/main/java/com/luma/lifeos/ui/review/ReviewValues.kt#L18), reviewNumber/reviewChange; [ReviewSignals.kt:119](../app/src/main/java/com/luma/lifeos/ui/review/ReviewSignals.kt#L119); [DailySummaryCard.kt:124](../app/src/main/java/com/luma/lifeos/ui/summary/DailySummaryCard.kt#L124); [Timeline.kt:9](../core/domain/src/main/kotlin/com/luma/lifeos/domain/model/Timeline.kt#L9), ActivityDetails  
**Confidence:** High  
**User Impact:** Low  
**Implementation Effort:** Low

**Problem:** Confirmed value-dependent issue: reviewNumber limits all numeric values to two fraction digits, although activity accepts finite nonnegative fractions. A valid 0.001 measurement renders zero. Daily Summary already preserves small nonzero activity. Current imported steps are integers, so production reach is limited to other/restored supported activity.

**Why It Matters:** A positive recorded observation can become indistinguishable from explicit zero.

**Recommended Solution:** Share a precision-preserving activity formatter with Daily Summary or use a localized less-than threshold. Keep rating-average precision separate from activity quantities and deltas.

**Expected Improvement:** Review accurately distinguishes small observations from zero.

### UX-027 — Generic Search does not explain excluded content

**ID:** UX-027  
**Title:** Make entry-search scope explicit  
**Priority:** P2 — Medium  
**Category:** Discoverability / UX writing  
**Affected Screen:** Timeline → Search  
**Source:** [SearchRoute.kt:49](../app/src/main/java/com/luma/lifeos/ui/search/SearchRoute.kt#L49), SearchRoute; [strings.xml:476](../app/src/main/res/values/strings.xml#L476); [SearchSql.kt:20](../core/data/src/main/kotlin/com/luma/lifeos/data/search/SearchSql.kt#L20)  
**Confidence:** High  
**User Impact:** Medium  
**Implementation Effort:** Low

**Problem:** Confirmed scope/copy gap: title/field prompt names Search and title/tag/person/place matches, but does not state that journal bodies, notes, habit logs and tasks are excluded. The SQL implements the narrower scope intentionally.

**Why It Matters:** A user recalling a phrase from a journal body can interpret no results as missing personal data.

**Recommended Solution:** Rename the page “Search entries” and add concise accessible scope help: “Matches entry titles, tags, people and places. Journal text, notes, habit logs and tasks aren't searched.” Do not expand indexing as a prerequisite to this copy fix.

**Expected Improvement:** Correct expectations and fewer fruitless searches.

### UX-028 — Attached images cannot be inspected beyond their previews

**ID:** UX-028  
**Title:** Consider a full-image review destination  
**Priority:** P3 — Low  
**Category:** Optional enhancement / Media review  
**Affected Screen:** Journal/Memory photo attachments and details  
**Source:** [PhotoGallery.kt:64](../app/src/main/java/com/luma/lifeos/ui/photos/PhotoGallery.kt#L64), PhotoGallery/PhotoPreview, fixed preview at line 102; [AndroidPhotoCodec.kt:20](../core/data/src/main/kotlin/com/luma/lifeos/data/media/AndroidPhotoCodec.kt#L20); [PhotoRepository.kt](../core/domain/src/main/kotlin/com/luma/lifeos/domain/repository/PhotoRepository.kt)  
**Confidence:** High  
**User Impact:** Medium  
**Implementation Effort:** High

**Problem:** Optional capability gap: images are non-interactive 240 dp previews, decoded at a preview cap of 640 pixels. Original images are retained, but there is no tap-to-open/zoom/full-image retrieval path. This is not an image-loading defect.

**Why It Matters:** Small text or meaningful photo details cannot be inspected adequately through the preview alone.

**Recommended Solution:** If detailed photo recall is a product priority, add an accessible full-screen viewer with bounded original-image decoding, zoom/pan, position and Back/dismiss. Preserve private storage and missing-image recovery.

**Expected Improvement:** Attachments become fully reviewable evidence while remaining local.

## 4. Design Consistency Recommendations

These are proposed standards grounded in existing variation, not additional unverified defects or a request for a visual redesign.

| Area | Proposed application-wide standard | Existing basis and application |
|---|---|---|
| Typography | One compact primary-page title role, titleLarge for major sections, titleMedium for cards, bodyLarge/bodyMedium for content, bodySmall for secondary context | Today/Timeline/More currently use different title scales; reuse Material type roles rather than introducing per-screen font sizes |
| Spacing | Document a small scale: 4/8/12/16/24/32 dp; select one compact-phone page gutter, normally 20 dp, and consistent 12–16 dp card padding | Today uses 20 dp gutters, More/Settings 24 dp, Tasks 16 dp; 14/5/6 dp history spacing should become intentional tokens rather than incidental values |
| Colors | Keep Theme.kt semantic palettes; use onSurfaceVariant for metadata, error roles for actual failures, primary/container roles for selection | Palette calculations show no basis for a brand-color replacement; widget XML has separate semantic needs and must remain theme-aware |
| Component styles | Share PageHeader, AppSectionHeading, ContextDisclosure, field feedback, operation feedback and searchable selector patterns | Extend existing DetailTopBar, AccessibleFeedback and SearchOptionPicker patterns instead of adding a competing component family |
| Navigation | Pinned secondary Back/title/actions; editor Close with dirty/busy protection; consistent delete-result handling; source links identify exact records | Resolve UX-010/013/021; preserve five primary tabs, More hub and saved state |
| Feedback | Named progress; actual operation/target/date in errors; polite success; persistent actionable failures; bounded retained-action regions | Preserve task/habit identities and Undo; fix UX-001/007/008/015/017/018 |
| Forms | Field-specific validation with retained accepted text, meaningful required/optional labels, reachable Save, clean no-op dismissal | Existing Journal/correction/task patterns are stronger references than Mood lifecycle handling |
| Disclosures | Subject retained in both states, expanded/collapsed semantics, scroll-safe content | Make DetailsDisclosure's named behavior the default (UX-023) |
| Lists | Lazy records with stable keys; bounded pages; restore scroll by identity; explicit empty/error/no-match states | Timeline and Tasks already use bounded pages; context chips/history should adopt equivalent scaling |
| Locale/RTL | One locale/hour-cycle/number policy; all changed copy in EN/FR/FA; directional icon mirroring; inspect mixed package/zone identifiers | Existing resources and date helpers provide a foundation; do not change intended Gregorian storage/calendar semantics |

Avoid shrinking text to force visual density. Let actions wrap or stack and maintain distinct touch targets. The fixed four-control DayNavigation row and five equal-width Mood choices deserve targeted narrow-window/large-text validation; this audit did not establish clipping or overlapping hit regions. Decorative null content descriptions are correct when adjacent content already names the object; UX-003 concerns an interactive control without a name.

Define compact and wide content-width policies before introducing tablet-specific redesigns. Current fill-width single-column pages should first receive a considered maximum reading width and configuration review, not speculative multi-pane complexity.

## 5. Quick Wins

These are low-effort changes with direct user benefit; each remains a recommendation only.

| Work | IDs | Concrete result |
|---|---|---|
| Correct unknown widget status and label the privacy switch | UX-002, UX-003 | “Mode unavailable” on read failure; one named privacy toggle |
| Make task conflicts openable | UX-007 | Users can follow the existing Review current state instruction |
| Route historical Check in through existing dated capture | UX-009 | Summary action respects explicit date intent |
| Correct review pagination labels | UX-016 | Older/newer direction agrees with oldest-first source order |
| Clear stale integration errors after relevant successful retry | UX-017 | Recovered status no longer contradicts old failure feedback |
| Apply existing feedback/heading/disclosure semantics | UX-018, UX-022, UX-023 | Consistent announcements, heading navigation and named expanded controls |
| Supply card action labels from actual destinations | UX-020 | Open and Edit are announced truthfully |
| Explain Search scope | UX-027 | “Search entries” and clear exclusions without repository changes |
| Use operation-specific habit errors | UX-015 | Failed history load is no longer called a correction failure |

## 6. Strategic Improvements

1. **Unify operation recovery contracts.** Give retained operations explicit kind, target identity, original time context, progress, retry eligibility and reconciled outcome. Start with habits and Mood (UX-001/004–006), then share presentation patterns with task/backup feedback. Preserve idempotency and existing task revision protections.
2. **Complete contextual navigation results.** Define Created/Saved/Deleted/Unmarked results; return IDs to callers and remove only matching deleted destinations (UX-010/011). This reduces special-case route-string logic and supports future features without changing the product's navigation hierarchy.
3. **Make accumulated history practical.** Add searchable catalog selection, lazy history rows and ID-anchored habit drill-through (UX-012–014). Preserve stable identities and page bounds. Measure large-library behavior during later implementation validation instead of claiming source inspection proves responsiveness.
4. **Adopt one page and accessibility contract.** Standardize insets, headers, heading semantics, named disclosures and feedback (UX-018/020–023). Pilot a small representative set before migrating all screens. A polished theme cannot compensate for inconsistent recovery/navigation.
5. **Define long-running local operation behavior.** Agree on ownership and cancellation for export/prepare/restore and how results remain available across navigation (UX-024). Keep replacement consent and the protected restore boundary.
6. **Evaluate optional media depth separately.** UX-028 should depend on evidence that users need detailed photo inspection. It should not delay the privacy, recovery or accessibility fixes.

No evidence justifies adding accounts, mandatory AI, cloud synchronization, gamified streak pressure or another primary destination as an audit remedy.

## 7. Prioritized Action Plan

Sorted by priority, then expected impact and effort; ties are grouped by closely related work. Medium-confidence rows are source-grounded runtime risks that need targeted reproduction during implementation.

| ID | Issue | Priority | Impact | Effort | Confidence | Affected Screen |
|---|---|---|---|---|---|---|
| UX-002 | Unknown widget mode reported as Summary | P1 | High | Low | High | Widget settings |
| UX-003 | Privacy switch lacks accessible name | P1 | High | Low | High | Widget configuration |
| UX-001 | Habit Retry conceals original date | P1 | High | Medium | High | Habits / Today / detail |
| UX-004 | Unbounded Mood note in saved state | P1 | High | Medium | Medium | Mood editor |
| UX-006 | Restored Mood text hidden on load failure | P2 | Medium | Low | High | Mood editor |
| UX-007 | Visible task conflict cannot be opened | P2 | Medium | Low | High | Today tasks |
| UX-008 | Recovery cards crowd out task viewport | P2 | Medium | Low | Medium | Tasks list |
| UX-009 | Past-day Check in initializes now | P2 | Medium | Low | High | Daily summary |
| UX-015 | Habit failures use correction-only copy | P2 | Medium | Low | High | Habit detail |
| UX-016 | Source paging direction labels reversed | P2 | Medium | Low | High | Review sources |
| UX-017 | Refresh retains obsolete integration error | P2 | Medium | Low | High | Usage / Health settings |
| UX-018 | Important results lack live feedback semantics | P2 | Medium | Low | High | Backup / Language / feeds |
| UX-020 | Card action says Edit but opens details | P2 | Medium | Low | High | Timeline / Search / sources |
| UX-022 | Visible headings lack semantic roles | P2 | Medium | Low | High | Today / Timeline / Search / history |
| UX-023 | Disclosures lose subject and state | P2 | Medium | Low | High | Integration / summary details |
| UX-025 | Restore warning body lacks scroll treatment | P2 | Medium | Low | Medium | Restore preview |
| UX-027 | Search exclusions are not explicit | P2 | Medium | Low | High | Search |
| UX-005 | Mood delete lacks interruption reconciliation | P2 | Medium | Medium | High | Mood editor |
| UX-010 | Deletion returns to missing detail | P2 | Medium | Medium | High | Event detail / editors |
| UX-011 | Created context is not attached to caller | P2 | Medium | Medium | High | Capture editors |
| UX-012 | Context selectors require whole-catalog scan | P2 | Medium | Medium | High | Capture / catalogs |
| UX-013 | Selected historical repetition is not located | P2 | Medium | Medium | High | Habit history |
| UX-014 | Management follows all loaded habit history | P2 | Medium | Medium | High | Habit detail |
| UX-019 | Hour-cycle policy varies between surfaces | P2 | Medium | Medium | High | Capture / saved records |
| UX-021 | Header and inset contracts vary | P2 | Medium | Medium | Medium | App-wide |
| UX-024 | Backup departure has unclear operation outcome | P2 | Medium | Medium | Medium | Backup / restore |
| UX-026 | Small activity rounds to zero | P2 | Low | Low | High | Weekly Review activity |
| UX-028 | No full-image review capability | P3 | Medium | High | High | Photo attachments |

## 8. Suggested Implementation Order

### Phase 1 — Critical usability and accessibility fixes

- **Privacy correctness:** UX-002/003 first. Unknown configuration must never imply hidden details; the opt-in must be named and operable.
- **Action identity and recovery:** UX-001, UX-004–008. Agree on Mood text/oversized-draft policy before changing validation or storage. Retain original retry payloads; adjust presentation rather than replaying against the displayed date.
- **Truthful, actionable feedback:** UX-009, UX-015–018 and UX-020. These can proceed independently of page redesign. Use distinct success/error semantics.
- **Destructive-dialog reachability:** UX-025. Keep all replacement warnings before confirmation.

**Proposed later verification:** fail a habit action on A, navigate to B and retry; inspect widget mode under a configuration read failure; exercise named switch semantics with TalkBack; paste large Mood notes and restore existing longer notes; interrupt a committed Mood delete; recover conflicting tasks; accumulate several off-page task failures; check historical capture initialization. These scenarios were not run for this audit.

### Phase 2 — Design consistency and navigation improvements

- Establish the shared page/inset contract, semantic heading/disclosure components and time policy (UX-019/021–023). Pilot on Today, Timeline and an editor.
- Implement explicit navigation result types, then deleted-detail cleanup and created-context attachment (UX-010/011). These tasks share result plumbing but must preserve memory-unmark behavior.
- Implement searchable selectors and lazy, target-aware habit history (UX-012–014). Agree on target-loading behavior before adding selection highlighting.
- Resolve backup ownership/navigation (UX-024) while preserving protected restore commits.
- Ship the Search-scope copy change independently if not already completed (UX-027).

**Proposed later verification:** source/detail/editor/delete/back journeys; creation from both catalog and capture; historical logs beyond the first 50; large catalogs; tab/scroll restoration; export departure and later status; light/dark, EN/FR/FA, 12/24-hour, narrow windows, landscape/IME and 100%/130%/200% font. Check focus after modal dismissal, deletion and selection.

### Phase 3 — Visual polish and advanced UX enhancements

- Apply documented typography/spacing/component standards across remaining screens after the representative patterns are validated.
- Share activity precision formatting (UX-026) without changing rating-average policy.
- Evaluate the optional full-image viewer (UX-028), including private bounded decoding and accessible zoom/dismissal.
- Review compact fixed-row controls, wide-window reading width, mixed-direction identifiers and launcher resize/scroll states with actual rendering. These are validation targets, not confirmed failures from this static review.

**Dependencies:** operation identity precedes reliable retry copy; Mood legacy-data policy precedes input enforcement; page/inset ownership precedes wholesale header migration; target-aware history loading precedes reliable selected-log arrival; accessible feedback must precede any claim of assistive-technology acceptance.

**Completion boundary for this audit:** the report provides implementation recommendations and proposed verification only. No source fixes, current build/test results, runtime visual acceptance, permission grants, data deletion, restore, or deployment are implied.
