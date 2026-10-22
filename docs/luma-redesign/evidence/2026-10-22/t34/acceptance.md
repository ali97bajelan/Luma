# T34 acceptance record — 2026-10-22

**Status: INCOMPLETE; release acceptance NOT ACCEPTED.** T34-D preview implementation and the synthetic API 36 renderer matrix were exercised, but the real launcher/API matrix remains NOT RUN. The connected-test teardown also removed the preexisting Luma package from the AVD, so no more device checks were run.

## Build and environment

- Source revision at build time: `ecb8bb5a0e9859e9448fb4b5405de05a43a18c0c`; T34-D changes were in the uncommitted working tree.
- App APK: `com.luma.lifeos`, version code `3`, version name `1.1.0`, min API `28`, target API `35`; SHA-256 `52ffb800a905c672de92a4f9c56e4a2e327b84c0b32094167540dc2823459d43`.
- Instrumentation APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`; SHA-256 `158279d79bfb56475b365833efed7c8871dc3e26d5ab7508b54713a0a20ef0de`.
- Device: `Medium_Phone_API_36.1` AVD, emulator model `sdk_gphone64_x86_64`, Android 16 / API 36, 1080 × 2400 px at 420 dpi. Home launcher resolved to `com.google.android.apps.nexuslauncher/.NexusLauncherActivity`; system night mode was off.
- At test start, the AVD had an existing `com.luma.lifeos` install at version code 3 / version name 1.1.0. The T34 renderer exporter used synthetic `WidgetSnapshot` fixtures and did not query or mutate the app database.

## Checks run

- `:app:testDebugUnitTest --tests 'com.luma.lifeos.widget.WidgetLayoutPolicyTest'` — PASS.
- `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` — PASS on the final source tree. Lint reported no preview-specific errors after localized count and RTL-padding cleanup.
- Focused API 36 `WidgetVisualBaselineTest.captureT34RendererFixtureMatrix` — PASS. The exporter reported 108 baseline shell cases, 96 French/Persian stress cases, 18 collection-row cases, and theme-specific feedback reapply cases. Its output used `t34/run-20261022T180016Z-498629172262` under app-specific external storage and recorded offscreen `RemoteViews.apply`; collection adapters were not launcher-bound.
- Focused API 36 `WidgetRendererAcceptanceTest.pickerPreviewsShowFictionalContentWithSharedCaptureHeader` — PASS for both day and night resource configurations.
- The first exporter attempt exceeded the main-thread window and was killed. The renderer was changed to perform one view draw per main-thread dispatch; the corrected matrix run passed. An initial preview assertion exposed misplaced metadata IDs; the IDs were corrected and the focused preview test then passed.
- The focused connected runs preceded the final localized sample-count and RTL-padding cleanup. That final resource tree passed local unit/build/lint checks; the instrumentation source was recompiled and repackaged after its final diagnostic-label edit. No connected tests were rerun after teardown removed the app package.

## W34 acceptance rows

| Row | Result | Evidence and remaining work |
| --- | --- | --- |
| W34-1 — distinct title/Capture/Refresh targets and count badge | PARTIAL | The offscreen matrix rendered both app-theme overrides against opposite system appearances and recorded pixel bounds/descriptions. No real launcher target-size or touch check was performed. |
| W34-2 — task cards, due state, action identity, Undo/Retry | PARTIAL | Synthetic row fixtures covered due-today and overdue states, but no launcher action delivery or mutation/feedback flow was exercised. |
| W34-3 — habit progress edge cases and four row variants | PARTIAL | Synthetic rows covered 0/5, 1/5, 2/8, reached, over-target, skipped and invalid-target fixtures. The matrix did not bind a launcher collection or verify paging/recycling after a real theme refresh. |
| W34-4 — measured geometry, overflow and fallback | PARTIAL | Offscreen bounds included 220 × 160, 373 × 321, 373 × 420, 180 × 160, 180 × 220 and 578 × 109 dp with the specified locale/font-scale cases. A 500-record shell fixture is not proof of real collection scrolling beyond page one. |
| W34-5 — compact, picker, legacy, RTL and accessibility | PARTIAL | The fictional picker preview assertion passed for day/night resources. Legacy Quick Capture, real RTL launcher output, TalkBack, and 48 dp host targets were NOT RUN. |

## NOT RUN and environment incident

- API 28 and API 31 launcher checks: **NOT RUN**; the SDK only had an API 36.1 system image/AVD.
- API 36 widget placement, resize, rotation, scrolling, Complete/+1/Undo/Retry delivery, locale refresh, TalkBack and target-size checks: **NOT RUN**. The successful API 36 tests were offscreen renderer and static picker-preview checks only.
- Durable screenshot paths: **NOT AVAILABLE**. The exporter wrote its PNG/CSV matrix to app-specific external storage, then the connected Gradle test teardown removed `com.luma.lifeos`. Android emitted `ACTION_PACKAGE_FULLY_REMOVED`; the app-private output directory was removed with the package. The passing test log contains the run path, but the PNGs and CSVs no longer exist on the AVD.
- The same uninstall removes app-private data. The preexisting AVD app data may therefore have been deleted by test teardown. No database reset or fixture seeding was performed by the test code, and no reinstall or further connected run was attempted after confirming the package was absent.

The T34-D source work is implemented, but every W34 row still needs durable captures and real-host acceptance. T34 status is **implemented (validation pending)**; release acceptance remains incomplete. This record does not change WG08 or T30–T33 status.
