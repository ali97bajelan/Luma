# Memory Photo Viewing and Editing Final QA Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans`. This document expands the single final task **MP-QA**; its checklists are QA steps, not interleaved implementation tasks.

**Goal:** Validate both memory photo features together after every implementation task is source complete.

**Architecture:** Independently specified pixel fixtures and domain/state tests cover geometry and recovery; instrumented storage/UI tests and emulator observation cover native decoding, picker behavior, authorization and complete saved-memory flows.

**Tech Stack:** Existing JUnit, coroutine tests, Compose instrumentation, Gradle and Android emulators. No new dependencies.

**Spec:** [Memory photo viewing and basic editing](../specs/2026-11-06-memory-photo-viewing-editing-design.md)
**Implementation:** [MP-I01–MP-I05](2026-11-06-memory-photo-viewing-editing.md)
**Status:** NOT STARTED — no tests, Gradle commands or emulator checks have run for this request.

## Global constraints and entry gate

- Begin only after MP-I01–MP-I05 are recorded source complete. Test/fixture authoring, Gradle/build/lint, APK installation, emulator/device checks and defect retesting all belong here.
- Carry forward every global constraint from the spec and implementation plan, including 20 photos, 25 MiB, 40,000,000 source pixels, 640-pixel previews, 8,000,000 working/export pixels, maximum working/export edge 4096 and 1×–4× zoom.
- Use the actual `productionDebug` app variant, not obsolete generic `testDebugUnitTest` commands from older plans. Core data has no production flavor.
- Use isolated emulator/test data and the QA package `com.luma.lifeos.qa`; do not clear or modify personal app data.
- Record actual results and command exit codes. Missing SDK/emulator/fixture prerequisites remain BLOCKED/NOT RUN, never PASS. No release, deployment or real photo sharing is required.

## Review Focus

1. Mirrored/rotated EXIF input and crop rotation consistency: independent pixel oracle, QA02.
2. Ambiguous completed transform/attach and changed retry payload: exactly one correct attachment, QA04.
3. Lock policy/session change during delayed async work: no rendered or attached stale result, QA05.
4. Maximum dimensions, alpha and encoded-size failures: enforce source/export limits without damaging originals, QA02/06.
5. Viewer/staging/main-editor lease races: cancellation cleans only unreferenced task-owned files, QA04/06.

## Step 1 — Capture candidate and prerequisites

- [ ] Record source revision and task patch identity, JDK 17, Gradle wrapper version, installed SDK/build tools, emulator serial/API and APK hash when built. Use evidence directory `docs/qa/evidence/<actual-run-date>/memory-photos/`; include a results ledger with requirement/case, candidate, command or action, expected/actual, evidence link and defect/retest status.
- [ ] Confirm at least an API 28 emulator and a modern API 35 or 36 emulator. Run core flows on both; perform the full accessibility/layout matrix on the modern target and record any additional minimum-API differences.
- [ ] Verify current Gradle task names in QA before executing the commands below. Set the intended emulator serial explicitly when multiple devices are connected. Document toolchain limitations without silently skipping checks.

## Step 2 — Author independent fixtures and automated coverage

All files below are future test work. Do not create or run them during implementation.

| File | Assertions / cases |
| --- | --- |
| `core/domain/src/test/kotlin/com/luma/lifeos/domain/PhotoEditTest.kt` (new) | Reject NaN/infinity/out-of-range/empty crop, invalid quarter turns, nonpositive viewport and zoom outside 1×–4×; accept exact valid boundaries; independently calculated four-rotation crop mapping, floor/ceil pixel bounds and nonempty edge crops, including one-ULP input gaps that must remain valid through rotation |
| `app/src/test/java/com/luma/lifeos/ui/photos/PhotoCropGeometryTest.kt` (new) | Handle/viewport mapping honors fit letterboxing and rotated aspect ratios; shared normalized bounds agree with independent corner/edge expectations; no app-only export geometry |
| `app/src/test/java/com/luma/lifeos/ui/photos/PhotoViewerStateTest.kt` (new) | Selected-ID opening/navigation, boundary disablement, zoom/pan clamping, missing/deleted selection, old request rejected and close/reset behavior |
| `app/src/test/java/com/luma/lifeos/ui/photos/MemoryPhotoEditSessionTest.kt` (new) | Import does not attach; Apply/Keep original/identity; immutable retry ID; changed edit gets new ID; failed lease transfer; already-attached restore; cancel/stale completions; parent action gating and count recheck |
| `app/src/test/java/com/luma/lifeos/ui/photos/PhotoDraftSessionTest.kt` (extend) | Idempotent imported attachment, retain-before-update, 20-photo bound, main draft preserved across staging cancellation and save failure |
| `app/src/test/java/com/luma/lifeos/ui/photos/TestPhotoRepository.kt` (extend) | Explicit new display/lease/transform methods, controllable delayed responses and faults; do not use defaults that hide missing coverage |
| `app/src/test/java/com/luma/lifeos/ui/memory/MemoryEditorTest.kt` (extend) | Existing-memory fields/attachment IDs remain unchanged, new and edit modes share behavior, pending operation blocks save/delete/share, save retry preserves lease |
| `app/src/test/java/com/luma/lifeos/lock/ProtectedPhotoRepositoryTest.kt` (new) | Before/after generation changes for display/transform; scoped import/retain with app lock enabled; saved-source sections; forged/expired/unregistered lease rejected; lease membership required; restored route must register again |
| `core/data/src/test/kotlin/com/luma/lifeos/data/media/ManagedPhotoFilesTest.kt` (extend) | Atomic output, valid lease ownership, partial cleanup, source hash unchanged, malformed paths/identities rejected and committed reference survives cleanup; failed transform keeps its output reservation through unrelated lease cleanup, rejects a changed payload for the same ID, and releases the reservation only when its owning lease is released |
| `core/data/src/androidTest/kotlin/com/luma/lifeos/data/PhotoTransformPersistenceTest.kt` (new) | Real orientation/crop/rotate/alpha pixels, actual metadata and budgets, truncated/corrupt inputs, failed/ambiguous publication, original/derived file hashes and reopen durability; reject a database-owned output ID with a missing image and no matching operation binding, while allowing recovery only for the exact bound operation |
| `core/data/src/androidTest/kotlin/com/luma/lifeos/data/PhotoPersistenceTest.kt` (extend) | Staging/viewer/editor lease isolation and main-draft transfer/reference cleanup with real repository |
| `core/data/src/androidTest/kotlin/com/luma/lifeos/data/backup/BackupPersistenceTest.kt` (extend) | Edited saved photo survives backup/restore with matching pixels, metadata and links; staged outputs excluded |
| `app/src/androidTest/java/com/luma/lifeos/MemoryPhotoFlowTest.kt` (new) | Memory details/editor viewer; crop/rotate add on create and edit; original/cancel; remove/save/reopen; error UI, recreation and relock; existing library navigation |
| `app/src/androidTest/java/com/luma/lifeos/PhotoCaptureTest.kt` and `MemoryCaptureTest.kt` (extend as needed) | Existing attachment import/remove/count/save and memory behavior regressions |

- [ ] Author an asymmetric synthetic image with labeled colored corners and a grid; hard-code expected crop/rotation results independently of production helpers. Add EXIF rotation/mirroring variants, portrait, panorama, a small image, transparent PNG, and accepted animated input if the existing decoder accepts it. Define editing as the decoder's first-frame still result for animated inputs; unchanged imports remain byte-identical.
- [ ] Add inputs at/over 25 MiB and 40,000,000 pixels, and real source images above the 8,000,000 working/export cap. Validate dimensions/counts from independent header/byte inspection and allocation instrumentation, not only the output metadata returned by the code under test.
- [ ] Add controllable import/decode/transform/retain/save/cleanup failures, delays, process restoration checkpoints and lock policy/session changes. Use fixed source/output IDs and record before/after hashes of source files and the full memory graph.
- [ ] Keep pure tests on the JVM; Android codec, filesystem durability and actual pixel assertions run instrumented. Do not label implementation-mirroring checks or a mocked decoder as pixel correctness evidence.

## Step 3 — Gradle build, unit tests and lint

Run from the repository root with the existing Android SDK/JDK configured. On Windows use `gradlew.bat`. Commands below are future QA actions, not results.

```bash
./gradlew :core:domain:test :core:data:testDebugUnitTest :app:testProductionDebugUnitTest
./gradlew :app:assembleProductionDebug :app:assembleProductionDebugAndroidTest :core:data:assembleDebugAndroidTest
./gradlew :app:lintProductionDebug :core:data:lintDebug
```

- [ ] Run all listed host/build/lint gates and retain logs/reports with exit codes. Require zero failing tests/build errors and classify new warnings. Update all test fakes/callers exposed by new repository signatures in this QA phase.
- [ ] Confirm targeted suites above are actually discovered and execute assertions; no zero-test/filter-mismatch pass. Repair compilation/resource issues under MP-QA ownership and rerun affected gates against the updated candidate.

## Step 4 — Instrumented and emulator acceptance

Run app and core-data instrumentation sequentially against each configured target:

```bash
./gradlew :core:data:connectedDebugAndroidTest
./gradlew :app:connectedProductionDebugAndroidTest
```

These commands do not by themselves configure locales, fonts, lock settings, seed the platform picker, simulate process death or certify TalkBack. Apply and record those inputs in the cases below.

| Case | Actions | Expected evidence | Spec coverage |
| --- | --- | --- | --- |
| QA01 — Viewer reachability | More → Memories → details; tap third of three. Repeat from an existing memory editor and marked source event; navigate Previous/Next, zoom, pan, reset, close/Back | Correct selected ID/position and source-backed image, fit/aspect ratio, bounded zoom/pan, untouched editor draft and preserved parent scroll. Unrelated gallery callers retain old behavior | R01/R02; AC01/02 |
| QA02 — Crop and rotation | Select grid/EXIF/alpha/large/panorama inputs; crop asymmetric and minimum-width/height rectangles near every edge, rotate 1/2/3/4 times, reset; Apply and save/reopen | Independent saved-pixel comparisons match preview; tiny accepted crops remain valid after rotation; correct alpha/orientation/MIME/byte count/dimensions and budgets; source hashes unchanged. Resolution explanation appears only when applicable | R02/R03; AC02/03 |
| QA03 — Create/edit/original/cancel | New memory with edited photo; existing memory with prior attachments and unrelated fields; Keep original despite changed preview; identity Apply; cancel picker/editor; open editor viewer | First two persist correct result only on memory save; existing graph preserved except explicit new photo; unchanged imports byte-identical; cancellations leave draft/count unchanged; viewing never marks dirty | R01/R03/R04; AC03/04/05 |
| QA04 — Failure/recovery | Fail import, transform, draft retain, memory save and release; trigger cleanup by releasing another lease while a failed transform remains pending; retry the same payload, then try a changed payload with the same output ID; cancel during delayed work. Recreate/kill and restore after source copy, output publication and attach persistence | No partial/duplicate attachment; pending output binding survives unrelated cleanup; same-payload retry reuses its ID, changed payload under that ID is rejected and a fresh ID succeeds; explicit confirmation for unapplied restore, completed attachment reconciles once, staged source usable after URI revocation, recoverable errors | R03/R04; AC05/06/09 |
| QA05 — Privacy/stale imagery | Lock during viewer/editor load and Apply, change policy/session generation, cancel unlock, reopen after authorization; attempt unrelated saved and forged draft reads; inspect recents/screenshot behavior | No raw new-method bypass, previous-photo leak, stale attachment or unrelated section access; registry expires; authorized reload required; full-screen window preserves app policy | R02/R04/R05; AC07/09 |
| QA06 — Limits/cleanup/backup | Attempt 21st photo, missing/corrupt files and oversized source/output; try a database-owned output ID whose image is missing; fill disk where safely injectable; open/close viewer repeatedly; remove/discard draft; fail save then retry; backup/restore saved edited image | 20-photo limit and all budgets hold; a missing committed image cannot be replaced by an unrelated transform; errors actionable; no source loss or orphan accumulation after normal cleanup; committed and other-editor files survive; restored photo pixels/metadata correct | R02/R03/R04/R05; AC07/08 |
| QA07 — Lifecycle/navigation | Rotate during viewer load, active crop, export and error; background/return, process recreation; close modal via Back and cancel parent later | Correct selection/edit state after authorization, no auto Apply/save, stale operation cannot publish, correct lease release and parent return without losing unrelated edits | R01/R02/R04/R05; AC06/09 |
| QA08 — Accessibility/layout | EN/FR/FA × light/dark × 100%/130%/200% font × 320 dp portrait/short landscape; include expanded window. Use TalkBack and hardware keyboard for all crop edges, zoom/pan/navigation and recovery | Localized wrapped text, RTL traversal/icons, geometrically clockwise rotation, named controls/errors, 48 dp targets, visible usable final actions and no gesture-only operation | R06; AC10 |

- [ ] Record actual picker selection and cancellation on API 28 and modern target using disposable seeded images. A direct repository call or injected picker callback does not certify the native picker interaction.
- [ ] Run both instrumentation gates and QA01–QA08 on the candidate. Record the per-API core cases and each layout/locale matrix cell. Capture screenshots only with synthetic data.
- [ ] For API calls that cannot cancel an in-progress native decode, verify result rejection, eventual bitmap release and no stale publication. Do not claim immediate interruption from coroutine cancellation alone.
- [ ] Observe responsiveness and peak allocations for the maximum accepted source and repeated viewer/image switching. Verify no full-size intermediate exceeding the working-image budget and no unbounded retained bitmap growth. Record measured behavior rather than inventing an FPS/latency guarantee.

## Step 5 — Fix, retest and final evidence

- [ ] Track each defect with reproduction, owning source task, fix and retest. All corrections remain under this final QA task; source-task status alone never certifies them.
- [ ] After source changes, rerun affected targeted assertions plus integrated create/edit/view/save/reopen and privacy/lease regressions. Run final host/build/lint and connected gates on the final candidate; preserve its revision/patch/APK identity with reports.
- [ ] Map all MP-R01–MP-R06 and MP-AC01–MP-AC10 to actual assertion/native/manual evidence. Record remaining blocked checks separately from passing cases.
- [ ] Mark MP-QA complete only when required Gradle gates and emulator/manual scenarios pass. Report limitations honestly. Keep source completion, QA acceptance and release status separate.

## Initial execution ledger

| Gate | Result | Evidence |
| --- | --- | --- |
| MP-I01–MP-I05 entry gate | NOT STARTED | Documentation only |
| Fixture/test authoring | NOT STARTED | — |
| Gradle unit/build/lint | NOT RUN | — |
| API 28 instrumentation/native flows | NOT RUN | — |
| Modern API instrumentation/native flows | NOT RUN | — |
| QA01–QA08 and accessibility matrix | NOT RUN | — |
| Defect retests/final acceptance | NOT RUN | — |
