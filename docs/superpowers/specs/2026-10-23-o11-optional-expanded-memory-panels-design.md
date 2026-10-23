# O11 — Optional expanded memory panels specification

Date: 2026-10-23  
Status: Optional / deferred / not selected.  
Depends on: T38.

## Intent and sources

Start the importance and context panels expanded on new memory forms to match the reference's immediately visible controls.

Sources: [PNG](../../luma-redesign/new-memory-light.png), [HTML](../../luma-redesign/new-memory-light.html), [shared brief](../../luma-redesign/README.md), [task index](../../UI_REDESIGN_TASKS.md). This option is a proposed behavior/feature change; the matching required redesign remains independently deliverable.

## Current behavior and why optional

Memory importance starts collapsed when unset; context expansion depends on edit/promote/restored links/photos and photo errors. Showing both panels on a fresh form changes the existing progressive disclosure behavior.

## Global constraints

- Native Android/Kotlin/Compose; preserve minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- Use T30 semantic roles and bundled fonts; support System/Light/Dark and English/French/Persian offline.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL, IME and system insets.
- This task is optional, deferred and not selected; base redesign acceptance does not depend on it.
- Preserve all product behavior outside the explicit changes below; no new permissions, dependencies or network services.

## Proposed contract if selected

1. Initialize importance and people/place/photos panels expanded once when a new ready editor has no restored disclosure state. Keep both disclosure actions, localized expanded/collapsed semantics and manual collapse.
2. Preserve user-chosen disclosure state through recreation. A late load or value change cannot reopen a manually collapsed section. Preserve the existing photo-error override: an actionable photo failure opens context and brings recovery into view.
3. Render the same nullable 1–5 importance choices and real picker/photo slots. No default importance, selected person/place or attachment is introduced. Tapping selected importance clears it exactly as before.
4. Apply the initial default to direct-create and newly opened promote/edit screens, while honoring any restored user choice. Keep source preview, locked timestamp and direct-delete/promoted-unmark behavior.
5. This is a presentation-state policy only; no new stored preference, database field, autosave, camera flow or change to Save requirements.

## Implementation boundaries

- Modify `app/src/main/java/com/luma/lifeos/ui/memory/MemoryEditorScreen.kt`: existing `importanceExpanded`, `more` and initialization effects.
- Reuse `app/src/main/java/com/luma/lifeos/ui/common/EditorOptionalSection.kt`; no shared default changes for other editors.
- Preserve `MemoryEditorViewModel.kt` and `MemoryEditorRoute.kt` in the memory package; existing callbacks remain unchanged.

File groups in braces identify existing files under the given directory, except files explicitly marked Create. Existing public signatures stay compatible except the specific additions defined above. These decisions are planning proposals, not claims of user approval.

## Acceptance if selected

- O11-1: New ready memory shows unset importance and real context controls expanded; opening/closing writes nothing and selects no values.
- O11-2: Collapse either section, recreate, then update an unrelated field: the restored collapsed state persists.
- O11-3: Photo failure reopens context and exposes Retry despite prior collapse; retry retains title/note/importance/links.
- O11-4: Promoted/edit screens keep original source semantics, clearable importance and reachable controls across both themes, all locales, IME and the size/font matrix.

## Evidence and status

Record every acceptance ID as PASS/FAIL/NOT RUN with build/device/date/theme/locale/window/font configuration under `docs/luma-redesign/evidence/<actual-run-date>/o11/`. Rerun affected base-editor and T33 checks after selection. Omitted unselected behavior is an intentional difference. No app tests or implementation occurred in this planning pass.

[Implementation plan](../plans/2026-10-23-o11-optional-expanded-memory-panels.md)

