# Focus Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use superpowers:subagent-driven-development only when delegation is explicitly selected for execution. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Save focus sessions and expose Today/weekly totals with a guarded Luma focus screen and selected-app shortcuts; separately resolve device-wide enforcement.

**Architecture:** Pure Kotlin session/timing/statistics rules, standalone Room persistence and a lifecycle coordinator wired through existing explicit injection. A shell-level guard controls Luma navigation. Independently observed Focus cards preserve the existing event-summary cache and Review metrics.

**Tech Stack:** Existing Kotlin, coroutines/Flow, Room, Compose/Material 3, `java.time`, Android elapsed realtime; no new module or dependency proposed.

**Spec:** [F16 specification](../specs/2026-10-28-roadmap-16-focus-mode.md)

Date: 30 October 2026 · Status: **FM01 DOMAIN QA PASS; FM02 STORAGE/REPOSITORY QA PASS; FM03 SOURCE COMPLETE + SCOPED API36 QA PASS; FM04–FM05 IN PROGRESS; FM06–FM08 NOT STARTED; FULL MATRIX/INTEGRATED QA PENDING**

[Tasks](../../FOCUS_MODE_TASKS.md) · [QA scenarios](../../feature-roadmap/16-focus-mode-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Global constraints

- Preserve minimum API **28**, compile/target **35** and JDK **17**.
- Keep `core:domain` Android/Room-free.
- Complete all FM implementation/source handoffs first, then start remaining QA. Earlier pending QA does not block later implementation; source dependencies still apply. Test authoring, Gradle/sync/build/lint/schema export and device work occur only in QA. FM01 domain QA was already performed.
- No fixed next Room/archive version: observed baseline is Room 7/archive 6; allocate next unused versions at execution and serialize migrations/inventory changes.
- No new network, Usage access, accessibility, device-owner, DND, foreground-service or exact-alarm requirement for personal-phone v1. No automatic stronger restriction from restored content.
- One open session; 5-second foreground checkpoints; >2-second wall discontinuity triggers conservative interruption; never count pause time.
- Only terminal credited time feeds saved totals; current-week/today partial labels and whole previous matching weekdays follow `reviewPeriod`.
- EN/FR/FA, Persian RTL, light/dark, accessibility/insets and existing drafts/navigation state remain requirements.
- FM08 is required before claiming the original device-wide restriction request is complete. A personal-phone guard cannot certify this requirement.

## Review focus

1. A force-stop/reboot/wall jump during an external-app visit: recover same-boot time only with valid continuity; otherwise retain checkpoint and disclose omitted time (FM01–03; QA03–04).
2. Simultaneous actions in two windows and ambiguous writes: singleton/revision/command receipts prevent duplicate starts, terminal reopening and double credit (FM02–03; QA02/QA11).
3. Midnight/DST/zone changes and sessions spanning weeks: split run overlaps; count distinct sessions once per selected range (FM01/FM05; QA06–08).
4. A widget/new intent/restored route arriving while focus loads or runs: guard centrally without consuming drafts or executing queued mutations (FM03; QA01/QA12).
5. Export/replacement/reset with an unfinished session: preserve cancel behavior, exclude runtime, and cancel stale coordinator writes (FM06; QA10–11).

## File map and contracts

New paths are proposed; reconcile with the actual execution baseline and reuse equivalents. Source files belong to their owning FM task; tests listed later are created only in QA.

| Owner | New production paths | Responsibility |
| --- | --- | --- |
| FM01 | `core/domain/src/main/kotlin/com/luma/lifeos/domain/focus/FocusSession.kt`, `FocusTiming.kt`, `FocusStatistics.kt`, `FocusRepository.kt` in that directory | State/types/command/results, sample continuity, half-open overlap aggregation, repository contract from spec |
| FM02 | `core/data/src/main/kotlin/com/luma/lifeos/data/focus/FocusEntities.kt`, `FocusDao.kt`, `RoomFocusRepository.kt` | Session/run/runtime and command-receipt storage, transactions, query projections |
| FM03 | `app/src/main/java/com/luma/lifeos/focus/FocusClock.kt`, `FocusCoordinator.kt` | Android wall/elapsed/boot samples; reconciliation/checkpoint lifecycle ownership |
| FM03 | `app/src/main/java/com/luma/lifeos/ui/focus/FocusViewModel.kt`, `FocusRoute.kt`, `FocusScreen.kt`, `FocusNavigationGuard.kt` | Screen/action state, authoritative guard and retry/emergency exit |
| FM04 | `app/src/main/java/com/luma/lifeos/focus/FocusPreferences.kt`, `AllowedFocusApps.kt`; `ui/focus/FocusSettingsRoute.kt` | Device-local package selection, narrow app discovery and explicit launches, settings |
| FM05 | `app/src/main/java/com/luma/lifeos/ui/focus/TodayFocusCard.kt`, `WeeklyFocusCard.kt`, `FocusHistoryRoute.kt`, `FocusHistoryViewModel.kt` | Independent report states, paged sources/detail and terminal deletion |

Modify existing paths:

- FM02: `core/data/src/main/kotlin/com/luma/lifeos/data/db/LumaDatabase.kt`, `data/LumaStorage.kt` for entities/DAO/migrations/injection; exported schema added during QA only.
- FM03–05: `app/src/main/java/com/luma/lifeos/MainActivity.kt`, `LumaApplication.kt`, `ui/LifeOsApp.kt`, `ui/navigation/AppDestinations.kt`, `ui/more/MoreRoute.kt`, `ui/settings/SettingsRoute.kt`, `ui/today/TodayRoute.kt`, `TodayScreen.kt` in that directory, and `ui/review/WeeklyReviewRoute.kt`.
- FM04: `app/src/main/AndroidManifest.xml` only for MAIN/LAUNCHER package queries. Preserve foreground-service removal and current exported-component policy.
- FM03–05: `app/src/main/res/values/strings.xml`, `values-fr/strings.xml`, `values-fa/strings.xml`.
- FM06: `core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupArchive.kt`, `BackupValidation.kt`, `RoomBackupService.kt`, `BackupSql.kt` as needed; `app/src/main/java/com/luma/lifeos/ui/backup/BackupViewModel.kt`, `BackupSettingsCard.kt`, and `ui/privacy/AppDataReset.kt`; `docs/BACKUP_FORMAT.md`, `docs/PRIVACY.md`.

Retain existing `DailySummary`, `ReviewStatistics` and event-summary cache formats in v1. Pass a `FocusRepository` into Today/Review and observe separate metrics using identical selected date/zone/period bounds. Cross-card loading is independent; one card error must not hide other content. Source invalidation must include new tables, reset and restore generation.

## FM01 — Pure session, timing and aggregate rules

**Consumes:** Existing `TimeRange` and `reviewPeriod` bounds, injected `FocusTimeSample`. **Produces:** Exact `FocusRepository` methods and value types from the spec; pure transition/continuity/overlap/formatting inputs for FM02–05.

### Phase 1 — Implementation

- [x] Define immutable session/run snapshots, state, stable commands and typed results in `FocusSession.kt`; define range-contribution and page values. Pin legal transitions, terminal idempotency, expected revisions and zero-time retention.
- [x] Implement pure sample continuity and credited-time calculation in `FocusTiming.kt`: monotonic delta within a known boot, canonical projection, last-checkpoint fallback, 2-second threshold and paused-reboot behavior. Use checked arithmetic.
- [x] Implement `focusStatistics(sessions, range): FocusStatistics` in `FocusStatistics.kt` for terminal snapshots; overlapping runs clipped to range, distinct contributing IDs, interrupted contributors and integer-millisecond totals. Deduplicate identical run IDs; reject conflicting snapshots. Define comparison output for zero denominator and one-decimal percentages.
- [x] Record exact final signatures, invariants and source handoff; map to QA02–04/QA06–09/QA11. No tests or Gradle during this phase.

### FM01 source handoff — 29 October 2026

**Status:** SOURCE COMPLETE — QA PENDING. This closes only the FM01 implementation phase; it does not verify runtime behavior or complete F16.

- **Files:** `core/domain/src/main/kotlin/com/luma/lifeos/domain/focus/FocusSession.kt`, `FocusTiming.kt`, `FocusStatistics.kt`, and `FocusRepository.kt`.
- **Final repository contract:** `FocusRepository.currentGeneration(): UUID`; `observeOpen(): Flow<FocusSession?>`; `start(commandId, sessionId, sample, context)`; `apply(command)`; `reconcile(sample, context)`; `resolveRecovery(sessionId, expectedRevision, choice, sample, context)`; `checkpoint(sessionId, expectedRevision, sample, context)`; `observeStatistics(range)`; `history(range, offset, limit = 50)`; and `delete(sessionId, expectedRevision, context)`. Mutations return `FocusWriteResult` with Applied, AlreadyApplied, Conflict, Invalid, Unavailable, or RecoveryRequired outcomes.
- **Transition and snapshot invariants:** RUNNING has one open run; PAUSED has no open run; terminal states have no open run and retain a terminal reason. Revisions and durations are nonnegative; run identity/ordinal are stable; a closed canonical end equals its canonical start plus credited milliseconds. Stop is terminally idempotent; pause time is absent from runs; zero-duration runs remain valid.
- **Timing signatures:** `focusSampleContinuity(previous, current)` requires known matching boot IDs, nondecreasing elapsed realtime, and wall/monotonic drift no greater than 2,000 ms. `calculateFocusTiming(run, checkpoint, sample)` projects only checked monotonic deltas; discontinuities preserve the checkpoint and carry a typed interruption reason. `assessFocusRecovery(...)` leaves paused sessions untouched and requires an explicit choice after a valid new-process RUNNING recovery.
- **Statistics signatures:** `focusStatistics(sessions, range)` and `focusRangeContributionMillis(session, range)` include terminal sessions only, clip runs to the half-open `TimeRange`, deduplicate equal identities, reject conflicting snapshots, and use checked integer-millisecond totals. `compareFocusStatistics(current, previous)` reports the absolute delta; it omits percentage only for a zero previous baseline (or zero against zero), and rounds nonzero-baseline percentages to one decimal using `HALF_UP`.
- **Implementation choice:** equal positive periods have `NO_CHANGE` with `0.0%`; zero against zero has `NO_CHANGE` with no percentage, consistent with the comparison formula and zero-period rule.
- **QA mapping at source handoff:** QA02–04, QA06–08 and QA11 were NOT RUN at the handoff. The 30 October run adds host tests for the FM01 pure transitions, continuity/recovery, overlap and comparison rules; the complete cases remain blocked by their FM02/FM03/FM05/FM06 dependencies (see the [QA ledger](../../feature-roadmap/16-focus-mode-qa.md)).
- **Review:** A fresh read-only review found transition/recovery results that did not use the deduplicated run view used by statistics. The implementation now deduplicates before open-run selection and normalizes runs in changed, already-applied and conflict snapshots; QA should retain a regression case for repeated identical run rows.
- **Source identity:** base commit `7beffaf`; source patch SHA-256 `84de2cabaaaca3e105efb059ae229f0ac162fcf8a6f2b569e736f5a9d89eed50`. Tracked and new-source `git diff --check` passed. No Gradle, test, schema or device command is part of this implementation handoff.
- **Suggested commit message:** `feat(focus): add pure session timing and statistics contracts`.

### Phase 2 — QA

- [x] Author `core/domain/src/test/kotlin/com/luma/lifeos/domain/focus/FocusTimingTest.kt` and `FocusStatisticsTest.kt`; pin pause exclusion, recovery, midnight 10/20, zero and 90/60 comparison, duplicate-run normalization, and boundary cases.
- [x] Verify unknown boot, 1,999/2,000/2,001-ms drift, checked overflow, paused recovery, week boundaries and DST. Fresh `--rerun-tasks` host suite passed with 157 tests, 0 failures/errors/skips.
- [x] Record outcomes/defect/retest and the actual scope in [FM01 QA evidence](../../feature-roadmap/evidence/2026-10-30/F16/fm01-domain-qa.md). Integrated QA remains blocked until owning layers are implemented.

## FM02 — Durable Room storage and command transactions

**Consumes:** FM01 repository/types/rules. **Produces:** `RoomFocusRepository(database): FocusRepository`, exported via `LumaStorage.focus`; atomic singleton and read projections.

### Phase 1 — Implementation

- [x] Add focus entities/DAO and next unused migration in `LumaDatabase.kt`. Add FK/index/uniqueness constraints, runtime singleton with process ownership/recovery-required state, independent latest-100 command receipts, deleted-session tombstones and durable database generation metadata. Preserve old schemas/destructive-fallback prohibition.
- [x] Implement `currentGeneration/start/apply/reconcile/resolveRecovery/checkpoint/delete` with serialized Room transactions; action UUID payload reconciliation, stale generation/revision conflicts, monotonic checkpoint ordering, singleton across windows and no checkpoint revision churn. Receipts survive Stop/deletion; tombstones prevent evicted Start replay. Older action samples than committed checkpoints conflict and resample. Recovery-required state survives another process death.
- [x] Implement observed open state/statistics and stable 50-row history with overlap filtering and distinct counts; no all-history Compose reads. Keep lifecycle/coroutines out of database timing policy and preserve cancellation.
- [x] Add explicit injection, identify required archive inventory changes for FM06, and record source-only handoff with schema generation pending.

### FM02 source handoff — 30 October 2026

**Status:** SOURCE COMPLETE — FM02 QA PASS for Room/repository and migration scope. Integrated lifecycle, report and restore/reset branches remain with FM03/FM05/FM06.

- **Files:** Added `data/focus/FocusEntities.kt`, `FocusDao.kt`, and `RoomFocusRepository.kt`; modified `data/db/LumaDatabase.kt` and `data/LumaStorage.kt`.
- **Schema decision:** Current Room version was 7; FM02 adds version 8 via explicit `Migration(7, 8)`. Instants use epoch seconds plus nanoseconds so canonical and raw timestamps preserve sub-millisecond precision. Nullable unique slots enforce one nonterminal session globally and at most one open run per session. The runtime row is device-local and references its session/run. The database generation is independent of runtime; command receipts use a sequence and retain the newest 100 per local database; deletion tombstones block replay of an evicted Start in the same generation.
- **Repository behavior:** Start/actions, generation checks, result receipts, singleton/runtime changes and terminal deletion serialize in Room transactions. Checkpoints advance credited milliseconds and runtime anchors without changing the action revision. Recovery-required state is durable; same-boot older action/checkpoint samples conflict. Statistics query only overlapping terminal runs. History uses stable started-descending/id-descending pages capped at 50, with range contribution and full duration.
- **FM06 handoff:** The current archive is version 6. Update `BackupArchive.kt`'s versioned table inventory, `BackupSql.kt` replacement list, `RoomBackupService.kt` export/staged restore, `BackupValidation.kt`, and `docs/BACKUP_FORMAT.md`; portable history should include only terminal `focus_session` and `focus_run` rows. Exclude `focus_runtime`, `focus_command_receipt`, `focus_deleted_session`, and `focus_storage_state`. Replacement/reset must rotate `generation_id` and clear tombstones atomically. These archive changes remain out of FM02.
- **QA mapping:** FM02 storage portions of QA02–04, QA09 and QA11 passed. QA14 performance remains NOT RUN. Native lifecycle/fault checks, report integration and restore/reset races remain with FM03/FM05/FM06; details are in [FM02 QA evidence](../../feature-roadmap/evidence/2026-10-30/F16/fm02-room-qa.md).
- **Source review:** A read-only review found older same-boot samples could be mistaken for a genuine elapsed-clock regression and end a session. Same-boot samples whose wall and elapsed deltas align within the 2-second continuity tolerance now return Conflict for resampling; actual elapsed regression continues through the interruption path. Repository QA verified stale-sample rejection and changed-boot checkpoint interruption.
- **Schema correction during QA:** KSP identified a missing index for the `focus_runtime(run_id, session_id)` composite foreign key. Added the index to the Room entity and v7→v8 migration, regenerated the compiler-exported schema, and passed both focused migration tests.
- **Syntax correction:** The user-reported parser/exhaustiveness errors came from trailing commas after the expressions used as `when` subjects. Removed that malformed punctuation from the recovery assessment, recovery resolution, checkpoint, and action-transition `when` expressions. The source-handoff compile attempt did not reach the compiler because the original Gradle cache was read-only and the sandbox blocked socket setup. This was superseded during QA: compilation and the focused Android instrumentation run succeeded with Gradle 9.1.0 and the isolated API 36.1 AVD.
- **Nullability correction:** Kotlin cannot smart-cast `openRun` based on the separate `next.state` check. The RUNNING branch now binds a checked `nextOpenRun` before accessing its ID or persisting runtime.
- **Source identity:** base commit `bf95757de351a309a12a96d35494a5fcefa75ee1`; production-source patch SHA-256 `a9d5f63b479b8dae184f696b850d843778d037aab8470ddcb0009f6d2b0c1871` (FM02 production files only; documentation and pre-existing worktree changes excluded).
- **Suggested commit message:** `feat(focus): add revision-safe Room session storage`.

### Phase 2 — QA

- [x] Author `core/data/src/androidTest/kotlin/com/luma/lifeos/data/focus/FocusPersistenceTest.kt`, `FocusMigrationTest.kt`; extend migration coverage. Export schema v8 and validate populated v7→v8; the existing populated v1→v8 test also passed.
- [x] Run FM02 storage portions of QA02–04/QA09/QA11 for command receipts, concurrent clients, checkpoints/recovery, history, deletion and replay protection. Inspect FK/indexes and generated schema; add the missing composite runtime index.
- [x] Run the data host/instrumentation suites on the proven isolated API 36.1 target. Host: 29/29 pass; focused FM02 instrumentation: 9/9 pass. Full connected data run: 170/172 pass, with two non-FM02 Todo failures. See [FM02 QA evidence](../../feature-roadmap/evidence/2026-10-30/F16/fm02-room-qa.md).

## FM03 — Lifecycle coordinator, focus UI and central navigation guard

**Consumes:** `FocusRepository`, FM01 samples/results. **Produces:** `FocusCoordinator` application-scoped ownership, `FocusViewModel` state/actions and shell guard; Start/Pause/Resume/Stop flow.

### Phase 1 — Implementation

- [x] Implement `FocusClock.sample(): FocusTimeSample` using raw wall instant, elapsed realtime and a platform boot identifier; failed boot read is unknown, never a made-up stable boot. Inject clock/dispatcher for later QA. Use Android `Settings.Global.BOOT_COUNT` where supported/available; no privileged settings write.
- [x] Implement coordinator reconciliation before route restoration, a per-process UUID, visible 5-second checkpoints, background checkpoint, and command retry/reconciliation. New-process RUNNING recovery requires Continue elapsed / End at checkpoint; emergency markers finalize at checkpoint. Attempt emergency marker in independent preferences before releasing guard and warn if both persistence stores fail. App scope prevents duplicate activity timers; repository generation checks reject stale writes and open-state observation clears removed sessions. No service, wake lock, background poll or dependence on exact UI ticks.
- [x] Build ViewModel/route/screen for load/error/ready/action-busy/write-error/emergency-exit states. Use authoritative durable results to release guard. Preserve screen ID across rotation/language changes; no automatic replay of Stop or Start.
- [x] Wire guard in `LifeOsApp.kt` plus activity external entry handling. Guard primary/secondary/capture/new intent/widget/restored routes; retain at most one pending destination with no auto-mutation. Paused navigation provides Resume banner. Keep Pause/Stop accessible with keyboard/TalkBack and small windows.
- [x] Add More/Today entry wiring behind availability and localized copy. Preserve existing draft state. Source-review all launch/action paths and record handoff.

### FM03 source handoff — 30 October 2026

**Status:** SOURCE COMPLETE — QA PENDING. The user requested FM03 QA directly after this task's handoff; this does not close the remaining FM04–FM06 source work or integrated F16 QA.

- **Lifecycle:** `LumaApplication` owns one `FocusRuntime`/`FocusCoordinator` for the process. Reconcile and read the open session before the shell leaves its loading gate. The application scope runs one 1-second visible timer projection, persists checkpoints every 5 seconds, and checkpoints when the last Luma activity pauses. No service, wake lock or background polling is added.
- **Recovery/actions:** repository results remain authoritative; start and action retries retain their command IDs. New-process RUNNING state requires Continue elapsed or End at checkpoint. A matching emergency marker finalizes at the durable checkpoint as INTERRUPTED with `EMERGENCY_EXIT`; emergency confirmation attempts a synchronous preference marker before releasing the route guard and presents the marker-write result. If the user confirms while startup is still loading, the same-process guard releases after reconciliation while ordinary elapsed/checkpoint accounting continues.
- **Navigation/UI:** Today and More route into the Focus screen. A RUNNING session covers other shell routes and redirects to Focus; app tab routes, widget launches and reminder launches are intercepted into one saveable pending destination. It is offered as an explicit action after pause/stop and is never auto-replayed. Back offers Pause/Stop. The screen covers loading, load failure/retry, recovery, running, paused, terminal, write/checkpoint failure and emergency exit. Existing drafts stay in their underlying back stack. New Focus copy is present in EN/FR/FA.
- **Scope:** selected-app preferences/discovery are only available when packages were already selected; the settings UI, complete allowed-app handoff/settings workflow, reports, backup/restore/reset integration and device-wide restrictions remain FM04–FM08 work. The API36 emulator run below cannot establish API28 or the full locale/theme/window/TalkBack matrix.
- **Source checks:** `git diff --check` passed; Python `xml.etree.ElementTree` parsed all three affected string resource files. No Gradle, test, schema or device action occurred before this handoff.
- **Source identity:** base commit `4472f5f89d4586795b3759ac8454d021d44c5ff4`; production patch SHA-256 `8a3f2f1538663fc27facb1a7581eebbf273009dd79b2cb85a3a7c2860e2c1f9e` (21 FM03 production/resource paths; unrelated worktree changes excluded).
- **QA at handoff:** QA01–04 and QA12–13 NOT RUN. See the F16 QA evidence ledger for the scoped FM03 results and unexecuted matrix cases.

### Phase 2 — QA

- [x] Author/run `FocusCoordinatorTest.kt` and `FocusNavigationGuardTest.kt` (4 host tests) for injected clock samples, stable process identity, emergency terminal reason and one explicit queued destination. Dedicated ViewModel retry tests and write-fault injection remain open.
- [x] Run connected `FocusRuntimeRecoveryTest`, `FocusLifecycleTest` and `FocusScreenTest` on the isolated API36.1 QA flavor (6/6 pass): More/Today, widget new intent, system Back, recreation, Pause/Resume/Stop, explicit recovery after a changed process identity and emergency-marker recovery.
- [ ] Execute the remaining native matrix: actual OS process death/force-stop, reboot, screen-off/sleep, API28, injected storage failures, TalkBack/keyboard, EN/FR/FA × theme/window/font/orientation. API36 subset does not certify these cases.
- [x] Run full app host unit suite (293/293) and `lintFocusQaDebug` (exit 0; no warnings in changed FM03 production sources). Record exact variant, artifact identity, findings and limits in [FM03 QA evidence](../../feature-roadmap/evidence/2026-10-30/F16/fm03-qa.md).

**FM03 QA disposition (30 October 2026):** Scoped API36.1 QA PASS; platform matrix and full F16 integration remain pending. QA used the dedicated `focusQaDebug` package and did not clear or mutate the installed production/QA app data. See [execution evidence](../../feature-roadmap/evidence/2026-10-30/F16/fm03-qa.md).

## FM04 — Focus settings and selected-app handoff

**Consumes:** FM03 running/paused state/coordinator. **Produces:** observed `FocusPreferences.allowedPackages`, picker and `AllowedFocusApps.launch(packageName)` typed outcome.

### Phase 1 — Implementation

- [ ] Persist device-local package set with empty default; build Settings → Focus mode search/list with labels/icons and collision-disambiguating package names. Running settings are unavailable; paused edits apply at Resume.
- [ ] Query MAIN/LAUNCHER activities using matching narrow manifest queries, deduplicate packages and omit own/unlaunchable/other-profile apps. Keep stale packages visible as unavailable/removable without logging the list.
- [ ] On shortcut tap checkpoint and recheck current selection/launchability before explicit launch. An unsuccessful checkpoint/launch reports retry and retains session state. Catch expected platform failures; selected app handoff never implicitly pauses the timer.
- [ ] Add scope guidance explaining system controls and lack of device-wide enforcement. Review manifest/dependency/permission diff and record handoff.

### Phase 2 — QA

- [ ] Author `app/src/test/java/com/luma/lifeos/focus/AllowedFocusAppsTest.kt`, `FocusPreferencesTest.kt` and `app/src/androidTest/java/com/luma/lifeos/ui/focus/FocusAllowedAppsTest.kt`. Use disposable installed fixture apps, no private app inventory capture.
- [ ] Run QA05 for selection persistence, duplicate labels/activities, disabled/uninstalled target, launch exception, preference change from another window, work-profile boundary and return continuity.
- [ ] Run QA12/QA13 for settings and shortcut controls. Denied notification/Usage access must not disable ordinary timer/shortcuts. Verify no new broad permissions.

## FM05 — Today totals, weekly comparisons and history

**Consumes:** FM02 `observeStatistics(range)`, `history(range, offset, limit)`, `delete`; existing day/period/zone and FM03 open state. **Produces:** independent Focus card state, period history route and terminal deletion.

### Phase 1 — Implementation

- [ ] Build Today card with selected-day saved duration, distinct contributors, historical date behavior and separate in-progress line; Start always uses now. Loading/error/retry remains independent of other Today content.
- [ ] Build Weekly Focus card using exact current/previous `ReviewPeriod` ranges; sum clipped runs before rounding, show partial labels/date bars/delta, and suppress percent at previous zero. Keep prior metrics and ISO week behavior unchanged.
- [ ] Build 50-row history and detail with stable sort, range contribution/full duration labels, interrupted-gap indicator and confirmed terminal deletion. Rapid date/zone changes cancel old loads and never show old data under new headers.
- [ ] Wire drill-down/back preservation and deletion invalidation; separate focus state from daily cache/events. Add all EN/FR/FA state/accessibility copy and record handoff.

### Phase 2 — QA

- [ ] Author `app/src/test/java/com/luma/lifeos/ui/focus/FocusReportStateTest.kt`, `core/data/src/androidTest/kotlin/com/luma/lifeos/data/focus/FocusReportPersistenceTest.kt` and `app/src/androidTest/java/com/luma/lifeos/ui/focus/FocusReportsTest.kt`.
- [ ] Execute QA06–09 using independently calculated fixtures; verify 23/25-hour days, cross-week spans, first-use zero history, partial current day, zero previous, rounding, >50 ties and exact range sources.
- [ ] Execute QA12–14 for matrix/source isolation/query targets. Do not regress existing event counts, task/habit behavior or baseline Review performance; record raw performance data.

## FM06 — Portable terminal history, replacement and reset

**Consumes:** FM02 terminal storage, FM03 stop/cancel generations and existing staged archive flow. **Produces:** new archive adapter/inventory and safe terminal-only restore/reset behavior.

### Phase 1 — Implementation

- [ ] Allocate next archive schema independently from Room; extend table allowlists, row/type/invariant validation, export selection and restore mapping for terminal sessions/runs. Runtime/receipts/preferences never enter payloads. Do not silently reinterpret older archives.
- [ ] Add paused-session export disclosure/Stop-or-continue choice; active replacement requires stop/discard through existing replacement confirmation with cancel preservation. Legacy replacement clears focus history. Validation failure precedes destructive replacement.
- [ ] Restore invalidates all focus state, clears open/runtime/receipt/tombstone work and atomically rotates database generation; delayed old-generation writes are rejected rather than relying on cancellation alone. Reset likewise rotates generation, cancels coordinator/actions and clears all focus data/preferences. Preserve existing unrelated backup/reset policies and documented isolation requirements.
- [ ] Update backup/privacy documents and hand off exact source/migration/archive decisions. Serialize integration with F01/F05 and any other schema task.

### Phase 2 — QA

- [ ] Extend existing backup tests and author `core/data/src/androidTest/kotlin/com/luma/lifeos/data/focus/FocusBackupTest.kt`, `app/src/androidTest/java/com/luma/lifeos/ui/focus/FocusResetRestoreTest.kt`.
- [ ] Execute QA10–11: current terminal history round-trip; every supported legacy version; malformed/orphan/negative/overflow/open input; cancelled confirmation; IO failure; replacement/reset races. Inspect archives independently for excluded package/runtime content.
- [ ] Run affected migration/backup/restore/reset regressions against disposable databases and isolated application IDs. Record retained unrelated content/photo hashes and all unresolved native-picker checks.

## FM07 — Integrated source handoff and QA acceptance

### Phase 1 — Implementation

- [ ] Review final production/resource/documentation diff against F16-R01–R05, all entry paths, permissions, schema/archive inventory and initial defaults. Resolve execution-baseline differences in this plan.
- [ ] Run source-only checks: `git diff --check`, Markdown link/file checks and XML parsing of affected resources. Record SHA, dirty patch hash, touched files, decisions, requirement→case mapping and uncertainty. Mark SOURCE COMPLETE — QA PENDING only for actual source work.

### Phase 2 — QA

- [ ] Execute all applicable QA01–14 plus the shared locale/theme/window/API/keyboard/TalkBack matrix on an isolated package; run domain/affected host, build/lint, Room/app connected and minified release gates from the shared catalogue. Record exact commands/exit codes/skips, source/APK/fixture hashes and device state.
- [ ] Fix defects and rerun their cases plus affected regressions. Measure QA14 with 5 warmups + 30 repetitions on named hardware; report cold separately.
- [ ] Complete acceptance ledger with scope and remaining blockers. Never call device-wide blocking complete if only v1 navigation/shortcuts were validated. All required personal-phone cells must pass for a v1 claim; FM08 controls full original enforcement scope.

## FM08 — Device-wide enforcement decision and feasibility

### Phase 1 — Assessment handoff

- [ ] Confirm target deployment and precise blocked behavior with the execution request: personal phone vs explicitly provisioned managed device, Home/Recents/notifications/deep links, allowed-app links into other apps, emergencies and Pause/Stop escape.
- [ ] Compare supported Android personal-phone capabilities with DPC multi-app lock task mode using current official docs and actual target/OS requirements. Document permission/onboarding/distribution/OEM/recovery constraints and the behavior each option cannot enforce.
- [ ] Write a concrete recommendation and revised spec/plan if stronger enforcement is selected. If deferred, retain the unmet requirement explicitly; do not install a controller, grant accessibility, provision/reset a device or add blocking code from this documentation task.

### Phase 2 — Assessment QA

- [ ] Execute QA15 documentation/API capability checks. If a separate authorized disposable-device spike is requested, label it throwaway and record exactly what was proved.
- [ ] Mark only the assessment complete when its claims are verified. A no-change recommendation never establishes that strict blocking was shipped. A chosen stronger branch needs implementation and new QA before feature acceptance.

## Current handoff

FM01 production source is handed off and its pure-domain QA subset passes. FM02 Room/repository QA passes for the storage scope, with schema/migration evidence recorded above; the full data module still has two non-FM02 Todo failures. FM03 lifecycle ownership, guarded UI and coordinator integration are source-complete, and its focused API36.1 host/instrumentation/lint subset passes; native lifecycle, fault-injection and accessibility/platform matrix checks remain open. FM04–FM08 source work and integrated QA remain pending. No complete F16 UI or archive integration is claimed.
