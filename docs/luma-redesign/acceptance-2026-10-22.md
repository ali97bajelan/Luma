# Redesign acceptance ledger

Status: **NOT ACCEPTED**. T33 remains **blocked**. The source contrast/font audit, all 271 unit tests, debug assembly and lint pass. A clean API 36 instrumentation run reports 15 failures and 1 skipped test. The focused 36-test Timeline/Review/navigation suite passes after fixing the large-text EventCard density, provider-absent Review routes, and stale disclosure/time-format assertions. T30, T31, T32-C and T32-D still have connected failures; the rendered locale/theme/window/accessibility matrix remains NOT RUN.

Planning baseline and run date: 2026-10-22. Tested source baseline revision: `3825167781a29cd25f7e37c761d32a787edddec8`; the tested working tree also included the follow-up code and test changes described in the [validation recheck](evidence/2026-10-22/t33-revalidation.md). JDK 17.0.20.1, Gradle 9.1.0, offline. Debug APK SHA-256: `5b67fb18e710a3f664fb717577bf2ea12f4f9a3a15af0351c380da0ae3d01ae6`. The clean run used a disposable cloned API 36.1 emulator: 1080 × 2400 px at 420 dpi (about 411 × 914 dp), font scale 1.0. Locale property was unset and was not recorded. Detailed commands and failures are in the [validation recheck](evidence/2026-10-22/t33-revalidation.md).

| Area | Status | Evidence / follow-up |
| --- | --- | --- |
| T30 source role contrast and font metadata/coverage | PASS | [Source audit](evidence/2026-10-22/t30-source-audit.md): 158 opaque light/dark role pairs and bundled-font metadata/coverage; 0 source-audit failures. This is source evidence. |
| T33 unit tests, debug assembly and lint | PASS | Fresh run: 271 unit tests, 0 failures/errors/skips; debug APK assembled; lint completed with 144 warnings. |
| Connected instrumentation suite | FAIL | Clean run: 224 cases, 15 failures, 0 errors, 1 skipped and 208 passed. See the grouped failure list in the [validation recheck](evidence/2026-10-22/t33-revalidation.md). |
| Focused Timeline/Review/navigation regression suite | PASS | 36 tests, 0 failures, 0 skipped. Includes EventCard 100%/130%/RTL, Timeline disclosure and date-only log behavior, Review provider fallbacks, paging, and Review/navigation flows. |
| T33 provider-absent Review route handling | PASS (focused) | Source and usage details render localized unavailable messages with an actionable Back control when providers are absent. Both route fallbacks pass instrumentation; visual matrix remains open. |
| Current-build connected test availability | PASS | Debug and test APKs ran on a clean disposable cloned emulator. Configured AVD user data was not used or cleared. |
| Current-build rendered review and six primary captures | NOT RUN | No screenshots were retained; the instrumented run is not visual acceptance. |
| English/French/Persian × Light/Dark/System × required size/font matrix | NOT RUN | 320 dp at 2.0, 411 × 891 dp at 1.0 and 840 dp width remain uninspected. RTL, TalkBack, keyboard focus, animation scale and rendered contrast also remain open. |
| T30 | BLOCKED | `LanguageFlowTest.journalDraftTextTagsAndDateSurviveLanguageChangeThenSaveAndDetail` failed in the full connected run; isolate and rerun. |
| T31 | BLOCKED | `HabitCaptureFlowTest.todayPreviewShowsScheduledRowsBeforeOlderUnscheduledHabitsAndCapsAtTwo` failed; isolate the scheduled-habit count assertion. |
| T32 coordinator | BLOCKED | T31, T32-C and T32-D failures and child rendered acceptance prevent coordinator acceptance. |
| T32-A | IMPLEMENTED (VALIDATION PENDING) | The 36-test focused run passes its Timeline/EventCard classes, including larger-text fit, RTL action targets, disclosure controls, date-only grouping, paging and source navigation. Required rendered and accessibility evidence remains NOT RUN. |
| T32-B | IMPLEMENTED (VALIDATION PENDING) | Focused Review/navigation flows and both provider-absent fallback tests pass. Rendered comparisons and the required locale/window/accessibility matrix remain NOT RUN. |
| T32-C | BLOCKED | Search recovery, habit management/navigation and task recovery assertions fail in the full run; investigate before assigning production causes. |
| T32-D | BLOCKED | Nine connected failures remain in Quick Capture navigation, Journal, Memory, Mood, People and Place flows; isolate before assigning production causes. |
| T35 Habit editor source refinement | IMPLEMENTED (VALIDATION PENDING) | Source-only implementation recorded in [T35 evidence](evidence/2026-10-23/t35/acceptance.md). H35-1–5, visual/accessibility matrix and runtime behavior remain NOT RUN; no Gradle task or emulator was run by request. T32-D remains blocked. |
| Optional O01–O08 | Deferred / not selected | No optional behavior was implemented or accepted. |

No task is marked complete. Reconsider T33 after the remaining connected failures are isolated and resolved or shown to be stale, and after the required visual/accessibility evidence is recorded.
