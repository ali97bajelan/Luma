# Luma design system proposal

Status: proposed implementation contract, 19 October 2026. No UI or business-logic changes are implemented by this document. Apply through the existing Kotlin/Compose/Material 3 stack; see [audit evidence](AUDIT_REPORT.md) and [scoped implementation tasks](IMPROVEMENT_TASKS.md).

## Principles and product identity

1. **Record first.** Mood, journal and memory capture must work offline without permissions or setup. Keep Today direct capture and progressively disclosed context.
2. **Time must be trustworthy.** A visible day, loaded rows and mutation target must agree. Tasks use civil due dates; habit tracking dates can differ from the device date; Review uses its own comparison week. Name those differences rather than merge their meanings.
3. **Acknowledge only what is known.** Missing, loading, read error, partial history and recorded zero are separate states. “Reset requested” does not mean “data erased.” “Export completed” does not mean a file still exists.
4. **Recover without losing work.** Preserve accepted drafts and failed operation identities. Every visible Retry must target the operation that failed.
5. **One interaction, one predictable meaning.** Browsing opens details; explicit Edit enters an editor. Creation from an attachment picker returns a selected item. Close and system Back use the same draft guard.
6. **Calm, private and native.** Retain Luma's muted green/cream identity, Android pickers, native Material controls and restrained motion. No rewards pressure, mandatory onboarding, account or cloud requirement.

## Existing technology and migration boundary

Current stack: Compose Material 3 (BOM `2025.08.01`), Navigation Compose, lifecycle ViewModels/SavedStateHandle, Room, Kotlin coroutines, Android API 28 minimum / 35 target. Use APIs compatible with the current dependencies; verify compile availability before adopting newer adaptive Material APIs. A window-width observation and native `NavigationRail` are sufficient initially; no dependency upgrade is required just to document or normalize spacing.

Extend `ui/common`, `ui/theme`, existing route composables and existing state holders. Do not build a replacement widget framework, replace Room, introduce remote services or migrate the whole navigation graph as a prerequisite. State-correctness fixes can ship before design-token migration.

## Semantic colors

Keep existing `Theme.kt` values as the initial authoritative palette. No dynamic color by default: the existing stable brand palette is appropriate. A later opt-in dynamic palette is a separate product decision.

| Role / Material token | Light | Dark | Usage |
| --- | --- | --- | --- |
| primary / onPrimary | `#245E51` / `#FFFFFF` | `#9FD2BF` / `#00382E` | Primary action, active selection, important interactive text |
| primaryContainer / onPrimaryContainer | `#CDE8DC` / `#0A2F27` | `#0D5144` / `#BCEEDB` | Selected context, restrained highlighted result |
| secondary / onSecondary | `#5E6258` / `#FFFFFF` | `#C5C9BB` / `#2F332D` | Secondary emphasis; avoid competing with primary |
| secondaryContainer / onSecondaryContainer | `#E0E4D7` / `#1A1E17` | `#454A42` / `#E0E4D7` | M3 selection/container roles |
| tertiary / onTertiary | `#775A35` / `#FFFFFF` | `#E3C094` / `#422D0B` | Limited accent, never generic error or mandatory mood meaning |
| tertiaryContainer / onTertiaryContainer | `#F5DEBB` / `#291A06` | `#5B421F` / `#F8DFB9` | Optional attention surface with explicit text |
| surface / onSurface | `#F8F8F2` / `#1B1C18` | `#111512` / `#E1E3DC` | Page and primary content |
| surfaceContainerLow | `#F2F3EA` | `#191D19` | Light content grouping |
| surfaceContainer | `#ECEDE5` | `#1D211D` | Filled content cards |
| surfaceContainerHigh | `#E6E8DF` | `#272B27` | Modal/raised tonal grouping |
| surfaceContainerHighest | `#E0E2DA` | `#323632` | Highest tonal layer; use sparingly |
| surfaceContainerLowest | `#FFFFFF` | `#0C100D` | Lowest contained layer where needed |
| onSurfaceVariant | `#44483F` | `#C4C8BE` | Supporting copy, metadata; never reduce text alpha casually |
| outline / outlineVariant | `#74796F` / `#C4C8BE` | `#8E9389` / `#424840` | Essential boundary / decorative separator |
| error / onError | `#BA1A1A` / `#FFFFFF` | `#FFB4AB` / `#690005` | Destructive confirmation and failed-action text/icon |
| errorContainer / onErrorContainer | `#FFDAD6` / `#410002` | `#93000A` / `#FFDAD6` | Persistent serious error panel |
| inverseSurface / inverseOnSurface | `#2F312C` / `#F0F1E9` | `#E1E3DC` / `#2F312C` | Native snackbar |
| scrim | `#000000` | `#000000` | Let M3 determine modal alpha |

Use `MaterialTheme.colorScheme`, never repeat raw hex in feature code. Keep remaining surfaceBright/surfaceDim/surfaceTint/inversePrimary values from the existing scheme. Do not remap imported source types, emotions or positive/negative trends to arbitrary red/green: saved observations are not judgments.

**Success:** use ordinary surface/onSurface copy and, where useful, primary icon with a checkmark and explicit “Saved” label. A new success hue is unnecessary. **Attention:** use explicit copy and an icon; tertiaryContainer may support it but must not carry meaning alone. **Destructive:** preserve existing clearly destructive entry/confirmation treatment, including Privacy's error-colored Delete action; use specific verbs and distinguish entry deletion from marker removal. **Disabled:** use Material's disabled styles; retain an adjacent reason when the disabled state would be unclear.

The audit calculated several base pairs and found no failure in those samples. Recheck all actual text/container/alpha combinations after migration, including photos, snackbars and system bars. Follow [WCAG normal/large-text contrast](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html) and [non-text contrast](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html); token sampling is not an accessibility certification.

## Typography

Use the platform sans family already supplied by Material, including script fallback. Keep user-authored text intact. Define one explicit `Typography` mapping in theme so role use is inspectable; the proposal uses standard M3 metrics, not a new typeface.

| Product role | M3 role | Size / line height | Weight | Application |
| --- | --- | --- | --- | --- |
| Primary page title | titleLarge | 22 / 28 sp | Regular | Today, Timeline, Habits, Review, More; align with native top-bar scale |
| Secondary top-bar title | titleLarge | 22 / 28 sp | Regular | Settings, catalogs, Tasks, details, editors, evidence routes |
| Detail content title | headlineSmall | 24 / 32 sp | Regular | User title/name inside a detail page; omitted when duplicating identical bar text adds no value |
| Section title | titleMedium | 16 / 24 sp | Medium | Habits/Tasks/Recent entries, Review sections, form sections |
| Row/card title | titleMedium | 16 / 24 sp | Medium | Event title, habit/task name, catalog row |
| Body / primary value | bodyLarge | 16 / 24 sp | Regular | Notes, explanations, normal content |
| Supporting body | bodyMedium | 14 / 20 sp | Regular | Card summary, scope text, empty-state explanation |
| Metadata | bodySmall | 12 / 16 sp | Regular | Zone/source, provenance, count qualification |
| Action/chip label | labelLarge | 14 / 20 sp | Medium | Native control labels; let M3 apply |
| Small label | labelMedium | 12 / 16 sp | Medium | Source/type tag where secondary to a title |
| Prominent metric | headlineSmall | 24 / 32 sp | Regular | One primary saved metric per group; stack rather than squeeze |

Sizes are sp and respond to user scaling. Do not use bodySmall for task titles, input values or the sole error message. Do not lower font scale to fit. A top-bar title may ellipsize to one line only when its full context exists accessibly/in content; user record titles in lists may use two lines with ellipsis, and full details must expose the complete value. Form and error copy wraps without line caps. Avoid universal bold; weight identifies hierarchy, not every fact.

## Spacing, sizing and surfaces

| Token | Value | Use |
| --- | ---: | --- |
| space.xs | 4 dp | Text/icon supporting gap |
| space.sm | 8 dp | Related controls, stacked label/value |
| space.md | 12 dp | Compact row inset/within-group separation |
| space.lg | 16 dp | Phone content gutter, standard card inset, row spacing |
| space.xl | 24 dp | Section boundary, roomy modal body padding |
| space.2xl | 32 dp | Rare large section separation |
| target.minimum | 48 × 48 dp | Layout-reserved actionable target |
| icon.standard | 24 dp | Navigation and action vector |
| icon.empty | 32 dp | Small decorative empty-state marker |
| content.readingMax | 680 dp | Editors, privacy, detail text, settings, single feed |
| content.wideMax | 1,200 dp | Optional grouped Review layout; never stretch individual reading columns |

Use 16 dp compact-window horizontal gutters; 24 dp on medium/expanded windows. Use 16 dp content-card insets, 12 dp for compact actionable preview rows, 8 dp gaps within a row, 16 dp between cards and 24 dp between conceptual sections. This role mapping deliberately replaces ad hoc 14 dp card insets/5 dp gaps; default Material internal padding remains native.

- Minimum interactive row 56 dp for a single line, 72 dp for title/supporting text; these are minimums, not hard clipping heights.
- Native buttons/chips may draw smaller than 48 dp; reserve non-overlapping touch space. Do not shrink five-score targets to fit a narrow row.
- Remove Today's fixed 100 dp trailing gap when no overlay occupies it; use a normal 24 dp tail. When capture FAB exists, preserve the existing measured FAB height plus 16 dp clearance. Test last row accessibility above navigation/IME.
- Rounded shapes: 8 dp extra-small annotations, 12 dp small cards/fields where M3 supplies them, 16 dp standard content cards, 28 dp dialogs/sheet top corners, full pill only for native buttons/chips. Set M3 Shapes roles to extraSmall 8, small 12, medium 16, large 24 and extraLarge 28 dp, keeping native component roles. Do not turn every row into a pill.
- Base pages and normal cards: 0 dp shadow. Separate content through spacing/tone or outline. Use native FAB elevation and modal/sheet elevation; no custom large shadows.
- Navigation destination cards use outlined surfaces. Observation/content cards use restrained tonal surfaces. Empty previews should not be large filled cards nested inside other cards.

## Window and inset contract

Use actual available window width, not device name/orientation. Proposed compact <600 dp: bottom navigation; medium 600–839 dp: navigation rail + centered content; expanded ≥840 dp: rail + bounded reading content, optional two-column Review only after UX-14 acceptance. Keep the same five destination identities and saved stacks. Do not build list/detail dual-pane navigation as a prerequisite.

At 320–359 dp or font scale ≥1.5, stack date/context actions and multi-value comparisons. A five-score group becomes a wrapping group preserving order and 48 dp targets; label the overall scale and selected value. Hardware Tab/D-pad follows visual reading order in LTR/RTL.

**Insets:** choose the outer `LifeOsApp` Scaffold as owner of system safe areas and navigation clearance. Apply its content padding and consume it once at the NavHost boundary. Nested route Scaffolds/top bars receive zero additional system insets. Route content owns only its toolbar and IME/action-area layout. Modal windows use their own M3/system inset handling. Validate this contract before broad migration; do not apply zero insets globally without observing cutouts/keyboard. [Compose inset guidance](https://developer.android.com/develop/ui/compose/system/insets-ui) explains consumption and nested padding.

## Navigation and page anatomy

- **Primary:** one compact native top bar; page title is a heading. Timeline gets a named Search action. Today keeps capture immediately under date. Habits retains Add habit. Review retains week context/Sections. More remains a simple hub.
- **Secondary:** extend existing `DetailTopBar` into a shared page-bar contract: auto-mirrored Back icon, title, at most one frequent action. Persist above scrolling content. Move long date/zone/filter explanation into the scroll body.
- **Editors:** Close icon + editor title, scrollable fields and sticky Save/action area padded for IME. Close/system Back dismiss the active modal/IME first as platform behavior permits; an actual editor exit uses one dirty guard. During non-cancelable commit, both Back paths agree.
- **Browsing:** event cards open read-only detail for manual records, then explicit Edit; imported data stays read-only. Existing quick edit can remain as a clearly named separate action. Card semantics say “Open [type]”, not “Edit” when opening detail.
- **Dates:** shared day selection across Today/Timeline/Habits remains intentional; selected date stays visible. Record timestamp explicitly says Today/time or selected date/time. Review capture defaults to now; a chip labeled “Use browsed day: [date]” must not imply it captures the Review week.
- **Tasks:** separate due date from occurrence time. “Add task” from Quick Capture starts without a due date. Today prefill follows the existing Tasks product specification; do not change it during visual cleanup. Clearly show No due date/Overdue/Due [date].
- **Return:** preserve selected tab/day/week/filter/scroll after details, selector cancellation and external UI. A created person/place attaches only in the invoking capture flow; catalog creation simply returns to catalog.

Native Android navigation conventions and adaptive patterns are the starting point; see [Android layout and navigation](https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-and-nav-patterns). No sixth primary destination is proposed.

## Component specifications

| Component | Concrete contract | Existing starting point |
| --- | --- | --- |
| PageTopBar | Heading, auto-mirrored Back where secondary, consistent 22 sp title, zero nested system inset, contextual action label | `DetailTopBar`, `EditorTopBar` |
| DayContextControls | Date picker, previous/next, Today; single line when fitting, date row + action row when constrained; full date accessible even if visually shortened | `DayNavigation` |
| ContentCard | 16 dp padding, title/value/supporting hierarchy, max two metadata lines in browse mode; detail exposes all metadata | `EventCard`, summary/review cards |
| Interactive row | Body click and trailing mutation are separate, named targets; no duplicate nested toggle; pending state prevents duplicate action only | `HabitRow`, `TodoRow` |
| DestinationRow | Outlined or plain native list row, title/subtitle + trailing chevron where useful; 72 dp minimum, grows with text | More/Settings rows |
| Single-choice group | Native radio/segmented controls for equal-status choices; selected semantics; wrapped/stacked if text grows | Theme/Language, Task filters |
| Multi-choice group | FilterChip/checkbox rows; clear overall label and selected state; no meaning by color alone | Mood emotions, tags |
| InputField | Persistent label, optional hint distinct from label, multiline body where appropriate, supporting validation, focus Next/Done | Existing OutlinedTextField editors |
| Field validation | Error adjacent to field, accepted text retained on rejection, specific reason + recovery, focus/bring-into-view on submit | Journal field feedback |
| CatalogSelector | Search input, lazy choice list, stable identity, selected count, Clear selection/Done/Create; body scrolls, actions fixed | SearchOptionPicker + existing pickers |
| LinkedSelectionChip | Name, duplicate-name context, explicit Remove [name/context], unknown item still removable | PersonPicker/PlacePicker/ActiveSearchFilters |
| ContextDisclosure | Named subject stays visible both states; stateDescription expanded/collapsed; save expansion when useful | `DetailsDisclosure` with consistent named-title behavior |
| OriginalEntryPreview | Six-line source excerpt, source type/date, Expand original/Collapse original; marker input before expanded source body | Memory promotion original block |
| AsyncFeedback | Polite live-region text; errors carry error semantics and specific retry; successful messages never tagged as errors | `AccessibleFeedback` |
| OffscreenOperationFeedback | Failed target title/action/reason independent of feed membership; Dismiss or safe Retry/Open; retained until resolved | Full Tasks off-page handling |
| LoadingState | Short named progress + scope; initial count unknown; refresh retains qualified previous snapshot; spinner never blocks unrelated modules | Existing isolated Today states |
| EmptyState | One concise explanation + useful existing action; differentiate empty day/library/no match/no scheduled habits; no duplicated zero copy | `EmptyStateCard` |
| ErrorState | Scope + what remains intact + Retry; missing record has Back rather than perpetual Retry | Existing EventDetail state model |
| PhotoAttachment | Stable ID, numbered thumbnail, Add/Remove-specific progress, indexed remove label, useful unavailable recovery | `PhotoEditor`, `PhotoGallery` |
| IntegrationStatus | Android access + import preference + last saved import are distinct; Manage access available while off; saved history retained | Existing Usage/Health cards |
| BackupFeedback | Exact exported/restored/failed result, announced once; replacement preview counts and explicit warning before confirm | `BackupCardContent` |

### Forms and specific copy decisions

- Required mood score has verbal endpoints and selected score description. Optional energy/stress/importance retain None/Clear behavior and are not prerequisites to Save.
- Mood note new-edit limit: **5,000 UTF-16 code units**. For a note at or below the limit, reject replacements over the limit, retaining accepted text and an inline “Use 5,000 characters or fewer” message. For a pre-existing over-limit note, permit strictly shortening edits even if still over 5,000 (12,000→11,000 accepted), reject growth (12,000→13,000 rejected), and keep its draft outside the Bundle. Preserve full text for reading/copying; never truncate or delete it. Counters appear near the limit, not on every tiny note. Domain and UI policy must agree without breaking old data.
- Journal's existing 50,000-character policy and tag constraints remain; do not silently tighten unrelated limits. Keep pending-tag acceptance on Save and invalid tag focus recovery.
- Search page title: **Search entries**. Scope explanation: **Matches entry titles, tags, people and places. Journal text, notes, habit logs and tasks are not searched.** Put full explanation behind an accessible “What is searched?” action near the input; show a short first-use hint. Preserve current SQL scope.
- Photo Save label says **Saving…** only for entry persistence. Copy progress says **Adding photo…** or **Removing photo…**. Progress should be indeterminate unless actual bytes/count are known; never invent a percentage.
- Failed correction: “Couldn't update this repetition. Your correction is kept.” Failed history: “Couldn't load older repetitions.” Failed delete: “Couldn't delete this habit. Nothing was confirmed deleted.” Reconcile ambiguous commits before asserting unchanged state.
- Selected habit log: visible Selected repetition label plus subtle primary-container surface; semantic selected state and a polite arrival cue. Use a text marker as well as color.
- Empty Today Tasks: **No open tasks due today or earlier.** and **Add task**. Show the count only when >0; full list shows a confirmed zero only after a successful load.
- Repeated navigation links need contextual accessible names: View all tasks, View all habits, Remove photo 2, Retry loading older repetitions. Avoid replacing visible labels with excessively verbose descriptions when the native role already conveys action/state.

## Dialogs and sheets

Use M3 `AlertDialog` for short confirmation; `DatePickerDialog`/native time input for dates/times; `ModalBottomSheet` or adaptive full-screen surface for complex selection. Do not stack unconstrained long Columns in small dialogs.

- Titles name the action; confirm labels name its effect (Delete task, Remove memory marker, Replace and restore). Cancel is non-destructive.
- Long body is bounded and scrollable; confirm/cancel remain reachable with 200% font, landscape and IME. Selection lists use available-height constraints, not a list-height constant plus unbounded surrounding text.
- Modal Back dismisses only the top modal; Cancel retains underlying draft/criteria. Search criteria are live-applied: label dismissal Close/Done, and do not imply transactional Cancel unless rollback is implemented.
- Restore preview retains archive date/counts, full replacement warning and zero-task warning. Reset keeps irreversibility, app closure, data scope and exported/provider-copy caveats next to the action.
- Default focus must not trigger a destructive confirmation accidentally. Restore focus to originating control after dismiss; after successful deletion focus a sensible neighbor or destination heading.

## Interaction and feedback

Retain native ripple/pressed/selected states. A click must immediately show pending state scoped to the target; a network-like global blocking spinner is inappropriate for independent local modules. Log/task mutations should display success only on acknowledged or reconciled commit. Keep failed operation identity for retry; never create duplicates to manufacture visual success.

Use native Snackbar for brief completion/Undo when the user can still understand the target. Retain persistent inline feedback for failures, conflicts and pending recovery; timed snackbars cannot be the only recovery surface. Undo that expires must not imply unbounded reversibility. Restore invalidates old Undo/result tokens as the existing task integration does.

Use short native motion (approximately 150–250 ms where configurable) for disclosure/state changes; no completion confetti, animated chart noise or long entry transitions. Respect reduced animation/system duration settings. Optional haptic for an acknowledged habit/task completion may be evaluated later; visual and spoken feedback remain primary. Never add haptics to every navigation tap.

## Accessibility and localization requirements

Reserve at least 48 dp actionable targets and prevent expanded hit rectangles from overlapping. Supply meaningful icon actions and rely on native role/checked/selected semantics. Decorative icons remain null-described. See [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).

Use heading semantics on pages and meaningful sections, specific action labels, polite async feedback and error semantics for actual errors. Do not force focus on every refreshing metric. Preserve selected state and reading order; modal dismissal and row removal need explicit focus verification. Test actual TalkBack speech and keyboard/Switch Access, not only source properties.

All labels, counts, errors, indexed actions and new copy must ship in English, French and Persian. Use existing number/date helpers; add an explicit shared device-hour-cycle policy for all time displays, not only HabitHistory. Preserve Gregorian storage/calendar behavior, civil task dates, habit tracking zones and true timestamp precision. Isolate raw package/zone identifiers for bidirectional display; do not mirror their text. Back/forward icons auto-mirror; user photos do not.

Verify font scale 1.0/1.3/2.0 and maximum display size with no hidden critical action or clipped accepted content. Compact rows may grow/stack; no fixed text-height assumptions. Large text is a supported layout, not a special zoom screen.

## Adoption and validation

1. Correct state/recovery contracts first (UX-01–07/33); accessible feedback UX-15/21 can proceed independently.
2. Establish tokens and one page/inset model (UX-11–14). Pilot on Today, Timeline and one editor before migrating all families.
3. Standardize selectors, detail/history, privacy and operation-specific states (UX-08–10/16–28/34–35).
4. Evaluate optional enhancements separately (UX-29–32); do not gate release on them.

For every migrated component: verify light/dark, English/French/Persian, narrow phone/font-2.0, landscape/IME, wide window, loading/empty/error/data, Back/restoration and assistive actions. Record tested build/device/state and actual result. Use the verification matrix in [IMPROVEMENT_TASKS.md](IMPROVEMENT_TASKS.md); no proposed acceptance is a claim that it has passed.
