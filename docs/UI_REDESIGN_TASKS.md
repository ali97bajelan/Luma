# App-wide UI redesign tasks

Regenerated 22 October 2026 from the images and HTML in [luma-redesign](luma-redesign/README.md), with current app behavior checked against the source. This replaces the previous overview and removes its blanket approval statement. Fresh source audit, all 271 unit tests, debug assembly and lint pass. The clean API 36 connected run completed 224 cases with 15 failures, 0 errors and 1 skipped; a focused 36-test Timeline/Review/navigation run passes. T30, T31, T32-C and T32-D remain blocked by connected failures; T32-A and T32-B implementation and focused checks pass, with rendered acceptance pending. T32 coordination and T33 remain blocked by child failures and the incomplete visual/accessibility matrix. The EventCard density and provider-absent Review gaps are fixed; stale timeline disclosure and clock-format test assumptions are corrected. See the [validation recheck](luma-redesign/evidence/2026-10-22/t33-revalidation.md). Optional features remain deferred.

Widget-reference addendum, 22 October 2026: the new Tasks/Habits light/dark PNG and HTML pairs are reviewed in [T34's spec](superpowers/specs/2026-10-22-t34-launcher-widgets-redesign-design.md). T34 is **pending**, with no widget implementation or Android validation performed in this documentation pass. Existing task statuses above are unchanged.

## Scope and reference decisions

- Required tasks update both themes while retaining product behavior, route IDs, dates, drafts, counts, permissions and privacy guarantees.
- Every task below has a specification and implementation plan. T32 remains the parent rollout task and is split into T32-A/B/C/D for reviewable implementation.
- New features and behavior changes implied by the mockups are optional, deferred by default and described explicitly below. They do not block the base redesign.
- The corrected `timeline-light.html` is a valid Timeline reference alongside its PNG. Use its date/count ribbon, rail, cards and capture-sheet styling with the shared tokens; preserve existing behavior unless an optional task explicitly changes it. Source assets remain unchanged by the documentation update.
- Today, Timeline and Review have direct references. The corrected Timeline HTML also contains a hidden Quick Capture sheet; use its styling within the existing capture contract. There are no supplied More/Settings/editor screen mockups.
- Tasks and Habits launcher widgets now have direct light/dark references. T34 owns their RemoteViews; T32-C continues to own in-app widget settings/configuration. Widget-specific palette roles are defined in T34 rather than replacing the app-wide T30 palette.
- Keep factual local-data wording; never copy the unsupported “Encrypted on-device” claim, fabricated metrics, sample records or stock photos into production.

The [reference brief](luma-redesign/README.md) defines visual precedence, palette, typography, spacing, source hashes, product differences and common acceptance rules. Read it with each task spec.

## Required backlog

Status vocabulary: **pending**, **in progress**, **implemented (validation pending)**, **blocked**, **complete**. A task is complete only when its acceptance scenarios have recorded passing evidence. A blocked/unavailable check is **NOT RUN**, not a pass.

| ID | Task | Depends on | Status | Specification | Implementation plan |
| --- | --- | --- | --- | --- | --- |
| T30 | Shared visual system and app shell | None | blocked | [Spec](superpowers/specs/2026-10-22-t30-shared-visual-system-design.md) | [Plan](superpowers/plans/2026-10-22-t30-shared-visual-system.md) |
| T31 | Today visual hierarchy | T30 | blocked | [Spec](superpowers/specs/2026-10-22-t31-today-redesign-design.md) | [Plan](superpowers/plans/2026-10-22-t31-today-redesign.md) |
| T32 | Remaining routes rollout coordinator | T30; complete T32-A through T32-D before T33 | blocked | [Spec](superpowers/specs/2026-10-22-t32-remaining-routes-design.md) | [Plan](superpowers/plans/2026-10-22-t32-remaining-routes.md) |
| T32-A | Timeline stream and editorial cards | T30 | implemented (validation pending) | [Spec](superpowers/specs/2026-10-22-t32-a-timeline-redesign-design.md) | [Plan](superpowers/plans/2026-10-22-t32-a-timeline-redesign.md) |
| T32-B | Weekly Review modules and comparisons | T30 | implemented (validation pending) | [Spec](superpowers/specs/2026-10-22-t32-b-review-redesign-design.md) | [Plan](superpowers/plans/2026-10-22-t32-b-review-redesign.md) |
| T32-C | More, catalogs, search, lists and settings | T30 | blocked | [Spec](superpowers/specs/2026-10-22-t32-c-supporting-screens-design.md) | [Plan](superpowers/plans/2026-10-22-t32-c-supporting-screens.md) |
| T32-D | Editors, details, sheets and dialogs | T30; consume T32-A EventCard treatment | blocked | [Spec](superpowers/specs/2026-10-22-t32-d-editors-details-modals-design.md) | [Plan](superpowers/plans/2026-10-22-t32-d-editors-details-modals.md) |
| T33 | Accessibility, regression and visual acceptance | T31 and T32 (all four children) | blocked (acceptance pending) | [Spec](superpowers/specs/2026-10-22-t33-redesign-acceptance-design.md) | [Plan](superpowers/plans/2026-10-22-t33-redesign-acceptance.md) |
| T34 | Tasks and Habits launcher widgets | T30 design baseline; existing widget contracts | pending | [Spec](superpowers/specs/2026-10-22-t34-launcher-widgets-redesign-design.md) | [Plan](superpowers/plans/2026-10-22-t34-launcher-widgets-redesign.md) |

### T30 — Shared visual system and app shell

**Status (22 October 2026): blocked.** The source contrast/font audit, 271 unit tests, debug assembly and lint pass, but `LanguageFlowTest.journalDraftTextTagsAndDateSurviveLanguageChangeThenSaveAndDetail` failed in the connected suite. The failure has not yet been isolated; theme restoration, rendered contrast, Android font shaping and accessibility acceptance also remain open. See the [T30 implementation evidence](luma-redesign/evidence/2026-10-22/t30-review.md) and [validation recheck](luma-redesign/evidence/2026-10-22/t33-revalidation.md).

Replace the existing green-led appearance with a coherent warm-paper/obsidian system and editorial typography across existing components.

**Scope:** Use the role mapping and component measurements in the reference brief. Keep `LifeOsTheme(content: @Composable () -> Unit)` and the stored `AppTheme` values unchanged. Define typography and shapes centrally, not inside routes.

**Acceptance:** Theme switches persist through recreation and System follows the system appearance; all primary destinations still resolve. All ordinary/selected/error/disabled/focus surfaces are distinguishable in both themes; measured role pairs meet the shared contrast thresholds. Font resources render offline; French accents and mixed Persian/Latin names and units are readable without truncating actions. The final row, snackbar and capture FAB remain clear of system bars and each other at all required widths.

### T31 — Today visual hierarchy

**Status (22 October 2026): blocked.** Today implementation source checks, all 271 unit tests, debug assembly and lint pass, but `HabitCaptureFlowTest.todayPreviewShowsScheduledRowsBeforeOlderUnscheduledHabitsAndCapsAtTwo` failed in the connected run. Fixture-specific percentages, historical-date/recovery states and current-build visual acceptance remain open. See the [T31 implementation evidence](luma-redesign/evidence/2026-10-22/t31-review.md) and [validation recheck](luma-redesign/evidence/2026-10-22/t33-revalidation.md).

Make date context, capture, habit progress, tasks and recent saved records scan clearly while preserving the bounded Today previews.

**Scope:** Order: title/date/current-day notice; habit tracking-zone context; existing Mood/Journal/Memory capture choices; scheduled habit preview; task preview; recent entries and saved daily summary. Tracking-zone text must come from habit state, not the device zone or the sample Asia/Tehran.

**Acceptance:** Two fixture habits (2/8 and 1/5) show exact 25% and 20% progress with separate open/log actions; pending, failure and undo preserve stored counts. More than two habits/three eligible tasks retains full totals and reachable View all/recovery actions; no completed or undated task appears in the base Today query. Navigating to a historical date shows explicit context and correct capture choices, hides Today tasks and never displays the previous date’s habit rows beneath a new date. Empty/loading/error/missing-summary/integration-disabled states remain useful and all existing callbacks work in both themes and RTL.

### T32 — Remaining routes rollout coordinator

**Status (22 October 2026): blocked.** The route ledger has been reconciled against current declarations, assigns conditional providers and shared `EventCard` consumers, and records launcher scope. Provider-absent Review routes now show useful, tested fallback states. The full connected suite still has failures in T31, T32-C and T32-D; the required visual/accessibility matrix is also open. See the [T32 coordinator review](luma-redesign/evidence/2026-10-22/t32-coordinator-review.md) and [validation recheck](luma-redesign/evidence/2026-10-22/t33-revalidation.md).

Replace the old catch-all remaining-screen task with four independently reviewable screen-family tasks and a route coverage ledger.

**Scope:** T32 is a coordination task, not permission to redesign unrelated behavior. T32-A owns Timeline, T32-B Review, T32-C supporting browse/settings surfaces and T32-D capture/detail/modal surfaces.

**Acceptance:** Every route and major sheet/dialog has one implementation owner and an evidence row; absent providers are recorded rather than silently omitted. T32-A/B/C/D acceptance is verified individually; no legacy hard-coded screen palette remains except explicitly documented platform/launcher surfaces. Optional feature omissions appear as intentional differences, not failed visual requirements.

### T32-A — Timeline stream and editorial cards

**Status (22 October 2026): implemented (validation pending).** The 36-test focused Timeline/Review/navigation run passes, including the compact and 130%/RTL EventCard viewport checks, Timeline disclosure behavior, date-only logs, paging and source navigation. EventCard reduces internal spacing at font scale 1.3 and above without reducing text size. The date assertion now follows the system locale and 12/24-hour setting. Rendered captures and the required visual/accessibility matrix remain NOT RUN. See the [T32-A implementation evidence](luma-redesign/evidence/2026-10-22/t32-a-review.md) and [validation recheck](luma-redesign/evidence/2026-10-22/t33-revalidation.md).

Present the existing mixed chronological feed with a continuous visual rail, type nodes and readable editorial cards.

**Scope:** Use timeline-light.png + the corrected timeline-light.html for light composition/details, timeline-dark.png + timeline-dark.html for dark treatment, and T30 for shared roles. The light date ribbon groups date controls above entry/habit counts and View options; retain a direct, named Search action.

**Acceptance:** A mixed fixture of timestamped entries, equal-time logs, date-only logs and imported activity retains original order and correct open/correction targets. Paging beyond the loaded window creates no duplicate items, disconnected hit targets or hidden retry controls; last item clears the FAB. An empty/error day retains Search, View options and recovery; toggling usage persists across day changes/recreation. Shared EventCard still opens/edits/promotes the correct record in Today, search and detail/history consumers.

### T32-B — Weekly Review modules and comparisons

**Status (22 October 2026): implemented (validation pending).** The Review period selector, sticky section navigation, overview modules and detailed comparisons use shared roles and preserve saved values/callbacks. Focused Review and navigation tests pass, including explicit unavailable states for source and app-usage detail when providers are missing. Rendered captures and the required locale/window/accessibility matrix remain NOT RUN. See the [T32-B implementation evidence](luma-redesign/evidence/2026-10-22/t32-b-review.md) and [validation recheck](luma-redesign/evidence/2026-10-22/t33-revalidation.md).

Apply the editorial Review layout to existing saved metrics, comparison periods, source links and section navigation.

**Scope:** Use a rounded selected-period container, explicit partial-week badge and comparable previous-period caption. Keep saved/restored week, current comparison calculation and all section anchors.

**Acceptance:** Partial and complete weeks retain their original ranges and comparison calculations, and section selection/restoration still works. No-data, recorded zero, fractional activity and incomplete usage fixtures retain their exact meaning, units and explanations in both appearances. All current/previous source links, View habits, memory open/retry, mood check-in and setup links reach the original routes. Base UI contains no “100% streak”, fabricated active-day badge, invented chart or unsupported pinned-memory model.

### T32-C — More, catalogs, search, lists and settings

**Status (22 October 2026): blocked.** Unit tests and build checks pass, but Search error recovery, Habits navigation/management and task recovery assertions failed in the full connected run. These failures need focused rechecks; settings safeguards, conditional-provider states and current-build visual acceptance also remain open. See the [T32-C implementation evidence](luma-redesign/evidence/2026-10-22/t32-c-review.md), [route coverage ledger](luma-redesign/route-coverage.md) and [validation recheck](luma-redesign/evidence/2026-10-22/t33-revalidation.md).

Carry the new visual language to supporting browse and settings screens without introducing new directory features.

**Scope:** More retains Tasks, People & Places and Settings. Restyle the existing People/Places tabs, independent saved search queries, Create and history/edit routes; search and catalog switching already exist and are required preservation, not a new optional feature.

**Acceptance:** More and all catalog/search/list routes retain working selection, query restoration, Create/Edit/Back and no-results behavior in all three languages. Settings expose the same choices and safeguards; rejected/unavailable integration states and backup/restore progress/results remain visible. Source/usage/memory-review routes preserve pagination, period context and record navigation. No unseen screen is described as a supplied design; route coverage distinguishes shared-style extrapolation and out-of-scope launcher surfaces.

### T32-D — Editors, details, sheets and dialogs

**Status (22 October 2026): blocked.** Source, unit and build checks pass, but several connected capture/editor flows failed, including Journal, Memory, Mood, People and Place scenarios and the Quick Capture lifecycle case. Draft, validation, picker, modal, Back and current-build visual acceptance remain pending focused investigation. See the [T32-D implementation evidence](luma-redesign/evidence/2026-10-22/t32-d-review.md) and [validation recheck](luma-redesign/evidence/2026-10-22/t33-revalidation.md).

Make capture/edit/detail surfaces and modal UI consistent while retaining drafts, validation, editing and recovery.

**Scope:** Use shared editor headers, text-field roles, rounded optional sections and consistent Save/Cancel/Back actions. Adapt the hidden Quick Capture sheet in corrected timeline-light.html while retaining current choices/date rules and omitting its encryption claim; habit entry is optional O08. Keep required fields, limits, validation timing and disabled/pending behavior.

**Acceptance:** Mood/journal/memory/task/habit/person/place create and edit preserve saved values, validation and drafts through recreation and Back/Cancel. Imported/read-only/missing/deleted details retain correct actions and return paths; deleting a detail does not leave a stale screen. Long text, IME, Persian RTL and 2.0 fonts keep Save/Cancel, photo retry, picker results and dialog confirmation reachable. Quick Capture retains date selection across rotation and emits no record on dismissal; successful saves retain single existing feedback.

### T33 — Accessibility, regression and visual acceptance

**Status (22 October 2026): blocked; release acceptance NOT ACCEPTED.** The source audit, 271 unit tests, debug assembly and lint pass. The clean API 36 instrumentation run reports 15 failures and 1 skipped case; all 36 focused Timeline/Review/navigation cases pass, and provider-absent Review routes now have tested fallback states. T30, T31, T32-C and T32-D failures and the current-build locale/theme/window/accessibility matrix remain unresolved. See the [T33 revalidation evidence](luma-redesign/evidence/2026-10-22/t33-revalidation.md), [acceptance ledger](luma-redesign/acceptance-2026-10-22.md) and [route coverage ledger](luma-redesign/route-coverage.md).

Produce auditable acceptance evidence for the required redesign and separately record any opted-in feature results.

**Scope:** Create `docs/luma-redesign/acceptance-2026-10-22.md` and `docs/luma-redesign/evidence/<actual-run-date>/`. Record actual run date/build SHA/device/API, locale, theme, window dp and font scale. The filename is the planning baseline, not a claim tests ran on that date.

**Acceptance:** Evidence includes six primary-screen captures, the route ledger, measured color-pair results and a reproducible environment/fixture description. Relevant existing unit/instrumented regression suites pass; any blocked build/device checks have explicit NOT RUN entries. No unresolved clipping, inaccessible action, false metric/privacy statement, obscured final row or broken state/navigation remains in required scope.

### T34 — Tasks and Habits launcher widgets

**Status (22 October 2026): pending; specification and plan prepared, implementation not started.** Source references: [light image](luma-redesign/widgets-light.png), [light HTML](luma-redesign/widgets-light.html), [dark image](luma-redesign/widgets-dark.png), [dark HTML](luma-redesign/widgets-dark.html).

**Outcome:** Cream/charcoal rounded shells, count badges, inset record cards, sage actions and saved-count habit progress while retaining the current collection, navigation, mutations and recovery behavior.

| Work item | Deliverable | Depends on | Status |
| --- | --- | --- | --- |
| T34-A | Semantic palette, shell, padded count badges and responsive state surfaces | Existing renderer; T30 reference direction | pending |
| T34-B | Inset task cards with due metadata and separate Complete action | T34-A | pending |
| T34-C | Inset habit cards, accurate progress and inline/stacked theme variants | T34-A | pending |
| T34-D | Picker/compact consistency, regression matrix and actual launcher acceptance | T34-A/B/C | pending |

**Preserve:** Offline operation, System/Light/Dark, en/fr/fa, widget IDs, saved date/zone, paging/stable record IDs, Capture/Refresh/list targets, Complete/+1 eligibility, Undo/Retry, setup/loading/empty/error states, resizing and legacy Quick Capture placements. No launcher chrome, sample data, inferred habit emoji, schema change, Glance migration or new dependency.

**Acceptance:** All W34-1 through W34-5 in the [spec](superpowers/specs/2026-10-22-t34-launcher-widgets-redesign-design.md) require current-build evidence. Verify both themes, narrow/expanded/flat bounds, large text, RTL, progress edge cases, record overflow, theme overrides, actual collection scrolling and mutation delivery on API 28/31/36. Missing checks are NOT RUN. T34 does not close WG08 or T30–T33 blockers; overall redesign acceptance requires T33 and T34.

## Optional features and behavior changes

All optional tasks are **deferred / not selected**. Each has a reviewable spec and plan, but documenting it does not authorize implementation. Choose only the additions wanted; the base UI uses existing behavior when an option is omitted.

| ID | Optional change | Why it is optional | Depends on | Specification | Implementation plan |
| --- | --- | --- | --- | --- | --- |
| O01 | four-tab navigation | Removes the existing primary Habits destination and changes back-stack/selection behavior. | T30 and T32-C | [Spec](superpowers/specs/2026-10-22-o01-optional-four-tab-navigation-design.md) | [Plan](superpowers/plans/2026-10-22-o01-optional-four-tab-navigation.md) |
| O02 | Timeline event-type filters | Adds a new query/count/paging state, not merely chip styling. | T32-A | [Spec](superpowers/specs/2026-10-22-o02-optional-timeline-filters-design.md) | [Plan](superpowers/plans/2026-10-22-o02-optional-timeline-filters.md) |
| O03 | expanded Today task panel | Changes Today eligibility, completion browsing, counts and paging. | T31 | [Spec](superpowers/specs/2026-10-22-o03-optional-today-task-panel-design.md) | [Plan](superpowers/plans/2026-10-22-o03-optional-today-task-panel.md) |
| O04 | Timeline photo previews | Adds image decoding, loading and privacy/performance behavior to the feed. | T32-A | [Spec](superpowers/specs/2026-10-22-o04-optional-feed-photo-previews-design.md) | [Plan](superpowers/plans/2026-10-22-o04-optional-feed-photo-previews.md) |
| O05 | saved-data Review charts | Requires real daily projections, especially for currently aggregate-only activity. | T32-B | [Spec](superpowers/specs/2026-10-22-o05-optional-review-charts-design.md) | [Plan](superpowers/plans/2026-10-22-o05-optional-review-charts.md) |
| O06 | defined cadence and active-day metrics | Adds new calculations and definitions; source “streak” wording cannot be copied as-is. | T32-B | [Spec](superpowers/specs/2026-10-22-o06-optional-review-cadence-design.md) | [Plan](superpowers/plans/2026-10-22-o06-optional-review-cadence.md) |
| O07 | reflection shortcut and editorial quotes | Adds an alternate capture entry point and new editorial content. | T31 and T32-A | [Spec](superpowers/specs/2026-10-22-o07-optional-reflection-quotes-design.md) | [Plan](superpowers/plans/2026-10-22-o07-optional-reflection-quotes.md) |
| O08 | habit shortcut in Quick Capture | Adds a date-aware habit entry point absent from the current capture sheet. | T32-D and T32-A | [Spec](superpowers/specs/2026-10-22-o08-optional-capture-habit-shortcut-design.md) | [Plan](superpowers/plans/2026-10-22-o08-optional-capture-habit-shortcut.md) |

### O01 — four-tab navigation

**Proposed change:** Match the four reference tabs while keeping Habits reachable as a secondary destination.

Change primary navigation to Today, Timeline, Review, More; add a clearly labeled Habits row to More and preserve Today/Review View habits entry points. Retain the `habits` route and old saved-back-stack resolution; do not delete its screen, widget/deep-link targets or selected-date contract. Return from Habits preserves the originating destination and selected date. A restored Habits destination must show a coherent navigation state with More selected and a usable Back path. This is a navigation behavior change, excluded from required visual acceptance. No account/profile destination is added.

**Acceptance if selected:** Exactly four primary actions remain and Habits can be reached from More, Today, Review and existing widget links. Restoring an old Habits stack and switching tabs retains dates/state and a valid Back path.

### O02 — Timeline event-type filters

**Proposed change:** Add functional All events, Reflections, Habits and Health filters over the full paged feed.

Introduce a single-selection `TimelineFilter` enum: ALL, REFLECTIONS, HABITS, HEALTH. Reflections includes manual mood/journal/memory entries; HABITS standalone habit logs; HEALTH imported Health Connect activity only. Existing app usage remains available only under ALL when its persisted switch is on. Apply the filter at repository query/count/window level before paging, not to currently loaded rows. Extend TimelineRepository.feed and implementations using the same enum contract; default ALL keeps every existing caller compatible. Store selected filter in SavedStateHandle, retain across day navigation/recreation and reset paging on change. Counts describe the filtered query; keep all-events counts separate if shown, labeled explicitly. Type-specific empty/retry states retain visible filter controls and an All events escape. No sample sync event or hydration-specific type is introduced.

**Acceptance if selected:** A matching record outside the initial 150-row window is found by the appropriate filter; total count and page controls are correct. Rapid day/filter changes never show stale results; app-usage preference remains independent and restored correctly.

### O03 — expanded Today task panel

**Proposed change:** Add the full task-status switch and undated group pictured on Today without silently changing the existing due-task preview.

Replace the bounded preview only when this option is selected. Open shows overdue, due-today and undated open tasks; future-dated open tasks remain in View all. Completed shows completed tasks through existing repository list semantics, labeled independently of the due summary. Keep the headline count limited to open overdue/due-today items. Open tab count includes undated items; never equate these two counts. Use bounded repository-backed prefixes, initially 50 and increasing by 50 up to 300 with explicit Load more, not an unbounded collection. Introduce TodayTaskPanelFilter OPEN/COMPLETED and a saved selected filter; reset the prefix limit on date/status changes. Preserve historical-day hiding and all pending/conflict/missing/retry/reopen feedback, including when a completed item disappears from the active list. Continue to expose View all. Extend repository queries only if current list contracts cannot express this panel; no new persistence fields or migration are needed.

**Acceptance if selected:** Three due/overdue plus one undated open task yields headline 3 and Open (4); future-dated tasks do not enter this panel. More than 50 eligible tasks pages correctly; completing/reopening/filter switching/recreation keeps totals and recoverable feedback coherent.

### O04 — Timeline photo previews

**Proposed change:** Show the first saved photo attachment inside eligible Timeline editorial cards using local preview data.

Add an optional trailing composable slot `photoPreview: (@Composable () -> Unit)? = null` to EventCard so existing consumers remain source-compatible. Timeline supplies a shared local preview component only for records with photos; Today/search remain metadata-only by default. Create `ui/photos/FeedPhotoPreview.kt` with `FeedPhotoPreview(repository: PhotoRepository, photo: PhotoAttachment, modifier: Modifier = Modifier)`. Render one 16:9 cropped image with a 160 dp maximum height; clicking the card keeps its existing detail action. Use PhotoRepository.preview on a background dispatcher with bounded decoded dimensions, lifecycle cancellation and attachment-ID keyed state. Loading/missing/corrupt previews fall back to photo-count metadata; never retain an image from another record. No remote stock images, new storage permission, full-screen viewer, encryption badge or automatic import. Add localized attachment semantics and avoid a redundant focus target.

**Acceptance if selected:** Saved preview appears offline and opens the same source record; no-photo/loading/corrupt/deleted attachments retain a usable card. Paging/rebinding rapidly between records never swaps thumbnails or decodes full-resolution images on the main thread.

### O05 — saved-data Review charts

**Proposed change:** Add small accessible charts backed by saved daily usage/activity data, with gaps instead of invented observations.

Create a pure `ReviewChartPoint(date: LocalDate, value: Double?)` projection and a `ReviewSignalChart(points: List<ReviewChartPoint>, unitLabel: String, modifier: Modifier = Modifier)` component. Use one point per selected-period date, ascending by date. Null is unknown, not zero. For usage use existing saved daily report totals only when known; preserve COMPLETE/PARTIAL/UNAVAILABLE state in adjacent text. For activity, extend WeeklyReview with saved per-day totals grouped by type/unit/source; the current aggregate activity model cannot support a genuine sparkline. No interpolation over gaps, smoothing that implies intermediate samples, extrapolated future dates or mixed-unit/source sums. Display isolated points honestly and an absence label when no values exist. Keep exact totals, sample counts, source actions and a textual date/value alternative. A chart cannot be the sole way to inspect a metric. No charting/network dependency is required; use Compose Canvas.

**Acceptance if selected:** Missing dates remain gaps; recorded zero sits at baseline; fractional activity and period boundaries retain precision and grouping. Chart/text values reconcile with source data and existing weekly aggregates; TalkBack can inspect date/value alternatives.

### O06 — defined cadence and active-day metrics

**Proposed change:** Replace illustrative coaching claims with explicitly defined optional calculations over saved records and habit schedules.

Call the habit metric “Scheduled habit-days completed”, never “streak”. Numerator: eligible non-skipped scheduled habit-days whose stored repetition count meets target; denominator: all non-skipped scheduled habit-days through the selected period end. Exclude upcoming and unscheduled days; no eligible denominator means unavailable, not 100%. Define active reflection days as distinct dates containing at least one manual mood/journal/memory record in the review zone. A promoted memory shares its event ID and counts once; imported activity/app usage and standalone habit logs do not create reflection days. Create pure domain review projection functions for the two metrics, expose their definitions in methodology and retain existing repetition totals. Aggregate daily cadence uses the same eligible-day rule; skipped/unscheduled/upcoming/unknown remain distinguishable. Do not add all-time streaks, motivational diagnoses or “on track”/“4 days running” claims. This option supplies truthful period metrics only; a future streak feature requires its own product rules.

**Acceptance if selected:** Multiple repetitions of a single scheduled habit-day never inflate the numerator; skipped/future/unscheduled days and denominator zero are handled by the defined rules. Active days de-duplicate entries by date in review zone, exclude imports and remain correct across midnight/timezone boundaries.

### O07 — reflection shortcut and editorial quotes

**Proposed change:** Offer a clearly defined reflection shortcut and static editorial footer without presenting invented personal insight.

Reflect opens the existing Journal editor using the same now/selected-date confirmation and draft behavior as Journal. It creates no record until Save and adds no new event type, AI service or automatic summary. If the fourth chip is included, label it “Reflect in journal” and preserve the existing Journal action so the difference is explicit. Treat this as an optional alternate entry point; omit it if a duplicate shortcut is not wanted. Use at most one bundled, translated, original editorial sentence per locale, identified as “Reflection prompt”. It is static editorial content, not attributed as the user’s own words or inferred from their records. Do not copy the reference quotation without confirmed reuse rights; do not invent source attribution. Footer remains secondary, accessible and clear of capture controls; it must not replace empty/error guidance.

**Acceptance if selected:** Shortcut respects selected-date capture rules and cancellation/draft restoration; it makes no silent write. Quote/prompt works offline in all locales, is explicitly editorial and never claims a saved reflection or calculated cadence insight.

### O08 — habit shortcut in Quick Capture

**Proposed change:** Add an optional “Log a habit” choice, inspired by Water/Stretch in the corrected Timeline HTML’s hidden sheet. Open the existing Habits screen for the chosen date; users select an actual saved habit and explicitly log there. Keep Mood/Journal/Memory/Task and their date policies. Do not hard-code Water/Stretch, add silent writes or claim automatic encryption.

**Acceptance if selected:** Missing callback omits the choice; selected date/Now follows the actual habit tracking zone; opening/dismissing creates no log; empty/error and RTL/large-text capture flows remain usable. The paired spec defines the callback and recovery rules.

## Excluded or unsupported mockup elements

Do not add an account/profile, claim encryption, enable Markdown export, create a pinned-memory subsystem, infer hydration volume/first-glass status or turn a saved daily aggregate into a fabricated midnight activity. Review +Link/+Tag controls require a specific target record and flow; use existing editor linking instead of shipping placeholder actions. People/Places search and catalog switching already exist and belong to required preservation, not an optional feature proposal.

## Execution order and acceptance

1. Review the shared brief and T30; implement shared roles/components first.
2. Implement T31 and T32-A/B/C/D using their paired plans; T32 maintains the route ledger and coordinates shared EventCard consumers.
3. Run T33 against the required base. Record intentional differences for all unselected options.
4. If any O-task is selected, implement its plan after its dependencies and rerun its affected acceptance scenarios plus the T33 checks for changed surfaces.
5. Implement T34-A/B/C and run T34-D for the newly supplied launcher references. Record its acceptance separately from the original T33 app-route gate; both are required for overall redesign release acceptance.

These artifacts do not begin app implementation and do not claim build/device checks passed. Plans use focused regression and visual evidence, avoiding tests that only mirror spacing/color assignments.
