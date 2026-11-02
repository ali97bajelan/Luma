# FM06 backup, restore and reset QA — 30 October 2026

**Result:** FM06 scoped QA **PASS** on the isolated API 36.1 target. QA10–11 are **PARTIAL**; full F16 integrated acceptance and the platform/accessibility matrix remain open.

## Candidate and environment

- Candidate base: `4e9f4d6623fafb846e49b1147c0fe219e79e2921` (`feat(focus): add terminal history backup and safe restore`). FM06 source, QA and documentation changes are uncommitted.
- Selected FM06 source/test diff SHA-256: `1bb95acb3b1ca40f414a407fa4f134138dfd85bd4cb13fd8281b11533ca59647`. The ledger and evidence doc are excluded from this source/test patch hash.
- Variant: `focusQaDebug`, app ID `com.luma.lifeos.focusqa.qa`, version code 8, version name `1.4.0-focusqa`. Instrumentation package: `com.luma.lifeos.focusqa.qa.test`.
- App APK SHA-256: `112af071680dc29f61782bfbe9e17965f87dfdfe8d3c905eebf1160f762d541b`.
- Instrumentation APK SHA-256: `45aeee49e6f785bbd5595445dd48e99cb40a145574e00e37ed654b00ee1262d3`.
- Device: `Medium_Phone_API_36.1` AVD, Android 16 / API 36, emulator serial `emulator-5554`, locale `Asia/Tehran`.
- The isolated package was installed for manual testing. Existing `com.luma.lifeos`, `com.luma.lifeos.qa` and `com.example.scrollfit` packages remained installed and were not targeted. Android's app-data reset was run only against `com.luma.lifeos.focusqa.qa`.

## Executed checks

1. `GRADLE_USER_HOME=/tmp/luma-f02-gradle-home ./gradlew :app:testFocusQaDebugUnitTest :core:data:testDebugUnitTest :app:assembleFocusQaDebugAndroidTest :core:data:assembleDebugAndroidTest :app:lintFocusQaDebug :core:data:lintDebug --continue --no-daemon --console=plain --offline` — **302/302 app host tests and 30/30 data host tests passed; both Android test APKs assembled; app and data lint exited 0.** See [final host/build/lint log](fm06-host-build-lint-final.log). There were no lint findings in the changed FM06 production files.
2. `GRADLE_USER_HOME=/tmp/luma-f02-gradle-home ./gradlew :app:connectedFocusQaDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.backup.BackupSettingsFlowTest --no-daemon --console=plain --offline` — **4/4 passed** on the requested emulator, including the action-bounds regression. See [final app connected log](fm06-connected-app-final.log) and its [JUnit report](fm06-api36-app-connected-junit.xml).
3. `GRADLE_USER_HOME=/tmp/luma-f02-gradle-home ./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.data.backup.BackupPersistenceTest,com.luma.lifeos.data.focus.FocusPersistenceTest --no-daemon --console=plain --offline` — **24/24 passed**, including archive v7 round-trip, schema-six replacement, invalid staged archive rejection, replacement-generation behavior and focus persistence. See [final data connected log](fm06-connected-data-retest.log) and the [JUnit report](fm06-api36-data-connected-junit.xml).
4. Focused Compose bounds test `BackupSettingsFlowTest.activeFocusReplacementActionsDoNotOverlap` passed after the fix. The earlier red run is retained in the Gradle report as the regression reproduction: before the fix, Cancel overlapped Discard.
5. `git diff --check` passed after final edits.

## Manual API 36.1 flow

- Started and paused a focus session in the isolated app. Export disclosed that a paused session is omitted unless stopped first; [the disclosure screenshot](fm06-export-prompt.png) is retained. Choosing “Continue without it” produced a synthetic archive in Downloads. The archive preview reported zero focus sessions.
- Selected that archive while the paused session remained open. The replacement dialog showed Stop, Discard and Cancel as three distinct vertical actions; see [the final confirmation screenshot](fm06-manual-restore-final.png). The pre-fix overlap is visible in [the initial reproduction screenshot](fm06-restore-confirmation.png).
- Cancel returned to Backup without replacing data. Reselecting the archive again showed the open-session actions, then explicit Discard completed replacement. The app displayed “Saved data restored”; Today showed zero saved focus sessions and “Start or resume focus,” with no restored active timer. See [restore success](fm06-manual-restored-final.png) and [post-restore Today](fm06-manual-post-restore-home-final.png).
- Created another paused session, opened Privacy → Delete local data, reviewed the full warning, and confirmed “Delete and reset Luma.” Android closed the app to the launcher. The isolated package stayed installed; relaunch showed an empty Today focus card and zero sessions. See [reset confirmation](fm06-manual-delete-dialog-final.png), [Android close result](fm06-manual-reset-result-final.png) and [clean relaunch](fm06-manual-post-reset-final.png).
- After evidence capture, removed the synthetic ZIP and uninstalled the isolated QA package. The emulator package inventory returned to the pre-QA set.

## Defects found and fixed

- Adding `onFinished` as a second optional trailing callback changed how existing Kotlin calls such as `confirmRestore { ... }` bind their lambda. A failed restore could invoke the success callback. `BackupViewModel` now keeps the original one-callback overload and provides a required-two-callback overload for the new UI behavior; a host regression verifies success/failure completion callbacks.
- `AlertDialog` placed Cancel in its dismiss slot beside a two-row Stop/Discard column. The tall confirm column overlapped Cancel at the bottom on this emulator. In active-session replacement, Cancel now stacks below Stop and Discard; the connected Compose bounds test verifies no overlap.

The iteration folder also retains preliminary failing logs from test assertion corrections and the callback regression discovery. The final green logs linked above are the authoritative results for this candidate.

## Remaining QA limits

- **QA10 PARTIAL:** not every legacy archive version or malformed payload was run; no stop-and-include export, archive/photo hash comparison, or exhaustive schema migration paths were exercised.
- **QA11 PARTIAL:** tests reject a stale old-generation start after restore, but queued checkpoint/action writes were not deliberately released across a real replacement/reset race. The full receipt-eviction replay matrix, reset refusal/reconciliation path, and post-reset preference inspection remain open.
- API 28, other OS versions/devices, TalkBack, keyboard/predictive Back, locale, font/window/orientation and theme matrices were not run. QA12–13 remain partial. The 50,000-session performance target (QA14) was not run. FM07 integrated acceptance and FM08 scope assessment remain open.

## Disposition

Record FM06 as **SCOPED API 36.1 QA PASS**, with QA10–11 **PARTIAL**. Do not mark F16 integrated acceptance complete.

**Suggested commit message:** `fix(focus): preserve restore callbacks and separate replacement actions`
