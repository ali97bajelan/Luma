# Memory photo viewing and basic editing

**Date:** 6 November 2026
**Status:** Draft for review. Documentation only; no implementation or runtime validation performed.
**Implementation:** [Implementation plan](../plans/2026-11-06-memory-photo-viewing-editing.md)
**Final task:** [Separate QA plan](../plans/2026-11-06-memory-photo-viewing-editing-qa.md)

## Intent

Let users inspect a memory's photos beyond the small preview, and crop or rotate a newly selected photo before attaching it while creating or editing a memory.

The user explicitly requires QA, including emulator and Gradle tests, to be the final task and separate from implementation. This applies across both features: finish all implementation tasks before starting QA.

## Current behavior and constraints

- More → Memories opens `MemoryLibraryRoute`; selecting a record opens `EventDetailRoute`. The library itself deliberately has no photo thumbnails.
- `PhotoGallery.kt` contains both `PhotoEditor` and `PhotoGallery`. Photo selection immediately calls `onImport`; previews are not clickable and display at 240 dp high.
- `PhotoRepository.preview()` and `AndroidPhotoCodec` produce orientation-correct previews with a maximum edge of 640 pixels. Enlarging these bytes cannot recover original detail.
- `PhotoDraftSession` imports private files, persists pending identities, retains draft leases, and releases them after save/discard. `MemoryEditorViewModel` already uses this session.
- `ManagedPhotoFiles` publishes validated files through temporary files and atomic moves. `RoomPhotoRepository` serializes file operations and cleanup with the shared photo mutex. Event/photo writes do not permit changing the path behind an existing attachment ID.
- `ProtectedPhotoRepository` checks authorization before and after preview reads. Its delegated interface means every new sensitive method needs an explicit protection override.
- The app currently uses Kotlin/JVM 17, Compose, coroutines and Room; Android minSdk 28, compileSdk/targetSdk 35. Its app variant is `productionDebug`, with application ID `com.luma.lifeos.qa`.

The older [optional viewer draft](2026-10-20-audit-n-ux-028-optional-photo-viewer-design.md) is background only. This document defines this requested scope and the final-QA workflow; it does not authorize that older plan's broader rollout to every Journal/Mood surface.

## Proposed decisions and approaches

These are planning defaults, not user-approved product decisions.

1. **Recommended: private Compose viewer and editor using Android image APIs.** Reuse managed storage and leases, add a higher-resolution read path, and keep crop/rotate inside Luma. This gives consistent offline behavior and avoids a new dependency, but requires careful crop geometry, decoding and lifecycle handling.
2. **Use an image editor/viewer library.** Potentially less gesture/UI work, but introduces a dependency, API integration and licensing/maintenance review. Not selected for this draft.
3. **Launch an external editor/viewer.** Requires exporting access to private files and depends on installed applications and their return behavior. Does not fit the existing private, self-contained flow.

“Original size” is interpreted as full-screen viewing backed by the actual saved image, preserving aspect ratio and allowing zoom/pan. It does not mean allocating every pixel of a maximum-size image at once or restoring detail removed by a previous crop. The viewer opens the saved result for edited photos.

Basic editing includes freeform crop, clockwise 90° rotation, reset and keeping the selected photo unchanged. Editing already attached photos is outside this iteration; the required editing opportunity is when adding a photo in either the create or edit memory flow.

## Global constraints

- Keep Kotlin/JVM 17, Compose, coroutines and Room; minSdk 28, compileSdk 35 and targetSdk 35. Add no dependencies or permissions.
- Preserve existing photo limits: 20 attachments per event, 25 MiB per managed image, 40,000,000 source pixels and 640-pixel preview edge.
- Viewer/editor working images and transformed exports have an 8,000,000-pixel budget and maximum edge of 4096 pixels; never upscale smaller sources. Unchanged imports retain their original bytes and dimensions.
- Keep all image processing offline and private. Never overwrite the device source or any existing managed attachment.
- Supply English, French and Persian resources, RTL, theme support, TalkBack/keyboard alternatives and at least 48 dp controls.
- Run no tests, Gradle, lint, APK assembly or emulator/device tooling during implementation tasks. Test/fixture authoring and all execution belong to the separate final QA task.
- Preserve unrelated working-tree edits. Record implementation as source complete and unverified until final QA supplies evidence.

The export cap is an explicit proposed quality tradeoff to bound memory use, including for a rotated 40-megapixel input. Show a short localized explanation when edits reduce resolution. Users can choose **Keep original** to retain the imported bytes instead. Encoding must never silently lower the cap further to hide an output-size failure.

## Requirements

### MP-R01 — Open a memory photo

- In memory event details, tapping a loaded photo preview opens that exact attachment in an in-app full-screen viewer. Support standalone memories and memory-marked source events displayed through the same detail route.
- Memory editor attachment previews also open read-only viewing; Remove remains a separate action. The viewer must not change the draft or mark it dirty.
- Reuse `PhotoGallery` with an optional open callback. Enable it explicitly at the memory detail/editor call sites; preserve other callers' existing behavior.
- The library continues opening details through its existing reading card. This feature adds no thumbnails or additional actions to library cards.
- The full-screen surface uses the available window, handles system insets, and initially fits the entire image without cropping or distortion. Show a localized position such as “Photo 2 of 3”.
- Provide Close, system Back, Previous and Next, disabling boundary navigation. Closing returns to the same detail/editor and scroll/draft context.

### MP-R02 — Inspect actual saved image detail

- Decode from the managed image, never from `preview()` bytes. Keep the existing preview path for galleries.
- Provide pinch zoom, pan, double-tap fit/2×, and named Zoom in, Zoom out, Reset and directional pan controls/actions. Zoom ranges from 1× fit to 4× fit; pan stays within image bounds. Use 25% viewport increments for accessible pan actions.
- Request more source detail when zooming, bounded by the global working-image limits. Repeated zoom beyond the decode cap magnifies the bounded image; it cannot promise native 1:1 detail for every 40-megapixel source.
- Decode off the main thread, cancel or discard obsolete requests, and display only the newest attachment/request result. Release replaced bitmaps and clear imagery on authorization loss or dismissal.
- Give the viewer its own lease. Acquire before reading and release only that lease on dismissal; do not replace or release an editor's lease. Rotation/recreation must not accumulate abandoned viewer leases.
- Missing/corrupt/unmanaged files show a localized error with Retry and Close. A failed read must never leave the previous photo visible under a new photo number.

### MP-R03 — Edit a newly selected photo

Flow: **Add photo → system image picker → copy to private staging → editor → Apply / Keep original / Cancel → memory draft**.

- Picker cancellation changes nothing. Selecting an image stages a private copy before editing; subsequent work must not depend on the external URI remaining readable.
- The photo is not part of the memory draft or count until Apply or Keep original succeeds. Check remaining capacity before selection and again before attachment.
- Show a fit-to-window preview, freeform crop rectangle with handles, a 90° clockwise Rotate action, Reset, Apply, Keep original and Cancel/Back.
- Crop coordinates use the EXIF-correct displayed image. Rotate updates the crop with the image; Reset restores orientation and the full rectangle from the initially staged source.
- Crop is available without precision gestures: labeled Left, Top, Right and Bottom edge controls expose percentages, enforce a nonempty crop, and work with keyboard/TalkBack. No filter, drawing, perspective correction, flip or arbitrary-angle rotation is required.
- Apply generates a separate orientation-normalized managed image reflecting the crop and rotation. Use PNG to preserve transparency; reject an output above 25 MiB with actionable recovery. No edit or identity crop/rotation uses the unchanged source rather than re-encoding it.
- If an accepted source is animated, the editing preview and transformed output use the decoder's first-frame still image. Keep original preserves the source bytes, including animation; animation playback in the viewer is outside this iteration.
- Keep original attaches the validated imported source unchanged, regardless of the current preview edits. Neither action saves the memory itself.
- Back/Cancel discards just the pending photo and its edits, returns to the unchanged memory draft, and releases staging files when unreferenced. No additional confirmation is needed for this pending-photo cancellation.
- Existing attachments and every non-photo field retain their values and IDs when a photo is added to an existing memory.

### MP-R04 — Draft integrity, failure and recovery

- Represent selection, import, editing, transformation, attachment and cancellation explicitly. Prevent duplicate picker launches and Apply taps. Disable parent Save/Delete/Share and competing attachment mutations while a pending photo operation is active; modal Back closes the photo surface first.
- Save only IDs, managed references and compact edit coordinates/state. Never place image bytes, bitmaps or large encoded strings in navigation arguments or saved state.
- Preserve the pending source/output IDs before file work. Retrying one immutable Apply payload reuses its output ID; changing crop/rotation after a failed Apply creates a new output ID. A completed output can be recovered without transforming it again.
- Lease source and output while pending. Acquire the memory draft's lease for the chosen result before releasing the staging lease. Publish the draft attachment once, retaining the same attachment ID across an ambiguous attach failure.
- Import, decode, transform, disk-space and lease-transfer failures do not attach partial results or alter the previous draft. Keep recoverable staged work and actionable Retry/Cancel controls. If the original source import never finished and URI access is lost, offer Select again.
- If attachment succeeded before recreation, reconcile the pending output ID against draft IDs and finish without duplication. A cancelled/replaced operation cannot later publish a stale completion.
- Configuration recreation restores pending crop/rotation and selected viewer ID. Process recreation rechecks authorization, verifies leases and reloads managed files; missing state recovers to a clear error/cancel state. No automatic Apply or memory save on restore.
- A failed memory save retains its attached photo and draft lease for retry. Discard removes only unsaved/unreferenced files. A committed event/reference always protects its image from cleanup.

### MP-R05 — Privacy and storage integration

- The new display, draft-display and transform paths must go through protected facades. Authorize before and after asynchronous work using the existing policy/session generations. Never allow Kotlin interface delegation to expose a raw new method.
- Saved photos use existing owner-event classification, including additional Mood/source protection on marked memories. Draft photos require a live authorized memory-editor scope and verified membership in that editor's staging/draft lease. A UUID or caller-supplied file path alone is not authority.
- Register draft scope only from the gated memory route. Invalidate its in-memory capability on route disposal, locking, policy/session changes or process death; a restored route obtains a new capability after authorization and lease validation.
- Scoped import and lease transfer must use the same route authorization. Permit only imported files from that editor's registered staging leases, its current draft, or authorized existing attachments; do not weaken handling of arbitrary unclassified images in other callers.
- Apply must check authorization again before publishing its result to the draft. A relock clears rendered bitmaps and blocks stale results; it never grants access to unrelated photos or unlocks another section.
- Preserve the existing screenshot/recents policy for full-screen surfaces, including any separate Dialog window. Add no public file URI, external intent, network request or log containing private paths/content.
- Publish derived files atomically under a fresh `photos/<UUID>.img` identity through `ManagedPhotoFiles`. Inspect encoded bytes for actual MIME, dimensions and size. Serialize with the shared photo mutex and clean incomplete temporary outputs.
- Reuse `PhotoAttachment` and existing event-photo writes. No Room schema or backup format change: saved derived images are ordinary attachments, covered by existing backup/restore. Staging/viewer leases are not backup content.

### MP-R06 — Usability and acceptance evidence

- All progress/errors have readable localized text and useful recovery. Provide focusable photo buttons with meaningful labels; announce position and errors without duplicating image labels.
- Keep controls reachable at 320 dp width, portrait/landscape, 100%/130%/200% font and light/dark themes. Directional controls and icons follow RTL conventions; image rotation remains geometrically clockwise in every locale.
- QA is one final task after all source tasks. It owns fixture/test authoring, Gradle compilation/test/lint, emulator execution, defects and retests. An implementation handoff does not imply acceptance.

## Proposed contract boundaries

- Domain: add validated `PhotoDisplayRequest`, `PhotoCrop`, `PhotoEdit` and image-budget constants, separate from existing import limits. Crop values are normalized finite doubles with `0 ≤ left < right ≤ 1` and `0 ≤ top < bottom ≤ 1`; clockwise quarter turns are `0..3`.
- Data: extend `PhotoRepository` with bounded original-backed display, lease-membership queries and transformation to a caller-chosen fresh output ID. Add explicit draft read scope rather than weakening saved-photo authorization. Preserve current methods.
- App: a focused `MemoryPhotoEditSession` owns staged import, edit state, immutable Apply payload and lease transfer. `PhotoDraftSession` gains an idempotent attachment operation for already imported results. Keep picker/UI composition separate from this state machine.
- Geometry: normalized rotation/crop-to-pixel calculations belong in the domain module, shared by the data exporter and UI. UI-only viewport/handle coordinate mapping lives under `ui/photos`.
- Rendering: `PhotoViewer`, `PhotoViewerState` and `PhotoEditScreen` live under `ui/photos`. `MemoryEditorRoute` connects these only to the memory flow.

The implementation plan fixes concrete signatures and files; helpers, layout spacing and coroutine decomposition are builder choices within the constraints above.

## Acceptance scenarios

| ID | Scenario | Required result | Owner |
| --- | --- | --- | --- |
| MP-AC01 | Open third attachment from memory details | Correct photo/position, full-screen fit, original-backed detail, close returns to same reading position | MP-I01/I02; final QA |
| MP-AC02 | Zoom/pan portrait, panorama and large photo | Correct orientation, 1×–4× clamping, bounded decode, accessible controls, no stale image | MP-I01/I02; final QA |
| MP-AC03 | Create memory, choose photo, crop and rotate, Apply, save/reopen | Saved pixels match the preview geometry; source bytes unchanged; new photo visible in viewer | MP-I03/I04; final QA |
| MP-AC04 | Add edited photo while editing existing memory | Existing attachment IDs and all other fields preserved; new result persists only on memory save | MP-I04; final QA |
| MP-AC05 | Keep original, identity Apply, picker cancel, editor cancel | Original bytes retained for first two; cancellation leaves draft/count unchanged | MP-I03/I04; final QA |
| MP-AC06 | Fail/retry transform, lease transfer and memory save; recreate mid-flow | No partial files/duplicate attachments, immutable retry identity, recoverable draft | MP-I03/I04; final QA |
| MP-AC07 | Open missing image; revoke access during decode/Apply | Retry/Close, no previous image leak, no stale publication or privacy bypass | MP-I01/I02/I03/I04; final QA |
| MP-AC08 | Hit 20-photo limit; delete/discard; backup/restore edited result | Limits preserved, only unreferenced files cleaned, committed pixels/metadata survive restore | MP-I03/I04; final QA |
| MP-AC09 | Rotate/recreate/background at viewer/editor states | Authorized state restores without automatic Apply; lease ownership remains correct | MP-I02/I04; final QA |
| MP-AC10 | EN/FR/FA, accessibility and small/large layouts | All controls and recovery reachable; actual TalkBack/keyboard observations recorded | MP-I02/I04/I05; final QA |

## Non-goals

No implementation in this documentation task; no library-card gallery redesign, camera capture, multi-select import, photo sharing/export, cloud sync, filters, destructive edits, re-editing an existing attachment, global rollout to unrelated surfaces, release or deployment. No automatic Git commit is required.
