# Luma fresh UI refresh — design specification

**Date:** 21 October 2026
**Status:** Proposed implementation specification; no application changes made.
**Basis:** [Fresh UI/UX review](../../APP_FLOW_UX_REVIEW_2026-10-21-FRESH.md)
**Tasks:** [Task catalog](../../APP_FLOW_UX_REVIEW_2026-10-21-FRESH-TASKS.md)
**Plan:** [Implementation plan](../plans/2026-10-21-fresh-ui-refresh.md)

## Intent and scope

Make Luma feel like a calm daily companion: frequent actions should be easy to scan, personal content should lead, and completion should receive a clear acknowledgement. The selected scope is the fresh review's focused visual refresh. It covers Today, the habit rows used by Today and Habits, Timeline's first view, Weekly Review's overview, More, and Quick Capture.

The review and the request establish the scope. Exact spacing, component boundaries, the Timeline disclosure, and the save-feedback policy below are proposed implementation decisions. This package does not select a new navigation architecture, new product features, account/cloud services, a widget redesign, or an editor-form redesign.

## Global constraints

- Keep the five primary destinations in this order: Today, Timeline, Habits, Review, More; preserve existing route IDs and back behavior.
- Keep the current dependency versions and SDK configuration; add no dependencies, repository interfaces, database/schema changes, permissions, or network services.
- Preserve null-to-follow-today versus explicit selected dates, habit tracking-zone behavior, and current-day-only task previews.
- Today shows at most two scheduled habit rows and at most three task rows; retained recovery controls may add content.
- Support English, French, and Persian using string/plural resources, existing localized formatters, and start/end layout directions.
- Every interactive target is at least 48 × 48 dp; important labels, values, errors, and recovery controls wrap or scroll instead of being clipped.
- Preserve System, Light, and Dark theme choices; use semantic color roles with at least 4.5:1 normal-text contrast and 3:1 meaningful non-text contrast.
- Use 20 dp page gutters, a centered maximum body width of 600 dp including gutters, 16 dp section padding, and spacing from 4, 8, 12, 16, and 24 dp.
- Use the existing system font and Material roles: titleLarge for page titles/major values, titleMedium for sections/row titles, bodyLarge for body text, and bodyMedium for metadata; new styling uses only Normal and Medium weights.
- Keep inset ownership in the existing app/page scaffolds and preserve measured capture-FAB bottom clearance; do not add duplicate system-bar or IME padding.
- Preserve loading, empty, error, disabled, pending, retry, undo, and retained-action behavior; show success only for confirmed outcomes.

The maximum reading width limits the body, not the full-screen bottom navigation, scrim, or system bars. Existing component signatures remain source-compatible unless a change is explicitly listed below. Preserve existing test tags except the two retired overview integration-action tags identified in FRESH-004.

<a id="fresh-001"></a>
## FRESH-001 — Shared visual foundation

**Outcome:** One small component vocabulary supports the reviewed destinations without replacing the whole theme or changing unrelated screens.

### Visual rules

- Use neutral `surfaceContainerLow`/`onSurface` section surfaces with 20 dp corners and no added shadow. Avoid nested filled cards within the same section.
- Use `tertiaryContainer`/`onTertiaryContainer` for the latest saved memory's reflection surface. These warm semantic colors already exist in both palettes; keep the green primary roles for actions and progress.
- Use 12 dp corners for clickable choice rows. Icons are 24 dp; icon/text spacing is 12 dp; title/supporting-text spacing is 4 dp.
- Section headers with an action provide a separate heading and action target. Stack the action below the heading when available width is below 280 dp or font scale is at least 1.5; neither label is ellipsized.
- Typography hierarchy uses roles and spacing rather than making every title bold. Metadata uses `onSurfaceVariant`, not arbitrary text-opacity reductions.

### Shared interfaces

Create `ui/common/LumaUiTokens.kt` for `internal object LumaUi` with `PageGutter = 20.dp`, `ReadingWidth = 600.dp`, `SectionPadding = 16.dp`, `SectionGap = 16.dp`, `RelatedGap = 4.dp`, `ContentGap = 8.dp`, `ChoiceGap = 12.dp`, `GroupGap = 24.dp`, `SectionRadius = 20.dp`, and `ChoiceRadius = 12.dp`.

Create `ui/common/LumaUiComponents.kt` with these internal Compose interfaces:

```kotlin
enum class LumaSurfaceTone { Neutral, Reflective }

@Composable
internal fun LumaReadingPane(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
)

@Composable
internal fun LumaSectionSurface(
    modifier: Modifier = Modifier,
    tone: LumaSurfaceTone = LumaSurfaceTone.Neutral,
    content: @Composable ColumnScope.() -> Unit,
)

@Composable
internal fun LumaSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionModifier: Modifier = Modifier,
)

@Composable
internal fun LumaChoiceRow(
    title: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    showChevron: Boolean = true,
)
```

`LumaReadingPane` centers a width-constrained content Box and owns neither scrolling, gutters, nor insets. `LumaSectionSurface` owns its 16 dp padding and 8 dp default content gap, has no click handler, and does not merge independently actionable descendants. `LumaSectionHeader` requires both action parameters or neither and marks only the title as a heading. `LumaChoiceRow` has one click target, a minimum height of 64 dp, 16 dp internal padding, wrapping text, a decorative icon, and an optional decorative direction-aware chevron.

Migrate only the reviewed bodies to these tokens/components through their owning tasks. Keep `AppSectionHeading`'s existing public default intact; use an explicit style at changed call sites. Match `EmptyStateCard`'s surface and internal gaps to this system while preserving its signature and optional CTA.

### Acceptance scenarios

1. Neutral sections in light and dark themes use semantic container/content pairs; the reflection surface uses the corresponding tertiary pair.
2. A section heading remains a heading and its action remains a separately focusable 48 dp target.
3. A long French title and action at 320 dp/2.0 font scale wrap or stack without overlap.
4. A Persian choice row has one announced click action, a decorative icon, and the correct chevron direction.
5. In an 840 dp window, the reviewed body is centered and no wider than 600 dp; its list remains scrollable and the bottom navigation stays reachable.

<a id="fresh-002"></a>
## FRESH-002 — Compact Today and habit rows

**Outcome:** Daily actions occupy less repeated container space while preserving separate open/log/complete controls.

### Screen contract

- Today retains this order: page/date context, three direct capture choices, habits, current-day tasks, recent entries, optional daily summary. Capture choices wrap with 8 dp horizontal and 4 dp vertical gaps and retain their date-choice behavior.
- Use one neutral section surface each for the habit and task previews, with consistent section headers. Do not put a filled habit card inside the preview's filled surface. Keep the two compact habit rows separated by 4 dp.
- Compact habit rows use 8 dp vertical padding and no extra horizontal padding inside the section. Keep name, exact count/target/unit, and the full-width 4 dp progress track together.
- In a compact row, place the separate log button beside the open target when local width is at least 240 dp and font scale is below 1.5. Otherwise stack the log button below, aligned to end. Its minimum width/height is 48 dp; text can increase it. Keep the existing full-row 336 dp/1.5 fallback for standalone Habits rows.
- Hide the visible ordinary `NO_RECORDS` status line only in compact rows when logging is enabled, the day is not skipped, and no pending schedule or pending log exists. The open target's accessible description still includes its status. Display all other statuses, disabled reasons, schedule notices, and recovery controls.
- Preserve `HabitRow`'s signature. It selects a transparent compact wrapper or a neutral standalone section wrapper internally; pending feedback remains outside the open/log click targets.
- In `HabitProgressContent`, allow long localized count/unit text to wrap; do not force the count to a single non-wrapping line. Keep exact numeric semantics and existing progress tags.
- Keep task ordering, the full eligible count, one ordinary empty message, checkbox semantics, and retained feedback. Append `compact: Boolean = false` after the existing `modifier` parameter of `TodoRow`; TodayTasksCard passes `compact = true` to use a transparent wrapper, while task lists retain the default standalone surface. Its state logic and other parameters remain unchanged.
- Apply shared reading width/gutters and title roles to Today and Habits. Keep tracking-zone information and differences visible on Habits; do not hide them to meet a height goal.

### Acceptance scenarios

1. At 411 × 891 dp, font scale 1.0, two short scheduled habit rows and three capture choices are fully visible and the task section heading is visible without scrolling. This is a fixture-based check, not a guarantee for arbitrary content.
2. At 320 dp/2.0 font scale with long names and units, the open and log targets remain separate, at least 48 dp, and all content is reachable by vertical scrolling.
3. A zero-count normal compact row omits redundant visible empty-status text but still announces the habit name, progress, and state. Skipped/disabled/error/pending rows retain their meaningful text and actions.
4. More than two habits or three due tasks still shows full eligible counts and working View all actions; off-preview retry/undo controls remain reachable.
5. Switching to a historical day preserves explicit date context and capture choices, hides the task preview, and does not show or mutate stale habit rows while loading.

<a id="fresh-003"></a>
## FRESH-003 — Timeline control hierarchy

**Outcome:** Date, search, and content lead; secondary display information uses a named disclosure.

### Screen contract

- Show a page-title/Search header, existing `DayNavigation`, and a wrapping row for loaded counts plus the View options control. Search remains one direct action.
- View options starts collapsed. Its expanded panel contains the existing app-usage switch, timezone, and newest-first explanation. Keep the switch's `timeline_show_app_usage` tag and localized accessible name.
- When app usage is enabled, show the localized “App usage included” indicator outside the disclosure. The filter's active state must not become invisible when the panel is closed.
- Hoist only the disclosure's `rememberSaveable` Boolean above the existing `key(date, zone)` block so expansion survives date changes and recreation. Continue reading the app-usage flag from the existing pager/preference path; do not introduce a second stored filter value.
- Keep existing persistence, one-minute threshold, paging, selected-day behavior, error/retry, entry routes, date-only habit grouping, and empty-state content. Keep measured FAB clearance.
- Keep options in document order above the feed; expanding them moves content naturally rather than opening a new route.

Create `ui/timeline/TimelineViewOptions.kt`:

```kotlin
@Composable
internal fun TimelineViewOptions(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    showAppUsage: Boolean,
    onShowAppUsageChange: (Boolean) -> Unit,
    zone: ZoneId,
    modifier: Modifier = Modifier,
)
```

Use `timeline_view_options` for the named toggle and `timeline_view_options_panel` for expanded content. The toggle announces localized expanded/collapsed state. Use `timeline_app_usage_active` for the collapsed-visible active indicator.

### Acceptance scenarios

1. An empty day initially shows title, Search, date controls, counts, View options, and the existing empty guidance without timezone/sort text occupying separate rows.
2. Opening the options exposes the switch; enabling it includes only eligible app-usage rows and persists the existing preference.
3. Closing the panel preserves the filter and displays the active indicator; expanding or collapsing does not alter the selected date or query policy.
4. Disclosure state survives day navigation and activity recreation; a fresh installation/screen state starts collapsed.
5. With French/Persian and 2.0 text, the header/count/control rows wrap and every control is reachable; paging, retry, and the final row remain clear of the capture FAB.

<a id="fresh-004"></a>
## FRESH-004 — Modular Weekly Review overview

**Outcome:** Personal summary precedes secondary saved signals and integration setup, without duplicating the detailed report already present below the overview.

### Screen contract

Keep the existing `overview` LazyColumn item and section keys. Inside `ReviewOverview`, render three modules in this order:

1. **Personal summary:** entry/log counts, existing habit repetition/scheduled-day summary and View habits action when available, mood value/sample count or honest absence with Check in, and linked people/place counts.
2. **Latest memory:** the current latest-memory content and existing open/retry behavior. Use a Reflective surface only when a saved memory exists; an empty memory state remains a small neutral module.
3. **Saved signals:** usage total/absence, coverage information, and the existing most-sampled activity's value/unit/source/sample details. Use body-size values for absent data. Preserve the recorded-zero versus missing-data distinction.

Place setup explanations and one Integration settings action inside a named, initially collapsed “Data setup” disclosure at the end of Saved signals when the current overview would offer either integration action or its secondary access explanation. Preserve every explanation; consolidate the two overview integration links into one. Keep signal values, incomplete-coverage information, and the activity's View sources action visible without opening setup.

Keep `ReviewOverview`'s signature, `review_overview` wrapper tag, and existing metric/memory tags. Extract the signals module to `ui/review/ReviewOverviewSignals.kt` and the latest-memory module to `ui/review/ReviewOverviewMemory.kt`; both receive the same current `WeeklyReview` rather than new repository observations. Only the memory module retains the current latest-memory observation/retry behavior.

The extracted interfaces are:

```kotlin
@Composable
internal fun ReviewOverviewSignals(
    report: WeeklyReview,
    availability: IntegrationAvailabilityState,
    onSources: (ReviewDates, ReviewMetric) -> Unit,
    onIntegrations: () -> Unit,
)

@Composable
internal fun ReviewOverviewMemory(
    report: WeeklyReview,
    repository: WeeklyReviewRepository,
    onEvent: (LifeEventRecord) -> Unit,
)
```

Keep `mostSampledActivity(activities: List<ReviewActivity>): ReviewActivity?` and `reviewUsageCoverageSummary(report: WeeklyReview, availability: IntegrationAvailabilityState): String` source-compatible. Keep the week header's period, partial-week and comparison information; use the shared page-title role. Keep existing detailed sections, sticky navigation, source routing, methodology dialogs, week selection/restoration, and bottom clearance.

New module tags: `review_overview_personal`, `review_overview_memory`, `review_overview_signals`. New setup tags: `review_overview_data_setup` and `review_overview_integrations`. Retire `review_overview_usage_integrations` and `review_overview_activity_integrations` and update their affected tests to the consolidated action.

### Acceptance scenarios

1. A populated week presents personal summary, latest memory, and saved signals as three separate modules in that order; View habits, Check in where applicable, memory open, and activity sources keep their original targets.
2. A week without mood/usage/activity shows absence labels rather than measured zeros; setup starts collapsed and one overview integration action appears after expanding it.
3. A recorded zero and a nonzero fractional activity retain their exact existing formatted values; source, units, sample counts, and partial coverage remain visible.
4. Changing/recreating the selected week preserves period behavior and valid section jumps; Overview returns to the original overview anchor and all existing sections remain listed.
5. A long memory, French labels, or Persian 2.0 text wraps in one reading column; memory loading/error/retry states and bottom-most actions remain reachable above the FAB.

<a id="fresh-005"></a>
## FRESH-005 — More and Quick Capture choices

**Outcome:** Destination and capture choices feel related and require less repeated decoration.

### Screen contract

- More uses neutral `LumaChoiceRow` entries in its existing order: Tasks when available, People & Places, Settings. Preserve every title, description, callback, and tag; include the decorative forward chevron.
- Quick Capture retains its modal sheet, expanded opening policy, scrollability, close/back dismissal, life-record/task grouping, and optional Task action.
- Replace the four large filled buttons with full-width neutral choice rows, 8 dp apart, with existing icons: mood `ic_review_mood`, journal `ic_nav_timeline`, memory `ic_bookmark_outline`, task `ic_nav_tasks`. These are decorative; the visible label supplies the accessible name. Omit chevrons for these action choices.
- Use 20 dp horizontal sheet padding, 16 dp top/bottom content padding, 16 dp between groups, and 4 dp between a group title and supporting copy. Keep the sheet's built-in system-inset behavior and drag handle.
- Keep the existing date-choice chips above the life-record choices when `browsedDate` exists. They wrap, have 48 dp targets, and continue to default to now. The Task callback remains undated regardless of the life-record date choice.
- Preserve `QuickCaptureSheet`'s signature and all existing `capture_*` tags. Keep labels descriptive; do not replace them with icon-only controls or a new generic capture step.

### Acceptance scenarios

1. More routes each available entry correctly and Back returns to More; omitting the optional task callback omits only Tasks.
2. With no browsed date, each life-record choice calls its existing callback once with null; Task calls its callback once and Close creates nothing.
3. With a historical browsed date, Now and selected-date choices reach each life-record editor with the correct timestamp policy; Task still has no due date.
4. French/Persian at 320 dp/2.0 text keeps all choices and Close reachable by scrolling; each row has one action and no icon-only announcement.
5. Rotating an open chooser preserves its date choice; dismissing it restores its originating screen without saving or changing the browsed date.

<a id="fresh-006"></a>
## FRESH-006 — Confirmed feedback and progress motion

**Outcome:** A completed capture ends with acknowledgement, and a confirmed habit count change has restrained visual continuity.

### Feedback contract

- In the existing `LifeOsApp` editor-result collector, consume a result before navigation as today. After a successful return for `EntryEditorResult.Saved`, launch a short localized “Entry saved” snackbar using the existing app-level coroutine scope and SnackbarHostState.
- Do not gate acknowledgement on the follow-up record lookup used to select a date: a completed write is still successful if that optional lookup fails. Preserve the lookup's current date behavior.
- Do not emit save acknowledgement for `Deleted`, `Unmarked`, cancellation, validation failure, or failed writes. Editing an existing entry and saving it is a valid Saved outcome.
- Do not replace, dismiss, or queue behind an already visible snackbar. If the host has active feedback, skip the generic save message so undo/retry feedback retains priority. Do not add a second live region or a modal success screen.
- The consumed result is the single event boundary: recomposition or recreation must not replay the same acknowledgement. No new event database or durable notification queue is introduced.
- Keep habit/task success, undo, and retry behavior. Do not add a duplicate success snackbar for those actions.

Keep the host-priority policy in `ui/common/EntrySaveAcknowledgement.kt` so it can be exercised with an actual SnackbarHostState independently of navigation timing:

```kotlin
internal suspend fun acknowledgeSavedEntry(
    result: EntryEditorResult,
    snackbarHostState: SnackbarHostState,
    message: String,
)
```

This UI helper returns without displaying anything for a non-Saved result or an occupied host; otherwise it shows the short message. It owns neither navigation nor consumption/deduplication. `LifeOsApp` calls it from its existing app-level scope after consuming the result and performing the normal return navigation.

### Motion contract

- Animate the visual habit-progress fraction for 180 ms on confirmed same-habit/same-date count changes, including confirmed undo. Keep numeric text and semantic values tied to the current stored count.
- A first observation, a different habit, or a different date displays its current fraction immediately rather than animating from another row/date or an assumed zero. Key remembered animation state by habit ID and date.
- Pending writes do not advance progress. Respect the system animation-duration scale, including disabled animations. Do not add haptics, confetti, fabricated streaks, or milestone copy.

### Acceptance scenarios

1. A confirmed mood/journal/memory save returns through the existing route and shows one short acknowledgement when the host is idle.
2. Cancellation, a failed write, Deleted, and Unmarked show no saved message; the existing error/draft/navigation behavior remains.
3. Recomposition and recreation after consuming Saved do not produce another acknowledgement; an active undo/retry snackbar is not replaced or followed by stale generic feedback.
4. A confirmed repetition and confirmed undo update the exact count once and animate only the visual fraction; a failed/pending request never shows successful progress.
5. With system animations disabled, current progress and successful acknowledgement remain understandable; TalkBack receives one success announcement rather than competing live regions.

## New localized copy

Add these keys to the existing `values`, `values-fr`, and `values-fa` string files. Reuse existing expanded/collapsed descriptions and existing labels elsewhere.

| Resource key | English | French | Persian |
| --- | --- | --- | --- |
| `timeline_view_options` | View options | Options d’affichage | گزینه‌های نمایش |
| `timeline_hide_view_options` | Hide view options | Masquer les options d’affichage | پنهان کردن گزینه‌های نمایش |
| `timeline_app_usage_active` | App usage included | Utilisation des applications incluse | استفاده از برنامه‌ها نمایش داده می‌شود |
| `review_overview_data_setup` | Data setup | Configuration des données | تنظیمات داده‌ها |
| `entry_saved_feedback` | Entry saved | Entrée enregistrée | ورودی ذخیره شد |

## Validation and evidence

Automated coverage should target changed interactions and state contracts, not reproduce spacing constants as tests. Reuse current fixtures for dates, habit actions, usage filtering, section navigation, and saved versus unavailable values. Add focused coverage for the new disclosure and save acknowledgement.

For visual acceptance, use a dedicated emulator fixture with two scheduled short-name habits, three due/overdue tasks, and one entry; then reuse it across all changed screens. Inspect a separate existing/controlled empty state without clearing user data. Capture the six reviewed surfaces at 411 × 891 dp/1.0 English in both themes, repeat affected controls at 320 dp/2.0 French and Persian RTL, and inspect one 840 dp-wide window. Restore locale, theme, font scale, and window settings afterward.

Capture success/error/pending/retry only where those states own a changed interaction. Confirm the actual theme color pairs meet contrast thresholds, capture FABs do not obscure the final actionable row, and metadata remains honest when integration imports are off or unavailable. Record actual observations in a new implementation validation note; this specification contains requirements rather than claims that those checks have passed.
