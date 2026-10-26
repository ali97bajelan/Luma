# F03 — Fast search across entry content Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use delegation only if separately authorized. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retrieve journal bodies, memory notes and tasks quickly without losing existing filters or substring behavior.

**Architecture:** Keep the existing SearchRepository API available through an adapter. Add a typed unified hit API over events and tasks; use a normalized local content index with bounded candidate retrieval and exact-match verification. Select an index strategy only after QA measures its actual semantics and speed.

**Tech Stack:** Existing Kotlin, pure domain contracts, Room, Compose, Java 17 and native Android adapters as appropriate; candidate dependency changes must be documented before integration.

**Spec:** [Fast search across entry content specification](../specs/2026-10-26-roadmap-03-search.md)

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](../specs/2026-10-26-roadmap-03-search.md) · [Plan](2026-10-26-roadmap-03-search.md) · [QA scenarios](../../feature-roadmap/03-search-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Global constraints

- Exactly two phases per task: implementation, then QA; do not interleave a test cycle into implementation.
- During implementation: no Gradle, sync, resolution, build, lint, schema generation, test execution, emulator or device. Test authoring belongs to QA.
- Preserve existing personal data/user edits, module boundaries, minimum API28 and local-first/offline defaults; follow the spec for any explicit compatibility exception.
- Calendar stays tasks/memories only; preserve navigation, drafts, revision checks, photo ownership and EN/FR/FA/RTL/light/dark support.
- Read [WORKFLOW.md](../../feature-roadmap/WORKFLOW.md) for exact isolation, source handoff and QA result/evidence obligations.

Dependencies: Task 02 establishes a reproducible candidate; task 01 protects migrations and restore.

## Review focus

- F03-R01 (Search semantics): execute F03-QA01, F03-QA02, F03-QA04; author assertion-level regressions in Q01 for the specified conditions.
- F03-R02 (Filter membership): execute F03-QA03, F03-QA04; author assertion-level regressions in Q01 for the specified conditions.
- F03-R03 (Deduplication and ordering): execute F03-QA02, F03-QA03, F03-QA06; author assertion-level regressions in Q01 for the specified conditions.
- F03-R04 (Freshness and privacy): execute F03-QA05, F03-QA08; author assertion-level regressions in Q01 for the specified conditions.
- F03-R05 (Responsiveness): execute F03-QA06, F03-QA07, F03-QA08; author assertion-level regressions in Q01 for the specified conditions.

## File map and interfaces

Existing paths are source anchors, not a request to edit every file regardless of need. New paths are proposed ownership boundaries; reconcile with existing equivalents before creating duplicates.

- Modify/review: [core/domain/src/main/kotlin/com/luma/lifeos/domain/search/Search.kt](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/search/Search.kt)
- Modify/review: [core/domain/src/main/kotlin/com/luma/lifeos/domain/repository/SearchRepository.kt](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/repository/SearchRepository.kt)
- Modify/review: [core/data/src/main/kotlin/com/luma/lifeos/data/search/SearchSql.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/search/SearchSql.kt)
- Modify/review: [core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomSearchRepository.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomSearchRepository.kt)
- Modify/review: [core/data/src/main/kotlin/com/luma/lifeos/data/dao/SearchDao.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/dao/SearchDao.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/search/SearchViewModel.kt](../../../app/src/main/java/com/luma/lifeos/ui/search/SearchViewModel.kt)
- Modify/review: [app/src/main/java/com/luma/lifeos/ui/search/SearchRoute.kt](../../../app/src/main/java/com/luma/lifeos/ui/search/SearchRoute.kt)
- Modify/review: [core/data/src/main/kotlin/com/luma/lifeos/data/db/LumaDatabase.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/db/LumaDatabase.kt)
- Create if required by selected implementation: `core/domain/src/main/kotlin/com/luma/lifeos/domain/search/UnifiedSearch.kt`
- Create if required by selected implementation: `core/data/src/main/kotlin/com/luma/lifeos/data/search/SearchContentIndex.kt`

Interfaces consumed/produced:

- Proposed `UnifiedSearchQuery(legacy: SearchQuery, kinds: Set<SearchKind>)`; kinds distinguish MOOD, JOURNAL, MEMORY, TASK and supported imported event types.
- Proposed `SearchHit(key: SearchKey, occurredAt: Instant?, snippet: String)`; SearchKey distinguishes Event(UUID) and Task(UUID), so task navigation never casts a task to a LifeEvent.
- Proposed `suspend fun searchPage(query: UnifiedSearchQuery, zone: ZoneId, offset: Int, limit: Int): UnifiedSearchPage`; page includes full total and bounded typed hits, maximum limit 50.

QA-owned tests (create only in phase 2; extend existing equivalent suites instead of duplicating them where practical):

- QA create/extend: `core/domain/src/test/kotlin/com/luma/lifeos/domain/roadmap/F03ContractTest.kt`
- QA create/extend: `core/data/src/androidTest/kotlin/com/luma/lifeos/data/roadmap/F03PersistenceTest.kt`
- QA create/extend: `app/src/test/java/com/luma/lifeos/roadmap/F03StateTest.kt`
- QA create/extend: `app/src/androidTest/java/com/luma/lifeos/roadmap/F03FlowTest.kt`

## Phase 1 — Implementation

Initial status: **PENDING**.

### F03-I01 — Define unified hit contracts

- [ ] Create UnifiedSearch.kt with typed event/task keys, query/type filters, page invariant and stable comparator. Preserve legacy SearchRepository callers with an explicit event-only adapter.
- [ ] Source-review the changed declarations/callers against F03-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F03-I02 — Implement content retrieval

- [ ] Add SearchContentIndex.kt with normalized content/candidate retrieval and exact substring verification. Implement invalidation and full rebuild after restore; document the proposed schema/migration and backup exclusion if persistent index tables are added.
- [ ] Source-review the changed declarations/callers against F03-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F03-I03 — Integrate results and navigation

- [ ] Extend SearchViewModel/Route and type filters for task hits, bounded snippets, total counts, per-kind empty states, missing-source recovery and original ID routes.
- [ ] Source-review the changed declarations/callers against F03-R01–R05 and record paths/decisions. Do not run compilation or tests.


### F03-I04 — Document benchmark hooks

- [ ] Inject clock/dispatchers and expose a repository entry point suitable for deterministic timing. Record current index choice as provisional until phase-2 semantics and performance evidence; do not run a benchmark here.
- [ ] Source-review the changed declarations/callers against F03-R01–R05 and record paths/decisions. Do not run compilation or tests.

### F03-I05 — Source handoff

- [ ] Review all changed files, call sites, permission/manifest declarations, EN/FR/FA resources and persistence/archive contracts for the actual selected scope.
- [ ] Run source-only checks such as `git diff --check`, file/link checks and XML parsing where relevant. A successful static check is not a test/build pass.
- [ ] Record Git/patch identity, implementation decisions, source-to-requirement mapping and known uncertainty. Generated schemas and all runtime verification remain pending QA.
- [ ] Mark phase 1 **SOURCE COMPLETE — QA PENDING** only after I01–I04 and this handoff are complete. Do not mark the task complete or run a Gradle/emulator command here.

## Phase 2 — QA

Initial status: **NOT RUN**. Entry requires I01–I05 handoff and isolated target verification. This phase owns test authoring, Gradle, schemas, emulator/device use and defect retesting.

### F03-Q01 — Fixtures and automated coverage

- [ ] Read the full [QA scenarios](../../feature-roadmap/03-search-qa.md), prepare its synthetic fixture, and create the QA-owned tests above. Give tests descriptive scenario-matching names; assert expected IDs/values and failure atomicity independently of implementation helpers.
- [ ] Cover every requirement and review-focus condition. Prefer small deterministic domain/state tests, real Room migration/transaction tests for persistence and Compose/native flows for platform behavior. Existing test passes cannot replace missing native or manual coverage.
- [ ] If a QA defect needs production correction, retain a failing regression and capture failure before correction where feasible; fix and rerun here without rewriting expected behavior to match a defect.
- [ ] For an explicitly deferred investigation/no-change branch, do not author tests that pretend new behavior exists. Record the branch evidence, mark inapplicable feature assertions N/A with rationale, and run the applicable existing baseline gates below. This accepts assessment scope only; feature runtime QA stays deferred.

### F03-Q02 — Gradle, schemas and affected regressions

Run from repository root **only in QA**, after reading the shared command/isolation policy:

```sh
bash gradlew -PdomainOnly=true :core:domain:test
bash gradlew :core:data:testDebugUnitTest :app:testDebugUnitTest
bash gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :core:data:assembleDebugAndroidTest :core:data:lintDebug
```

- [ ] Record actual commands, exit codes and JUnit/lint outcomes. Expected automated gate: exit 0; required tests actually executed with no failures/errors; skipped tests explained; no lint errors/fatals and new warnings classified.
- [ ] Where schema changed, generate/export current Room schema through QA build/KSP, inspect against hand-written migration, preserve old JSON and execute populated-upgrade plus archive compatibility cases. Commit schema evidence with the validated task, not as an unverified phase-1 claim.
- [ ] Run affected instrumented suites only on a confirmed isolated package/AVD using the shared QA catalogue; do not assume current debug package is isolated. Test-class filters and variant commands must match authored test packages and target identity.

### F03-Q03 — Scenarios, native behavior and acceptance

- [ ] Execute all F03-QA01–QA08 cases and every relevant shared matrix cell; Representative phone performance, 50k fixture, RTL/large text and minified navigation.
- [ ] Record actual result, requirement mapping and artifact links in the scenario ledger. Source/compile/host passes cannot certify native/manual cases.
- [ ] Resolve defects, rerun owning and affected regression cases, and identify invalidated evidence. Leave blocked/unrun cases visible with precise prerequisites.
- [ ] Mark complete only for the exact validated scope when required cases pass. Assessment/deferred branch closure must explicitly state that feature runtime acceptance has not occurred. Record release blockers/authorized exceptions without silently self-waiving.

Suggested future commit grouping: one source handoff followed by QA tests, generated schemas and evidence; do not commit unrelated user changes. Creating these documents does not authorize implementation, release or commit actions now.
