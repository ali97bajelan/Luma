# F07 — Accessibility and adaptive layout gaps Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use delegation only if separately authorized. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every existing core action operable with large text, RTL, assistive technology and supported window sizes.

**Architecture:** Improve shared Compose components and semantics first, then feature-specific layouts. Preserve established typography/theme/navigation and use window space to improve reachability rather than introduce a new visual design system.

**Tech Stack:** Existing Kotlin, pure domain contracts, Room, Compose, Java 17 and native Android adapters as appropriate; candidate dependency changes must be documented before integration.

**Spec:** [Accessibility and adaptive layout gaps specification](../specs/2026-10-26-roadmap-07-accessibility.md)

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../specs/2026-10-26-roadmap-07-accessibility.md) · [Plan](2026-10-26-roadmap-07-accessibility.md) · [QA scenarios](../../feature-roadmap/07-accessibility-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Global constraints

- Exactly two phases per task: implementation, then QA; do not interleave a test cycle into implementation.
- During implementation: no Gradle, sync, resolution, build, lint, schema generation, test execution, emulator or device. Test authoring belongs to QA.
- Preserve existing personal data/user edits, module boundaries, minimum API28 and local-first/offline defaults; follow the spec for any explicit compatibility exception.
- Calendar stays tasks/memories only; preserve navigation, drafts, revision checks, photo ownership and EN/FR/FA/RTL/light/dark support.
- Read [WORKFLOW.md](../../feature-roadmap/WORKFLOW.md) for exact isolation, source handoff and QA result/evidence obligations.

Dependencies: Coordinate task 02 acceptance; task 07 fixes found defects and supplies focused regression evidence.

## Review focus

- F07-R01 (Reachability): execute F07-QA01, F07-QA06, F07-QA07; author assertion-level regressions in Q01 for the specified conditions.
- F07-R02 (Semantics and focus): execute F07-QA02, F07-QA03; author assertion-level regressions in Q01 for the specified conditions.
- F07-R03 (Input and contrast): execute F07-QA03, F07-QA05; author assertion-level regressions in Q01 for the specified conditions.
- F07-R04 (Locale and adaptation): execute F07-QA04, F07-QA06, F07-QA07; author assertion-level regressions in Q01 for the specified conditions.
- F07-R05 (Coverage and evidence): execute F07-QA02, F07-QA07, F07-QA08; author assertion-level regressions in Q01 for the specified conditions.

## File map and interfaces

Existing paths are source anchors, not a request to edit every file regardless of need. New paths are proposed ownership boundaries; reconcile with existing equivalents before creating duplicates.

- Modify/review: [app/src/main/java/com/luma/lifeos/ui/common/AppPageScaffold.kt](../../../app/src/main/java/com/luma/lifeos/ui/common/AppPageScaffold.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/common/EditorTopBar.kt](../../../app/src/main/java/com/luma/lifeos/ui/common/EditorTopBar.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/common/LumaUiComponents.kt](../../../app/src/main/java/com/luma/lifeos/ui/common/LumaUiComponents.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/common/AccessibleFeedback.kt](../../../app/src/main/java/com/luma/lifeos/ui/common/AccessibleFeedback.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/calendar/CalendarScreen.kt](../../../app/src/main/java/com/luma/lifeos/ui/calendar/CalendarScreen.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/todo/TodoRow.kt](../../../app/src/main/java/com/luma/lifeos/ui/todo/TodoRow.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/theme/LumaTypography.kt](../../../app/src/main/java/com/luma/lifeos/ui/theme/LumaTypography.kt)
- Modify/review: [docs/UI_REDESIGN_TASKS.md](../../UI_REDESIGN_TASKS.md)
- Modify/review: [docs/CALENDAR_QA.md](../../CALENDAR_QA.md)
- Create if required by selected implementation: `docs/validation/roadmap-07-accessibility-findings.md`

Phase 1 source findings and QA handoff: [Accessibility findings ledger](../../validation/roadmap-07-accessibility-findings.md).

Interfaces consumed/produced:

- Keep existing editor callbacks and navigation contracts; improvements must not change saved record values.
- Each finding has ID A11Y-nn, affected surface, triggering matrix cell, expected interaction, source fix, and linked QA scenario. Unobserved risk remains a candidate finding, not an asserted runtime defect.

QA-owned tests (create only in phase 2; extend existing equivalent suites instead of duplicating them where practical):

- QA create/extend: `core/domain/src/test/kotlin/com/luma/lifeos/domain/roadmap/F07ContractTest.kt`
- QA create/extend: `core/data/src/androidTest/kotlin/com/luma/lifeos/data/roadmap/F07PersistenceTest.kt`
- QA create/extend: `app/src/test/java/com/luma/lifeos/roadmap/F07StateTest.kt`
- QA create/extend: `app/src/androidTest/java/com/luma/lifeos/roadmap/F07FlowTest.kt`

## Phase 1 — Implementation

Status: **SOURCE COMPLETE — QA PENDING**.

### F07-I01 — Map findings from source

- [x] Create A11Y finding ledger using current acceptance gaps and shared component inspection; mark suspected runtime issues unverified and map each to a QA case/matrix cell.
- [x] Source-review the changed declarations/callers against F07-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F07-I02 — Fix shared layout primitives

- [x] Adjust scaffold/reading pane, editor header/footer/IME padding, wrapping action rows and error feedback. Preserve callbacks and existing draft/validation state.
- [x] Source-review the changed declarations/callers against F07-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F07-I03 — Refine feature-specific semantics

- [x] Update Calendar date descriptions, task/habit actions, result rows and modal focus without duplicating spoken labels. Replace color-only state with visible text/shape cues.
- [x] Source-review the changed declarations/callers against F07-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F07-I04 — Document matrix and regression scope

- [x] List shared-component consumers and required screenshot/TalkBack/keyboard checks; update finding source references and initialize results NOT RUN.
- [x] Source-review the changed declarations/callers against F07-R01–R05 and record paths/decisions. Do not run compilation or tests.

### F07-I05 — Source handoff

- [x] Review all changed files, call sites, permission/manifest declarations, EN/FR/FA resources and persistence/archive contracts for the actual selected scope.
- [x] Run source-only checks such as `git diff --check`, file/link checks and XML parsing where relevant. A successful static check is not a test/build pass.
- [x] Record Git/patch identity, implementation decisions, source-to-requirement mapping and known uncertainty. Generated schemas and all runtime verification remain pending QA.
- [x] Mark phase 1 **SOURCE COMPLETE — QA PENDING** only after I01–I04 and this handoff are complete. Do not mark the task complete or run a Gradle/emulator command here.

## Phase 2 — QA

Initial status: **NOT RUN**. Entry requires I01–I05 handoff and isolated target verification. This phase owns test authoring, Gradle, schemas, emulator/device use and defect retesting.

### F07-Q01 — Fixtures and automated coverage

- [ ] Read the full [QA scenarios](../../feature-roadmap/07-accessibility-qa.md), prepare its synthetic fixture, and create the QA-owned tests above. Give tests descriptive scenario-matching names; assert expected IDs/values and failure atomicity independently of implementation helpers.
- [ ] Cover every requirement and review-focus condition. Prefer small deterministic domain/state tests, real Room migration/transaction tests for persistence and Compose/native flows for platform behavior. Existing test passes cannot replace missing native or manual coverage.
- [ ] If a QA defect needs production correction, retain a failing regression and capture failure before correction where feasible; fix and rerun here without rewriting expected behavior to match a defect.
- [ ] For an explicitly deferred investigation/no-change branch, do not author tests that pretend new behavior exists. Record the branch evidence, mark inapplicable feature assertions N/A with rationale, and run the applicable existing baseline gates below. This accepts assessment scope only; feature runtime QA stays deferred.

### F07-Q02 — Gradle, schemas and affected regressions

Run from repository root **only in QA**, after reading the shared command/isolation policy:

```sh
bash gradlew -PdomainOnly=true :core:domain:test
bash gradlew :app:testDebugUnitTest :core:data:testDebugUnitTest
bash gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :core:data:assembleDebugAndroidTest :core:data:lintDebug
```

- [ ] Record actual commands, exit codes and JUnit/lint outcomes. Expected automated gate: exit 0; required tests actually executed with no failures/errors; skipped tests explained; no lint errors/fatals and new warnings classified.
- [ ] Where schema changed, generate/export current Room schema through QA build/KSP, inspect against hand-written migration, preserve old JSON and execute populated-upgrade plus archive compatibility cases. Commit schema evidence with the validated task, not as an unverified phase-1 claim.
- [ ] Run affected instrumented suites only on a confirmed isolated package/AVD using the shared QA catalogue; do not assume current debug package is isolated. Test-class filters and variant commands must match authored test packages and target identity.

### F07-Q03 — Scenarios, native behavior and acceptance

- [ ] Execute all F07-QA01–QA08 cases and every relevant shared matrix cell; Human TalkBack, external keyboard, contrast measurement, IME and actual launcher matrix.
- [ ] Record actual result, requirement mapping and artifact links in the scenario ledger. Source/compile/host passes cannot certify native/manual cases.
- [ ] Resolve defects, rerun owning and affected regression cases, and identify invalidated evidence. Leave blocked/unrun cases visible with precise prerequisites.
- [ ] Mark complete only for the exact validated scope when required cases pass. Assessment/deferred branch closure must explicitly state that feature runtime acceptance has not occurred. Record release blockers/authorized exceptions without silently self-waiving.

Suggested future commit grouping: one source handoff followed by QA tests, generated schemas and evidence; do not commit unrelated user changes. Creating these documents does not authorize implementation, release or commit actions now.
