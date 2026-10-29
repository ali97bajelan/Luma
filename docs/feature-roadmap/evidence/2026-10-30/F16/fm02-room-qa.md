# FM02 Room/repository QA — 30 October 2026

**Result:** PASS for the FM02 Room schema and repository scope. The integrated F16 scenarios remain blocked by FM03, FM05 and FM06 work. The full `core:data` regression run also exposed two failures in existing Todo migration/backup tests; neither is an FM02 focus test.

## Candidate and environment

- Base source commit: `a0c22f3749c2f6e705659b734b9e660a4f943295`.
- FM02 QA code/schema patch SHA-256: `002b351ddfd9b9d40da8357a44db11d16bf4ddacd3c423f1715e89f4db38a936`. This digest covers the two new instrumentation classes, `FocusEntities.kt`, `LumaDatabase.kt`, the generated v8 schema and schema-history README.
- Exported Room v8 schema SHA-256: `4446467cbcf05c8dcb59e22f718709b18247190d1d6409bfb50f3a991126374b`.
- Instrumentation APK: `core/data` debug Android-test variant, package/target `com.luma.lifeos.data.test`, runner `androidx.test.runner.AndroidJUnitRunner`; APK SHA-256 `5b42cea41ee82a37635656604147005e750e6f8cbaecc3c457798a9e51dfae11`.
- Device: AVD `Medium_Phone_API_36.1`, emulator model `sdk_gphone64_x86_64`, API 36 / Android 16. Time zone `Asia/Tehran`; reported locale was empty (`system_locales=null`), font scale 1.0, portrait 1080×2400 at density 420 (~411×914 dp).
- Host toolchain: OpenJDK 17.0.20.1 and Gradle 9.1.0. Instrumentation tests use fixed synthetic timestamps and named disposable databases. The `core:data` target package is separate from installed `com.luma.lifeos` and `com.luma.lifeos.qa`; neither app database was opened or changed.
- The API 36.1 emulator use was explicitly requested for this QA. No focus UI or lifecycle screen exists yet, so this is storage/repository verification only.

## Executed checks

1. `GRADLE_USER_HOME=/tmp/luma-f02-gradle-home bash ./gradlew :core:data:testDebugUnitTest :core:data:connectedDebugAndroidTest --no-daemon --console=plain --rerun-tasks`: the host task passed **29 tests, 0 failures, 0 skips**. The combined Gradle command exited 1 because of the two unrelated connected failures below. Exact output is in [the full regression log](core-data-regression.log).
2. `GRADLE_USER_HOME=/tmp/luma-f02-gradle-home bash ./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.data.focus.FocusPersistenceTest,com.luma.lifeos.data.focus.FocusMigrationTest --no-daemon --console=plain`: **9 passed, 0 failed, 0 skipped**, exit 0. See [the focused instrumentation log](fm02-focused-instrumentation.log).
3. Full `:core:data:connectedDebugAndroidTest` in check 1: **172 executed, 170 passed, 2 failed, 0 skipped**, exit 1. All nine focus tests and the existing `SchemaMigrationTest` passed. The two failures were outside FM02:
   - `TodoMigrationTest.populatedVersionFiveTasksMigrateWithNullTimeAndStableIndexes` expected the `.007` row before `.006` while the query orders IDs ascending.
   - `TodoBackupTest.schemaFourExactLegacyTaskHeaderImportsWithNullDueTime` failed in `RoomBackupService.prepare` with `Backup tables do not match its schema version`.

The filtered focus suite was rerun after corrections. The full connected suite confirms these two unrelated module regressions remain visible and prevent a green module-wide instrumentation gate.

## FM02 coverage and findings

- **QA02 repository portion — PASS:** one singleton start, exact 25 minutes credited across 20 running / 10 paused / 5 running minutes, repeated-command replay, changed-payload rejection, concurrent clients against the same on-disk database, and stale checkpoint rejection. Checkpointing keeps the user-action revision unchanged.
- **QA03 repository portion — PASS:** a persisted checkpoint survives database close/reopen; changed process ownership requests recovery; Continue credits the gap once, and End at checkpoint records only previously checkpointed time. Actual OS process death, force-stop and sleep are FM03 lifecycle checks and remain pending.
- **QA04 repository portion — PASS:** changed boot identity finalizes an interrupted session at its exact last checkpoint (59,999 ms) and removes runtime state. Clock-boundary and injected storage/emergency-write failures remain pending with FM03.
- **QA09 storage portion — PASS:** 52 terminal history rows with equal start timestamps paginate stably; a 50-row range query clips contributions exactly; zero-duration rows do not contribute; terminal delete cascades runs; a tombstone blocks Start replay after its receipt is evicted. Report UI, concurrent report invalidation and deletion confirmation remain FM05 work.
- **QA11 storage portion — PASS:** old-generation writes are rejected; deleted-session tombstones continue blocking replay after receipt eviction. Restore/reset orchestration and its stale-write races remain FM06 work.
- **Migration — PASS:** the exported v7 database upgraded to v8 with populated journal, photo-reference and task rows retained; the focus generation initialized; focus tables remained empty; Room validation, foreign-key checks and focus indexes passed. The existing v1→v8 migration test also passed.
- **Schema correction:** KSP warned that `focus_runtime(run_id, session_id)` lacked a child-key index for its composite foreign key. Added the matching Room entity index and v7→v8 DDL index, regenerated schema v8, and reran the migration/repository suite successfully.

## Limits and disposition

This result certifies the FM02 database/repository handoff only. It does not pass complete F16-QA02–04, QA09 or QA11: native lifecycle/guard behavior, injected write failures, FM05 report integration and FM06 restore/reset coverage remain pending their owning tasks. The two Todo failures should be fixed and rerun in their own task scope before claiming the full data-module regression gate is green.

**Suggested commit message:** `test(focus): verify Room durability, recovery and migrations`
