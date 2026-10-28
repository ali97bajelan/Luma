# Focus Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use superpowers:subagent-driven-development only when delegation is explicitly selected for execution. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Save focus sessions and expose Today/weekly totals with a guarded Luma focus screen and selected-app shortcuts; separately resolve device-wide enforcement.

**Architecture:** Pure Kotlin session/timing/statistics rules, standalone Room persistence and a lifecycle coordinator wired through existing explicit injection. A shell-level guard controls Luma navigation. Independently observed Focus cards preserve the existing event-summary cache and Review metrics.

**Tech Stack:** Existing Kotlin, coroutines/Flow, Room, Compose/Material 3, `java.time`, Android elapsed realtime; no new module or dependency proposed.

**Spec:** [F16 specification](../specs/2026-10-28-roadmap-16-focus-mode.md)

Date: 28 October 2026 · Status: **DRAFT — NOT IMPLEMENTED**

[Tasks](../../FOCUS_MODE_TASKS.md) · [QA scenarios](../../feature-roadmap/16-focus-mode-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Global constraints

- Preserve minimum API **28**, compile/target **35** and JDK **17**.
- Keep `core:domain` Android/Room-free.
- Exactly two phases per task: source implementation/handoff, then QA. Test authoring, Gradle/sync/build/lint/schema export and device work occur only in QA. This roadmap workflow overrides generic interleaved TDD steps.
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

- [ ] Define immutable session/run snapshots, state, stable commands and typed results in `FocusSession.kt`; define positive range-contribution and page values. Pin legal transitions, terminal idempotency, expected revisions and zero-time retention.
- [ ] Implement pure sample continuity and credited-time calculation in `FocusTiming.kt`: monotonic delta within a known boot, canonical projection, last-checkpoint fallback, 2-second threshold and paused-reboot behavior. Use checked arithmetic.
- [ ] Implement `focusStatistics(sessions, range): FocusStatistics` in `FocusStatistics.kt` for terminal snapshots; overlapping runs clipped to range, distinct contributing IDs, interrupted contributors and integer-millisecond totals. Deduplicate identical run IDs; reject conflicting snapshots. Define comparison output for zero denominator and one-decimal percentages.
- [ ] Record exact final signatures, invariants and source handoff; map to QA02–04/QA06–09/QA11. No tests or Gradle during this phase.

### Phase 2 — QA

- [ ] Author `core/domain/src/test/kotlin/com/luma/lifeos/domain/focus/FocusTimingTest.kt`, `FocusTransitionsTest.kt`, `FocusStatisticsTest.kt`; pin QA fixture values including 20 active + 10 paused + 5 active = 25 minutes, midnight 10/20, zero and 90/60 comparison.
- [ ] Verify unknown boot, 1,999/2,000/2,001-ms drift, overflow/conflicting snapshots, paused reboot, full/partial weeks and DST. Run `bash gradlew -PdomainOnly=true :core:domain:test`; exit 0 with required assertions executed is the automated gate.
- [ ] Record outcomes/defects/retests and actual scope before FM02.

## FM02 — Durable Room storage and command transactions

**Consumes:** FM01 repository/types/rules. **Produces:** `RoomFocusRepository(database): FocusRepository`, exported via `LumaStorage.focus`; atomic singleton and read projections.

### Phase 1 — Implementation

- [ ] Add focus entities/DAO and next unused migration in `LumaDatabase.kt`. Add FK/index/uniqueness constraints, runtime singleton with process ownership/recovery-required state, independent latest-100 command receipts, deleted-session tombstones and durable database generation metadata. Preserve old schemas/destructive-fallback prohibition.
- [ ] Implement `currentGeneration/start/apply/reconcile/resolveRecovery/checkpoint/delete` with serialized Room transactions; action UUID payload reconciliation, stale generation/revision conflicts, monotonic checkpoint ordering, singleton across windows and no checkpoint revision churn. Receipts survive Stop/deletion; tombstones prevent evicted Start replay. Older action samples than committed checkpoints conflict and resample. Recovery-required state survives another process death.
- [ ] Implement observed open state/statistics and stable 50-row history with overlap filtering and distinct counts; no all-history Compose reads. Keep lifecycle/coroutines out of database timing policy and preserve cancellation.
- [ ] Add explicit injection, identify required archive inventory changes for FM06, and record source-only handoff with schema generation pending.

### Phase 2 — QA

- [ ] Author `core/data/src/androidTest/kotlin/com/luma/lifeos/data/focus/FocusPersistenceTest.kt`, `FocusMigrationTest.kt`; extend existing full migration suite where appropriate. Export actual Room schema and validate every supported migration path against populated old databases.
- [ ] Run QA02–04/QA09/QA11 for crash boundaries, duplicate/evicted receipts, concurrent windows and retained unrelated record/photo hashes. Inspect FK/indices and generated schema; fix source/schema mismatches before acceptance.
- [ ] Run affected data host/instrumentation suites using the shared command catalogue on a proven isolated target. Record exact required tests executed; successful KSP/assembly alone is insufficient.

## FM03 — Lifecycle coordinator, focus UI and central navigation guard

**Consumes:** `FocusRepository`, FM01 samples/results. **Produces:** `FocusCoordinator` application-scoped ownership, `FocusViewModel` state/actions and shell guard; Start/Pause/Resume/Stop flow.

### Phase 1 — Implementation

- [ ] Implement `FocusClock.sample(): FocusTimeSample` using raw wall instant, elapsed realtime and a platform boot identifier; failed boot read is unknown, never a made-up stable boot. Inject clock/dispatcher for later QA. Use Android `Settings.Global.BOOT_COUNT` where supported/available; no privileged settings write.
- [ ] Implement coordinator reconciliation before route restoration, a per-process UUID, visible 5-second checkpoints, background checkpoint, and command retry/reconciliation. New-process RUNNING recovery requires Continue elapsed / End at checkpoint; emergency markers finalize at checkpoint. Attempt emergency marker in independent preferences before releasing guard and warn if both persistence stores fail. App scope prevents duplicate activity timers; stopped/reset generations cancel stale work. No service, wake lock, background poll or dependence on exact UI ticks.
- [ ] Build ViewModel/route/screen for load/error/ready/action-busy/write-error/emergency-exit states. Use authoritative durable results to release guard. Preserve screen ID across rotation/language changes; no automatic replay of Stop or Start.
- [ ] Wire guard in `LifeOsApp.kt` plus activity external entry handling. Guard primary/secondary/capture/new intent/widget/restored routes; retain at most one pending destination with no auto-mutation. Paused navigation provides Resume banner. Keep Pause/Stop accessible with keyboard/TalkBack and small windows.
- [ ] Add More/Today entry wiring behind availability and localized copy. Preserve existing draft state. Source-review all launch/action paths and record handoff.

### Phase 2 — QA

- [ ] Author `app/src/test/java/com/luma/lifeos/focus/FocusCoordinatorTest.kt`, `ui/focus/FocusViewModelTest.kt`; verify exact sample/retry/ownership behavior against QA01–04/QA11–12.
- [ ] Author `app/src/androidTest/java/com/luma/lifeos/ui/focus/FocusNavigationTest.kt`, `FocusLifecycleTest.kt`; execute system Back, new intents/widgets, recreation, process death/force-stop, reboot, screen off and paused entry on isolated API28/API36 devices.
- [ ] Exercise injected storage failures and emergency exit; record what remained durable. Run affected host/build/lint/device gates and QA12/QA13 matrix before claiming the guard works.

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

Documentation only. No FM production work, test authoring, Gradle, schema export, emulator/device operation, app installation or release has occurred for F16. Every implementation checkbox remains open and every QA scenario begins NOT RUN.
