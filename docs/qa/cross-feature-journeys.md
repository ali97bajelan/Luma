# Cross-feature QA journeys

All journeys are NOT RUN. Use only disposable fixtures. A step failure stops that journey; capture actual result and file a defect before cleanup. Preserve exact IDs and counts between checkpoints.

## JOURNEY-001 — Mood to Review sources · P0 · E

1. Today > Check in; set D 09:00, score 4, energy 2, stress 3; Save. Expected: 1 Mood event.
2. Timeline D. Expected: 1 entry, 0 habit logs. Open it. Expected: same timestamp/score.
3. Today > Summary. Expected: 1 sample, mean 4/5. Review current week. Expected: 1 entry and 1 mood sample.
4. Review Mood sources; open the source; Back. Expected: one source with same identity, selected week retained.
5. Search a title/linked catalog value supported by the Search document. Do not assert body search unless that file says the current implementation indexes it.

Oracle: 1 event, 1 Mood, no memory marker. Reset E.

## JOURNEY-002 — Historical Memory across date views · P0 · E

1. Calendar select D−1 > Add Memory. Enter title QA journey memory, body QA journey content, timestamp 2026-11-15 18:30 Asia/Tehran; Save.
2. Calendar D−1. Expected: one Memory row/marker. Open and verify title/time.
3. Timeline D−1 and Today D−1. Expected: the same event appears on that date.
4. Review previous full week. Expected: Memory count includes it once; source/detail opens same identity.
5. Calendar D. Expected: no duplicate row. Reopen Memory without edits.

Oracle: 1 event, 1 direct Memory, one timestamp. Reset E.

## JOURNEY-003 — Task from Today through Calendar · P0 · E

1. Today > add task; create QA journey task due D+1; Save. Expected: 1 open task.
2. Calendar D+1. Expected: marker and one task. Complete it. Expected: row leaves open agenda.
3. More > Tasks > Completed; reopen the same task. Expected: same ID once and open.
4. Today D. Expected: tomorrow task absent. Calendar D+1. Expected: task present.
5. Edit title to QA journey task edited and due date to D+2; Save. Expected: absent D+1, present once D+2.

Oracle: one task identity, exact final title/date, open. Reset E.

## JOURNEY-004 — Habit log, undo, Timeline, Review · P1 · E

1. Create daily habit QA journey steps, target 2, starts D, zone Tehran.
2. Habits D tap +1. Expected: one log, count 1/2.
3. Tap Undo while snackbar is visible. Expected: count 0/2. Wait until feedback expires; tap +1 once.
4. Timeline D. Expected: one habit log and zero life-event entries.
5. Review current week and habit detail. Expected: 1 repetition on D. Tap +1 once more; leave/reopen app.

Oracle: 1 habit, 2 logs D, zero events, count 2/2 persisted. Reset E.

## JOURNEY-005 — Memory edit/delete invalidation · P1 · G

1. Open G-E1 from Timeline D; change title to QA sunrise edited; Save. Expected: same identity.
2. Verify title in Today, Calendar, Memory Library, Review source and exact Search title query.
3. Delete G-E1 and confirm. Expected: gone from all projections/search; G-M1 remains.
4. Confirm Calendar/Timeline selected dates remain coherent and deleted detail is no longer accessible.
5. Open G-E2. Expected: previous-date record unaffected.

Oracle after edit: 5 events. After deletion: 4 events, 1 Memory, 3 Moods. Reset G.

## JOURNEY-006 — Catalog links and Search · P1 · capture pack C

1. Create QA Journey Alex and QA Journey Park.
2. Create one Mood and one Memory linked to both where those editor controls exist.
3. Open People and Places details. Expected: linked events appear under correct catalog entries.
4. Search person/place names. Expected: matching records. Search unindexed notes only according to Search file's actual field contract.
5. Edit catalog names. Expected: source detail and Review use updated name with same event identities.
6. Delete a catalog item only when UI permits. If source does not define relationship deletion semantics, stop and mark that assertion BLOCKED; do not guess.

Oracle: 2 events and one person/place link per event; reset C.

## JOURNEY-007 — Backup/restore replace · P0 · F1/FB from system file

1. Create F1 and export using BAK-001. Keep an untouched copy.
2. Create the live-only and same-identity modifications specified by FB.
3. Restore once and cancel. Expected: live state unchanged. Restore again and confirm.
4. Follow BAK-004 exact oracle: archive identities/fields restored; live-only row removed.
5. Relaunch and compare counts/IDs.

Oracle: original F1 identities and fields. Never use personal/only-copy data. Cleanup disposable install and archive.

## JOURNEY-008 — Persistent settings preserve data · P1 · G

1. Today select D−1; open Settings, set Dark, return. Expected: selected date/data retained.
2. Force-stop/relaunch. Expected: Dark persists.
3. Set Persian. Reopen Today/Calendar. Expected: localized RTL display, same record identities/date values.
4. Switch calendar display Persian then Gregorian. Expected: stored ISO dates unchanged.
5. Restore English/Light/Gregorian.

Oracle: G record IDs/counts unchanged; preferences only changed.

## JOURNEY-009 — Photo select/crop/cancel · P1 · capture U with assets

1. Create Memory with CA/CB. Crop using specified supported operation, Keep original if offered, Save.
2. Reopen Library/gallery/viewer. Expected: thumbnail/full image and crop relation match owning case's oracle.
3. Change crop in edit, Cancel. Expected: saved attachment unchanged.
4. Retry/decode failure is BLOCKED unless the capture document's repository fixture is available.

Oracle: exact attachment IDs/count; reset U/remove disposable assets. Prerequisite: valid image assets.

## JOURNEY-010 — Lock/restricted capture/recovery · P1 · FL

1. Follow LOCK-001 on disposable install and save recovery code offline in a disposable note.
2. Lock app, open Restricted Capture. Discard one valid draft; create and Save a second.
3. Navigate to gated app area. Expected: gate blocks protected content until valid unlock.
4. Unlock. Expected: explicitly saved capture appears once.
5. Test recovery only on separate disposable clone/snapshot if recovery resets data. Check screenshots/Recents only per lock contract.

Oracle: only saved capture adds a record. Never attach recovery code to evidence.

## JOURNEY-011 — Interrupted editor and picker · P1 · G

1. Open G-T1, replace title with QA interrupted; do not Save.
2. Open due date picker and Back. Expected: editor returns with draft title/date intact per TASK case.
3. Rotate/background using the owning lifecycle steps. Expected: specified draft behavior.
4. Discard; More > Tasks reopen G-T1.

Oracle: original task ID/title/due unchanged and no extra task. Reset G.
