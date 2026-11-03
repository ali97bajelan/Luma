# Focus Names, Tags, and Insights Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task after the draft specification is reviewed. Steps use checkboxes for tracking. FI03 source implementation is recorded below; Room-generated schema and runtime validation remain deferred.

**Goal:** Add named/tagged focus sessions and useful period insights without changing timer behavior.

**Architecture:** Extend the existing focus repository and shared runtime, using a focus-specific single-tag catalog. Pure domain functions calculate insights from credited terminal run overlaps; Compose consumes generation-bound repository reports and independently filtered history.

**Tech Stack:** Kotlin/JVM, existing coroutines/Room/Compose, JUnit 4; Python standard library for standalone test orchestration/SQLite fixtures. No new app dependencies.

**Spec:** [Draft specification](../specs/2026-11-03-focus-names-tags-insights-design.md)

## Global constraints

- No emulator, device, Gradle, instrumentation, Android lint, or APK build commands for this work; use standalone source-level JVM tests and static checks only.
- Keep existing timer, checkpoint, recovery, navigation guard, generation, replay and deletion contracts.
- Preserve Kotlin/Compose architecture, existing SDK levels and dependencies; operate offline without new permissions.
- Supply English, French and Persian resources; support RTL, wrapping text and 48 dp minimum controls.
- Report runtime/UI/Room integration verification as NOT RUN; standalone tests cannot establish those guarantees.

## Review Focus

1. Emoji/Persian name lengths and control characters: validation must reject invalid input without truncating valid text (FI01 tests).
2. Ambiguous Start followed by changed draft or database replacement: preserve original replay payload and reject old-generation writes (FI02/FI05 tests).
3. Midnight/DST runs and equal longest durations: correct clipped credit and stable winner (FI04 tests).
4. Search `%`/`_`, more than 50 matches and filter changes during load: literal matching, pre-page counts and no stale results (FI06 tests).
5. Legacy archives and cached private reports after relock/restore: null metadata adaptation, no live replacement on failure and no stale labels exposed (FI03/FI07 tests).

## File structure and proposed interfaces

Existing source roots:

- Domain: `core/domain/src/main/kotlin/com/luma/lifeos/domain/`.
- Data: `core/data/src/main/kotlin/com/luma/lifeos/data/`.
- App: `app/src/main/java/com/luma/lifeos/`.
- Tests mirror these packages under each module's `src/test/kotlin` (app uses `src/test/java`).

Create domain `focus/FocusMetadata.kt` for names/tags and `focus/FocusInsights.kt` for aggregation. Modify `FocusSession.kt` and `FocusRepository.kt` for metadata and read contracts. Append the following proposed interfaces, keeping existing report methods for Today/Review:

```kotlin
data class FocusTag(val id: UUID, val name: String)
data class FocusMetadata(val name: String?, val tag: FocusTag?)
data class FocusStartInput(val name: String, val tagName: String?)
fun normalizeFocusStartInput(input: FocusStartInput): FocusStartInput // throws IllegalArgumentException
sealed interface FocusTagFilter {
    data object All : FocusTagFilter
    data object Untagged : FocusTagFilter
    data class Tagged(val id: UUID) : FocusTagFilter
}
data class FocusHistoryQuery(val range: TimeRange?, val search: String, val tag: FocusTagFilter)
data class FocusTagSummary(val tag: FocusTag?, val totalMillis: Long, val sessions: Int, val percentage: BigDecimal)
data class FocusLongest(val session: FocusSession, val contributionMillis: Long)
data class FocusInsights(
    val totalMillis: Long, val sessions: Int, val averageMillis: Long,
    val interruptedContributors: Int, val tags: List<FocusTagSummary>, val longest: FocusLongest?,
)
fun focusInsights(sessions: List<FocusSession>, range: TimeRange?): FocusInsights
```

`FocusRepository.start` appends required `input: FocusStartInput`. Add `observeInsights(range: TimeRange?): Flow<FocusInsights>`, `observeTagSuggestions(): Flow<List<FocusTag>>`, and `history(query: FocusHistoryQuery, offset: Int, limit: Int = FOCUS_HISTORY_PAGE_SIZE): FocusPage`. Preserve the old history method as a forwarding All/empty-search overload. Add the same required input to both coordinator start overloads; propagate through runtime pending operations and ViewModel. All fakes/overrides must compile against the updated interface. Invalid input maps to a dedicated metadata invalid reason without starting a timer.

## FI01 — Domain metadata and rules

**Files:** Create domain `focus/FocusMetadata.kt`; modify `focus/FocusSession.kt`; create test `core/domain/src/test/kotlin/com/luma/lifeos/domain/focus/FocusMetadataTest.kt`.

**Consumes:** Existing `domain/model/Journal.kt` `Tag.normalize` and session invariants.
**Produces:** Metadata/start input and normalization contracts above; legacy-null session default.

- [x] Add failing tests for trimmed name/tag, blank name, 120/121 Unicode code points, 50/51 tag UTF-16 units, newline/control rejection, emoji/Persian round-trip, optional tag and Work/work identity. Blank present tag is invalid; absent tag is null.
- [x] Compile/run this test through standalone Kotlin/JUnit; confirm missing API failure before implementation.
- [x] Implement normalization and append the legacy-compatible metadata default. Test names need not be unique and translated fallback text is never persisted.
- [x] Run metadata and existing focus domain tests; record exact output.

Source verification: `python3 scripts/run_focus_domain_tests.py` — OK (23 tests). Runtime/UI and Room generated-schema validation remain NOT RUN.

## FI02 — Atomic writes and metadata lifecycle

**Files:** Modify domain `focus/FocusRepository.kt`, `focus/FocusSession.kt`; data `focus/{FocusEntities,FocusDao,RoomFocusRepository}.kt`; app `focus/{FocusCoordinator,FocusRuntime,FocusViewModel}.kt`; all implementing repository fakes found by `rg 'FocusRepository'`. Create data `focus/FocusMetadataMapping.kt` and data tests `focus/FocusMetadataMappingTest.kt`, app test `focus/FocusMetadataCommandTest.kt`.

**Consumes:** FI01 models/rules.
**Produces:** Required start input propagation, `focus_tag` entity, session metadata mapping and normalized receipt payload identity.

- [ ] Write failing pure tests for metadata mapping, stable payload encoding, changed-metadata command reuse, legacy receipt decoding, and immutable pending Start input on retry.
- [ ] Implement entities/query/mapping and transaction changes together with the FI03 migration before opening a modified database. Preserve all timer fields and existing unique indices. Include metadata in receipt comparison; create/reuse tags only after authority/conflict validation.
- [ ] Update both coordinator overloads, runtime pending-operation input and all call sites/test fixtures. Pause/Resume/Stop/reconcile do not replace metadata. Add tests for input preservation and stale-generation result handling with fake repositories.
- [ ] Add source checks that mutation paths carry required metadata and no default new-Start bypass exists. Run source-level tests. Explicitly leave actual Room transaction concurrency NOT RUN.

## FI03 — Migration, backup and legacy compatibility

**Files:** Fixture-test the existing data `db/LumaDatabase.kt` 9→10 migration; modify data `backup/{BackupArchive,BackupValidation,RoomBackupService}.kt`; create data `backup/FocusMetadataArchiveAdapter.kt` and test `backup/FocusMetadataArchiveAdapterTest.kt`; create `scripts/check_focus_metadata_sql.py`; update `docs/{BACKUP_FORMAT,PRIVACY}.md`. Future Room snapshot target: `core/data/schemas/com.luma.lifeos.data.db.LumaDatabase/10.json` after permitted generation.

**Consumes:** FI02 entity/SQL contract.
**Produces:** Migration 9→10 and archive 8 adapters, rebased if versions advance.

- [x] Add adapter fixtures for schema 7 null metadata, schemas 1–6 absence of focus, schema 8 round-trip, dangling/duplicate tags, invalid labels, open sessions and omitted unused tags.
- [x] Implement staged validation/adaptation and export/import table ordering. Keep legacy command receipts compatible and preserve current reset/generation rotation and paused-export behavior.
- [x] Create a Python SQLite fixture check that extracts and executes the actual migration SQL; seed v9 running/paused/terminal sessions, execute 9→10 and assert existing rows, indices, FKs and receipts survive.
- [x] Run pure adapter tests and `python3 scripts/check_focus_metadata_sql.py`; record what each proves. Do not generate or fabricate Room schema JSON manually; record schema generation/Room validation as deferred under the no-Gradle constraint.

## FI04 — Tag summaries and longest session

**Files:** Create domain `focus/FocusInsights.kt`, test `core/domain/src/test/kotlin/com/luma/lifeos/domain/focus/FocusInsightsTest.kt`; modify domain `focus/FocusRepository.kt`, data `focus/{FocusDao,RoomFocusRepository}.kt`; create app `ui/focus/FocusInsightsState.kt` and test `app/src/test/java/com/luma/lifeos/ui/focus/FocusInsightsStateTest.kt`.

**Consumes:** FI01 metadata, existing overlap arithmetic and FI02 persisted tags.
**Produces:** `focusInsights`, repository insight/suggestion flows, query-bound Loading/Ready/Error state.

- [ ] Add failing tests using the spec's 45-minute example, untagged bucket, multi-run pauses, positive interrupted credit, zero duration, no data, exact boundary, Tehran midnight, 23/25-hour DST days, all-time and deterministic ties.
- [ ] Add duplicate-identical snapshot deduplication, conflicting duplicate rejection and checked-overflow cases. Assert sum(tag.totalMillis)==totalMillis, truncated average and one-decimal HALF_UP percentages.
- [ ] Implement pure calculation by deduplicating consistently with `focusStatistics`, then using `focusRangeContributionMillis` (or full credit for null range). Ignore nonterminal sessions at the repository query boundary; pure function rejects them to prevent silent mixed snapshots.
- [ ] Read a consistent complete terminal snapshot off the main thread, including associated tags; do not derive aggregates from a paged result. Observe all relevant table invalidations and expose top-10 referenced-tag suggestions in specified order.
- [ ] Test reducer cancellation/query/generation keys, empty vs error and stale result rejection. Run standalone domain/state tests; do not claim unmeasured all-time performance or Room observation coverage.

## FI05 — Setup, session labels and insight cards

**Files:** Modify app `focus/{FocusRuntime,FocusViewModel}.kt`, `ui/focus/{FocusRoute,FocusScreen}.kt`; create `ui/focus/{FocusSetupState,FocusInsightsCard}.kt`; modify `ui/focus/FocusReportState.kt` as needed; create app test `ui/focus/FocusSetupStateTest.kt`; modify all three `app/src/main/res/values{,-fr,-fa}/strings.xml`.

**Consumes:** FI01 normalization, FI02 required Start input, FI04 insight flow.
**Produces:** Setup draft reducer and Compose presentation using existing resource/theme patterns.

- [ ] Add failing pure state tests for Start enablement, draft recreation, failed Start preservation, ambiguous retry with changed editor text, successful clearing, reset-generation clearing and period/zone switching.
- [ ] Implement SavedStateHandle draft serialization and explicit captured-input submission. Keep IO failure distinct from validation. Add name/tag fields and suggestions without creating tags during typing.
- [ ] Display metadata in all session states; keep existing Pause/Stop and recovery action ordering. Wire idle period insights, five tag rows/Show all, longest detail and loading/error/retry states. Period comparison reuses existing rules; All time hides it.
- [ ] Add EN/FR/FA text, accessibility labels and text metrics. Check source for hardcoded new user copy and unsafe logging. Run pure reducer tests and resource parity checks; visual/IME/RTL/large-font/device interaction remains NOT RUN.

## FI06 — History search and tag filters

**Files:** Create domain `focus/FocusHistoryQuery.kt` with query/filter contracts; modify data `focus/{FocusDao,RoomFocusRepository}.kt`, app `ui/focus/{FocusHistoryViewModel,FocusHistoryRoute}.kt`; create data `focus/FocusHistoryFilter.kt` and test `core/data/src/test/kotlin/com/luma/lifeos/data/focus/FocusHistoryFilterTest.kt`, app test `ui/focus/FocusHistoryFilterStateTest.kt`.

**Consumes:** FI02 catalog/session data, FI04 insights for invalidation only.
**Produces:** Filtered repository paging overload and saved history-filter state.

- [ ] Write failing tests for All/Untagged/Tagged, trimmed Locale.ROOT substring matching, null legacy names, `%`/`_` literal input, >50 matches, consistent total/order and combined period/search/tag predicates. Choose a pure shared matcher if SQLite collation cannot provide the specified Unicode matching; bound DB reads in batches before final filtered paging.
- [ ] Implement filters before page slicing/counts, stable started-descending/UUID-descending ordering, and tag association loads without row multiplication. Keep page size 50 and old-history forwarding behavior.
- [ ] Add reducer tests and UI wiring for resetting page offset, canceling stale results, restoration and database replacement. Preserve page-bound deletion authority; refresh history/insights when deletion succeeds.
- [ ] Run filter/state tests standalone and review source wiring. Keep history filters explicitly separate from whole-period insight cards.

## FI07 — Privacy and final code-only verification

**Files:** Review app `ui/{LifeOsApp.kt,privacy/AppLockSettingsRoute.kt}`, `ui/focus/{TodayFocusCard,WeeklyFocusCard,FocusRoute,FocusHistoryRoute}.kt`; modify gate wiring only where missing; create pure app test `ui/focus/FocusInsightsVisibilityTest.kt` and evidence `docs/validation/2026-11-03-focus-insights.md` (use actual execution date for future runs).

**Consumes:** All P0 work and existing Focus access decisions.
**Produces:** Coverage matrix with precise PASS/FAIL/NOT RUN states.

- [ ] Add pure access-presentation tests: locked Focus suppresses metadata/insights on every affected surface; relock and restore invalidate cached private state. Do not invent a second credential flow.
- [ ] Run all supported standalone JVM source suites; expand the local runner source/classpath list explicitly for pure adapter/state classes, rather than compiling Android-dependent classes without their runtime. Never replace production classes with stubs to claim integration passes.
- [ ] Check relative document links, EN/FR/FA key parity, source call sites and `git diff --check`; record test counts and excluded boundaries.
- [ ] Review FI-A01–FI-A07 against changes. Mark Android UI/lifecycle, generated Room schema and actual Room migration/concurrency as NOT RUN; source verification alone leaves those acceptance portions pending.

## Standalone validation available now

`python3 scripts/run_focus_domain_tests.py` compiles the existing production focus domain sources and their JUnit tests using a locally installed Kotlin compiler and cached JUnit/coroutines jars, then invokes JUnitCore directly. It uses a temporary output directory, invokes no Gradle and downloads nothing. This is baseline evidence only; the proposed feature APIs/tests do not exist yet. The runner discovers all focus-domain tests, so extend its production dependency list if FI01 introduces `Tag.normalize` dependencies.

## Handoff status

FI01 is complete at source level; FI02 source changes and FI03 source implementation are present, while FI02 runtime verification and FI04–FI07 remain pending. Session metadata models and persistence exist; insight screens remain pending. Room-generated schema validation and Android runtime checks were not run. No commit, emulator run or Gradle command was required for FI03.
