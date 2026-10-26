# F12 — Selective readable exports specification

Date: 26 October 2026 · Priority: **P2** · Status: **DRAFT — NOT IMPLEMENTED / NOT VALIDATED**

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](2026-10-26-roadmap-12-readable-exports.md) · [Plan](../plans/2026-10-26-roadmap-12-readable-exports.md) · [QA scenarios](../../feature-roadmap/12-readable-exports-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Intent and dependencies

Export explicitly selected journals and memories as readable Markdown or plain text without exposing unselected records.

Dependencies: Task 01 safe file handling; task 06 privacy gate if installed.

Rank and scope come from [the feature proposal](../../FEATURE_IMPROVEMENT_PROPOSAL_2026-10-26.md). Specific new defaults in this document are proposed implementation choices, not claims of user approval. Read the existing implementation at execution time; this document does not certify its current runtime behavior.

## Architecture and alternatives

Recommended approach: Create a dedicated read-only export selection/formatter service separate from replacement backup. Snapshot selected records for consistent output; use system create-document and explicit user share actions with private temporary file ownership.

Considered alternatives: retain the current behavior and document its limitations, or introduce an independent subsystem. Retaining behavior is acceptable only for an explicitly documented investigation/deferred outcome; otherwise it does not meet the intent above. An independent replacement would duplicate existing persistence/navigation and recovery paths; extend the current module contracts unless a specific requirement makes reuse impossible. The recommendation is proposed, not a recorded user selection.

## Existing source and handoff material

- [app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt](../../../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt)
- [app/src/main/java/com/luma/lifeos/ui/settings/SettingsRoute.kt](../../../app/src/main/java/com/luma/lifeos/ui/settings/SettingsRoute.kt)
- [app/src/main/java/com/luma/lifeos/ui/journal/JournalDetailRoute.kt](../../../app/src/main/java/com/luma/lifeos/ui/journal/JournalDetailRoute.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomJournalRepository.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomJournalRepository.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomMemoryRepository.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomMemoryRepository.kt)



## Contracts and ownership

Names below are proposed interfaces to create or adapt, not claims that these declarations already exist. Keep existing public callers working or migrate every caller explicitly in the owning change. Resolve exact current types while preserving these behaviors.

- Proposed `ReadableExportSelection(from: LocalDate?, through: LocalDate?, zone: ZoneId, ids: Set<UUID>?, includeNames: Boolean=false)`; export journal/memory records only.
- Proposed `ReadableExportService.prepare(selection): PreparedReadableExport` with exact preview count and `write(format: MARKDOWN|TEXT, output: OutputStream)` over an immutable bounded snapshot.
- Event ID deduplication means a marked journal exports once. Header carries ISO-8601 occurrence offset and zone ID; original user body is preserved as text.

## Requirements

### F12-R01 — Selection

Offer individual entry or inclusive date range with JOURNAL/MEMORY type choices. Dates use [startOfDay(from), startOfDay(through+1)) in selected zone, with valid maximum-date handling. Require explicit preview confirmation showing count, range, names choice and destination action.

### F12-R02 — Privacy defaults

Linked person/place/tag names and photo attachments are excluded by default; include names is explicit and previewed. V1 excludes photo binaries entirely and states that clearly. Remove only structured linked names; do not claim free-text redaction of names written in body.

### F12-R03 — Output format

Ship UTF-8 Markdown and plain text first. Preserve user Unicode/newlines, show unambiguous ISO timestamps/zone, and escape app-generated Markdown metadata so titles cannot inject unintended structures. Separate records clearly and export marked source once.

### F12-R04 — Safe file lifecycle

Only selected records enter snapshot; preview/write use same snapshot. Selection change invalidates preview. Final success follows stream close; cancel/failure does not mutate saved records and warns about possibly incomplete provider output. Share uses explicit chooser and bounded app-private cache, cleaned after expiry safely.

### F12-R05 — Scope and reachability

Readable exports are not restorable backups and UI says so. App lock gates selection/preview/share. Support large ranges through bounded snapshot/file streaming and accessible progress/cancel; do not place all content in a Bundle or memory string.

## Two-phase delivery contract

**Phase 1 — Implementation:** implement the source/configuration/resources/documentation described in the plan, review the source and diff, and record the handoff. **No Gradle command, sync, dependency resolution, build, schema generation, lint, automated test execution, emulator launch or device test. Test authoring is deferred to QA.** Source checks do not prove compilation or correctness.

**Phase 2 — QA:** author tests/fixtures, generate and inspect schemas where relevant, run Gradle/build/lint/automated suites, execute native/manual cases, correct discovered defects and record evidence. Begin only after this task’s implementation source handoff; all initial outcomes are NOT RUN. Follow the [shared workflow](../../feature-roadmap/WORKFLOW.md) for isolation, matrix, commands, evidence and completion gates.

## Scope boundaries

CSV and photo-inclusive export are deferred follow-ups with separate format/privacy specs; no re-import, automatic sharing or PDF in v1.

## Acceptance and traceability

All five requirements must map to implementation steps and QA outcomes. [The QA document](../../feature-roadmap/12-readable-exports-qa.md) provides executable scenarios with fixtures/actions/oracles; [the plan](../plans/2026-10-26-roadmap-12-readable-exports.md) owns concrete files and implementation/QA checklists. Both phases start pending. A source-complete task is not feature-complete. For assessment/deferred branches, report the delivered investigation separately from feature/runtime acceptance.
