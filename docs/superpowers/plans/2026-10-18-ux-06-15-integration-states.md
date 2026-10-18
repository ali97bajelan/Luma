# Integration and Metric States Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement UX-06/15 with truthful access/import/missing-data states.
**Architecture:** Read-only domain access probes, one shared availability observer and saved-data presentation mapping.
**Tech Stack:** Kotlin coroutines/Flow, existing integrations, Compose, JUnit.
**Spec:** [Integration states design](../specs/2026-10-18-ux-06-15-integration-states-design.md)

**Execution status (2026-10-18):** Production implementation and required fake-interface updates are in the working tree. A source review's findings on in-flight opt-out, observer recovery, a stale instrumented call site and metric-specific rating copy were addressed. Gradle compilation was blocked before project configuration by restricted sandbox socket access. No test tasks or manual acceptance were run; implementation checkboxes are marked individually and verification/commit steps remain open. A commit message was generated separately; no commit was created.

## Global Constraints

- Keep minSdk 28, compileSdk 35, targetSdk 35, JDK 17, AGP 8.7.3, Kotlin 2.2.10 and Compose BOM 2025.08.01.
- Add no product dependency, database migration, permission, network access, telemetry or AI service.
- English, Persian and French remain available offline; preserve user-authored text, stored timestamps and timezone rules.
- UX-01, UX-02 and UX-12 are excluded; preserve their existing behavior.
- Minimum interactive touch target: 48 × 48 dp; do not reduce configured text scale.

## Review Focus

- Off + granted/denied remains distinguishable without record reads (Task 1).
- Access probe races/lifecycle cancellation cannot revert state (Task 1).
- Grant return must not import/enable, including Health with switch off (Task 2).
- Historical zero survives disabled access and failed import (Task 3).
- Manual activity and missing comparison values avoid false provider reasons/deltas (Task 3).

### Task 1: Independent access probes and shared observer

**Files:** Modify core/domain/src/main/kotlin/com/luma/lifeos/domain/usage/UsageContracts.kt, UsageSyncer.kt and health/HealthContracts.kt, HealthSyncer.kt. Create app/src/main/java/com/luma/lifeos/ui/integrations/IntegrationAvailability.kt and IntegrationAvailabilityViewModel.kt. Modify ui/LifeOsApp.kt. Extend core/domain/src/test/kotlin/com/luma/lifeos/domain/usage/UsageSyncerTest.kt and health/HealthSyncerTest.kt; add app/src/test/java/com/luma/lifeos/ui/integrations/IntegrationAvailabilityViewModelTest.kt. Update all integration fakes found with rg.
**Interfaces:** New suspend access(): UsageAccess / HealthAccess. Availability types exactly as spec; ViewModel.state: StateFlow<IntegrationAvailabilityState>, refreshAccess(): Unit. Share instance at app navigation scope.

- [ ] Add tests accessWhileOffDoesNotReadRecords (GRANTED/REQUIRED returned faithfully; query/schedule/setEnabled/commit calls all 0), configurationNotLoadedDoesNotMeanOff (enabled == null), configurationObserverFailureStaysUnknown (configurationFailed == true), and probeFailureIsNotDenied (access == ERROR). Null integration maps UNAVAILABLE. Test coalesced resume/manual refresh and disposal/cancellation; no stale result overwrites latest.
- [ ] Run `bash gradlew :core:domain:test :app:testDebugUnitTest --offline`; new API/tests must fail before implementation.
- [x] Implement side-effect-free probes and lifecycle-aware shared observer; keep current syncer locks, health revisions, scheduling/foreground refresh rules. Update fakes explicitly; no guessed default access.
- [ ] Rerun domain/observer tests including existing disable-during-import cases; expected zero failures.
- [ ] Commit with `feat(integrations): separate access probes from import opt-in`.

### Task 2: Settings state/action matrix (UX-15)

**Files:** Modify ui/usage/UsageSettingsRoute.kt, UsageSettingsViewModel.kt, ui/health/HealthSettingsCard.kt, HealthSettingsViewModel.kt, ui/settings/IntegrationsSettingsRoute.kt, ui/LifeOsApp.kt; values*/strings.xml. Extend ui/usage/UsageSettingsViewModelTest.kt, ui/health/HealthSettingsViewModelTest.kt and device usage/UsageSettingsFlowTest.kt, health/HealthSettingsFlowTest.kt.
**Interfaces:** Settings cards consume IntegrationAvailability plus onRefreshAccess: () -> Unit from Task 1; existing vm.setEnabled(Boolean), importNow(), refresh() retain their import-control roles.

- [ ] Add matrix tests for every spec row; opening card/probe/grant return performs zero import calls, never changes enabled; explicit Health grant can launch while off and then only refresh access. ERROR access does not become Not allowed. LastSync remains prior success on failure.
- [ ] Run targeted unit/device classes and observe matrix failures.
- [x] Implement separate status labels, one primary action, secondary controls/retention copy, optional unknown saved-range omission and truthful Health wording. Add resume refresh using shared observer; preserve permission explanation and intent-unavailable recovery.
- [ ] Run targeted suites plus existing integration domain tests; verify imports-off + saved history and in-flight disable fixtures.
- [ ] Commit with `feat(settings): clarify integration access and import choices`.

### Task 3: Missing metric mapping and wiring (UX-06)

**Files:** Create ui/integrations/MetricAvailability.kt; modify ui/summary/DailySummaryCard.kt, ui/today/TodayRoute.kt, TodayScreen.kt, ui/review/ReviewOverview.kt, ReviewValues.kt, ReviewSignals.kt, WeeklyReviewRoute.kt and LifeOsApp.kt. Add app/src/test/java/com/luma/lifeos/ui/integrations/MetricAvailabilityTest.kt; extend summary/DailySummaryFlowTest.kt and review/WeeklyReviewFlowTest.kt.
**Interfaces:** fun metricAbsence(loading: Boolean, readFailed: Boolean, hasSavedData: Boolean, availability: IntegrationAvailability?): MetricAbsence? (null when saved value is present). Mood/manual/optional-rating absence is selected by the owning surface instead of passing fake provider availability. Add explicit absence: MetricAbsence? = null to ReviewOverviewMetric for styling/semantics instead of localized string equality. Add callbacks onCheckIn: () -> Unit, onIntegrations: () -> Unit and existing saved-read Retry; new availability arguments are explicit at production call sites. ReviewScale/reviewDuration callers handle null with a metric-specific label rather than a generic fallback.

- [ ] Add table assertions for precedence: read error beats off; hasSavedData=true with zero yields null even if off/required; enabled=null yields LOADING or CONFIGURATION_ERROR; no data + off yields IMPORTS_OFF; enabled/allowed/read-success yields NO_SAVED_DATA; checking yields CHECKING_ACCESS; import/probe errors remain distinct. Test manual data unaffected by Health state, mood with no energy/stress samples yields NO_RATINGS, and missing previous metric has no delta.
- [ ] Run `bash gradlew :app:testDebugUnitTest --tests '*MetricAvailabilityTest' --offline`; observe initial failure.
- [x] Implement mapper and period-specific text/actions, pass shared state/callbacks through Today and Review. Retain deterministic summaries/cache keys, live habit observation, exact zeros and source navigation.
- [ ] Run app/domain JVM tests and affected summary/Review/integration device classes under isolated package. Assert Check in opens existing editor and Integration settings opens existing route; neither summary opening nor retry invokes native import.
- [ ] Commit with `feat(summary): explain missing metrics without hiding saved data`.
