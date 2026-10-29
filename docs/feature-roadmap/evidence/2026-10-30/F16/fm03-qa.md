# FM03 lifecycle and guarded-screen QA — 30 October 2026

**Result:** Scoped FM03 QA **PASS** on API 36.1: the app host suite, selected lifecycle/navigation/recovery instrumentation, and variant lint all passed. The unexecuted lifecycle, fault-injection, API28 and accessibility/layout/localization matrix remains pending; this is not integrated F16 acceptance.

## Candidate and environment

- Base commit: `4472f5f89d4586795b3759ac8454d021d44c5ff4`.
- FM03 implementation + QA code/config/test patch SHA-256: `461eb72ef9df56c0c540b9a2ea25a83fe29dd9190b45ebed1d77f8287439d2c6`. This digest covers changed production/resource/build/test files and excludes documentation, the pre-existing release APK edit and unrelated evidence artifacts.
- Instrumented app APK: variant `focusQaDebug`, application ID `com.luma.lifeos.focusqa.qa`, version code `8`, version name `1.4.0-focusqa`; APK SHA-256 `588441290b14377b329015d25c1d948dce203c0e940ca94078a737612f40d26a`. See [output metadata](fm03-apk-output-metadata.json).
- Device: requested AVD `Medium_Phone_API_36.1`, emulator model `sdk_gphone64_x86_64`, API 36 / Android 16. Portrait 1080×2400 at density 420 (~411×914 dp), font scale 1.0. The reported system locale was empty (`system_locales=null`); device theme and time zone were not captured for this run.
- Host toolchain: OpenJDK 17.0.20.1, Gradle 9.1.0, Android Gradle Plugin 8.7.3, Kotlin 2.2.10, compile/target SDK 35, min SDK 28.
- The dedicated flavor uses package `com.luma.lifeos.focusqa.qa`. The pre-run package inventory showed no installation under that ID; existing `com.luma.lifeos` and `com.luma.lifeos.qa` installations were left intact. No app data was cleared. The connected runner removed the disposable QA target afterward. The user explicitly authorized use of this emulator.

## Executed checks

1. `GRADLE_USER_HOME=/tmp/luma-f02-gradle-home bash ./gradlew :app:testFocusQaDebugUnitTest --no-daemon --console=plain`: **293 passed, 0 failed, 0 errors, 0 skipped**, exit 0. The full host JUnit XML set is in [the host reports archive](fm03-app-host-junit.zip); the four new FM03 host tests cover coordinator samples/process identity/emergency reason and queued navigation behavior.
2. `GRADLE_USER_HOME=/tmp/luma-f02-gradle-home bash ./gradlew :app:connectedFocusQaDebugAndroidTest --no-daemon --console=plain` with `FocusRuntimeRecoveryTest`, `FocusLifecycleTest` and `FocusScreenTest` selected: **6 passed, 0 failed, 0 errors, 0 skipped**, exit 0. See [the connected JUnit report](fm03-api36-connected-junit.xml). The AVD report timestamp is `2026-10-29T22:38:59Z` (30 October, Asia/Tehran).
3. `GRADLE_USER_HOME=/tmp/luma-f02-gradle-home bash ./gradlew :app:lintFocusQaDebug --no-daemon --console=plain`: exit 0; 0 errors/fatals. The report contains 154 project-wide warnings and 10 informational notices. No warnings were reported in changed FM03 production files; three informational `AutoboxingStateCreation` suggestions occur in `LifeOsApp.kt` (two) and `TodayRoute.kt` (one). See [the lint XML](fm03-lint-focusqa.xml).
4. `git diff --check` and XML parsing of the EN/FR/FA string resources passed after the QA corrections.

## Scoped behavior and QA corrections

- **Lifecycle/navigation subset — PASS:** Today/More entry, widget new-intent queuing until an explicit post-pause choice, system Back prompt, recreation, Pause/Resume/Stop and one retained focus session. The screen tests verify reachable Pause/Stop actions and a healthy running screen's emergency exit.
- **Runtime recovery subset — PASS:** tests construct a second coordinator with a distinct process identity over the same durable state and assert explicit recovery; a matching emergency marker interrupts at the last saved checkpoint and is cleared. This simulates a new owner identity; it does not kill/reboot the Android process.
- **QA defects corrected and retested:** startup-time emergency exit now releases the UI guard after reconciliation while checkpoint/time accounting continues; the healthy running state always shows emergency exit; Back-triggered Pause/Stop leaves only after durable success; nullable recovery snapshots are handled safely. Two stale existing `HabitCaptureFlowTest` calls were updated to the current `TodayHabitsCard` signature so Android test compilation succeeds.
- **Coverage limits — NOT RUN:** actual OS process death/force-stop, reboot/boot-ID change, screen-off/sleep, injected database/preference write failures, API28, all Luma launch paths beyond those listed above, predictive Back/keyboard/TalkBack, and EN/FR/FA × theme/window/font/orientation matrix. No manual screenshot or audio-accessibility evidence was captured. QA01–04 and QA12–13 therefore remain partial at the overall scenario level.

## Disposition

FM03's selected API36.1 behavior is verified; keep remaining FM03 native/platform checks open and keep FM07 integrated QA pending until FM04–FM06 handoffs and feature-wide cases are complete. No production or existing QA app database was opened, cleared or modified by this run.

**Suggested commit message:** `feat(focus): add guarded session lifecycle and recovery`
