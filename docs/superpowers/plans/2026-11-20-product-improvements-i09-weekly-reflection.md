# IP-I09 — Weekly Review reflection loop Implementation Plan

> **For agentic workers** Use the applicable Superpowers implementation skill when this plan is authorized for execution. Steps use checkbox syntax for tracking.

**Goal** Help users interpret a review period and take one optional follow-up action.

**Architecture** Reuse existing review data and route callbacks. A new reflection entity adds storage, privacy and export obligations while Memory already supports the intended action. Use explicit today capture to distinguish occurrence date from the historically reviewed range.

**Tech stack** Kotlin, Jetpack Compose, Luma components and existing route/state/repository contracts.

**Spec** [IP-I09 specification](../specs/2026-11-20-product-improvements-i09-weekly-reflection-design.md) · [Task index](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md) · [IP-Q01 scenario](./2026-11-20-product-improvements-q01-emulator-qa.md#qa-09)

## Files and interfaces

Paths are from the project root. Unqualified sibling filenames share the preceding directory.

- `app/src/main/java/com/luma/lifeos/ui/review/WeeklyReviewRoute.kt + ReviewHeader.kt + ReviewOverview.kt` — Period, coverage and reflection prompts.
- `app/src/main/java/com/luma/lifeos/ui/review/ReviewOverviewSignals.kt + ReviewSourcesRoute.kt` — Existing samples, availability and evidence navigation; reuse established states.
- `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt` — Route dates through existing guarded Memory, Tasks and Habits destinations.
- Update string resources and plurals in `app/src/main/res/values/`, `values-fr/` and `values-fa/` for affected copy.

Use WeeklyReviewRepository’s existing read projection and the existing capture, Tasks and Habits callbacks. Pass the explicitly shown date to the guarded Memory capture path.

## Global constraints

IP-I03 durable editor behavior, IP-I05 capture terminology and IP-I06 existing retrieval experience. Apply the [shared implementation contract](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md#shared-implementation-contract). Record source handoff before IP-Q01.

## Review focus

Historical display ranges and the new Memory occurrence date are distinct values. Pass and show the intended capture date.

## Implementation checklist

- [x] **1.** Map date endpoints and coverage facts to the active and comparison periods. Keep data computation as it is and reuse the existing display-time policy and authorized result.
- [x] **2.** Render localized coverage and optional prompts using a shared ReviewSection. Keep sources/error/retry reachable and omit unavailable sample values using existing feedback patterns.
- [x] **3.** Route “What helped?” to Memory capture with the displayed current civil date. Route “What would you change?” to the normal Tasks list with the current date visible on its action and Habits at the displayed reviewed through-date. Keep the fallback Memory Review route. Keep saved date and Back behavior, with no hidden writes or schedule changes.
- [x] **4.** Review source and localization against IP-I09-R1, IP-I09-R2, IP-I09-R3. Record the changed files, interface decisions, risks and source evidence for IP-Q01.

## QA handoff

- [x] Map `historicalReflectionMemoryUsesShownCurrentDate`, `sparseAndZeroCoverageRemainDistinct`, `containsOnlyObservationalComparisonCopy` to [QA-09](./2026-11-20-product-improvements-q01-emulator-qa.md#qa-09) for the final verification task. Test authoring and execution remain with IP-Q01 under the shared delivery workflow; existing Weekly Review route/source tests, HabitReviewFlowTest and calendar/navigation cases remain QA inputs.
- [x] Record source status as **SOURCE COMPLETE — QA PENDING** after handoff and leave the QA result **NOT RUN** until IP-Q01 executes.

**Acceptance evidence** Use no data, sparse observed data, genuine zero and a dense partial week; switch week start and Gregorian/Persian. From a historical Review open source and Back, then save a Memory for the displayed date and open Tasks/Habits without date mutation. Recheck protected sources and observational copy.

## Source handoff — 2026-11-21

**Source status:** SOURCE COMPLETE — QA PENDING

**Pre-change revision:** `7645dccc7da8d2de5eb4e84e60c8da1100883a78`

**Pre-change tracked working-tree patch SHA-256:** `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` (empty patch)

**Implementation source patch SHA-256:** `1a381ef8fd1cf7dcb7d456bc4fbe3c69cb4e4dd5bee4ff748f092a20e2eb17c8`, over the binary source and localization diff, including the new `ReviewReflection.kt` and the post-review fixes.

### Changed paths

- `app/src/main/java/com/luma/lifeos/ui/review/ReviewReflection.kt`
- `app/src/main/java/com/luma/lifeos/ui/review/ReviewOverview.kt`
- `app/src/main/java/com/luma/lifeos/ui/review/WeeklyReviewRoute.kt`
- `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt`
- `app/src/main/java/com/luma/lifeos/ui/privacy/RestrictedCaptureRoute.kt + app/src/main/java/com/luma/lifeos/lock/RestrictedCaptureService.kt`
- English, French and Persian `app/src/main/res/values*/strings.xml`
- `docs/PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md`
- `docs/superpowers/specs/2026-11-20-product-improvements-i09-weekly-reflection-design.md`
- `docs/superpowers/plans/2026-11-20-product-improvements-i09-weekly-reflection.md`

### Interface decisions and evidence

- Current and comparison dates come from the authorized `WeeklyReview.period`; `compactReviewRange` applies the active calendar system and week-start preference. Partial reviews show the actual through-date and the localized in-progress state.
- Entry counts are displayed for both periods, including genuine zero. Mood sample counts use the existing summary; absent samples display localized “No saved mood samples,” with no synthetic values.
- The Memory action shows and passes the displayed current civil date through the existing `openCapture` route. When App Lock redirects to restricted capture, the initial Memory kind and date are carried in the route state and applied to the authorized new draft in its first persisted write; the manual Memory retry retains that date.
- Coverage facts are hidden while protected summary access is denied; the period labels and optional actions remain available. The initial restricted-capture request is consumed once in saved navigation state so process recreation returns to draft recovery instead of creating a second draft.
- Tasks opens its normal list and shows the current date on the Review action. Habits opens through the reviewed period’s displayed through-date via `openHabitsDate`; neither action changes a task due date or habit schedule.
- The existing source navigation/retry flow and Memory Review fallback remain in place. New copy is localized in English, French and Persian and uses the shared `ReviewSection`, Luma text actions, theme roles and spacing tokens.

### Static checks and limits

- `git diff --check`: clean.
- Android Studio analysis of changed Kotlin found no errors. Weak-warning collection timed out for some files; warnings reported in `LifeOsApp.kt` and unrelated existing `RestrictedCaptureRoute.kt` code remain.
- `./gradlew check`: FAILED (exit 1) at `:core:data:compileDebugUnitTestKotlin` and `:core:data:compileReleaseUnitTestKotlin`; `FocusMetadataArchiveAdapterTest.kt` lines 120, 123 and 125 omit required `taskId` and `taskOrigin` arguments. The failing test file is outside the IP-I09 diff.
- No tests were authored and no emulator/device QA was performed in this source phase. Runtime acceptance remains with IP-Q01.
- Final read-only review found no Critical or Important issues; three Important findings from the initial review were fixed in the final source patch. One Minor wording issue in the mood sample count label is deferred: it repeats the localized “Samples” prefix in populated summaries.

### QA mapping

- `historicalReflectionMemoryUsesShownCurrentDate` → QA-09 current civil Memory date, including App Lock redirection and retry.
- `sparseAndZeroCoverageRemainDistinct` → QA-09 no data, sparse samples and genuine zero; protected coverage labels remain hidden without aggregate access.
- `containsOnlyObservationalComparisonCopy` → QA-09 neutral copy and labeled comparison ranges.
- `restoredRestrictedCaptureConsumesLaunchOnce` → QA-09 recreation returns to existing draft recovery without creating a duplicate draft.

**QA result:** NOT RUN; IP-Q01 owns runtime acceptance.
