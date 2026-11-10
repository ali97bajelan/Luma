# Luma UI consistency checklist

This pass follows the current warm-paper/obsidian theme in `ui/theme`, shared typography and shapes, `LumaUi` spacing/size tokens, `LumaUiComponents`, and `AppPageScaffold`. The older green-palette audit is superseded.

Status: **source migration complete** for the rows below. **Rendered inspection remains not run** because Android Studio reported no running device and the app UI has no Compose previews.

## Route and surface coverage

| Reachable screen or surface | Checklist: shared component/token for the inconsistency | Source |
|---|---|---|
| Today | [x] `AppPageScaffold`/shared header, `LumaReadingPane`, `PageGutter`, `SectionGap`; task, habit, focus and summary cards use Luma surfaces and shared text actions. Preserve bottom/FAB clearance. | Migrated |
| Calendar | [x] Shared page header and reading width; shared chips, buttons, cards, and dialog styling. Calendar cell markers and chart encodings keep their semantic colors and shapes. | Migrated |
| Timeline | [x] Shared page header, event cards, day navigation, paging/retry actions, and `PageGutter`/gap tokens. | Migrated |
| Review | [x] Shared section surfaces, headings, text actions, dialogs, and state feedback across overview, habits, values, signals, memory and focus summaries. Charts retain their data encodings. | Migrated |
| More | [x] Shared reading width, section surfaces, typography, choice rows, and spacing tokens. | Migrated |
| Focus setup and session | [x] Shared fields, chips, cards, buttons and dialogs; shared page inset/gap tokens. Keep the timer, progress ring, and session-specific controls specialized. | Migrated |
| Focus completion and reflection | [x] Shared editor/action footer, fields, text actions, dialogs, card surfaces and state feedback. | Migrated |
| Focus history and session detail | [x] Shared header, reading pane, list/card surfaces, filters, text actions, dialogs and retry states. | Migrated |
| Focus task picker | [x] Shared field, selected-state colors, card/list surfaces, 48 dp targets, and shared page/gap tokens. | Migrated |
| Focus settings, goals, invitations and tags | [x] Shared settings sections, fields, chips, buttons, dialogs, typography and loading/error/retry states. | Migrated |
| Habits list and management | [x] Shared page header/gutters, section surfaces, habit cards, chips, action buttons and empty/error/retry feedback. | Migrated |
| Habit detail and history | [x] Shared detail header, card/list surfaces, text actions and destructive confirmation dialog; keep pending/undo/error recovery distinct. | Migrated |
| Habit editor and log correction | [x] Shared editor header/action row/footer, fields, chips, date/time pickers and dialogs; preserve drafts, validation and callbacks. | Migrated |
| Tasks list and Today task card | [x] Shared row/card surfaces, filters, text actions, status/undo feedback and retry actions. | Migrated |
| Task editor and date/time pickers | [x] Shared editor header/footer, fields, buttons, semantic section radius and date-picker dialog; keep keyboard clearance and draft/reload flows. | Migrated |
| Memory library and review | [x] Shared page gutters, reading width, cards, headings, filters, dialogs and empty/loading/error states. | Migrated |
| Memory editor and photo editor | [x] Shared editor actions, fields, chips, cards, photo controls and dialogs; preserve selection, draft and recovery behavior. | Migrated |
| Journal editor and detail | [x] Shared editor/detail headers, fields, photo/tag actions, action footer, cards and discard/delete dialogs. | Migrated |
| Mood editor | [x] Shared editor shell, typography, fields, chips, radio-score surfaces, actions and confirmation dialogs. | Migrated |
| People list, details, editor and picker | [x] Shared page/detail/editor headers, list surfaces, fields, picker rows, buttons and dialogs. | Migrated |
| Places list, details, editor and picker | [x] Shared page/detail/editor headers, list surfaces, fields, picker rows, buttons and dialogs. | Migrated |
| Event detail and share | [x] Shared detail header, reading width, cards, text actions, state feedback and share sheet actions. | Migrated |
| Search and filters | [x] Shared field, page gutter, chips, text actions, dialogs, selected option surfaces and retry/empty states. | Migrated |
| Review sources and usage | [x] Shared page header, reading width, gutters, list/card surfaces, pagination actions, and retry/empty states. | Migrated |
| Settings and theme/language | [x] Shared settings sections, choice rows, typography, theme roles and selection controls; preserve System/Light/Dark and English/French/Persian choices. | Migrated |
| Reminder, widget, backup and integration settings | [x] Shared settings surfaces, fields, buttons, dialogs, section/gap tokens and loading/error/retry states. | Migrated |
| Privacy and app-lock settings/recovery | [x] Shared fields, buttons, cards, dialogs and semantic success/error feedback; keep security-specific confirmation and recovery steps intact. | Migrated |
| Restricted capture | [x] Shared fields, chips, editor actions and discard dialog; preserve validation, keyboard clearance, and saved draft behavior. | Migrated |
| Quick capture, timestamp, context and date choices | [x] Shared sheet/dialog, fields, chips, text actions, content gaps and 48 dp touch targets. | Migrated |
| Photo gallery and viewer | [x] Shared action/button sizing and close icon; preserve immersive image sizing, secure-window behavior, loading, retry and recovery states. | Migrated |
| Global dialogs, sheets, pickers and empty/error states | [x] Use shared Luma dialog/date-picker/sheet, field, button, choice/list surface and feedback components wherever their semantics fit; retain action labels, test tags, retry and undo. | Migrated |

## Dual-calendar source ownership

DC-I02 adds the Calendar card to the existing Settings surface. DC-I03 owns the Calendar system control and presentation. DC-I04 owns the shared calendar-aware date-picker content used by DayNavigation, task due dates, capture timestamps, habit dates, Search and Weekly Review. DC-I05 owns Settings-driven date labels, memory month grouping and widget due-date text. Source review and the full ownership/evidence map are in the [dual-calendar handoff](../validation/2026-11-10-dual-calendar.md). These source changes follow the Luma components and tokens listed above; DQA-01–10 remain NOT RUN until DC-Q01, so this note does not change any historical visual acceptance result.

## Shared-system checks

- [x] Consumer screens use shared Luma buttons, fields, chips, cards and dialogs instead of direct Material controls.
- [x] Repeated 4/8/12/16/24 dp arrangement gaps reference `RelatedGap`, `ContentGap`, `ChoiceGap`, `SectionGap`, and `GroupGap` without changing measured values.
- [x] Screen gutters use `PageGutter`; common 16 dp container insets use `SectionPadding`; reading panes use `ReadingWidth`.
- [x] Feature-level fixed text sizes and non-semantic corner literals were removed. Theme shape definitions remain the source of global radii.
- [x] Button wrappers enforce at least 48 dp touch height; primary actions use the shared primary-action height.
- [x] Warm-paper/obsidian surface and content roles stay theme-aware. Destructive actions, calendar/chart encodings, and the crop overlay retain distinct semantics.
- [x] Navigation graph, callbacks, ViewModels, stored drafts, validation, localized strings, retry/undo logic, and test tags were preserved by source changes.
- [x] Insets remain owned by their existing scaffold/surface; keyboard padding and Today’s FAB clearance remain in place.
- [x] Launcher RemoteViews/Glance widgets remain on their separate design contract.

## Verification matrix

| Check | Result |
|---|---|
| Android Studio source analysis of shared wrappers and representative migrated routes | PASS: no errors reported; some inspections timed out while collecting weak warnings. |
| `:app:assembleDebug` | PASS |
| `git diff --check` | PASS: no whitespace errors. |
| `:app:testDebugUnitTest` | FAIL: 340 passed, 8 failed (1 `TranslationCatalogTest` format-argument mismatch for `fr/photo_crop_summary`; 7 `MemoryEditorTest` ViewModel draft/photo restoration cases). Those resource/ViewModel files were not changed by this UI pass, so these failures do not point to the migrated UI callsites. |
| Device/emulator rendered inspection | NOT RUN: no running device was attached. |
| Compose Preview render | NOT RUN: no `@Preview` composables were found under the app UI package. |
| Light and dark, Persian RTL, narrow/wide layouts, font scale 2.0, keyboard-open forms | NOT RUN: requires a connected device or preview fixture. |

No remaining source-level typography, page-gutter, feature-radius, or direct Material-control inconsistencies were found in the UI scan. Visual acceptance across device configurations is still pending.
