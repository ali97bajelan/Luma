# O10 — Optional visible mood note specification

Date: 2026-10-23  
Status: Optional / deferred / not selected.  
Depends on: T36.

## Intent and sources

Show the existing mood note immediately, as pictured, while keeping structured energy/stress/people/place controls in More details.

Sources: [PNG](../../luma-redesign/new-mood-light.png), [HTML](../../luma-redesign/new-mood-light.html), [shared brief](../../luma-redesign/README.md), [task index](../../UI_REDESIGN_TASKS.md). This option is a proposed behavior/feature change; the matching required redesign remains independently deliverable.

## Current behavior and why optional

Mood currently places note, energy/stress and context slots inside More details. The reference shows a note field immediately below emotion chips; moving it changes interaction/disclosure behavior even though no new persistence field is needed.

## Global constraints

- Native Android/Kotlin/Compose; preserve minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- Use T30 semantic roles and bundled fonts; support System/Light/Dark and English/French/Persian offline.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL, IME and system insets.
- This task is optional, deferred and not selected; base redesign acceptance does not depend on it.
- Preserve all product behavior outside the explicit changes below; no new permissions, dependencies or network services.

## Proposed contract if selected

1. In ready editable content, render exactly one `mood_note` field after emotions and before More details, bound to the existing note value and `onNote: (String) -> Unit`. Remove its duplicate from the expanded section. Keep the note limit and existing error feedback beside the field.
2. More details continues to reveal energy/stress/people/place with current initialization/toggle/restoration behavior. Opening, collapsing or recreating never clears note or structured values. The note remains visible independently of that toggle.
3. Loading/delete/draft-storage recovery remains authoritative; keep current read-only recovery previews, retries, durability-gated Save, dirty-close and existing record type. The edit affordance/“Reflective Journal entry” text is omitted; note entry creates no journal record.
4. No new persisted field, autosave policy, schema, picker or navigation route. O10 is independent of memory disclosure option O11 and can be selected alone.

## Implementation boundaries

- Modify `app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorScreen.kt` only for note placement and disclosure copy.
- Reuse `MoodEditorViewModel.kt`, `MoodDraftStore.kt` and `MoodEditorRoute.kt` unchanged in the same package.
- Update `app/src/main/res/values{,-fr,-fa}/strings.xml` only if context help needs wording that no longer promises to reveal the note.

File groups in braces identify existing files under the given directory, except files explicitly marked Create. Existing public signatures stay compatible except the specific additions defined above. These decisions are planning proposals, not claims of user approval.

## Acceptance if selected

- O10-1: A new ready form exposes one note field without opening More details; expanding/collapsing never duplicates it or loses text.
- O10-2: Restored/edit note and energy/stress/links survive recreation, Save and Cancel under existing rules; no journal record is created.
- O10-3: Over-limit note, draft-storage failure and delete recovery retain visible validation/retry and correct Save gating.
- O10-4: Note, disclosure, structured controls and Save remain reachable with IME in both themes, en/fr/fa and the shared size/font matrix.

## Evidence and status

Record every acceptance ID as PASS/FAIL/NOT RUN with build/device/date/theme/locale/window/font configuration under `docs/luma-redesign/evidence/<actual-run-date>/o10/`. Rerun affected base-editor and T33 checks after selection. Omitted unselected behavior is an intentional difference. No app tests or implementation occurred in this planning pass.

[Implementation plan](../plans/2026-10-23-o10-optional-visible-mood-note.md)

