# UX-021 — Page chrome and inset ownership vary across screen families

**Status:** Draft specification; implementation has not started. Proposed decisions below are reviewable defaults, not previously approved product policy.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-021](../../UI_UX_AUDIT-20261020N.md#ux-021--page-chrome-and-inset-ownership-vary-across-screen-families)
**Priority / effort / confidence:** P2 — Medium / Medium / Medium (copied from the static audit; not a delivery estimate).
**Affected surface:** Editors, secondary pages, Today/Timeline/Habits/More
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-021-page-header-insets.md)
**Delivery phase:** 3 — navigation, scale and consistency

## Intent and success

Give primary, secondary and editor pages one explicit inset owner and predictable pinned navigation. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

The audit is source evidence, not reproduced runtime acceptance. Its recommendations are input to this draft, not executable instructions. All tests, runtime exercises and commits in the plan are future work.

## Dependencies

None. Coordinate overlapping files using the index's integration order.

## Global constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies unless a task explicitly requires a reviewed exception.
- All changed user-facing copy must exist in EN/FR/FA with matching resource names and numbered placeholder signatures; preserve RTL and intended Gregorian display/storage behavior.
- Keep semantic light/dark palettes, at least 48 dp interactive targets, and reachability at 100%/130%/200% font, narrow portrait and short landscape.
- Preserve offline/private storage, existing consent boundaries, stable record/command IDs, original retry payloads and Undo protections.
- Never truncate accepted personal content, infer write failure means no commit, or treat an unavailable privacy mode as known.
- Only implement this task and its declared prerequisites; preserve unrelated working-tree changes and existing navigation/draft state.

## Behavior and policy

1. Primary titles use Material titleLarge; sections use titleLarge and cards titleMedium. Default compact page gutter is 20 dp, card padding 12–16 dp and spacing scale 4/8/12/16/24/32 dp. Preserve an intentional exception only when a specific component requires it.
2. Keep primary five-destination navigation and state saving. Secondary Back/title/actions are pinned; editor Close invokes the existing dirty/busy handler.
3. Pilot Today, Timeline and Mood and record compact/wide, navigation-bar and IME geometry before migrating Settings, Privacy, saved usage, other editors/details, Habits, More and remaining hand-built secondary headers.
4. Use a proposed centered 840 dp maximum content width on wide windows while retaining single-column layouts; validate reading comfort before broad rollout. Preserve full-width system chrome and meaningful list scroll restoration.

## Design and contract

Keep the app shell as system-bar inset owner, consume its applied padding, and make nested page scaffolds use zero system-bar insets. Extend existing header components and pilot Today, Timeline and Mood before migrating remaining pages.

@Composable AppPageScaffold(title: String, navigation: PageNavigation, onNavigate: (() -> Unit)?, actions: @Composable RowScope.() -> Unit, content: @Composable (PaddingValues) -> Unit): Unit. enum PageNavigation { NONE, BACK, CLOSE }. Nested Scaffold contentWindowInsets = WindowInsets(0, 0, 0, 0); app shell applies and consumes system-bar padding once. Editor content owns IME avoidance once.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `singleSystemInsetOwner` | Measure pilot content with gesture and three-button navigation. | System bars are accounted for once; header/content geometry has no duplicate system-bar gap. |
| AC-2 | `editorImeAndCloseStaySafe` | Open Mood keyboard at 200% text, edit then Close/Back. | Save remains reachable; dirty confirmation and busy protections still apply. |
| AC-3 | `secondaryBackIsPinned` | Scroll Privacy and saved usage to the bottom. | Back and title remain accessible outside scrolling body content. |
| AC-4 | `rtlAndWindowChangesPreserveState` | Switch EN/FR/FA, rotate and resize from compact to wide. | Directional icons/focus remain correct; selected tab/date and list position survive. |
| AC-5 | `pilotGatesBroaderMigration` | Compare screenshots/bounds for Today, Timeline and Mood at 100/130/200% font. | No clipping or duplicate insets; wider rollout waits for recorded pilot acceptance. |

## Scope boundary

No palette replacement, font-size shrinking, new primary destination or speculative tablet multi-pane redesign.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.

