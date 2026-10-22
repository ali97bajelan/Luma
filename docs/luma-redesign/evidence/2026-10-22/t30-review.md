# T30 implementation review — 2026-10-22

Status: **implemented (validation pending)**. This is source evidence and a T33 handoff, not release acceptance.

Execution date: 2026-10-22, Asia/Tehran. Base revision: `867413e5dbe9620cf88076f8ec45f4099a13db79`; implementation is an uncommitted working-tree diff. Build SHA, device, API, measured window and runtime font scale: **N/A — no build or device run**. Required runtime matrix remains English/French/Persian, Light/Dark/System, 320 dp at 2.0, 411 × 891 dp at 1.0, and 840 dp width.

## Implemented scope

- Shared warm-paper/obsidian role mapping, corrected container/foreground and inverse pairs, pale fixed-role fills, centrally defined shapes and offline variable fonts. Existing theme preference values, observation and edge-to-edge setup remain unchanged.
- Bundled Newsreader editorial titles, Plus Jakarta Sans body/data/controls, and Vazirmatn for Persian appearance. Mixed-script missing glyphs in Latin locales use Android's offline fallback. All resources have SIL OFL notices and source URLs under `app/src/main/assets/font-licenses/`.
- Muted reading surfaces, 24 dp sections, 16 dp Material cards, 12 dp choices, shared 24 dp icons and 8 dp habit track. Serif shared headings and journal card titles; sans dates, counts and controls. The header reserves two scaled text lines without taking ownership of system insets.
- Five original navigation destinations with selected pills, compact labels and full accessible names. Existing tags, selected semantics, callbacks and state contracts remain. The capture FAB uses paired terracotta roles and a rounded shape with its original measured bottom clearance.
- No optional features selected. No local-data indicator was added, so no new strings or privacy claims were needed.

## Checks performed

| Check | Result | Evidence / limits |
| --- | --- | --- |
| Opaque role contrast | PASS | [Full source audit](t30-source-audit.md): 158 light/dark role pairs, 4.5:1 text and 3:1 essential boundaries. This is sRGB source measurement, not a rendered-pixel or alpha/disabled-state audit. |
| Font resource integrity and coverage | PASS | Three real sfnt variable fonts; declared weight/optical axes supported. French accent samples in both Latin fonts and Persian/Latin/digit/ZWNJ samples in Vazirmatn have glyph coverage. Hashes in the source audit. This does not validate Android shaping or layout. |
| Pinned API compatibility review | PASS | Cached Compose BOM 2025.08.01 POM selects Material 3 1.3.2 and UI Text 1.9.0. Used cached sources to inspect TopAppBar expandedHeight, FontVariation, resource Font and FontFamily APIs. Fixed roles are shared ColorScheme extensions because native fixed-role properties are unavailable in 1.3.2; no dependency change. Compilation NOT RUN. |
| Shared consumer review | PASS | Pale Review partial-week badge now uses primaryFixed/onPrimaryFixed. Reflective memory section uses a neutral surface, keeping its existing onSurfaceVariant text and primary actions legible. Habit progress uses surfaceContainerHighest track: primary/track 5.616 light, 6.832 dark (previous outline track 1.630/1.873). |
| Static contract and scope review | PASS | AppTheme preferences, five PrimaryDestination entries, route/capture callbacks, tags, selection semantics, measured FAB clearance and system-inset ownership retained in source. No route-local literal palette, permission/schema change or dependency update introduced. |
| Patch whitespace | PASS | `git diff --check` |

Reproduce the source audit from the repository root with Python 3 and fontconfig installed:

```bash
python3 docs/luma-redesign/audit-t30.py
git diff --check
```

## Checks not run

The user explicitly requested no Gradle tests or emulator. No Gradle invocation or emulator/device work was performed; build/lint and all rendered checks were also skipped in this execution. Existing behavior/semantics tests were retained without adding token/padding tests.

| Check | Result | Follow-up |
| --- | --- | --- |
| Before/after captures and primary screen matrix | NOT RUN | T33: compare real rendered screens against references. |
| ThemeSettingsTest and LanguageFlowTest | NOT RUN | T33: run existing instrumentation, including recreation and resolved System appearance. |
| PrimaryNavigationAccessibilityTest and PageInsetsContractTest | NOT RUN | T33: both are in `app/src/androidTest`, not the unit-test source set listed in the original plan. The plan's future command was corrected. |
| Gradle unit/instrumented tests, assembleDebug and lintDebug | NOT RUN | Run in a later authorized validation session. No build success is claimed. |
| Offline Android font rendering, French accents and mixed Persian/Latin shaping | NOT RUN | T33: inspect real text shaping, font metrics, wrapping and language switching. |
| Narrow/large-text, tablet, RTL, keyboard/IME, TalkBack/focus and 48 dp reachability | NOT RUN | T33: exercise the full required matrix; inspect navigation labels, long titles and action visibility. |
| Runtime state/retry/undo, snackbar/final-row/FAB clearance, system bars and animation scale | NOT RUN | T33: retain controlled fixtures and record actual device/build/configuration. |
| Rendered selected/error/focus/disabled states | NOT RUN | T33: source role measurements do not establish rendered state acceptance. |

## Implementation decisions

- Supply the specified fixed roles through shared extensions while staying on Material 3 1.3.2. Cost if wrong: a future library upgrade must remove extensions once native roles are adopted.
- Adapt the shared heading/journal/progress and pale-badge consumers as part of theme integration. Keep reflective reading surfaces neutral for their existing children. Cost if wrong: visual composition may need refinement during T31/T32/T33; callbacks, metrics and navigation are unchanged.
- Work in the supplied checkout and generate a commit message without creating a commit. Cost if wrong: changes are uncommitted and must be integrated by the user.

Final review was an author self-review, as this plan requests no delegation. No release acceptance is claimed; the remaining runtime/build checks are the material limitation.
