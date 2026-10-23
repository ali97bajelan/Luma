# O09 — Optional persistent task tags specification

Date: 2026-10-23  
Status: Optional / deferred / not selected.  
Depends on: T37.

## Intent and sources

Make the reference's tag chips functional with user-authored tags saved on standalone tasks.

Sources: [PNG](../../luma-redesign/new-task-light.png), [HTML](../../luma-redesign/new-task-light.html), [shared brief](../../luma-redesign/README.md), [task index](../../UI_REDESIGN_TASKS.md). This option is a proposed behavior/feature change; the matching required redesign remains independently deliverable.

## Current behavior and why optional

Tasks have title/note/due date and revision tokens; no tags. Journal tags already use Tag.normalize, a tag catalog and event_tag links. A todo_task is not a life_event, so event_tag cannot be reused as a task link. Room is version 5 and the archive schema is version 4 at this planning baseline.

## Global constraints

- Native Android/Kotlin/Compose; preserve minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- Use T30 semantic roles and bundled fonts; support System/Light/Dark and English/French/Persian offline.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL, IME and system insets.
- This task is optional, deferred and not selected; base redesign acceptance does not depend on it.
- Preserve all product behavior outside the explicit changes below; no new permissions, dependencies or network services.

## Proposed contract if selected

1. Add task-only tag associations using the existing shared tag catalog. New `todo_tag` links `task_id` to `todo_task.id` and `tag_id` to `tag.id`, composite primary key, foreign keys with cascade delete and an index on tag_id. Never synthesize life_event records for tasks.
2. Add `tagNames: Set<String> = emptySet()` at the end of `TodoInput`, `TodoTask` and `TodoDraft`; update all input/task mapping, normalization and equality/reconciliation paths. Normalize/deduplicate with existing `Tag.normalize`; preserve the existing catalog display name. Reject blank or multiline names; max 50 UTF-16 code units per trimmed tag and 50 distinct normalized tags, matching the journal limits. Base title/note limits stay in Unicode code points.
3. On Save, create/reuse catalog tags and replace associations in the same transaction as the revision-checked task write. A stale revision cannot mutate tags or create orphan catalog rows. Idempotent replay compares the normalized tag set as well as fields. Completion/reopen and widget mutations preserve tags without replacement; deleting a task deletes its associations, leaving shared tags/other records intact.
4. Add an inline Add tag field/action below Due date, wrapping selected chips with separate Remove semantics, selected-count/limit errors and no seeded Focus/Personal/Work. Labels are actual names; no inferred sage/terracotta category meaning. Adding/removing before Save changes the draft only. No global tag manager, task filtering, People/Place links or task-search expansion is included.
5. Persist tags in draft/baseline/pending-operation SavedStateHandle data; restore dirty/conflict/retry/confirmed reload semantics. Opening an old untagged task yields an empty set. Restore invalidation must drop stale pre-restore tag data just as it drops stale task revisions.
6. Add migration 5→6 without deleting data and export schema 6 (rebase versions if the schema advances before implementation). Add backup schema 5 with todo_tag after both parent tables, preserving import of archive schemas 1–4 as empty task tags. Validate foreign keys, tag bounds/normalization/duplicates and per-task association limits before replacement; malformed archives leave live data untouched. Include tags in privacy reset through the existing database graph.
7. The optional feature remains gated until persistence, migration, backup/restore and revision tests pass. A chip-only mock implementation does not satisfy this task.

## Implementation boundaries

- Domain: modify `core/domain/src/main/kotlin/com/luma/lifeos/domain/todo/{TodoModels,TodoRules,TodoInputs}.kt`; retain repository method signatures in `TodoRepository.kt`.
- Create `core/data/src/main/kotlin/com/luma/lifeos/data/todo/TodoTagEntity.kt`; modify `TodoDao.kt`, `TodoMapping.kt`, `data/repository/RoomTodoRepository.kt` and `data/db/LumaDatabase.kt` under that source root. Page counts/order must remain task-based; load associations by bounded task-ID batches rather than joining duplicates into the page.
- Modify `core/data/src/main/kotlin/com/luma/lifeos/data/backup/{BackupArchive,BackupValidation,BackupSql,RoomBackupService}.kt` and export the Room schema under `core/data/schemas/com.luma.lifeos.data.db.LumaDatabase/`.
- Modify `app/src/main/java/com/luma/lifeos/ui/todo/{TodoUiState,TodoEditorViewModel,TodoEditorScreen,TodoEditorRoute}.kt`; retain existing route result/restore contracts.
- Add matching strings in `app/src/main/res/values{,-fr,-fa}/strings.xml`; update `docs/BACKUP_FORMAT.md` and `docs/PRIVACY.md` for the added data.

File groups in braces identify existing files under the given directory, except files explicitly marked Create. Existing public signatures stay compatible except the specific additions defined above. These decisions are planning proposals, not claims of user approval.

## Acceptance if selected

- O09-1: Adding Focus and focus produces one actual tag association; absent/removed tags persist across Save/reopen/recreation, while canceled changes create no links.
- O09-2: Blank, multiline, 51-code-unit names and 51 distinct tags produce localized validation; French/Persian/emoji tags round-trip without silent truncation.
- O09-3: Stale/conflicting/replayed saves are atomic; complete/reopen/widget actions preserve tags; deleting one tagged task preserves tags on other tasks/journals.
- O09-4: Migration preserves old tasks; new archives round-trip tags; schemas 1–4 restore empty task tags; dangling/invalid/oversized associations reject before live replacement.
- O09-5: Saved draft/pending/reload and post-restore invalidation retain correct tags/revisions; paging totals/order and Today/widget eligibility remain unchanged.

## Evidence and status

Record every acceptance ID as PASS/FAIL/NOT RUN with build/device/date/theme/locale/window/font configuration under `docs/luma-redesign/evidence/<actual-run-date>/o09/`. Rerun affected base-editor and T33 checks after selection. Omitted unselected behavior is an intentional difference. No app tests or implementation occurred in this planning pass.

[Implementation plan](../plans/2026-10-23-o09-optional-task-tags.md)

