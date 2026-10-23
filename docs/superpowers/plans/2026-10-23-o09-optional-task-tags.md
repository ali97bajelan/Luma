# O09 — Optional persistent task tags Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan does not request subagent delegation.

**Goal:** Make the reference's tag chips functional with user-authored tags saved on standalone tasks.

**Architecture:** Extend the existing revision-checked task model transactionally with a shared-catalog association; carry it through saved editor state and staged backup validation. Keep task queries and event semantics separate.

**Tech Stack:** Existing Kotlin, Compose Material 3, Navigation Compose, Gradle/JDK 17 and JUnit/Compose tests, Room and current ZIP backup infrastructure.

**Spec:** [O09 specification](../specs/2026-10-23-o09-optional-task-tags-design.md).

**Status:** Optional / deferred / not selected. Start only after explicit selection; no checks have run.

## Global constraints

- Native Android/Kotlin/Compose; preserve minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- Use T30 semantic roles and bundled fonts; support System/Light/Dark and English/French/Persian offline.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL, IME and system insets.
- This task is optional, deferred and not selected; base redesign acceptance does not depend on it.
- Preserve all product behavior outside the explicit changes below; no new permissions, dependencies or network services.

## Review Focus

- Case-normalized duplicates and multilingual limits (O09-1/2).
- Stale write cannot partially update links/catalog (O09-3).
- Completion or old caller input cannot silently erase tags (O09-3).
- Invalid archive graph leaves live data untouched (O09-4).
- Association loading cannot multiply page totals or restore stale drafts (O09-5).

## File map

- Domain: modify `core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/{TodoModels,TodoRules,TodoInputs}.kt`; retain repository method signatures in `TodoRepository.kt`.
- Create `core/data/src/main/kotlin/com/luma/lifeos/data/todo/TodoTagEntity.kt`; modify `TodoDao.kt`, `TodoMapping.kt`, `data/repository/RoomTodoRepository.kt` and `data/db/LumaDatabase.kt` under that source root. Page counts/order must remain task-based; load associations by bounded task-ID batches rather than joining duplicates into the page.
- Modify `core/data/src/main/kotlin/com/luma/lifeos/data/backup/{BackupArchive,BackupValidation,BackupSql,RoomBackupService}.kt` and export the Room schema under `core/data/schemas/com.luma.lifeos.data.db.LumaDatabase/`.
- Modify `app/src/main/java/com/luma/lifeos/ui/todo/{TodoUiState,TodoEditorViewModel,TodoEditorScreen,TodoEditorRoute}.kt`; retain existing route result/restore contracts.
- Add matching strings in `app/src/main/res/values{,-fr,-fa}/strings.xml`; update `docs/BACKUP_FORMAT.md` and `docs/PRIVACY.md` for the added data.

### Task 1: Domain and transactional persistence

**Interfaces:** `TodoInput.tagNames`, `TodoTask.tagNames` and `TodoDraft.tagNames` are `Set<String>` defaulting to empty; existing `TodoRepository` method signatures are preserved. New `TodoTagEntity(taskId: String, tagId: String)` owns associations.

- [ ] Extend `core/domain/src/test/kotlin/com/luma/lifeos/domain/todo/TodoRulesTest.kt` with O09-1/2 normalization/boundary cases.
- [ ] Extend `core/data/src/androidTest/kotlin/com/luma/lifeos/data/{TodoPersistenceTest,TodoMigrationTest}.kt` with O09-3/4 atomicity, replay and migration cases, including a failed tag mutation under a stale revision.
- [ ] Run these focused cases to establish the failing baseline before adding fields/storage.
- [ ] Add the fields, validation, entity, DAO reads/writes, migration/schema and repository mapping; ensure get/list/Today load tags without duplicate page rows. Defaulted inputs from non-editor callers must be audited; status writes never replace tag sets.
- [ ] Rerun the focused cases and confirm unchanged task ordering, counts and widget status behavior.

### Task 2: Draft editing and backup integration

**Interfaces:** Consume Task 1's normalized tag-set contract. Add `setTagNames(names: Set<String>)` to `TodoEditorViewModel` and an `onTagNames: (Set<String>) -> Unit` screen callback wired by `TodoEditorRoute.kt`; update all test/direct screen callers.

- [ ] Extend `app/src/test/java/com/luma/lifeos/ui/todo/{TodoEditorViewModelTest,TodoRestoreInvalidationTest}.kt` for O09-5 draft, pending replay, reload and restore invalidation.
- [ ] Extend `core/data/src/androidTest/kotlin/com/luma/lifeos/data/backup/TodoBackupTest.kt` plus existing host `BackupValidationTest.kt` for O09-4 new/legacy/dangling/oversized archives. Confirm failures before implementation.
- [ ] Implement tag editing/chips and all saved-state equality/pending/baseline paths; preserve completion and conflict behavior. Add aligned localized copy.
- [ ] Update archive inventory/schema, staged validation, replacement/reset paths and format documentation; keep old-version import support.
- [ ] Extend `TodoEditorFlowTest.kt` in the existing app androidTest todo package for add/remove/cancel/recreate plus large-text/RTL actions; rerun focused tests and record O09-1 through O09-5.

### Final task: Accept the selected option

**Files:** Create `docs/luma-redesign/evidence/<actual-run-date>/o09/acceptance.md`; update the task index and affected T33/route acceptance rows.

- [ ] Run the focused commands below on an isolated test install/AVD, then app assembly/lint. Confirm tests actually execute; unavailable prerequisites mean NOT RUN.
- [ ] Review every spec acceptance ID and Review Focus item, adding a meaningful regression assertion to its owning suite where coverage is missing and rendered/TalkBack evidence for visual/focus behavior.
- [ ] Record real results, intentional differences and any failures; leave the option incomplete while required checks fail or remain NOT RUN.
- [ ] Review and commit only the selected feature and evidence with `feat: add O09 task tags`.

## Planned verification

```bash
./gradlew :core:domain:test --tests com.luma.lifeos.domain.todo.TodoRulesTest
./gradlew :app:testDebugUnitTest --tests com.luma.lifeos.ui.todo.TodoEditorViewModelTest --tests com.luma.lifeos.ui.todo.TodoRestoreInvalidationTest
./gradlew :core:data:testDebugUnitTest --tests com.luma.lifeos.data.backup.BackupValidationTest
./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.data.TodoPersistenceTest,com.luma.lifeos.data.TodoMigrationTest,com.luma.lifeos.data.backup.TodoBackupTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.todo.TodoEditorFlowTest,com.luma.lifeos.widget.WidgetIntentsTest
./gradlew :app:assembleDebug :app:lintDebug
```

Expected: BUILD SUCCESSFUL, selected cases executed with zero failures, zero lint errors, and passing recorded acceptance. A missing device or skipped test is not a pass. The supplied plans describe future execution only.

