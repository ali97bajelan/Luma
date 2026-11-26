# Luma Android Performance and Reliability Audit

**Date:** 2026-11-27  
**Scope:** Read-only review of startup, navigation, Today and Timeline Compose paths, focus timer state, Room-backed storage setup, and startup theme/lock rendering. Source files were not changed.  
**Limits:** No emulator, device, instrumentation test, screenshot, profiler trace, or rendering/timing measurement was used.

## Findings

### P2 — Photo filesystem setup runs synchronously during process startup

**Evidence:** [LumaApplication.kt:165](../../app/src/main/java/com/luma/lifeos/LumaApplication.kt#L165) constructs the protected focus repository through lazy properties, and [LumaApplication.kt:217](../../app/src/main/java/com/luma/lifeos/LumaApplication.kt#L217) initializes the focus runtime in Application.onCreate. That path forces [LumaStorage.kt:52](../../core/data/src/main/kotlin/com/luma/lifeos/data/LumaStorage.kt#L52) to construct ManagedPhotoFiles. Its initializer calls canonicalFile, isDirectory, and mkdirs, then checks that directory creation succeeded: [ManagedPhotoFiles.kt:19](../../core/data/src/main/kotlin/com/luma/lifeos/data/media/ManagedPhotoFiles.kt#L19).

**Trigger and impact:** Every process startup reaches this setup on the main thread. Slow filesystem metadata or directory creation can delay startup. If directory creation fails, the check throws before the app UI appears, including when photos are not being used.

**Proposed fix:** Defer photo-directory initialization until photo storage is first used, run filesystem work on an I/O dispatcher, and surface failures from the photo operation instead of failing application startup.

### P2 — A running Focus timer invalidates the app shell every second

**Evidence:** The resumed-activity timer loop calls updateDisplayedTime once per second and updates runtime state: [FocusRuntime.kt:278](../../app/src/main/java/com/luma/lifeos/focus/FocusRuntime.kt#L278), [FocusRuntime.kt:297](../../app/src/main/java/com/luma/lifeos/focus/FocusRuntime.kt#L297). FocusViewModel combines runtime state into its UI state at [FocusViewModel.kt:201](../../app/src/main/java/com/luma/lifeos/focus/FocusViewModel.kt#L201), and LifeOsApp collects that full state at its root: [LifeOsApp.kt:308](../../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt#L308). The timer value is passed to Today and Review content at [LifeOsApp.kt:1188](../../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt#L1188).

**Trigger and impact:** While a Focus session is running and the activity is visible, each timer tick invalidates the root composition. The broad invalidation is confirmed by the state flow; resulting dropped frames or lag are unmeasured.

**Proposed fix:** Separate frequently changing elapsed time from navigation and authorization state. Collect the timer value closer to the timer display, while the app shell observes only the slower-changing state it needs.

### P2 — FAB measurement feeds back into content padding after layout

**Evidence:** LifeOsApp reads captureFabHeight to compute captureBottomClearance at [LifeOsApp.kt:775](../../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt#L775), then writes the measured value from onGloballyPositioned at [LifeOsApp.kt:1009](../../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt#L1009). The resulting clearance is passed into Timeline and Review content at [LifeOsApp.kt:1137](../../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt#L1137) and [LifeOsApp.kt:1272](../../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt#L1272).

**Trigger and impact:** On first display of the FAB, or when its height changes with text, font scale, or window size, the content can first compose with zero clearance and then update its bottom padding after layout. This guarantees another composition when the measured height differs; a visible content shift is possible but was not rendered here.

**Proposed fix:** Reserve clearance using an appropriate stable design token, or measure the FAB and content in one layout instead of feeding layout measurement back through Compose state.

### P2, plausible — Recreated Today content starts with Loading again

**Evidence:** Navigation saves and restores destination state at [LifeOsApp.kt:989](../../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt#L989). Today creates its observation inside a date-keyed composition and collects with Loading as the initial value at [TodayRoute.kt:128](../../app/src/main/java/com/luma/lifeos/ui/today/TodayRoute.kt#L128). TodayScreen renders a spinner for that state at [TodayScreen.kt:200](../../app/src/main/java/com/luma/lifeos/ui/today/TodayScreen.kt#L200).

**Trigger and impact:** If navigation recreates Today content on return to the tab, the screen starts collecting a fresh feed and may briefly replace recent entries with a loading indicator, even for the same date. The state reset is visible in code; frequency and perceptibility are unverified.

**Proposed fix:** Retain the last successful feed per date in a destination-scoped state holder or while-subscribed state flow. Continue clearing or separating state when the selected date or authorization changes.

### P3, plausible — The app-lock gate may appear briefly during cold start

**Evidence:** AppLockCoordinator starts with a default LockSnapshot whose load status is UNKNOWN: [AppLockCoordinator.kt:26](../../app/src/main/java/com/luma/lifeos/lock/AppLockCoordinator.kt#L26). Policy initialization is launched asynchronously at [LumaApplication.kt:217](../../app/src/main/java/com/luma/lifeos/LumaApplication.kt#L217). LifeOsApp makes the gate visible while the status is not READY at [LifeOsApp.kt:536](../../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt#L536) and overlays AppLockGate at [LifeOsApp.kt:2164](../../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt#L2164).

**Trigger and impact:** If the first frame occurs before policy initialization finishes, a full-screen gate may appear and then disappear when the stored policy does not require a lock. The timing depends on the device and was not rendered.

**Proposed fix:** Keep a non-sensitive launch surface visible until the initial policy check completes, then show the gate when the resolved policy requires it.

### P3, plausible — Platform launch theme is fixed to Light while Compose supports Dark

**Evidence:** The Android theme inherits Theme.Material.Light.NoActionBar and sets a light status-bar flag: [themes.xml:3](../../app/src/main/res/values/themes.xml#L3). Compose can select a dark palette with a dark background at [Theme.kt:65](../../app/src/main/java/com/luma/lifeos/ui/theme/Theme.kt#L65), and updates edge-to-edge system-bar styles in a SideEffect at [Theme.kt:137](../../app/src/main/java/com/luma/lifeos/ui/theme/Theme.kt#L137).

**Trigger and impact:** On a cold launch using the Dark app theme or system dark mode, the platform window or splash may use a light background before Compose draws. Whether that produces a visible flash is unverified.

**Proposed fix:** Make the launch/window background follow the selected app theme so the platform surface matches the Compose palette before first draw.

### P3, unmeasured — Timeline uses BoxWithConstraints inside every lazy row

**Evidence:** Timeline items have stable keys and content types at [TimelineRoute.kt:161](../../app/src/main/java/com/luma/lifeos/ui/timeline/TimelineRoute.kt#L161), but each TimelineRailItem creates BoxWithConstraints at [TimelineRoute.kt:202](../../app/src/main/java/com/luma/lifeos/ui/timeline/TimelineRoute.kt#L202).

**Trigger and impact:** Visible and prefetched rows create subcomposition work during scrolling. This may contribute to scroll jank, especially on slower devices; no profiling was performed.

**Proposed fix:** Compute the compact-layout decision once at the list level and pass the resulting rail and node dimensions to each row.

## Existing JVM checks

Ran the targeted local JVM task with these selectors:

    :app:testProductionDebugUnitTest
      --tests com.luma.lifeos.ui.today.TodayStateTest.startsLoadingThenShowsEmptyForA23HourLocalDay
      --tests com.luma.lifeos.ui.today.TodayStateTest.queryFailureShowsErrorAndAFreshCollectionCanRetry
      --tests com.luma.lifeos.ui.today.TodayStateTest.lifecycleCancellationIsNotShownAsAnError
      --tests com.luma.lifeos.ui.timeline.TimelineViewModelTest.aLateOldDayResultCannotAppearUnderTheNewDate
      --tests com.luma.lifeos.ui.timeline.TimelineViewModelTest.failedInitialObservationAndLoadCanRetry
      --tests com.luma.lifeos.ui.timeline.TimelineViewModelTest.stoppedScreensDoNotRefreshUntilForegroundObservationRestarts
      --tests com.luma.lifeos.focus.FocusNavigationGuardTest

**Result:** 9 passed, 0 skipped, 0 failed. The checks cover feed loading, retry and cancellation, late Timeline results, lifecycle refresh, and focus navigation guard state. They do not verify rendering smoothness or flicker.

Report: [Gradle JVM test results](../../app/build/reports/tests/testProductionDebugUnitTest/index.html).

## Acceptance status

| Area | Status | Basis |
|---|---|---|
| Startup | **Fail** | Startup reaches main-thread photo filesystem setup; failure can throw before the UI. |
| Responsiveness | **Unverified** | Timer-driven root invalidations and per-row subcomposition are code-level risks; no frame measurements were taken. |
| Screen flicker | **Unverified** | Today loading, lock-gate, FAB-padding, and launch-theme transitions require rendering to assess. |
| Reliability | **Fail** | Photo-directory setup has a startup exception path. Tested feed retry and cancellation behavior passed. |
