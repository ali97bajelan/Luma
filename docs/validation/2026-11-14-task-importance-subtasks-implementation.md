# Task importance and subtasks — implementation handoff

Date: 14 November 2026  
Status: **Latest source handoff: TI-06/I02 hierarchy storage, family queries and transactional commands SOURCE COMPLETE — QA PENDING.** The TI-05 importance archive slice is preserved below; broader I04–I08 surface/archive work remains open and TI-QA01 owns test authoring and full validation.

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
