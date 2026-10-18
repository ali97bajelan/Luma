# Luma UI/UX review — 18 October 2026

**Implementation preparation:** [Specs, plans and sequence](UI_UX_IMPLEMENTATION_READY_2026-10-18.md) and [acceptance checklist](UI_UX_ACCEPTANCE_2026-10-18.md) cover every finding except UX-01, UX-02 and UX-12. UX-11 is already implemented and is included for regression/manual acceptance. At the review checkpoint, no code had been changed for these findings; later implementation status is recorded under each finding.

## Design assessment

Luma has a calm, consistent visual identity and sensible privacy safeguards. Its biggest opportunity is to make everyday recording and reviewing feel lighter. Large summaries, repeated explanations and management controls currently receive more space than the content or action people came for.

**Recommended first release of improvements:** simplify Today, clarify how habit logs relate to entry counts, improve habit correction, and turn the weekly habit report into a compact summary. Preserve the existing palette, five main destinations, explicit capture-date choice and discard protections.

At the review checkpoint, no application code had been changed for these findings. Later implementation updates are recorded under the relevant findings. Recommendations below are independent of the implementation status recorded in earlier UX reviews.

## Method, evidence and limits

- Inspected the running `com.luma.lifeos` app on `emulator-5554`: Android 16 / API 36, 1080 × 2400 pixels, 420 dpi, approximately 411 dp wide, Asia/Tehran.
- Installed version: `0.1.0`, version code `1`, last updated 18 October at 13:06. Repository HEAD during review: `4653c9f`. This identifies the source inspected; the installed APK was not independently matched byte-for-byte to that commit.
- Reviewed English flows, Persian RTL on representative screens, dark Today/Timeline, and English text at font scale 1.3. These are spot checks, not certification of every language, screen or text size.
- Saved screenshots and UI hierarchy XML in [ux-review-2026-10-18](ux-review-2026-10-18/). Evidence links open original emulator captures. XML files with matching names support text and control-state inspection; XML alone does not establish TalkBack speech quality.
- Existing data consisted of an accessible `water` habit with one completion; the inspected day and preceding day had no journal, mood or memory entries, and People/Places were empty. Populated entry-reading and linked-history behavior therefore received source review rather than a live end-to-end walkthrough.
- Typed `UX test draft` into a journal and discarded it to check unsaved-work protection. No life record, catalog item, habit definition or repetition was saved, edited or deleted. No import switch was changed, permission granted, backup written/restored or app-data reset executed. Export and photo pickers were opened and canceled; the reset confirmation was opened and canceled.
- Restored English, font scale 1.0 and light mode. Left the app on More. Opening screens can refresh normal derived summaries; this review makes no claim that caches were unchanged.
- A conventional docked software keyboard did not appear in the current hardware-keyboard configuration. Keyboard occlusion, TalkBack speech, provider data, dense lists, failure recovery and persistence after process death remain unverified.

### Coverage

| Area | Live review | Limits / source review |
| --- | --- | --- |
| Today | Current/past day, summary, habit preview, empty state, date navigation, RTL, dark mode, larger text | Populated multi-entry day not available |
| Timeline | Habit completion, day controls, usage-switch presentation, Search entry point, dark mode | Dense paging and imported records not exercised |
| Quick Capture | All three choices, current/past-day sheet, larger-text expansion | Past-day selection inspected; saved-date persistence not exercised |
| Mood | Blank form, score anchors, emotions, expanded energy/stress/note/context | Save/edit/promotion with stored mood not executed |
| Journal | Blank form, temporary text, discard confirmation, tags/context, people selector, photo picker | Stored detail, tag limits and successful save not executed |
| Memory | Blank form, importance and context disclosure | Stored/promoted memory and photo rendering not available |
| Habits | Existing progress, unscheduled day, detail/history, edit form, correction dialog, RTL | Logging, undo, lifecycle changes, deletion and successful correction not executed |
| People & Places | Both empty catalogs and new-item forms | Creation, populated detail, linking and deletion not executed |
| Search | Initial empty results, filters dialog, scope/help text | Populated matches, active filters and pagination reviewed in source |
| Weekly Review | Overview, section jump/sticky navigation, empty sections, populated habit report | Populated mood/activity/usage comparisons not available |
| Settings | Hub, English/Persian language selection, backup, privacy, integrations | French and every translated screen not checked |
| Backup / Privacy | Export picker canceled; reset confirmation canceled | Archive validation, actual export/restore and reset not executed |
| Integrations / Photos | Imports-off presentation, expanded explanation; empty system photo picker canceled | Real access grants, imported totals, attached-image states not exercised |

## Priorities

**P1** = substantial friction or confusing feedback in a core flow. **P2** = meaningful usability or consistency improvement. **P3** = useful polish. No P0 blocker was established in this review. Effort is a relative estimate, not a delivery commitment: S = localized UI/state change; M = several components or states; L = repository/navigation/data-contract work.

| ID | Priority | Improvement | Effort |
| --- | --- | --- | --- |
| UX-01 | P1 | Make Today useful before scrolling | M |
| UX-02 | P1 | Explain entry counts alongside habit completions | S–M |
| UX-03 | P1 | Replace raw habit correction input and unnecessary discard prompts | M |
| UX-04 | P2 | Put habit progress/history ahead of management actions | S–M |
| UX-05 | P1 | Summarize weekly habits and surface the data that actually exists | M |
| UX-06 | P2 | Replace generic “Unavailable” with meaningful states | M |
| UX-07 | P2 | Put capture content before expanded date metadata | M |
| UX-08 | P2 | Recover the space reserved for the global capture row | S–M |
| UX-09 | P2 | Make habit progress compact and understandable | M |
| UX-10 | P2 | De-emphasize habits that are not scheduled for the selected day | S–M |
| UX-11 | P2 | Give Search distinct initial, empty-library and no-match states | S |
| UX-12 | P2 | Make the limits of Search explicit | S for copy; L for expanded search |
| UX-13 | P2 | Standardize fixed Back/title bars | M |
| UX-14 | P2 | Make privacy explanations easier to scan | S–M |
| UX-15 | P2 | Separate integration access from import preferences | M |
| UX-16 | P2 | Finish date/time, numeral and plural localization | M |
| UX-17 | P3 | Improve discovery of Review sections beyond the visible chips | S–M |
| UX-18 | P3 | Show useful backup status without overstating protection | M |
| UX-19 | P2 | Open past-day capture with its choices visible | S–M |

## Findings and clear solutions

### UX-01 — Today should start with what I can do or what I recorded

**Evidence:** [Today](ux-review-2026-10-18/02-today.png), [past empty day](ux-review-2026-10-18/54-yesterday.png), [dark Today](ux-review-2026-10-18/60-dark-today.png). Observed layout; prioritization is a design judgment.

The zero-entry summary occupies roughly 200 dp vertically, followed by a large habit container. On the current day, the empty-entry heading is only partially visible at the bottom of the content area. A new user meets a report before a useful prompt.

**Solution:** use this order: compact date/header → primary capture shortcuts → today's scheduled habits → recent entries → expandable daily summary. On an empty day, replace the full zero summary with “No entries yet” and direct Mood / Journal / Memory actions. Retain a compact secondary summary link. Avoid repeating privacy reassurance in every daily empty state.

**Acceptance:** at the current emulator width and font scale 1.0, show a capture action, one compact scheduled habit and the recent-entry heading without scrolling. At 1.3, preserve reading order and every action; do not shrink text to meet the same density target.

**Implementation starting points:** [TodayScreen.kt](../app/src/main/java/com/luma/lifeos/ui/today/TodayScreen.kt), [DailySummaryCard.kt](../app/src/main/java/com/luma/lifeos/ui/summary/DailySummaryCard.kt), [TodayHabitsCard.kt](../app/src/main/java/com/luma/lifeos/ui/habits/TodayHabitsCard.kt).

**Implementation update — 18 October 2026:** Today now follows the proposed order, with wrapping Mood / Journal / Memory shortcuts, a compact scheduled-habit preview, a recent-entry heading and the daily summary collapsed below entries. Empty days show “No entries yet” beside the shortcuts. Past-day shortcuts explicitly offer current time or the selected date. Summary expansion survives entry updates and retries; habit logging, retry and undo remain available. New copy is translated into Persian and French.

**Implementation validation:** Debug and Android-test APK builds passed, along with 605 JVM tests and 35 targeted emulator tests. The 1080 × 2400 / 420-dpi emulator at font scale 1.0 shows capture, one scheduled habit and the recent-entry heading without scrolling. Tests cover capture/date choices at font scale 1.3 with RTL, separate habit actions at scale 2.0, actual editor navigation/date initialization, persistence, summary updates/retry, and habit logging/undo. Instrumentation ran directly through adb because Gradle's offline test launcher lacked a cached UTP dependency. App lint still reports the two pre-existing `ImpliedQuantity` errors in the Persian and French `habit_unit_default` plurals.

### UX-02 — Counts describe different things without explaining the distinction

**Evidence:** [Today: zero entries and one habit log](ux-review-2026-10-18/02-today.png), [Timeline: zero events and one habit completion](ux-review-2026-10-18/03-timeline.png). Observed wording, supported by the separate entry and habit models in source.

The counts may be internally correct, but “0 entries” and “No events” can read as “nothing was recorded” immediately after a habit completion. Timeline includes that completion while presenting an event count of zero.

**Solution:** keep the domains separate and name them explicitly: “0 entries · 1 habit log.” Use “No mood, journal or memory entries yet” for the entry-only empty state. Keep a habit completion count separate from the number of habits whose daily target was reached. Apply the vocabulary consistently to Today, Timeline and Review.

**Acceptance:** a day with one habit log and no life entries clearly acknowledges that log on all three surfaces. A day with one journal and two habit logs shows one entry and two logs. Summary calculations retain their intended scope.

**Starting points:** TodayScreen, DailySummaryCard, [TimelineRoute.kt](../app/src/main/java/com/luma/lifeos/ui/timeline/TimelineRoute.kt), Review counts and localized strings.

**Implementation update — 18 October 2026:** Today, the expanded daily summary, Timeline and the weekly Review overview now show separate entry and habit-log totals, for example “0 entries · 1 habit log” or “1 entry · 2 habit logs.” The entry-only empty state names mood, journal and memory entries; Timeline rows say “One habit log” instead of “One completion.” Totals count saved logs, including repetitions beyond a daily target, independently of target progress. Today counts the selected date even when the habit preview follows another tracking-zone date. Review shares one live habit observation between its overview and habit section. Existing summary and comparison calculations retain their scope. Count plurals and updated copy are available in English, Persian and French.

**Implementation validation:** Debug and Android-test APK builds passed, along with 605 JVM tests and 37 targeted emulator tests across the affected flows. New database-backed tests cover a habit-only day, one journal with two habit logs beyond a target of one, live Review updates after deletion, zero logs, the expanded daily summary and differing selected/preview dates. Existing Today density, larger-text/RTL, Timeline paging, summary scope/retry, Review navigation and Persian numeral/plural checks passed. Tests ran through adb with a separate validation application ID. App lint still reports the two pre-existing `ImpliedQuantity` errors in Persian and French `habit_unit_default` plurals.

### UX-03 — Habit correction exposes storage formats and asks to discard untouched input

**Evidence:** [correction dialog](ux-review-2026-10-18/50-habit-correction.png). Reproduction: Habits → water → Edit beside the recorded repetition. Opening then dismissing without typing also requests discard confirmation. Source confirms both paths.

The time field is labeled `HH:MM` but displays `13:08:25.276`. The date requires `YYYY-MM-DD`. These are avoidable formatting tasks for a simple correction. `beginCorrection()` uses `LocalTime.toString()`, while the dialog labels expect minute precision. Cancel/Back opens the discard dialog whenever a correction exists, irrespective of changes. Parsing accepts the stored time format; this review did **not** establish that saving it fails.

**Solution:** use the existing date/time-picker interaction pattern, show a localized date and minute-level time, and offer an explicit “No time” choice. Keep the original precise instant until a user changes the time; merely opening and saving must not silently truncate seconds. Track the initial correction draft and prompt to discard only after a real edit. Associate date/time validation with its field.

**Acceptance:** opening and canceling an untouched correction takes one dismissal; editing then leaving asks for confirmation. Invalid dates/times have field-specific feedback. An unchanged round trip preserves the original instant. Verify Persian numerals, 12/24-hour presentation and timezone behavior.

**Starting points:** [HabitsViewModel.kt](../app/src/main/java/com/luma/lifeos/ui/habits/HabitsViewModel.kt) (`beginCorrection`, `saveCorrection`), [HabitDetailRoute.kt](../app/src/main/java/com/luma/lifeos/ui/habits/HabitDetailRoute.kt), [MoodTimeControls.kt](../app/src/main/java/com/luma/lifeos/ui/mood/MoodTimeControls.kt) as an existing picker reference.

**Implementation update — 18 October 2026:** UX-03 was implemented with typed correction drafts, localized date/time pickers, field-specific feedback, clean no-op Save/dismissal, and preservation of precise timestamps. It was committed with UX-04 in `58d4903`. Focused JVM/build checks and 19 isolated API 36 Android tests passed; manual acceptance remains open. See the [validation record](VALIDATION.md#ux-03-and-ux-04-habit-correction-and-detail-hierarchy--2026-10-18).

### UX-04 — Habit detail gives management too much prominence

**Evidence:** [habit detail](ux-review-2026-10-18/17-habit-detail.png), [Persian detail](ux-review-2026-10-18/46-persian-habit-detail.png). Observed layout; proposed hierarchy is a design judgment.

Skip, Pause, Archive and Delete occupy the area between logging and history. They share the same green action treatment, making infrequent lifecycle operations visually compete with the daily task.

**Solution:** order the screen as progress → Log → recent history → schedule details. Put Pause/Archive/Delete in a labeled management menu or disclosure; keep Skip available as a secondary daily action. Give Delete a destructive treatment and keep its confirmation. Explain the effective date beside pending schedule changes and retain a way to cancel them.

**Acceptance:** recent history appears immediately after progress/logging. All management operations remain discoverable through a clearly labeled control. Hiding controls does not remove existing confirmations or pending-change feedback.

**Starting points:** HabitDetailRoute, [HabitHistory.kt](../app/src/main/java/com/luma/lifeos/ui/habits/HabitHistory.kt).

**Implementation update — 18 October 2026:** UX-04 was implemented with history ahead of management, a labeled management disclosure, effective-date/cancel-pending feedback, and preserved action confirmations. It was committed with UX-03 in `58d4903`. The focused automated checks passed; manual acceptance remains open. See the [validation record](VALIDATION.md#ux-03-and-ux-04-habit-correction-and-detail-hierarchy--2026-10-18).

### UX-05 — Weekly Review should summarize habits rather than repeat seven daily reports

**Evidence:** [overview](ux-review-2026-10-18/05-review.png), [habit section](ux-review-2026-10-18/49-review-mood-lower.png). Source: each habit day renders a separate history button.

The overview shows unavailable mood/usage/activity and no memories, while the available habit data is much farther down. The habit report spends multiple screens on dates marked “Not scheduled,” each with “View habit history.” With several habits this will become expensive to scan; that larger dataset was not tested live.

**Solution:** include a compact habit metric in the overview when habits exist. Render each habit as name → repetitions / scheduled-day context → a seven-day status strip → one “View history” action. Put per-day details behind expansion or open them from the selected day. Represent not-scheduled days neutrally; never treat them as missed goals. For zero scheduled comparison days, say “No comparable scheduled days” rather than implying a performance change.

**Acceptance:** one habit fits in a compact card at normal text size. Seven day states remain accessible with spoken date, state and count. Per-day history is still reachable. Current partial-week and previous-period comparison rules stay intact.

**Starting points:** [ReviewHabits.kt](../app/src/main/java/com/luma/lifeos/ui/review/ReviewHabits.kt), [ReviewOverview.kt](../app/src/main/java/com/luma/lifeos/ui/review/ReviewOverview.kt), [WeeklyReviewRoute.kt](../app/src/main/java/com/luma/lifeos/ui/review/WeeklyReviewRoute.kt).

**Implementation update — 18 October 2026:** The overview now shows current repetitions and scheduled habit-days with a View habits jump, using the same live habit-week observation as the entry/log count. Habit cards show seven chronological date states, a single collapsed View history action and saveable per-day details; partial-week future dates are marked Upcoming, not included, previous-only habits report no records for the current period, and zero scheduled denominators do not imply a change. Loading and errors remain distinct from zero. Focused regression cases now cover partial-week slot mapping, off-days, previous-only rows, the habit overview metric, disclosure-based history navigation and section destinations. The UI and tests are in the working tree. The wrapper first failed to create its lock under the read-only global Gradle directory; running the installed Gradle with a writable temporary home then failed before project configuration because the sandbox could not determine a usable wildcard IP. Compilation and test execution are blocked; broader automated and manual acceptance remain open. See the [validation record](VALIDATION.md#ux-05-and-ux-17--weekly-review-summaries-and-discovery--2026-10-18).

### UX-06 — “Unavailable” needs a reason and a useful next step

**Evidence:** Today and Review overview show unavailable usage/measurements; [Integrations](ux-review-2026-10-18/37-integrations.png) shows imports off. Observed presentation. Other states below are proposed requirements, not reproduced defects.

**Solution:** distinguish “No check-ins this week,” “Imports off,” “Access needed,” “No saved data for this period,” and an actual loading/error state. Add a relevant action only where it helps: Check in, Integration settings, Refresh or Retry. Preserve a recorded zero as zero. An off switch alone must not imply access is denied or that historical data is absent.

**Acceptance:** every missing metric explains its state without requiring a trip to Settings. Permission denied, disabled imports, historical data retained, genuine zero and read failure have distinct test fixtures. Enabling integrations remains optional.

**Starting points:** DailySummaryCard, ReviewOverview, [ReviewSignals.kt](../app/src/main/java/com/luma/lifeos/ui/review/ReviewSignals.kt), usage/health UI state mappings. This may require passing integration state to summary surfaces.

**Implementation update — 18 October 2026:** UX-06 now uses explicit missing-metric states across Today and Weekly Review, including saved-data/read failures, imports off, access/provider states, import failure, no check-ins and metric-specific missing energy/stress ratings. Check in and Integration settings actions use existing routes; saved zeros and historical values remain visible, and missing comparison values produce no delta. English, French and Persian copy was added.

**Implementation verification:** Implemented in the working tree; no commit created. A source review caught and the implementation corrected in-flight opt-out, shared configuration-observer recovery on Refresh, a stale duration-display test call, and metric-specific rating absence copy. `:app:compileDebugKotlin --offline` could not reach project configuration because Gradle's file-lock service could not obtain a usable wildcard IP under the sandbox. No test tasks or manual RTL, large-text, TalkBack or device acceptance were run. See [validation status](VALIDATION.md#ux-06-and-ux-15--integration-and-metric-states--2026-10-18); UX-06 acceptance remains open.

### UX-07 — Capture forms should lead with recording

**Evidence:** [mood](ux-review-2026-10-18/07-capture-choice.png), [journal](ux-review-2026-10-18/10-journal.png), [memory](ux-review-2026-10-18/15-memory.png).

A “When” heading, separate date/time rows and timezone text precede the main input. In Journal, the body begins around 400 dp from the screen top. The date is useful but its presentation makes a quick capture feel like administrative entry.

**Solution:** condense the visible date and time into one editable row, for example “Today, 13:21 · Change.” Keep the chosen date prominent, especially for past-day capture. Put timezone explanation inside the date/time controls or an information disclosure, with an inline warning when the zone materially changes interpretation. Put mood selection or journal/memory content immediately below that row. Retain the fixed Save action.

**Acceptance:** the main recording control is within the first viewport in all editors. Past-day capture always names the chosen date before saving. Verify the new layout with a real docked keyboard; it was unavailable in this review.

**Starting points:** MoodTimeControls and the three editor screens. The current date-choice policy should remain unchanged.

### UX-08 — The global capture action reserves a full extra footer row

**Evidence:** [More](ux-review-2026-10-18/01-more.png), Today, Timeline. Source: the capture button's Row is inside the outer Scaffold `bottomBar` above navigation.

Approximately 72 dp is reserved for that row across primary destinations. This reduces useful list space even on Review and More, where capture is less central. It is a layout tradeoff, not content overlapping navigation.

**Solution:** move capture to the Scaffold floating-action-button slot with appropriate list-end padding, or keep an inline action on Today and a compact floating action where needed. On Habits, give “Log” and “Add habit” priority; consider omitting the unrelated global capture action there and on More. Keep all three capture types easy to reach from Today and Timeline.

**Acceptance:** recover the reserved row without covering the last list item. Check gestures, system insets, RTL placement, accessibility focus and 1.3 text. A capture action remains clearly labeled.

**Starting point:** [LifeOsApp.kt](../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt). Android documents dedicated Scaffold slots for app bars and floating actions: [Scaffold guidance](https://developer.android.com/develop/ui/compose/components/scaffold).

### UX-09 — Habit rows need compact progress and clearer language

**Evidence:** [Habits](ux-review-2026-10-18/04-habits.png), [habit editor](ux-review-2026-10-18/18-habit-edit.png).

A single habit uses a large card with its name/progress styled as a text button and a separate full-width Log button. “Unit label” and “1 / 10 times” leave users to infer how counted habits work.

**Solution:** make the name/content area a clear detail target, show “1 of 10” with a secondary unit, and add a compact progress indicator alongside the numeric value. Keep a distinct, adequately sized “+1” action labeled for the habit. In creation, use “What are you counting?” with examples such as glasses or pages and explain “Each tap adds one.” Add “Daily,” “Weekdays,” and “Custom” schedule presets while retaining individual day selection. Show timezone explanation in a disclosure after setup unless it differs from the device zone.

**Acceptance:** several short habits can be scanned in a normal viewport without reducing type size. Progress and logging are separate accessible actions. Long names wrap; numeric progress remains visible and meaningful without color. Only offer unit-count behavior the current domain supports.

**Starting points:** [HabitsScreen.kt](../app/src/main/java/com/luma/lifeos/ui/habits/HabitsScreen.kt), TodayHabitsCard, [HabitEditorRoute.kt](../app/src/main/java/com/luma/lifeos/ui/habits/HabitEditorRoute.kt).

**Implementation update — 18 October 2026:** Habit rows and detail now share exact `count of target` progress text, a clamped indicator and the secondary unit; the count remains exact above target. Habit content and +1 logging use separate named targets, with a stacked layout at narrow widths or larger font scale. The editor explains what is counted and offers Daily, Monday–Friday and Custom schedule presets; timezone details are disclosed after setup, with a mismatch explanation when relevant. English, French and Persian strings were updated.

**Implementation verification:** Committed in `d43d731`. `bash gradlew :app:assembleDebug --offline` and `git diff --check` passed. Focused behavior tests and manual acceptance have not been run; visual density, TalkBack and the full RTL/font-size matrix remain unverified. A read-only code review deferred two minor findings: unusually long progress values can clip because the count is kept on one line, and an empty habit repository does not show the Scheduled heading/zero-scheduled Today action. See the [acceptance checklist](UI_UX_ACCEPTANCE_2026-10-18.md#ux-09--habit-rowssetup); its UX-09 criteria remain open.

### UX-10 — An unscheduled day should not be dominated by disabled habit cards

**Evidence:** [Habits at larger text on the preceding day](ux-review-2026-10-18/59-large-habits.png).

The screen says no habits are scheduled, then shows the habit, “Not scheduled,” another explanatory sentence and a disabled Log button.

**Solution:** separate “Scheduled for this day” from an expandable “Other habits.” When nothing is scheduled, show a short explanation and a return-to-today action. Keep unscheduled habits available for browsing, with the reason shown once; remove the disabled logging action from their compact rows.

**Acceptance:** zero scheduled habits produces a concise state. Users can still inspect unscheduled, paused and archived history. The change does not enable logging where the domain currently forbids it.

**Starting points:** HabitsScreen, habit state labels and row rendering.

**Implementation update — 18 October 2026:** Habits are divided using the scheduled review target: Scheduled rows remain visible, while Other habits appear in a collapsed counted section. When no habits are scheduled, a concise state offers a Today action. Other rows remain browsable without a disabled logging control; a pending or failed log forces the section open so Retry remains reachable. The Today preview uses the same grouping and shows only scheduled habits.

**Implementation verification:** Committed in `d43d731`; the debug assembly and diff check passed. Focused behavior tests and manual acceptance have not been run; accessibility, RTL and large-text/device checks remain open. The read-only review also noted that the Scheduled heading and Today action are omitted when the repository has no habit rows. See the [acceptance checklist](UI_UX_ACCEPTANCE_2026-10-18.md#ux-10--other-habits); its UX-10 criteria remain open.

### UX-11 — Search treats an untouched empty library like a failed query

**Implemented 2026-10-18 in commit `910a596`:** Search distinguishes the initial prompt, an empty searchable library and no matches for active criteria. The counter appears at 180 of 200 characters. English, French and Persian copy is included; named filter chips and reset behavior are preserved. All 11 targeted Search UI tests pass. See [validation results](VALIDATION.md#ux-11--search-initial-and-empty-states--2026-10-18) for checks and remaining manual coverage.

**Evidence:** [initial Search](ux-review-2026-10-18/21-search.png).

With an empty query and no filters, the screen says “No matching entries…or clear filters,” while Clear all and Reset filters are disabled. This suggests a correction the user cannot use.

**Solution:** use three states: initial prompt (“Search entry titles, tags, people or places”), empty searchable library (“Your saved entries will appear here”), and no results for an actual query/filter (“No entries match…” plus an enabled relevant reset). Display the character limit near its limit rather than making `0/200` prominent at rest. Keep the existing named active-filter chips.

**Acceptance:** the initial state never asks users to clear nonexistent filters. Changing the query or filters updates the state appropriately. Reset filters preserves the query; Clear all clears both.

**Starting point:** [SearchRoute.kt](../app/src/main/java/com/luma/lifeos/ui/search/SearchRoute.kt) and localized search strings.

### UX-12 — Search's actual scope is narrower than the likely expectation

**Evidence:** Timeline contains a habit completion, but unfiltered Search contains no matching entries. The current hint mentions titles and linked names. Source confirms lookup excludes journal bodies and habit logs; this is a product-scope gap, not a failed text query established live.

**Solution now:** label the surface “Search entries” and explain “Searches titles, tags, people and places. Journal text and habit logs are not included.” Make the explanation available without displaying a long paragraph permanently.

**Possible later enhancement:** add journal-body search and a typed habit-result group if the desired product promise is universal search. Plan indexed queries, pagination and typed destinations before expanding the scope. Keep all lookup on device.

**Acceptance:** users can identify what can be found before relying on Search. If scope expands, a journal-body-only match and habit-name match become explicit regression cases, with correct destinations and acceptable performance on a large dataset.

**Starting points:** SearchRoute, [SearchSql.kt](../core/data/src/main/kotlin/com/luma/lifeos/data/search/SearchSql.kt), [RoomSearchRepository.kt](../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomSearchRepository.kt), result routing.

### UX-13 — Secondary screens need one consistent Back/title pattern

**Evidence:** [Settings: centered Back](ux-review-2026-10-18/29-settings.png), [Search: leading Back](ux-review-2026-10-18/21-search.png), [habit detail: text Back](ux-review-2026-10-18/17-habit-detail.png), [Privacy after scrolling: no visible Back](ux-review-2026-10-18/35-privacy-lower.png).

**Solution:** use a shared fixed top bar with a leading back arrow and screen title for secondary destinations. Keep editor Close/Cancel separate because it can trigger discard. Move Privacy's Back/title outside its scroll container. Reuse the existing DetailTopBar pattern and handle outer Scaffold insets consistently.

**Acceptance:** Back stays visible during scrolling on every secondary screen. System Back and visible Back have equivalent behavior, including dirty drafts. RTL navigation points and reads correctly; titles can truncate without losing their full accessible name.

**Starting points:** [SettingsRoute.kt](../app/src/main/java/com/luma/lifeos/ui/settings/SettingsRoute.kt), SearchRoute, HabitDetailRoute, [PrivacySettingsRoute.kt](../app/src/main/java/com/luma/lifeos/ui/privacy/PrivacySettingsRoute.kt), [DetailTopBar.kt](../app/src/main/java/com/luma/lifeos/ui/common/DetailTopBar.kt).

### UX-14 — Privacy information needs layers, not less accuracy

**Evidence:** [Privacy](ux-review-2026-10-18/34-privacy.png), [lower section](ux-review-2026-10-18/35-privacy-lower.png), [reset confirmation](ux-review-2026-10-18/36-reset-confirmation.png).

The optional-access paragraph combines usage, health, refresh behavior, revocation and photo access. Key facts are hard to retrieve while scanning.

**Solution:** begin with a short “On this device / No account / Optional connections” summary. Split expandable sections into Records & photos, App usage, Health Connect, and Backups. State what is read, when it is read, what remains after disabling, and how to revoke access in each section. Keep “What deletion removes” and “What stays outside Luma” visible near reset. Preserve the existing red delete action and explicit confirmation.

**Acceptance:** someone can locate revocation and backup-deletion boundaries without reading every paragraph. No existing retention or background-read qualification disappears; full explanations remain accessible.

**Starting point:** PrivacySettingsRoute and localized privacy copy, including the shared Health Connect rationale content.

### UX-15 — Integration setup needs two clearly named states

**Evidence:** [imports off](ux-review-2026-10-18/37-integrations.png), [expanded usage explanation](ux-review-2026-10-18/39-usage-about.png). Granted/denied transitions were not exercised.

The switch, Manage access and Refresh status are presented as similarly prominent controls. Users must work out the relationship between Android permission and Luma's import preference.

**Solution:** show “Android access: Allowed / Not allowed / Unavailable” separately from “Automatic imports: On / Off.” Use one primary next step for the current state: Grant access, Import now, or Manage access. Refresh status can be secondary; recheck when returning from provider settings. Use “App usage” consistently and explain that it measures foreground usage rather than device screen-on time. Show last successful import and the saved data period when known.

**Acceptance:** access allowed + imports off clearly means no new imports while history remains. Denied and provider-unavailable states do not imply zero activity. Import failures offer recovery without clearing saved history. No grant or import occurs merely from opening settings.

**Starting points:** [UsageSettingsRoute.kt](../app/src/main/java/com/luma/lifeos/ui/usage/UsageSettingsRoute.kt), [HealthSettingsCard.kt](../app/src/main/java/com/luma/lifeos/ui/health/HealthSettingsCard.kt), their view models.

**Implementation update — 18 October 2026:** UX-15 now displays Android access separately from the import preference and chooses the primary action from the current state. Access probes run without importing or changing preferences, grant return only refreshes access, and Health steps are described as imports that run while Luma is open. An enabled integration can still be turned off during an active import. Saved history and last successful import remain visible; unknown saved bounds are not invented. English, French and Persian copy was updated.

**Implementation verification:** Implemented in the working tree; no commit created. A source review caught and the implementation corrected in-flight opt-out, shared configuration-observer recovery on Refresh, and a stale duration-display test call. The app compile was blocked before project configuration by restricted sandbox socket access. No test tasks or manual provider-grant/revocation checks were run; UX-15 acceptance remains open. See [validation status](VALIDATION.md#ux-06-and-ux-15--integration-and-metric-states--2026-10-18).

### UX-16 — Formatting localization is incomplete even where RTL layout works

**Evidence:** [Persian habit detail](ux-review-2026-10-18/46-persian-habit-detail.png) mixes Persian day/progress digits with Latin time and recorded-date digits. [English weekly habits](ux-review-2026-10-18/49-review-mood-lower.png) says “1 repetitions · 1 scheduled days.” The main RTL layout and navigation mirroring worked in the checked screens.

**Solution:** centralize locale-aware date/time/numeral formatting and honor the intended 12/24-hour preference. HabitHistory currently uses localized DateTimeFormatter output while other date helpers shape digits differently; apply one app-wide policy. Use quantity resources for repetitions and scheduled days. Preserve user-written names and isolate timezone IDs/mixed-direction fragments for stable reading order. A Persian-calendar preference would be a separate product decision, not a requirement of translation alone.

**Acceptance:** equivalent system-generated values use the same digits and time convention across Today, Timeline, habit history and Review. Test quantities 0, 1 and 2 in English, Persian and French, plus long mixed-script habit names. Keep machine formats out of ordinary input labels.

**Starting points:** HabitHistory, ReviewHabits, [language package](../app/src/main/java/com/luma/lifeos/language/), `res/values*/strings.xml`.

### UX-17 — Review sections beyond the first chips are easy to overlook

**Evidence:** [overview chip row](ux-review-2026-10-18/05-review.png), [scrolled review](ux-review-2026-10-18/49-review-mood-lower.png). The row already scrolls, sticks and indicates the active section; those behaviors should be preserved.

**Solution:** add a “Sections” selector that lists all categories, or a compact section index including Habits. Keep the existing chips as shortcuts if desired. Include the selected week in compact sticky context after the full week header scrolls away.

**Acceptance:** every section can be discovered without guessing that chips extend horizontally. Returning to the overview and identifying the selected week are straightforward. Empty sections remain available with a meaningful explanation.

**Starting points:** WeeklyReviewRoute, ReviewSectionNavigation and ReviewHeader.

**Implementation update — 18 October 2026:** Sticky navigation now has a Sections dialog for Overview, Entry counts, Mood, Memories, People & places, App usage, Activity and optional Habits, plus the existing shortcut chips. One section definition supplies dialog destinations, shortcut chips and list tracking. Sticky context shows the selected Gregorian range and in-progress state, with an Overview jump; changing weeks clears stale targets and scrolls to the new week while same-week restoration remains saveable. Focused regression cases cover the exact destination keys, dialog contents, Counts navigation and Overview return. The UI and tests are in the working tree. The wrapper first failed to create its lock under the read-only global Gradle directory; running the installed Gradle with a writable temporary home then failed before project configuration because the sandbox could not determine a usable wildcard IP. Compilation and test execution are blocked; broader automated and manual acceptance remain open. See the [validation record](VALIDATION.md#ux-05-and-ux-17--weekly-review-summaries-and-discovery--2026-10-18).

### UX-18 — Backup should communicate the last known local action

**Evidence:** [Backup](ux-review-2026-10-18/32-backup.png), [export picker](ux-review-2026-10-18/41-export-picker.png). Source shows operation-result messages but no durable last-export status.

**Solution:** show “No backup exported from this installation” or “Last successful export: [date/time].” Name the action “Export backup file” and keep the unencrypted-content warning adjacent to it. Offer a backup shortcut near reset. Preserve restore's replacement warning and preview counts. Do not imply that a successfully exported file still exists or is remotely protected.

**Acceptance:** picker cancellation does not set last-successful-export status. Only a successful completed write updates it. Failure leaves the prior success time unchanged. Restoring a backup never silently replaces data; the existing confirmation remains required.

**Starting points:** [BackupSettingsCard.kt](../app/src/main/java/com/luma/lifeos/ui/backup/BackupSettingsCard.kt), BackupViewModel and a small local preference for successful export metadata.

### UX-19 — Past-day capture initially conceals a capture choice

**Evidence:** [past-day sheet at normal text](ux-review-2026-10-18/55-past-capture.png), [at 1.3](ux-review-2026-10-18/56-large-past-capture.png), [after expansion](ux-review-2026-10-18/57-large-capture-scrolled.png).

The sheet initially opens at a partial height. At normal size Memory reaches the system-navigation area; at 1.3 it is below the visible area. Expanding the sheet reveals Memory and Close, so this is a discoverability issue rather than a permanently unreachable action.

**Solution:** open past-day capture fully expanded, or reduce introductory copy and use a compact date-choice group so all three capture options appear together. Preserve explicit “Use current time” versus “Add to [date]” behavior. Keep a visible Close action and appropriate system-bar padding.

**Acceptance:** all three capture choices and dismissal are reachable at 1.0 and 1.3 without discovering an unadvertised expansion gesture. At more extreme text sizes, scrolling is clear and usable. Backdrop and system Back still dismiss safely.

**Starting point:** [QuickCaptureSheet.kt](../app/src/main/java/com/luma/lifeos/ui/QuickCaptureSheet.kt).

## Suggested implementation sequence

1. **Clarify core feedback:** UX-02, UX-03, UX-11 and the plural correction in UX-16. These address specific confusing states with a small surface area.
2. **Improve daily use:** UX-01, UX-07, UX-08, UX-09 and UX-19. Design and validate Today/capture/habit density together so space is not reclaimed twice or actions duplicated.
3. **Improve reflection:** UX-05 and UX-17 are implemented in the working tree; UX-10 is committed. Use populated mixed-data and empty-data fixtures for acceptance. UX-06 is implemented in the working tree and awaits verification.
4. **Unify supporting flows:** UX-04, UX-12, UX-13, UX-14, remaining UX-16 formatting, and UX-18. UX-15 is implemented in the working tree and awaits verification.

This sequence remains a suggested ordering for pending findings. Expanded search should be scoped independently if selected; the immediate scope-copy improvement does not require it.

## Design and validation guardrails

Keep the warm surface/green palette and established Material controls. Reduce nested containers, repeated rows and spacing before reducing type size. Use approximately 20 dp screen padding, 12–16 dp card padding and 8–12 dp related-content gaps as starting tokens; these are design proposals, not measured defects. Reserve prominent filled actions for the next useful action and destructive color for destructive operations.

Retain interactive touch targets of at least 48 × 48 dp while compacting visible content. Android's guidance distinguishes visual bounds from expanded touch bounds, so XML text bounds alone must not be used to declare a target too small. Use component semantics, meaningful labels and state descriptions; verify actual focus and speech. See [Android accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) and [Android accessibility guidance](https://developer.android.com/guide/topics/ui/accessibility/apps).

For each changed flow, verify the relevant cases:

- **Fixtures:** empty install; entries only; habits only; mixed entries/habits; ten habits with long names; genuine zero measurements; absent provider data; imports disabled with retained history.
- **Capture:** all three types, selected past date, cancel/dirty discard, saved-date return, real docked keyboard and photo selection/cancellation. Use an isolated test install for record creation and persistence tests.
- **Habits:** log, undo, correction, skip, scheduled/off day, future-effective edits, pause/archive, deletion confirmation and existing-history retention. These mutations were not executed in the review install.
- **Review/Search:** populated and empty periods, partial week, zero comparison denominator, source destinations, filter removal/reset and large-fixture performance if query scope changes.
- **Accessibility/layout:** 360 dp width and the current emulator width; 1.0, 1.3 and a large supported font setting; English/French/Persian; light/dark; TalkBack reading order, distinct open/log actions, selected state and error announcements. Screenshots reviewed here do not establish contrast compliance or TalkBack acceptance.
- **Privacy/integrations:** permission denied/unavailable/granted, import off with saved history, failed reads, picker cancellation, export success/failure and restore preview. Reset/restore acceptance belongs on disposable test data.

## What should be preserved

The five-destination navigation has clear labels and visible selection. People and Places share a useful selector and helpful empty-state creation actions. Mood has visible low/high anchors before selection. Editors expose optional context through named disclosures and keep Save visible. Journal dirty-exit protection worked with temporary text. Import controls are named, optional access stays optional, backup warnings are explicit, and destructive reset requires confirmation. Persian navigation mirrored correctly in the checked screens, and the checked dark screens retained the visual language.

These strengths provide a solid base. The proposed work focuses on priority, explanation and the number of steps needed for frequent tasks.
