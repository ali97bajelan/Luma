# T34 — Launcher Widgets Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan does not request subagent delegation.

**Goal:** Apply the reviewed light/dark widget design to the existing Tasks and Habits RemoteViews without changing saved-data behavior.

**Architecture:** Retain providers, paged collection factory, token-backed actions and measured compact fallbacks. Extend semantic resources and XML rows, bind progress from saved counts, then validate actual launcher behavior separately from renderer fixtures.

**Tech Stack:** Kotlin, Android RemoteViews/XML, JUnit, Android instrumentation, Gradle, JDK 17.

**Spec:** [T34 launcher widget specification](../specs/2026-10-22-t34-launcher-widgets-redesign-design.md). T34-A/B/C and T34-D source implementation is present. Local build/unit/lint checks and focused API 36 offscreen renderer/preview checks pass; real launcher acceptance remains incomplete. See the [T34 acceptance record](../../luma-redesign/evidence/2026-10-22/t34/acceptance.md).

## Global constraints

- Keep Kotlin, Android RemoteViews/XML, minSdk 28, compileSdk 35, targetSdk 35 and JDK 17; no Glance migration or new dependency.
- Work offline; no account, network font, CDN, telemetry, new permission or schema migration.
- Preserve System/Light/Dark and English/French/Persian, including app theme overrides independent of system appearance.
- Preserve widget IDs/configuration, stable record IDs, paging, saved date/zone, routes, pending-intent identity, mutation eligibility and loading/error/retry/undo behavior.
- Render saved values only; no reference records, emoji inferred from names, sample dates, wallpaper, dock, status/navigation bars or theme toggle.
- Every interactive target is at least 48 × 48 dp; distinct targets have at least 8 dp separation.
- Normal text contrast is at least 4.5:1; large text and essential non-text indicators are at least 3:1. Status also has text.

## Review focus

- App Light on system Dark and app Dark on system Light: shell, collection rows, progress and pressed states must agree. Covered by A/C/D.
- Long French/Persian at narrow bounds and 2.0 text: padded count and action labels remain whole or use accessible compact fallback. Covered by A/B/C/D.
- Zero, huge, skipped, reached or invalid-target counts: no false nonzero progress, overflow, duplicate accessibility announcement or extra +1. Covered by C.
- Collection recycling and feedback reapply after mutation/theme change: no stale track, hidden recovery, wrong record action or changed stable ID. Covered by B/C/D.
- More records than the measurement page: real collection scrolling and overflow remain available after added padding, including 500 records. Covered by A/D.

## File and interface map

Existing Kotlin files are under `app/src/main/java/com/luma/lifeos/widget/`:

| File | Responsibility in this plan |
| --- | --- |
| `WidgetRenderer.kt` | Theme-resolved row/shell binding, padded header/row measurements and progress binding |
| `WidgetLayoutPolicy.kt` | Existing capacity policy plus pure `habitProgressPermille(count: Int, target: Int): Int` |
| `WidgetCompactRenderer.kt` | Compact state consistency and recovery geometry |
| `WidgetCollectionFactory.kt` | Four habit layout types; existing stable IDs/paging preserved |

Resource and test files are listed per task. `WidgetRenderer.render(context: Context, widgetId: Int, snapshot: WidgetSnapshot?, failure: Boolean, options: Bundle? = null, loadingKind: WidgetKind? = null): RemoteViews` and `collectionRow(context: Context, snapshot: WidgetSnapshot, row: WidgetRow, widthDp: Int): RemoteViews` remain compatible. All shared resources must work without assuming a Compose theme in the launcher host.

Execution order: A → B/C → D. Execute B and C sequentially in this session because they edit the same renderer. T30 supplies design direction; its existing acceptance blockers do not prevent isolated widget implementation, and T34 cannot close them. T32-C retains settings/configuration ownership.

### T34-A: Shared palette, shell and responsive states

**Modify:** `app/src/main/res/values/widget_colors.xml`; existing `widget_background{,_dark}.xml`, `widget_button_background{,_dark}.xml`, `widget_row_action_background{,_dark}.xml`, `widget_utility_background{,_dark}.xml` in `app/src/main/res/drawable/`; `app/src/main/res/layout/widget_data.xml`, `widget_data_stacked_header.xml`, `widget_data_compact.xml`, `widget_data_compact_wide.xml`; `WidgetRenderer.kt`; `WidgetCompactRenderer.kt`.

**Create:** `app/src/main/res/drawable/widget_row_background.xml`, `widget_row_background_dark.xml`, `widget_count_background.xml`, `widget_count_background_dark.xml`.

**Test:** existing `app/src/androidTest/java/com/luma/lifeos/widget/WidgetColorContrastAcceptanceTest.kt`, `WidgetRendererAcceptanceTest.kt`; `app/src/test/java/com/luma/lifeos/widget/WidgetLayoutPolicyTest.kt` when capacity calculations change.

**Interfaces:** Consumes existing render API, theme resolver, totals and localized plurals. Produces the spec's resource roles, 28 dp shell/16 dp row/24 dp action shapes, padded badge and consistent compact shells. Keep existing view IDs. No product-state interface changes.

- [ ] Record current focused widget results and synthetic baseline before edits; do not relabel historical passes as current evidence. NOT RUN at the user's request.
- [ ] Add contrast assertions for primary/secondary/overdue text on both shell and row, badge/pressed-action pairs, focus states, and progress fill versus track. Assert 4.5:1 text and 3:1 indicators; avoid tests that merely compare hex literals. Tests were not added in this pass.
- [ ] Extend `headerTargetsKeepLocalizedLabelsWholeAcrossFontScales` and `constrainedStateShellsKeepPrimaryRecoveryInsideBounds` for badge padding, opposite app/system themes, narrow French/Persian and loading unknown counts. Preserve existing list/Capture/Refresh identity assertions.
- [ ] Run the focused contrast/renderer classes using the commands below; confirm added assertions expose the current missing role/badge behavior before implementing. NOT RUN at the user's request.
- [x] Apply the spec palette/shape values and count badge backgrounds. Account for the badge's extra 16 dp width and 4 dp height in `headerPlan`; retain count omission when it cannot fit. Preserve the hint below the header and measured stacked/compact transitions.
- [ ] Render setup/loading/empty/read failure and all feedback outcomes with shared colors; check legacy capture consumers of changed resources. NOT RUN; no renderer or emulator validation was performed.

Suggested commit message for T34-A: `feat: restyle widget shells and semantic roles`

### T34-B: Inset Tasks cards

**Status:** Implemented (validation pending). Gradle tests and emulator checks were not run at the user's request.

**Modify:** `app/src/main/res/layout/widget_task_row.xml`; `WidgetRenderer.kt` (`taskRow`, `measureTaskRow`); existing `app/src/androidTest/java/com/luma/lifeos/widget/WidgetRendererAcceptanceTest.kt` and, if action coverage changes, `WidgetIntentsTest.kt`.

**Interfaces:** Consumes A's row backgrounds, existing `WidgetRow.Task` and fill-in intents. Produces the same collection row API with 8 dp card padding and the task sizing formula from the spec; task view-type count remains one.

- [ ] Add `paddedTaskRowsKeepDueAndCompleteInsideBounds` in the renderer test: long mixed-script title, overdue/today dates, en/fr/fa, 1.0/2.0 text, 180/373 dp widths, and a tokenless row. Tests were not added or run at the user's request; acceptance remains NOT RUN.
- [ ] Extend existing completed-task/retry/reapply tests to confirm new backgrounds do not change action identity or retain unsafe rows/old feedback. NOT RUN at the user's request.
- [x] Bind explicit app-theme row backgrounds, add padding and preserve the existing due-label and `canComplete` logic. Recalculate available text width for 24 dp shell inset, 16 dp row inset, action width and 8 dp gap; use `max(48, titleHeight + dueHeight) + 16` for row height.
- [ ] Rerun renderer and intent classes; inspect light/dark task fixture captures at recorded/expanded/compact bounds, including fallback rather than a clipped card. NOT RUN at the user's request. Suggested commit message: `feat: add inset task widget cards`.

### T34-C: Habits cards and saved progress

**Status:** Implemented (validation pending). Gradle tests and emulator checks were not run at the user's request.

**Modify:** `app/src/main/res/layout/widget_habit_row.xml`, `widget_habit_row_inline.xml`; `WidgetRenderer.kt`; `WidgetLayoutPolicy.kt`; `WidgetCollectionFactory.kt`.

**Create:** `app/src/main/res/layout/widget_habit_row_dark.xml`, `widget_habit_row_inline_dark.xml`; `app/src/main/res/drawable/widget_habit_progress.xml`, `widget_habit_progress_dark.xml` (rounded native horizontal progress layer lists).

**Test:** `app/src/test/java/com/luma/lifeos/widget/WidgetLayoutPolicyTest.kt`; existing `app/src/androidTest/java/com/luma/lifeos/widget/WidgetRendererAcceptanceTest.kt`, `WidgetCollectionFactoryTest.kt`.

**Interfaces:** Consumes A's roles and `WidgetRow.Habit` unchanged. Produces `WidgetLayoutPolicy.habitProgressPermille(count: Int, target: Int): Int` and `widget_habit_row_progress` in all four habit layouts. Factory advertises four habit view types and one task type. Existing render/collection entry points remain compatible.

- [ ] Add JVM cases for progress: `(0,5)=0`, `(1,5)=200`, `(2,8)=250`, `(5,5)=1000`, `(6,5)=1000`, `(-1,5)=0`, `(1,0)=0`, `(1,-1)=0`, `(Int.MAX_VALUE,Int.MAX_VALUE)=1000`. Tests were not added or run at the user's request; acceptance remains NOT RUN.
- [ ] Add `savedHabitProgressRendersAndClearsAcrossStates` plus inline/stacked layout and factory assertions for recycled state, accessibility focus, stable identity and paging. Tests were not added or run at the user's request; acceptance remains NOT RUN.
- [x] Build the four layout variants with matching IDs, native determinate ProgressBar max 1000, themed progress drawables and explicit app-theme selection. Bind progress/visibility on every row. Use a vertical outer text block and an inner inline/stacked title/count block; mirror progress with RTL layout direction.
- [x] Add 16 dp row inset to width calculation and `max(48, titleCountHeight + 4 + 8) + 16` to height measurement. Keep no-fit compact fallback and bounded measurement. Increase habit view types without changing stable IDs, paging, tokens, receivers or workers.
- [ ] Rerun policy/renderer/factory/contrast suites and inspect zero/partial/reached/skipped in both themes. NOT RUN at the user's request. Suggested commit message: `feat: show saved habit progress in widget cards`.

### T34-D: Preview consistency and launcher acceptance

**Modify:** `app/src/main/res/layout/widget_preview_tasks.xml`, `widget_preview_habits.xml`; other `widget_preview_capture.xml` or `widget_quick_capture*.xml` only if changed shared roles expose an actual visual defect; existing `app/src/androidTest/java/com/luma/lifeos/widget/WidgetVisualBaselineTest.kt`.

**Create during execution:** `docs/luma-redesign/evidence/<actual-run-date>/t34/acceptance.md` and referenced captures. Update `docs/UI_REDESIGN_TASKS.md`, `docs/luma-redesign/route-coverage.md` and the T34 evidence link with actual results.

**Interfaces:** Consumes A/B/C and all five spec acceptance rows. Produces reproducible renderer evidence plus separate real-host evidence. No new app route or widget provider.

- [x] Align fictional picker previews with the new card/header/progress roles. Preserve reconfiguration and legacy Quick Capture placements/actions. The focused preview assertion passes for day/night layouts; broader configuration/settings/intent coverage is NOT RUN.
- [x] Extend the synthetic matrix exporter to render both resolved app themes and collection rows, including saved progress cases. It writes to unique `t34/run-*` output directories and records app override/system appearance separately; it identifies offscreen output explicitly.
- [x] Run the offscreen matrix on API 36: both kinds and theme overrides, nine counts, three baseline bounds, French/Persian 1.3/2.0 font scales at narrow/flat bounds, edge progress rows and feedback reapply. The test passed, but connected-test teardown removed the app and its private external-files output; no screenshot bundle remains. Offscreen output does not verify real scrolling beyond page one.
- [x] Run the focused layout-policy unit test, full app unit-test task, debug APK/Android-test APK packaging and lint. Focused API 36 `WidgetVisualBaselineTest` and picker-preview assertion pass. The broader connected class list below is NOT RUN.
- [ ] On isolated API 28, 31 and 36 launcher test installs, place/resize/rotate both widgets, scroll beyond the first page, verify target sizes/TalkBack/RTL and trigger Complete/+1/Undo/Retry. Verify theme overrides and locale refresh with existing widget IDs. API 28/31 images are unavailable. The API 36 connected-test teardown removed the preexisting Luma package and may have removed its app data, so no further device operations were performed.
- [x] Write the partial acceptance ledger with W34-1 through W34-5, build/device identity and precise NOT RUN entries. Screenshot paths could not be retained because the connected-test teardown removed the app-private output directory; side-by-side screenshot review remains NOT RUN. Keep WG08/T30–T33 statuses independent. Do not commit D as `test: record launcher widget redesign acceptance` until the remaining launcher evidence is complete.

## Verification commands

Run from the repository root with JDK 17 and an isolated configured emulator/device. The focused renderer and preview classes passed on API 36; the broader connected suite and API 28/31/launcher matrix remain NOT RUN. The Gradle connected-test teardown uninstalled the target package, so do not rerun on this AVD.

```bash
./gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.widget.WidgetLayoutPolicyTest'
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.widget.WidgetColorContrastAcceptanceTest,com.luma.lifeos.widget.WidgetRendererAcceptanceTest,com.luma.lifeos.widget.WidgetCollectionFactoryTest,com.luma.lifeos.widget.WidgetIntentsTest,com.luma.lifeos.widget.WidgetConfigurationContentTest,com.luma.lifeos.widget.WidgetSettingsRouteTest,com.luma.lifeos.widget.WidgetVisualBaselineTest
```

For a focused red/green step, pass only its owning class in the same instrumentation property; policy tests use the first command. Existing unrelated baseline failures must be distinguished from new failures. Installing successfully or capturing offscreen views is insufficient for D's launcher acceptance.

## Coverage and handoff

W34-1 → A; W34-2 → B; W34-3 → C; W34-4 → A/B/C geometry plus D matrix; W34-5 → D and A contrast tests. Every review-focus case has an owning task. Completion requires no missing acceptance row; do not mark T34 complete while a required launcher/API/locale check is NOT RUN.

T34-A through T34-D source changes are present. Local unit/build/lint checks and focused API 36 offscreen renderer/picker-preview checks pass. T34 remains implemented (validation pending) because real launcher/API, accessibility, durable screenshot and side-by-side visual acceptance remain NOT RUN. See the [partial acceptance record](../../luma-redesign/evidence/2026-10-22/t34/acceptance.md); do not mark T34 complete or commit D until remaining launcher evidence is captured on an isolated test install.
