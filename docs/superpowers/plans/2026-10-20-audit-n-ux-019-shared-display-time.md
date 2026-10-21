# UX-019 — Saved records and capture use different hour-cycle policies Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use superpowers:subagent-driven-development only if the user selects delegation. Steps use checkbox syntax for tracking. This documentation request does not start implementation or delegate work.

**Goal:** Use the app locale and Android 12/24-hour preference consistently for capture and saved-record time displays.

**Architecture:** Create an explicit DisplayTimePolicy at the Compose boundary and pure locale-aware formatter methods. Migrate display call sites while leaving stored instants and Gregorian civil dates intact.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose/Material 3, lifecycle StateFlow and the existing Android/Room modules; existing dependencies.

**Spec:** [UX-019 specification](../specs/2026-10-20-audit-n-ux-019-shared-display-time-design.md)

**Status:** Implementation complete; regression tests and runtime verification remain pending at the user's request. Relative effort: Medium.

## Global Constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies unless a task explicitly requires a reviewed exception.
- All changed user-facing copy must exist in EN/FR/FA with matching resource names and numbered placeholder signatures; preserve RTL and intended Gregorian display/storage behavior.
- Keep semantic light/dark palettes, at least 48 dp interactive targets, and reachability at 100%/130%/200% font, narrow portrait and short landscape.
- Preserve offline/private storage, existing consent boundaries, stable record/command IDs, original retry payloads and Undo protections.
- Never truncate accepted personal content, infer write failure means no commit, or treat an unavailable privacy mode as known.
- Only implement this task and its declared prerequisites; preserve unrelated working-tree changes and existing navigation/draft state.

## Review Focus

These five concrete failure modes are pinned to the regression cases owned by this task. The assertions below are requirements for tests to add, not claims that those tests exist or currently pass.

- **RF-1 → AC-1:** Display 13:05 in capture, saved card, Search, Review and history under 12-hour preference. All use 1:05 with localized day period; no surface silently renders 13:05. Covered by `sameInstantUsesSameHourCycle` in the regression step below.
- **RF-2 → AC-2:** Render the same instant with 24-hour preference in EN/FR/FA. All use the explicit 24-hour cycle and correct locale digits. Covered by `explicit24HourPolicy` in the regression step below.
- **RF-3 → AC-3:** Switch Android hour setting while app is backgrounded. Existing screens update their time policy on resume without data writes. Covered by `resumePicksUpPreferenceChange` in the regression step below.
- **RF-4 → AC-4:** Render an instant across midnight in Asia/Tehran and a DST-transition zone. Displayed civil date uses its intended zone and Gregorian calendar; stored instant is unchanged. Covered by `zoneAndCalendarStayStable` in the regression step below.
- **RF-5 → AC-5:** Display Persian time beside an IANA zone/package identifier at 200% text. Localized digits and bidirectional text remain readable with no forced storage transformation. Covered by `mixedDirectionIdentifiersStayReadable` in the regression step below.

---

## Task 1: Deliver UX-019

**Files and responsibilities:**

- Modify: `app/src/main/java/com/luma/lifeos/language/LocalizedFormats.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/common/CaptureTimestampRow.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/timeline/EventCard.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/habits/HabitHistory.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/review/ReviewSourcesRoute.kt`
- Create: `app/src/main/java/com/luma/lifeos/language/DisplayTimePolicy.kt`
- Extend test: `app/src/test/java/com/luma/lifeos/language/LocalizedFormatsTest.kt`
- Extend test: `app/src/androidTest/java/com/luma/lifeos/language/PlatformLanguageTest.kt`
- Localized resources, only when copy changes: `app/src/main/res/values/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fr/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fa/strings.xml`

Keep changes in their current screen/state/repository responsibility. New files own only the interface or component named in the specification. If a listed existing file has changed since this plan, reconcile with the live code before editing; do not revert user changes to match the audit.

**Interfaces:**

- Consumes the current route/ViewModel/repository contracts in the files listed above; no new cross-task API is required.
- Produces: data class DisplayTimePolicy(val locale: Locale, val use24Hour: Boolean) has time(): DateTimeFormatter and dateTime(style: FormatStyle): DateTimeFormatter. @Composable rememberDisplayTimePolicy(): DisplayTimePolicy reads app configuration locale and DateFormat.is24HourFormat(context), refreshing on resume/configuration changes. Remove the implicit true hour-cycle default once all callers migrate.
- Other consumers: migrate every caller affected by these interface changes; preserve callback target identity and existing readiness/busy guards.

- [x] **Step 1: Read the spec and confirm prerequisites.** Inspect each listed file and the working-tree diff; preserve unrelated edits. No implementation prerequisite is required. Classify missing tests as additions, not evidence of an already passing regression.

- [ ] **Step 2: Add the behavior regressions and manual checks.** Use the listed test suites, keeping state/repository assertions in JVM tests where provided and navigation/Compose assertions in device tests. Use the named cases, fixtures and assertions below; extend existing fixtures instead of introducing a parallel harness. For speech, gesture comfort and visual reachability, automate the semantics/bounds that can be checked and record actual manual device evidence under the same case ID. A test cannot certify TalkBack speech merely by finding a live-region node. For copy-only changes, extend existing UI assertions.

| Case | Regression / evidence case | Fixture / action | Assertions |
|---|---|---|---|
| AC-1 | `sameInstantUsesSameHourCycle` | Display 13:05 in capture, saved card, Search, Review and history under 12-hour preference. | All use 1:05 with localized day period; no surface silently renders 13:05. |
| AC-2 | `explicit24HourPolicy` | Render the same instant with 24-hour preference in EN/FR/FA. | All use the explicit 24-hour cycle and correct locale digits. |
| AC-3 | `resumePicksUpPreferenceChange` | Switch Android hour setting while app is backgrounded. | Existing screens update their time policy on resume without data writes. |
| AC-4 | `zoneAndCalendarStayStable` | Render an instant across midnight in Asia/Tehran and a DST-transition zone. | Displayed civil date uses its intended zone and Gregorian calendar; stored instant is unchanged. |
| AC-5 | `mixedDirectionIdentifiersStayReadable` | Display Persian time beside an IANA zone/package identifier at 200% text. | Localized digits and bidirectional text remain readable with no forced storage transformation. |

- [ ] **Step 3: Establish the regression.** Run the relevant targeted command below before implementation. New-interface tests may initially fail to compile; existing-behavior tests must fail on the specified mismatch. Record the actual failure. For a runtime-risk finding, first exercise its constrained fixture; if it already passes, record that result and retain the reachability regression without claiming a reproduced failure.

```bash
./gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.language.LocalizedFormatsTest'
```

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.language.PlatformLanguageTest
```

Device commands require a connected, configured test device/emulator. Locale/font/window and TalkBack settings are deliberate validation inputs; the command alone does not configure or certify that matrix. Do not install dependencies or alter the execution environment as part of this documentation task.

- [x] **Step 4: Implement the owning contract.** Add pure DisplayTimePolicy and configuration/resume-aware Compose provider. Formatter tests remain pending.

- [x] **Step 5: Connect the behavior.** Run a complete formatter-call inventory and migrate capture plus saved-record surfaces, updating APIs where a policy must be passed.

- [ ] **Step 6: Exercise the complete flow.** Remove unsafe implicit-hour defaults and verify the same instant across editor, Timeline, Search, Review and histories.

- [ ] **Step 7: Verify the task.** Rerun the targeted commands above and require zero failures. Then run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` to catch all-call-site, resource and lint regressions. Exercise the relevant acceptance scenarios in EN/FR/FA, semantic light/dark, 100%/130%/200% font and narrow/landscape where layout or semantics changed. Record app/build revision, device/API, locale, font/window inputs and results; label missing checks pending. Expected outcome: AC-1 through AC-5 hold and affected compilation/lint checks pass.

- [ ] **Step 8: Review and integrate this task alone.** Inspect the diff against the spec and confirm every AC/RF has evidence. Stage only this task's hunks in existing modified files and its new files; do not stage the pre-existing dirty tree wholesale. If the execution workflow uses commits, commit with `fix: ux-019 shared display time`. Update the index status only from actual evidence.

## Handoff

Deliver this task's code changes and outstanding verification together. No Gradle or emulator checks were run, and no new tests were added, at the user's request. Do not mark runtime acceptance complete solely from source changes.
