# UX-028 — Attached images cannot be inspected beyond their previews Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use superpowers:subagent-driven-development only if the user selects delegation. Steps use checkbox syntax for tracking. This documentation request does not start implementation or delegate work.

**Goal:** Offer an optional private full-screen photo viewer for details that previews cannot reveal.

**Architecture:** Keep existing 640-pixel previews for lists and add a bounded higher-resolution display path through PhotoRepository. Use a shared full-screen Compose viewer that preserves draft leases and never exposes private file paths.

**Tech Stack:** Kotlin/JVM 17, Jetpack Compose/Material 3, lifecycle StateFlow and the existing Android/Room modules; existing dependencies.

**Spec:** [UX-028 specification](../specs/2026-10-20-audit-n-ux-028-optional-photo-viewer-design.md)

**Status:** Draft plan. All implementation, test and commit steps remain unchecked. Relative effort: High.

## Global Constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies unless a task explicitly requires a reviewed exception.
- All changed user-facing copy must exist in EN/FR/FA with matching resource names and numbered placeholder signatures; preserve RTL and intended Gregorian display/storage behavior.
- Keep semantic light/dark palettes, at least 48 dp interactive targets, and reachability at 100%/130%/200% font, narrow portrait and short landscape.
- Preserve offline/private storage, existing consent boundaries, stable record/command IDs, original retry payloads and Undo protections.
- Never truncate accepted personal content, infer write failure means no commit, or treat an unavailable privacy mode as known.
- Only implement this task and its declared prerequisites; preserve unrelated working-tree changes and existing navigation/draft state.

## Review Focus

These five concrete failure modes are pinned to the regression cases owned by this task. The assertions below are requirements for tests to add, not claims that those tests exist or currently pass.

- **RF-1 → AC-1:** Tap the third of four attachments. Viewer opens the third ID and reports position 3 of 4 without exporting a path/URI. Covered by `selectedPhotoOpensPrivately` in the regression step below.
- **RF-2 → AC-2:** Open a large portrait/panorama and repeatedly zoom. Source decode stays within 8,000,000 pixels; zoom remains 1x–4x and obsolete decodes are cancelled. Covered by `decodeAndZoomStayBounded` in the regression step below.
- **RF-3 → AC-3:** Use TalkBack/keyboard for zoom, pan/navigation, reset and Close. Each function is named and reachable without relying only on pinch/swipe. Covered by `controlsWorkWithoutGestures` in the regression step below.
- **RF-4 → AC-4:** Original becomes unavailable/corrupt during loading. Retry and Close are usable, stale imagery is cleared and the editor draft stays intact. Covered by `missingImageCanRecover` in the regression step below.
- **RF-5 → AC-5:** Rotate, switch images and dismiss while cleanup runs. Selected ID survives; viewer lease releases on close without dropping editor leases or deleting referenced media. Covered by `leaseAndLifecycleAreScoped` in the regression step below.

---

## Task 1: Deliver UX-028

**Files and responsibilities:**

- Modify: `app/src/main/java/com/luma/lifeos/ui/photos/PhotoGallery.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/photos/PhotoDraftSession.kt`
- Modify: `core/domain/src/main/kotlin/com/luma/lifeos/domain/repository/PhotoRepository.kt`
- Modify: `core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomPhotoRepository.kt`
- Modify: `core/data/src/main/kotlin/com/luma/lifeos/data/media/AndroidPhotoCodec.kt`
- Modify: `core/data/src/main/kotlin/com/luma/lifeos/data/media/ManagedPhotoFiles.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/journal/JournalDetailRoute.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/journal/JournalEditorScreen.kt`
- Modify: `app/src/main/java/com/luma/lifeos/ui/memory/MemoryEditorScreen.kt`
- Create: `app/src/main/java/com/luma/lifeos/ui/photos/PhotoViewer.kt`
- Create: `core/domain/src/main/kotlin/com/luma/lifeos/domain/model/PhotoDisplayRequest.kt`
- Create test: `app/src/test/java/com/luma/lifeos/ui/photos/PhotoViewerStateTest.kt`
- Extend test: `app/src/androidTest/java/com/luma/lifeos/PhotoCaptureTest.kt`
- Extend test: `core/data/src/androidTest/kotlin/com/luma/lifeos/data/PhotoPersistenceTest.kt`
- Localized resources, only when copy changes: `app/src/main/res/values/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fr/strings.xml`
- Localized resources, only when copy changes: `app/src/main/res/values-fa/strings.xml`

Keep changes in their current screen/state/repository responsibility. New files own only the interface or component named in the specification. If a listed existing file has changed since this plan, reconcile with the live code before editing; do not revert user changes to match the audit.

**Interfaces:**

- Consumes the current route/ViewModel/repository contracts in the files listed above; no new cross-task API is required.
- Produces: Optional proposal: PhotoRepository adds suspend fun display(photo: PhotoAttachment, request: PhotoDisplayRequest): ByteArray?; PhotoDisplayRequest(viewportWidth: Int, viewportHeight: Int, maxPixels: Long = 8_000_000L) rejects nonpositive dimensions and maxPixels outside 1..8_000_000. @Composable PhotoViewer(photos: List<PhotoAttachment>, initialId: UUID, repository: PhotoRepository, onDismiss: () -> Unit): Unit owns display loading and zoom state.
- Other consumers: migrate every caller affected by these interface changes; preserve callback target identity and existing readiness/busy guards.

- [ ] **Step 1: Read the spec and confirm prerequisites.** Inspect each listed file and the working-tree diff; preserve unrelated edits. No implementation prerequisite is required. Classify missing tests as additions, not evidence of an already passing regression.

- [ ] **Step 2: Add the behavior regressions and manual checks.** Use the listed test suites, keeping state/repository assertions in JVM tests where provided and navigation/Compose assertions in device tests. Use the named cases, fixtures and assertions below; extend existing fixtures instead of introducing a parallel harness. For speech, gesture comfort and visual reachability, automate the semantics/bounds that can be checked and record actual manual device evidence under the same case ID. A test cannot certify TalkBack speech merely by finding a live-region node. For copy-only changes, extend existing UI assertions.

| Case | Regression / evidence case | Fixture / action | Assertions |
|---|---|---|---|
| AC-1 | `selectedPhotoOpensPrivately` | Tap the third of four attachments. | Viewer opens the third ID and reports position 3 of 4 without exporting a path/URI. |
| AC-2 | `decodeAndZoomStayBounded` | Open a large portrait/panorama and repeatedly zoom. | Source decode stays within 8,000,000 pixels; zoom remains 1x–4x and obsolete decodes are cancelled. |
| AC-3 | `controlsWorkWithoutGestures` | Use TalkBack/keyboard for zoom, pan/navigation, reset and Close. | Each function is named and reachable without relying only on pinch/swipe. |
| AC-4 | `missingImageCanRecover` | Original becomes unavailable/corrupt during loading. | Retry and Close are usable, stale imagery is cleared and the editor draft stays intact. |
| AC-5 | `leaseAndLifecycleAreScoped` | Rotate, switch images and dismiss while cleanup runs. | Selected ID survives; viewer lease releases on close without dropping editor leases or deleting referenced media. |

- [ ] **Step 3: Establish the regression.** Run the relevant targeted command below before implementation. New-interface tests may initially fail to compile; existing-behavior tests must fail on the specified mismatch. Record the actual failure. For a runtime-risk finding, first exercise its constrained fixture; if it already passes, record that result and retain the reachability regression without claiming a reproduced failure.

```bash
./gradlew :app:testDebugUnitTest --tests 'com.luma.lifeos.ui.photos.PhotoViewerStateTest'
```

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.PhotoCaptureTest
```

```bash
./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.data.PhotoPersistenceTest
```

Device commands require a connected, configured test device/emulator. Locale/font/window and TalkBack settings are deliberate validation inputs; the command alone does not configure or certify that matrix. Do not install dependencies or alter the execution environment as part of this documentation task.

- [ ] **Step 4: Implement the owning contract.** After separate product selection of this optional task, add the bounded display request and private repository/codec path, updating every PhotoRepository fake/implementation.

- [ ] **Step 5: Connect the behavior.** Build PhotoViewer with bounded decoding, cancellable loading, viewer lease ownership, zoom/pan and labelled non-gesture controls.

- [ ] **Step 6: Exercise the complete flow.** Connect all Journal/Memory photo previews/details and verify process/lifecycle restoration, missing images, memory bounds and accessibility.

- [ ] **Step 7: Verify the task.** Rerun the targeted commands above and require zero failures. Then run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` to catch all-call-site, resource and lint regressions. Exercise the relevant acceptance scenarios in EN/FR/FA, semantic light/dark, 100%/130%/200% font and narrow/landscape where layout or semantics changed. Record app/build revision, device/API, locale, font/window inputs and results; label missing checks pending. Expected outcome: AC-1 through AC-5 hold and affected compilation/lint checks pass.

- [ ] **Step 8: Review and integrate this task alone.** Inspect the diff against the spec and confirm every AC/RF has evidence. Stage only this task's hunks in existing modified files and its new files; do not stage the pre-existing dirty tree wholesale. If the execution workflow uses commits, commit with `feat: ux-028 optional photo viewer`. Update the index status only from actual evidence.

## Handoff

Deliver this task's code changes, five scenario results and remaining device limitations together. No implementation has been performed by generating this plan. This optional P3 plan needs separate product selection and must not block required audit corrections.

