# IP-I06 — Findable and understandable Search Implementation Plan

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

- [ ] **1.** Confirm current matcher fields, Tasks title filter entry and protected query wiring. Use one route for Search, with a fresh entry for a new launch and single-top protection against duplicate taps.
- [ ] **2.** Place local scope copy and active-filter/reset controls with the existing Luma components. Preserve separate query clearing and page reset when criteria change, and preserve the viewport when simply returning from a result.
- [ ] **3.** Connect the distinct Tasks lookup without prepopulating its text. Preserve back navigation, provider guards, loading, empty/error/retry behavior and late result invalidation.
- [ ] **4.** Review source and localization against IP-I06-R1, IP-I06-R2, IP-I06-R3. Record the changed files, interface decisions, risks and source evidence for IP-Q01.

## QA handoff

- [ ] Map `newSearchStartsFreshFromEachOrigin`, `resultBackRestoresCriteriaAndViewport`, `protectedResultsDisappearAfterRelock` to [QA-06](./2026-11-20-product-improvements-q01-emulator-qa.md#qa-06) for the final verification task. Extend the existing test surfaces where appropriate. SearchViewModelTest; ActiveSearchFiltersTest; SearchSqlHostTest; SearchFlowTest; SearchStatesTest; SearchPersistenceTest.
- [ ] Record source status as **SOURCE COMPLETE — QA PENDING** after handoff and leave the QA result **NOT RUN** until IP-Q01 executes.

**Acceptance evidence** Seed title-only, body-only, tag/person/place-linked, protected and task-only records in EN/FR/FA. Verify three entry origins, fresh starts, per-filter/reset behavior, paging and restored viewport, invalid range, failure/retry and visibility after relock. Verify Tasks links lead to title search separately.
