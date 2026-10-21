# WD17 API 36 emulator evidence — 21 October 2026

## Environment and artifact

The isolated emulator ran Google `sdk_gphone64_x86_64` (`emu64xa`), API 36, 420 dpi, `en-US`, font scale 1.0, light theme. The tested APK reports version `1.0.1` (version code `2`). Fixtures are synthetic `WidgetSnapshot` values; no app records or personal data were read or changed.

The [render archive](wd10-after-1792564418550.zip) contains 54 matrix images for Tasks and Habits at totals 0, 1, 2, 3, 4, 8, 10, 50 and 500 across 220×160, 373×321 and 373×420 dp, plus four Undo/Retry images at 373×321 dp. The archive includes `environment.txt`, `fixtures.csv` and `feedback.csv` manifests.

These captures use offscreen `RemoteViews.apply`. The recorded home-activity resolver was `com.android.settings`, and collection adapter rows were not launcher-bound. The images help inspect the current header, metadata, collection visibility and overflow affordance; they do not demonstrate item rows inside a live launcher, actual scrolling, scroll preservation, duplicate-instance behavior, gestures or assistive navigation.

## Automated results

- `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.luma.lifeos.widget`: 35/35 passed on API 36. This includes 24 `WidgetRendererAcceptanceTest` cases, two configuration cases, collection factory/intents, color contrast, settings and the render-matrix capture.
- `WidgetStoreAcceptanceTest`: 7/7 passed within `:core:data:connectedDebugAndroidTest`.
- `:core:data:connectedDebugAndroidTest`: 152/152 passed after clearing unused Gradle caches. An earlier attempt ran out of temporary disk quota and produced SQLite/fsync errors; that run is superseded by this successful retry.
- The final JVM, lint and APK/Android-test assembly gate passed for the changed build. The broader app instrumentation run completed 218 tests with 1 skipped and 7 failed. Two failed cases subsequently passed in focused reruns (Mood completion navigation and the long-reading fixture). Five remain: widget Capture's paused `ActivityScenario` teardown, one Search assertion and three Todo semantics assertions. See WD13; these results do not establish launcher acceptance.

## Remaining acceptance

Live launcher-host scrolling to items beyond the first viewport, large-data and recovery interactions, duplicate instances, reapplication/resize/restart scroll preservation, keyboard/D-pad traversal and manual TalkBack speech remain open. Only API 36 was available for this run; API 28/35, a second launcher, landscape and the full locale/font-scale matrix still need evidence.
