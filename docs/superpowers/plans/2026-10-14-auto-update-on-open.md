# Automatic App Usage and Steps Updates on Open Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import the latest available usage and Health Connect steps automatically when Luma launches or returns to active use.

**Architecture:** Extend the existing app-root foreground hooks with small suspend functions that refresh access, read current integration state and import when eligible. Run each hook independently once per RESUMED activation and reuse existing syncers, Room transactions and observable screens.

**Tech Stack:** Kotlin, coroutines, AndroidX Lifecycle/Compose, existing UsageStats and Health Connect adapters, Room, JUnit 4 and Compose instrumentation.

**Spec:** [Automatic updates on open](../specs/2026-10-14-auto-update-on-open-design.md).

**Status:** Implementation requested on 2026-10-14 and completed on branch `feat/auto-update-on-open`; Android device and native-provider acceptance remain pending because no test device was attached.

## Global constraints

- Keep `minSdk = 28`, `compileSdk = 35`, `targetSdk = 35`, JDK 17, AGP 8.7.3, Kotlin 2.2.10 and Compose BOM 2025.08.01.
- Keep AndroidX Lifecycle 2.9.0, coroutines 1.10.2 and Health Connect 1.1.0-beta01; add no dependency or database migration.
- Add no permission, network access, telemetry, AI service, sensor listener or background health read.
- Existing usage and health import switches remain the opt-in; opening Luma must never enable either integration or launch permission UI.
- Keep English, Persian and French available offline; update import explanations in all three resource bundles.
- Preserve existing import identities, history coverage, checkpoints, seven-date health reconciliation and saved health import timezone.
- Missing observations remain unavailable; explicit zero remains zero; failures and cancellation retain the last successfully committed data.

## Review focus

- A stale ACCESS_REQUIRED status after a new permission grant must be refreshed before deciding eligibility: Task 1 post-refresh-state test; Task 2 return-transition test.
- Previous ERROR or usage INTERVAL_CHANGED must recover without a tight retry loop: Task 1 status matrix; Task 2 failed-activation retry test.
- A rapid RESUMED → STARTED → RESUMED transition must cancel old work and allow a new attempt: Task 2 pending-import lifecycle test.
- One unavailable or slow provider must not delay its counterpart or browsing: Task 2 independent-effects test with a suspended fake provider.
- Activity recreation or an overlapping manual request must preserve record identity and current-day totals: Task 2 Room identity test plus existing syncer serialization suites.

---

## File structure and ownership

| File | Responsibility |
| --- | --- |
| `app/src/main/java/com/luma/lifeos/ui/usage/UsageForegroundRefresh.kt` | Existing composable hook plus a usage-specific access/eligibility/import function. |
| `app/src/main/java/com/luma/lifeos/ui/health/HealthForegroundRefresh.kt` | Existing composable hook plus a health-specific access/eligibility/import function. |
| `app/src/test/java/com/luma/lifeos/ui/usage/UsageForegroundRefreshTest.kt` — new | Usage attempt policy and cancellation tests with a fake integration. |
| `app/src/test/java/com/luma/lifeos/ui/health/HealthForegroundRefreshTest.kt` — new | Health attempt policy and cancellation tests with a fake integration. |
| `app/src/androidTest/java/com/luma/lifeos/integration/ForegroundRefreshFlowTest.kt` — new | Controlled lifecycle, independent effects and Room-to-Today updates with fake providers. |
| `app/src/main/res/values/strings.xml`, `values-fa/strings.xml`, `values-fr/strings.xml` | Existing import explanations updated in all supported languages. |
| `README.md`, `docs/PRIVACY.md`, `docs/VALIDATION.md` | Describe foreground imports and record actual verification results. |

`LifeOsApp.kt`, `LumaApplication.kt`, the Settings ViewModels, syncers, adapters, workers and Room repositories already provide the required wiring and contracts. Read them for integration context; the selected design does not require changes to their signatures or behavior. Keep the Health Connect permission callback’s existing access refresh.

### Task 1: Testable foreground import attempts

**Files:**

- Modify: `app/src/main/java/com/luma/lifeos/ui/usage/UsageForegroundRefresh.kt`.
- Modify: `app/src/main/java/com/luma/lifeos/ui/health/HealthForegroundRefresh.kt`.
- Create: `app/src/test/java/com/luma/lifeos/ui/usage/UsageForegroundRefreshTest.kt`.
- Create: `app/src/test/java/com/luma/lifeos/ui/health/HealthForegroundRefreshTest.kt`.

**Interfaces:**

- Consumes: `UsageIntegration.refreshAccess(): Unit`, `observeState(): Flow<UsageImportState>`, `importNow(): UsageSyncOutcome`; analogous `HealthIntegration` methods returning `Flow<HealthState>` and `HealthStatus`.
- Produces: `internal suspend fun refreshUsageOnOpen(integration: UsageIntegration): Unit` in the usage hook file; `internal suspend fun refreshHealthOnOpen(integration: HealthIntegration): Unit` in the health hook file.
- Both functions propagate exceptions, including cancellation. Task 2 owns lifecycle handling and non-cancellation exception containment.

- [x] **Step 1: Write JVM policy tests using `runTest` and fake integrations.** Give each fake a `MutableStateFlow` current state, `calls: MutableList<String>`, `imports: Int`, configurable state replacement during refresh, and an optional deferred import gate. Health enabled states must specify `importZone = ZoneId.of("UTC")`. A usage fake’s unused `observeDaily` can return `flowOf(null)`.

The central ordering assertion is:

```kotlin
@Test fun readyUsageChecksAccessThenImports() = runTest {
    val integration = RecordingUsageIntegration(
        UsageImportState(enabled = true, status = UsageSyncStatus.READY)
    )
    refreshUsageOnOpen(integration)
    assertEquals(listOf("refreshAccess", "observeState", "importNow"), integration.calls)
    assertEquals(1, integration.imports)
}
```

Add the equivalent health test. In each test class, define `everyStatusUsesTheEligibilityTable`: iterate all enum entries with enabled true and assert one import only for the spec’s eligible set; repeat with enabled false and assert zero imports. Define `eligibilityUsesTheStateAfterRefresh`: start ACCESS_REQUIRED, publish READY during refresh and assert one import; reverse the states and assert zero. Define `optOutDuringRefreshSkipsImport`: publish disabled during refresh and assert zero. Define `refreshOrStateFailureNeverStartsImport`: throw at each stage and assert the same exception propagates and imports remain zero. Define `cancellationPropagatesFromRefreshStateAndImport`: cancellation at each stage propagates; a gated import cancelled by its caller does not complete or emit a fake success.

- [x] **Step 2: Run the new focused tests and verify the initial failure.**

```sh
bash gradlew --offline :app:testDebugUnitTest \
  --tests 'com.luma.lifeos.ui.usage.UsageForegroundRefreshTest' \
  --tests 'com.luma.lifeos.ui.health.HealthForegroundRefreshTest'
```

Expected before implementation: compilation fails on the missing `refreshUsageOnOpen` / `refreshHealthOnOpen` functions. An environment/dependency failure is not the intended red result.

- [x] **Step 3: Implement the two internal suspend functions without changing the composable hooks yet.** Call `refreshAccess()`, then `observeState().first()`, then `importNow()` only if enabled and eligible. Usage eligibility is `{READY, NO_DATA, ERROR, INTERVAL_CHANGED}`; health eligibility is `{READY, NO_DATA, ERROR}`. Do not branch on `lastSync`, add a mutex, catch exceptions, recalculate totals or loop over state emissions.

- [x] **Step 4: Rerun the focused command and verify both classes pass.** Also run existing `UsageSettingsViewModelTest` and `HealthSettingsViewModelTest` with the same Gradle task and their full package names; their existing manual action behavior must pass unchanged.

- [x] **Step 5: Record the policy implementation as one reviewable change.** Limit the diff to the two helper files and two JVM suites. If commits are requested for implementation, use `feat: add foreground import attempt policies` and stage only these paths.

### Task 2: Run imports once per active app visit and verify observed updates

**Files:**

- Modify: both foreground hook files from Task 1.
- Create: `app/src/androidTest/java/com/luma/lifeos/integration/ForegroundRefreshFlowTest.kt`.
- Reference: `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt`, `app/src/main/java/com/luma/lifeos/LumaApplication.kt`.
- Regression: `core/domain/src/test/kotlin/com/luma/lifeos/domain/usage/UsageSyncerTest.kt`, `core/domain/src/test/kotlin/com/luma/lifeos/domain/health/HealthSyncerTest.kt`.
- Regression: `core/data/src/androidTest/kotlin/com/luma/lifeos/data/UsagePersistenceTest.kt`, `core/data/src/androidTest/kotlin/com/luma/lifeos/data/HealthPersistenceTest.kt`.
- Regression: `app/src/androidTest/java/com/luma/lifeos/usage/UsageSettingsFlowTest.kt`, `app/src/androidTest/java/com/luma/lifeos/health/HealthSettingsFlowTest.kt`, `app/src/androidTest/java/com/luma/lifeos/summary/DailySummaryFlowTest.kt`, `app/src/androidTest/java/com/luma/lifeos/usage/ReviewUsageFlowTest.kt`.

**Interfaces:**

- Consumes: Task 1’s two suspend functions and the existing `@Composable fun UsageForegroundRefresh(integration: UsageIntegration)` / `HealthForegroundRefresh(integration: HealthIntegration)` signatures.
- Produces: each root hook invokes its attempt once per RESUMED lifecycle entry, waits for cancellation afterward, and propagates lifecycle cancellation. No new production type or route parameter is introduced.

- [x] **Step 1: Add controlled-lifecycle instrumentation tests against the current hooks.** Use `createComposeRule`, a custom `LifecycleOwner` backed by `LifecycleRegistry`, and `CompositionLocalProvider(LocalLifecycleOwner provides owner)`. Drive `registry.currentState` on the main thread through `compose.runOnIdle`. Place both hooks in this provider; keep integration identities stable across recomposition. Use recording integrations for lifecycle counts and real syncers/in-memory Room for persistence cases.

Create these test methods and assertions:

| Test | Assertions |
| --- | --- |
| `importsOnResumeAndAfterReturnButNotWhileOnlyStarted` | At CREATED/STARTED both imports = 0; first RESUMED both = 1; STARTED → RESUMED both = 2. |
| `recompositionAndInternalContentChangesDoNotRepeatAttempts` | Change unrelated Compose state, replace visible child content and show an in-app dialog while RESUMED; both counts stay 1. |
| `permissionGrantIsImportedAfterResumeWithoutManualAction` | Initially required access produces zero provider queries; pause to STARTED, grant fake access, then resume; query and commit occur without Import. Repeat with denial and assert saved history is unchanged. |
| `unavailableAndDisabledIntegrationsDoNotReadProviders` | Both off, usage-only, health-only, locked/denied usage and unavailable/update-required health obey F2; a visible test action remains usable. |
| `slowOrFailedIntegrationDoesNotStopItsCounterpart` | Suspend usage import and assert health completes; reverse the roles. Throw a non-cancellation exception in one hook and assert the other completes and visible content remains actionable. |
| `pausingCancelsPendingAutomaticImportWithoutRecordingFailure` | Begin a deferred import; move RESUMED → STARTED; its cancellation flag becomes true, no success/ERROR is recorded; resume and a new attempt completes. |
| `failureWaitsUntilNextActivationToRetry` | Return ERROR from a fake import; recomposition keeps imports = 1; STARTED → RESUMED produces imports = 2 and can succeed. |
| `automaticRoomImportsRefreshTodayAndRetainStableIds` | Real syncers save initial usage/steps on resume; changed provider values update Today on the next resume; recreating the owner reimports without new identities. |

For the Room case, use UTC, a fixed clock at `2026-10-14T12:00:00Z`, `RoomUsageRepository`, `RoomHealthRepository`, `RoomDailySummaryRepository` and `RoomLifeEventRepository` on the same in-memory database. Fake usage reports one bucket for `org.example.reader`, starting at local midnight, with `120_000L` then `180_000L` duration. Fake health reports `1_000L` then `1_250L` for today and null for previous dates. The fake usage scheduler performs no real WorkManager scheduling. Mount `TodayRoute` beside the hooks with the fixture date and zone; open `summary_details` for the step metric.

Assert `summary_usage` contains `2 min` then `3 min`; `summary_activity_steps_count_HEALTH_CONNECT` contains the locale-formatted `1_000` then `1_250` counts. Capture source record and activity child IDs after the first import and assert those IDs remain equal after subsequent imports. Assert exactly one usage event and one current-day health event remain. Finish by observing each repository and confirming the committed totals, so rendered strings alone cannot mask stale persistence. Dispose lifecycle/content before closing the database.

- [ ] **Step 2: Compile and run the new flow suite before wiring the helpers.**

```sh
bash gradlew --offline :app:assembleDebugAndroidTest
bash gradlew --offline :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.integration.ForegroundRefreshFlowTest
```

Expected on a configured test device: failures because the hooks only refresh status and do not import. If no test device is available, record the device run as pending; compilation alone does not validate lifecycle behavior.

- [x] **Step 3: Connect the hooks to their Task 1 functions using `repeatOnLifecycle(Lifecycle.State.RESUMED)`.** Preserve their existing integration/lifecycle `LaunchedEffect` keys and separate effects. Replace the access-only call with the corresponding attempt function. Keep a wrapper catch that rethrows `CancellationException` and contains other Exceptions; call `awaitCancellation()` after the attempt/catch. Keep root mounting in `LifeOsApp` and shared instances in `LumaApplication` unchanged. Do not add a second import to the Health Connect result callback.

- [ ] **Step 4: Rerun the flow suite to green and run existing domain/adapter regressions.**

```sh
bash gradlew --offline :core:domain:test \
  :integration:usage:testDebugUnitTest :integration:health:testDebugUnitTest \
  :app:testDebugUnitTest
```

Expected: successful Gradle result with the new tests passing. Existing `concurrentImportsSerializeAndConflictingNativeRowsNeverCommit`, `queuedImportsSerializeProviderReads`, opt-out, cancellation, clock-safety, midnight/DST and null/zero tests must continue to pass. Run the named existing app and data instrumentation suites on the configured test device to check manual actions, Room identities, reconciliation and derived-display regressions; report skipped device suites explicitly.

- [x] **Step 5: Record the lifecycle wiring and flow suite as one reviewable change.** If commits are requested, use `feat: refresh usage and steps when Luma becomes active`; include only the two hooks and new instrumentation suite.

### Task 3: Explain automatic imports and record verification

**Files:**

- Modify: `app/src/main/res/values/strings.xml`.
- Modify: `app/src/main/res/values-fa/strings.xml`.
- Modify: `app/src/main/res/values-fr/strings.xml`.
- Modify: `README.md`, `docs/PRIVACY.md`, `docs/VALIDATION.md`.
- Regression: `app/src/test/java/com/luma/lifeos/language/TranslationCatalogTest.kt` and the existing usage/health Settings explanation flows.

**Interfaces:**

- Consumes: existing `R.string.usage_access_explanation` and `R.string.health_explanation`, displayed both in import details and permission explanations.
- Produces: spec F5’s exact English copy, equivalent Persian/French resource values, accurate foreground-import documentation and recorded verification evidence. No new resource key, control or UI state is required.

- [x] **Step 1: Update both existing explanations in all three languages.** Use spec F5’s English values verbatim. Translations must say imports run when enabled and permitted on open/return, preserve on-device retention and manual import availability, preserve usage background updates, and preserve the seven-calendar-date Health Connect scope and available-source-data qualification. Escape Android string-resource characters correctly.

- [x] **Step 2: Update README and privacy documentation.** Describe launch/return imports, optional access, saved history during failures, manual retry, health foreground-only reads and unchanged usage background scheduling. Do not claim continuous device-sensor counting or native acceptance that has not been run.

- [x] **Step 3: Run the existing translation check, builds and lint.**

```sh
bash gradlew --offline :app:testDebugUnitTest \
  --tests 'com.luma.lifeos.language.TranslationCatalogTest'
bash gradlew --offline :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Expected: successful Gradle result; no missing/invalid translations or newly introduced lint errors. Use existing Settings tests to verify that the explanation still appears before explicit permission actions and can be cancelled. Inspect English/Persian/French explanations for wrapping and RTL readability using their existing UI; no new test suite is needed solely for copy changes.

- [ ] **Step 4: Verify native launch/return behavior and record evidence.** On a test install with optional access enabled and known source data, launch and return from Home; check usage/steps and last-sync update without Import. Repeat with a native Usage Access return and a Health Connect permission result; record whether RESUMED transitions and automatic imports occur. Revoke one source and verify retained history, independent counterpart updates and usable navigation. Leave unavailable native-provider checks pending with the reason. Record commands, actual outcomes and unresolved checks in `docs/VALIDATION.md`; update this plan’s checkboxes only for completed work.

- [x] **Step 5: Review the final diff against A1–A10 and deliver the implementation.** Report behavior, checks and any pending native acceptance. If commits are requested, use `docs: explain automatic foreground imports` for this task’s resource/document changes. Do not stage existing release outputs, generated schema files or unrelated work.

## Requirement coverage

| Spec requirements | Owning tasks |
| --- | --- |
| F1 / A1–A3: launch, return, once per activation and safe recreation | Task 2 |
| F2 / A4–A6: eligibility, permission state, recovery and opt-in | Tasks 1 and 2 |
| F3 / A7–A8: independence, cancellation, opt-out and serialized triggers | Task 2 plus existing syncer/Room regressions |
| F4 / A2, A8–A10: stable records, existing time/data semantics and observed UI updates | Task 2 plus existing domain/adapter/Room regressions |
| F5 / A10: localized explanation, manual controls and recorded validation | Task 3 |

## Planning validation

The plan is based on the current hooks, integration contracts, syncers, shared application instances, Room observation/invalidation and Settings resources inspected on 2026-10-14. It introduces no coordinator, new integration contract or persistence policy. JVM tests, instrumentation APK compilation, debug build and app lint completed. Android instrumentation runtime and native Usage Access/Health Connect acceptance remain pending because no test device was attached.
