# T10 Photo Attachments Implementation Plan

Goal: private durable photos on journals and direct/promoted memories; safe removal and graceful unavailable-media rendering.
Spec: TASKS.md T10 and ARCHITECTURE.md file/persistence boundaries.

Use v1 media_attachment rows. PhotoAttachment (UUID, validated relative path, optional typed metadata) is carried by LifeEventRecord. Nullable photo snapshots on JournalInput/MemoryInput/MemoryMarkerInput preserve existing references by default; explicit lists replace only attachments in the capture transaction. Creates compare explicitly supplied photos for idempotent retries. Deleting manual records removes references; physical cleanup is separate and checks current references.

PhotoRepository imports selected content URIs to unique photos/<UUID>.img files, retains durable draft leases, releases leases, creates bounded previews, and cleans unreferenced/unleased managed files. ManagedPhotoFiles owns bounded temporary copying, sync/atomic publication and strict managed-path containment; AndroidPhotoCodec validates image headers/decoding and handles orientation using ImageDecoder. Limits: 20 images per draft, 25 MiB per import, 40 million source pixels, 640-pixel previews. Never persist source URI in database or upload files. No new permission, schema migration, image library or network dependency.

PhotoDraftSession shares import/removal/recovery between journal/memory editors. Save metadata/paths, not bytes, in SavedStateHandle. Retain existing/restored draft paths before editing. Pending source/UUID survive recreation and reconcile a finished private copy. Failed imports/writes retain previous draft; discard releases its lease; post-write release cannot turn a committed save into a UI save error. Protected abandoned drafts remain leased until explicit release; cleanup favors retaining files over removing a restorable draft.

Use AndroidX PickVisualMedia with image-only selection and its older-device fallback. Add repeatable Add photo, preview, unavailable state/retry and draft removal to journal/memory editors; journal detail renders photos read-only. Today/Timeline, Review and person history show photo counts. Promotion shares the original event photos; unmark preserves them. Journal/memory/mood deletion releases DB references, with cleanup on capture completion and application start.

- [x] Domain/file tests first; implement metadata/path validation, snapshots, mapping, targeted Room writes and pure managed-file engine.
- [x] Room/device test sources for import/restart/removal/rollback, references/leases, decoder failures and deletion; implement PhotoRepository/codec/wiring.
- [x] Session/editor tests first; implement bounded saved photo drafts, picker/rendering and navigation wiring.
- [x] Full JVM/APK/data lint checks, independent review, docs and honest pending device/app-lint status.

Review focus: interrupted copies and committed results, retention of unsaved/restored/other-editor photos, missing/corrupt/oversized/non-image sources, path/symlink containment, DB failure without physical deletion, promotion/unmark and complete-graph preservation. Preserve existing untracked schemas/plans; no commit or emulator execution.

Verification: 110 JVM tests pass, debug/test APKs compile, data lint is clean, and
independent review found no remaining critical/important defects after a tested
removal-intent recovery fix. Full app lint repeats the existing bundled analyzer
crash. Device/native picker checks remain pending; no emulator was launched.
