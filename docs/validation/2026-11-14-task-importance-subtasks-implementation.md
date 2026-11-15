# Task importance and subtasks — implementation handoff

Date: 14 November 2026  
Status: **Latest source handoff: I07/TI-10 Today, Calendar, widget, reminder and Focus integration SOURCE COMPLETE — QA PENDING. I08 remains open; TI-QA01 owns test authoring and full validation.** Earlier TI-05/TI-06 handoffs and their historical check results are preserved below.

## Source identity

- Base commit: `1d1941fc6cd6ad15b92b3450991da3d7e13ee5cf`.
- Room schema: 16, already allocated for the combined feature; unchanged by TI-05.
- Portable archive schema: 13, already allocated for the combined feature; retained.
- Changed source paths: `core/data/src/main/kotlin/com/luma/lifeos/data/backup/BackupArchive.kt`, `FocusMetadataArchiveAdapter.kt`, and new `TodoImportanceArchiveAdapter.kt`.
- Changed tracking paths: this note, `docs/TASKS_IMPROVEMENT_BACKLOG_2026-11-14.md`, and the approved design status line.
- The pre-existing untracked `app/production/` directory was left untouched.

## Implemented contract

- Current schema-13 task archives include `importance_code`; export and import retain it with the task row.
- Supported task archive schemas 4–12 omit `importance_code`; the current Room entity defaults those imports to None (code 0). The schema-4 `due_time_minutes` omission remains handled separately.
- Staging validation constructs `TodoEntity`, which rejects unsupported importance codes before any live database replacement.
- Restore preview counts staged task rows. Malformed archives fail during staging, and cancelling closes the staged backup without replacing live data.
- Successful restore rotates every task `revision_token` and resets task reminder state/tokens, invalidating stale task drafts and actions.
- No Room migration or destructive fallback was added for this archive-only slice.

## Checks

- Static inspection confirmed archive version dispatch, legacy column adapters, staged validation, cancel/failure cleanup, and restore revision rotation.
- `git diff --check`: passed.
- User-requested `bash ./gradlew check --console=plain`: failed at `:core:domain:test` with the same three previously tracked failures: `MemoryTest.directMemoryRequiresTextAndValidImportanceAndBoundedDrafts`, `ProtectedRecordClassifierTest.eventRecordsMatchTheIndependentFixtureProtectionSets`, and `ProtectedRecordClassifierTest.linkedIdentityFieldsRequirePeopleIndependentlyOfJournalOrMemoryProse`. `:core:data:compileDebugKotlin` succeeded before the task stopped.
- Tests, schema generation, and emulator verification remain pending for TI-QA01.

## TI-06 / I02 family storage and commands

- Base commit: `23ddb94d332fb13720166ea87d4b665eeebe7656`.
- Room schema version 17 is allocated; KSP generated [schema 17](../../core/data/schemas/com.luma.lifeos.data.db.LumaDatabase/17.json) during the requested check. Schema/migration verification remains TI-QA01 evidence. Portable archive schema 13 remains unchanged in this source handoff.
- Changed source paths: `core/data/src/main/kotlin/com/luma/lifeos/data/db/LumaDatabase.kt`, `core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomTodoRepository.kt`, new `RoomTodoFamilyCommands.kt`, `core/data/src/main/kotlin/com/luma/lifeos/data/todo/TodoDao.kt`, `TodoEntity.kt`, `TodoMapping.kt`, `core/data/src/main/kotlin/com/luma/lifeos/data/calendar/CalendarDao.kt`, new `TodoCommandDao.kt`, new `TodoCommandEntity.kt`, `core/data/src/main/kotlin/com/luma/lifeos/data/widget/WidgetDao.kt`, `WidgetCommandExecutor.kt`, and `core/data/src/main/kotlin/com/luma/lifeos/data/backup/RoomBackupService.kt`.
- Implemented additive 16→17 task-family storage and local command journal; bounded family row/search/Today projections with parent/progress joins; transactional child create/reorder, family completion/Undo/delete, reminder invalidation, Focus unlinking, widget-command invalidation, and legacy fail-closed behavior. Restore clears the local command journal inside the replacement transaction.
- Static review corrected stale family retry checks, a depth-transition trigger gap, and missing root progress in search rows. Widget-to-task confirmation routing and restored family-token/archive adaptation remain I07/I08 source work; no release-ready claim is made.
- `git diff --check`: passed after the source and handoff updates.
- User-requested `bash ./gradlew check --console=plain`: FAILED at `:core:domain:test` with the same 3 previously recorded failures: `MemoryTest.directMemoryRequiresTextAndValidImportanceAndBoundedDrafts`, `ProtectedRecordClassifierTest.eventRecordsMatchTheIndependentFixtureProtectionSets`, and `ProtectedRecordClassifierTest.linkedIdentityFieldsRequirePeopleIndependentlyOfJournalOrMemoryProse`. KSP completed for debug/release and `:core:data:copyRoomSchemas` produced schema 17; `:core:data:compileDebugKotlin` was not reached.
- TI-QA01 cases 01/03/05/07/08/10/11, schema/migration verification, test authoring, and emulator verification remain NOT RUN. The pre-existing untracked `app/production/` directory remains untouched.

## I07 / TI-10 Today, Calendar, widget, reminder and Focus integration

- Status: **SOURCE COMPLETE — QA PENDING** (15 November 2026). Base commit: `694b13e021d31545fb62e79106e87685c15158e8`.
- Today uses a bounded 50-row prefix, lazy list, count and Load more while retaining device-date-only visibility and all-item count. Calendar uses a batched row projection for parent/importance/progress with existing date membership and stable task-ID navigation. Focus picker uses bounded literal search rows; Focus completion runs the shared family preview/apply policy and validates the linked session, task and generation inside the same Room transaction as the completion write. This review correction closes the race between Focus validation and task mutation. Widget task rows show localized importance, parent and full child progress; family-confirmation outcomes open the exact task after unlock. Existing database invalidation requests widget refresh and reminder reconciliation after task-family transactions; reminder notification actions preflight family completion before dispatch. Focus releases its retained completion receipt because it does not offer Undo.
- Legacy Undo/reminder/widget status writes still call the repository, whose Room family engine rejects unsafe single-row family mutations with a typed confirmation requirement. No direct app Todo DAO completion/status writes were found outside the family transaction engine. Restore replacement already deletes local widget commands, clears family command records and resets widget feedback transactionally; the app restore generation/runtime invalidates UI handles.
- I07 changes no Room or portable archive version. The combined feature retains its previously allocated Room schema 17 and archive schema 13; generated schema, migrations and archive acceptance remain TI-QA01 evidence.
- `git diff --check`: passed after the final source correction. Android Studio editor inspections reported no errors in the reviewed changed files, including the Focus transaction guard; non-blocking warnings remain. No tests were authored and no device/emulator actions were performed.
- Requested final `bash ./gradlew check --console=plain`: **FAILED** at `:core:domain:test` (187 tests, 3 failures): `MemoryTest.directMemoryRequiresTextAndValidImportanceAndBoundedDrafts`, `ProtectedRecordClassifierTest.eventRecordsMatchTheIndependentFixtureProtectionSets`, and `ProtectedRecordClassifierTest.linkedIdentityFieldsRequirePeopleIndependentlyOfJournalOrMemoryProse`. These are the same three failures recorded in earlier feature checks. Integration health/usage unit tasks passed; `:core:data:kspDebugKotlin` and `:core:data:kspReleaseKotlin` ran, `:core:data:copyRoomSchemas` was `NO-SOURCE`, and app/data Kotlin compilation was not reached. The transaction-guard correction was applied after this check; this check does not verify that final correction, and no second Gradle run was made. TI-QA01 remains NOT RUN; I08 remains open.
- Changed source paths:
  - `app/src/main/java/com/luma/lifeos/focus/FocusViewModel.kt`
  - `app/src/main/java/com/luma/lifeos/lock/ProtectedCatalogRepositories.kt`
  - `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt`
  - `app/src/main/java/com/luma/lifeos/ui/calendar/CalendarAgenda.kt`
  - `app/src/main/java/com/luma/lifeos/ui/focus/FocusCompletionContent.kt`
  - `app/src/main/java/com/luma/lifeos/ui/focus/FocusRoute.kt`
  - `app/src/main/java/com/luma/lifeos/ui/focus/FocusScreen.kt`
  - `app/src/main/java/com/luma/lifeos/ui/focus/FocusSessionDetailContent.kt`
  - `app/src/main/java/com/luma/lifeos/ui/focus/FocusTaskCompletionAction.kt`
  - `app/src/main/java/com/luma/lifeos/ui/focus/FocusTaskCompletionState.kt`
  - `app/src/main/java/com/luma/lifeos/ui/focus/FocusTaskPicker.kt`
  - `app/src/main/java/com/luma/lifeos/ui/focus/FocusTaskPickerState.kt`
  - `app/src/main/java/com/luma/lifeos/ui/today/TodayRoute.kt`
  - `app/src/main/java/com/luma/lifeos/ui/today/TodayScreen.kt`
  - `app/src/main/java/com/luma/lifeos/ui/todo/TodayTasksCard.kt`
  - `app/src/main/java/com/luma/lifeos/ui/todo/TodayTasksState.kt`
  - `app/src/main/java/com/luma/lifeos/ui/todo/TodoFamilyActions.kt`
  - `app/src/main/java/com/luma/lifeos/widget/WidgetCompactRenderer.kt`
  - `app/src/main/java/com/luma/lifeos/widget/WidgetRenderer.kt`
  - `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml`, `app/src/main/res/values-fa/strings.xml`
  - `core/data/src/main/kotlin/com/luma/lifeos/data/calendar/CalendarDao.kt`
  - `core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomCalendarRepository.kt`
  - `core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomTodoRepository.kt`
  - `core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomTodoFamilyCommands.kt`
  - `core/data/src/main/kotlin/com/luma/lifeos/data/todo/TodoDao.kt`
  - `core/data/src/main/kotlin/com/luma/lifeos/data/widget/RoomWidgetStore.kt`
  - `core/data/src/main/kotlin/com/luma/lifeos/data/widget/WidgetCommandExecutor.kt`
  - `core/data/src/main/kotlin/com/luma/lifeos/data/widget/WidgetCommandPayload.kt`
  - `core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoFamilyModels.kt`
  - `core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoModels.kt`
  - `core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/TodoRepository.kt`
  - `core/domain/src/main/kotlin/com/luma/lifeos/domain/widget/WidgetModels.kt`
  - Tracking: `docs/TASKS_IMPROVEMENT_BACKLOG_2026-11-14.md`, `docs/superpowers/plans/2026-11-14-task-importance-subtasks.md`, `docs/superpowers/specs/2026-11-14-task-importance-subtasks-design.md`, and this handoff.
- Suggested commit message: `feat(todo): integrate task families across surfaces`. No commit was created.
