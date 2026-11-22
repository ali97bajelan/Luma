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

## QA workflow (required)

Use this workflow whenever you are asked to run or plan QA. Match the checks to the acceptance criteria and the changed behavior.

1. Start with the smallest relevant set of existing tests. Use local JVM tests for business logic and state, and targeted Compose instrumentation tests for UI behavior. Select the affected test class or method before expanding to broader suites. Run any broader checks required by the task.
2. Reuse a running emulator and the installed QA build when they match the revision and device configuration under test. Start each scenario from known data. Wait for the expected UI state or event instead of using fixed sleeps.
3. Drive app flows through Compose semantics and stable test tags. For exploratory steps without an existing test, query the accessibility hierarchy and return only matching nodes. Use UI Automator for system dialogs, the launcher, and other cross-app flows.
4. Capture screenshots only for explicit visual checkpoints, requested evidence, or failures where an image helps diagnose the problem. For recurring visual checks, prefer local screenshot comparisons and inspect failed diffs. Keep screenshots and full logs as artifacts and pass only the relevant excerpts or images into the agent context.
5. Report pass, fail, or unverified for each acceptance criterion. Include the checks run, a concise failure summary when relevant, and paths to evidence artifacts.

## Commit messages

When asked to generate a commit message, use Conventional Commits format: `<type>(<optional scope>): <imperative summary>`. Keep it concise and based on the actual changes.
