# UI/UX implementation preparation — 18 October 2026

All review tasks except **UX-01, UX-02 and UX-12** are covered by this handoff. It records reviewed design decisions, eight scoped specs, eight executable plans, a shared acceptance checklist and the current execution status.

**Current status (2026-10-18):** UX-03/04 were implemented and committed as `58d4903`; focused JVM/build checks and 19 isolated API 36 Android tests pass. UX-09/10 were implemented and committed as `d43d731`; the debug app assembly passed, while their focused behavior tests and device acceptance have not been run. UX-11 is implemented in `910a596`; its targeted Search UI tests pass. UX-06/15 are implemented in the current working tree; Gradle compilation was blocked before project configuration by the sandbox's restricted socket access, and test tasks and manual acceptance were not run. No commit was created for UX-06/15. UX-05/17 UI changes and focused regression test additions are in the working tree; compilation and targeted test execution were blocked before project configuration by restricted sandbox access, with broader automated and manual acceptance still open. UX-07/08/19 are now implemented in the working tree; focused JVM/build and isolated API 36.1 device checks passed, with manual keyboard, 2.0-scale and inset acceptance still open. No commit was created for UX-07/08/19; review-only commit messages are recorded in the capture plan. Manual acceptance remains open for all listed findings. Other units remain pending. See [validation](VALIDATION.md#ux-03-and-ux-04-habit-correction-and-detail-hierarchy--2026-10-18), [UX-09/10 status](VALIDATION.md#ux-09-and-ux-10--habit-progress-and-other-habits--2026-10-18), [UX-06/15 status](VALIDATION.md#ux-06-and-ux-15--integration-and-metric-states--2026-10-18), [UX-07/08/19 validation](VALIDATION.md#ux-07-ux-08-and-ux-19-capture-layout-and-navigation--2026-10-18) and the [acceptance checklist](UI_UX_ACCEPTANCE_2026-10-18.md).

**Baseline:** 485125654e797e955a3fee02c98d86abdd915e1f, inspected on 18 October 2026.
**Source of scope:** [UI/UX review](UI_UX_REVIEW_2026-10-18.md). The numbered findings in the 13/14 October reviews belong to different reviews; do not identify them by number alone.
**Validation:** [acceptance checklist](UI_UX_ACCEPTANCE_2026-10-18.md).

## Understanding and scope

Prepare improvements for people recording daily life, logging habits and reflecting on saved data in the existing offline Android app. Success means an implementer can select a unit and know the behavior, exact state boundaries, files/interfaces, tests and completion criteria without inventing core UX decisions.

The user explicitly excluded UX-01/02/12. UX-01/02 are already in this baseline; preserve their ordering, shortcuts, count scope and existing tests where shared components change. Search scope expansion or scope-copy work from UX-12 is excluded. UX-11 is already implemented in commit 910a596; it needs regression/manual acceptance, not another implementation.

The original preparation task authorized producing the complete spec/plan package. UX-03/04 were later implemented and committed as `58d4903`; UX-09/10 were subsequently implemented and committed as `d43d731`. No release or deployment was requested. Existing untracked release output, SQL validation scripts and screenshot evidence remain untouched.

## Brainstormed approaches

| Approach | Trade-off | Decision |
| --- | --- | --- |
| Targeted changes within existing routes/domain rules, with a few shared presentation helpers | Fits current architecture; isolates correction, integration and layout risk; allows incremental review | Selected |
| Rewrite navigation/editors/review using a new generic screen framework | Uniform infrastructure but broad regression surface and unnecessary scope | Rejected |
| Copy/spacing changes only | Smaller changes, but cannot fix precision/dirty state, access-vs-import modeling or persistent export status | Insufficient |

These choices are proposed defaults and can be revised in the linked specs before execution.

## Coverage and implementation units

| Finding | Unit | Spec | Plan | Implementation status |
| --- | --- | --- | --- | --- |
| UX-03 correction | B | [Habits](superpowers/specs/2026-10-18-ux-03-04-09-10-habits-design.md) | [Habits plan](superpowers/plans/2026-10-18-ux-03-04-09-10-habits.md) | Implemented in `58d4903`; focused automated checks pass; manual acceptance open |
| UX-04 detail hierarchy | B | Same habit spec | Same habit plan, Task 2 | Implemented in `58d4903`; focused automated checks pass; manual acceptance open |
| UX-05 weekly habits | F | [Review](superpowers/specs/2026-10-18-ux-05-17-review-design.md) | [Review plan](superpowers/plans/2026-10-18-ux-05-17-review.md) | UI and focused regression tests added in working tree; compilation/test execution blocked by read-only wrapper lock and sandbox wildcard-IP restriction; broader automated and manual acceptance open |
| UX-06 missing metric states | D | [Integration states](superpowers/specs/2026-10-18-ux-06-15-integration-states-design.md) | [Integration plan](superpowers/plans/2026-10-18-ux-06-15-integration-states.md) | Implemented in working tree; compile blocked; tests and manual acceptance open |
| UX-07 editor content priority | C | [Capture](superpowers/specs/2026-10-18-ux-07-08-19-capture-design.md) | [Capture plan](superpowers/plans/2026-10-18-ux-07-08-19-capture.md) | Implemented in working tree; focused JVM/build/device checks pass; keyboard/rotation/TalkBack acceptance open |
| UX-08 capture footer | C | Same capture spec | Same capture plan, Task 3 | Implemented in working tree; route matrix and final Timeline/Review clearance tests pass; manual large-text/inset checks open |
| UX-09 progress/setup | B | Habit spec | Habit plan, Task 3 | Implemented in `d43d731`; debug assembly passes; focused behavior checks and manual acceptance open |
| UX-10 other habits | B | Habit spec | Habit plan, Task 3 | Implemented in `d43d731`; debug assembly passes; focused behavior checks and manual acceptance open |
| UX-11 Search states | V | [Current implementation evidence](VALIDATION.md#ux-11--search-initial-and-empty-states--2026-10-18) | [Regression/acceptance](UI_UX_ACCEPTANCE_2026-10-18.md#ux-11--existing-search-implementation) | Implemented in `910a596`; manual acceptance open |
| UX-13 fixed bars | E | [Navigation](superpowers/specs/2026-10-18-ux-13-navigation-design.md) | [Navigation plan](superpowers/plans/2026-10-18-ux-13-navigation.md) | Pending |
| UX-14 layered privacy | G | [Privacy](superpowers/specs/2026-10-18-ux-14-privacy-design.md) | [Privacy plan](superpowers/plans/2026-10-18-ux-14-privacy.md) | Pending |
| UX-15 integration setup | D | Integration spec | Integration plan, Tasks 1–2 | Implemented in working tree; compile blocked; tests and manual acceptance open |
| UX-16 formatting | A | [Formatting](superpowers/specs/2026-10-18-ux-16-formatting-design.md) | [Formatting plan](superpowers/plans/2026-10-18-ux-16-formatting.md) | Pending; two known plural lint errors |
| UX-17 section discovery | F | Review spec | Review plan, Task 2 | UI and focused regression tests added in working tree; compilation/test execution blocked by read-only wrapper lock and sandbox wildcard-IP restriction; broader automated and manual acceptance open |
| UX-18 export status | H | [Backup](superpowers/specs/2026-10-18-ux-18-backup-status-design.md) | [Backup plan](superpowers/plans/2026-10-18-ux-18-backup-status.md) | Pending |
| UX-19 expanded chooser | C | Capture spec | Capture plan, Task 2 | Implemented in working tree; chooser checks pass at 360/411 dp and 1.0/1.3 font scales; 2.0/manual dismissal checks open |

## Sequence and dependencies

Recommended serial order: **A → B → C → D → E → F → G → H → V**.

| Unit | Required before execution | Shared-file coordination |
| --- | --- | --- |
| A formatting | Current baseline | Publish time/numeral/plural policy before consuming UI |
| B habits | A | Preserve Today UX-01/02; E later consumes correction close handler |
| C capture | A | LifeOsApp and editor controls; coordinate FAB padding with Review |
| D integration states | A | Publishes availability model consumed by F; touches LifeOsApp/Today/Review |
| E fixed navigation | B for correction Back integration | Touches Privacy, Search, settings and Review secondary routes |
| F Review | A, C for content clearance, D for metric state wiring | Preserve single habit observation and stable list keys |
| G privacy | E; use D's actual import wording | Shared native rationale must remain independent of services |
| H backup status | A, E, G | Add privacy shortcut after layered reset layout; local preference only |
| V acceptance | Relevant implemented units | Includes existing UX-11; no duplicate Search feature work |

Do not run simultaneous edits to LifeOsApp.kt, strings.xml catalogs, HabitDetailRoute.kt or ReviewOverview.kt. Each unit is a coherent review/commit boundary, with smaller testable tasks in its plan. Native in-session execution is a reasonable default because these units share routes/resources; no subagents were used for this preparation.

## Locked design defaults

- Keep palette, five tabs, existing Material controls, explicit past-date choice and unsaved-work protection.
- Today keeps its current inline capture shortcuts. Timeline/Review get a labeled FAB; Habits/More/secondary screens have no global life-entry FAB. Remove the extra bottomBar capture row.
- Correction preserves original seconds/offset unless deliberately changing time; clean Save does not write; clean dismissal asks no discard question.
- Weekdays preset explicitly means Mon–Fri in every locale; no silent local-workweek assumption.
- Other habits remain browsable; pending failures/retry cannot disappear behind collapsed content.
- Review's seven cells describe real state or Upcoming; no unscheduled day is treated as missed. Keep existing comparison math.
- Probe Android access independently of import preference, with no native record read. Saved values, including zero, remain visible when access/imports are off.
- Health wording describes current manual, foreground imports; do not add background Health work to make a label true.
- Use Gregorian dates, localized digits, Android's effective 12/24-hour convention and unchanged authored names.
- Last successful export describes a completed local write; no file-existence/cloud-protection claim.

## Global constraints

These exact constraints appear in every implementation plan:

- Keep minSdk 28, compileSdk 35, targetSdk 35, JDK 17, AGP 8.7.3, Kotlin 2.2.10 and Compose BOM 2025.08.01.
- Add no product dependency, database migration, permission, network access, telemetry or AI service.
- English, Persian and French remain available offline; preserve user-authored text, stored timestamps and timezone rules.
- UX-01, UX-02 and UX-12 are excluded; preserve their existing behavior.
- Minimum interactive touch target: 48 × 48 dp; do not reduce configured text scale.

Additional invariants: preserve stable IDs, recordedAt, retry idempotency, log undo, pending-schedule cancellation, history, photo retention, import opt-out guards, existing duration precision, restore preview/confirmation and reset confirmation. No automatic format truncation writes.

Use approximately 20 dp screen padding, 12–16 dp card padding and 8–12 dp related gaps as starting tokens. Prefer wrapping/adaptive layout over smaller type. Noninteractive Review state cells need readable semantics; interactive controls keep minimum touch targets. Automated semantics do not certify TalkBack speech.

## Development preflight and verification commands

1. Read this handoff, selected spec/plan and acceptance checklist. Recheck git HEAD/status: adapt anchors if source changed; do not repeat completed work.
2. Preserve unrelated untracked paths: app/release/, existing core/data/src/test/resources/*_check.py scripts and docs/ux-review-2026-10-18/ evidence. Create branch/worktree at execution time only if needed; no branch or commit was created by this planning task.
3. Use existing wrapper/JDK/SDK and cached dependencies. No toolchain upgrade is part of this work. Offline cache failures are infrastructure failures, not passing tests.
4. Run each new behavior test before changing behavior, observe relevant failure, implement and rerun targeted suites. Pure copy/layout cases may use focused rendering/manual checks instead of redundant unit tests.
5. Run JVM/build checks and affected device classes; broaden only for shared changes. Record actual command/exit code, counts and logs in docs/VALIDATION.md. Old review results are historical evidence, not a new pass.

Standard JVM/build command (run during implementation, not claimed executed here):

```bash
bash gradlew :core:domain:test :app:testDebugUnitTest :core:data:testDebugUnitTest :integration:usage:testDebugUnitTest :integration:health:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline
```

Known baseline: UX-11 validation recorded 605 JVM tests and 11 targeted Search UI tests; those were prior runs. Two habit_unit_default ImpliedQuantity errors in Persian/French are documented; A must fix them. Existing warnings/other failures require honest results, not blanket “lint passed”.

## Disposable device-test install

Never install a mutating fixture runner against the personal com.luma.lifeos package. Prepare an isolated application ID and build output. Create /tmp/luma-ux18-validation.init.gradle with:

```groovy
gradle.beforeProject { project ->
    project.plugins.withId('com.android.application') {
        project.layout.buildDirectory.set(project.layout.projectDirectory.dir('build/ux18validation'))
        project.androidComponents.finalizeDsl { android ->
            android.defaultConfig.applicationId = 'com.luma.lifeos.ux18validation'
        }
    }
}
```

Build:

```bash
bash gradlew -I /tmp/luma-ux18-validation.init.gradle :app:assembleDebug :app:assembleDebugAndroidTest --offline
```

Before installing, inspect APK application IDs with the installed SDK's aapt or apkanalyzer: app APK must be com.luma.lifeos.ux18validation and test target must be that package. Stop if it targets com.luma.lifeos. Then use the selected disposable device serial (replace emulator-5554 if different):

```bash
adb -s emulator-5554 install -r app/build/ux18validation/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/ux18validation/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r -e class com.luma.lifeos.SearchStatesTest,com.luma.lifeos.SearchFlowTest,com.luma.lifeos.ActiveSearchFiltersTest com.luma.lifeos.ux18validation.test/androidx.test.runner.AndroidJUnitRunner
```

Substitute the affected classes from the checklist, retaining the isolated runner/target. Class packages remain com.luma.lifeos even when applicationId changes. Run serially where fixtures mutate the same database. Restore device locale/time/keyboard/font settings in finally. If Gradle connected tests fail on uncached UTP, direct adb instrumentation is an available fallback after successful APK builds; capture runner output and its failure summary.

## Completion and review

An implemented unit has its code, focused passing checks and recorded limitations; accepted means its relevant automated/manual criteria actually passed. Do not mark manual checks complete from screenshots or semantics alone.

Before implementation, review the linked proposals as one package; decisions can be adjusted in the owning spec and plan together. The initial sequence recommended Unit A before Unit B; the user later selected UX-03/04, now implemented. Any change to the excluded findings remains outside this scope.
