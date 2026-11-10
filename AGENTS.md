# Repository instructions

## UI design system (required)

All app UI work—including new screens, fixes, refactors, dialogs, sheets, pickers, and editor states—must use the existing Luma design system as its foundation.

Before changing UI, inspect the relevant shared definitions:

- `app/src/main/java/com/luma/lifeos/ui/theme/`
- `app/src/main/java/com/luma/lifeos/ui/common/LumaUiTokens.kt`
- `app/src/main/java/com/luma/lifeos/ui/common/LumaUiComponents.kt`
- `app/src/main/java/com/luma/lifeos/ui/common/AppPageScaffold.kt`
- `docs/luma-redesign/README.md`
- `docs/luma-redesign/route-coverage.md`
- `docs/luma-redesign/ui-consistency-checklist.md`

Use the current warm-paper/obsidian theme, shared `MaterialTheme` color roles, typography and shapes, and `LumaUi` tokens for spacing, sizing and radii. Reuse Luma components and established page/editor scaffolds wherever their semantics fit. When a repeated pattern is missing, extend the shared system and migrate its consumers; avoid duplicate wrappers and arbitrary feature-level colors, text sizes, spacing or shapes.

Resolve conflicting design documents against current code and the latest applicable specification. Older green-palette audits must not be used to restore a previous palette or introduce a competing visual system.

Preserve intentional specialized visuals such as Focus timers, charts, calendar markers and photo crop overlays. Follow the separate launcher-widget design contract for launcher widgets.

UI changes must preserve navigation, callbacks, state, drafts, validation, test tags, retry and undo behavior. Maintain System/Light/Dark support, English/French/Persian localization, RTL, offline fonts, accessible semantics, touch targets of at least 48 dp, large-text support, and existing inset, keyboard, scrolling and FAB clearance behavior.
