# T30 — Shared visual system and app shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan does not request subagent delegation.

**Goal:** Replace the existing green-led appearance with a coherent warm-paper/obsidian system and editorial typography across existing components.

**Architecture:** Existing repositories/view models retain data ownership and LifeOsApp retains navigation. Shared T30 roles/components provide presentation; the spec defines any optional new interface. Keep current product behavior.

**Tech Stack:** Kotlin, Compose Material 3, Navigation Compose, JUnit/Compose instrumentation, Gradle and JDK 17.

**Spec:** [Corresponding specification](../specs/2026-10-22-t30-shared-visual-system-design.md).

## Global constraints

- Native Android, Kotlin, Compose Material 3 and Navigation Compose; keep minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- Offline operation; no account, cloud sync, AI service, telemetry, new permissions or schema migration in required visual work.
- System/Light/Dark selection and English/French/Persian support remain available offline.
- Preserve existing route IDs, callbacks, saved state, data semantics, loading/error/retry/undo and accessibility tags unless an optional task explicitly changes its own contract.
- Use actual saved data; never ship reference people, dates, percentages, measurements, quotes or charts as user records.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL and system animation scale.
- Optional tasks default to deferred and are not required for redesign acceptance. No optional feature is authorized for implementation by this planning document.

## Review focus

- ThemeSettingsTest: select Light/Dark/System, recreate, assert selected option and resolved appearance. Inspect bundled font rendering offline, including French accents and Persian/Latin mixes.
- All essential color-pair measurements meet 4.5:1 text/3:1 large-text and non-text thresholds; no app-local palette bypasses shared roles.
- PrimaryNavigationAccessibilityTest and PageInsetsContractTest retain five destinations, selected semantics and system-bar ownership.
- At 320 dp/2.0 and 840 dp, inspect every primary action, snackbar and final row in both themes; retain focus and 48 dp targets.
- Long French/Persian at 320 dp/2.0, restored context and system/IME insets must retain every action and exact value.

## Files and interfaces

- Modify: `app/src/main/java/com/luma/lifeos/ui/theme/Theme.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/common/LumaUiTokens.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/common/LumaUiComponents.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/common/AppPageScaffold.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/navigation/PrimaryNavigationBar.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt`.
- Create: `app/src/main/java/com/luma/lifeos/ui/theme/LumaTypography.kt`; bundled licensed resources under `app/src/main/res/font/` and notices under `app/src/main/assets/font-licenses/`.

Consumes: current signatures/state/callbacks in these files and shared T30 roles. Produces: the linked spec’s screen contract. Preserve existing composable signatures except extensions explicitly named in that spec; new named interfaces use the exact types defined there. String additions go into all three existing values/values-fr/values-fa catalogs with matching placeholders/plurals.

### Regression owners

- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/settings/ThemeSettingsTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/ui/PrimaryNavigationAccessibilityTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/ui/PageInsetsContractTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/language/LanguageFlowTest.kt`.

### Task 1: Theme and typography

**Interfaces:** Implement this unit against the file map and contract above; no independent repository observations for decorative content. Existing state/callback contracts remain authoritative.

- [ ] Establish the baseline with existing fixtures and a before capture. For behavior/semantics additions, add the focused regression scenario before production changes: ThemeSettingsTest: select Light/Dark/System, recreate, assert selected option and resolved appearance. Inspect bundled font rendering offline, including French accents and Persian/Latin mixes.
- [ ] Run the selected test; a new behavior test must fail for its intended missing behavior. Existing visual-only baseline tests should remain green; do not invent failing padding/token tests.
- [x] Update role colors in Theme.kt using the reference brief’s corrected Timeline light container/foreground, primaryFixed, outline and inverse pairs; verify consumers use paired roles and preserve pale chips via primaryFixed. Introduce LumaTypography.kt and bundled res/font resources with license notices, and pass typography/shapes to MaterialTheme. Preserve theme preference and system-bar behavior.
- [ ] Rerun the affected regression tests and inspect: All essential color-pair measurements meet 4.5:1 text/3:1 large-text and non-text thresholds; no app-local palette bypasses shared roles.
- [ ] Record results/evidence for this unit and review the diff against every owned spec requirement. Commit the reviewed implementation/tests with a task-specific message when executing this plan.

### Task 2: Shared components and shell

**Interfaces:** Implement this unit against the file map and contract above; no independent repository observations for decorative content. Existing state/callback contracts remain authoritative.

- [ ] Establish the baseline with existing fixtures and a before capture. For behavior/semantics additions, add the focused regression scenario before production changes: PrimaryNavigationAccessibilityTest and PageInsetsContractTest retain five destinations, selected semantics and system-bar ownership.
- [ ] Run the selected test; a new behavior test must fail for its intended missing behavior. Existing visual-only baseline tests should remain green; do not invent failing padding/token tests.
- [x] Update LumaUi token use, LumaSectionSurface/LumaSectionHeader/LumaChoiceRow and PrimaryNavigationBar treatment. Keep existing signatures and tags. Add factual local-data copy in all three string catalogs only where the shell displays it.
- [ ] Rerun the affected regression tests and inspect: At 320 dp/2.0 and 840 dp, inspect every primary action, snackbar and final row in both themes; retain focus and 48 dp targets.
- [ ] Record results/evidence for this unit and review the diff against every owned spec requirement. Commit the reviewed implementation/tests with a task-specific message when executing this plan.

## Planned verification and completion

Run focused classes while iterating; extend their fixtures rather than duplicate the same state contract in a new suite. Also run any new test classes named in the spec. Commands are planned checks and have not run in this documentation change.

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.settings.ThemeSettingsTest,com.luma.lifeos.language.LanguageFlowTest,com.luma.lifeos.ui.PrimaryNavigationAccessibilityTest,com.luma.lifeos.ui.PageInsetsContractTest
./gradlew :app:assembleDebug :app:lintDebug
```

Expected: BUILD SUCCESSFUL, zero selected test failures/lint errors, actual device results for instrumentation. Missing prerequisites or skipped device tasks are NOT RUN with the exact reason. Confirm each acceptance scenario in the spec against implementation and evidence; record commands, build/device configuration and screenshots in the T33 ledger. Do not repeat passing checks unless changes/failures justify it.

## Handoff

Depends on: None. Execute after dependency/spec review and hand results to T33. T30 implementation is recorded below; release acceptance remains pending.

## Execution record — 2026-10-22

Status: **implemented (validation pending)**. Both production implementation steps above are done. Unchecked baseline, regression, capture and commit steps are deliberately not claimed complete. The user requested implementation without Gradle tests or emulator use and requested a commit message; no Gradle command, emulator or commit was run/created. Build/lint and rendered acceptance were also left NOT RUN. Existing regression coverage is unchanged because product behavior and semantics remain unchanged.

- Task 1: shared palette, fixed roles, variable-font typography and shapes implemented. Source contrast and font metadata/coverage audit PASS. Font rendering and theme-restoration regression execution NOT RUN.
- Task 2: shared surfaces/headings/choice icons, five-tab pills and capture FAB implemented. Header height accounts for two scaled lines; existing inset ownership and measured FAB clearance remain. Static contract/diff review done; runtime layout/accessibility/regressions NOT RUN.
- Pre-flight: both tasks share MaterialTheme roles and typography. The existing Review partial-week badge consumes the old pale primaryContainer; it now uses primaryFixed/onPrimaryFixed. The reflective reading section uses a neutral surface to retain contrast for its existing primary actions and secondary text.
- Ruling: provide fixed-role extensions on ColorScheme — the pinned Compose BOM selects Material 3 1.3.2, before native fixed roles; keep dependencies unchanged. A future Material upgrade can replace the extensions.
- Ruling: adapt AppSectionHeading, the journal title in shared EventCard, ReviewHeader and shared HabitProgress as palette/typography consumers — this avoids leaving headings sans or progress fill indistinct after shared changes; no callbacks/data/state contracts change. Progress fill/track contrast improves from 1.630/1.873 to 5.616/6.832 (light/dark) using surfaceContainerHighest, with the specified 8 dp track and unchanged 180 ms animation.
- Ruling: keep the supplied working checkout and generate a commit message without creating a commit — this keeps the requested result reviewable in the workspace. All edits remain uncommitted.
- Final review: self-review of the working diff against the spec and cached dependency APIs; no delegation, as directed by this plan. Source checks cannot establish build or device acceptance.

Evidence, audit reproduction, exact skipped checks and remaining acceptance: [T30 review](../../luma-redesign/evidence/2026-10-22/t30-review.md).
