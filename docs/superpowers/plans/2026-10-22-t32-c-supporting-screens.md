# T32-C — More, catalogs, search, lists and settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan does not request subagent delegation.

**Goal:** Carry the new visual language to supporting browse and settings screens without introducing new directory features.

**Architecture:** Existing repositories/view models retain data ownership and LifeOsApp retains navigation. Shared T30 roles/components provide presentation; the spec defines any optional new interface. Keep current product behavior.

**Tech Stack:** Kotlin, Compose Material 3, Navigation Compose, JUnit/Compose instrumentation, Gradle and JDK 17.

**Spec:** [Corresponding specification](../specs/2026-10-22-t32-c-supporting-screens-design.md).

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

- SearchFlowTest, ActiveSearchFiltersTest, PlaceCatalogFlowTest and navigation suites: independent catalog queries restore, filters/results/Create/Edit/Back remain reachable.
- Inspect empty/error/populated lists, details/history navigation, all source/usage/memory-review browse routes and both catalog tabs.
- SettingsHubFlowTest, BackupSettingsFlowTest, PrivacySettingsFlowTest and WidgetSettingsRouteTest: selectors, progress, confirmations, rejected integrations and restore outcomes remain available.
- Inspect factual local-data/unencrypted-ZIP copy, all supported languages and in-app widget configuration in both themes; launcher layouts are unchanged.
- Long French/Persian at 320 dp/2.0, restored context and system/IME insets must retain every action and exact value.

## Files and interfaces

- Modify: `app/src/main/java/com/luma/lifeos/ui/more/MoreRoute.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/people/PeopleRoute.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/places/PlacesRoute.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/search/SearchRoute.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/search/SearchFilters.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/search/SearchOptionPicker.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/HabitsScreen.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/todo/TodoListScreen.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/settings/SettingsRoute.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/settings/IntegrationsSettingsRoute.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/theme/ThemeSettingsCard.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/backup/BackupSettingsCard.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/privacy/PrivacySettingsRoute.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/usage/UsageSettingsRoute.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/health/HealthSettingsCard.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/widgets/WidgetSettingsRoute.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/review/ReviewSourcesRoute.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/review/ReviewUsageRoute.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/memory/MemoryReviewRoute.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/widget/WidgetConfigurationActivity.kt`; `ui/LifeOsApp.kt` only for existing language/configuration wiring.

Consumes: current signatures/state/callbacks in these files and shared T30 roles. Produces: the linked spec’s screen contract. Preserve existing composable signatures except extensions explicitly named in that spec; new named interfaces use the exact types defined there. String additions go into all three existing values/values-fr/values-fa catalogs with matching placeholders/plurals.

### Regression owners

- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/AppNavigationTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/SearchFlowTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/ActiveSearchFiltersTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/PlaceCatalogFlowTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/PeopleCaptureTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/todo/TodoNavigationTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/settings/SettingsHubFlowTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/backup/BackupSettingsFlowTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/privacy/PrivacySettingsFlowTest.kt`.
- Extend only when behavior/semantics change: `app/src/androidTest/java/com/luma/lifeos/widget/WidgetSettingsRouteTest.kt`.

### Task 1: Browse and catalog routes

**Interfaces:** Implement this unit against the file map and contract above; no independent repository observations for decorative content. Existing state/callback contracts remain authoritative.

- [ ] Establish the baseline with existing fixtures and a before capture. For behavior/semantics additions, add the focused regression scenario before production changes: SearchFlowTest, ActiveSearchFiltersTest, PlaceCatalogFlowTest and navigation suites: independent catalog queries restore, filters/results/Create/Edit/Back remain reachable.
- [ ] Run the selected test; a new behavior test must fail for its intended missing behavior. Existing visual-only baseline tests should remain green; do not invent failing padding/token tests.
- [ ] Update More, catalogs, search, Habits and task lists using shared components; restyle existing catalog tabs rather than replacing their state. Include source/usage/memory-review browsing.
- [ ] Rerun the affected regression tests and inspect: Inspect empty/error/populated lists, details/history navigation, all source/usage/memory-review browse routes and both catalog tabs.
- [ ] Record results/evidence for this unit and review the diff against every owned spec requirement. Commit the reviewed implementation/tests with a task-specific message when executing this plan.

### Task 2: Settings and integration routes

**Interfaces:** Implement this unit against the file map and contract above; no independent repository observations for decorative content. Existing state/callback contracts remain authoritative.

- [ ] Establish the baseline with existing fixtures and a before capture. For behavior/semantics additions, add the focused regression scenario before production changes: SettingsHubFlowTest, BackupSettingsFlowTest, PrivacySettingsFlowTest and WidgetSettingsRouteTest: selectors, progress, confirmations, rejected integrations and restore outcomes remain available.
- [ ] Run the selected test; a new behavior test must fail for its intended missing behavior. Existing visual-only baseline tests should remain green; do not invent failing padding/token tests.
- [ ] Apply shared treatments to Settings, theme/language, backup/privacy, integrations and in-app widget settings/configuration. Keep status, disclosure and confirmation wording factual and preserve existing flows.
- [ ] Rerun the affected regression tests and inspect: Inspect factual local-data/unencrypted-ZIP copy, all supported languages and in-app widget configuration in both themes; launcher layouts are unchanged.
- [ ] Record results/evidence for this unit and review the diff against every owned spec requirement. Commit the reviewed implementation/tests with a task-specific message when executing this plan.

## Planned verification and completion

Run focused classes while iterating; extend their fixtures rather than duplicate the same state contract in a new suite. Also run any new test classes named in the spec. Commands are planned checks and have not run in this documentation change.

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.AppNavigationTest,com.luma.lifeos.SearchFlowTest,com.luma.lifeos.ActiveSearchFiltersTest,com.luma.lifeos.PlaceCatalogFlowTest,com.luma.lifeos.PeopleCaptureTest,com.luma.lifeos.todo.TodoNavigationTest,com.luma.lifeos.settings.SettingsHubFlowTest,com.luma.lifeos.backup.BackupSettingsFlowTest,com.luma.lifeos.privacy.PrivacySettingsFlowTest,com.luma.lifeos.widget.WidgetSettingsRouteTest
./gradlew :app:assembleDebug :app:lintDebug
```

Expected: BUILD SUCCESSFUL, zero selected test failures/lint errors, actual device results for instrumentation. Missing prerequisites or skipped device tasks are NOT RUN with the exact reason. Confirm each acceptance scenario in the spec against implementation and evidence; record commands, build/device configuration and screenshots in the T33 ledger. Do not repeat passing checks unless changes/failures justify it.

## Handoff

Depends on: T30. Execute after dependency/spec review and hand results to T33. This plan does not begin app implementation.
