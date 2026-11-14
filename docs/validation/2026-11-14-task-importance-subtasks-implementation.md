# Task importance and subtasks — implementation handoff

Date: 14 November 2026  
Status: **TI-05 importance backup compatibility SOURCE COMPLETE — QA PENDING.** This handoff covers the importance archive slice. Broader I08 hierarchy/archive work remains open; TI-QA01 owns test authoring and full validation.

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
