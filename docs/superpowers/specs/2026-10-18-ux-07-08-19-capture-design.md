# UX-07, UX-08, UX-19: Capture layout and access

Status: implemented in the working tree on 18 October 2026; focused automated checks passed. Keyboard, 2.0 text-scale, Back/backdrop, and manual inset acceptance remain open. No commit created; generated messages are in the [implementation plan](../plans/2026-10-18-ux-07-08-19-capture.md).
Source: [review](../../UI_UX_REVIEW_2026-10-18.md); shared [brief](../../UI_UX_IMPLEMENTATION_READY_2026-10-18.md). Depends on UX-16.

## Intent and approaches

Bring editor content into the first viewport and reclaim navigation space while preserving deliberate past-date capture. Recommended: compact timestamp row, existing picker interactions, standard Scaffold FAB. Keeping the footer saves implementation work but loses the same space; replacing navigation with a central capture destination would change the established five tabs unnecessarily.

## UX-07: Compact time disclosure

Replace MoodTimeControls' permanent heading, separate date/time rows and zone paragraph with one full-width ≥48 dp editable timestamp row. Show “Today, [time] · Change” for current device date; otherwise the full localized selected Gregorian date plus time. Past dates must never collapse to an ambiguous “Earlier” label. Main mood/content input immediately follows it. Keep fixed Save and optional-context disclosures.

Tapping Change opens one scrollable date/time dialog with Date and Time controls, timezone/offset explanation, and Done/Cancel. A nested picker changes only the dialog draft; Done invokes the existing onTime(LocalDateTime, ZoneId): Boolean exactly once. Cancel, outside dismiss and Back do not mutate editor state or dirty status. Validation false keeps dialog and field feedback visible. Initial full precision survives opening/closing and choosing the same visible minute; a different time explicitly selects minute precision. Date-only changes preserve current precise LocalTime. Existing editor onTime/domain rules keep their DST and future-date policy.

Create common/CaptureTimestampRow.kt rather than keeping a mood-owned control shared by Journal/Memory. Interface: milliseconds: Long, enabled: Boolean, timeError: Boolean, onTime: (LocalDateTime, ZoneId) -> Boolean, modifier: Modifier. Keep MoodTimeControls as a temporary forwarding wrapper or migrate all three callers atomically.

Show full zone explanation inside dialog. Inline warning only if the saved record's zone (when supplied by editor) differs from device zone and changes its displayed civil date; do not infer a saved zone from an Instant. Editors currently display device zone: retain that behavior, without inventing a new persisted zone. Memory promotion remains disabled for time edits and preserves the source timestamp.

## UX-08: Capture locations and insets

| Destination | Capture access |
| --- | --- |
| Today | Existing inline Mood/Journal/Memory shortcuts; no additional FAB |
| Timeline | Labeled “Quick capture” extended FAB |
| Review | Labeled “Quick capture” extended FAB; current-time default |
| Habits | Add habit and Log; no life-entry FAB |
| More | Existing navigation to Today/Timeline; no capture FAB |
| Secondary/editor destinations | No global FAB |

LifeOsApp.bottomBar contains only PrimaryNavigationBar. Use outer Scaffold.floatingActionButton for Timeline/Review; preserve tag quick_capture on that action. Keep Today shortcut behavior, five tab labels, route state restoration and selectedDate unchanged.

FAB overlays content. Pass a content-bottom clearance value to TimelineRoute and WeeklyReviewRoute (and the MemoryReviewRoute fallback), computed from measured FAB height plus 16 dp. End padding is at least that clearance plus existing content gap; do not duplicate system/navigation inset already handled by outer Scaffold. At large text allow FAB label to wrap/stack within screen width while preserving ≥48 dp targets. Last row and its action must scroll fully above FAB; avoid blank 72 dp footer rows elsewhere. RTL uses logical end placement.

## UX-19: Expanded quick-capture choices

Set rememberModalBottomSheetState(skipPartiallyExpanded = true) for QuickCaptureSheet. Use shorter introduction, explicit current-time versus selected-date chips, three type choices and visible Close. Default remains Use current time; selected date only used after deliberate selection. Sheet gets safe bottom/system-bar padding once and scrolls at extreme sizes.

At 360/411 dp widths, fonts 1.0/1.3: all three choices and Close appear together when opened. At 2.0 or landscape: scroll is visibly available, all controls reachable without expansion gestures. Backdrop/Back/Close dismiss without editor navigation; switching date option alone saves nothing. On capture selection, close sheet and navigate once, keeping the selected date initialization and saved-date return policy.

## Acceptance

All three editors' main input is in first viewport with keyboard closed; with a real docked keyboard focused input and fixed Save remain reachable. Test current/past dates, date choice for all three types, canceled picker, pristine/dirty exits, memory promotion, photo cancellation, gestures/insets, RTL and last-list-row accessibility. No UX-01 reorder or UX-02 count changes.
