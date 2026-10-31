# FM07 integrated source review and QA — 30 October 2026

**Disposition:** FM07 source review and scoped API 36.1 QA are recorded. Five in-scope defects found by review were fixed and covered by regressions. **Integrated release acceptance remains PARTIAL:** QA01–13 still have unrun native or matrix cells, QA14 performance is not run, and the full connected app suite has unresolved observed failures. This is not full F16 acceptance or device-wide enforcement.

## Candidate and environment

- Source base: `d0572d76ff9e0212359242ac1dd676eb946adbf3` (`fix(focus): preserve restore callbacks and separate replacement actions`). The app source/test patch from that base has SHA-256 `3b2e48aceeefbcf578db2f9105643a54758454f945ccbfbf59e683cf7c5bbbeb`.
- FM07 fixes touch Focus runtime/coordinator/guard, history and weekly report UI/state, localized strings and focused tests. Room schema remains v8 and portable archive schema v7; no manifest or archive format change. The shared manifest retains the narrow MAIN/LAUNCHER query for app discovery and has no Usage access, accessibility, DPC/device-admin, `QUERY_ALL_PACKAGES` or Focus foreground-service requirement.
- Variant: `focusQaDebug`, package `com.luma.lifeos.focusqa.qa`, instrumentation package `com.luma.lifeos.focusqa.qa.test`, version `1.4.0-focusqa` (code 8). Debug APK SHA-256: `f5f56cc8d30c85022070ad0b350e52497f92765aa69dac2d5cbc8d7373cab4fc`.
- Minified variant: `focusQaRelease`, package `com.luma.lifeos.focusqa`, version `1.4.0-focusqa` (code 8), minSdk 28, compile/target 35. Current unsigned APK SHA-256: `a9466d1cbf2acb1c4c32023c7674c41eda7b5bd672576b903bf22facc22145af`. It was signed with an ephemeral QA-only key, installed under the isolated package, launched, used to start/stop a session, and opened to unfiltered history; the package and key were removed afterward. This does not certify a production-signed release.
- Device: `Medium_Phone_API_36.1` AVD, emulator-5554, Android 16 / API 36, 1080×2400 at 420 dpi (about 411 dp wide), English, font scale 1.0, portrait, light appearance. The emulator initially had `com.luma.lifeos`, `com.luma.lifeos.qa` and `com.example.scrollfit`; none were targeted. API 28 system image and physical representative hardware were unavailable.
- Toolchain: Gradle 9.1.0, AGP 8.7.3, Kotlin 2.2.10, JDK 17. Dependency resolution was offline. The pre-existing tracked `app/release/app-release.apk` remained unchanged at SHA-256 `c7086e7dc72183f8509493f353c38fed24ea568da3bec73d5653896cb91966bd`.

## Source review and static checks

- Reviewed the F16 R01–R05 handoffs, migration/archive integration, shell navigation guard, launcher query and EN/FR/FA focus resources against the specification and plan. The feature remains clearly described as personal-phone navigation/shortcut behavior; the original device-wide restriction is still deferred to FM08.
- An independent source review covered F16 R01–R05, entry paths, command/generation authority, archive and schema boundaries, permissions, resource defaults, and report behavior. It found five defects; all five were corrected and regression-tested below.
- `git diff --check` passed. EN/FR/FA string XML parsed and focus resource keys remain in parity.
- App lint exited 0 with 0 errors, 154 warnings and 10 informational findings; no findings point at Focus production files. Data lint exited 0 with no issues.
- Current minified release assembly and host tests passed, but R8 printed 301 Kotlin-metadata parser compatibility warnings. The current minified APK launched and completed a Start/Stop session (00:11 credited), then displayed its saved row on the unfiltered history route; see [release smoke log](fm07-minified-release-smoke.log), [Today](fm07-minified-release-home-final.png), [Focus setup](fm07-minified-focus-setup-final.png), [running](fm07-minified-focus-running-final.png), [saved](fm07-minified-focus-stopped-final.png) and [history](fm07-minified-history-final.png). The Android Gradle plugin has no `assembleFocusQaReleaseAndroidTest` task configured, so connected instrumentation was debug-only.

## Automated runs

1. Full host command: `GRADLE_USER_HOME=/tmp/luma-f02-gradle-home bash gradlew :core:domain:test :core:data:testDebugUnitTest :app:testFocusQaDebugUnitTest :app:testFocusQaReleaseUnitTest --rerun-tasks --continue --no-daemon --console=plain --offline` — the final fresh app host runs passed **304/304 debug and 304/304 release**; the earlier domain/data host run passed 157/157 and 30/30. See [final host/build/lint log](fm07-final-host-release-lint.log) and [host JUnit archive](fm07-host-junit-final.zip).
2. Final build/lint command: `GRADLE_USER_HOME=/tmp/luma-f02-gradle-home bash gradlew :app:testFocusQaDebugUnitTest :app:testFocusQaReleaseUnitTest :app:assembleFocusQaRelease :app:lintFocusQaDebug :core:data:lintDebug --rerun-tasks --continue --no-daemon --console=plain --offline` — exit 0; app lint and minified assembly passed. R8 warnings are in the linked log.
3. Focused connected command selected `FocusLifecycleTest`, `FocusRuntimeRecoveryTest`, `FocusScreenTest` and `FocusReportsTest` via `:app:connectedFocusQaDebugAndroidTest` — **13/13 passed**, 0 skipped, on `Medium_Phone_API_36.1`. The retry suite was rerun after adding the simulated generation-change boundary — **3/3 passed**. See [final connected log](fm07-connected-final-all.log), [JUnit report](fm07-api36-app-focus-junit-final.xml) and [retry rerun log](fm07-runtime-retry.log).
4. Focus/backup data command selected `FocusPersistenceTest`, `FocusMigrationTest`, `FocusReportPersistenceTest` and `BackupPersistenceTest` via `:core:data:connectedDebugAndroidTest` — **29/29 passed**, 0 skipped, on the same AVD. See [JUnit report](fm07-api36-data-focus-junit.xml) and [run log](fm07-data-connected.log).
5. The earlier complete app instrumentation run started 262 tests but was interrupted after 83 completed tests (Gradle exit 130) when 10 failures had appeared. Observed failures included the previously recorded `AppNavigationTest` ActivityScenario teardown and failures in Journal/Memory/Mood/People/Place/Search/Calendar tests. This broad run was not repeated after the fixes and does not establish integrated regression acceptance; see [partial full-suite log](fm07-app-connected.log).

## Defect found and correction

The lifecycle test still looked for the pre-FM05 `today_focus` tag; it now uses `today_focus_open` and waits for the card after returning from a queued destination. The independent review also found and the FM07 changes now fix:

- Repeated identical widget/reminder requests could bypass the running guard when the queued value compared equal. The external request path now tests whether the guard is active independently of whether its queued destination changed; a repeated Quick Capture integration regression passes.
- Retrying a command after a committed write with a lost acknowledgement reused its command ID with a newly sampled time and generation. The UI operation now retains its sample and generation from invocation through retry. A commit-then-throw regression advances the sample and simulates a generation change before retry; it receives the saved receipt result.
- History deletion used a newly fetched generation instead of the generation associated with the visible page. Each history load now binds deletion to the generation captured before its page query, refreshes on resume, and clears stale confirmation state while reloading.
- Weekly Review exposed only current/previous aggregate bars. It now observes seven local-day buckets, leaves future dates unavailable in the current week, and labels each elapsed day.
- All-session history had no route from Focus/More, hiding zero-duration terminal records from the UI. Setup, paused and terminal Focus screens now expose unfiltered history; the running screen remains guarded. The minified release smoke opened the new route.

All fixes have scoped regression coverage; they do not close the native platform, full-suite, accessibility or performance gates.

## Case disposition

| Case | Result | FM07 evidence and remaining work |
| --- | --- | --- |
| QA01 | PARTIAL | Focus lifecycle, duplicate Quick Capture request, More entry, widget intent, Pause/Resume/Stop and system Back assertions pass. Other entry paths, gesture/system interruptions, draft preservation and the full guard matrix remain open. |
| QA02 | PARTIAL | Domain, repository, app and Room subsets pass. Commit-then-lost-ack replay with a changed sample and simulated generation boundary passes. Cross-window write races and older checkpoint/Stop races remain unrun. |
| QA03 | PARTIAL | Recreation and simulated recovery checks pass. Actual process death, force-stop and device sleep recovery were not exercised. |
| QA04 | PARTIAL | Pure continuity/recovery and persistence subsets pass. Native reboot/clock changes and independent Room/preferences failure injection remain unrun. |
| QA05 | PARTIAL | Preferences, package discovery, validation and shortcut handoff tests pass. No disposable external app was launched/returned to; disabled/uninstalled target and profile boundary remain open. |
| QA06 | PARTIAL | Report/domain and Room clipping subsets pass. Full zone-change, DST device behavior and exact endpoint fixture remain open. |
| QA07 | PARTIAL | Review now loads seven local-day buckets, marks future current-week dates unavailable and shows day bars. Full deterministic weekday fixture, both source drill-downs, DST device behavior and back-stack preservation remain open. |
| QA08 | PARTIAL | Comparison and report UI subsets pass. Remaining rounding cases and all loading/error/unavailable UI states remain open. |
| QA09 | PARTIAL | History, paging, detail, deletion and unfiltered history access pass. A zero-duration record can now be browsed; dedicated zero/paused/interrupted fixture and concurrent report/delete race remain open. |
| QA10 | PARTIAL | Focus migration, report persistence and backup connected suites pass. Full legacy/malformed archive matrix, archive-content inspection and retained photo-hash comparison remain open. |
| QA11 | PARTIAL | History deletion retains its page generation; retry replay retains its operation generation across a simulated generation change. A real queued write racing restore/reset, checkpoint races and full receipt-eviction replay remain open. |
| QA12 | PARTIAL | 13/13 focused connected regressions and 304/304 debug and release app host suites pass. The earlier 262-test connected run stopped at 83/262 with 10 observed failures and was not rerun, so integrated regression acceptance is not established. |
| QA13 | PARTIAL | One API 36.1 English, light, portrait, normal-font cell was observed. API28, EN/FR/FA × theme/window/font/orientation matrix, TalkBack, hardware keyboard and predictive Back were not run. |
| QA14 | NOT RUN | No 50,000-session fixture, 5 warmups + 30 timing samples, query plan, write cadence or representative-device measurements were collected. |

## Remaining release gates

- Run the full QA01–14 required cases and platform/accessibility matrix on isolated targets, including minimum API 28.
- Resolve the full app suite failures and rerun the complete affected regression suite; the partial log is not a pass.
- Run QA14 with 50,000 sessions and named representative hardware. Emulator measurements alone cannot meet this target.
- Review and resolve the R8 Kotlin-metadata warnings before claiming the minified release gate fully accepted.
- FM08 remains pending. The personal-phone guard and app shortcuts do not establish device-wide enforcement.

**Suggested commit message:** `fix(focus): harden retries, navigation, and weekly reports`
