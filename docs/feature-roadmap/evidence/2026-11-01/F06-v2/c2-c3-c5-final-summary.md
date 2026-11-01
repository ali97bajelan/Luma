# App Lock QA current-source C2/C3/C5 — 1 November 2026

## Candidate and environment

- Base commit: `cee76ca63b9cba9753337360347566dc3ff084c4`.
- Selected App Lock source/test working-tree diff SHA-256: `0bbc5c522ffc4b30d32787098f4817cdb463ddffb3596e4b39897676aa2375bc` (the changed lock/search/journal/navigation source and connected-test paths listed in the digest command for this QA run).
- Shared fixture SHA-256: `be6d08d327577bc5835a73b2085895995fb57811636aaa2e0bef46d3d259f4bf`.
- Gradle 9.1, offline mode, existing persistent Gradle cache; no dependencies were downloaded.
- Device for the release startup smoke: `emulator-5554`, `sdk_gphone64_x86_64`, Android 16 / API 36, build `BP41.250822.007`, 1080x2400 at 420 dpi, font scale 1.0, device time Asia/Tehran. The app rendered English UI; Android's `system_locales` setting returned `null`.

## C2 — host tests: PASS

Command: `GRADLE_USER_HOME=/home/bajelan/.gradle bash gradlew --no-daemon --console=plain --offline :app:testAppLockQaDebugUnitTest :core:data:testDebugUnitTest ... --rerun-tasks`.

- App host unit tests: 310 passed, 0 skipped/failures/errors.
- `core:data` host unit tests: 30 passed, 0 skipped/failures/errors.
- Combined: 340 passed, 0 skipped/failures/errors.

## C3 — compile/assembly/lint: PASS

- `:app:assembleAppLockQaDebug`, `:app:assembleAppLockQaDebugAndroidTest`, `:core:data:assembleDebugAndroidTest` succeeded.
- `:app:lintAppLockQaDebug` succeeded: 0 errors/fatals, 156 warnings and 11 informational findings.
- `:core:data:lintDebug` succeeded: 0 findings.

## C5 — minified release build and startup smoke: PASS (startup only)

- `:app:assembleAppLockQaRelease` succeeded. The unsigned artifact SHA-256 is `dce7c0ceea8a47f9362b22c831f3d6ba88843ee48e071caeea49695675b2f344`.
- Android rejected the unsigned APK with `INSTALL_PARSE_FAILED_NO_CERTIFICATES`; this is an artifact-signing prerequisite, not an app runtime failure. A separate copy was signed with the local Android debug key for emulator-only QA installation. Certificate SHA-256: `773fe3131f919d63669fde840f4e46553f5d9b24d9a29771127c6585b58c1011`; signed-copy SHA-256: `be1422d387150a8b9f52fbd67d1db29473a8850804dbbe385dca07e3d440a106`.
- The signed copy installed as isolated package `com.luma.lifeos.applockqa`, versionCode 8 / versionName `1.5.0-applockqa`; `MainActivity` was resumed and the empty QA Today screen was visible. The captured AndroidRuntime/System.err error log is empty. The temporary package was uninstalled after capture; package inventory confirmed `com.luma.lifeos` and `com.luma.lifeos.qa` remained installed.
- This smoke does not validate locked startup, an enabled policy, or protected routes in the minified artifact. Production signing is not represented by the debug-key copy.

## Artifacts

- Full Gradle output/exit: [`c2-c3-c5-final.log`](c2-c3-c5-final.log), exit 0, 376 tasks executed in 4m38s.
- Latest C3 debug APK SHA-256: `d956a4ef53c1a2d5e3168642a1bbc362d2cabf26f0b09e36a5cb392ad986ee57`.
- Latest C3 instrumentation APK SHA-256: `230da40568be75684de19631e773825dbabc2081bec4381dd99f5c5c9abf05f0`.
- Release startup image: [`c5-release-smoke.png`](c5-release-smoke.png); resumed activity: [`c5-release-smoke-activity.txt`](c5-release-smoke-activity.txt); package: [`c5-release-smoke-package.txt`](c5-release-smoke-package.txt); device: [`c5-release-smoke-device.txt`](c5-release-smoke-device.txt); runtime log: [`c5-release-smoke-logcat.txt`](c5-release-smoke-logcat.txt); cleanup/inventory: [`c5-release-smoke-cleanup.txt`](c5-release-smoke-cleanup.txt), [`c5-release-smoke-luma-packages-after.txt`](c5-release-smoke-luma-packages-after.txt).

This refresh does not change the connected C4 result: the latest full app run remains 277 tests with 254 passing, 22 failing, and 1 skipped. That run's exact APK hashes and JUnit are recorded in the QA ledger.
