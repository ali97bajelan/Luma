# F11 — Reusable journal capture templates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use delegation only if separately authorized. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users create and apply local journal title/body/tag templates without overwriting drafts silently.

**Architecture:** Store templates as reusable content separate from journal records. Applying copies a snapshot into an existing journal draft; entry persistence remains in the current Journal repository.

**Tech Stack:** Existing Kotlin, pure domain contracts, Room, Compose, Java 17 and native Android adapters as appropriate; candidate dependency changes must be documented before integration.

**Spec:** [Reusable journal capture templates specification](../specs/2026-10-26-roadmap-11-capture-templates.md)

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../specs/2026-10-26-roadmap-11-capture-templates.md) · [Plan](2026-10-26-roadmap-11-capture-templates.md) · [QA scenarios](../../feature-roadmap/11-capture-templates-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Global constraints

- Exactly two phases per task: implementation, then QA; do not interleave a test cycle into implementation.
- During implementation: no Gradle, sync, resolution, build, lint, schema generation, test execution, emulator or device. Test authoring belongs to QA.
- Preserve existing personal data/user edits, module boundaries, minimum API28 and local-first/offline defaults; follow the spec for any explicit compatibility exception.
- Calendar stays tasks/memories only; preserve navigation, drafts, revision checks, photo ownership and EN/FR/FA/RTL/light/dark support.
- Read [WORKFLOW.md](../../feature-roadmap/WORKFLOW.md) for exact isolation, source handoff and QA result/evidence obligations.

Dependencies: Task 02 editor recovery; task 01 for portable template data.

## Review focus

- F11-R01 (Template content): execute F11-QA01, F11-QA02; author assertion-level regressions in Q01 for the specified conditions.
- F11-R02 (Safe application): execute F11-QA03, F11-QA04; author assertion-level regressions in Q01 for the specified conditions.
- F11-R03 (Context and validation): execute F11-QA04, F11-QA05; author assertion-level regressions in Q01 for the specified conditions.
- F11-R04 (Starter templates): execute F11-QA06; author assertion-level regressions in Q01 for the specified conditions.
- F11-R05 (Persistence and navigation): execute F11-QA01, F11-QA05, F11-QA07, F11-QA08; author assertion-level regressions in Q01 for the specified conditions.

## File map and interfaces

Existing paths are source anchors, not a request to edit every file regardless of need. New paths are proposed ownership boundaries; reconcile with existing equivalents before creating duplicates.

- Modify/review: [core/domain/src/main/kotlin/com/luma/lifeos/domain/model/Journal.kt](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/model/Journal.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/journal/JournalEditorViewModel.kt](../../../app/src/main/java/com/luma/lifeos/ui/journal/JournalEditorViewModel.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/journal/JournalEditorScreen.kt](../../../app/src/main/java/com/luma/lifeos/ui/journal/JournalEditorScreen.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/journal/JournalEditorRoute.kt](../../../app/src/main/java/com/luma/lifeos/ui/journal/JournalEditorRoute.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/QuickCaptureSheet.kt](../../../app/src/main/java/com/luma/lifeos/ui/QuickCaptureSheet.kt)
- Modify/review: [core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupRows.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupRows.kt)
- Create if required by selected implementation: `core/domain/src/main/kotlin/com/luma/lifeos/domain/journal/JournalTemplate.kt`
- Create if required by selected implementation: `core/data/src/main/kotlin/com/luma/lifeos/data/journal/RoomJournalTemplateRepository.kt`
- Create if required by selected implementation: `app/src/main/java/com/luma/lifeos/ui/journal/JournalTemplatePicker.kt`
- Create if required by selected implementation: `app/src/main/java/com/luma/lifeos/ui/journal/JournalTemplateEditor.kt`

Interfaces consumed/produced:

- Proposed `JournalTemplate(id: UUID, name: String, title: String?, body: String, tags: Set<String>, revision: UUID)`; name 1..100 code points, title/body/tag limits reuse JournalTextLimits.
- Proposed `applyTemplate(draft: JournalDraftSnapshot, template: JournalTemplate, mode: REPLACE|APPEND): JournalDraftSnapshot`; snapshot contains title, body and tag names only; preserve all draft identity/date/photos/people/place fields outside it.
- Proposed template repository list/get/create/update/delete uses revision checks; changing template never updates saved journal entries.

QA-owned tests (create only in phase 2; extend existing equivalent suites instead of duplicating them where practical):

- QA create/extend: `core/domain/src/test/kotlin/com/luma/lifeos/domain/roadmap/F11ContractTest.kt`
- QA create/extend: `core/data/src/androidTest/kotlin/com/luma/lifeos/data/roadmap/F11PersistenceTest.kt`
- QA create/extend: `app/src/test/java/com/luma/lifeos/roadmap/F11StateTest.kt`
- QA create/extend: `app/src/androidTest/java/com/luma/lifeos/roadmap/F11FlowTest.kt`

## Phase 1 — Implementation

Initial status: **PENDING**.

### F11-I01 — Define template and apply functions

- [ ] Create JournalTemplate domain validation, snapshot/mode values, pure append/replace/undo behavior and tag normalization using existing JournalTextLimits.
- [ ] Source-review the changed declarations/callers against F11-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F11-I02 — Persist templates

- [ ] Implement revision-safe repository/entity/DAO, migration and archive inventory; give starters fixed resource identities separate from user template UUIDs.
- [ ] Source-review the changed declarations/callers against F11-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F11-I03 — Integrate editor and picker

- [ ] Add template management/picker under journal capture, populated-draft mode dialog, one-step Undo apply and complete parent draft preservation.
- [ ] Source-review the changed declarations/callers against F11-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F11-I04 — Add localized starters and recovery

- [ ] Create all three resource variants, explicit starter-install action, error/retry/delete confirmation and source-independent saved-entry behavior; document legacy restore behavior.
- [ ] Source-review the changed declarations/callers against F11-R01–R05 and record paths/decisions. Do not run compilation or tests.

### F11-I05 — Source handoff

- [ ] Review all changed files, call sites, permission/manifest declarations, EN/FR/FA resources and persistence/archive contracts for the actual selected scope.
- [ ] Run source-only checks such as `git diff --check`, file/link checks and XML parsing where relevant. A successful static check is not a test/build pass.
- [ ] Record Git/patch identity, implementation decisions, source-to-requirement mapping and known uncertainty. Generated schemas and all runtime verification remain pending QA.
- [ ] Mark phase 1 **SOURCE COMPLETE — QA PENDING** only after I01–I04 and this handoff are complete. Do not mark the task complete or run a Gradle/emulator command here.

## Phase 2 — QA

Initial status: **NOT RUN**. Entry requires I01–I05 handoff and isolated target verification. This phase owns test authoring, Gradle, schemas, emulator/device use and defect retesting.

### F11-Q01 — Fixtures and automated coverage

- [ ] Read the full [QA scenarios](../../feature-roadmap/11-capture-templates-qa.md), prepare its synthetic fixture, and create the QA-owned tests above. Give tests descriptive scenario-matching names; assert expected IDs/values and failure atomicity independently of implementation helpers.
- [ ] Cover every requirement and review-focus condition. Prefer small deterministic domain/state tests, real Room migration/transaction tests for persistence and Compose/native flows for platform behavior. Existing test passes cannot replace missing native or manual coverage.
- [ ] If a QA defect needs production correction, retain a failing regression and capture failure before correction where feasible; fix and rerun here without rewriting expected behavior to match a defect.
- [ ] For an explicitly deferred investigation/no-change branch, do not author tests that pretend new behavior exists. Record the branch evidence, mark inapplicable feature assertions N/A with rationale, and run the applicable existing baseline gates below. This accepts assessment scope only; feature runtime QA stays deferred.

### F11-Q02 — Gradle, schemas and affected regressions

Run from repository root **only in QA**, after reading the shared command/isolation policy:

```sh
bash gradlew -PdomainOnly=true :core:domain:test
bash gradlew :core:data:testDebugUnitTest :app:testDebugUnitTest
bash gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :core:data:assembleDebugAndroidTest :core:data:lintDebug
```

- [ ] Record actual commands, exit codes and JUnit/lint outcomes. Expected automated gate: exit 0; required tests actually executed with no failures/errors; skipped tests explained; no lint errors/fatals and new warnings classified.
- [ ] Where schema changed, generate/export current Room schema through QA build/KSP, inspect against hand-written migration, preserve old JSON and execute populated-upgrade plus archive compatibility cases. Commit schema evidence with the validated task, not as an unverified phase-1 claim.
- [ ] Run affected instrumented suites only on a confirmed isolated package/AVD using the shared QA catalogue; do not assume current debug package is isolated. Test-class filters and variant commands must match authored test packages and target identity.

### F11-Q03 — Scenarios, native behavior and acceptance

- [ ] Execute all F11-QA01–QA08 cases and every relevant shared matrix cell; Populated-draft dialogs, IME, RTL starter text and parent picker/editor lifecycle.
- [ ] Record actual result, requirement mapping and artifact links in the scenario ledger. Source/compile/host passes cannot certify native/manual cases.
- [ ] Resolve defects, rerun owning and affected regression cases, and identify invalidated evidence. Leave blocked/unrun cases visible with precise prerequisites.
- [ ] Mark complete only for the exact validated scope when required cases pass. Assessment/deferred branch closure must explicitly state that feature runtime acceptance has not occurred. Record release blockers/authorized exceptions without silently self-waiving.

Suggested future commit grouping: one source handoff followed by QA tests, generated schemas and evidence; do not commit unrelated user changes. Creating these documents does not authorize implementation, release or commit actions now.
