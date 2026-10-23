# Task due-time acceptance record

Date: 2026-10-23  
Implementation base: `ca8cf1b62aee411987f0c6c3a00b103636acb38a` (working-tree changes are uncommitted)  
Device/emulator: none  
Status: source implementation and production Kotlin compilation completed; behavioral acceptance remains pending.

## Checks performed

| Check | Result | Evidence |
| --- | --- | --- |
| Room schema generation | PASS | `bash ./gradlew :core:data:kspDebugKotlin` generated Room schema 6. |
| Production Kotlin compilation | PASS | `bash ./gradlew :core:data:compileDebugKotlin :app:compileDebugKotlin` exited 0 after the final UI cleanup. |
| Debug APK assembly | PASS | `:app:assembleDebug` completed. |
| Android test APK/source compilation | PASS | `:app:assembleDebugAndroidTest` and `:core:data:assembleDebugAndroidTest` completed; test sources compiled but were not executed. |
| Lint | PASS | `:app:lintDebug` and `:core:data:lintDebug` completed. |
| Whitespace/error-marker check | PASS | `git diff --check` exited 0. |
| Gradle unit or instrumentation tests | NOT RUN | Explicitly excluded by the user. Test source was compiled as part of APK assembly but not executed. |
| Emulator/device interaction | NOT RUN | Explicitly excluded by the user; no device was used. |
| Visual/accessibility matrix | NOT RUN | No emulator or device was used. |

## Acceptance IDs

All DUE acceptance scenarios are **NOT RUN** because they require behavioral tests or device validation, which the user explicitly prohibited. Source and regression-test cases were added for these requirements, but that is not runtime evidence.

| ID | Scenario | Result |
| --- | --- | --- |
| DUE-01 | Minute precision, midnight, absent time, and invalid date/time combinations | NOT RUN |
| DUE-02 | Picker add/change/remove, cancel/dismiss, editability and date changes | NOT RUN |
| DUE-03 | Dirty state, save/replay/conflict, saved-state recovery and restore invalidation | NOT RUN |
| DUE-04 | Localized task/Today/completed/widget labels and summary privacy | NOT RUN |
| DUE-05 | SQL/domain order, paging, calendar-day buckets and Today eligibility | NOT RUN |
| DUE-06 | Populated Room 5→6 and historical migrations | NOT RUN |
| DUE-07 | Schema-5 backup round trip, exact schema-4 import and malformed archive rejection | NOT RUN |
| DUE-08 | Timezone/DST civil-time behavior and window/font/RTL/IME accessibility matrix | NOT RUN |

The first Android test APK assembly exposed three Kotlin source errors in `TodoEditorFlowTest`; these were fixed, and the build/lint command then exited 0. Additional picker interaction cases were added and `:app:compileDebugAndroidTestKotlin` exited 0. Some planned locale/hour-preference and completed/Today/summary assertions remain unauthored, in addition to the unrun behavior matrix. No screenshots or behavioral results were collected. Do not treat compilation or lint as a substitute for the unrun acceptance scenarios.
