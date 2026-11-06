# Memory Photo Viewing and Editing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement task-by-task. Steps use checkbox syntax. Follow the user's final-QA requirement: do not insert test authoring, Gradle, build/lint or emulator gates between implementation tasks.

**Goal:** Add original-backed memory photo viewing and crop/rotate before attaching a new photo, then validate both in one separate final QA task.

**Architecture:** Extend existing private photo storage with bounded display and immutable derived-image operations. Share photo rendering components, but opt in only at memory detail/editor call sites. Stage new selections in a dedicated memory photo session and transfer leases into the existing draft session only after confirmation.

**Tech Stack:** Existing Kotlin/JVM 17, Compose, coroutines, Android image APIs, Room and JUnit. No new dependencies.

**Spec:** [Memory photo viewing and basic editing](../specs/2026-11-06-memory-photo-viewing-editing-design.md)
**Final QA:** [MP-QA — separate final task](2026-11-06-memory-photo-viewing-editing-qa.md)

## Global constraints

- Keep Kotlin/JVM 17, Compose, coroutines and Room; minSdk 28, compileSdk 35 and targetSdk 35. Add no dependencies or permissions.
- Preserve existing photo limits: 20 attachments per event, 25 MiB per managed image, 40,000,000 source pixels and 640-pixel preview edge.
- Viewer/editor working images and transformed exports have an 8,000,000-pixel budget and maximum edge of 4096 pixels; never upscale smaller sources. Unchanged imports retain their original bytes and dimensions.
- Keep all image processing offline and private. Never overwrite the device source or any existing managed attachment.
- Supply English, French and Persian resources, RTL, theme support, TalkBack/keyboard alternatives and at least 48 dp controls.
- Run no tests, Gradle, lint, APK assembly or emulator/device tooling during implementation tasks. Test/fixture authoring and all execution belong to the separate final QA task.
- Preserve unrelated working-tree edits. Record implementation as source complete and unverified until final QA supplies evidence.

## Phase policy and status

This planning request creates documentation only. All boxes start unchecked. MP-I01–MP-I05 are implementation tasks and end in source review, not runtime acceptance. Record **SOURCE COMPLETE — UNVERIFIED** only when each task's source exists. Do not run tests or create fixtures during these tasks; any test-fake compatibility edits also belong to MP-QA. Source-review corrections are allowed throughout implementation.

MP-QA starts only after every implementation task is source complete. It owns test authoring, compile fixes, Gradle/emulator execution, defects and retests. No commit, push, APK installation or release is part of this documentation request. If later implementation uses commits, stage only task-owned changes; the inspected checkout already contains many unrelated edits.

## Review Focus

1. EXIF-oriented portrait or mirrored input with crop then rotation: saved visible pixels must match the preview (MP-I03; QA02).
2. Apply finishes before process death but attachment acknowledgment does not: recover one output ID, never duplicate it (MP-I03/I04; QA04).
3. Relock during source decode or transformation: stale pixels and late attachment results must not publish (MP-I01/I03/I04; QA05).
4. 40-megapixel/transparent/panoramic input or output over 25 MiB: obey budgets and preserve the original on failure (MP-I01/I03; QA02/06).
5. Viewer dismissal or editor cancellation while cleanup runs: release only the owning lease and keep committed/draft photos (MP-I01/I02/I04; QA04/06).

## Source map and proposed interfaces

Paths are relative to the repository. These roots expand to exact directories:

- **D:** `core/domain/src/main/kotlin/com/luma/lifeos/domain`
- **R:** `core/data/src/main/kotlin/com/luma/lifeos/data`
- **A:** `app/src/main/java/com/luma/lifeos`

Create `D/model/PhotoDisplay.kt` and `D/model/PhotoEdit.kt`; extend `D/repository/PhotoRepository.kt`. Create `R/media/AndroidPhotoTransform.kt`; extend `R/media/AndroidPhotoCodec.kt`, `R/media/ManagedPhotoFiles.kt` and `R/repository/RoomPhotoRepository.kt`. Create `A/lock/PhotoDraftAccessRegistry.kt` and extend `A/lock/ProtectedAggregateRepositories.kt`, `A/LumaApplication.kt`, `A/MainActivity.kt` and `A/ui/LifeOsApp.kt` for registry wiring.

Create `A/ui/photos/PhotoViewer.kt`, `PhotoViewerState.kt`, `PhotoEditScreen.kt`, `PhotoCropGeometry.kt`, `MemoryPhotoEditSession.kt` and `MemoryPhotoEditor.kt`. Modify `PhotoGallery.kt`, `PhotoDraftSession.kt`, `A/ui/timeline/EventDetailRoute.kt`, `A/ui/memory/MemoryEditorRoute.kt` and `MemoryEditorViewModel.kt`. Modify `MemoryEditorScreen.kt` only for parent-action gating/modal Back integration. Add copy to `app/src/main/res/values/strings.xml`, `values-fr/strings.xml` and `values-fa/strings.xml`.

### Contract ownership

MP-I01 defines these domain contracts; MP-I03 adds the transform method. Values below are proposed interfaces, not existing code:

```kotlin
object PhotoWorkingLimits { const val PIXELS = 8_000_000L; const val EDGE = 4096 }
data class PhotoDisplayRequest(
    val viewportWidth: Int, val viewportHeight: Int, val zoom: Float = 1f,
)
sealed interface PhotoReadScope {
    data object Saved : PhotoReadScope
    data class Draft(val leaseId: UUID) : PhotoReadScope
}
data class PhotoCrop(val left: Double, val top: Double, val right: Double, val bottom: Double)
data class PhotoEdit(val clockwiseQuarterTurns: Int, val crop: PhotoCrop)

// Additions to PhotoRepository; all existing methods stay compatible.
suspend fun display(photo: PhotoAttachment, request: PhotoDisplayRequest,
    scope: PhotoReadScope = PhotoReadScope.Saved): ByteArray?
suspend fun isRetainedBy(leaseId: UUID, photo: PhotoAttachment): Boolean
suspend fun transform(draftId: UUID, outputId: UUID,
    source: PhotoAttachment, edit: PhotoEdit): PhotoAttachment
```

Reject nonpositive viewport sizes, nonfinite zoom or zoom outside `1f..4f`; normalize crop validation as the spec requires. `PhotoEdit` coordinates describe the EXIF-normalized image after the declared clockwise quarter turns. Identity is zero turns and full bounds. Encoded display bytes are bounded PNG; output metadata describes the actual encoded result. A `Draft(leaseId)` identifies a lease but is not itself an authorization capability.

`PhotoDraftAccessRegistry.open(leaseId: UUID, eventId: EventId?): AutoCloseable` registers a scope only for a currently gated, authorized memory editor/viewer. The registry verifies MEMORIES and any parent event's required sections against `ProtectedContentGateway`; it supplies no blanket access for missing/unknown classification. It exposes `suspend fun permits(leaseId: UUID, photo: PhotoAttachment): Boolean` to the protected facade. Registration requires an active authorized route; lock epoch changes clear registrations. The facade additionally verifies actual lease membership and existing-photo owner authorization before and after work. Never persist a registry handle or trust a restored UUID without route reauthorization.

Register the main memory draft owner (`captureId.value`, exposed by the ViewModel as `photoDraftId: UUID`) and any staging/viewer lease before its first file operation. The route supplies an authorized scope factory to the coordinator so registration happens synchronously before import, rather than waiting for UI recomposition. Scoped `import` and `retainDraft` must use MEMORIES plus parent-event classification; accept unowned files only when imported/retained under that live editor's registered leases. Preserve the current conservative unclassified-file policy for all other callers. This also allows safe staging-to-main-draft lease transfer when app lock is enabled.

## MP-I01 — Original-backed display and private read contracts

**Requirements:** MP-R01/R02/R05. **Depends on:** none.
**Status:** SOURCE COMPLETE — UNVERIFIED.

**Files:** Domain display model/repository, `AndroidPhotoCodec.kt`, `ManagedPhotoFiles.kt`, `RoomPhotoRepository.kt`, protected facade/registry and registry wiring files listed above.

**Consumes:** Existing private import, retain/release draft, shared photo mutex and gateway generations.
**Produces:** `display`, `isRetainedBy`, validated display/scope types and active-route draft access registration. MP-I02/I04 consume these without raw storage access.

- [x] Define display budgets, request validation and Saved/Draft scopes. Preserve the 640-pixel `preview()` behavior.
- [x] Add `ManagedPhotoFiles.isRetainedBy(leaseId, photo)` with canonical identity/path matching; missing/unreadable lease returns false. Add bounded source-backed display decoding to `AndroidPhotoCodec`, honoring EXIF and request aspect ratio without upscaling. Move file/decode work off main and release temporary bitmaps.
- [x] Implement repository display under the shared mutex. A Draft read must prove lease membership; resolve only canonical managed files. Bound both decoded dimensions and encoded output; missing/corrupt reads return null, coroutine cancellation propagates.
- [x] Implement the route registry and explicit `ProtectedPhotoRepository` overrides for display/membership and scoped import/retain behavior; protect results before/after asynchronous reads. Register viewer/editor leases only after authorization and before file work. Wire one registry instance from `LumaApplication` through the existing app/route dependency chain.
- [x] Source-review all new sensitive paths for delegated bypasses, saved owner classification, staged-photo authorization and stale-generation returns. Hand off MP-I01 as source complete and unverified; run no build/tests.

## MP-I02 — Full-screen memory photo viewer

**Requirements:** MP-R01/R02/R06. **Depends on:** MP-I01.

**Files:** Create `PhotoViewer.kt` and `PhotoViewerState.kt`; modify `PhotoGallery.kt`, `EventDetailRoute.kt` and viewer copy in all three locale files. Final editor wiring is MP-I04.

**Interfaces:** Extend `PhotoGallery(..., onRemove: ((UUID) -> Unit)? = null, onOpen: ((UUID) -> Unit)? = null)`. Add `PhotoViewer(repository: PhotoRepository, photos: List<PhotoAttachment>, initialId: UUID, readScope: PhotoReadScope, onDismiss: () -> Unit)` as a composable. Viewer state stores selected UUID and compact zoom/pan state, never image bytes.

- [ ] Add a separate enabled photo-open target and preserve Remove behavior. Keep all existing callers source-compatible; opt memory details in with `record.memory != null` or standalone MEMORY classification. Do not enable unrelated gallery callers implicitly.
- [ ] Implement fit, selected-ID navigation, 1×–4× zoom, bounded pan, double-tap and named controls/actions. Cancel/discard old image requests and clear previous imagery before changing position. Expose `photo_viewer`, `photo_viewer_close`, `photo_viewer_previous`, `photo_viewer_next`, `photo_viewer_zoom_in`, `photo_viewer_zoom_out`, `photo_viewer_reset`, and `photo_open_<id>` tags.
- [ ] Acquire a distinct viewer lease before display. Register its scope where needed; retain it across configuration recreation, reconcile lease IDs after process recreation, and release only that lease on actual dismissal/route disposal. Handle attachment deletion/removal by closing unavailable selection safely.
- [ ] Implement localized progress, unavailable/Retry/Close, inset handling and focus restoration. Ensure any Dialog window inherits screenshot protection; clear the surface immediately on lock/revocation and require a fresh authorized load.
- [ ] Wire memory detail selection without changing its scroll/back stack or library-card layout. Source-review decoding, accessibility and lease ownership; hand off unverified source.

## MP-I03 — Immutable crop/rotate processing and safe publication

**Requirements:** MP-R03/R04/R05. **Depends on:** MP-I01.

**Files:** Create `D/model/PhotoEdit.kt`, `R/media/AndroidPhotoTransform.kt`, `A/ui/photos/PhotoCropGeometry.kt`; extend `PhotoRepository.kt`, `RoomPhotoRepository.kt`, `ManagedPhotoFiles.kt` and `ProtectedAggregateRepositories.kt`.

**Consumes:** MP-I01 scopes, budgets, canonical managed-file access and draft registration.
**Produces:** `transform` above; domain functions `rotateCropClockwise(crop: PhotoCrop): PhotoCrop` and `cropPixelBounds(crop: PhotoCrop, width: Int, height: Int): PhotoPixelBounds` in `D/model/PhotoEdit.kt`. `PhotoPixelBounds` contains validated integer left/top/right/bottom bounds. The UI-only `PhotoCropGeometry.kt` maps handles/viewport positions to those shared normalized coordinates; the data module never imports app UI code.

- [ ] Define edit validation and clockwise crop mapping: `(left, top, right, bottom)` becomes `(1-bottom, left, 1-top, right)`. Rotating increments quarter turns modulo four. Pixel bounds floor left/top, ceil right/bottom, clamp to oriented dimensions and remain nonempty. Reset uses full bounds/zero turns.
- [ ] Implement EXIF normalization → declared rotation → crop → bounded export. Map crop back to the decoded source region correctly; do not crop a 640-pixel thumbnail or allocate an unbounded 40-megapixel intermediate. Recycle source/intermediate bitmaps as ownership changes. Preserve alpha in PNG output.
- [ ] Add a managed derived-file publication operation with a caller-chosen fresh output ID: retain before write, write `.part`, fsync, inspect size/MIME/dimensions, atomically publish `.img`, clean partial output on failure. Reuse completed output only for the exact immutable operation ID/payload. Never overwrite source or change an existing attachment's file identity.
- [ ] Implement `transform` and its explicit protected override. Require source membership in an active draft scope, authorize any existing owner and verify the current epoch before returning/publishing into app state. Check cancellation between expensive stages; stale completions cannot attach.
- [ ] Keep identity handling in the coordinator: it attaches the staged source unchanged instead of calling transform. Source-review orientation, maximum dimensions, size failure, retry identities and cleanup interactions; hand off unverified source.

## MP-I04 — Stage and edit selections in create/edit memory

**Requirements:** MP-R01/R03/R04/R05/R06. **Depends on:** MP-I01/I02/I03.

**Files:** Create `MemoryPhotoEditSession.kt`, `MemoryPhotoEditor.kt`, `PhotoEditScreen.kt`; modify `PhotoDraftSession.kt`, `MemoryEditorViewModel.kt`, `MemoryEditorRoute.kt`, relevant `MemoryEditorScreen.kt` parent-action gating and localized strings.

**Interfaces:** Add `suspend fun PhotoDraftSession.attachImported(photo: PhotoAttachment)` with idempotent same-ID handling. It retains the complete new draft list before updating it and enforces `PhotoLimits.COUNT`. Add `MemoryPhotoEditSession` commands `select(source: String)`, `setCrop(crop: PhotoCrop)`, `rotateClockwise()`, `reset()`, `confirm(keepOriginal: Boolean)`, `cancel()` and `restore()`, exposing a `StateFlow<MemoryPhotoEditState>`.

`MemoryPhotoEditState` is a sealed state: Idle, Importing, Editing, Applying or Failed. Non-Idle states carry the operation ID, staging lease ID and source/output IDs/references available at that stage; Editing/Failed carry `PhotoEdit`. Failed also carries retry stage and localized-error category. ViewModel exports state and delegates commands; UI owns neither persistence nor lease transfer.

- [ ] Implement persisted staging identity before import; import once into the staging lease and show editing only after validation. Keep pending selection outside `MemoryDraft.photos`; use managed files after import. On failed initial import, Retry may reuse the URI; Select again handles lost access. Enforce capacity before selection and attachment.
- [ ] Implement immutable Apply payload capture before transform. Same-payload retries reuse output ID; a changed edit invalidates the old operation and allocates a new ID. Keep original and identity Apply select the staged source. Guard all completions with operation and authorization generations.
- [ ] Implement `attachImported`: recheck can-commit state and capacity, retain main-draft references first, then persist/update its list once. If the ID is already present, reconcile without duplicating. Release staging only after draft persistence succeeds; errors retain recoverable work. Save/discard continue using the existing draft session.
- [ ] Restore from IDs/references and compact edit state. Detect already attached results; recover completed outputs without rerunning transformation, but require an explicit confirmation after restoring an unapplied edit. Reauthorize/register restored staging/main-draft leases from the route. Cancel invalidates operation generation before cleanup and never releases the main editor lease.
- [ ] Build `PhotoEditScreen` with crop handles, edge percentage controls, rotate/reset, Apply/Keep original/Cancel, progress/retry and resolution-reduction explanation. Keep crop math shared with export. Add tags `memory_photo_edit`, `photo_edit_apply`, `photo_edit_keep_original`, `photo_edit_cancel`, `photo_edit_rotate`, `photo_edit_reset` and `photo_crop_<edge>`.
- [ ] Introduce the memory-specific picker host `MemoryPhotoEditor`; leave shared `PhotoEditor` behavior unchanged for unrelated callers. Wire both new and existing memories, block competing parent actions while active, and make modal Back close the photo surface first. Wire editor attachment viewing with a distinct viewer lease; opening it must not dirty the draft.
- [ ] Source-review failure transitions, attach/cleanup ordering, saved-state contents and create/edit parity. Hand off unverified source with no test or Gradle run.

## MP-I05 — Integration and source handoff

**Requirements:** All MP-R01–MP-R06. **Depends on:** MP-I01–MP-I04.

**Files:** Task-owned production files above; implementation status in this plan. No test/fixture files in this task.

- [ ] Inspect all new repository methods, protected facade overrides and route-scoped registrations for consistent signatures and cancellation handling. Inspect unrelated callers for preserved defaults; confirm no Room/backup migration or manifest dependency/permission changes were introduced.
- [ ] Inspect EN/FR/FA resource keys/placeholders, RTL controls, 48 dp targets, focus labels, screenshot policy and reachable Back/Cancel/Retry. This is source review, not emulator/accessibility certification.
- [ ] Inspect final changes against each acceptance scenario and record any known unresolved issue for MP-QA. Whitespace/link/source-reference checks are allowed; no Gradle or device tooling.
- [ ] Record MP-I01–MP-I05 as **SOURCE COMPLETE — UNVERIFIED** only when all code is present. Hand off the candidate source identity/patch and proceed to the single final task below; do not ask for per-task QA handoffs.

## MP-QA — Separate final task: tests, Gradle and emulator acceptance

**Depends on:** MP-I01–MP-I05 source complete. **Status:** NOT STARTED.

- [ ] Execute the [separate final QA plan](2026-11-06-memory-photo-viewing-editing-qa.md) in full: author tests/fixtures, run Gradle/build/lint, exercise emulator/native flows, fix defects within QA and retest the exact candidate.
- [ ] Map MP-AC01–MP-AC10 to evidence. Record unexecuted/blocked checks honestly; source completion alone cannot close this task.

This is the final task in the delivery sequence. No implementation, QA or release has been performed by writing this plan.
