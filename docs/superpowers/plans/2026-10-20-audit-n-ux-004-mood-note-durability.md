# UX-004 — Unbounded Mood notes are copied into saved instance state Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use superpowers:subagent-driven-development only if the user selects delegation. Steps use checkbox syntax for tracking. This documentation request does not start implementation or delegate work.

**Goal:** Bound new Mood notes while keeping accepted and legacy long text recoverable outside the instance-state Bundle.

**Architecture:** Introduce a pure domain note-change policy and an app-private atomic draft store. Keep only draft identity and small navigation metadata in SavedStateHandle; preserve full text in the private store.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose/Material 3, lifecycle StateFlow and the existing Android/Room modules; existing dependencies.

**Spec:** [UX-004 specification](../specs/2026-10-20-audit-n-ux-004-mood-note-durability-design.md)

**Status:** Implemented / acceptance pending. Steps 1, 4 and 5 are complete; regression exercises, runtime acceptance and integration remain pending. Relative effort: Medium.

## Global Constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies unless a task explicitly requires a reviewed exception.
- All changed user-facing copy must exist in EN/FR/FA with matching resource names and numbered placeholder signatures; preserve RTL and intended Gregorian display/storage behavior.
- Keep semantic light/dark palettes, at least 48 dp interactive targets, and reachability at 100%/130%/200% font, narrow portrait and short landscape.
- Preserve offline/private storage, existing consent boundaries, stable record/command IDs, original retry payloads and Undo protections.
- Never truncate accepted personal content, infer write failure means no commit, or treat an unavailable privacy mode as known.
- Only implement this task and its declared prerequisites; preserve unrelated working-tree changes and existing navigation/draft state.

## Review Focus

These five concrete failure modes are pinned to the regression cases owned by this task. The assertions below are requirements for tests to add, not claims that those tests exist or currently pass.

- **RF-1 → AC-1:** Accept 5,000 UTF-16 units, then attempt 5,001. 5,000 units remain unchanged and an inline limit error appears; a surrogate pair counts as two units. Covered by `noteLimitRetainsAcceptedText` in the regression step below.
- **RF-2 → AC-2:** Load 12,000 units, save unchanged or shorten to 10,000; try expanding to 12,001. Read/copy and allowed edits succeed; growth is rejected without truncation. Covered by `legacyNoteCanBeReadAndShortened` in the regression step below.
- **RF-3 → AC-3:** Persist 100,000 legacy units, recreate the ViewModel with only draft ID metadata. Full text restores; the new saved-state payload has no note string. Covered by `restorationUsesPrivateSnapshot` in the regression step below.
- **RF-4 → AC-4:** The atomic write fails, or revision N finishes after an N+1 request. Visible latest text is retained, Retry is offered and no older snapshot replaces a newer durable revision. Covered by `failedWriteDoesNotLoseDraft` in the regression step below.
- **RF-5 → AC-5:** Discard one draft, or save/delete and acknowledge its result, while another exists; interrupt migration before write success. Only the discarded or acknowledged completed draft is removed; unacknowledged completion remains recoverable, old Bundle text survives failed migration and the other draft remains. Covered by `draftLifecycleIsScoped` in the regression step below.

---

## Task 1: Deliver UX-004

**Files and responsibilities:**

- Modify: `app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorViewModel.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorScreen.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt`
- Modify: `app/src/main/java/com/luma/lifeos/LumaApplication.kt`
- Modify: `core/domain/src/main/kotlin/com/luma/lifeos/domain/model/Mood.kt`
- Modify: `core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomMoodRepository.kt`
- Create: `core/domain/src/main/kotlin/com/luma/lifeos/domain/model/MoodNotePolicy.kt`
- Create: `app/src/main/java/com/luma/lifeos/ui/mood/MoodDraftStore.kt`
- Create: `app/src/main/java/com/luma/lifeos/ui/mood/FileMoodDraftStore.kt`
- Extend test: `app/src/test/java/com/luma/lifeos/ui/mood/MoodEditorTest.kt`
- Create test: `app/src/test/java/com/luma/lifeos/ui/mood/MoodDraftStoreTest.kt`
- Extend test: `app/src/androidTest/java/com/luma/lifeos/MoodCaptureTest.kt`
- Extend test: `core/domain/src/test/kotlin/com/luma/lifeos/domain/MoodTest.kt`
- Localized resources, only when copy changes: `app/src/main/res/values/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fr/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fa/strings.xml`

Keep changes in their current screen/state/repository responsibility. New files own only the interface or component named in the specification. If a listed existing file has changed since this plan, reconcile with the live code before editing; do not revert user changes to match the audit.

**Interfaces:**

- Consumes the current route/ViewModel/repository contracts in the files listed above; no new cross-task API is required.
- Produces: Proposed MoodNotePolicy.LIMIT = 5_000 (UTF-16 units); canAcceptMoodNote(previous: String, next: String): Boolean accepts next.length <= LIMIT, unchanged text, or strictly shorter legacy text. MoodDraftStore has suspend read(id: UUID): MoodDraftSnapshot?, write(id: UUID, snapshot: MoodDraftSnapshot): Unit, remove(id: UUID): Unit. MoodDraftSnapshot contains draft: MoodDraft, revision: Long, pendingDeleteId: EventId? = null, completion: MoodDraftCompletion? = null. MoodDraftCompletion(eventId: EventId, deleted: Boolean) records the durable terminal result. Load checks completion before other dependencies; save/delete retain this snapshot until caller acknowledgement, then remove it.
- Other consumers: migrate every caller affected by these interface changes; preserve callback target identity and existing readiness/busy guards.

- [x] **Step 1: Read the spec and confirm prerequisites.** Inspect each listed file and the working-tree diff; preserve unrelated edits. No implementation prerequisite is required. Classify missing tests as additions, not evidence of an already passing regression.

- [ ] **Step 2: Add the behavior regressions and manual checks.** Use the listed test suites, keeping state/repository assertions in JVM tests where provided and navigation/Compose assertions in device tests. Use the named cases, fixtures and assertions below; extend existing fixtures instead of introducing a parallel harness. For speech, gesture comfort and visual reachability, automate the semantics/bounds that can be checked and record actual manual device evidence under the same case ID. A test cannot certify TalkBack speech merely by finding a live-region node. For copy-only changes, extend existing UI assertions.

| Case | Regression / evidence case | Fixture / action | Assertions |
|---|---|---|---|
| AC-1 | `noteLimitRetainsAcceptedText` | Accept 5,000 UTF-16 units, then attempt 5,001. | 5,000 units remain unchanged and an inline limit error appears; a surrogate pair counts as two units. |
| AC-2 | `legacyNoteCanBeReadAndShortened` | Load 12,000 units, save unchanged or shorten to 10,000; try expanding to 12,001. | Read/copy and allowed edits succeed; growth is rejected without truncation. |
| AC-3 | `restorationUsesPrivateSnapshot` | Persist 100,000 legacy units, recreate the ViewModel with only draft ID metadata. | Full text restores; the new saved-state payload has no note string. |
| AC-4 | `failedWriteDoesNotLoseDraft` | The atomic write fails, or revision N finishes after an N+1 request. | Visible latest text is retained, Retry is offered and no older snapshot replaces a newer durable revision. |
| AC-5 | `draftLifecycleIsScoped` | Discard one draft, or save/delete and acknowledge its result, while another exists; interrupt migration before write success. | Only the discarded or acknowledged completed draft is removed; unacknowledged completion remains recoverable, old Bundle text survives failed migration and the other draft remains. |

- [ ] **Step 3: Establish the regression.** Run the relevant targeted command below before implementation. New-interface tests may initially fail to compile; existing-behavior tests must fail on the specified mismatch. Record the actual failure. For a runtime-risk finding, first exercise its constrained fixture; if it already passes, record that result and retain the reachability regression without claiming a reproduced failure.

```bash
./gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.ui.mood.MoodEditorTest' --tests 'com.luma.lifeos.ui.mood.MoodDraftStoreTest'
```

```bash
./gradlew :core:domain:test --tests 'com.luma.lifeos.domain.MoodTest'
```

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.MoodCaptureTest
```

Device commands require a connected, configured test device/emulator. Locale/font/window and TalkBack settings are deliberate validation inputs; the command alone does not configure or certify that matrix. Do not install dependencies or alter the execution environment as part of this documentation task.

- [x] **Step 4: Implement the owning contract.** Implement MoodNotePolicy and apply create versus previous-value update validation without making old stored records unreadable.

- [x] **Step 5: Connect the behavior.** Add versioned private atomic snapshots and inject the store through MoodEditorRoute; serialize revisions so older writes cannot overwrite newer accepted text.

- [ ] **Step 6: Exercise the complete flow.** Move note persistence out of SavedStateHandle, migrate old keys safely, add inline validation/storage feedback and safe-close protection.

- [ ] **Step 7: Verify the task.** Rerun the targeted commands above and require zero failures. Then run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` to catch all-call-site, resource and lint regressions. Exercise the relevant acceptance scenarios in EN/FR/FA, semantic light/dark, 100%/130%/200% font and narrow/landscape where layout or semantics changed. Record app/build revision, device/API, locale, font/window inputs and results; label missing checks pending. Expected outcome: AC-1 through AC-5 hold and affected compilation/lint checks pass.

- [ ] **Step 8: Review and integrate this task alone.** Inspect the diff against the spec and confirm every AC/RF has evidence. Stage only this task's hunks in existing modified files and its new files; do not stage the pre-existing dirty tree wholesale. If the execution workflow uses commits, commit with `fix: ux-004 mood note durability`. Update the index status only from actual evidence.

## Handoff

Deliver this task's code changes, five scenario results and remaining device limitations together. No implementation has been performed by generating this plan. Do not mark the finding fixed solely because a plan, test name or historical screenshot exists.

