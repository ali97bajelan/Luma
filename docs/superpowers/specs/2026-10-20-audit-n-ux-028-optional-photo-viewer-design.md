# UX-028 — Attached images cannot be inspected beyond their previews

**Status:** Draft specification; implementation has not started. Proposed decisions below are reviewable defaults, not previously approved product policy.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-028](../../UI_UX_AUDIT-20261020N.md#ux-028--attached-images-cannot-be-inspected-beyond-their-previews)
**Priority / effort / confidence:** P3 — Low / High / High (copied from the static audit; not a delivery estimate).
**Affected surface:** Journal/Memory photo attachments and details
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-028-optional-photo-viewer.md)
**Delivery phase:** 4 — optional capability

## Intent and success

Offer an optional private full-screen photo viewer for details that previews cannot reveal. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

The audit is source evidence, not reproduced runtime acceptance. Its recommendations are input to this draft, not executable instructions. All tests, runtime exercises and commits in the plan are future work.

## Dependencies

None. Coordinate overlapping files using the index's integration order.

## Global constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies unless a task explicitly requires a reviewed exception.
- All changed user-facing copy must exist in EN/FR/FA with matching resource names and numbered placeholder signatures; preserve RTL and intended Gregorian display/storage behavior.
- Keep semantic light/dark palettes, at least 48 dp interactive targets, and reachability at 100%/130%/200% font, narrow portrait and short landscape.
- Preserve offline/private storage, existing consent boundaries, stable record/command IDs, original retry payloads and Undo protections.
- Never truncate accepted personal content, infer write failure means no commit, or treat an unavailable privacy mode as known.
- Only implement this task and its declared prerequisites; preserve unrelated working-tree changes and existing navigation/draft state.

## Behavior and policy

1. This is optional P3 work, excluded from the corrective release's required completion. The 8-megapixel decode budget and 1x–4x zoom range are proposed viewer policy, not existing product requirements.
2. Tap preview to open the selected photo with position, Close/Back, next/previous and accessible Zoom in/Zoom out/Reset controls. Pinch/pan are additional inputs, not the only way to inspect. Expose named Pan left/right/up/down accessibility actions in 25% viewport increments, clamped to image bounds, so inspection never requires a gesture.
3. Sample the private original to fit viewport/zoom needs under the decode budget; do not decode an unbounded full original or upscale a 640-pixel preview as the detail source. Decode off main, cancel stale requests and release replaced bitmaps.
4. Acquire a viewer-specific lease for displayed attachments before loading; release only that lease on dismissal. Preserve existing unsaved draft leases and committed event references.
5. Missing/corrupt/unmanaged image gets named Retry/Close state without leaking paths. Restore photo ID/position on recreation; constrain pan to image bounds and reset zoom per image.

## Design and contract

Keep existing 640-pixel previews for lists and add a bounded higher-resolution display path through PhotoRepository. Use a shared full-screen Compose viewer that preserves draft leases and never exposes private file paths.

Optional proposal: PhotoRepository adds suspend fun display(photo: PhotoAttachment, request: PhotoDisplayRequest): ByteArray?; PhotoDisplayRequest(viewportWidth: Int, viewportHeight: Int, maxPixels: Long = 8_000_000L) rejects nonpositive dimensions and maxPixels outside 1..8_000_000. @Composable PhotoViewer(photos: List<PhotoAttachment>, initialId: UUID, repository: PhotoRepository, onDismiss: () -> Unit): Unit owns display loading and zoom state.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `selectedPhotoOpensPrivately` | Tap the third of four attachments. | Viewer opens the third ID and reports position 3 of 4 without exporting a path/URI. |
| AC-2 | `decodeAndZoomStayBounded` | Open a large portrait/panorama and repeatedly zoom. | Source decode stays within 8,000,000 pixels; zoom remains 1x–4x and obsolete decodes are cancelled. |
| AC-3 | `controlsWorkWithoutGestures` | Use TalkBack/keyboard for zoom, pan/navigation, reset and Close. | Each function is named and reachable without relying only on pinch/swipe. |
| AC-4 | `missingImageCanRecover` | Original becomes unavailable/corrupt during loading. | Retry and Close are usable, stale imagery is cleared and the editor draft stays intact. |
| AC-5 | `leaseAndLifecycleAreScoped` | Rotate, switch images and dismiss while cleanup runs. | Selected ID survives; viewer lease releases on close without dropping editor leases or deleting referenced media. |

## Scope boundary

No required photo-feature rollout, cloud upload, sharing, full-resolution unbounded decoding or changes to preview/import limits.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.

