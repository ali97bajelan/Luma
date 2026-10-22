# T32-C — More, catalogs, search, lists and settings specification

Date: 2026-10-22  
Status: Proposed / pending implementation. This document is a planning artifact, not release acceptance.  
Depends on: T30

## Intent and sources

Carry the new visual language to supporting browse and settings screens without introducing new directory features.

References: No direct screen mockups; extrapolate shared surfaces from Today/Timeline/Review. Read the [shared reference brief](../../luma-redesign/README.md) and [task index](../../UI_REDESIGN_TASKS.md) with this specification. The brief resolves inconsistencies and provides common tokens; it is normative for every task.

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

- More retains Tasks, People & Places and Settings. Restyle the existing People/Places tabs, independent saved search queries, Create and history/edit routes; search and catalog switching already exist and are required preservation, not a new optional feature.
- Keep search filter chips and selectors, no-result/error/loading states, task Open/Completed lists and habit browse/history controls. Do not add these states to Today merely because they exist elsewhere.
- Use neutral list surfaces, editorial page headings and compact source/date labels. Keep original list order, counts, empty guidance, accessible names and exact values.
- Keep System/Light/Dark, app language, widget settings, integrations, backup/restore and privacy controls discoverable in their existing destinations. Preserve destructive confirmation copy and routes.
- Preserve factual offline/local-data and unencrypted ZIP backup disclosures. Markdown export, cloud sync, new encryption, account/profile and new permissions are excluded; the current folder provides no designs requiring them.
- Launcher RemoteViews remain governed by existing widget acceptance docs. Include the in-app widget settings/configuration UI in the new theme; do not silently replace launcher layouts or their independent color resources.

## Implementation boundaries

- `app/src/main/java/com/luma/lifeos/ui/more/MoreRoute.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/people/PeopleRoute.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/places/PlacesRoute.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/search/SearchRoute.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/search/SearchFilters.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/search/SearchOptionPicker.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/habits/HabitsScreen.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/todo/TodoListScreen.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/settings/SettingsRoute.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/settings/IntegrationsSettingsRoute.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/theme/ThemeSettingsCard.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/backup/BackupSettingsCard.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/privacy/PrivacySettingsRoute.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/usage/UsageSettingsRoute.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/health/HealthSettingsCard.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/widgets/WidgetSettingsRoute.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/review/ReviewSourcesRoute.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/review/ReviewUsageRoute.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/memory/MemoryReviewRoute.kt` — existing integration surface.

Existing public/internal composable signatures remain compatible except additions explicitly named above. Existing repositories/view models own data and mutations; decorative components receive values and callbacks. New files named in the contract are proposed implementation files, not files already created by this documentation change. Consult the paired plan for test ownership.

Additional existing UI surface: `app/src/main/java/com/luma/lifeos/widget/WidgetConfigurationActivity.kt` owns the in-app configuration content; restyle its app-facing controls while leaving launcher RemoteViews unchanged. `ui/LifeOsApp.kt` retains language-route wiring.

## Acceptance scenarios

1. More and all catalog/search/list routes retain working selection, query restoration, Create/Edit/Back and no-results behavior in all three languages.
2. Settings expose the same choices and safeguards; rejected/unavailable integration states and backup/restore progress/results remain visible.
3. Source/usage/memory-review routes preserve pagination, period context and record navigation.
4. No unseen screen is described as a supplied design; route coverage distinguishes shared-style extrapolation and out-of-scope launcher surfaces.

## Verification and completion

Use focused existing regression coverage plus visual/manual checks for the changed surfaces. Add tests for changed behavior and meaningful state/semantics risks; do not add tests that merely assert padding or duplicate token assignments. Required font/contrast/RTL/inset checks are acceptance work, not a reason to add fake data. Record PASS/FAIL/NOT RUN and evidence in the T33 ledger. No checks are claimed to have run in this specification.

[Implementation plan](../plans/2026-10-22-t32-c-supporting-screens.md)
