# Capture, catalogs, details, Search and sharing

Status: **NOT RUN** for every case below. This is a source-derived execution specification, not an execution report. No device checks or test commands were run while writing it. Run each case independently; never carry mutations into the next case.

## Execution contract and exact fixtures

Use the productionDebug build (application ID `com.luma.lifeos.qa`) on a disposable installation with app lock off, integrations/reminders off, English, Light, Gregorian calendar, Asia/Tehran, device local time **D = Monday 2026-11-16 12:00**. Keep automatic time off during deterministic capture checks. All stated times are local (+03:30); use the timestamp control to enter them instead of assuming the current minute. If D must move, shift every listed calendar date by the same number of days and record the new mapping. Preserve separate time-zone transition tests as stated.

Before each manual case, clear disposable app data with Android Settings > Apps > Luma > Storage > Clear storage, launch, restore baseline settings, and build only its named pack. Do not clear a real installation. After each case discard open drafts, remove the pack by clearing disposable data, and record that reset. Instrumented/unit tests use their own isolated repositories; do not confuse their database counts with the device app. Maximum **50 records per entity type**; no stress/load tests. A repeated text character does not create another record. Never run a whole existing suite that seeds 120/50,000 records to validate this document.

Fixture labels are human aliases. Normal UI generates UUIDs: record the actual UUID from an instrumentation repository snapshot in evidence rather than claiming the UI can choose an ID. When a harness seeds a specific alias, use UUID `00000000-0000-0000-0000-00000000NNNN`: CP01=0101, CP02=0102, CL01=0201, CL02=0202, CJ01=0301, CJ02=0302, CM01=0401, CM02=0402, CX01=0501, CX02=0502. Never reuse an ID across types. CTagAmber and CTagBlue are tags resolved by normalized names `c-amber` and `c-blue`; normal UI generates their IDs.

Packs (build in the listed order):

| Pack | Exact records and UI setup | Expected persisted totals |
|---|---|---|
| E | Empty baseline; do not capture anything. Built-in emotions may exist and are not user entries. | Events 0, moods 0, journals 0, direct memories 0, memory markers 0, people 0, places 0, user tags 0, attached photos 0. |
| C | From E, More > People; People tab > Add person: CP01 name `Ada QA`, note `neighbor orchard`; CP02 `Benoit QA`, note `trail partner`. Places tab > Add place: CL01 `Cedar Hall`, note `north entrance`; CL02 `River Walk`, note `east path`. Save each. | Events 0; people 2; places 2; tags/photos 0. |
| U | From C, Today > Check in: CM01 D 08:00, score 2, note `moodonly-low`, CP01, CL01. CM02 D-2 (Nov 14) 20:00, score 5, energy 4, stress 1, note `moodonly-high`, CP01+CP02, CL02. Open CM02 from Timeline > event detail > Mark as memory; marker note `markeronly-bright`, importance 3, add `c-blue`, Save. Today > Record memory: CX01 D 11:00, title `Cedar memory`, note `bodyonly-photo`, importance 5, CP01, CL01, tag `c-amber`, photos CA+CB (Keep original). CX02 D-1 (Nov 15) 17:00, title `River memory`, note `bodyonly-walk`, importance unset, CP02, CL02, tags `c-amber`+`c-blue`, no photos. Context lives under More details; tags and importance have separate disclosures. | Events 4 = moods 2 + direct memories 2; journals 0; markers 3 (CM02, CX01, CX02); people 2; places 2; tags 2; attached photos 2. Library 3. |
| J | Harness-only addition to C: CJ01 D-1 09:00, title `Cedar journal`, body `bodyonly-cedar`, tag `c-amber`, CP01, CL01; CJ02 D 10:00, title `River journal`, body `bodyonly-river`, tag `c-blue`, CP02, CL02, marker importance 2/note `markeronly-quiet`. | Events/journals 2, direct memories/moods 0, markers 1; people/places/tags 2 each; photos 0. |

CA is a local, non-sensitive JPEG, 800×600, under 1 MiB, with normal EXIF orientation (1), red/green/blue/yellow corners and visible “TOP”; CB is a local PNG, 600×800, under 1 MiB, visible “B”. Put copies in the system photo picker before building U. Record file hashes, dimensions and byte sizes in evidence. These fixture image files are **not supplied by this repository**: photo cases are BLOCKED until those two files exist on the disposable device. No network/cloud photo source is required.

“Open CX01” means Timeline > select D > `Cedar memory` > event detail > Edit, or More > Memories > `Cedar memory` (opens the editor). “Open CM01” means Timeline > D > its score/note > event detail > Edit. Catalog paths use More > People > People/Places tab. “Search” means the Search entry in app navigation. Controls are identified by source test tags in backticks when labels depend on wording.


Persisted oracle: after Save and after a full relaunch, reopen the entry and compare every stated field, links, tags and photos. For identity/count assertions use an instrumentation snapshot via `(activity.application as LumaApplication).storage`, the same existing seam used by [JournalCaptureTest][JCT]; query repositories by the recorded IDs and use `search.page(SearchQuery(), Asia/Tehran, 0, 50)` for total saved visible events. Inspect marker presence via `memories.get(id).memory`, not the event type. A screenshot alone cannot prove UUID identity, idempotence, orphan cleanup or a rollback; if snapshot access is unavailable mark that oracle BLOCKED, not PASS.

J is not creatable through current production navigation. To seed it in an executor-owned temporary instrumentation fixture, use the existing JCT `storage.journals.create(EventId(UUID.fromString(...)), JournalInput(body, instant, title, tags, personIds, PlaceSelection(placeId)))` seam after creating C with the specified IDs through `storage.people.create`/`storage.places.create`; then `storage.memories.saveMarker(CJ02, MemoryMarkerInput(2, "markeronly-quiet", setOf(CP02), PlaceSelection(CL02), emptyList(), setOf("c-blue")))`. Convert local time with `LocalDateTime.parse(...).atZone(ZoneId.of("Asia/Tehran")).toInstant()`. **BLOCKED prerequisite:** no checked-in test seeds this exact J pack or opens the legacy Journal route directly. Add an isolated fixture using that seam before any J-specific screen claim; do not modify production navigation.

Every case's evidence includes case ID, NOT RUN → actual result when executed, build/commit, device/API, local time/zone, settings, fixture UUID map and before/after counts. Capture the relevant field/error/dialog and final reopened state. Secure photo dialogs intentionally prevent ordinary screenshots; use approved non-sensitive external visual capture or semantics/test output, and never report a blank secure screenshot as rendering evidence. Any “BLOCKED” below is a prerequisite annotation; execution status remains NOT RUN.

UI acceptance for every manual case: current warm-paper/obsidian theme, shared Luma typography/spacing, 48 dp targets, readable feedback and labels; no clipped footer, inaccessible Save, keyboard overlap, hidden final field or lost scroll context. Repeat representative MOOD-002, MEM-009, CAT-003, SRCH-007 in Dark/System, French and Persian RTL, font scale 2.0 and landscape; values/counts must stay identical. See [design contract][DESIGN], [tokens][TOKENS], [components][COMPONENTS], [scaffold][SCAFFOLD], [coverage][COVERAGE] and [checklist][CHECKLIST].

## Harness execution and source limitations

Run only named, bounded methods. Unit command template (repository root):
`./gradlew :app:testProductionDebugUnitTest --tests 'com.luma.lifeos.ui.mood.MoodEditorTest.oneScoreIsEnoughToCaptureAndRapidRepeatedSaveCreatesOneRecord'`.
For another method substitute its exact class/method from the case. Instrumented template:
`./gradlew :app:connectedProductionDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.SearchStatesTest#filterResetPreservesQueryAndClearAllRemovesBoth`.
Data template:
`./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luma.lifeos.data.SearchPersistenceTest#combinesCategoriesAndDeduplicatesAnySelectionWithinEachCategory`.
Prerequisites: configured Gradle/JDK/SDK; instrumented methods also need a disposable connected device. These commands are specified, **not executed** here.

Current [navigation][NAV] maps new journal capture and both legacy journal routes to Memory. [JournalCaptureTest][JCT] and the journal method in [MemoryCaptureTest][MCT] still expect removed `today_capture_journal` / Journal detail UI, so they are stale for current navigation; do not present them as runnable current app paths. Journal ViewModel tests remain useful isolated seams. Older Memory persistence assertions may also predate separate direct note/context-note semantics; current [repository][MREPO] and [formatter][SHAREFORMAT] own the oracle. No checked-in share-specific test, failure-injection screen launcher, exact J seeder, or MemoryLibrary 31-row fixture was found; corresponding extensions are explicit BLOCKED prerequisites below.

## Mood

### MOOD-001 — Minimum check-in and repeated Save
Priority P0 · Level manual + unit · Status NOT RUN · Pack E.

1. Today > Check in; wait until loading/draft persistence ends → score unselected, Save disabled.
2. Set timestamp D 12:00; tap score 3, then Save twice quickly → one save completes and editor closes once.
3. Timeline D > created mood > Edit → score 3, D 12:00, optional fields unset.

Persisted oracle: exactly 1 event/1 mood/0 markers, same UUID after relaunch. Cleanup/evidence: reset E; record UUID/count and selected radio semantics. Harness: [MoodEditorTest][MOODTEST] `oneScoreIsEnoughToCaptureAndRapidRepeatedSaveCreatesOneRecord`.

### MOOD-002 — Full scales, emotions and context
Priority P0 · Level manual · Status NOT RUN · Pack C.

1. New check-in; set D 09:30, score 1; expand details → energy, stress, emotions, people/place available.
2. Select `Happy` and `Calm` (record their built-in IDs), energy 1, stress 5, note `C full mood`, CP01+CP02, CL01; Save → completes.
3. Reopen → both emotions, exact scales/time/note and links retained; switch score to 5 and Save → same entry updated.

Persisted oracle: 1 mood/event, 2 person links, 1 place link, 0 markers; score now 5. Cleanup/evidence: reset; capture both endpoint radios, scales and final detail. Source [Mood screen][MOODSCREEN], [Mood VM][MOODVM].

### MOOD-003 — Optional deselection and explicit context clear
Priority P1 · Level manual + unit · Status NOT RUN · Pack U.

1. Open CM02; tap selected energy 4 and stress 1 choices → both unset; deselect CP01 and CP02, choose no place.
2. Save; relaunch and reopen → energy/stress null, people empty, place null; score 5, note and memory marker retained.

Persisted oracle: events 4/moods 2/markers 3 unchanged; CM02 has 0 person and 0 place links. Cleanup/evidence: reset U; compare context before/after. Harness [MoodEditorTest][MOODTEST] `peopleDraftRestoresAndSubmitsExplicitSelectionIncludingClear` and `placeSelectionRestoresLoadsAndExplicitlyClearsWhileNoneIsOptional` (run separately).

### MOOD-004 — Note boundary rejects growth without replacing draft
Priority P1 · Level manual · Status NOT RUN · Pack E.

1. New mood, score 3; expand More details and paste exactly 5,000 ASCII `n` characters → accepted.
2. Append one `n` → field remains 5,000, limit feedback appears; timestamp and score stay unchanged.
3. Replace with `C accepted`; Save and reopen → new accepted text only.

Persisted oracle: one mood, note `C accepted`; no partial 5,001-character write. Cleanup/evidence: reset; export semantics/text length. Source [MoodNotePolicy][MOODNOTE], [Mood VM][MOODVM]. Imported over-limit text may shrink without being forced below the limit immediately; that separate legacy condition requires a seed, not a new UI capture.

### MOOD-005 — Keep editing, discard and recreation
Priority P0 · Level manual · Status NOT RUN · Pack E.

1. New mood, score 4; expand More details, enter note `C draft mood`, D-1 23:55; rotate/recreate activity → exact draft restored, still unsaved.
2. Close → discard dialog; Keep editing → draft remains.
3. Close > Discard; open a fresh mood → previous draft absent.

Persisted oracle: events/moods 0 throughout. Cleanup/evidence: reset; record dialog and both drafts. Durable Mood store differs from ordinary saved-state drafts; full force-stop recovery is not proven by activity rotation. Source [Mood VM][MOODVM], [Mood route][MOODROUTE].

### MOOD-006 — Save failure retains input; retry is idempotent
Priority P0 · Level unit · Status NOT RUN · Pack isolated fake, no device U.

1. Run [MoodEditorTest][MOODTEST] `errorRetainsOptionalValuesAndRetryKeepsTheSameCaptureId` via unit template → injected create failure leaves draft and error.
2. Allow retry → same capture ID succeeds with retained values.

Persisted oracle: exactly one fake repository record; no duplicate ID. Cleanup/evidence: test teardown resets dispatcher; save test report. Production manual injection is BLOCKED without a debug-only failing MoodRepository wrapper; airplane mode cannot induce local database failure.

### MOOD-007 — Missing/imported edit stays protected
Priority P0 · Level unit · Status NOT RUN · Pack isolated fake.

1. Run `missingRecordCannotBeSavedOrRecreatedByEditing` → missing edit is not ready, Save cannot recreate.
2. Independently run `importedMoodCannotBeModifiedByTheEditor` → imported record rejected for editing.

Persisted oracle: missing remains absent; imported graph unchanged. Cleanup/evidence: unit report and per-test counts. Source/harness [MoodEditorTest][MOODTEST].

### MOOD-008 — Delete cancel and confirmed deletion
Priority P0 · Level manual · Status NOT RUN · Pack U.

1. Open CM01 > Delete > Cancel → remains in editor; reopen from Timeline → still present.
2. Delete > confirm `mood_confirm_delete` → editor completes only after deletion.
3. Relaunch; Timeline D/Search → CM01 absent; CM02 still present with marker.

Persisted oracle: events 3/moods 1/markers 3; people/places 2 each. Cleanup/evidence: reset; confirmation and before/after IDs. Source [Mood screen][MOODSCREEN], [Mood VM][MOODVM].

### MOOD-009 — Draft storage and ambiguous delete recovery
Priority P0 · Level injection/manual · Status NOT RUN · Pack U.

1. In an executor-owned fixture inject MoodDraftStore.write failure for a CM01 edit after setting note `C recovery` → visible draft-storage feedback, Save/Delete disabled while draft non-durable.
2. Restore writes; tap `mood_draft_retry` → durable snapshot, same draft, enabled controls.
3. Inject delete throwing after committing, then get throwing during reconciliation; recreate with same draft ID → status unknown and check retry, no duplicate/create.
4. Restore get; Retry → absence reconciled and completion acknowledged once.

Persisted oracle: after recovery events 3/moods 1, CM01 absent; draft completion removed only on acknowledgment. Cleanup/evidence: reset; log snapshot revisions/pendingDeleteId. Seam [Mood VM][MOODVM] constructor `draftStore` / [MoodDraftStore][MOODSTORE]; **BLOCKED:** no checked-in failure launcher for this complete sequence. Do not simulate with process kill timing alone.

### MOOD-010 — Invalid local time retains accepted instant
Priority P1 · Level unit · Status NOT RUN · Pack isolated fake.

1. Run [MoodEditorTest][MOODTEST] `nonexistentTimeKeepsThePreviousTimestampAndShowsAnError` → DST-gap local input fails.
2. Check previous epoch millis, selected values and TIME error → unchanged draft timestamp; no event created.

Persisted oracle: 0 writes. Cleanup/evidence: unit report; record test's explicit DST zone, not Asia/Tehran. Tehran baseline has no such contemporary DST gap.

## Journal compatibility and isolated editor

### JRN-001 — Current journal routing compatibility
Priority P0 · Level manual/injected route · Status NOT RUN · Pack J.

1. Open CJ01 from Timeline/Search/People history → current navigation opens Memory promotion editor, with `memory_original` containing `bodyonly-cedar`.
2. Inspect → original timestamp disabled; source title/body preserved; Save can mark without nonblank marker text.
3. Cancel without changes → source remains.

Persisted oracle: J totals unchanged, CJ01 still JOURNAL/unmarked. Cleanup/evidence: reset; route/tag and source graph snapshot. **BLOCKED** until J seed exists. Source [navigation][NAV], [Memory VM][MEMVM]; no production Journal-create path should be invented.

### JRN-002 — Legacy required body and one-shot submit
Priority P0 · Level unit · Status NOT RUN · Pack isolated fake.

1. Run [JournalEditorTest][JTEST] `blankBodyCannotSaveAndRepeatedSubmitCommitsOnce` → whitespace body creates 0.
2. Valid body and repeated save → exactly one JOURNAL with one capture ID.

Persisted oracle: 1 record only after valid save. Cleanup/evidence: unit report. This validates the legacy ViewModel, not current navigation.

### JRN-003 — Legacy draft text/time/tags restoration
Priority P1 · Level unit · Status NOT RUN · Pack isolated fake.

1. Run `restoredDraftRetainsTextTimestampAndTags` → reconstruct ViewModel with SavedStateHandle.
2. Verify exact accepted text, timestamp, selected tags, dirty state retained.

Persisted oracle: no write before Save; restoration does not add an event. Cleanup/evidence: report. Source/harness [JournalEditorTest][JTEST], [Journal VM][JVM].

### JRN-004 — Legacy title/body/tag boundaries
Priority P1 · Level unit + isolated screen · Status NOT RUN · Pack E in direct JournalEditor fixture.

1. Set title `t`×200, body `b`×50,000 → accepted; attempt lengths 201/50,001 → previous values retained, field-specific errors.
2. Add tag `z`×50 → accepted; 51-character tag rejected; add ` C-Amber ` then `c-amber` → one normalized selection.
3. Recreate → accepted values/errors persist; change title alone → only its error clears.

Persisted oracle: no event before Save; after Save one Journal, normalized unique tags. Cleanup/evidence: reset fixture; lengths/error semantics. Existing [JTEST] `oversizedInputIsRejectedBeforeReplacingTheRestorableDraft`, `twoRejectedFieldsClearIndependently`, `recreationRestoresFieldFlags` are executable separately. UI part **BLOCKED** until direct legacy fixture launcher exists.

### JRN-005 — Legacy load, missing and imported failures
Priority P0 · Level unit · Status NOT RUN · Pack isolated fake.

1. Run `loadFailureKeepsDirtyDraftAndRetryUnlocksIt` → unavailable get retains draft, retry reload enables form.
2. Separately run `missingAndImportedRecordsCannotBeEditedOrRecreated` → no accidental replacement/create.

Persisted oracle: no mutation on failure; missing stays missing, imported graph unchanged. Cleanup/evidence: report. Source/harness [JTEST].

### JRN-006 — Legacy ambiguous save/delete recovery
Priority P0 · Level unit · Status NOT RUN · Pack isolated fake.

1. Run `ambiguousCommittedSaveRestoresAsEditAndPreservesNewerText` → commit-before-error restores same ID, newer draft survives.
2. Separately run `failedDeleteRetainsDraftAndCompletesOnlyAfterSuccessfulRetry` and `committedDeleteWithLostResultCompletesAfterRestoration` → delete error retains draft; committed absence reconciles.

Persisted oracle: one event after save recovery, zero after delete; never a second ID. Cleanup/evidence: individual reports. Source/harness [JTEST].

### JRN-007 — Legacy photo/context restoration and discard
Priority P1 · Level unit · Status NOT RUN · Pack isolated fake.

1. Run `photosSurviveRestorationAndExistingPhotoCanBeRemovedWithoutChangingText` → selected attachment IDs restore; removing one retains body.
2. Run `failedImportKeepsPreviousSelectionAndDiscardDoesNotWriteEntry` → failed import keeps prior photos; discard creates no Journal.
3. Run explicit-selection people/place tests from [JTEST] separately → empty set/null place clears links.

Persisted oracle: photo IDs and context exactly as asserted by each bounded fake; no unsaved entry. Cleanup/evidence: report and fake photo ownership operations; no real media required.

### JRN-008 — Legacy detail rendering, error and navigation gap
Priority P1 · Level isolated Compose · Status NOT RUN · Pack J.

1. Render [JournalDetailRoute][JDETAIL] directly with CJ01 repository result → exact title/body/tag/people/place visible; no Share before marking.
2. Return error or null from get; Retry after returning CJ01 → generic Journal load error clears, body returns; null is an error here, not separate missing screen.
3. Render CJ02 → marker note/importance, Share, original body; scroll long body → top-bar Back/Edit remain reachable.

Persisted oracle: J unchanged. Cleanup/evidence: dispose fixture/reset; semantics and snapshot. **BLOCKED:** direct route fixture absent and current app route redirects to Memory; older [JCT] does not execute this current app path.

## Memory, photos, library and sharing

### MEM-001 — Minimum direct capture
Priority P0 · Level manual · Status NOT RUN · Pack E.

1. Today > Record memory; leave note whitespace → Save disabled even with title.
2. Enter title ` C direct `, note ` C direct body `, timestamp D 12:00; Save.
3. Reopen after relaunch → title `C direct`, note `C direct body`, direct editor with Delete, no source block.

Persisted oracle: 1 MEMORY event + 1 marker; no Journal/Mood; event.note holds body and memory.note is null when context unset. Cleanup/evidence: reset; graph snapshot. Source [Memory screen][MEMSCREEN], [MREPO].

### MEM-002 — Edit date, title, note and optional importance
Priority P0 · Level manual · Status NOT RUN · Pack U.

1. Open CX02; set title empty, note `C moved`, D 07:45; expand importance, select 1 then 5 then tap 5 again → importance unset.
2. Save; inspect Timeline D and D-1 → entry moves to D, absent D-1.
3. Relaunch/reopen → note/time exact, title absent, importance null.

Persisted oracle: 4 events/3 markers, CX02 same ID and createdAt. Cleanup/evidence: reset; both dates and graph. Source [MEMVM], [MREPO].

### MEM-003 — Promotion keeps original, permits blank marker
Priority P0 · Level manual · Status NOT RUN · Pack U.

1. Timeline D > CM01 > Mark as memory → source score2/note `moodonly-low`; timestamp disabled; no editable direct title.
2. Leave marker note empty; Save → original CM01 gains marker.
3. Reopen → source unchanged and empty optional marker, importance unset.

Persisted oracle: 4 events, 2 moods, markers 4; no new MEMORY event; same CM01 ID. Cleanup/evidence: reset; source/marker snapshot. Source [MEMVM], [MREPO].

### MEM-004 — Marker context replaces links without changing source content
Priority P0 · Level manual · Status NOT RUN · Pack U.

1. Open CM02 via Mark/Edit memory; change marker note to `C revised marker`, importance to 1; deselect CP01, retain CP02; replace CL02 with CL01; replace tags with `c-amber`; Save.
2. Open CM02's Mood editor → original score5, energy4, stress1, note/time unchanged; updated people/place apply to same event.

Persisted oracle: 4 events/3 markers; CM02 source child unchanged, exactly CP02/CL01/c-amber links. Cleanup/evidence: reset; before/after graph. Marker does not own a separate copy of event context. Source [MREPO].

### MEM-005 — Unmark versus delete; cancellation
Priority P0 · Level manual · Status NOT RUN · Pack U.

1. Open CM02 marker > Unmark > Cancel → still marked; repeat and confirm → source CM02 remains mood, marker removed.
2. Open CX02 > Delete > Cancel → remains; repeat and confirm → CX02 removed entirely.
3. Relaunch → CM02 source fields intact, CX02 absent.

Persisted oracle: events 3/moods2/direct memories1/markers1 (CX01); CP/CL catalogs remain 2 each. Cleanup/evidence: reset; both confirmation texts and IDs. Source [MEMSCREEN], [MREPO].

### MEM-006 — Boundaries and normalized tags
Priority P1 · Level manual · Status NOT RUN · Pack E.

1. New direct memory; title `t`×200 and note `n`×50,000 → accepted; attempt 201/50,001 → retained accepted values plus respective errors.
2. Expand Tags; add ` C-Amber `, then `c-amber` → one selected tag; 50-character tag accepted, 51 rejected without replacing accepted pending input.
3. Change title to `C boundary`, note to `C body`; Save/reopen → these exact values and two unique tags.

Persisted oracle: 1 direct memory/marker; no duplicate normalized tag. Cleanup/evidence: reset; lengths, field semantics, tag IDs. Source [MEMSCREEN], [MEMVM], [Memory model][MEMMODEL]. The 51st distinct tag case is excluded: it would violate this pack's 50-record ceiling.

### MEM-007 — Draft, pending tag and explicit discard
Priority P0 · Level manual · Status NOT RUN · Pack E.

1. New memory; note `C unsaved`, expand Tags, type `C pending` without Add; rotate → note and pending text remain.
2. Close > Keep editing → both retained; close > Discard → editor exits.
3. Open fresh memory → empty note, no selected/pending C tag.

Persisted oracle: 0 events/tags, no unsaved entry written. Cleanup/evidence: reset; pending-input discard dialog. Source [MEMSCREEN], [Memory route][MEMROUTE]. Pending tag is not automatically included by Save; add it explicitly before a persistence test.

### MEM-008 — Write failure, ambiguous completion and protected load
Priority P0 · Level unit · Status NOT RUN · Pack isolated fake.

1. Run [MemoryEditorTest][MEMTEST] `ambiguousCreateRestoresAsEditAndKeepsNewerDraft` and `ambiguousPromotionRetryKeepsOriginalAndNewerMarkerNote` independently → same ID restored, newer draft retained.
2. Run `confirmedUnmarkLostResultReconcilesAfterRestorationWithoutDeletingOriginal`, `confirmedDirectDeleteLostResultReconcilesAfterRestoration` → respective source retained/deleted.
3. Run `missingImportedAndIncompleteRecordsStayProtected` → no invalid edit/create.

Persisted oracle: counts/identity remain those of each bounded fake; no duplicated original. Cleanup/evidence: method reports. Some Memory fake tests predate current photo-route access; if they fail on readiness, report prerequisite/harness mismatch and do not weaken production protection to pass them.

### MEM-009 — Add image, crop/rotate, apply and persist
Priority P0 · Level manual + photo oracle · Status NOT RUN · Pack C plus CA.

1. New memory note `C cropped`; More details > Add photo > select CA → secure crop editor opens; main Save blocked during photo operation.
2. Move left crop to 25%, keep top0/right100/bottom100; Rotate once clockwise → normalized crop becomes left0/top25%/right100%/bottom100%, rotation1; Apply.
3. Save entry; relaunch/reopen thumbnail/viewer → cropped/rotated 600×600 PNG derivative retained, source picker file unchanged.

Persisted oracle: 1 event/marker, exactly1 attached photo; derivative has distinct managed identity when transform applied; no unintended source attachment. Cleanup/evidence: reset; source/derivative hashes, crop state and attachment snapshot. Pixel oracle uses normalized crop/rotation in [Photo model][PHOTOEDIT] and [Android transform][PHOTOTRANSFORM]; CA is below working-image downsampling limits. Source [Photo editor][PHOTOUI], [photo session][PHOTOSESSION]. BLOCKED until CA exists.

### MEM-010 — Keep original and cancel crop
Priority P0 · Level manual · Status NOT RUN · Pack C plus CA/CB.

1. New memory note `C original`; select CA, Rotate and change crop, then Keep original → one uncropped original attachment.
2. Select CB then Cancel crop → returns to entry with only CA; note retained.
3. Save/reopen → CA is 800×600 with all original corners/TOP; CB absent.

Persisted oracle: 1 event/marker,1 attachment; no CB ownership after cleanup. Cleanup/evidence: reset; metadata/hashes. Source [MemoryPhotoEditor][MPHOTO], [PHOTOSESSION]. BLOCKED until images exist.

### MEM-011 — Crop reset and activity recreation
Priority P1 · Level manual · Status NOT RUN · Pack C plus CA.

1. New note `C rotate draft`; select CA; rotate twice and move top crop to25%; recreate activity → same pending source, rotation and crop restored, no attached duplicate.
2. Reset → full crop and zero rotation; Apply, Save/reopen → complete original orientation.

Persisted oracle: one entry/one attachment; pending source lease released after commit. Cleanup/evidence: reset; pending/edit state, dimensions. Source [PHOTOSESSION], [PHOTOUI]. BLOCKED until CA exists; process death recovery needs persistent staging storage, not just rotation.

### MEM-012 — Remove photo remains a draft until Save
Priority P0 · Level manual · Status NOT RUN · Pack U.

1. Open CX01; remove CB thumbnail → editor shows CA only.
2. Close > Discard; reopen → CA+CB both retained in saved entry.
3. Remove CB again, Save; relaunch → only CA.

Persisted oracle: events4/markers3; CX01 attachment count2 before save,1 after; other source text/context unchanged. Cleanup/evidence: reset; attachment IDs and ownership snapshot, not just thumbnail count. Source [MEMROUTE], [photo draft][PHOTODRAFT].

### MEM-013 — Viewer navigation, zoom, pan and return
Priority P1 · Level manual · Status NOT RUN · Pack U.

1. Open CX01 gallery's first thumbnail → viewer CA, Previous disabled, Next enabled.
2. Zoom + repeatedly → capped4×; pan controls bound image; Reset →1×, centered, Reset disabled. Double-tap →2× then1×.
3. Next → CB with reset transform, Next disabled; Previous → CA; Back/Close → same entry draft.

Persisted oracle: no saved writes, 4 events/2 attachments unchanged. Cleanup/evidence: reset; semantics including selected index/zoom and end controls. Source [viewer][VIEWER], [viewer state][VIEWERSTATE]. BLOCKED until images exist.

### MEM-014 — Image import/read failure and retry
Priority P0 · Level injection/manual · Status NOT RUN · Pack U.

1. Existing CX01: make PhotoRepository staging import fail for selected CB → import error offers Retry/Select again/Cancel; prior CA+CB selection and note stay intact.
2. Restore staging; Retry → exactly one new staged candidate; Cancel → no added attachment.
3. Inject viewer display read failure for CA → unavailable state with Retry/Close; restore read; Retry → CA visible without changing selection.

Persisted oracle: original2 photos/4 events unchanged. Cleanup/evidence: reset; failure type, lease/retry log. Seam [PHOTOSESSION], [VIEWER] PhotoRepository; **BLOCKED:** no checked-in full photo-failure UI harness. Existing [photo draft test][PTEST] covers a smaller isolated session; do not call revoked picker permission equivalent to every managed-file failure.

### MEM-015 — Photo count boundary without load stress
Priority P1 · Level manual · Status NOT RUN · Pack C plus CA.

1. New note `C twenty`; import/Keep original CA twenty times, waiting for each operation →20 distinct attachment IDs, Add photo disabled.
2. Remove one →19, Add enabled; add once →20.
3. Save/reopen →20 attachments; no21st.

Persisted oracle:1 event/marker,20 attachments (below entity ceiling50), no duplicate attachment IDs/paths. Cleanup/evidence: reset; list IDs/count. Source [photo limits][PHOTOMODEL], [MPHOTO]. BLOCKED until CA and ownership snapshot exist; a UI screenshot alone cannot prove distinct managed copies.

### MEM-016 — Library membership, order and calendar grouping
Priority P0 · Level manual · Status NOT RUN · Pack U.

1. More > Memories → exactly CX01, CX02, CM02; CM01 unmarked excluded.
2. Read newest-first → CX01 D11:00, CX02 D-1 17:00, CM02 D-2 20:00; one November2026 group.
3. Tap CX02, then cancel without edits → returns to library with same rows.

Persisted oracle:4 events/3 markers/library3, no write. Cleanup/evidence: reset; all IDs/order/group. Source [library][LIBRARY], [state][LIBSTATE]. A marked JOURNAL is intentionally excluded by supported types; verify separately with J (library0 despite CJ02 marker).

### MEM-017 — Library empty, initial error and retry
Priority P1 · Level manual + injection · Status NOT RUN · Pack E.

1. More > Memories → empty message plus Record memory action; no older-load action.
2. In a separate fixture, make MemoryRepository.observeLibrary fail before emission → error + `memory_library_retry`, not false empty state.
3. Restore repository; Retry → empty; Record memory > save note `C from library` → library has1 and save acknowledgment/View action opens saved entry.

Persisted oracle:0 before save,1 event/marker after. Cleanup/evidence: reset; empty/error distinguishable. Source [LIBRARY], [LIBSTATE], [NAV]. Error injection **BLOCKED** until isolated Compose repository launcher exists; empty/create portion runs manually.

### MEM-018 — Library bounded older load and anchor restoration
Priority P1 · Level injection/Compose · Status NOT RUN · Pack exactly31 direct memories.

1. Seed CXL01…CXL31 with title/note `C library 01`…`31`, occurring D12:00 minus index hours; no context/photos/tags → initial library30 newest, one Load older.
2. Fail next expansion emission; tap Load older → existing30 retained, older error/retry; restore and retry →31, no duplicates, no Load older.
3. Open oldest, Back/recreate →31 retained and anchor near the opened row; delete anchor in repository → nearest surviving neighbor restored without crash.

Persisted oracle:31 MEMORY/markers initially;30 after deletion; maximum31 per type. Cleanup/evidence: clear fixture; visible ID list, anchor/committed limit. Seam [LIBSTATE] MemoryLibraryStateHolder.observeLibrary; **BLOCKED:** exact31-row/error fixture not checked in. Do not use 50,000-row search tests.

### MEM-019 — Share preview defaults and editable text, no photo export
Priority P0 · Level manual · Status NOT RUN · Pack U.

1. Open CX01 without edits > Share → preview with heading `Cedar memory`, local date, `bodyonly-photo`, trailing `#Luma`; people/place disabled by default, no images exported.
2. Enable people/place → `Ada QA` and `Cedar Hall` included; turn Date off → date removed.
3. Edit preview to exactly `C reviewed export`; Share → Android chooser for text; dismiss chooser without selecting a recipient.

Persisted oracle: CX01 unchanged,4 events/2 photos. Cleanup/evidence: reset; preview/options and intercepted text payload if available. Do not actually send. Source [share route][SHAREROUTE], [SHAREFORMAT], [launcher][SHARELAUNCH]. CX01 memory.note is null so body fallback is used.

### MEM-020 — Marked-source sharing opt-in and dirty editor gate
Priority P0 · Level manual · Status NOT RUN · Pack U.

1. Open CM02's Memory editor > Share → MEMORY preview includes `markeronly-bright`; original `moodonly-high`, energy4, stress1 absent by default.
2. Enable source content → score5/source note appear; enable Energy/Stress →4/5 and1/5 included; people/place remain opt-in.
3. Back; change marker note to `C share revised` without saving → Share disabled/save-first feedback; Save then Share → updated marker preview.

Persisted oracle: CM02 remains MOOD, same ID/source values; only saved marker update persisted. Cleanup/evidence: reset; default/opt-in previews and dirty gate. Source [MEMROUTE], [SHAREROUTE], [SHAREFORMAT]. Mood-kind sharing instead exposes mood datetime and original mood note; it is a separate request kind.

### MEM-021 — Regeneration confirmation and export size validation
Priority P1 · Level manual · Status NOT RUN · Pack U.

1. CX01 > Share; edit preview to `C custom`; toggle Date → regeneration confirmation; Cancel → custom text/options retained.
2. Toggle Date again > Regenerate → custom text replaced by newly formatted preview/options.
3. Replace preview with spaces > Share → EMPTY error, no chooser. Replace with65,537 ASCII `a` > Share → TOO_LARGE; replace with65,536 ASCII `a` → chooser permitted, dismiss it.

Persisted oracle: source4 events unchanged; limit is UTF-8 bytes, not codepoints. Cleanup/evidence: reset; byte lengths and errors. Source [SHAREFORMAT], [share VM][SHAREVM]; no automated share test found. If platform text-entry cannot enter boundary content, BLOCKED until direct ViewModel fixture exists.

### MEM-022 — Stale or deleted share snapshot; refresh and retry
Priority P0 · Level injection/manual · Status NOT RUN · Pack U.

1. Open CX01 Share; mutate saved CX01 via repository to note `C newer saved`; attempt Share → stale feedback, no chooser; Refresh → fresh snapshot/options reset.
2. Delete CX01 via repository, then Share → unavailable; no chooser with old private text.
3. Independently inject repository.get failure at load → load error/Retry; restore get, Retry → preview returns.

Persisted oracle: exactly3 events after delete; no write by share and no unintended launch. Cleanup/evidence: reset; launch invocation count0, snapshot before/after. Seam [SHAREVM] TimelineRepository.get and injected launch function; **BLOCKED** until executor-owned ViewModel/Compose fixture is supplied; no existing share test harness.

### MEM-023 — Event detail missing/error/read-only and share launch failure
Priority P0 · Level injection/Compose · Status NOT RUN · Pack U.

1. Render [EventDetailRoute][DETAIL] for absent ID → `event_missing`, no Edit/Share; return failure → error + Retry; return CX01 → content recovered.
2. In a separate one-record fixture return ACTIVITY ID `00000000-0000-0000-0000-000000000601`, D11:00, title `C imported steps`, source HEALTH_CONNECT/sourceReference `c-readonly`, one activity detail type `steps`, value10/unit `count` → source/read-only text, no editable capture or promote controls.
3. In EntryShareViewModel supply launch returning UNAVAILABLE → visible launch error, text retained; retry with launcher recording text → once, same reviewed payload.

Persisted oracle: fixture events unchanged; missing remains absent, no create. Cleanup/evidence: dispose/reset; callback log. Existing [detail state test][DTEST] covers missing/error state flow; full route/share launch injection **BLOCKED** until isolated launcher exists. User cancellation of Android chooser does not equal UNAVAILABLE.

## People and Places

### CAT-001 — Empty catalogs, create and duplicate visible names
Priority P0 · Level manual · Status NOT RUN · Pack E.

1. More > People: empty People state offers create; Places tab empty offers create.
2. Create ` Ada QA ` with note ` neighbor orchard ` → trimmed saved CP01.
3. Create a second distinct person named `Ada QA`; create CL01 `Cedar Hall` → duplicate names allowed, each independently selectable by ID.

Persisted oracle:2 people/1 place/0 events; no name-based accidental overwrite. Cleanup/evidence: reset; UUIDs and both rows. Source [person VM][PVM], [person repo][PREPO], [place repo][PLREPO].

### CAT-002 — Required fields, name/note bounds and error retention
Priority P1 · Level manual, two independent variants · Status NOT RUN · Pack E each variant.

1. Variant Person, then reset and Variant Place: new editor with whitespace name → Save disabled.
2. Enter name `n`×200, note `q`×10,000 → accepted; attempt201/10,001 → old text retained and field errors.
3. Edit only name to `C bounded` → name error clears, note error remains; change note to `C note`, Save/reopen.

Persisted oracle:one respective catalog record,0 events; exact accepted name/note. Cleanup/evidence: reset each variant; error semantics/lengths. Sources [PVM], [place VM][PLVM], [person model][PMODEL], [place model][PLMODEL].

### CAT-003 — Catalog search matches notes and remembers separate tab queries
Priority P1 · Level manual · Status NOT RUN · Pack C.

1. People search `ORCHARD` → CP01 only; `no-such-catalog` → no-results message with create still available.
2. Set People search `Ada`; switch Places, search `east` → CL02 only.
3. Switch People → `Ada` retained; rotate, return Places → `east` retained; clear → both places.

Persisted oracle:people2/places2/events0, no writes. Cleanup/evidence: reset; per-tab text/results. Source [People][PEOPLE], [Places][PLACES], [context match][CONTEXT]. This note matching is catalog-local; global Search excludes notes.

### CAT-004 — Create from picker auto-selects only new result
Priority P0 · Level manual · Status NOT RUN · Pack C.

1. New mood score3/note `C picker`; More details > people picker > Create person; save CP03 name `Clara QA` → returns and selects CP03 once.
2. Place picker > Create place; save CL03 `Birch Room` → returns with CL03 selected.
3. Save mood/reopen → CP03/CL03 linked; CP01/CP02 not accidentally selected.

Persisted oracle:1 mood/event, people3/places3, one person and one place link. Cleanup/evidence: reset; catalog result IDs. Source [catalog result][CATRESULT], [Mood route][MOODROUTE]. Duplicate result delivery must be idempotent, not toggle the selected result off.

### CAT-005 — Picker cancel preserves draft and creates no catalog row
Priority P0 · Level manual · Status NOT RUN · Pack C.

1. New memory note `C picker cancel`; select CP01 and CL01.
2. Picker > Create person, type `C unsaved person`, Cancel > Keep editing then Cancel > Discard → returns; CP01 retained, people count2.
3. Repeat Place create/discard → CL01 retained; Save memory.

Persisted oracle:1 memory/marker, people2/places2, CP01+CL01 links only. Cleanup/evidence: reset; parent draft before/after. Source [MEMROUTE], [CATRESULT], [person route][PROUTE], [place route][PLROUTE].

### CAT-006 — Person and place histories are oldest first and cross-open
Priority P0 · Level manual · Status NOT RUN · Pack U.

1. People > CP01 → history order CM02 (Nov14), CM01 (Nov16 08:00), CX01 (11:00); every row exactly once.
2. Open CM01 then Back → returns to same person's history; source detail/score correct.
3. Places > CL02 → CM02 then CX02 (Nov15); open CX02 → correct memory editor/detail for that record.

Persisted oracle:U unchanged; person history3, place history2. Cleanup/evidence: reset; ordered IDs and Back destination. Source [PEOPLE], [PLACES], [NAV].

### CAT-007 — Rename propagates without changing links
Priority P0 · Level manual · Status NOT RUN · Pack U.

1. CP01 > Edit > rename `Ada Renamed`, note `C revised`, Save.
2. CL01 > Edit > rename `Cedar Renamed`, Save; open CX01 and CM01 → updated catalog labels, same IDs.
3. Global Search `Ada QA` →0; `Ada Renamed` →3; `Cedar Renamed` →2.

Persisted oracle:4 events,2 people/2 places, link IDs unchanged, CP01 history3. Cleanup/evidence: reset; before/after names and result counts. Source [PREPO], [PLREPO], [SQL].

### CAT-008 — Delete detaches links and preserves entries
Priority P0 · Level manual · Status NOT RUN · Pack U.

1. CP01 > Edit > Delete > Cancel → counts/history unchanged; then confirm Delete → list retains CP02 only.
2. CL01 > Edit > confirm Delete → CL02 only.
3. CX01/CM01 reopen → missing links removed, content intact; CM02 still CP02/CL02 and marked.

Persisted oracle:events4/markers3, people1/places1; CP01/CL01 absent, their join links0; Search filters for deleted IDs must return0 until removed.
Cleanup/evidence: reset; IDs, joins, no source deletion. Source [PREPO], [PLREPO], [Search persistence][SPERSIST].

### CAT-009 — Editor failure, lost result and retry identities
Priority P0 · Level unit · Status NOT RUN · Pack isolated fake, Person and Place separately.

1. Run [PersonEditorTest][PEDITTEST] and [PlaceEditorTest][PLEDITTEST] method `failedLoadKeepsDirtyDraftAndRetryUnlocksIt` → dirty input retained and retry unlocks.
2. Run `committedCreateWithLostResultRecoversSameIdAndNewerDraft` → same ID edited, no duplicate.
3. Run `failedDeleteRetainsDraftAndLostCommittedDeleteRestoresCompletion` → error preserves draft; confirmed missing record reconciles.

Persisted oracle:per fake one record after create recovery,zero after delete; no device writes. Cleanup/evidence: separate reports and dispatcher teardown. Do not use airplane mode as local repository injection.

### CAT-010 — Missing catalogs/detail errors and retry
Priority P1 · Level unit + injection/Compose · Status NOT RUN · Pack C.

1. Run Person/Place `missingEditCannotRecreateDeletedPerson` / `missingEditCannotRecreateDeletedPlace` → editing absent ID cannot Save-create.
2. In isolated detail fixture return null from observeHistory → missing state, no Edit; throw → error with Retry; restore CP01/CL01 history → exact summary and empty-history state.
3. Independently throw observePeople/observePlaces → list error; Retry after restoration →2 rows, no false empty-catalog create-only state.

Persisted oracle:C unchanged; absent ID stays absent. Cleanup/evidence: reports/fixture disposal and state screenshots. Source [People state][PSTATE], [Places state][PLSTATE]. Unit methods are executable; full error observation launcher **BLOCKED** until provided.

## Search

### SRCH-001 — Empty library versus initial prompt
Priority P0 · Level manual · Status NOT RUN · Run E, reset then U.

1. E: Search, no query/filters → empty-library text, reset/clear disabled, no results.
2. U: Search with no criteria → prompt plus4 saved rows newest-first CX01, CM01, CX02, CM02; result count criteria label absent.
3. Type `no-such-capture`, wait at least300ms →0 result count and no-match state; Clear all → initial prompt and4 rows.

Persisted oracle:0 for E,4 for U; no mutation. Cleanup/evidence: reset each; three distinct states. Source [Search route][SEARCH], [SearchStatesTest][SSTEST].

### SRCH-002 — Text scope, case and hidden body/note
Priority P0 · Level manual · Status NOT RUN · Pack U.

1. Search `cEdAr`, settle → CX01 and CM01 (2: title/place matching).
2. Replace with `Ada QA` → CX01, CM01, CM02 (3 via linked person).
3. Independently search `bodyonly`, `moodonly`, `markeronly`, `orchard`, `north entrance` →0 each; `c-amber` →CX01/CX02 (2).

Persisted oracle:events4; search never broadens to bodies/notes/catalog notes. Cleanup/evidence: reset; ordered IDs/query/count per value. Source [SQL], [SPERSIST]. ASCII case folding does not promise locale-specific accent-insensitive matching.

### SRCH-003 — Literal wildcard and quote safety
Priority P0 · Level manual · Status NOT RUN · Pack E.

1. Create direct memory CY01 D11:00 title `100%_done 'quote' \\path`, note `C literal`; CY02 D10:00 title `Plain`, note containing the same token.
2. Search `%_`, then `'quote'`, then `\\path`, then `100%` →CY01 only for each.
3. Search `' OR 1=1 --` →0; all entries remain.

Persisted oracle:2 MEMORY/markers, no query writes; wildcard chars literal, not wildcard match. Cleanup/evidence: reset; exact raw query/payload and result IDs. Source [SQL], [SPERSIST] `matchesOnlyTitlesAndLinkedNamesWithLiteralWildcardsAndPersian` is a bounded data-method alternative.

### SRCH-004 — Inclusive date range and local midnight
Priority P0 · Level manual · Status NOT RUN · Pack U.

1. Filters > From Nov16, Through Nov16 > Done →2 rows CX01/CM01.
2. Through Nov15 while From Nov16 → invalid range feedback both dialog/active chips; no rows/no query result presented as valid.
3. Remove From chip →Through Nov15 returns CX02/CM02 (2); Clear filters →4, text unchanged.

Persisted oracle:4 unchanged. Cleanup/evidence: reset; dates/zone and invalid state. Source [Search filters][SFILTERS], [SVM]. Exact 00:00 and23:59:59.999 versus next midnight requires additional2-record repository fixture (below50); BLOCKED until seeded; use date-bounds model instead of guessing timezone UTC.

### SRCH-005 — OR within category; AND across categories
Priority P0 · Level manual · Status NOT RUN · Pack U.

1. Choose c-amber+c-blue tags →CX01/CX02/CM02 (3), CX02 once despite two tags.
2. Add CP01+CP02 people →still3; add CL01 →CX01 only.
3. Add From=Through D and text `Cedar` →CX01 only (1); switch place to CL02 →0.

Persisted oracle:4 unchanged; multi-tags/people are “any of”, categories intersect. Active count with2 tags+2people+place+2dates=7; text excluded. Cleanup/evidence: reset; chips/count and IDs. Source [SQL], [SFILTERS], [SVM].

### SRCH-006 — Remove one filter, reset filters, clear all
Priority P0 · Level manual · Status NOT RUN · Pack U.

1. Query `River`, choose c-amber, CP02, CL02, From Nov15, Through Nov16 →CX02 only; filter count5.
2. Remove c-amber chip →CX02 still, count4; Reset filters →query `River` retained, CX02+CM02 (2), count0.
3. Clear all →empty query, all4 rows/prompt; Reset disabled.

Persisted oracle:4 unchanged. Cleanup/evidence: reset; query/chips before/after. Executable [SSTEST] `filterResetPreservesQueryAndClearAllRemovesBoth` uses its own small fixture; source [active filters][ACTIVE].

### SRCH-007 — Picker search, cancel/done and live selection
Priority P1 · Level manual · Status NOT RUN · Pack U.

1. Filters > People, search `Ada` →CP01 only; select, Done > Done →3 results.
2. Reopen Places, select CL01; dismiss parent via outside/back →selection remains applied (filters mutate immediately, Done closes).
3. Reopen place picker > Clear →place unset; people CP01 retained;3 results.

Persisted oracle:4 unchanged, active criteria not a cancelable transactional form. Cleanup/evidence: reset; picker selected state and results. Source [SFILTERS], [option picker][SOPTIONS].

### SRCH-008 — Text cap, debounce and late result isolation
Priority P1 · Level manual + unit · Status NOT RUN · Pack U.

1. Enter179 ASCII characters →no limit helper;180 →helper; paste201 →field truncated to200.
2. Replace quickly with `Cedar`, then `River` within300ms →settled rows CX02/CM02 only, never stale Cedar result.
3. Run [SearchViewModelTest][SVTEST] `lateUncooperativeResultCannotOverwriteNewInput` →uncancellable old result rejected by generation.

Persisted oracle:4 unchanged, current query River/count2. Cleanup/evidence: reset; text length/time-stamped callback trace for unit. Source [SVM], [SEARCH]. No performance threshold/load claim is made.

### SRCH-009 — Result navigation, edits and lifecycle restoration
Priority P0 · Level manual · Status NOT RUN · Pack U.

1. Query `Cedar`+CP01 →CX01/CM01; open CX01 > Edit, title `Oak memory`, clear place and Save; Back Search →only CM01 matches Cedar.
2. Rotate/Background/foreground Search →same query/person/date criteria retained and fresh rows.
3. Clear criteria →4 entries, edited CX01 shown once; no lost capture.

Persisted oracle:4 same IDs; CX01 title Oak memory/place null, other fields untouched. Cleanup/evidence: reset; return destination/query and repository snapshot. Source [NAV], [SVM] lifecycle stop/start and observeChanges.

### SRCH-010 — Search/options failure, retry and deleted selected ID
Priority P0 · Level unit + injection/Compose · Status NOT RUN · Pack U.

1. Run [SVTEST] `errorRetryRetainsFiltersAndRestorationRetainsDeletedSelections` →error keeps criteria; Retry same query; deleted selected ID retained, not broadened.
2. In fixture fail observeOptions only →option picker error/Retry; existing result query independent. Restore options →same selected IDs, names recover.
3. Select CP01 then delete it externally; Search shows unavailable chip/ID and0 until removed; removing chip restores4.

Persisted oracle:events4; after delete people1, selected missing ID matches0. Cleanup/evidence: reset; unknown chip/error and query/callback trace. Page failure can use existing [SSTEST] Fixture.pageFailure; options-failure UI launcher **BLOCKED** until injected.

### SRCH-011 — Search paging boundary and zone requery without stress
Priority P1 · Level unit/repository fixture · Status NOT RUN · Pack exactly50 visible events max.

1. Seed50 direct memories at D12:00 minus index minutes, titles `C page 01`…`50`, no tags/context; query all →one50-row page, total50, no Later.
2. Reopen result near end then return →all50 remain reachable, no duplicated keys.
3. On U-size isolated fixture, From=Through D; change foreground zone to UTC →requery uses UTC bounds, query/filters retained. Use explicit one event at D00:15 Tehran (=Nov15 20:45Z) to demonstrate exclusion after zone change.

Persisted oracle:fixture50 events/markers (never51), or zone variant1; no mutation during query. Cleanup/evidence: close/reset; page offsets/count/IDs and both bounds. Seam [SVM] `start(zone)` and `rememberResult`; **BLOCKED** until bounded fixture supplied. Existing SVTEST paging methods seed >50 and are excluded from this plan; do not run them. Baseline manual U is one page only.

## Source index

[DESIGN]: ../../../AGENTS.md
[TOKENS]: ../../../app/src/main/java/com/luma/lifeos/ui/common/LumaUiTokens.kt
[COMPONENTS]: ../../../app/src/main/java/com/luma/lifeos/ui/common/LumaUiComponents.kt
[SCAFFOLD]: ../../../app/src/main/java/com/luma/lifeos/ui/common/AppPageScaffold.kt
[COVERAGE]: ../../luma-redesign/route-coverage.md
[CHECKLIST]: ../../luma-redesign/ui-consistency-checklist.md
[NAV]: ../../../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt
[MOODSCREEN]: ../../../app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorScreen.kt
[MOODVM]: ../../../app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorViewModel.kt
[MOODROUTE]: ../../../app/src/main/java/com/luma/lifeos/ui/mood/MoodEditorRoute.kt
[MOODSTORE]: ../../../app/src/main/java/com/luma/lifeos/ui/mood/MoodDraftStore.kt
[MOODNOTE]: ../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/model/MoodNotePolicy.kt
[MOODTEST]: ../../../app/src/test/java/com/luma/lifeos/ui/mood/MoodEditorTest.kt
[JVM]: ../../../app/src/main/java/com/luma/lifeos/ui/journal/JournalEditorViewModel.kt
[JDETAIL]: ../../../app/src/main/java/com/luma/lifeos/ui/journal/JournalDetailRoute.kt
[JTEST]: ../../../app/src/test/java/com/luma/lifeos/ui/journal/JournalEditorTest.kt
[JCT]: ../../../app/src/androidTest/java/com/luma/lifeos/JournalCaptureTest.kt
[MCT]: ../../../app/src/androidTest/java/com/luma/lifeos/MemoryCaptureTest.kt
[MEMVM]: ../../../app/src/main/java/com/luma/lifeos/ui/memory/MemoryEditorViewModel.kt
[MEMSCREEN]: ../../../app/src/main/java/com/luma/lifeos/ui/memory/MemoryEditorScreen.kt
[MEMROUTE]: ../../../app/src/main/java/com/luma/lifeos/ui/memory/MemoryEditorRoute.kt
[MEMMODEL]: ../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/model/Memory.kt
[MREPO]: ../../../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomMemoryRepository.kt
[MEMTEST]: ../../../app/src/test/java/com/luma/lifeos/ui/memory/MemoryEditorTest.kt
[MPHOTO]: ../../../app/src/main/java/com/luma/lifeos/ui/memory/MemoryPhotoEditor.kt
[PHOTOSESSION]: ../../../app/src/main/java/com/luma/lifeos/ui/memory/MemoryPhotoEditSession.kt
[PHOTODRAFT]: ../../../app/src/main/java/com/luma/lifeos/ui/photos/PhotoDraftSession.kt
[PHOTOMODEL]: ../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/model/Photo.kt
[PHOTOEDIT]: ../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/model/PhotoEdit.kt
[PHOTOUI]: ../../../app/src/main/java/com/luma/lifeos/ui/photos/PhotoEditScreen.kt
[VIEWER]: ../../../app/src/main/java/com/luma/lifeos/ui/photos/PhotoViewer.kt
[VIEWERSTATE]: ../../../app/src/main/java/com/luma/lifeos/ui/photos/PhotoViewerState.kt
[PTEST]: ../../../app/src/test/java/com/luma/lifeos/ui/photos/PhotoDraftSessionTest.kt
[LIBRARY]: ../../../app/src/main/java/com/luma/lifeos/ui/memory/MemoryLibraryRoute.kt
[LIBSTATE]: ../../../app/src/main/java/com/luma/lifeos/ui/memory/MemoryLibraryState.kt
[DETAIL]: ../../../app/src/main/java/com/luma/lifeos/ui/timeline/EventDetailRoute.kt
[DTEST]: ../../../app/src/test/java/com/luma/lifeos/ui/timeline/EventDetailStateTest.kt
[SHAREROUTE]: ../../../app/src/main/java/com/luma/lifeos/sharing/EntryShareRoute.kt
[SHAREVM]: ../../../app/src/main/java/com/luma/lifeos/sharing/EntryShareViewModel.kt
[SHAREFORMAT]: ../../../app/src/main/java/com/luma/lifeos/sharing/EntryShareFormatter.kt
[SHARELAUNCH]: ../../../app/src/main/java/com/luma/lifeos/sharing/AndroidTextShareLauncher.kt
[PEOPLE]: ../../../app/src/main/java/com/luma/lifeos/ui/people/PeopleRoute.kt
[PLACES]: ../../../app/src/main/java/com/luma/lifeos/ui/places/PlacesRoute.kt
[PSTATE]: ../../../app/src/main/java/com/luma/lifeos/ui/people/PeopleState.kt
[PLSTATE]: ../../../app/src/main/java/com/luma/lifeos/ui/places/PlacesState.kt
[PVM]: ../../../app/src/main/java/com/luma/lifeos/ui/people/PersonEditorViewModel.kt
[PLVM]: ../../../app/src/main/java/com/luma/lifeos/ui/places/PlaceEditorViewModel.kt
[PROUTE]: ../../../app/src/main/java/com/luma/lifeos/ui/people/PersonEditorRoute.kt
[PLROUTE]: ../../../app/src/main/java/com/luma/lifeos/ui/places/PlaceEditorRoute.kt
[PREPO]: ../../../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomPersonRepository.kt
[PLREPO]: ../../../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomPlaceRepository.kt
[PMODEL]: ../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/model/Person.kt
[PLMODEL]: ../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/model/Place.kt
[PEDITTEST]: ../../../app/src/test/java/com/luma/lifeos/ui/people/PersonEditorTest.kt
[PLEDITTEST]: ../../../app/src/test/java/com/luma/lifeos/ui/places/PlaceEditorTest.kt
[CATRESULT]: ../../../app/src/main/java/com/luma/lifeos/ui/navigation/CatalogEditorResult.kt
[CONTEXT]: ../../../app/src/main/java/com/luma/lifeos/ui/common/ContextOptionPicker.kt
[SEARCH]: ../../../app/src/main/java/com/luma/lifeos/ui/search/SearchRoute.kt
[SVM]: ../../../app/src/main/java/com/luma/lifeos/ui/search/SearchViewModel.kt
[SFILTERS]: ../../../app/src/main/java/com/luma/lifeos/ui/search/SearchFilters.kt
[SOPTIONS]: ../../../app/src/main/java/com/luma/lifeos/ui/search/SearchOptionPicker.kt
[ACTIVE]: ../../../app/src/main/java/com/luma/lifeos/ui/search/ActiveSearchFilters.kt
[SQL]: ../../../core/data/src/main/kotlin/com/luma/lifeos/data/search/SearchSql.kt
[SPERSIST]: ../../../core/data/src/androidTest/kotlin/com/luma/lifeos/data/SearchPersistenceTest.kt
[SVTEST]: ../../../app/src/test/java/com/luma/lifeos/ui/search/SearchViewModelTest.kt
[SSTEST]: ../../../app/src/androidTest/java/com/luma/lifeos/SearchStatesTest.kt

[PHOTOTRANSFORM]: ../../../core/data/src/main/kotlin/com/luma/lifeos/data/media/AndroidPhotoTransform.kt
