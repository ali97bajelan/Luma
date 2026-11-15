# TI-QA01 execution record — 15 November 2026

Status: **PARTIAL — acceptance remains BLOCKED.** Focused domain and Todo host tests pass. Persistence/device/manual evidence is incomplete, and the requested final Gradle check fails in broader suites. No release acceptance is claimed.

## Run identity and environment

- Test XML window: 2026-11-15 12:00:30.028–12:00:32.995 UTC.
- Base commit: `83cba30d89f9e5f30b703e1c891293faffa721e7`.
- Tracked working-tree diff SHA-256 at the verification run: `e089012a4031ef44fd1001a13095145e95824978dcbf8291d070a63ba6f894f8`.
- Five new test files were untracked at run time; their SHA-256 values are listed in the source inventory below. The pre-existing untracked `app/production/` directory was not touched and is excluded.
- Gradle 9.1.0, Kotlin 2.2.0, JDK 17.0.21, Linux amd64. Android CLI 1.1.16717390.
- `adb devices -l`: no connected devices. A disposable `medium_phone` AVD creation attempt failed with `Failed to connect to remote repository`. The existing `Medium_Phone_API_36.1` AVD was not started or modified.
- No application/test package was installed; applicationId, testApplicationId, runner, APK hash and device configuration were not captured.

## Executed checks

| Command | Result |
| --- | --- |
| `bash ./gradlew -PdomainOnly=true :core:domain:test --tests com.luma.lifeos.domain.todo.TodoImportanceTest --tests com.luma.lifeos.domain.todo.TodoFamilyPolicyTest --console=plain` | PASS — 5 tests across the two new suites. |
| `bash ./gradlew :app:testProductionDebugUnitTest --tests 'com.luma.lifeos.ui.todo.TodoEditorViewModelTest' --tests 'com.luma.lifeos.ui.todo.TodoListViewModelTest' --tests 'com.luma.lifeos.ui.todo.TodoFamilyActionsTest' --tests 'com.luma.lifeos.ui.todo.TodayTasksStateTest' --tests 'com.luma.lifeos.ui.todo.TodoRestoreInvalidationTest' --console=plain` | PASS. |
| `bash ./gradlew :core:data:compileDebugAndroidTestKotlin --console=plain` | PASS — test sources compile; instrumentation was not run. |
| `bash ./gradlew :app:compileProductionDebugAndroidTestKotlin --console=plain` | FAIL — unrelated app instrumentation sources do not compile: lock test call sites no longer match current APIs; `WidgetRendererAcceptanceTest` references missing `widget_capture_journal`. The newly added Todo UI test sources produced no reported compiler errors. |
| `git diff --check` | PASS. |
| `bash ./gradlew check --console=plain` | FAIL — see the three failures below. This was run after test implementation was complete, as requested. |

The final check reported:

- `:core:domain:test`: 192 tests, 3 failures: `MemoryTest.directMemoryRequiresTextAndValidImportanceAndBoundedDrafts`, `ProtectedRecordClassifierTest.eventRecordsMatchTheIndependentFixtureProtectionSets`, and `ProtectedRecordClassifierTest.linkedIdentityFieldsRequirePeopleIndependentlyOfJournalOrMemoryProse`. These match the three failures recorded by earlier task handoffs.
- `:core:data:compileDebugUnitTestKotlin`: fails to compile three `FocusMetadataArchiveAdapterTest` calls at lines 120, 123 and 125 because required `taskId` and `taskOrigin` arguments are missing.
- `:app:testProductionDebugUnitTest`: 352 tests, 344 passed and 8 failed outside the Todo package: one `TranslationCatalogTest` and seven `MemoryEditorTest` cases. The 34 tests across the five Todo host suites listed above passed in this run.

Room schema 17 is present and declares the task importance, parent ID and sibling position columns; source inspection found the 16→17 migration. Migration execution, Room integrity/FK checks and populated legacy database behavior remain unverified because Android instrumentation could not run.

## Case results

Every case remains **BLOCKED** as a complete acceptance scenario. Passing host tests are useful evidence, but they do not close required persistence, UI, platform or manual checks.

| Case | Result and evidence |
| --- | --- |
| 01 — Model/migrations | BLOCKED — [domain tests](../../core/domain/src/test/kotlin/com/luma/lifeos/domain/todo/TodoImportanceTest.kt) pass; [populated Room migration tests](../../core/data/src/androidTest/kotlin/com/luma/lifeos/data/TodoMigrationTest.kt) have not executed. |
| 02 — Editor/drafts | BLOCKED — [Todo editor host tests](../../app/src/test/java/com/luma/lifeos/ui/todo/TodoEditorViewModelTest.kt) pass; [Compose editor journey](../../app/src/androidTest/java/com/luma/lifeos/todo/TodoEditorFlowTest.kt) and device matrix are unavailable. |
| 03 — Hierarchy/order/CRUD | BLOCKED — [domain policy tests](../../core/domain/src/test/kotlin/com/luma/lifeos/domain/todo/TodoFamilyPolicyTest.kt) pass and [data command tests](../../core/data/src/androidTest/kotlin/com/luma/lifeos/data/TodoFamilyCommandsTest.kt) compile; persistence tests have not executed. |
| 04 — Parent/child journeys | BLOCKED — [family-action host test](../../app/src/test/java/com/luma/lifeos/ui/todo/TodoFamilyActionsTest.kt) passes; [Compose hierarchy flow](../../app/src/androidTest/java/com/luma/lifeos/todo/TodoHierarchyFlowTest.kt) was not run. |
| 05 — Queries/paging | BLOCKED — [list](../../app/src/test/java/com/luma/lifeos/ui/todo/TodoListViewModelTest.kt) and [Today](../../app/src/test/java/com/luma/lifeos/ui/todo/TodayTasksStateTest.kt) host tests pass; [Room scale/query tests](../../core/data/src/androidTest/kotlin/com/luma/lifeos/data/TodoScaleAcceptanceTest.kt) have not executed. |
| 06 — Cross-surface journeys | BLOCKED — [Today host coverage](../../app/src/test/java/com/luma/lifeos/ui/todo/TodayTasksStateTest.kt) passes; Calendar, widget, reminder and Focus device journeys have not run. |
| 07 — Atomic actions/Undo/delete | BLOCKED — [host family-action coverage](../../app/src/test/java/com/luma/lifeos/ui/todo/TodoFamilyActionsTest.kt) passes; [transactional command/Undo tests](../../core/data/src/androidTest/kotlin/com/luma/lifeos/data/TodoFamilyCommandsTest.kt) have not executed. |
| 08 — Retry/restore/app lock | BLOCKED — [editor](../../app/src/test/java/com/luma/lifeos/ui/todo/TodoEditorViewModelTest.kt) and [restore](../../app/src/test/java/com/luma/lifeos/ui/todo/TodoRestoreInvalidationTest.kt) host tests pass; process-death, restore UI and app-lock device paths have not run. |
| 09 — Locale/theme/accessibility | BLOCKED — [language journey](../../app/src/androidTest/java/com/luma/lifeos/todo/TodoLanguageFlowTest.kt) was not run; no EN/FR/FA device matrix, Persian RTL, TalkBack, keyboard, font-scale or theme observations. |
| 10 — Archive/reset | BLOCKED — [archive instrumentation source](../../core/data/src/androidTest/kotlin/com/luma/lifeos/data/backup/TodoBackupTest.kt) compiles; round-trip, malformed archive and reset tests have not executed. |
| 11 — Scale/performance | BLOCKED — [10,000-record test source](../../core/data/src/androidTest/kotlin/com/luma/lifeos/data/TodoScaleAcceptanceTest.kt) compiles; no device run, timing samples or query-plan evidence were captured. |

## Source inventory

Changed paths at the verification run:

- `app/src/androidTest/java/com/luma/lifeos/todo/TodoEditorFlowTest.kt`
- `app/src/androidTest/java/com/luma/lifeos/todo/TodoLanguageFlowTest.kt`
- `app/src/androidTest/java/com/luma/lifeos/todo/TodoHierarchyFlowTest.kt` (new)
- `app/src/main/java/com/luma/lifeos/ui/todo/TodoEditorViewModel.kt`
- `app/src/test/java/com/luma/lifeos/ui/todo/TodayTasksStateTest.kt`
- `app/src/test/java/com/luma/lifeos/ui/todo/TodoEditorViewModelTest.kt`
- `app/src/test/java/com/luma/lifeos/ui/todo/TodoFamilyActionsTest.kt` (new)
- `app/src/test/java/com/luma/lifeos/ui/todo/TodoListViewModelTest.kt`
- `app/src/test/java/com/luma/lifeos/ui/todo/TodoRepositoryFixture.kt`
- `app/src/test/java/com/luma/lifeos/ui/todo/TodoRestoreInvalidationTest.kt`
- `core/data/src/androidTest/kotlin/com/luma/lifeos/data/TodoMigrationTest.kt`
- `core/data/src/androidTest/kotlin/com/luma/lifeos/data/TodoScaleAcceptanceTest.kt`
- `core/data/src/androidTest/kotlin/com/luma/lifeos/data/TodoFamilyCommandsTest.kt` (new)
- `core/data/src/androidTest/kotlin/com/luma/lifeos/data/WidgetStoreAcceptanceTest.kt`
- `core/data/src/androidTest/kotlin/com/luma/lifeos/data/backup/TodoBackupTest.kt`
- `core/domain/src/test/kotlin/com/luma/lifeos/domain/todo/TodoFamilyPolicyTest.kt` (new)
- `core/domain/src/test/kotlin/com/luma/lifeos/domain/todo/TodoImportanceTest.kt` (new)

`TodoEditorViewModel` gained a source-compatible constructor retained by older host test callers. Room schema 17 includes importance, parent ID, sibling position and family revision fields; the 16→17 migration is present. Portable archive version 14 is allocated. Authored populated migration tests target exported schemas 4–10, 12, 14–16 but have not executed; snapshots 11 and 13 are not available to `MigrationTestHelper`, so those baselines remain uncovered. Android Studio inspections reported no Kotlin errors in reviewed changed files. The report and status documents were written after the Gradle run and are not part of the source patch hash.

New test file hashes (SHA-256):

| File | SHA-256 |
| --- | --- |
| `app/src/androidTest/java/com/luma/lifeos/todo/TodoHierarchyFlowTest.kt` | `23dc34de10a0b231b95d34742f2ef1fd301524c022c32de31d07e168da158b38` |
| `app/src/test/java/com/luma/lifeos/ui/todo/TodoFamilyActionsTest.kt` | `175224fd128931424fc2e62b3308431be2e43df9e4a3118f899f1e92086b46a8` |
| `core/data/src/androidTest/kotlin/com/luma/lifeos/data/TodoFamilyCommandsTest.kt` | `e8503b508dd4336fe4fe429d8830e1816386f3232d10411322d91f1e16a0c5c7` |
| `core/domain/src/test/kotlin/com/luma/lifeos/domain/todo/TodoFamilyPolicyTest.kt` | `9a2516bb64dcebfe9c181a5df954a0677f44ab5beaf0a4df2c3b3557564649b5` |
| `core/domain/src/test/kotlin/com/luma/lifeos/domain/todo/TodoImportanceTest.kt` | `56bf0ad4e07ce69f4927c16f9d2096d495bd4e0ab1da73005dc86f6a3d4a97c1` |

## Required next step

Restore Android SDK repository access or provide a disposable configured API 36/API 28 device. Fix the listed non-Todo test-source compilation failures, rerun the final check, then execute and record the blocked persistence, emulator, accessibility, archive and performance cases. Keep TI-QA01 open until those required cases pass or an explicit exception is approved.
