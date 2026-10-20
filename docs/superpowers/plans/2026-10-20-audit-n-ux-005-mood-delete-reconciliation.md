# UX-005 — Interrupted Mood deletion has no reconciliation path Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use superpowers:subagent-driven-development only if the user selects delegation. Steps use checkbox syntax for tracking. This documentation request does not start implementation or delegate work.

**Goal:** Resolve interrupted Mood deletion to an honest deleted, still-present or unavailable outcome.

**Architecture:** Record pendingDeleteId in the durable Mood draft snapshot before repository.delete. On restoration reconcile that intent before emotion preparation or normal record loading.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose/Material 3, lifecycle StateFlow and the existing Android/Room modules; existing dependencies.

**Spec:** [UX-005 specification](../specs/2026-10-20-audit-n-ux-005-mood-delete-reconciliation-design.md)

**Status:** Draft plan. All implementation, test and commit steps remain unchecked. Relative effort: Medium.

## Global Constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies unless a task explicitly requires a reviewed exception.
- All changed user-facing copy must exist in EN/FR/FA with matching resource names and numbered placeholder signatures; preserve RTL and intended Gregorian display/storage behavior.
- Keep semantic light/dark palettes, at least 48 dp interactive targets, and reachability at 100%/130%/200% font, narrow portrait and short landscape.
- Preserve offline/private storage, existing consent boundaries, stable record/command IDs, original retry payloads and Undo protections.
- Never truncate accepted personal content, infer write failure means no commit, or treat an unavailable privacy mode as known.
- Only implement this task and its declared prerequisites; preserve unrelated working-tree changes and existing navigation/draft state.

## Review Focus

These five concrete failure modes are pinned to the regression cases owned by this task. The assertions below are requirements for tests to add, not claims that those tests exist or currently pass.

- **RF-1 → AC-1:** Delete commits but completion delivery is lost; recreate with intent. The editor completes deleted without LOAD error or a second delete. Covered by `committedDeleteIsReconciled` in the regression step below.
- **RF-2 → AC-2:** Draft-store intent persistence throws. Repository.delete is never called and note content stays available. Covered by `intentFailurePreventsDelete` in the regression step below.
- **RF-3 → AC-3:** Restore matching intent while repository.get returns the record. Show retryable deletion state; no automatic second delete occurs. Covered by `stillPresentRequiresExplicitRetry` in the regression step below.
- **RF-4 → AC-4:** Load a missing record without deletion intent, or with intent for another ID. Show missing source; do not report successful deletion. Covered by `unrelatedMissingIsNotDeleted` in the regression step below.
- **RF-5 → AC-5:** Reconciliation read throws, then retry returns null; emotions also fail. Unknown outcome is recoverable; successful null reconciliation completes deletion independently of emotions. Covered by `statusReadFailureRemainsUnknown` in the regression step below.

---

## Task 1: Deliver UX-005

**Files and responsibilities:**

- Modify: `app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorViewModel.kt`
- Modify (created by UX-004): `app/src/main/java/com/luma/lifeos/ui/mood/MoodDraftStore.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorScreen.kt`
- Extend test: `app/src/test/java/com/luma/lifeos/ui/mood/MoodEditorTest.kt`
- Extend test: `app/src/androidTest/java/com/luma/lifeos/MoodCaptureTest.kt`
- Localized resources, only when copy changes: `app/src/main/res/values/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fr/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fa/strings.xml`

Keep changes in their current screen/state/repository responsibility. New files own only the interface or component named in the specification. If a listed existing file has changed since this plan, reconcile with the live code before editing; do not revert user changes to match the audit.

**Interfaces:**

- Consumes [UX-004](./2026-10-20-audit-n-ux-004-mood-note-durability.md): Proposed MoodNotePolicy.LIMIT = 5_000 (UTF-16 units); canAcceptMoodNote(previous: String, next: String): Boolean accepts next.length <= LIMIT, unchanged text, or strictly shorter legacy text. MoodDraftStore has suspend read(id: UUID): MoodDraftSnapshot?, write(id: UUID, snapshot: MoodDraftSnapshot): Unit, remove(id: UUID): Unit. MoodDraftSnapshot contains draft: MoodDraft, revision: Long, pendingDeleteId: EventId? = null, completion: MoodDraftCompletion? = null. MoodDraftCompletion(eventId: EventId, deleted: Boolean) records the durable terminal result. Load checks completion before other dependencies; save/delete retain this snapshot until caller acknowledgement, then remove it.
- Produces: Reuse UX-004 MoodDraftSnapshot.pendingDeleteId: EventId?. MoodEditorViewModel.delete(): Unit durably writes intent before deletion; load(): Unit checks pending intent via MoodRepository.get(id) before prepareEmotions(). Completion keeps existing completed/deleted state for UX-010 to translate.
- Other consumers: migrate every caller affected by these interface changes; preserve callback target identity and existing readiness/busy guards.

- [ ] **Step 1: Read the spec and confirm prerequisites.** Inspect each listed file and the working-tree diff; preserve unrelated edits. Complete UX-004 first and read its declared interfaces. Classify missing tests as additions, not evidence of an already passing regression.

- [ ] **Step 2: Add the behavior regressions and manual checks.** Use the listed test suites, keeping state/repository assertions in JVM tests where provided and navigation/Compose assertions in device tests. Use the named cases, fixtures and assertions below; extend existing fixtures instead of introducing a parallel harness. For speech, gesture comfort and visual reachability, automate the semantics/bounds that can be checked and record actual manual device evidence under the same case ID. A test cannot certify TalkBack speech merely by finding a live-region node. For copy-only changes, extend existing UI assertions.

| Case | Regression / evidence case | Fixture / action | Assertions |
|---|---|---|---|
| AC-1 | `committedDeleteIsReconciled` | Delete commits but completion delivery is lost; recreate with intent. | The editor completes deleted without LOAD error or a second delete. |
| AC-2 | `intentFailurePreventsDelete` | Draft-store intent persistence throws. | Repository.delete is never called and note content stays available. |
| AC-3 | `stillPresentRequiresExplicitRetry` | Restore matching intent while repository.get returns the record. | Show retryable deletion state; no automatic second delete occurs. |
| AC-4 | `unrelatedMissingIsNotDeleted` | Load a missing record without deletion intent, or with intent for another ID. | Show missing source; do not report successful deletion. |
| AC-5 | `statusReadFailureRemainsUnknown` | Reconciliation read throws, then retry returns null; emotions also fail. | Unknown outcome is recoverable; successful null reconciliation completes deletion independently of emotions. |

- [ ] **Step 3: Establish the regression.** Run the relevant targeted command below before implementation. New-interface tests may initially fail to compile; existing-behavior tests must fail on the specified mismatch. Record the actual failure. For a runtime-risk finding, first exercise its constrained fixture; if it already passes, record that result and retain the reachability regression without claiming a reproduced failure.

```bash
./gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.ui.mood.MoodEditorTest'
```

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.MoodCaptureTest
```

Device commands require a connected, configured test device/emulator. Locale/font/window and TalkBack settings are deliberate validation inputs; the command alone does not configure or certify that matrix. Do not install dependencies or alter the execution environment as part of this documentation task.

- [ ] **Step 4: Implement the owning contract.** Persist deletion intent to the UX-004 store and separate intent-write, delete-write and existence-check failures.

- [ ] **Step 5: Connect the behavior.** Reorder restoration to reconcile pending deletion before loading emotions or rejecting a missing source.

- [ ] **Step 6: Exercise the complete flow.** Expose still-present/unknown/missing recovery states and keep destructive Retry explicit.

- [ ] **Step 7: Verify the task.** Rerun the targeted commands above and require zero failures. Then run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` to catch all-call-site, resource and lint regressions. Exercise the relevant acceptance scenarios in EN/FR/FA, semantic light/dark, 100%/130%/200% font and narrow/landscape where layout or semantics changed. Record app/build revision, device/API, locale, font/window inputs and results; label missing checks pending. Expected outcome: AC-1 through AC-5 hold and affected compilation/lint checks pass.

- [ ] **Step 8: Review and integrate this task alone.** Inspect the diff against the spec and confirm every AC/RF has evidence. Stage only this task's hunks in existing modified files and its new files; do not stage the pre-existing dirty tree wholesale. If the execution workflow uses commits, commit with `fix: ux-005 mood delete reconciliation`. Update the index status only from actual evidence.

## Handoff

Deliver this task's code changes, five scenario results and remaining device limitations together. No implementation has been performed by generating this plan. Do not mark the finding fixed solely because a plan, test name or historical screenshot exists.

