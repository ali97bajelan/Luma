# UX Review 09: Focused Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn Settings into a concise configuration hub and keep day-based saved usage browsing in the Review flow.

**Architecture:** Keep the Settings primary destination as a hub. Add focused Language, Backup, Privacy, and Integrations child routes. Retain the existing usage and health controls in Integrations while removing the day browser from Settings; Review continues to open its existing read-only daily usage route.

**Tech Stack:** Kotlin, Jetpack Compose Navigation, Material 3, lifecycle-aware state, current usage/health/backup/privacy services, Android string resources.

**Spec:** [docs/superpowers/specs/2026-10-13-ux-review-08-09-15-design.md](../specs/2026-10-13-ux-review-08-09-15-design.md), Settings hub section.

## Global Constraints

- Keep the five primary destinations and hide the bottom bar on all Settings child routes.
- Preserve import/permission, retention, backup/restore, privacy reset, and language behavior.
- Show a Settings row only when its backing service/route is available; Language is always present.
- Remove saved daily usage browsing from Settings; Review daily usage remains read-only and does not request or import provider data.
- Localize new labels in English, Persian, and French; preserve accessibility state and RTL behavior.
- Preserve user data and unrelated untracked files; do not change database schema or dependencies.

## Review Focus

- Optional dependencies absent: hub omits unavailable rows and still shows Language. Test in `SettingsHubFlowTest.hubShowsOnlyAvailableSections`.
- Returning from a child route: Back returns to the Settings hub with navigation restored. Test in `SettingsHubFlowTest.focusedPageBackReturnsToHub`.
- Imports disabled or provider access revoked: integration status and access management remain usable and saved records remain intact. Keep assertions in `UsageSettingsFlowTest` and `HealthSettingsFlowTest`.
- Saved usage day view: opening it from Review performs no provider query or import. Test in `ReviewUsageFlowTest.savedUsageDetailsAreReadOnly`.
- Language, backup restore confirmation, and privacy reset confirmation: actions stay on their focused page and retain cancellation safeguards. Keep existing language, backup, and privacy flow tests on the new routes.

---

### Task 1: Add the Settings hub and focused routes

**Files:**
- Create: `app/src/main/java/com/luma/lifeos/ui/settings/SettingsRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-fa/strings.xml`
- Modify: `app/src/main/res/values-fr/strings.xml`
- Test: `app/src/androidTest/java/com/luma/lifeos/settings/SettingsHubFlowTest.kt`
- Update: `app/src/androidTest/java/com/luma/lifeos/language/LanguageFlowTest.kt`
- Keep: `app/src/androidTest/java/com/luma/lifeos/backup/BackupSettingsFlowTest.kt` and `app/src/androidTest/java/com/luma/lifeos/privacy/PrivacySettingsFlowTest.kt` as focused confirmation-safety tests.

**Interfaces:**
- Consumes: optional `BackupService`, optional `AppDataReset`, optional usage/health integrations, and existing Language/Backup/Privacy composables.
- Produces: `SettingsRoute(backupAvailable: Boolean, privacyAvailable: Boolean, integrationsAvailable: Boolean, onLanguage: () -> Unit, onBackup: () -> Unit, onPrivacy: () -> Unit, onIntegrations: () -> Unit)` and `SettingsSectionPage(@StringRes title: Int, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit)`.

- [x] **Step 1: Add failing hub navigation tests.** Assert Language is always shown; Backup, Privacy, and Integrations appear only for true availability flags; clicking each visible row opens its tagged page; Back returns to the hub.
- [ ] **Step 2: Run the focused instrumentation tests.** Run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.settings.SettingsHubFlowTest`; expect the new route and tags to be missing.
- [x] **Step 3: Implement the Settings hub and child-page header.** Use localized, full-width labeled rows with accessible click roles and stable `settings_*` tags.
- [x] **Step 4: Wire Settings navigation in `LifeOsApp`.** Keep `Destination.SETTINGS` as the only primary Settings route; add child routes for Language, Backup, Integrations, and the existing Privacy screen. Pass only available destinations and make every Back action pop to the hub.
- [x] **Step 5: Remove Settings-specific cards and Places link from `FoundationScreen`.** Keep its generic empty destination presentation for any remaining use.
- [x] **Step 6: Add hub/section titles and row descriptions to English, Persian, and French.** Check child headers and Back labels in RTL.
- [ ] **Step 7: Run navigation, language, backup, and privacy flow tests.** Run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.settings.SettingsHubFlowTest,com.luma.lifeos.language.LanguageFlowTest,com.luma.lifeos.backup.BackupSettingsFlowTest,com.luma.lifeos.privacy.PrivacySettingsFlowTest`; expected result is green flows. Run `./gradlew :app:assembleDebug`.
- [ ] **Step 8: Commit the task.** Commit only Settings navigation, route, resource, and test changes.

### Task 2: Separate integration configuration from saved usage browsing

**Files:**
- Modify: `app/src/main/java/com/luma/lifeos/ui/usage/UsageSettingsRoute.kt`
- Create: `app/src/main/java/com/luma/lifeos/ui/settings/IntegrationsSettingsRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-fa/strings.xml`
- Modify: `app/src/main/res/values-fr/strings.xml`
- Update: `app/src/androidTest/java/com/luma/lifeos/usage/UsageSettingsFlowTest.kt`
- Update: `app/src/androidTest/java/com/luma/lifeos/health/HealthSettingsFlowTest.kt`
- Test: `app/src/androidTest/java/com/luma/lifeos/usage/ReviewUsageFlowTest.kt`

**Interfaces:**
- Consumes: existing `UsageSettingsViewModel`, `UsageSettingsCard` controls, `HealthSettingsCard`, and `ReviewUsageRoute`.
- Produces: `UsageIntegrationSettingsCard(integration: UsageIntegration, zone: ZoneId)` with configuration/status/actions only, and `IntegrationsSettingsRoute(usage: UsageIntegration?, health: HealthIntegration?, zone: ZoneId, onBack: () -> Unit)` containing only available integration cards.

- [x] **Step 1: Add failing integration and usage-separation tests.** Verify toggles, permission status, import, refresh, health retention, and error states remain present in the focused Integrations page; assert Settings hub and Integrations do not expose `day_previous`, usage timeline/totals chips, or daily report rows. Update `UsageSettingsFlowTest` so persistence is checked from the repository and daily display is covered by `ReviewUsageFlowTest`; remove obsolete Settings-to-Places assertions.
- [ ] **Step 2: Run the focused usage and health instrumentation tests.** Run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.usage.UsageSettingsFlowTest,com.luma.lifeos.health.HealthSettingsFlowTest`; expect old navigation/content assumptions to fail.
- [x] **Step 3: Extract `UsageIntegrationSettingsCard`.** Keep the current ViewModel and interaction semantics while removing date observation, day navigation, and timeline/totals rendering from the Settings content.
- [x] **Step 4: Implement `IntegrationsSettingsRoute`.** Render available usage and health configuration cards inside the focused page and preserve status, retry, permissions, retention, and import behavior. Update `HealthSettingsFlowTest` to enter this route and use its focused screen tag.
- [x] **Step 5: Wire the Integrations child route.** Pass the current zone and nullable services; do not add a usage history link here.
- [x] **Step 6: Add a read-only usage-route test with a counting source.** Seed a stored day, open `ReviewUsageRoute`, assert saved totals/sessions display, and assert the source query count and import count remain zero.
- [ ] **Step 7: Run integration and Review usage flows.** Run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.usage.UsageSettingsFlowTest,com.luma.lifeos.health.HealthSettingsFlowTest,com.luma.lifeos.usage.ReviewUsageFlowTest`; expected result is green tests. Run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug`.
- [ ] **Step 8: Commit the task.** Commit only Settings integration separation, localized copy, and associated tests.

## Execution notes — 2026-10-13

Implemented the Settings scope (finding 09) as the next task after finding 08.
Existing workspace changes and CRLF files were preserved. No commit was created.

- Added a short Settings hub with Language always available and conditional
  Backup, Privacy, and Integrations destinations. Optional child routes are
  registered only when their backing services exist.
- Focused child pages hide primary navigation and return to the hub. Language
  recreation, selected-day preservation, and child Back navigation have test coverage.
- Extracted import-only usage configuration. Settings no longer observes or
  renders a usage day. Health configuration and existing backup/privacy
  confirmation safeguards are retained.
- Added counting-source coverage for read-only Review usage, session/aggregate
  evidence, opt-out persistence, and records retained after revoked access.
- English/Persian/French resource XML, resource coverage, Settings format arguments,
  and added-line whitespace checks pass. Android unit, build, and instrumentation
  commands were attempted but could not start because no JDK is configured in PATH
  or JAVA_HOME. The previously checked temporary offline tool cache lacks AGP 8.7.3.
- Pending device checks: focused Settings/navigation/language/integration flows,
  existing backup/privacy confirmation flows, English/French/Persian RTL,
  font scale 1.3, keyboard/TalkBack rows and Back actions, and Review usage rendering.

### Release dependency on finding 15

The required removal of the Settings-to-Places link leaves the Places catalog
without a navigation entry until the People/Places selector from finding 15 is
implemented. Do not release this task independently of that follow-up. Existing
place creation through capture pickers and linked-place details through Review
remain available, but general catalog browsing is unavailable in this intermediate
state. Both cases in `PlaceCaptureTest` still depend on the retired `settings_places`
entry and require navigation updates with finding 15. This is an Important review
finding about the plan sequence, not a passing test result or a resolved issue.

Re-run the unchecked verification steps in a configured Android environment.
