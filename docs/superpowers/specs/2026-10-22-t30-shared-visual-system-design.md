# T30 — Shared visual system and app shell specification

Date: 2026-10-22  
Status: Implemented (validation pending), 2026-10-22. Source audit passed; Gradle/build/device and rendered acceptance checks remain NOT RUN. See [T30 evidence](../../luma-redesign/evidence/2026-10-22/t30-review.md). This is not release acceptance.
Depends on: None

## Intent and sources

Replace the existing green-led appearance with a coherent warm-paper/obsidian system and editorial typography across existing components.

References: All six PNGs and six distinct HTML documents, including the corrected timeline-light.html. Read the [shared reference brief](../../luma-redesign/README.md) and [task index](../../UI_REDESIGN_TASKS.md) with this specification. The brief resolves inconsistencies and provides common tokens; it is normative for every task.

## Global constraints

- Native Android, Kotlin, Compose Material 3 and Navigation Compose; keep minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- Offline operation; no account, cloud sync, AI service, telemetry, new permissions or schema migration in required visual work.
- System/Light/Dark selection and English/French/Persian support remain available offline.
- Preserve existing route IDs, callbacks, saved state, data semantics, loading/error/retry/undo and accessibility tags unless an optional task explicitly changes its own contract.
- Use actual saved data; never ship reference people, dates, percentages, measurements, quotes or charts as user records.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL and system animation scale.
- Optional tasks default to deferred and are not required for redesign acceptance. No optional feature is authorized for implementation by this planning document.

## Screen and behavior contract

- Use the role mapping and component measurements in the reference brief, including corrected Timeline light primaryContainer/onPrimaryContainer `#ba4d28`/`#fff1ed` and tertiaryContainer/onTertiaryContainer `#8b693b`/`#fff1e5`. Pale terracotta chips use primaryFixed/onPrimaryFixed rather than overwriting primaryContainer. Keep `LifeOsTheme(content: @Composable () -> Unit)` and the stored `AppTheme` values unchanged. Define typography and shapes centrally, not inside routes.
- Create `ui/theme/LumaTypography.kt` for bundled Newsreader headings, bundled Plus Jakarta Sans Latin body/data, and an offline Persian-capable fallback. Retain font license notices with the resources; use platform sans until a redistributable resource is available, record the visual gap and do not download fonts at runtime.
- Use serif page/section and journal titles; use sans for values, dates, labels, controls and Persian runs. Do not force serif Latin glyphs onto Persian text. Text must scale without fixed-height clipping.
- Keep the five destinations Today, Timeline, Habits, Review, More. Apply selected pills, muted surfaces and shared icon sizing to the existing bar. Four tabs are O01.
- Use existing title/search/settings entry points. Omit the mock profile avatar and unmapped tune controls. If showing a local-data indicator, use factual localized “Stored on this device”; it must not claim encryption or encrypted ZIP backups.
- Preserve edge-to-edge ownership in `LifeOsTheme`/app scaffold, including system bar contrast and measured capture-FAB clearance. Do not copy HTML fixed positioning, disabled browser zoom, hover-only controls or tiny target sizes.

## Implementation boundaries

- `app/src/main/java/com/luma/lifeos/ui/theme/Theme.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/common/LumaUiTokens.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/common/LumaUiComponents.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/common/AppPageScaffold.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/navigation/PrimaryNavigationBar.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt` — existing integration surface.

Existing public/internal composable signatures remain compatible except additions explicitly named above. Existing repositories/view models own data and mutations; decorative components receive values and callbacks. New files named in the contract are proposed implementation files, not files already created by this documentation change. Consult the paired plan for test ownership.

## Acceptance scenarios

1. Theme switches persist through recreation and System follows the system appearance; all primary destinations still resolve.
2. All ordinary/selected/error/disabled/focus surfaces are distinguishable in both themes; measured role pairs meet the shared contrast thresholds.
3. Font resources render offline; French accents and mixed Persian/Latin names and units are readable without truncating actions.
4. The final row, snackbar and capture FAB remain clear of system bars and each other at all required widths.

## Verification and completion

Use focused existing regression coverage plus visual/manual checks for the changed surfaces. Add tests for changed behavior and meaningful state/semantics risks; do not add tests that merely assert padding or duplicate token assignments. Required font/contrast/RTL/inset checks are acceptance work, not a reason to add fake data. Record PASS/FAIL/NOT RUN and evidence in the T33 ledger. No checks are claimed to have run in this specification.

[Implementation plan](../plans/2026-10-22-t30-shared-visual-system.md)
