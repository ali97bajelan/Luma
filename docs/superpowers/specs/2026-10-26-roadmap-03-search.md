# F03 — Fast search across entry content specification

Date: 26 October 2026 · Priority: **P1** · Status: **DRAFT — NOT IMPLEMENTED / NOT VALIDATED**

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](2026-10-26-roadmap-03-search.md) · [Plan](../plans/2026-10-26-roadmap-03-search.md) · [QA scenarios](../../feature-roadmap/03-search-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Intent and dependencies

Retrieve journal bodies, memory notes and tasks quickly without losing existing filters or substring behavior.

Dependencies: Task 02 establishes a reproducible candidate; task 01 protects migrations and restore.

Rank and scope come from [the feature proposal](../../FEATURE_IMPROVEMENT_PROPOSAL_2026-10-26.md). Specific new defaults in this document are proposed implementation choices, not claims of user approval. Read the existing implementation at execution time; this document does not certify its current runtime behavior.

## Architecture and alternatives

Recommended approach: Keep the existing SearchRepository API available through an adapter. Add a typed unified hit API over events and tasks; use a normalized local content index with bounded candidate retrieval and exact-match verification. Select an index strategy only after QA measures its actual semantics and speed.

Considered alternatives: retain the current behavior and document its limitations, or introduce an independent subsystem. Retaining behavior is acceptable only for an explicitly documented investigation/deferred outcome; otherwise it does not meet the intent above. An independent replacement would duplicate existing persistence/navigation and recovery paths; extend the current module contracts unless a specific requirement makes reuse impossible. The recommendation is proposed, not a recorded user selection.

## Existing source and handoff material

- [core/domain/src/main/kotlin/com/luma/lifeos/domain/search/Search.kt](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/search/Search.kt)
- [core/domain/src/main/kotlin/com/luma/lifeos/domain/repository/SearchRepository.kt](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/repository/SearchRepository.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/search/SearchSql.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/search/SearchSql.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomSearchRepository.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomSearchRepository.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/dao/SearchDao.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/dao/SearchDao.kt)
- [app/src/main/java/com/luma/lifeos/ui/search/SearchViewModel.kt](../../../app/src/main/java/com/luma/lifeos/ui/search/SearchViewModel.kt)
- [app/src/main/java/com/luma/lifeos/ui/search/SearchRoute.kt](../../../app/src/main/java/com/luma/lifeos/ui/search/SearchRoute.kt)
- [core/data/src/main/kotlin/com/luma/lifeos/data/db/LumaDatabase.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/db/LumaDatabase.kt)



## Contracts and ownership

Names below are proposed interfaces to create or adapt, not claims that these declarations already exist. Keep existing public callers working or migrate every caller explicitly in the owning change. Resolve exact current types while preserving these behaviors.

- Proposed `UnifiedSearchQuery(legacy: SearchQuery, kinds: Set<SearchKind>)`; kinds distinguish MOOD, JOURNAL, MEMORY, TASK and supported imported event types.
- Proposed `SearchHit(key: SearchKey, occurredAt: Instant?, snippet: String)`; SearchKey distinguishes Event(UUID) and Task(UUID), so task navigation never casts a task to a LifeEvent.
- Proposed `suspend fun searchPage(query: UnifiedSearchQuery, zone: ZoneId, offset: Int, limit: Int): UnifiedSearchPage`; page includes full total and bounded typed hits, maximum limit 50.

## Requirements

### F03-R01 — Search semantics

Retain title/catalog substring matching and 200-character query bound. Add journal body, memory note, task title/note. Normalize NFC and locale-independent case consistently in index and verifier; accents remain distinct and Persian ی/ي and ک/ك remain distinct in v1. Document this explicitly.

### F03-R02 — Filter membership

Keep existing date/catalog filters conjunctive across categories and any-match within tags/people. A task with no person/place/tag relation is excluded when such a filter is active. Event date filters use occurrence in the selected zone; task date filters use dueDate, with undated tasks excluded only when a date bound is active. Task calendar days do not convert to instants.

### F03-R03 — Deduplication and ordering

A marked journal/mood matches MEMORY and its source kind but appears once by event ID. Events order by occurrence descending and UUID descending; tasks order by updatedAt then UUID descending. All-kinds results use event occurrence/task updatedAt descending, UUID descending, then key kind for collisions; date filtering and ordering are intentionally separate.

### F03-R04 — Freshness and privacy

Create/edit/delete, catalog rename and replacement restore invalidate results and update/rebuild any derived index. Indexes stay app-private and are rebuildable, not portable archive payload. Snippets are at most 160 Unicode code points, plain text, and never logged.

### F03-R05 — Responsiveness

Retain 300 ms input debounce, bounded paging, stale-result cancellation, offline operation and original-record navigation. QA target: p95 under 300 ms repository first-page retrieval for a 50,000-record fixture, excluding debounce; include count and mapping in timing. A failed target remains open, not waived by a fast SQL microbenchmark.

## Two-phase delivery contract

**Phase 1 — Implementation:** implement the source/configuration/resources/documentation described in the plan, review the source and diff, and record the handoff. **No Gradle command, sync, dependency resolution, build, schema generation, lint, automated test execution, emulator launch or device test. Test authoring is deferred to QA.** Source checks do not prove compilation or correctness.

**Phase 2 — QA:** author tests/fixtures, generate and inspect schemas where relevant, run Gradle/build/lint/automated suites, execute native/manual cases, correct discovered defects and record evidence. Begin only after this task’s implementation source handoff; all initial outcomes are NOT RUN. Follow the [shared workflow](../../feature-roadmap/WORKFLOW.md) for isolation, matrix, commands, evidence and completion gates.

## Scope boundaries

No cloud search, semantic embeddings, OCR, query-history telemetry, or silent change from substring to token-only search.

## Acceptance and traceability

All five requirements must map to implementation steps and QA outcomes. [The QA document](../../feature-roadmap/03-search-qa.md) provides executable scenarios with fixtures/actions/oracles; [the plan](../plans/2026-10-26-roadmap-03-search.md) owns concrete files and implementation/QA checklists. Both phases start pending. A source-complete task is not feature-complete. For assessment/deferred branches, report the delivered investigation separately from feature/runtime acceptance.
