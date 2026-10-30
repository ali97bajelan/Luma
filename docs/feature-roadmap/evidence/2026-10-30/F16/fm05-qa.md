# FM05 reports and history QA — 30 October 2026

**Result:** FM05 scoped QA **PASS**. Host suites, focused API 36.1 instrumentation and both lint targets passed on the final FM05 source/test tree. QA06–09 have verified subsets and remain partial; the full F16 platform and integration matrix is open.

## Candidate and environment

- Candidate base: `c6e73a5220e4f2e5a116230444324c4c636c7a53`.
- Sorted FM05 source/test tree SHA-256: `5a62e7e10ffc0fe16071e345bd950078c1d2db9ce1d237fbaa5b9cf062c796c4` across 17 source/test files. The FM05 source, QA and documentation changes are uncommitted.
- Variant: `focusQaDebug`, application ID `com.luma.lifeos.focusqa.qa`, version code `8`, version name `1.4.0-focusqa`. Instrumentation package: `com.luma.lifeos.focusqa.qa.test`.
- App APK SHA-256: `b8ee7b9dcbcdc56cacfba8b832d13f93b8228bef3e91f797d102742c9330a1e4`.
- Instrumentation APK SHA-256: `444c1742df3ab525e82dd0c348cef3f9ad87e9815493e83abcc9dfd74dbbc385`.
- Device: `Medium_Phone_API_36.1` AVD; Android 16 / API 36; `sdk_gphone64_x86_64` emulator. The QA variant is isolated from the normal app package.

## Executed checks

1. `gradle :app:testFocusQaDebugUnitTest :app:connectedFocusQaDebugAndroidTest :app:lintFocusQaDebug :core:data:lintDebug -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.ui.focus.FocusReportsTest --offline --no-daemon --console=plain --continue` — **301 app host tests passed; 3/3 selected connected tests passed; app and data lint exited 0.** The final combined output is in [the final QA log](fm05-final-qa.log); host reports are in [the app JUnit archive](fm05-app-host-junit.zip).
2. `gradle :core:data:testDebugUnitTest --offline --no-daemon --console=plain` — **29 passed**, exit 0. See [the data host log](fm05-data-host-tests.log) and [JUnit archive](fm05-data-host-junit.zip).
3. `gradle :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.data.focus.FocusReportPersistenceTest --offline --no-daemon --console=plain` — **3/3 passed** on the AVD. See [the connected log](fm05-data-connected-tests.log) and [JUnit report](fm05-api36-data-connected-junit.xml).
4. App lint reported 154 project-wide warnings and 10 informational issues; it has no findings in the FM05 focus source files or FM05 plural resources. Data lint reported 0 issues. Reports: [app lint XML](fm05-lint-focusqa.xml) and [data lint XML](fm05-lint-data.xml).
5. `git diff --check` passed. EN/FR/FA XML parsed; both focus plural resources are present in every locale.
6. Host-screen regressions: `TodayPersistenceTest` **2/2 passed** and `WeeklyReviewFlowTest` **16/16 passed** on the same AVD when run by class. `AppNavigationTest` was **8/9**; `dataWidgetCaptureUsesOneCurrentTimeChooserFromEitherProvider` failed only in `ActivityScenario` cleanup because the Activity remained PAUSED. The same failure reproduced with the FM05 `focusRepository` argument temporarily omitted, and the production wiring was restored before the final passing FM05 build. Logs: [combined regression](fm05-app-integration-regression.log), [Today](fm05-today-integration-retest.log), [Review](fm05-review-layout-retest.log), [AppNavigation](fm05-navigation-integration-retest.log), and [repository-wiring isolation](fm05-navigation-wiring-isolation.log).

## Behavior covered

- Four host tests verify exact day and paired week ranges, independent report failure/cancellation behavior, a 50.0% nonempty-baseline comparison, and 23/25-hour DST day lengths.
- Three app Compose tests verify Today’s saved total remains separate from an in-progress timer, contributor/interrupted counts and actions; partial-week copy and no percentage for an empty previous period; and credited run details plus cancel/confirm deletion behavior.
- Three Room tests verify exact cross-midnight and cross-week clipping with distinct contributors, full-duration versus selected-range history, delete-driven report invalidation, stable UUID ordering for equal timestamps, and 50-row pages (50 + 1 rows for 51 records).
- The combined 27-test Today/Review/AppNavigation regression invocation had three failures. Running the classes separately cleared the Today (2/2) and Review (16/16) failures; the one AppNavigation teardown failure remained and also occurred with the new repository wiring omitted. The individual focused FM05 UI/Room suites pass on the final restored wiring.
- QA corrected a weekly test fixture expectation: its existing cross-midnight session also fell in the current week, so the correct aggregate was 50 minutes (30 + 20). The test now asserts each source row’s clipped contribution and the total separately.
- The interrupted-session count uses localized plural resources in English, French and Persian. The UI test verifies the English singular rendering; locale catalog tests and XML parsing pass.

## Remaining QA limits

- QA06–09 remain **PARTIAL**. The run did not exercise zone changes, every exact day/week endpoint, all specified week source/back-stack combinations, every comparison rounding and unavailable state, interrupted/zero/open/paused deletion cases, or concurrent report/delete races. DST lengths were checked in host tests, not through locale-specific emulator rendering.
- QA12/13 remain **PARTIAL**. No API 28, TalkBack, keyboard/predictive Back, locale visual, theme, window-size, font-scale or orientation matrix was run. The one AppNavigation teardown failure remains open. The emulator checks target report components and Room repository; no full manual navigation walkthrough or screenshot review was recorded.
- QA14 performance remains **NOT RUN**. No 50,000-session data set or 5-warm-up/30-measurement representative-device benchmark was run. The 51-row test checks paging correctness only.
- FM06 restore/reset, FM07 integrated feature acceptance and FM08 enforcement assessment remain pending.

## Disposition

Mark FM05’s QA phase **SCOPED API 36.1 PASS**. Keep QA06–09 partial and QA14 not run; retain the full matrix and integrated acceptance as pending.

**Suggested commit message:** `feat(focus): add reports and paged session history`
