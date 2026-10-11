# T14 Daily summaries implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline with one fresh final review.

Goal: reproducible cached daily metrics from saved records, visible on Today.
Spec: docs/superpowers/specs/2026-10-11-t14-daily-summaries-design.md.
Architecture: domain calculator/repository -> Room projection/cache -> Today summary state/card.
Stack: existing API28/35, AGP8.7.3, Kotlin2.2.10, Room2.8.5, Compose, JDK17.
Constraints: no dependencies/schema/SDK changes, health acquisition, commits or device execution. Preserve uncommitted T12 and existing untracked files; execute in the shared checkout authorized for ongoing task work. Baseline HEAD16663cf plus completed uncommitted T12; 153 passing JVM tests. Common-file baseline copies are in /tmp/luma-t14-baseline.

Review Focus:
- Imported parent occurrence can differ from native usage start: index/query both and aggregate by the correct date.
- Promotions and duplicate snapshots must not count events twice; typed absence must not become measured zero.
- Stopped collectors, raw updates and concurrent cache rebuilds must not leave fresh-looking stale values or trigger feedback loops.
- Old/corrupt cache and skipped civil dates must rebuild safely without altering raw data.
- Decimal activity totals must remain deterministic; distinct units/providers must not be combined, and loading failures must preserve core navigation.

- [x] Task 1 domain: create summary/DailySummary.kt (models/calculator), repository/DailySummaryRepository.kt and summary/DailySummaryTest.kt. API calculateDailySummary(LocalDate, ZoneId, List<SummaryRecord>): DailySummary, repository observe/get/recalculate(date, zone). Tests first for literal mixed values, memory promotion, absent/known-zero signals, order/dedup/conflicts, unit/provider segregation, native-start bounds, DST/skipped dates and decimal precision. Run :core:domain:test RED (missing API), implement, run GREEN.
- [x] Task 2 data: entity/SummarySource.kt, DAO indexed projections, mapping/SummaryCacheCodec.kt and repository/RoomDailySummaryRepository.kt; expose storage.summaries. Consume domain models/calculator. Produce reusable fresh cache reads, forced rebuild and source-driven observation. Add DailySummaryPersistenceTest fixtures for mixed metrics/cache-only content, cache reuse/deletion/corruption/version rebuild, mutation/promotion/deletion invalidation, atomic failure, native start vs parent date, live changes and DST/skipped dates. Compile data instrument APK; device execution pending.
- [x] Task 3 app: ui/summary/DailySummaryState.kt and DailySummaryCard.kt; optional repository argument through LifeOsApp/TodayRoute/TodayScreen, with production wiring in MainActivity. Add summary state tests first for loading/data/update/error/retry/cancellation, and Compose fixture exercising actual Room+calculator with summary and event browsing/date navigation. Consume repository.observe(date,zone); no fake fallback values. Run app JVM tests RED/GREEN, compile app debug/instrument APKs.
- [ ] Task 4 verify/review/docs: all JVM suites, debug and app/data instrumentation APKs, data/adapter lint, app lint attempt; one reviewer with spec/plan and T14-only review range using baseline common-file copies. Fix important findings with regressions. Update README/ARCHITECTURE/TASKS/VALIDATION and provide commit message. Record actual tests separately from pending device acceptance.

Verification: bash gradlew --offline -I /tmp/luma-t12-mirror.init.gradle :core:domain:test :app:testDebugUnitTest :core:data:testDebugUnitTest :integration:usage:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest :core:data:lintDebug :integration:usage:lintDebug --no-daemon --console=plain.
Ledger: Task1->Task2 calculator models/API align; Task2->Task3 repository.observe(date,zone) align. Existing cache storage DTOs stay storage-only; T14 introduces a new domain repository without changing legacy APIs. Source-only projections avoid unrelated photo/catalog failures and private text in the cache. No unresolved interface conflicts.

Task 1 complete: :core:domain:test RED missing API -> GREEN 55 tests (6 new). Literal mixed totals, promotion semantics, absence/zero, exact decimal/order/dedup, unit/provider segregation, usage-start day boundaries and DST/skipped dates pass.

Task 2 implementation/fixture compilation: data instrument APK and data lint pass after missing API RED, with seven initial Room cases compiled. A concurrent-capture fixture was added afterward and remains in the final compile run. Codec validates cached counts/sums/nulls/group identity and old-format/version fallback; raw metric projections avoid content text and unrelated catalog/photo reads. Room fixtures are not runtime-verified without a device.

Task 3 complete: app JVM RED missing state API -> GREEN 96 tests (4 new), debug APK builds. Separate summary loading/retry/cancellation and selected-day validation preserve feed use; two actual Room+calculator Compose fixtures are in the final instrument compile run.
