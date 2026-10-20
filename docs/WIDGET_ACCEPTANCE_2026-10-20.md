# Home-screen widget acceptance record — 20 October 2026

## Decision

**NO-GO for release acceptance.** WG01–WG07 are implemented and have useful API 36 / Pixel Launcher coverage, but WG-AC-01–12 are only partially verified. API 28, a second launcher family, resize/orientation and locale/theme/font matrices, TalkBack/keyboard traversal, process/restore races and performance profiling remain open.

## Build and test identity

- Branch and base revision: `main`, `ec7b8cbb98c1a05411dcebd6a5c6169ebf493184`. The working tree contains uncommitted changes; no release APK was built from a commit.
- Final debug APK: `app/build/outputs/apk/debug/app-debug.apk`, version `0.1.0` (1), SHA-256 `53a972c4fa666f8c022484f4bca9abb3055ff9285fe4570a27ade97042a886f7`.
- AVD: `Medium_Phone_API_36.1`; Android 16 / API 36, `sdk_gphone64_x86_64`; Pixel/Nexus Launcher `com.google.android.apps.nexuslauncher`.
- Display: 1080×2400 px, 420 dpi. Locale `en-US`, font scale 1.0, light mode, portrait at the time of the screenshots.
- The host advertised Quick capture at 3×2, Habits at 4×3 and Tasks at 4×3. `OPTION_APPWIDGET_SIZES` included about 276×210 dp portrait and 578×109 dp landscape bounds; combining min width with min height had selected the wrong Quick capture layout. The renderer now uses each offered size.
- Screenshots: [Quick capture grid](ui-ux-audit/evidence/widgets/quick-capture-api36.png) and [Habits/Tasks after Undo](ui-ux-audit/evidence/widgets/habits-tasks-undo-api36.png). The visible names are emulator test fixtures.

## Verification performed

- The full cross-module offline Gradle JVM/build/Android-test-packaging/lint gate passed after the widget-store fix. After the final renderer and localized accessibility-label fixes, `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleDebugAndroidTest` and `:app:lintDebug` passed again. The app unit tests include the three size-policy cases.
- The full `core:data` Android instrumentation suite passed **147/147** on the emulator, including the new storage regressions that retain Undo after task rows disappear and habit targets are reached.
- `WidgetRendererAcceptanceTest` passed **1/1** on the emulator. It reapplies RemoteViews after completion and proves that the old row is removed while the task-specific Undo action remains.
- The full app instrumentation suite ran **171 tests, 166 passed and 5 failed** against the preserved emulator app database. Failures: `MemoryCaptureTest.journalPromotionAndUnmarkingRetainOriginalBody`, `MoodCaptureTest.capturesMultipleEmotionsRestoresDraftAndEditsAndDeletesFromTimeline`, `HabitCaptureFlowTest.historyCorrectionMovesLogToAnotherDateWithoutChangingItsIdentity` (closed Room connection), and both `TodayTasksFlowTest` cases (one lacked `day_next`; one timed out waiting for the empty-tasks state). Memory and Mood were rerun separately and still failed in this state. These results are recorded without attributing all failures to the existing fixtures.
- The Gradle `connectedDebugAndroidTest` task could not resolve the uncached offline dependency `com.android.tools.utp:android-test-plugin-host-additional-test-output:31.7.3`. The built test APKs were installed and run with `adb shell am instrument` instead.
- Native launcher checks: all three providers appeared in the Pixel Launcher picker. Quick capture opened the Mood, Memory and Journal editors. Habits and Tasks first placement showed their required configuration screen with summary mode off by default. The Tasks **Add task** button opened a new task with today’s due date.
- Habits: opted into details, logged `Undo habit` with **+1**, saw **Saved** and **Undo**, then used Undo; the row returned to 0/1 with +1 available. A target-reached `Widget habit` had no +1 action.
- Tasks: opted into details, created a due-today test task, and used **Complete**. The completed row disappeared, **Undo** remained available with the task-specific accessible label, and Undo restored the open row. Add, data changes and completion refreshed the launcher widget.
- The app unit/build/lint and focused widget tests pass; this does not clear the full app-suite failures or satisfy the remaining host/accessibility/performance matrix.

## Acceptance criteria status

These are criterion-level statuses. A partial pass on one API/launcher does not close a criterion that requires a wider matrix.

| Criterion | Status | Observed evidence and remaining work |
| --- | --- | --- |
| WG-AC-01 — previews, placement/removal, multiple instances and safe initial states on API 28 and 35/36 | **PARTIAL** | All three picker entries and first-placement flows were exercised on API 36 Pixel Launcher; summary mode was the default for data widgets. API 28, another API, remove/re-add and multiple instances of each kind remain untested. |
| WG-AC-02 — cold/warm Mood, Memory and Journal routing, Cancel, tap-time and draft preservation | **PARTIAL** | Each Quick capture button opened the matching editor on the launcher. Full cold/warm/recreation, repeated taps, Cancel and existing-draft preservation are not covered by a widget-specific test. |
| WG-AC-03 — habit eligibility, ordering, counts, skip, zone and stale-write rejection | **PARTIAL** | Live +1, target behavior and exact Undo passed; in-memory storage regression passed. Skip, zone/midnight, concurrent delivery and stale schedule/count rejection remain open. |
| WG-AC-04 — overdue/today task scope, order/count and stale/missing/reopened safety | **PARTIAL** | A due-today row completed, disappeared and returned after Undo. Overdue ordering/count, missing task and stale revision behavior remain open. |
| WG-AC-05 — duplicate/concurrent delivery, process death and old-token replay safety | **PARTIAL** | Persisted action/Undo state and storage paths were exercised. Duplicate/concurrent delivery, lost-result restart, process death and replay after Undo need dedicated integration checks. |
| WG-AC-06 — retained Saved/Undo/Retry and exact Undo | **PARTIAL** | Habit and task Saved→Undo were observed on the real launcher; store and renderer regressions pass, including row removal. Retry, conflicting Undo and recovery across all row-prefix changes remain open. |
| WG-AC-07 — refresh propagation and safe periodic/delayed work | **PARTIAL** | Habit/task creation and actions refreshed active launcher widgets. Periodic schedule uniqueness, app-absent writes, reboot, delayed work and lifecycle races remain open. |
| WG-AC-08 — hidden-detail defaults, instance configuration, token revocation and malformed-input rejection | **PARTIAL** | First-placement summary default and detail opt-in were observed; command/store tests ran. Cancel/revocation, provider/ID rejection and malformed external inputs need targeted tests. |
| WG-AC-09 — migrations, archive compatibility and restore/reset invalidation | **PARTIAL** | The full data suite passed with schema migration and backup tests. Widget command invalidation under interrupted/successful restore and host remap is not fully exercised end to end. |
| WG-AC-10 — en/fr/fa, RTL, light/dark, 100/130/200% text and size/orientation usability | **PARTIAL** | Launcher-provided sizes exposed and fixed the portrait/landscape min/min bug; size-policy and RemoteViews reapply tests pass. Only en-US, light, 100%, portrait was visually checked; the rest of the matrix remains open. |
| WG-AC-11 — real launcher TalkBack/keyboard and private-content boundaries | **PARTIAL** | Summary mode hid names; launcher accessibility nodes exposed action labels, and task Undo now has an accurate accessible description. TalkBack speech/focus and keyboard traversal were not tested. |
| WG-AC-12 — loading/error/empty states and recovery after process/host changes | **PARTIAL** | Renderer reapplication removes stale task children and keeps Undo visible. Process death, host ID remap, failure recovery and privacy after launcher caching remain open. |

## Remaining release gates

- Run the complete app instrumentation suite on a clean, authorized test state and resolve/classify the five reported failures.
- Test API 28 plus API 35/36, at least one second launcher family, provider removal/re-add and multiple widget instances.
- Exercise en/fr/fa, RTL, light/dark, 1.0/1.3/2.0 font scale, portrait/landscape and compact/large host sizes.
- Complete TalkBack/keyboard focus and spoken-label checks; profile large fixtures (1,000 habits and 10,000 tasks) for query/render time, payload size and ANRs.
- Finish process death, reboot, restore, host remap, stale-token and retry/conflict checks.

The full app run used the existing emulator database. A request to run `pm clear com.luma.lifeos` was rejected by automatic approval review as destructive and not explicitly authorized by the request to test; the command was not executed and app data was preserved. Do not treat this acceptance record as a release pass until the remaining gates are complete.
