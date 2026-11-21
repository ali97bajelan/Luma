# IP-I07 — Habit dates, counts and schedule changes Implementation Plan

> **For agentic workers** Use the applicable Superpowers implementation skill when this plan is authorized for execution. Steps use checkbox syntax for tracking.

**Goal** Explain the civil date of a habit log and when a schedule edit changes future targets.

**Architecture** Expose existing resolved date and schedule state through shared habit formatting. Recomputing schedule dates separately in composables risks a different day at midnight or after travel.

**Tech stack** Kotlin, Jetpack Compose, Luma components and existing route/state/repository contracts.

**Spec** [IP-I07 specification](../specs/2026-11-20-product-improvements-i07-habit-date-explanations-design.md) · [Task index](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md) · [IP-Q01 scenario](./2026-11-20-product-improvements-q01-emulator-qa.md#qa-07)

## Files and interfaces

Paths are from the project root. Unqualified sibling filenames share the preceding directory.

- `app/src/main/java/com/luma/lifeos/ui/habits/HabitProgress.kt + TodayHabitsCard.kt + HabitsScreen.kt + HabitDetailRoute.kt` — Dated progress and state labels.
- `app/src/main/java/com/luma/lifeos/ui/habits/HabitHistory.kt + HabitLogCorrectionDialog.kt` — Original/target dates, tracking zone and correction feedback.
- `app/src/main/java/com/luma/lifeos/ui/habits/HabitEditorRoute.kt + HabitEditorViewModel.kt` — Effective-date summary and current schedule fields.
- `app/src/main/java/com/luma/lifeos/ui/QuickCaptureSheet.kt` — Check existing habit capture affects the date shown; preserve capture callbacks.
- `core/domain/src/main/kotlin/com/luma/lifeos/domain/habit/HabitRepository.kt`, `core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomHabitRepository.kt`, and `app/src/main/java/com/luma/lifeos/lock/ProtectedCatalogRepositories.kt` — Bind schedule-changing editor saves to the displayed effective date through the protected repository boundary.
- Update string resources and plurals in `app/src/main/res/values/`, `values-fr/` and `values-fa/` for affected copy.

Use HabitEditorState.effectiveDate and existing tracking-zone/schedule models. Source the presented affected log date from the same state as the mutation target.

## Global constraints

IP-I01 Today habit preview ownership and IP-I04 habit-detail reminder presentation. Apply the [shared implementation contract](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md#shared-implementation-contract). Record source handoff before IP-Q01.

## Review focus

Device and tracking zones may disagree on today during an open editor or correction dialog. Use the same resolved date for copy and mutation.

## Implementation checklist

- [x] **1.** Trace UI log targets and repository effective date logic, including today in the habit’s zone. Reuse existing formatting and share a date/target label only where multiple surfaces need it.
- [x] **2.** Render date, count, zone and lifecycle presentation for list, detail, history, Today and quick capture where applicable. Keep actual log target IDs, corrections, retry and Undo.
- [x] **3.** Drive the editor summary from the schedule change state and authoritative effective-date policy. Validate the displayed date at commit in the same transaction as schedule-changing edits. If it changed, keep draft and stored data intact, refresh the effective date and require Save again. Name/unit-only changes retain their existing behavior.
- [x] **4.** Review source and localization against IP-I07-R1, IP-I07-R2, IP-I07-R3. Record the changed files, interface decisions, risks and source evidence for IP-Q01.

## QA handoff

- [x] Map `scheduleSummaryMatchesCommittedEffectiveDate`, `correctionShowsOriginalAndTargetDate`, `pausedDayDoesNotShowMissedProgress` to [QA-07](./2026-11-20-product-improvements-q01-emulator-qa.md#qa-07) for the final verification task. Extend the existing test surfaces where appropriate. HabitDateSelectionFlowTest; HabitCaptureFlowTest; existing habit editor and history tests.
- [x] Record source status as **SOURCE COMPLETE — QA PENDING** after handoff and leave the QA result **NOT RUN** until IP-Q01 executes.

**Acceptance evidence** With 2 of 3 logged, edit the target to 4 from a device zone on a different day, including a tracking-zone midnight and a DST edge. Verify the existing effective-date rule, unchanged earlier progress, dated correction and Undo. Check paused, unscheduled, archived and future-date controls.

## Source handoff

**Status:** SOURCE COMPLETE — QA PENDING. **Baseline:** `7edd333e7a71453c5e930e236cea49b621848927`. Source changes are the current uncommitted IP-I07 working-tree patch.

**Changed paths:**

- `app/src/main/java/com/luma/lifeos/lock/ProtectedCatalogRepositories.kt`
- `app/src/main/java/com/luma/lifeos/ui/habits/HabitDatePresentation.kt`, `HabitDetailRoute.kt`, `HabitEditorRoute.kt`, `HabitEditorViewModel.kt`, `HabitHistory.kt`, `HabitLogCorrectionDialog.kt`, `HabitProgress.kt`, `HabitUndoFeedback.kt`, `HabitsScreen.kt`, `PendingHabitFeedback.kt`, `TodayHabitsCard.kt`
- `app/src/main/res/values/strings.xml`, `values-fr/strings.xml`, `values-fa/strings.xml`
- `app/src/test/java/com/luma/lifeos/ui/habits/HabitRepositoryFixture.kt`
- `core/domain/src/main/kotlin/com/luma/lifeos/domain/habit/HabitRepository.kt`
- `core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomHabitRepository.kt`

**Decisions:** Date labels reuse the existing display-calendar policy; tracking-zone identifiers appear only when device and tracking zones differ. Target counts retain the existing schedule/lifecycle projection. Schedule changes use `renameAndSchedule` through the protected repository and compare the editor’s displayed effective date inside the write transaction. A mismatch aborts the write, preserves the draft, refreshes the summary and requires another Save. Name/unit-only edits continue through rename alone; earlier schedule versions and log identities remain intact. Existing capture date callbacks, correction, retry and Undo paths are retained. No launcher-widget behavior changed.

**Source evidence:** Studio inspections of changed Kotlin and resource files reported no Kotlin errors; `git diff --check` passed. The requested `./gradlew check` ran after implementation: app production Kotlin compiled for debug and release, but the overall check failed while compiling `:core:data` unit tests. Existing `FocusMetadataArchiveAdapterTest` calls at lines 120, 123 and 125 omit required `taskId` and `taskOrigin` arguments. No tests were authored. Other module checks ran as part of Gradle; IP-Q01 runtime QA remains NOT RUN.

**Known risks / QA:** Runtime date-boundary behavior across tracking-zone midnight, device/tracking-zone disagreement and DST has not been exercised. Visual verification of RTL, large text, and the corrected-date/Undo flows remains for IP-Q01. QA-07 must verify `scheduleSummaryMatchesCommittedEffectiveDate`, `correctionShowsOriginalAndTargetDate`, and `pausedDayDoesNotShowMissedProgress`, plus `HabitDateSelectionFlowTest`, `HabitCaptureFlowTest`, and existing editor/history coverage. QA result remains NOT RUN until IP-Q01.
