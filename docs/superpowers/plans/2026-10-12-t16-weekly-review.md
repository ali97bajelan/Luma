# Weekly Review Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline. Steps use checkbox syntax for tracking.

**Goal:** Deliver T16's offline weekly statistics, comparisons and navigable stored sources.
**Architecture:** Pure domain period/calculation models; metric-only Room reads in one transaction; bounded SQL source pages; lifecycle-aware Compose routes.
**Tech Stack:** Existing Kotlin, Coroutines, Room and Compose; no new dependencies or schema.
**Spec:** docs/superpowers/specs/2026-10-12-t16-weekly-review-design.md

## Global Constraints

- ISO Monday–Sunday; current week so far compared with matching previous weekdays.
- No AI service, network permission, account, fabricated data or causal narrative.
- Missing measurements are unavailable; known zero remains zero.
- Keep source pages at 50 rows; no journal-body loading for report calculations.
- Preserve existing edits, promotions, integration rules and untracked user files.

## Review Focus

- A mood-rich date must not receive the same weight as a one-sample date.
- A saved native usage bucket remains dated by its start even when its event moves.
- Deleted catalog links and corrected records must change both statistics and sources.
- A selected historical week must survive source navigation and recreation.
- Obsolete week/page work must not overwrite a new selection; DST is civil-date based.

### Task 1: Domain report

Files: domain/review/WeeklyReview.kt; repository/WeeklyReviewRepository.kt; review/WeeklyReviewTest.kt.
Interfaces: reviewPeriod(date,today,zone): ReviewPeriod; calculateWeeklyReview(period,records,usageDays): WeeklyReview. Repository observe(period): Flow<WeeklyReview>, observeChanges(): Flow<Unit>, sources(period,metric,offset,limit): SearchPage.
- [x] Write failing tests: weighted mean 13/3, current Monday one-day previous period, seven-date DST week, promotions counted once, missing/zero, identity/determinism and per-date usage aggregation.
- [x] Run :core:domain:test; confirm missing APIs.
- [x] Implement immutable metric inputs, periods, totals and calculator; run domain suite green.

### Task 2: Transactional data and sources

Files: entity/ReviewSource.kt; LifeEventDao.kt projections; review/ReviewSql.kt; repository/RoomWeeklyReviewRepository.kt; RoomUsageRepository.kt; LumaStorage.kt; WeeklyReviewPersistenceTest.kt.
Consumes: Task 1 models; produces repository implementation and storage.reviews.
- [x] Write Room fixtures before implementation for weighted inputs, source filters/paging, live corrections/catalog changes and session/native bucket dating.
- [x] Compile instrumentation; confirm missing APIs; implement indexed projections and bounded bound-argument SQL.
- [x] Reuse loadDaily inside the report transaction; replace fallback full-graph occurrence reads with native-start metric reads.
- [ ] Compile data instrumentation and run device checks if a device is already available; data lint.

### Task 3: Review and source browsing

Files: ui/review/WeeklyReviewState.kt, WeeklyReviewRoute.kt, ReviewSourcesRoute.kt; LifeOsApp.kt; MainActivity.kt; strings.xml; WeeklyReviewStateTest.kt; WeeklyReviewFlowTest.kt.
Consumes: repository; produces Review tab and source/usage routes.
- [x] Write state tests for errors/retry/cancellation and source paging; run RED then implement and run GREEN.
- [x] Write Compose fixtures before rendering for week navigation, report sections, exact metric sources and restoration.
- [x] Add saved week selector, calendar, lazy statistics/comparisons, bounded memories/source previews, catalog routes and saved per-date usage views. Wire production.
- [ ] Compile normal debug/test APKs; execute available device fixtures.

### Task 4: Verify, review and document

- [x] Run all five JVM suites, normal debug/test APKs and data/usage lint. Record existing app/health lint limitations.
- [ ] Measure realistic multi-year fixture loads; target repository p95 <300 ms.
- [x] Request one fresh read-only code review; address important findings with regressions.
- [x] Update README/ARCHITECTURE/TASKS/VALIDATION with actual evidence; no complete status for unexecuted acceptance checks.

## Execution ledger

User approved design and requested implementation with “do it”; execute inline without another approval handoff.
Ruling: work in the shared Android Studio checkout, preserving existing files, rather than moving the user's project to another directory. Task authorization covers reversible edits here; no branch merge/push is requested. Cost: changes remain alongside existing untracked files, which are preserved.
Pre-flight: calculator models feed Room projections; repository's source page contract feeds both Review preview and source browsing; usage uses the same loadDaily calculation as Settings.

Task 1: implemented; domain missing-API RED -> GREEN. Weighted means, calendar/DST bounds, promotion, catalog identity, activity grouping, missing/zero and deterministic usage totals pass.
Task 2: implemented; missing repository/SQL RED -> GREEN compilation and host SQLite checks. Room metric relations confirmed in generated code to exclude note/body/media fields. No connected device; compiled Room fixtures are not executed.
Task 3: implemented; state missing-API RED -> GREEN, then source-clamping regression exposed an illegal Flow emission context. transformLatest fixes it; all state tests pass. Debug and instrumentation APKs compile after resolving an experimental test API import.
Final review: fresh read-only reviewer found illegal source Flow emissions and legacy zero masked by empty partial windows (important), plus reading excluded trailing previous-week dates (minor).
Final: fixed illegal emissions — sourcesRefreshAndClampAnOffsetAfterDeletion RED -> GREEN.
Final: fixed observed zero fallback — SavedUsageSelectionTest missing-API RED -> GREEN; added a Room fixture for weekly and saved-day views.
Final: minor (deferred): partial-week report loads up to six previous-week dates outside its comparison before discarding them. Correctness is unaffected; representative repository p95 remains pending.
Validation: adb devices -l returned no attached devices. Device UI/restoration and Room fixtures, including the 50,000-entry/five-year/2,500-selected repository timing fixture, remain pending. Host source SQL p95 0.006–0.012 ms excludes Room mapping/device UI.

App and health lint succeeded with the currently configured lint 8.8.2; their previously documented detector crashes did not recur. Reworded four new quantity strings as labeled counts to avoid introducing pluralization warnings. No existing target/dependency warnings were changed.

Final verification: all 239 JVM tests forced to run (94 domain, 117 app, 9 data, 13 usage, 6 health), zero failures/errors/skips. Normal debug/app-test/data-test APKs build. All four lint modules succeed, app eight pre-existing warnings. git diff --check passes. Device-dependent steps remain unchecked.
