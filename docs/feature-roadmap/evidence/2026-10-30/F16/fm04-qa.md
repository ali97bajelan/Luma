# FM04 settings and selected-app handoff QA — 30 October 2026

**Result:** FM04 scoped QA **PASS**. The app host suite, focused API 36.1 instrumentation suite and `focusQaDebug` lint all exited successfully. This covers settings, local selection and the shortcut handoff contract; it does not complete the full F16 QA scenarios or verify Android-wide blocking.

## Candidate and environment

- Candidate base: `0c3ae4cc055188818586253a7e210b8d96be675c`. Implementation, QA and documentation changes are uncommitted.
- Instrumentation variant: `focusQaDebug`, application ID `com.luma.lifeos.focusqa.qa`, version code `8`, version name `1.4.0-focusqa`.
- Device: requested `Medium_Phone_API_36.1` AVD; Android 16 / API 36, emulator reported as `sdk_gphone64_x86_64`.
- No app package inventory was logged or saved. The PackageManager test asserted only aggregate discovery invariants and a synthetic missing package. No disposable external fixture app was installed.
- No Usage access or notification permission was granted or required by these tests. The existing narrow MAIN/LAUNCHER package query remains; no broad package permission or dependency was added.

## Executed checks

1. `GRADLE_USER_HOME=/tmp/luma-f02-gradle-home bash ./gradlew :app:testFocusQaDebugUnitTest --no-daemon --console=plain` — **297 passed, 0 failed, 0 errors, 0 skipped**. The host JUnit reports are in [the host report archive](fm04-app-host-junit.zip).
2. `GRADLE_USER_HOME=/tmp/luma-f02-gradle-home bash ./gradlew :app:connectedFocusQaDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.focus.FocusPreferencesTest,com.luma.lifeos.focus.AllowedFocusAppsPlatformTest,com.luma.lifeos.focus.FocusShortcutHandoffTest,com.luma.lifeos.ui.focus.FocusAllowedAppsTest,com.luma.lifeos.settings.SettingsHubFlowTest,com.luma.lifeos.ui.focus.FocusScreenTest,com.luma.lifeos.focus.FocusRuntimeRecoveryTest,com.luma.lifeos.ui.focus.FocusLifecycleTest --no-daemon --console=plain` — **20 passed, 0 failed, 0 errors, 0 skipped** on the AVD. See [the connected JUnit report](fm04-api36-connected-junit.xml).
3. `./gradlew :app:lintFocusQaDebug --no-daemon --console=plain` — **exit 0**. Lint reported 154 warnings and 10 informational suggestions across the project. No findings were reported in FM04 focus production files. The remaining findings relevant to touched files are one `WidgetSettingsRoute.kt` obsolete SDK warning and two informational `LifeOsApp.kt` suggestions. See [the lint XML](fm04-lint-focusqa.xml).
4. `git diff --check` and XML parsing of EN/FR/FA string resources passed.

## Behavior covered

- Preferences default to no selected apps, store device-local package IDs, report durable write success, and preserve updates observed through another preferences instance. Blank package IDs are rejected and stale selections can be removed.
- Launcher discovery tests assert unique package rows, exclude Luma and unlaunchable entries, provide deterministic ordering, and distinguish duplicate labels with package IDs. Security/package lookup failures produce an unavailable result.
- Compose assertions cover searching, duplicate-label identity, selection callback, stale removable state, and edits disabled while a session is running.
- The handoff test verifies the session checkpoint precedes launch; a changed selection blocks launch; an injected launch exception offers retry; retry can succeed while the focus session remains RUNNING; and the nonselected package path is rejected.
- Screen and Settings hub regressions cover retry visibility and the settings entry's feature/callback gating.

## Remaining QA limits

- QA05 remains partial: actual external app launch and return continuity were not manually exercised; disabled/uninstalled real targets, apps with multiple launcher activities, and work-profile separation were not checked with installed fixtures.
- QA12/13 remain partial: the connected run did not cover every Luma entry path, existing feature regression baseline, drafts, or restore/reset races. API 28, TalkBack, keyboard, predictive Back, and EN/FR/FA × theme/window/font/orientation checks remain open. No screenshot or audio accessibility evidence was captured.
- The settings copy states that selected-app shortcuts do not block Android/system controls or apps opened elsewhere. Device-wide enforcement is still unresolved under FM08.
- The AVD run does not establish behavior on physical devices or Android versions beyond API 36.

## Disposition

Mark FM04 source complete and record a scoped API 36.1 QA pass. Keep QA05/12/13 partial and full F16 integrated QA pending until the remaining fixtures and platform matrix run.

**Suggested commit message:** `feat(focus): add settings and safe selected-app shortcuts`
