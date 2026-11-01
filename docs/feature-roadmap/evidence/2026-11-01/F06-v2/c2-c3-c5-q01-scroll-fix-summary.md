# App Lock QA current-source C2/C3/C5 — Q01 scroll fix

## Candidate identity and environment

- Base commit: `cee76ca63b9cba9753337360347566dc3ff084c4`.
- Selected App Lock source/test working-tree diff SHA-256: `9bf325b5a18c02dd29cc00ae87c8c8731920761cbf6c0c1bc24ec47dfe0fe083`. Digest input: `ProtectedEventRepositories.kt`, `LifeOsApp.kt`, `JournalEditorScreen.kt`, `AppLockSettingsRoute.kt`, `AppNavigationTest.kt`, `JournalEditorScreenTest.kt`, `JournalCaptureTest.kt`, `LanguageFlowTest.kt` under `app/src`.
- Shared fixture SHA-256: `be6d08d327577bc5835a73b2085895995fb57811636aaa2e0bef46d3d259f4bf`.
- Gradle 9.1 in offline mode with the existing persistent cache; no dependencies were downloaded.
- Release-smoke device: `emulator-5554`, `sdk_gphone64_x86_64`, Android 16 / API36, build `BP41.250822.007`, 1080x2400 at 420 dpi, font scale 1.0, Asia/Tehran.

## C2 — host tests: PASS

- App host unit tests: 310 passed, 0 skipped/failures/errors.
- `core:data` host unit tests: 30 passed, 0 skipped/failures/errors.
- Combined: 340 passed, 0 skipped/failures/errors.

## C3 — compile, assembly, lint: PASS

- `:app:assembleAppLockQaDebug`, `:app:assembleAppLockQaDebugAndroidTest`, and `:core:data:assembleDebugAndroidTest` succeeded.
- `:app:lintAppLockQaDebug`: 0 errors/fatals, 156 warnings, 11 informational findings.
- `:core:data:lintDebug`: 0 findings.
- Latest C3 debug APK SHA-256: `f2ac24854330bb2b4087f66eedb4061970f7d998284e417ea3bc2ef4e0a33a2c`.
- Latest C3 instrumentation APK SHA-256: `d177e80e7f4b2b1e717d202d45bcf1d787084be0d4c9a6f3b802fddf4df2cbb6`.

## C5 — minified release build and startup smoke: PASS (startup only)

- `:app:assembleAppLockQaRelease` succeeded despite nonfatal R8 Kotlin metadata parser warnings. Unsigned APK SHA-256: `0f1d57533595096fc4dc295d17f4dac27c6b019225c05eb6a058d3e70da99047`.
- A separate evidence copy was signed using the local Android debug key for the isolated emulator install. Certificate SHA-256: `773fe3131f919d63669fde840f4e46553f5d9b24d9a29771127c6585b58c1011`; signed-copy SHA-256: `51fc4af5388b3b0d05311041f794d361a1f606e0386d45e3667eb18612d86763`.
- The signed copy installed as `com.luma.lifeos.applockqa`, versionCode 8 / versionName `1.5.0-applockqa`; `MainActivity` resumed and Today rendered. The filtered AndroidRuntime/System.err log is empty. The QA package was uninstalled; inventory confirmed `com.luma.lifeos` and `com.luma.lifeos.qa` remain installed.
- This validates minified startup only. It does not cover an enabled lock policy, locked startup, setup, or protected routes in the release artifact. Production signing is not represented by the debug-key copy.

## Current full app C4

- JUnit XML: 278 cases, 254 passed, 23 failed, 1 skipped. The Gradle console reports “Finished 279 tests”; that count differs from the exported XML total and remains an evidence discrepancy.
- The new `emptyAppLockSetupScrollsValidationIntoView` regression passes. The 23 failures include an intermittent journal capture failure plus memory/mood/People/Place capture, SearchStates, calendar, habits, language navigation, todo, accessibility, and widget rendering. No App Lock credential integration test failed.
- C4 run APK SHA-256: app `614f1593338b7deb5819df4b5a1578a902384966938dbd86ede5411b169f98e7`; instrumentation `e3ca60b5c1d0731065d9a6b47ae8101bf745f3130a2c6fb7544f788398ac1f11`.
- `core:data` remains unchanged and its API36 C4 178/178 result remains valid.

## Evidence

- Full current-source Gradle gates: [`c2-c3-c5-q01-scroll-fix.log`](c2-c3-c5-q01-scroll-fix.log), exit 0, 376 tasks executed in 4m48s.
- Full app C4 console/JUnit: [`c4-app-q01-scroll-fix.log`](c4-app-q01-scroll-fix.log), [`c4-app-q01-scroll-fix.xml`](c4-app-q01-scroll-fix.xml), [`test-results.log`](c4-app-q01-scroll-fix-test-results.log), and [`c4-q01-scroll-fix-test-output-metadata.json`](c4-q01-scroll-fix-test-output-metadata.json).
- Release startup: [`c5-q01-scroll-fix.png`](c5-q01-scroll-fix.png), [`c5-q01-scroll-fix-activity.txt`](c5-q01-scroll-fix-activity.txt), [`c5-q01-scroll-fix-package.txt`](c5-q01-scroll-fix-package.txt), [`c5-q01-scroll-fix-logcat.txt`](c5-q01-scroll-fix-logcat.txt), and [`c5-q01-scroll-fix-luma-packages-after.txt`](c5-q01-scroll-fix-luma-packages-after.txt).

Overall ALOCK-QA remains in progress because full C4 has unexplained failures and required manual/device/API/security coverage is incomplete.
