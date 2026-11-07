# Focus delivery — separate QA plan

Date: 2026-11-07 · Status: PLANNED — NOT RUN

[Tasks](../FOCUS_DELIVERY_TASKS_2026-11-07.md) · [Spec](../superpowers/specs/2026-11-07-focus-delivery-design.md) · [Source plan](../superpowers/plans/2026-11-07-focus-delivery.md) · [Emulator plan](focus-delivery-emulator-2026-11-07.md)

## Phase boundary

This plan is separate from every implementation task. **Do not author tests/fixtures or run any command below until FD01–FD12 are SOURCE COMPLETE — UNVERIFIED.** QA owns test authoring, stale fixture repair, builds/schema generation, host/static checks and defect fixes/retests.

Emulator/device interactions, Room instrumentation execution, APK installation and native/manual journeys belong exclusively to the linked emulator plan. Q may assemble instrumentation artifacts but must not execute connected tests. Q06 final acceptance occurs after that plan returns evidence.

Existing [FX final QA](focus-product-extensions-qa.md), [F16 QA](16-focus-mode-qa.md) and FI acceptance remain carried requirements; this plan adds case ownership and actual current variant/version discovery. Their old version/command assumptions are superseded by the new spec and artifact inspection.

Result states: NOT RUN / PASS / FAIL / BLOCKED / N/A with case-specific reason. Missing tooling is BLOCKED, never PASS or N/A. No historic target pass transfers to this candidate.

## Q01 — Candidate and baseline record

**Entry:** all FD source handoffs recorded. **Output:** reproducible candidate/evidence manifest.

- [ ] Record source revision, dirty-patch digest, changed files, plan/spec versions, JDK/SDK/Gradle versions and dependency resolution mode.
- [ ] Confirm actual Room/archive versions (planned 15/12), migration registration and portable inventory. Never assume an old plan's version.
- [ ] Record prior known failures: two Focus migration failures; broader data-suite backup Long/String casts; stale standalone archive runner; app instrumentation API mismatches in app-lock/Focus/widget sources.
- [ ] Separate stale expected-schema assertions from potential data loss. The missing seeded journal row remains unresolved until independently traced; do not fix it by deleting the preservation assertion.
- [ ] List actual Gradle variant tasks. Current source has production flavor; :app:testDebugUnitTest is not assumed valid.
- [ ] Create evidence directory docs/feature-roadmap/evidence/<actual-run-date>/focus-delivery/ with candidate manifest, commands/exit codes, logs, test XML, independent expected values and later screenshot index.

No actual clock or device changes in this phase.

## Q02 — Author and run pure/state tests

Tests are written now, not in FD tasks. Reuse existing runners/JUnit setup; no new library is required. Every new test must assert externally meaningful behavior rather than mirror helper implementation.

Paths use D = core/domain/src/test/kotlin/com/luma/lifeos/domain/ and A = app/src/test/java/com/luma/lifeos/.

| Test file to create/extend | Required assertions |
| --- | --- |
| D/focus/FocusMetadataTest.kt | 120/121 code-point names; current 50/51 code-unit tag limit including surrogate pairs; linked input cannot normalize to standalone; edited/unlinked origins persist |
| D/focus/FocusTaskRulesTest.kt | Missing/completed task rejected at Start, overlong title requires edit, terminal task completion eligibility excludes interruption |
| D/focus/FocusInvitationRulesTest.kt | 1–7 days, valid minute time, disabled state, DST gap/overlap, inclusive 60-minute deadline, 61-minute skip, one snooze and no catch-up |
| D/focus/FocusTimingTest.kt and FocusInsightsTest.kt | 25-minute capped target across pause/late callback, canonical split at midnight, stopped-versus-target distinction, range-clipped totals |
| D/lock/ProtectedRecordClassifierTest.kt | Task origin requires both sections after edit/unlink/task deletion; standalone remains Focus-only |
| A/ui/focus/FocusSetupStateTest.kt | Untouched blank neutral, blur-after-edit/attempt shows error, invalid prefill visible, IME cannot start invalid, reset/success/relock clears state, unresolved Start blocks replacement |
| A/ui/focus/FocusTaskPickerStateTest.kt | Literal query, 50/51 paging, stable tie order, new search resets offset, late query/grant/generation result rejected |
| A/ui/focus/FocusTaskCompletionStateTest.kt | UUID revision pair retained on ambiguous retry, conflict reload requires new explicit action, missing/already-completed safe; no Stop retry |
| A/ui/focus/FocusCompletionStateTest.kt | Durable terminal required for saved label; correct early-stop/target/interrupted copy selection; break/custom/reflection cancellation never changes credit |
| A/ui/focus/FocusRecentWorkStateTest.kt | Terminal positive-only eligibility, deterministic order, dirty-draft confirmation, deleted source, loading clears stale title, open session wins, private context cleared |
| A/ui/focus/FocusReportStateTest.kt | Context range follows report including partial week; no global-latest substitution, mixed protected aggregate suppressed, optional context error leaves authorized totals; requested-session detail uses direct ID beyond first history page and handles deleted/locked/stale-generation records |
| A/focus/FocusCoordinatorTest.kt and FocusNavigationGuardTest.kt | Immutable command retries, pending destination/open-session precedence, runtime cancellation and safe controls |
| A/reminders/FocusInvitationStateTest.kt | Permission/channel blocked does not block Focus, token/action replay rejected, task/habit/timer identities unchanged |

- [ ] Author independent fixtures in Q03 before production-path assertions.
- [ ] Run focused classes while repairing defects; retain failing evidence and explain each fix.
- [ ] Run full relevant host suites after focused checks pass. Discover and report actual test counts; uncompiled/undiscovered tests do not pass.
- [ ] Update scripts/run_focus_metadata_archive_tests.py to include its currently missing adapter dependencies if retaining the runner. A Gradle substitute must be explicitly labeled by coverage, not passed off as the stale runner passing.

## Q03 — Persistence/archive test authoring and independent fixtures

Author/repair device tests now; execution is E phase. Host archive/SQLite checks run in Q. No generated schema is handwritten.

Device tests to create/extend:

- core/data/src/androidTest/kotlin/com/luma/lifeos/data/focus/FocusMigrationTest.kt
- .../focus/FocusProductPersistenceTest.kt
- .../focus/FocusRecentWorkPersistenceTest.kt
- .../focus/FocusInvitationPersistenceTest.kt
- .../focus/FocusReportPersistenceTest.kt
- .../backup/FocusProductBackupTest.kt
- .../backup/BackupPersistenceTest.kt and TodoBackupTest.kt

Host tests: core/data/src/test/kotlin/com/luma/lifeos/data/backup/FocusMetadataArchiveAdapterTest.kt and BackupValidationTest.kt; core/data/src/test/kotlin/com/luma/lifeos/data/focus/FocusHistoryFilterTest.kt.

Required independent fixtures:

| Fixture | Expected values / failure assertions |
| --- | --- |
| Timer T | Target 25 min; run 10, pause 7, run 15; credit 1,500,000 ms, wall span 1,920,000 ms. Callback 3 min late still saves 1,500,000 once |
| Midnight C | 23:50–00:20 gives 1,800,000 ms: prior day 600,000 and current day 1,200,000. Add 25 min today: total 2,700,000, count 2, average 1,350,000. Break adds zero |
| Week W | Freeze Nov4 2026 09:00 Asia/Tehran; Nov2/3/4 = 30/0/30 min, 2 active days, highest Nov2; prior Oct26–28 = 10/10/10 min; +30 min/+100%. Do not compare full prior week |
| Task K | Open, completed, deleted, renamed, 150-code-point title, and unsaved editor; task-origin synthetic private markers; unlink/deletion must preserve origin/runs and invalidate totals |
| Recent R | Positive terminal, zero-credit, interrupted, equal-ended timestamps with ordered UUIDs, recent global item outside W, cross-midnight overlap item, accessible standalone plus protected candidate |
| Goal G | Configure Nov4 Tehran; effective Nov9, edited Nov5 replaces pending version, next change active week takes effect Nov16; disable/re-enable hides until next boundary; travel preserves zone |
| Invitation I | Nov weekday slot; 59/60/61 min late. America/New_York Mar8 2026 02:30 → 03:00; Nov1 01:30 → earlier 05:30 UTC; one local-date claim, snooze +10 min with own inclusive 60-min deadline |
| Archive B | Schemas 1–11 retain actual layouts; current 12 round-trips task/provenance, targets, reflections and goals; dangling FK, invalid origin, unknown enum, over-target and malformed text reject without changing live data |
| Scale S | 50,000 terminal sessions, 51+ equal-order matching task/history rows; mixed origins/tags; unrelated mood/journal/habit/photo fixtures with independent hashes |

Required transaction assertions: simultaneous Start singleton, changed-payload replay rejection, target/Stop/expiry once-only result, tag merge/unlink/task-delete concurrent with active revision, claim/snooze/disable replay, generation replacement. Assert actual run bytes and unrelated records before/after, not only row counts.

Repair stale migration table inventory to the full new schema only after proving the added tables are intentional. Independently track where the seeded journal row is lost in the migration chain; preserve evidence if it is a fixture problem.

Add direct RoomFocusRepository.observeInsights invalidation coverage (save/delete/tag edit) plus recent-query invalidation and mixed-access projection checks. Fake flow tests do not substitute for Room observation.

## Q04 — Build, schema generation and artifacts

Run commands sequentially through Android Studio shell tooling; no devices yet. Resolve task names first:

```sh
./gradlew :app:tasks --all
./gradlew :core:domain:test :app:testProductionDebugUnitTest :core:data:testDebugUnitTest :integration:usage:testDebugUnitTest :integration:health:testDebugUnitTest
./gradlew :app:lintProductionDebug :core:data:lintDebug :integration:usage:lintDebug :integration:health:lintDebug
./gradlew :app:assembleProductionDebug :app:assembleProductionDebugAndroidTest :core:data:assembleDebugAndroidTest
```

Use --offline only when dependencies are cached and record the choice. Missing dependencies/tooling are explicit blockers; do not silently change versions or download tools. Correct task names from the actual task inventory if flavor structure changes.

- [ ] Inspect generated Room 15 schema/defaults/FKs/indexes versus FD01 source; retain existing exported schemas and generate missing intermediate versions through the appropriate historical source/build if required, never invent JSON.
- [ ] Repair app-lock/Focus/widget instrumentation API drift so device tests actually compile; record these as validation infrastructure fixes and rerun affected host checks.
- [ ] Inspect manifest/application ID and test target in built artifacts; expected debug ID is com.luma.lifeos.qa, not proof until inspected.
- [ ] Record APK/test APK SHA-256, variant, source candidate and app/test target pair.
- [ ] Build a minified validation artifact on an isolated validation checkout/config using a validation-only qaMinified build type initialized from release, applicationIdSuffix = ".qa.minified", and debug signing; assembleProductionQaMinified. Do not change or deploy the normal productionRelease package. Record the temporary config diff and map artifact evidence to it.
- [ ] Treat compilation, Room adapter generation, lint warnings, R8 warnings and runtime acceptance as distinct results.

## Q05 — Static checks and validation readiness

- [ ] Verify EN/FR/FA resource key parity, placeholders and formatting; inspect source accessibility semantics, 48 dp targets and IME/inset composition. Runtime presentation is E10.
- [ ] Validate local Markdown links and source/acceptance crosswalk; run git diff --check over the final changes.
- [ ] Execute existing applicable domain/setup/history/archive/SQL runners after fixing current source discovery; record each exit/count individually.
- [ ] Inspect query plans for history/task picker/recent queries and verify bounded reads and relevant indexes. On-device timings belong to E11.
- [ ] Ensure no per-second persistence/background polling/service/wake-lock was introduced; verify permission/dependency diff.
- [ ] Publish E-phase manifest and known blockers. No connected Gradle task, emulator or installation is run here.

## Q06 — Final reconciliation, after emulator evidence

Entry to E requires Q01–Q05 PASS for build/host prerequisites. A genuinely device-only test may remain scheduled for E; any failing build/schema prerequisite blocks E rather than bypassing it.

After E returns:

- [ ] Link every FD-A01–09 to Q/E evidence on the same final candidate. Re-run Q checks affected by E-phase fixes and rerun the affected E cases plus E12 integrated journey.
- [ ] Triage every mandatory failure, skip and baseline discrepancy. A full suite with unexplained failures is not accepted because targeted tests passed.
- [ ] Report API/locale/lifecycle matrix coverage exactly; emulator-only results do not certify physical-device performance or vendor background behavior.
- [ ] Keep strict device-wide enforcement DEFERRED/UNMET outside scope; never mark it passed.
- [ ] Close source delivery independently of product research. The proposed 6–8 participant study remains NOT RUN unless conducted with actual consent/participants.
- [ ] Mark technical acceptance only when all required Q/E cases and applicable carried requirements pass. Otherwise list blocking cells and retain PARTIAL/BLOCKED status.

## Carried acceptance crosswalk

| Existing case | Owner in this package |
| --- | --- |
| FX-Q01 Repeat | Q02/Q03 + E05/E12 |
| FX-Q02 Tasks | Q02/Q03 + E02/E03/E08 |
| FX-Q03 timed credit | Q02/Q03 + E04 |
| FX-Q04 recovery/break | Q02 + E04/E07 |
| FX-Q05 reflection | Q02/Q03 + E03/E05 |
| FX-Q06 goals/Review | Q02/Q03 + E11 |
| FX-Q07 invitations | Q02/Q03 + E06/E07 |
| FX-Q08 privacy/access | Q02/Q03 + E08/E10 |
| FX-Q09 persistence/replacement | Q03/Q04 + E02/E09 |
| FX-Q10 correction/tag merge | Q02/Q03 + E03/E08/E09 |
| FX-Q11 scale/errors | Q05 + E11 |
| FX-Q12 integrated journey | E12 + Q06 |
| FI-A01–07 | Q02–05 + E01/E02/E05/E08/E09/E10; direct Room insights invalidation required |
| F16 QA01–14 | Existing scenario steps carried into E01–12; map each actual matrix cell in evidence |
| F16 QA15 enforcement | DEFERRED/UNMET, outside accepted personal-phone scope |

All rows currently NOT RUN for the new candidate. Generating this plan executes none of them.
