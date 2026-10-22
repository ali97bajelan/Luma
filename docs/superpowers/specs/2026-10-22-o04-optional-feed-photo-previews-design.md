# O04 — Optional — Timeline photo previews specification

Date: 2026-10-22  
Status: Optional / deferred; requires a separate scope decision before implementation.  
Depends on: T32-A

## Intent and sources

Show the first saved photo attachment inside eligible Timeline editorial cards using local preview data.

References: timeline-light.png and timeline-dark PNG/HTML journal imagery. Read the [shared reference brief](../../luma-redesign/README.md) and [task index](../../UI_REDESIGN_TASKS.md) with this specification. The brief resolves inconsistencies and provides common tokens; it is normative for every task.

## Global constraints

- Native Android, Kotlin, Compose Material 3 and Navigation Compose; keep minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- Offline operation; no account, cloud sync, AI service, telemetry, new permissions or schema migration in required visual work.
- System/Light/Dark selection and English/French/Persian support remain available offline.
- Preserve existing route IDs, callbacks, saved state, data semantics, loading/error/retry/undo and accessibility tags unless an optional task explicitly changes its own contract.
- Use actual saved data; never ship reference people, dates, percentages, measurements, quotes or charts as user records.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL and system animation scale.
- Optional tasks default to deferred and are not required for redesign acceptance. No optional feature is authorized for implementation by this planning document.

## Screen and behavior contract

- Add an optional trailing composable slot `photoPreview: (@Composable () -> Unit)? = null` to EventCard so existing consumers remain source-compatible. Timeline supplies a shared local preview component only for records with photos; Today/search remain metadata-only by default.
- Create `ui/photos/FeedPhotoPreview.kt` with `FeedPhotoPreview(repository: PhotoRepository, photo: PhotoAttachment, modifier: Modifier = Modifier)`. Render one 16:9 cropped image with a 160 dp maximum height; clicking the card keeps its existing detail action.
- Use PhotoRepository.preview on a background dispatcher with bounded decoded dimensions, lifecycle cancellation and attachment-ID keyed state. Loading/missing/corrupt previews fall back to photo-count metadata; never retain an image from another record.
- No remote stock images, new storage permission, full-screen viewer, encryption badge or automatic import. Add localized attachment semantics and avoid a redundant focus target.

## Implementation boundaries

- `app/src/main/java/com/luma/lifeos/ui/timeline/EventCard.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/timeline/TimelineRoute.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/photos/PhotoGallery.kt` — existing integration surface.
- `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt` — existing integration surface.

Existing public/internal composable signatures remain compatible except additions explicitly named above. Existing repositories/view models own data and mutations; decorative components receive values and callbacks. New files named in the contract are proposed implementation files, not files already created by this documentation change. Consult the paired plan for test ownership.

## Acceptance scenarios

1. Saved preview appears offline and opens the same source record; no-photo/loading/corrupt/deleted attachments retain a usable card.
2. Paging/rebinding rapidly between records never swaps thumbnails or decodes full-resolution images on the main thread.

## Verification and completion

Use focused existing regression coverage plus visual/manual checks for the changed surfaces. Add tests for changed behavior and meaningful state/semantics risks; do not add tests that merely assert padding or duplicate token assignments. Required font/contrast/RTL/inset checks are acceptance work, not a reason to add fake data. Record PASS/FAIL/NOT RUN and evidence in the T33 ledger. No checks are claimed to have run in this specification.

[Implementation plan](../plans/2026-10-22-o04-optional-feed-photo-previews.md)
