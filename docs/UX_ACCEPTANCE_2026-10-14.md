# UX acceptance checklist and validation runbook

Status as of 17 October 2026: all findings have implementation changes. Tasks 17 and 18 have targeted JVM and isolated-emulator regressions passing. T19's French navigation behavior has automated layout/semantics coverage; broader manual acceptance remains open. Historical results stay in [the UX review](UX_REVIEW_2026-10-13.md) and [VALIDATION.md](VALIDATION.md); this checklist does not replace that evidence.

Read [the development handoff](UX_DEVELOPMENT_READY_2026-10-14.md) before implementation or acceptance. Record the tested source snapshot, device/API, app ID, language, layout direction, font scale, keyboard/assistive technology, result and evidence path for every run. Use PASS, FAIL, BLOCKED or NOT RUN; never infer manual acceptance from compilation.

Snapshot for the 14 October runs: base HEAD `b52b9a7` plus that run's uncommitted working tree. No commit was created for that dated run.

## New implementation acceptance

| Finding / criteria | Fixture and operation | Expected result | Result / evidence |
| --- | --- | --- | --- |
| T19/T21 / French primary navigation | English/French/Persian resources at 320/411 dp and font scales 1.0/1.3/2.0; assert labels, full accessible names, tab roles/state/actions and text bounds | French uses compact readable labels; all full localized names remain accessible; labels fit within two lines | Automated PASS on API 36.1, isolated `com.luma.lifeos.uxvalidation`; see `/tmp/luma-t19-final-api36.log`. Forced Compose layout does not certify TalkBack speech or physical-device layout. |
| 13 / S13.1–S13.3 | Text + two dates + two same-name people + tag + place; close dialog, remove one, Reset filters, Clear all | Named chips; identity-safe removal; text retained only by Reset; explicit no-filter state | Automated PASS for chip identity, removal, reset/clear and empty-state regressions; full walkthrough pending |
| 13 / S13.4–S13.5 | Recreate/return from detail; delete a selected catalog row; fail options; invalid dates; finish an old uncancellable query | Selections retained/identified; correction possible; current generation wins | Automated targeted Search tests PASS, including removal while options load/fail; correction UI and manual lifecycle/provider walkthrough pending |
| 13 / S13.6 | 100 tags + 100 people + place + two dates at 320 dp/scale 2.0/RTL | Filter section scrolls with results; fixed controls operable | Automated PASS in forced Compose component layout; full production SearchRoute and native device walkthrough pending |
| 14 / A14.1 | Traverse editor, Privacy and Review headings with TalkBack | Meaningful section traversal and normal field labels | Automated heading/semantics regressions PASS; TalkBack navigation/speech pending |
| 14 / A14.2–A14.3 | Reject length on two fields, repeat retained callbacks, edit unrelated field, correct one, recreate | Last accepted text retained; independent field errors/restoration | Targeted ViewModel and field-semantics regressions PASS; manual recreation walkthrough pending |
| 14 / A14.4 | Invalid local timestamp, rejected 51st distinct tag, photo import/removal failure | Local named errors/retry; tag retained and Save blocked; photo context revealed and retained attachments explained | Targeted capture regressions PASS, including dialog dismissal and failed removal; manual provider/import walkthrough pending |
| 14 / A14.5–A14.6 | Inject save/delete/load failures and settled search/import updates; fake accepted reset request | Visible feedback, polite single owner, existing retries; no reset replay/false erasure claim | Targeted settled-feedback regressions PASS; full injected-failure/TalkBack walkthrough pending |
| 16 / C16.1–C16.3 | Fresh mood/journal/direct memory/promoted memory, expand/collapse, recreate; en/fr/fa | Exact descriptive copy/state; no mood-photo promise; timestamps remain outside; drafts retained | English/French/Persian resource and disclosure-state regressions PASS; language switching and manual layout/speech checks pending |
| 17 / D17.1–D17.3 | Durations 0/null/1/999/1,000/59,999/60,000/3,599,999/3,600,000/Long.MAX_VALUE; ±1 ms deltas | Same summary precision, positive never zero, localized exact details/signs | PASS: projection boundaries incl. Long.MAX, English/French/Persian rendering, summary parity, null and exact detail. NOT RUN: Review ±1 ms sign assertion and manual RTL/TalkBack. |
| 17 / D17.4–D17.6 | Stable equal timestamps, 51+ Review sources, two marked memories; partial/aggregate-zero history | Correct labels/paging; newest memory identity; missing/zero/coverage unchanged | PASS: Search and reverse-insertion person/place fixture order, order labels, latest-memory flow, and saved-usage/Review regressions. NOT RUN: equal-time/51+ page-union device rerun; manual ongoing/coverage and RTL checks. |
| 18 / N18.1–N18.3 | Long journal/mood/direct memory/imported event and 60+ person/place history; scroll to end, Back/Edit | Controls displayed; conditional Edit; invoking date/query/week/catalog restored | PASS: long journal/mood-event/person/place scroll, read-only/missing edit visibility, Back/Edit actions, and Search/Review/catalog flow regressions. NOT RUN: long direct-memory route and full manual source-context matrix. |
| 18 / N18.4–N18.6 | Live deletion, error/retry, loading/missing, photos, RTL/landscape/large title | No stale Edit; Back persists; no content/inset overlap; keyboard/TalkBack actions reachable | PASS: deleted catalog state hides Edit and keeps Back; shared bar test checks 320 dp, RTL, 2.0 font scale, 48 dp targets and body clearance. NOT RUN: TalkBack, keyboard, landscape, device insets and route retry/loading/error matrix. |

## Outstanding checks for implemented findings

These are required follow-ups from the review, not new feature scope. Already passing automated regressions should be reused; their absence from this run is not a new defect.

| Finding | Remaining walkthrough | Evidence to capture | Result |
| --- | --- | --- | --- |
| 01 | From a past browsed date, capture mood/journal/memory; inspect visible timestamp, save in disposable data and reopen | Chosen date/time/timezone and saved record timestamp for each editor | NOT RUN |
| 02 | Focus screen-time/step import switches with TalkBack; test off/on/disabled using fake integrations where possible | Spoken feature name and checked/disabled state; keyboard operation | NOT RUN |
| 03 | Mixed manual/usage day, usage hidden; change counts/delete beyond anchor during fallback read | Filter remains off; no usage insertion; consistent filtered count | NOT RUN |
| 04 | Review week and day arrows in Persian; cross month/week boundary | Opposite icons, correct date transition and spoken names | NOT RUN |
| 05–06 | Populated day and compact cards at 320/360 dp, scales 1.0/1.3/2.0, en/fr/fa, light/dark | Entry readability, summary/card hierarchy, wrapping, memory shortcut target/name/state | NOT RUN |
| 07 | Open mood/journal/direct/promoted memory from Today/Timeline/Search/person/place/Review; read → edit; memory action | Same reading flow, correct source ID, TalkBack action availability | NOT RUN |
| 08 | Review overview/sections/evidence with a full and partial week, unavailable and known-zero measurements | Overview usable; section focus; source period/coverage intact | NOT RUN |
| 09 | Settings hub → each focused page → Back; Review → saved usage | No usage feed in configuration; deliberate read-only saved-usage route; keyboard/TalkBack | NOT RUN |
| 10 | Fresh mood scale before selection, Persian RTL and scale 2.0 | Visible low/high anchors align and speak correctly before choice | NOT RUN |
| 11 | Real 50-tag fixture, pending 51st distinct tag, Save, correction/retry; long tag | Input retained, field revealed/error spoken, no save until pending tag accepted | NOT RUN |
| 12 | 500-row day, reach 300-row window cap in both directions; insert/delete while browsing | Visible anchor stable, automatic older trigger, no gaps/duplicates or unexpected jumps | NOT RUN |
| 15 | People/Places selector, create/edit place, link/unlink entries, live updates/deletion | Discoverability, linked history, event preservation and RTL/TalkBack | NOT RUN |

## Shared manual matrix

Use generated journal/photo/catalog records in a disposable install. For each new unit and touched existing flow:

- English, French, Persian; actual resource language switching and Persian RTL, not only forced layout direction.
- Widths 320 dp and a representative normal phone; portrait and landscape; font scales 1.0, 1.3 and 2.0; light/dark where color feedback changes.
- Conventional docked software keyboard: focus fields, IME actions, scroll errors, keep Save visible; restore user's device settings after testing.
- TalkBack: heading navigation, names/roles/states, removal, local errors, polite updates, retry and focus stability. Confirm speech, not only accessibility XML.
- Long user names/titles/body, duplicate catalog names, missing/deleted selection, blank and populated states, missing versus known zero.
- Activity recreation, source invalidation, detail return, and a controlled process-recreation probe with an unsaved generated draft. Verify draft content, field flags and selection restoration; never kill the personal app for a probe.
- Inject load/save/import/media failures using fake services. Real provider grants are a separate explicit native test, not necessary for semantics tests.

For broader states explicitly left open by the review, validate backup export/restore, provider access/revocation outcomes and real local-data reset only in the isolated install with generated data. Record native behavior and interruption/lifecycle results; a fake reset acceptance test proves UI behavior only. Do not reset personal data or use a real personal backup for these checks. Reliability fixes discovered here get their own issue/spec; they are not silently added to this UX feature scope.

## Repeatable automated commands

Commands below reproduce the implementation-time checks. The affected unit tests, shared JVM regression sweep, debug/test APK builds, and app lint were run on 14 October 2026. No dependency upgrades are required.

```sh
bash gradlew :app:testDebugUnitTest :core:domain:test :core:data:testDebugUnitTest :integration:usage:testDebugUnitTest :integration:health:testDebugUnitTest
bash gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
git diff --check
```

To keep fixture writes out of the personal app, create a local init script and build a distinct disposable application ID. This repeats the already documented finalizeDsl approach in VALIDATION.md without changing production Gradle files:

```sh
cat > /tmp/luma-ux-validation.init.gradle <<'GRADLE'
gradle.beforeProject { project ->
    project.plugins.withId('com.android.application') {
        project.androidComponents.finalizeDsl { android ->
            android.defaultConfig.applicationId = 'com.luma.lifeos.uxvalidation'
        }
    }
}
GRADLE
bash gradlew -I /tmp/luma-ux-validation.init.gradle :app:assembleDebug :app:assembleDebugAndroidTest
adb devices -l
```

Select an available disposable emulator/device after inspecting that output; replace the example serial when necessary. Confirm the built APK's application ID is `com.luma.lifeos.uxvalidation` using SDK `apkanalyzer manifest application-id` before installing. The test package must target that ID. Rebuild normal APKs without the init script before any later personal-app update; this runbook itself does not install to the personal app.

```sh
adb -s emulator-5554 install -r -t app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r \
  -e class com.luma.lifeos.ActiveSearchFiltersTest,com.luma.lifeos.SearchFlowTest \
  com.luma.lifeos.uxvalidation.test/androidx.test.runner.AndroidJUnitRunner
```

Use each plan's fully qualified targeted classes. Other new classes are `com.luma.lifeos.CaptureAccessibilityTest`, `com.luma.lifeos.FeedbackAccessibilityTest`, `com.luma.lifeos.DurationDisplayTest` and `com.luma.lifeos.DetailNavigationTest`. Existing feature suites have the package paths shown in their source (`com.luma.lifeos.review.*`, `.usage.*`, `.privacy.*`, `.health.*`, `.summary.*`).

The repository has previously encountered an unresolved optional UTP test-output plugin for `connectedDebugAndroidTest`. Try the normal connected task with the same validation init script if dependencies are available; if it fails for that documented tooling reason, direct AndroidJUnitRunner on the built APKs is a valid fallback. Inspect its full output for `OK (N tests)`, failures and assumptions/skips; ADB exit code alone is insufficient. Record the exact APK/commit and invocation. Never describe a compiled APK as passing instrumentation.

## Recorded automated run — 14 October 2026

- PASS: 313 shared JVM tests (176 app, 95 domain, 23 data, 13 usage, 6 health), zero failures/errors/skips; debug app and Android test APK builds; and app lint.
- PASS: 33 targeted AndroidJUnitRunner tests on API 36 (`sdk_gphone64_x86_64`) using the isolated application ID `com.luma.lifeos.uxvalidation`. The cases cover Search filter chips and flows, including unavailable-selection removal in loading/error states; timestamp feedback after dialog dismissal; failed photo removal with retained attachments; editor/capture accessibility; Privacy feedback; and Weekly Review. The many-filter Compose test forces a 320 dp surface, font scale 2.0 and RTL; that is an automated layout test, not a manual system configuration check.
- The emulator is 1080×2400 at 420 dpi. Test code sets English for the Review suite. The app package was isolated and its data cleared between runs.
- PASS: `git diff --check` after implementation and documentation updates.
- Minor coverage remains deferred for an actual 203-filter production SearchRoute viewport test, interactive invalid-date correction UI, keyboard-open save-failure UI and the accepted Privacy reset-request fake-service flow. VM/component behavior is covered; see the manual matrix for device checks.
- PENDING: actual TalkBack speech/navigation, conventional keyboard operation, system language switching, physical narrow/landscape layouts and manual 320 dp / 1.3 / 2.0 text-scale walkthroughs. Automated semantics tests do not close these checks.

The exact instrumentation class list and run notes are in [VALIDATION.md](VALIDATION.md). The UX review records the implementation behavior. Keep findings marked implemented until the pending manual checks are completed.

## Tasks 17/18 automated run — 14 October 2026

- PASS: `bash gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` with the normal application ID. The full app JVM suite, both APK builds and app lint completed successfully. `git diff --check` also passes.
- PASS: 45 affected AndroidJUnitRunner tests across isolated runs on API 36, emulator `emulator-5554` (`sdk_gphone64_x86_64`), application ID `com.luma.lifeos.uxvalidation`. No test data was written to the installed personal package. The isolated package was cleared before the main run.
- Main run classes: `com.luma.lifeos.DurationDisplayTest`, `DetailNavigationTest`, `JournalCaptureTest`, `TimelineFlowTest`, `SearchFlowTest`, `PlaceCatalogFlowTest`, `summary.DailySummaryFlowTest`, `usage.ReviewUsageFlowTest`, and `review.WeeklyReviewFlowTest` — **38 tests passed**.
- Follow-up classes: `PeopleCaptureTest`, `MoodCaptureTest`, and `MemoryCaptureTest` — **5 tests passed** after fixing two stale PeopleCapture assumptions (context details start collapsed and the current empty-history copy). The PeopleCapture class was rerun after the corrections and passed 2/2. `PlaceCaptureTest` passed its 2 tests in a separate run.
- Instrumentation APKs were built with `/tmp/codex-luma-ux-17-18.init.gradle`, which sets the application ID to `com.luma.lifeos.uxvalidation`. The main invocation was:

```sh
/home/bajelan/Android/Sdk/platform-tools/adb -s emulator-5554 shell am instrument -w -r \
  -e class com.luma.lifeos.DurationDisplayTest,com.luma.lifeos.DetailNavigationTest,com.luma.lifeos.JournalCaptureTest,com.luma.lifeos.TimelineFlowTest,com.luma.lifeos.SearchFlowTest,com.luma.lifeos.PlaceCatalogFlowTest,com.luma.lifeos.summary.DailySummaryFlowTest,com.luma.lifeos.usage.ReviewUsageFlowTest,com.luma.lifeos.review.WeeklyReviewFlowTest \
  com.luma.lifeos.uxvalidation.test/androidx.test.runner.AndroidJUnitRunner
```

- Layout coverage includes a shared detail-bar test at 320 dp, RTL and 2.0 font scale, plus long-scroll route tests. It is automated Compose geometry/semantics coverage, not manual device, keyboard or TalkBack acceptance.
- NOT RUN: actual TalkBack speech/traversal, system locale switching, physical landscape/narrow layouts, keyboard interactions, manual inset inspection, Review source 51+ page union/equal-timestamp UI fixture, or the complete N18 route retry/loading/error matrix.

## T19 accessibility and language update — 17 October 2026

- PASS: primary-navigation Compose regression across English/French/Persian, 320/411 dp and font scales 1.0/1.3/2.0. It checks visible strings, full localized content descriptions, tab role, selected state, click action, text line count/width and containment inside each tab. Persian resources and RTL composition are included. Evidence: `/tmp/luma-t19-final-api36.log`.
- PASS: `AppNavigationTest`, four tests covering all primary routes, Quick Capture reachability, selected-day preservation and route restoration. Evidence: `/tmp/luma-t19-final-api36.log`.
- PASS: `PlatformLanguageTest` and `LanguageFlowTest`, five tests on API 36 covering app-language choices, Persian RTL, activity recreation, selected-day retention and an in-progress journal draft. Evidence: `/tmp/luma-t19-final-api36.log`. These exercise API 33+ LocaleManager behavior, not the legacy SharedPreferences branch.
- PASS: focused JVM `AppLanguageTest` and `TranslationCatalogTest`; compact-label keys exist in English, French and Persian.
- BLOCKED: API 28 and API 32 native language tests and force-stop relaunch. The local SDK has only `android-36.1/google_apis_playstore/x86_64` and the only AVD is `Medium_Phone_API_36.1`; inventory: `/tmp/luma-t19-device-inventory.log`. No legacy emulator was created and no existing AVD data was wiped.
- NOT RUN: actual TalkBack speech/focus traversal, conventional keyboard walkthrough, physical 320 dp/normal-size layouts, landscape, insets, and the outstanding route-specific manual matrix. The API 36 emulator was headless, and Compose semantics/layout tests do not substitute for spoken-output or manual acceptance.
- T19 remains pending for the manual acceptance, legacy API validation and separate Timeline/Search/Review performance targets. No dependency, permission, schema, network or product-scope change was made.

## Exit criteria

- All five implementation findings have their spec criteria implemented and appropriate automated checks passing.
- Each pending manual check has a result/evidence or a specific blocker. A blocked manual check remains open.
- Translation coverage/formatting, keyboard, RTL, large-text and actual TalkBack checks are recorded separately.
- No added permission/dependency/schema/network change, no personal-data mutation, no lost drafts or read-only edit regression.
- UX tracker distinguishes implemented from accepted. Release acceptance stays open for unrelated TASKS.md performance/reliability work.
