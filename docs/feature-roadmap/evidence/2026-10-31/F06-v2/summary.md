# App Lock T01/T02 QA run

Date: 31 October 2026 · Disposition: **partial QA; ALOCK-T01 and ALOCK-T02 BLOCKED**

## Candidate and environment

- Base source: `7a1764e2211880266b923ffd1825a10450e38183` (`main`, dirty QA workspace).
- Test-source patch SHA-256: `85e2df34673939c0ada25351d1c1da5e2aa166f9b6d973972580dd5edafaf5d6` (the 11 test paths listed in the QA patch; no App Lock production source changed).
- Fixture `app-lock-v1.json` SHA-256: `be6d08d327577bc5835a73b2085895995fb57811636aaa2e0bef46d3d259f4bf`.
- Toolchain: Gradle 9.1.0, AGP 8.7.3, Kotlin 2.2.10, JDK 17.0.20.1, compile/target SDK 35, min SDK 28. Commands used `GRADLE_USER_HOME=/tmp/luma-f02-gradle-home`, `--no-daemon --console=plain --offline`; Gradle ran with the approved host access needed for its daemon.
- Device: `Medium Phone API 36.1`, `emulator-5554`, Android API 36, 1080×2400 at 420 dpi (about 411 dp wide), `en-US`, `Asia/Tehran`, font scale 1.0, light theme. Airplane mode was enabled for offline checks and restored to off; the emulator now has only the original `com.luma.lifeos` and `com.luma.lifeos.qa` Luma packages.
- Isolated debug app: `com.luma.lifeos.applockqa.qa`, version 8 / `1.5.0-applockqa`; instrumentation package `com.luma.lifeos.applockqa.qa.test`. Core data instrumentation target: `com.luma.lifeos.data.test`. APK hashes and output metadata are recorded in the sibling files.
- Debug APK SHA-256: `2f6c9579d5abb8303b36fd2ebb6e12ffaec9202eef6846e2e00a5463ac6691c2`; instrumentation APK: `09bf23eb8ceec795024aeec04dea794ba504c9c90fb54e6122019a2800d4064d`.
- Minified release APK: `com.luma.lifeos.applockqa`, unsigned, version 8 / `1.5.0-applockqa`; SHA-256 `4dca909f78be3f807b303272ea01c095671baafe600bfdb0a081c94110a42241`. It was assembled only and not installed or smoke-tested.

## Executed gates

All listed Gradle runs completed with exit 0 after the named fixes/retests. JUnit counts are XML totals, with zero failed, errored, or skipped tests on the final run.

| Gate | Result | Evidence |
| --- | --- | --- |
| C1 `:core:domain:test` (`-PdomainOnly=true --rerun-tasks`) | 169 passed | `c1-domain-tests.log` |
| C2 `:app:testAppLockQaDebugUnitTest :core:data:testDebugUnitTest --rerun-tasks` | 340 passed (310 app, 30 data) | `c2-host-tests-retest.log` |
| C3 app/debug and Android-test assembly plus app/core:data lint (`--rerun-tasks`) | Build passed; 0 lint errors/fatals. App lint reported 156 warnings and 11 informational findings; core:data reported none. App warnings were mainly unused resources (86) and dependency-age notices (27). | `c3-build-lint.log` |
| C4 App Lock store/credential instrumentation on API 36.1 | 10 passed, 0 skipped | `applock-api36-connected.xml`, `c4-applock-connected-retest2.log` |
| C4 full `core:data` connected regression suite on API 36.1 | 178 passed, 0 skipped | `core-data-api36-connected.xml`, `c4-data-connected-retest.log` |
| C5 `:app:assembleAppLockQaRelease` | Minified unsigned APK assembled successfully. R8 emitted nonfatal Kotlin metadata parsing warnings; no minified runtime smoke was run. | `c5-minified-release.log` |

Test code uses the fixture's exact section sets, single-section variants, marked-record classification, People-field classification, unknown/error denial, policy generation, relock boundaries, retry schedule, operation-purpose binding, store corruption, atomic-write failure, and recovery vectors. Native tests exercised Android Keystore and `noBackupFilesDir` `AtomicFile` storage. The credential integration test exercised offline verification, 7/129 and 8/128 length bounds, exact whitespace/Unicode, emoji code points, setup failure atomicity, recovery replacement, and the complete cooldown schedule with service recreation and an injected boot-marker change.

## Manual observation and defect/retest record

- The QA package was absent before this run. It was installed under the isolated package ID, opened to an empty Today baseline in airplane mode, and removed afterward. Existing personal and Focus QA package IDs were not reset or modified.
- Privacy → App Lock settings opened the fresh setup screen. Backing out before commit and reopening still showed setup, with no enabled-lock/authentication state. A synthetic seven-character UI submission did not yield observable validation feedback through the emulator tap path, so UI validation and successful setup/recovery acknowledgement remain unverified. No password or recovery code was saved in evidence.
- Initial C2 compile exposed seven existing `BackupViewModelTest` trailing-lambda calls that bound to `authorize` rather than `open`; calls now name `open`, and the full host retest passed.
- Initial App Lock C4 attempts exposed two test-harness issues: the fake wall clock did not advance with elapsed time, then a process-local recovery confirmation was reused after service recreation. The test clock now advances both clocks, and the recreated service creates its own confirmation. Final C4 is 10/10 passed.
- The first full core:data C4 run had two stale test expectations: migration ordering expected reversed IDs, and a schema-4 test archive retained schema-6/7 tables. Assertions/fixture generation now match the stored ordering and exact legacy table set. Focused rerun passed 8/8; full rerun passed 178/178.

## Open acceptance work

These gates validate domain rules and credential/storage behavior; they do not complete the shared end-to-end scenarios. T01 and T02 remain **BLOCKED**, and overall task status remains TODO, because API 28 is unavailable in this run, the manual setup/unlock/recovery flow is incomplete, and the complete supported UI matrix and security review have not run. T01 still needs actual secured content projections and lifecycle/navigation/capture integration for Q03–Q08. T02 still needs successful UI setup, locked relaunch after real process/device restart, actual cooldown behavior across reboot, and recovery UI completion. Q22 also needs the broader integrated app suites and a minified runtime check using an isolated signed build. Landscape/wide screens, large font, RTL/FR, dark mode, TalkBack, multi-window, and capture/preview boundaries were not exercised.

The evidence proves only the executed gates listed above; see the QA ledger for per-case dispositions. Do not mark ALOCK-T01/T02 complete until the blocked scenario obligations pass on the current source.
