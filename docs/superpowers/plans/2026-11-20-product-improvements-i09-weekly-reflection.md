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

- [ ] **1.** Map date endpoints and coverage facts to the active and comparison periods. Keep data computation as it is and reuse the existing display-time policy and authorized result.
- [ ] **2.** Render localized coverage and optional prompts using a shared ReviewSection. Keep sources/error/retry reachable and omit unavailable sample values using existing feedback patterns.
- [ ] **3.** Route “What helped?” to Memory capture with the displayed current civil date. Route “What would you change?” to current Tasks and Habits at the displayed reviewed through-date. Keep the fallback Memory Review route. Keep saved date and Back behavior, with no hidden writes or schedule changes.
- [ ] **4.** Review source and localization against IP-I09-R1, IP-I09-R2, IP-I09-R3. Record the changed files, interface decisions, risks and source evidence for IP-Q01.

## QA handoff

- [ ] Map `historicalReflectionMemoryUsesShownCurrentDate`, `sparseAndZeroCoverageRemainDistinct`, `containsOnlyObservationalComparisonCopy` to [QA-09](./2026-11-20-product-improvements-q01-emulator-qa.md#qa-09) for the final verification task. Extend the existing test surfaces where appropriate. Existing Weekly Review route/source tests; HabitReviewFlowTest; current calendar/navigation cases.
- [ ] Record source status as **SOURCE COMPLETE — QA PENDING** after handoff and leave the QA result **NOT RUN** until IP-Q01 executes.

**Acceptance evidence** Use no data, sparse observed data, genuine zero and a dense partial week; switch week start and Gregorian/Persian. From a historical Review open source and Back, then save a Memory for the displayed date and open Tasks/Habits without date mutation. Recheck protected sources and observational copy.
