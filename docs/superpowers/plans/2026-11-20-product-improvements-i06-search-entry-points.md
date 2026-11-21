# IP-I06 — Findable and understandable Search Implementation Plan

**Status:** SOURCE COMPLETE — QA PENDING.

> **For agentic workers** Use the applicable Superpowers implementation skill when this plan is authorized for execution. Steps use checkbox syntax for tracking.

**Goal** Make the existing entry search easy to reach and accurate about the content its text matching covers.

**Architecture** Reuse the existing route and repository with correct scope copy. A unified index would expand data ownership, migration and privacy risk beyond a reachability change.

**Tech stack** Kotlin, Jetpack Compose, Luma components and existing route/state/repository contracts.

**Spec** [IP-I06 specification](../specs/2026-11-20-product-improvements-i06-search-entry-points-design.md) · [Task index](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md) · [IP-Q01 scenario](./2026-11-20-product-improvements-q01-emulator-qa.md#qa-06)

## Files and interfaces

Paths are from the project root. Unqualified sibling filenames share the preceding directory.

- `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt + app/src/main/java/com/luma/lifeos/ui/today/TodayRoute.kt + TodayScreen.kt + app/src/main/java/com/luma/lifeos/ui/memory/MemoryLibraryRoute.kt` — Nullable existing-search callbacks, back stack and distinct Tasks title-search route.
- `app/src/main/java/com/luma/lifeos/ui/search/SearchRoute.kt + SearchViewModel.kt + ActiveSearchFilters.kt + SearchFilters.kt` — Scope, clear actions, saved route state and viewport.
- `core/data/src/main/kotlin/com/luma/lifeos/data/search/SearchSql.kt` — Read-only source of searchable fields; keep current SQL membership and pagination semantics.
- Update string resources and plurals in `app/src/main/res/values/`, `values-fr/` and `values-fa/` for affected copy.

Keep SearchRoute(repository, zone, onBack, onEvent) callable from current clients. Add nullable entry callbacks at Today and Memory Library, and reuse the existing Tasks title search state.

## Global constraints

IP-I01 Today header and IP-I05 Memory Library capture entry placement. Apply the [shared implementation contract](../../PRODUCT_IMPROVEMENT_TASKS_2026-11-18.md#shared-implementation-contract). Record source handoff before IP-Q01.

## Review focus

Existing asynchronous results may arrive after criteria or authorization changes. Keep generation-aware invalidation.

## Implementation checklist

- [x] **1.** Confirmed current matcher fields, Tasks title filter entry and protected query wiring. Reused one route for Search, with a fresh entry for a new launch and single-top protection against duplicate taps.
- [x] **2.** Placed local scope copy and active-filter/reset controls with the existing Luma components. Kept query clearing separate from filter reset and preserved the loaded window and viewport when returning from a result.
- [x] **3.** Connected the distinct Tasks title lookup without prepopulating its text. Preserved back navigation, provider guards, loading, empty/error/retry behavior and late result invalidation.
- [x] **4.** Reviewed source and localization against IP-I06-R1, IP-I06-R2, IP-I06-R3. Recorded changed files, interface decisions, risks and source evidence for IP-Q01.

## QA handoff

- [x] Mapped `newSearchStartsFreshFromEachOrigin`, `resultBackRestoresCriteriaAndViewport`, `protectedResultsDisappearAfterRelock` to [QA-06](./2026-11-20-product-improvements-q01-emulator-qa.md#qa-06). Test extensions and execution remain assigned to IP-Q01: SearchViewModelTest; ActiveSearchFiltersTest; SearchSqlHostTest; SearchFlowTest; SearchStatesTest; SearchPersistenceTest.
- [x] Recorded source status as **SOURCE COMPLETE — QA PENDING** and left the QA result **NOT RUN** for IP-Q01.

**Acceptance evidence** Seed title-only, body-only, tag/person/place-linked, protected and task-only records in EN/FR/FA. Verify three entry origins, fresh starts, per-filter/reset behavior, paging and restored viewport, invalid range, failure/retry and visibility after relock. Verify Tasks links lead to title search separately.

## Source handoff — 21 November 2026

**Status:** SOURCE COMPLETE — QA PENDING. **Base revision:** `fe270c09734dc0297c2e7e7e81c053b165f5271e`.

**Changed paths:** `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt`, `app/src/main/java/com/luma/lifeos/ui/common/LumaUiComponents.kt`, `app/src/main/java/com/luma/lifeos/ui/memory/MemoryLibraryRoute.kt`, `app/src/main/java/com/luma/lifeos/ui/search/SearchRoute.kt`, `app/src/main/java/com/luma/lifeos/ui/search/SearchViewModel.kt`, `app/src/main/java/com/luma/lifeos/ui/timeline/TimelineRoute.kt`, `app/src/main/java/com/luma/lifeos/ui/today/TodayRoute.kt`, `app/src/main/java/com/luma/lifeos/ui/today/TodayScreen.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml` and `app/src/main/res/values-fa/strings.xml`. This plan, the IP-I06 specification and the product task index record the handoff.

**Requirement mapping and decisions:**

- **R1:** Today, Timeline and Memory Library open the same Search destination with single-top protection. New navigation entries receive their own empty Search state. Returning from an entry reloads the saved result window in repository-capped 50-row pages and restores the saved list anchor; query/zone changes clear the anchor. Late results remain generation guarded.
- **R2:** Search scope now names entry titles and linked tag/person/place names. The Tasks action opens the existing title-search route without passing Search text and is shown only when Tasks are available and readable. Individual active-filter chips remain; “Clear all filters” preserves text, and “Clear text” preserves filters.
- **R3:** Replacement queries clear cached rows/counts while loading, option names clear before refresh, and the existing authorized repository remains the access boundary. The separate empty-library, no-match, invalid-range, query-error, paging and retry states remain.
- **QA-06 mapping:** `newSearchStartsFreshFromEachOrigin`, `resultBackRestoresCriteriaAndViewport`, `protectedResultsDisappearAfterRelock` → [QA-06](./2026-11-20-product-improvements-q01-emulator-qa.md#qa-06). Tests and emulator scenarios remain NOT RUN for IP-Q01.
- **Check:** `./gradlew check` FAILED (exit 1). App production Debug and Release Kotlin compilation completed. `:core:data:compileDebugUnitTestKotlin` and `:core:data:compileReleaseUnitTestKotlin` failed because `FocusMetadataArchiveAdapterTest.kt:120, :123 and :125` omit required `taskId` and `taskOrigin` arguments. No IP-I06 tests were authored or executed; test work remains assigned to IP-Q01.
- **Known risks:** Rendered Light/Dark, EN/FR/FA, RTL, large-text and lock-transition acceptance remain for QA-06. If a saved viewport row disappears after mutation, restoration falls back to the nearest prior list index.
- **Source patch SHA-256:** `42c436d599ea1a0d71738dd792364946a6bbdd9501560af81fc7b4618416c7fc` (app source/resources diff; handoff documents excluded).

**Suggested commit message:** `feat(search): add entry points and task-title search`
