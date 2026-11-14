# TI-QA01 — Final task importance and subtasks QA

Date: 14 November 2026. Status: **NOT RUN**. This document defines **one QA task**, including emulator testing, after all feature implementation is source complete. Cases 01–11 are coverage scenarios inside this task, not separate tasks or intermediate gates.

[Specification](superpowers/specs/2026-11-14-task-importance-subtasks-design.md) · [Implementation plan](superpowers/plans/2026-11-14-task-importance-subtasks.md) · [Research/backlog](TASKS_IMPROVEMENT_BACKLOG_2026-11-14.md)

## Entry gate

- [ ] I01–I08 each have SOURCE COMPLETE — QA PENDING handoffs and the final feature handoff records source SHA/dirty patch identity, changed paths, schema/archive allocations, static-review findings and known uncertainty.
- [ ] No predecessor QA PASS is needed to complete implementation. Start this task only once every implementation handoff exists.
- [ ] Read the complete spec, plan, current AGENTS.md, [shared workflow](feature-roadmap/WORKFLOW.md) and actual implementation before deriving test assertions.
- [ ] Author/update tests and synthetic fixtures here; generate/inspect Room schemas, run Gradle/build/lint, control emulators and correct defects here. No test result or evidence directory is pre-created by this planning pass.

## Isolation and evidence

Use Android Studio studio_ tools for files, commands and device integration. Load the android-cli skill before CLI/emulator work. Discover actual supported test/build commands and installed SDK/device tools; command examples below are templates, not results.

Use a disposable dedicated AVD and a verified isolated application/test target. Debug is not proof of an isolated package ID. Inspect applicationId, testApplicationId, test runner and uninstall/clear targets before any installation/connected test. If necessary, QA creates a dedicated test flavor/build configuration, then records the exact variant/runner. Never reinstall, clear, reset or restore over the user’s personal Luma package/data or use their saved task titles in evidence.

Record actual timestamp, source SHA and dirty patch hash, JDK/Gradle/SDK versions, command/exit code, variant/package/version/APK SHA, AVD/API, locale, theme, timezone, calendar preference, window dp/orientation, font scale, input method, permission/app-lock state and fixture seed/hash. Use `docs/validation/evidence/<actual-run-date>/task-importance-subtasks/` and create the acceptance record during execution at `docs/validation/<actual-run-date>-task-importance-subtasks-qa.md`.

Outcomes: NOT RUN, PASS, FAIL, BLOCKED, N/A. BLOCKED names the exact prerequisite and next step; N/A needs a source-backed scope rationale. Assembly success does not prove instrumentation ran; semantics tests do not prove TalkBack speech. Every case/matrix cell needs its own outcome and evidence links. No fabricated screenshots, pass counts or waivers.

## Fixtures and independent expected results

Freeze test clocks and use deterministic UUIDs. Keep expected data separate from production comparators/predicates.

- One legacy standalone task for every existing status/date/time combination and populated supported old Room/archive baselines, including Focus/reminder links.
- Four roots, one per importance, plus explicit None and Low children; duplicates in titles and dates to force UUID tie-breaks.
- An undated None parent with a High child due today, a Low child overdue, a completed child, a child due after the parent, and an undated child.
- A completed root with completed children; reopen scenarios; a root with no children; 51 and 101 children spanning page boundaries.
- Two competing writers, stale confirmations, retained command UUIDs, crash-after-commit hooks, restore while editors/Focus/widgets are open, expired/missing journal handles and old widget payloads.
- Valid family archives with child rows before parent; malformed codes, duplicate sibling ranks, missing/self/deep/cyclic parents and completed-root/open-child graphs.
- A synthetic 10,000-record dataset with both standalone tasks and families, including a single 1,000-child family; count actual records independently. Do not create 10,000 rows through UI tapping.

## Case matrix

All cases initially **NOT RUN**. Requirements refer to TI-R01–TI-R12 in the spec.

| Case | Scope | Requirements | Initial result |
| --- | --- | --- | --- |
| 01 | Importance model and populated migrations | R01/R10 | NOT RUN |
| 02 | Editor importance, validation and drafts | R01/R03/R11 | NOT RUN |
| 03 | Hierarchy/order invariants and CRUD | R02/R10 | NOT RUN |
| 04 | Child/parent editor journeys and progress | R02/R03/R04/R06/R11 | NOT RUN |
| 05 | Query modes, ordering, filters and paging | R04/R05 | NOT RUN |
| 06 | Today/Calendar/reminder/widget/Focus journeys | R04/R05/R09/R11 | NOT RUN |
| 07 | Atomic completion, Undo, deletion and concurrency | R06/R07/R08/R09 | NOT RUN |
| 08 | Failure/retry/crash/restore/app-lock recovery | R03/R07/R08/R09/R10 | NOT RUN |
| 09 | Locale/theme/accessibility/calendar/input matrix | R01/R03/R04/R05/R09/R11 | NOT RUN |
| 10 | Archive/restore/reset and revision invalidation | R02/R07/R08/R09/R10 | NOT RUN |
| 11 | Large dataset and bounded rendering/query behavior | R04/R05/R09/R12 | NOT RUN |

### Case 01 — Model and migration

- Author domain assertions for stable codes 0/1/2/3, None distinct from Low, invalid-code rejection, no importance→date/urgency mutation, existing title/note/date/time limits and model parent/position shape.
- Generate the new Room schema through the repository build, inspect it against migration SQL and retain every old exported schema unchanged.
- Run populated upgrades from all supported Room entry versions to the candidate. Assert exact original IDs/content/dates/timestamps/revisions, None/root defaults, valid new family guards and retained Focus/reminder relationships. Reopen the upgraded DB and inspect FK/integrity checks. No destructive fallback.
- Expected: exact retained values plus intended new defaults; migration tests execute successfully, not just schema compilation.

### Case 02 — Importance editor and drafts

- Emulator journey: create a title-only task, then create/edit each importance choice and clear back to None. Kill/recreate the editor with unsaved changes and repeat after injected failed save/conflict/reload.
- Assert selected importance, title/note/date/time, dirty state and pending payload restore correctly; Save is reachable above IME; old drafts without importance default to None.
- Verify quick capture remains undated by default and contextual Today Add retains its existing date behavior. No importance selection adds a deadline/reminder automatically.
- Expected: persisted field equality after restart, preserved drafts on failure and unchanged navigation origin/test tags.

### Case 03 — Relationship and order

- Persistence assertions reject missing parent, completed parent, child-as-parent, self/cycle/deep relationships and duplicate sibling position, without partial writes.
- Create 101 children; check initial explicit order, Move up/down across loaded-page edges, edge no-ops, stale moves and restart. Complete one, move another and verify completed siblings still participate in order.
- Run concurrent child-create vs root-complete, child-reopen vs root-complete and delete vs reorder. Inspect invariants independently from repository models.
- Expected: only roots have children; stable IDs/positions; completed root never has open child; family token rotates for actual writes and not no-ops; parent content revision need not change for child-only progress.

### Case 04 — Parent/child journeys and progress

- Emulator journey: unsaved parent → Add subtask → Cancel, invalid Save and continue, failed Save/retry, successful Save and continue; assert exactly one root and correct child parent ID.
- Edit parent fields then enter a child; follow child breadcrumb back while dirty; cancel/discard/save according to existing editor guard without losing unrelated drafts.
- Create differently dated/prioritized children. Verify no inheritance/cascade, true full-family progress beyond loaded pages, expand/collapse restoration and accessible reorder actions.
- Complete last child: parent remains open and explicit completion action is offered. Empty parent has no 0/0 badge. Completed parent disallows child creation until explicit reopen.
- Expected: exact IDs, callback origins, child fields, all-status child list and full progress, with no duplicate root creation.

### Case 05 — Membership/sorting/paging

- Author independently ordered expected lists for All root mode, each explicit importance filter (especially None), Open/Completed and Date/Importance sort.
- High child under undated None parent must appear in High matching view even if parent does not match. Parent context is not an extra result. Root Date mode still contains the parent once and exposes child by expansion.
- Verify timed/date-only/undated ties, duplicate titles, UUID ties, insertion/deletion/completion at row 50/51/100/101, live refresh, retained prefixes/scroll, no-results reset and off-page failed-action recovery.
- Expected: SQL filter/sort occurs before limit; totals match entire query, children appear once, sibling order does not change under root importance sort and completed default ordering remains intact.

### Case 06 — Cross-surface emulator journeys

- Today: child date independently determines eligibility; High/undated remains excluded; current-date only; all due/overdue rows remain reachable via lazy paging beyond 50 with accurate total. No three-row cap returns.
- Calendar: independent parent/child dates and calendar markers/counts; open exact child editor, complete/reopen through shared policy, return to same viewed date; date-only/time labels honor current calendar preferences.
- Native launcher: place actual task widget, scroll collection and compare count/order/context. Complete/Undo safe leaf, retry failed command, tap parent with open children and verify app confirmation after unlock. Replay pre-feature and stale payloads; no silent cascade.
- Reminder: due-time child schedules under existing quiet-hour/permission behavior, completion/delete cancels, restart/zone change reconciles; date-only and undated behavior unchanged. Verify denied notification permission fallback without new prompts from importance.
- Focus: search/select child with parent context, start/finish session, complete child, then try parent requiring confirmation. Task link/session/generation change must invalidate pending completion. Compare task/session time: selected ID only, no automatic parent duplicate. Deleting a family keeps historical Focus durations and unlinks every deleted task.
- Expected: consistent IDs/state/counts and common family policy across every entry point, with existing app-lock and retry behavior preserved.

### Case 07 — Completion/Undo/delete transactions

- Cancel root-completion dialog: no rows change. Confirm root plus remaining open children: exact count/timestamps/revisions change; already completed children untouched. Repeated same command returns same receipt/timestamps.
- Change family after preview, including a newly added child or an unrelated member edit: apply rejects atomically and requires a new preview. Double taps and competing roots do not duplicate or block unrelated families unnecessarily.
- Undo group: exact previous statuses/timestamps restored only for changed rows. Add/reopen/edit/delete/reorder any family member after commit: stale Undo fails wholly; prove completed-root invariant and no lost concurrent change.
- Reopen child beneath completed root: confirmation describes both changes; Cancel is no-op, confirm reopens both. Reopen root alone leaves child statuses alone.
- Delete child vs root family with completed siblings; changed membership after preview requires new confirmation. Verify full Focus unlink/runtime revision update and reminder invalidation in the same transaction where required.
- Expected: rollback on injected mid-operation failure; no partial mutation, timestamp invention or old single-row API bypass.

### Case 08 — Retry, crash and security recovery

- Inject repository failure before commit, after commit/before response and during refresh. Recreate UI/process and retry retained command IDs; committed results reconcile without duplicate state; changed-family retry conflicts rather than overwrites.
- Cancel/unpin/prune expired journal handles; missing handles show stale state and do not recreate commands. Uncommitted previews require fresh confirmation after process death. Verify pinned pending retry/Undo records are not pruned.
- Restore while a dirty child editor, family confirmation, Today action, Focus completion and widget Undo exist. Successful replacement invalidates all old handles/generations; cancelled/failed restore preserves live records and drafts.
- Lock app, enter via child/widget/notification/Focus paths and try deletion/confirmation; preserve existing auth/cancel boundaries and public-preview privacy. Evidence/logs contain synthetic IDs/content only.
- Expected: recoverable errors with drafts and retry feedback, no silent stale replay or cross-dataset mutation.

### Case 09 — UI/platform matrix

Run affected interactions (importance selection, parent/child editor, expanded list, family confirmation/Undo and each changed picker/row family) in EN/FR/FA × system Light/Dark × these configurations: 320 dp portrait/font 2.0; 411 dp portrait/font 1.0; 411 dp landscape/font 2.0; at least 840 dp/font 1.0. Include one live system-theme transition and actual Persian RTL.

Verify localized importance and count/plural text, mixed Persian/Latin titles, all four choices, wrapped breadcrumbs, at least 48 dp targets, keyboard tab/Enter/Back, IME visibility, gesture/three-button insets and final action/FAB reachability. Test Gregorian/Persian calendar display and 12/24-hour preferences without changing stored civil values; use UTC and Asia/Tehran plus a DST boundary zone with injected clocks.

Use API 36 for the full affected matrix and API 28 for core capture/editor/status/delete/restore journeys, plus API 33+ notification permission paths. If actual project support differs, record actual manifest/toolchain baseline and justify the matrix update rather than silently claiming unsupported runs.

Observe TalkBack spoken importance, expanded state, progress, parent context, dialog impact and focus return after collapse/delete. Semantics assertions supplement human observation; they do not substitute for speech/focus evidence. Native launcher evidence is required for widgets.

### Case 10 — Archives/restore/reset

- Round-trip current archive with family fields, importance, child order/status, Focus links and reminder state; verify exact content and graph after replacement.
- Import every currently supported legacy header/version with populated tasks where that version supports them; older versions without tasks import zero tasks. Defaults are roots/None; exact old time adapter behavior retained.
- Valid archive with child rows before parent succeeds via staging root-first import. Missing/cyclic/deep parent, duplicate sibling position, unknown importance and completed-root/open-child graph reject before live replacement. Cancelled preview leaves live data unchanged.
- Verify preview total includes each task record once and explicitly includes subtasks, task and family token rotation, local journal exclusion/clearing, stale widget/Focus/UI rejection and post-restore reminder reconciliation.
- Run app-data reset only in the isolated fixture; all tasks/journal/pending family state clear and other existing reset guarantees remain intact.

### Case 11 — Scale and performance

- Compare equivalent baseline and candidate builds on the same named emulator/configuration. Seed 10,000 records and one 1,000-child family; independently count roots, children, statuses and date/filter matches.
- Capture 5 warmups then 30 measured query/count/mapping runs for root prefix, importance matches, child prefix/progress and Today prefix. Save raw samples and nearest-rank p95; separately capture cold first-open and scroll frame timing.
- Verify initial root/child/Today materialization is at most 50 rows per active prefix, no Int.MAX_VALUE task fetch, no N+1 parent/progress lookups, full counts without full-family UI loading and correct paging to the end. Capture query plans for new indexes.
- Provisional regression budget: candidate p95 at most 1.20× baseline for equivalent pre-existing queries on identical fixtures; investigate any new frame-deadline misses relative to baseline. New hierarchy-only queries require bounded-work/query-plan evidence and documented latency, not a fabricated old-feature comparator. If measurement noise prevents a conclusion, mark BLOCKED and improve controls.
- Emulator measurements are labelled as such; representative-phone performance remains unverified unless separately measured. No claim that emulator timing proves phone performance.

## Owning test files to author/update in this task

Use existing test style/fixtures. Planned additions may be renamed with a recorded mapping.

- Domain: existing `core/domain/src/test/kotlin/com/luma/lifeos/domain/todo/TodoRulesTest.kt`; new `TodoImportanceTest.kt` and `TodoFamilyPolicyTest.kt` in that directory.
- Data instrumentation: existing `core/data/src/androidTest/kotlin/com/luma/lifeos/data/{TodoMigrationTest,TodoPersistenceTest,TodoScaleAcceptanceTest}.kt`, `backup/TodoBackupTest.kt`; add `TodoFamilyCommandsTest.kt` for transactional races/replay/Undo and extend widget/Focus integration tests discovered from actual callsites.
- App host: existing `app/src/test/java/com/luma/lifeos/ui/todo/{TodoEditorViewModelTest,TodoListViewModelTest,TodoRestoreInvalidationTest,TodayTasksStateTest,TodoRepositoryFixture}.kt`; new `TodoFamilyActionsTest.kt` and actual Today/Focus fixture consumers. Update all TodoRepository fakes/constructors for new signatures here before compiling tests.
- App instrumentation: existing `app/src/androidTest/java/com/luma/lifeos/todo/{TodoEditorFlowTest,TodoNavigationTest,TodoAccessibilityTest,TodoLanguageFlowTest,TodayTasksFlowTest,TodayTasksSemanticsTest}.kt`; add `TodoHierarchyFlowTest.kt`; affected Calendar/Focus/widget regressions discovered from actual test artifacts.

For each assertion record requirement → case → test method/manual observation. Do not mirror implementation predicates in expected ordering/graph assertions. Use deterministic failure hooks or transactional fixtures for crash/concurrency branches rather than timing-dependent sleeps.

## Execution checklist and command templates

- [ ] Confirm all source handoffs, isolation and fixture identities; record candidate before any test-side edits.
- [ ] Author focused domain/data/app tests, update existing fakes and run targeted red/green checks for defects where feasible within this QA phase.
- [ ] Generate and inspect schema JSON; compile production/test sources and lint affected modules. Commit generated schema artifacts with validated migration source, preserving old schemas.
- [ ] Run owning host suites and focused instrumentation first, then required affected regression suites and the core feature matrix. Inspect actual test counts/skips; do not repeat full suites without new changes/failures that justify it.
- [ ] Execute Cases 01–11 on dedicated emulator fixtures and retain screenshots/logs/raw measurements/manual observations. Keep user informed during long runs.
- [ ] Fix defects within this same task, invalidate affected earlier evidence and rerun owning tests plus affected integrations. Substantial scope change revises spec/source handoff before testing resumes; it does not silently broaden the feature.
- [ ] Reconcile old TD07/redesign pending evidence with current results. Old failures remain historical; do not label them fixed without current proof or use them to waive new failures.
- [ ] Complete acceptance record with exact validated scope and remaining gaps. Required cases/matrix cells must pass or have explicit user-approved exceptions before release; data-loss/orphan/stale-mutation defects block acceptance.

Command templates, to adapt after verifying actual build targets:

```sh
bash gradlew -PdomainOnly=true :core:domain:test
bash gradlew :app:testDebugUnitTest :core:data:testDebugUnitTest
bash gradlew :app:assembleDebug :app:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest :app:lintDebug :core:data:lintDebug
# Only when these variants are verified to target the isolated QA package/runner:
bash gradlew :app:connectedDebugAndroidTest :core:data:connectedDebugAndroidTest
```

If debug targets the personal app, replace with actual isolated variant tasks before running connected tests. Dependency/toolchain failure is BLOCKED, not PASS. Direct ADB instrumentation fallback is allowed only with documented package/runner/output evidence; do not silently skip connected coverage because a host runner fails.

## Exit criteria

Exactly one TI-QA01 result is recorded after all source work. PASS requires all necessary host/schema/build/lint checks and required emulator/manual cases/matrix cells with evidence, zero unresolved critical defects, and documented scope for any approved exceptions. SOURCE COMPLETE, compilation or a subset pass never substitutes for full acceptance. This document’s current status remains **NOT RUN** until actual execution evidence exists.
