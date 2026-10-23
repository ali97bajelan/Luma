# O12 — Optional memory writing helpers specification

Date: 2026-10-23  
Status: Optional / deferred / not selected.  
Depends on: T38; independent of O11.

## Intent and sources

Add the reference's secondary writing prompt and a truthful derived word count beneath the direct-memory description.

Sources: [PNG](../../luma-redesign/new-memory-light.png), [HTML](../../luma-redesign/new-memory-light.html), [shared brief](../../luma-redesign/README.md), [task index](../../UI_REDESIGN_TASKS.md). This option is a proposed behavior/feature change; the matching required redesign remains independently deliverable.

## Current behavior and why optional

The current memory description has no prompt toolbar or word count. There is no durable-draft status exposed for a truthful saved-draft badge, and no defined High value classification.

## Global constraints

- Native Android/Kotlin/Compose; preserve minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- Use T30 semantic roles and bundled fonts; support System/Light/Dark and English/French/Persian offline.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL, IME and system insets.
- This task is optional, deferred and not selected; base redesign acceptance does not depend on it.
- Preserve all product behavior outside the explicit changes below; no new permissions, dependencies or network services.

## Proposed contract if selected

1. Add one localized static prompt beneath the direct-memory description, visually identified as a writing prompt. Keep it separate from editable text; never inject it into the note, infer it from private records or describe it as generated insight.
2. Define `internal fun memoryWordCount(text: String, locale: Locale): Int` in `ui/memory/MemoryWritingHelpers.kt`: use platform `java.text.BreakIterator.getWordInstance(locale)`; count segments containing at least one Unicode letter or digit, testing code points with `Character.isLetterOrDigit`. Punctuation/whitespace/emoji-only segments count zero. Persian ZWNJ segmentation follows that locale's platform iterator; do not replace it with whitespace splitting or advertise cross-platform identical counts.
3. Count only `state.draft.note` in direct create/edit, recomputing on note or locale change. Format with localized plural resources and numbers; zero is 0 words. Show no counter/prompt for promoted marker notes, whose source body has separate meaning.
4. This is a nonblocking display aid. Existing String.length validation and Save rules remain authoritative; word count is never a length limit or persisted metric. Avoid live-region announcements on every keystroke; expose readable semantics when focused.
5. Base/optional scope both omit the Draft badge and High value badge. This task introduces no durable autosave indicator, importance classification, journal shortcut, camera capture, new stored preference or network dependency.

## Implementation boundaries

- Create `app/src/main/java/com/luma/lifeos/ui/memory/MemoryWritingHelpers.kt` for `memoryWordCount`.
- Modify `app/src/main/java/com/luma/lifeos/ui/memory/MemoryEditorScreen.kt` to render the derived direct-memory toolbar.
- Add aligned prompt/plural resources in `app/src/main/res/values{,-fr,-fa}/strings.xml`.
- Create `app/src/test/java/com/luma/lifeos/ui/memory/MemoryWritingHelpersTest.kt`; extend existing `MemoryCaptureTest.kt` for direct/promoted UI behavior.

File groups in braces identify existing files under the given directory, except files explicitly marked Create. Existing public signatures stay compatible except the specific additions defined above. These decisions are planning proposals, not claims of user approval.

## Acceptance if selected

- O12-1: Empty/whitespace/punctuation/emoji-only notes count 0; “A calm morning” counts 3 and “café 123” counts 2.
- O12-2: Editing/recreation/locale changes update localized count from the same note; French/Persian mixed text follows the documented platform segmentation without modifying stored text.
- O12-3: Direct form shows the static prompt/counter; promoted marker form shows neither. Helper text is never saved as user content; limits/Save behavior stay unchanged.
- O12-4: Long localized prompt/plurals wrap under IME/large text/RTL in both themes; no per-keystroke TalkBack interruption and no false Draft/High value claim.

## Evidence and status

Record every acceptance ID as PASS/FAIL/NOT RUN with build/device/date/theme/locale/window/font configuration under `docs/luma-redesign/evidence/<actual-run-date>/o12/`. Rerun affected base-editor and T33 checks after selection. Omitted unselected behavior is an intentional difference. No app tests or implementation occurred in this planning pass.

[Implementation plan](../plans/2026-10-23-o12-optional-memory-writing-helpers.md)

