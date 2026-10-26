# F11 — Reusable journal capture templates specification

Date: 26 October 2026 · Priority: **P2** · Status: **DRAFT — NOT IMPLEMENTED / NOT VALIDATED**

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](2026-10-26-roadmap-11-capture-templates.md) · [Plan](../plans/2026-10-26-roadmap-11-capture-templates.md) · [QA scenarios](../../feature-roadmap/11-capture-templates-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Intent and dependencies

Let users create and apply local journal title/body/tag templates without overwriting drafts silently.

Dependencies: Task 02 editor recovery; task 01 for portable template data.

Rank and scope come from [the feature proposal](../../FEATURE_IMPROVEMENT_PROPOSAL_2026-10-26.md). Specific new defaults in this document are proposed implementation choices, not claims of user approval. Read the existing implementation at execution time; this document does not certify its current runtime behavior.

## Architecture and alternatives

Recommended approach: Store templates as reusable content separate from journal records. Applying copies a snapshot into an existing journal draft; entry persistence remains in the current Journal repository.

Considered alternatives: retain the current behavior and document its limitations, or introduce an independent subsystem. Retaining behavior is acceptable only for an explicitly documented investigation/deferred outcome; otherwise it does not meet the intent above. An independent replacement would duplicate existing persistence/navigation and recovery paths; extend the current module contracts unless a specific requirement makes reuse impossible. The recommendation is proposed, not a recorded user selection.

## Existing source and handoff material

- [core/domain/src/main/kotlin/com/luma/lifeos/domain/model/Journal.kt](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/model/Journal.kt)
- [app/src/main/java/com/luma/lifeos/ui/journal/JournalEditorViewModel.kt](../../../app/src/main/java/com/luma/lifeos/ui/journal/JournalEditorViewModel.kt)
- [app/src/main/java/com/luma/lifeos/ui/journal/JournalEditorScreen.kt](../../../app/src/main/java/com/luma/lifeos/ui/journal/JournalEditorScreen.kt)
- [app/src/main/java/com/luma/lifeos/ui/journal/JournalEditorRoute.kt](../../../app/src/main/java/com/luma/lifeos/ui/journal/JournalEditorRoute.kt)
- [app/src/main/java/com/luma/lifeos/ui/QuickCaptureSheet.kt](../../../app/src/main/java/com/luma/lifeos/ui/QuickCaptureSheet.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupRows.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupRows.kt)



## Contracts and ownership

Names below are proposed interfaces to create or adapt, not claims that these declarations already exist. Keep existing public callers working or migrate every caller explicitly in the owning change. Resolve exact current types while preserving these behaviors.

- Proposed `JournalTemplate(id: UUID, name: String, title: String?, body: String, tags: Set<String>, revision: UUID)`; name 1..100 code points, title/body/tag limits reuse JournalTextLimits.
- Proposed `applyTemplate(draft: JournalDraftSnapshot, template: JournalTemplate, mode: REPLACE|APPEND): JournalDraftSnapshot`; snapshot contains title, body and tag names only; preserve all draft identity/date/photos/people/place fields outside it.
- Proposed template repository list/get/create/update/delete uses revision checks; changing template never updates saved journal entries.

## Requirements

### F11-R01 — Template content

Users can create/edit/delete named title/body/tag templates offline. Empty body is allowed in a template but a journal still requires valid nonblank body before save. Name is required and duplicate names are allowed with stable separate IDs.

### F11-R02 — Safe application

Apply to an empty draft directly. On a populated title/body draft offer Replace, Append or Cancel; replacement requires explicit selection. Append inserts two newlines before template body and keeps an existing title, using template title only if absent. Both modes merge normalized tags within existing limits or fail without partial mutation.

### F11-R03 — Context and validation

Template application preserves current timestamp, identity, photos, people/place, memory status and unsaved recovery metadata. Validate title/body/tag bounds before applying; no truncation. Undo apply is available until next text edit/save and restores the previous snapshot.

### F11-R04 — Starter templates

Provide optional Morning plan and Evening reflection examples translated EN/FR/FA, displayed in current language. Installing a starter copies editable text into a user template once by explicit action; later language/template edits never rewrite user-created content.

### F11-R05 — Persistence and navigation

Picker/editor back navigation retains parent draft. Save/apply/delete errors have retry and do not create a journal automatically. User templates are portable with new archive version; legacy restore yields none; starters stay bundled resources.

## Two-phase delivery contract

**Phase 1 — Implementation:** implement the source/configuration/resources/documentation described in the plan, review the source and diff, and record the handoff. **No Gradle command, sync, dependency resolution, build, schema generation, lint, automated test execution, emulator launch or device test. Test authoring is deferred to QA.** Source checks do not prove compilation or correctness.

**Phase 2 — QA:** author tests/fixtures, generate and inspect schemas where relevant, run Gradle/build/lint/automated suites, execute native/manual cases, correct discovered defects and record evidence. Begin only after this task’s implementation source handoff; all initial outcomes are NOT RUN. Follow the [shared workflow](../../feature-roadmap/WORKFLOW.md) for isolation, matrix, commands, evidence and completion gates.

## Scope boundaries

No general form builder, remote template marketplace, automatic journal creation or template-linked live documents.

## Acceptance and traceability

All five requirements must map to implementation steps and QA outcomes. [The QA document](../../feature-roadmap/11-capture-templates-qa.md) provides executable scenarios with fixtures/actions/oracles; [the plan](../plans/2026-10-26-roadmap-11-capture-templates.md) owns concrete files and implementation/QA checklists. Both phases start pending. A source-complete task is not feature-complete. For assessment/deferred branches, report the delivered investigation separately from feature/runtime acceptance.
