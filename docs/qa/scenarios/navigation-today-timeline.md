# Navigation, Today and Timeline

All cases **NOT RUN**. Manual/device unless a case states harness. Baseline and exact G/E setup: [fixtures](../fixtures.md). Recreate the named fixture before each case. Save a screenshot of every failed checkpoint plus logcat/time; always reopen mutated records to confirm persistence. Cleanup: return to baseline E/G before the next case.

Sources: [navigation](../../../app/src/main/java/com/luma/lifeos/ui/navigation/AppDestinations.kt), [wiring](../../../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt), [Today route](../../../app/src/main/java/com/luma/lifeos/ui/today/TodayRoute.kt), [Today screen](../../../app/src/main/java/com/luma/lifeos/ui/today/TodayScreen.kt), [Today state](../../../app/src/main/java/com/luma/lifeos/ui/today/TodayState.kt), [Timeline](../../../app/src/main/java/com/luma/lifeos/ui/timeline/TimelineRoute.kt), [Timeline state](../../../app/src/main/java/com/luma/lifeos/ui/timeline/TimelineViewModel.kt).
Existing harnesses: [AppNavigationTest](../../../app/src/androidTest/java/com/luma/lifeos/AppNavigationTest.kt), [TodayScreenTest](../../../app/src/androidTest/java/com/luma/lifeos/TodayScreenTest.kt), [TimelineFlowTest](../../../app/src/androidTest/java/com/luma/lifeos/TimelineFlowTest.kt). These are references, not evidence of this run.

## NAV-001 — Fresh launch and primary destinations

Priority: **P0** · Status: **NOT RUN** · Setup: **E**.

1. Launch Luma from the launcher → Today is selected; no old entries appear.
2. Tap Timeline, Calendar, Review, More, then Today → each shows its own heading and selected navigation state; exactly five primary destinations are available in that order.
3. On Today press system Back, then launch again → no crash; the app can reopen normally.

Persisted oracle: No user record is created by browsing.

## NAV-002 — More links and return targets

Priority: **P0** · Status: **NOT RUN** · Setup: **G**.

1. From More open Tasks, press Back; repeat for Habits, Focus, Memories, People & places, and Settings → each opens the matching section and returns to More.
2. On People & places switch to Places and open QA Park → correct place details; Back returns to its list.
3. Repeat More → Tasks → open QA due today → Back twice → More; no unrelated editor or duplicate task is opened.

Persisted oracle: All G counts/fields unchanged; Focus setup alone creates no saved session.

## NAV-003 — Tab date context versus Calendar selection

Priority: **P1** · Status: **NOT RUN** · Setup: **G**.

1. On Today select D−7 with the day picker → two entries for that date.
2. Tap Timeline → D−7 remains selected and exactly G-M3/G-E2 are shown.
3. Open Calendar and select D+1; return to Today → Today retains shared D−7 selection rather than adopting Calendar's independent date.
4. Tap Today in the day control → D entries return; Timeline now uses D too.

Persisted oracle: No writes; Calendar selection is independent of shared Today/Timeline date.

## NAV-004 — Quick Capture dismissal

Priority: **P0** · Status: **NOT RUN** · Setup: **G**.

1. On Timeline tap the capture FAB → sheet opens with current supported capture actions.
2. Dismiss by system Back → Timeline restored; reopen and dismiss by tapping the scrim → same result.
3. Switch to Review, open/dismiss capture → original week and scroll context remain usable.

Persisted oracle: Still 5 life events, 5 tasks and 2 habit logs; opening/dismissing creates nothing.

## NAV-005 — Quick Capture routes and cancel

Priority: **P0** · Status: **NOT RUN** · Setup: **G**.

1. On Timeline open capture and choose Mood → a new Mood editor, not existing G-M1.
2. Back and discard any changed draft; reopen capture and choose Memory → new Memory editor. Cancel/discard.
3. Choose Task from capture → new Task editor with no existing task selected; cancel. Choose Log a habit → existing Habits section.

Persisted oracle: No new saved event/task/log from these navigation-only actions; do not tap +1.

## NAV-006 — Historical Today capture chooses date

Priority: **P1** · Status: **NOT RUN** · Setup: **G**.

1. On Today select D−7; tap Mood → date-choice dialog appears.
2. Choose selected date → editor timestamp falls on D−7. Cancel/discard.
3. Repeat and choose Now → timestamp falls on D. Cancel/discard.
4. Repeat and dismiss dialog with Back → Today still displays D−7, no editor remains.

Persisted oracle: No new record; selected historical context remains intact.

## NAV-007 — Back stack and recreation

Priority: **P1** · Status: **NOT RUN** · Setup: **G**.

1. More → Tasks → open QA due today. Rotate twice with no edits → same task/title/due date.
2. Press Back → Tasks; Back → More. Tap More repeatedly → no stack of duplicate More screens.
3. Open Timeline D−7; switch to More then Timeline → selected date remains D−7.

Persisted oracle: No duplicated or changed records. Capture video if an unexpected screen or stale header appears.

## TOD-001 — Empty Today is actionable

Priority: **P0** · Status: **NOT RUN** · Setup: **E**.

1. Open Today → recent entries show an empty message; no fabricated mood, memory or imported measurements.
2. Open each available Mood/Memory capture then cancel → return to Today.
3. Open Tasks/Habits empty-state actions → correct destinations; return.

Persisted oracle: Zero saved user records; unavailable integration data is not labeled measured zero.

## TOD-002 — Recent entries, counts and order

Priority: **P0** · Status: **NOT RUN** · Setup: **G**.

1. On Today select D → entry summary is 3 entries and 2 habit logs, not 5 entries.
2. Read recent entries → QA sunrise at 11:00, Mood score 4 at 10:00, Mood score 2 at 09:00; no previous Monday entries.
3. Open each row → editable local mood/memory opens the matching ID/content; cancel without edits.

Persisted oracle: 5 total events; 3 on D. Habit repetitions are separate counts.

## TOD-003 — Historical Today hides live task panel

Priority: **P1** · Status: **NOT RUN** · Setup: **G**.

1. On Today D confirm the task panel is present.
2. Select D−7 → historical-date notice appears; today's task action panel is absent rather than relabeled as historical tasks.
3. Tap the date notice/current-day action → D restored; live task panel reappears.

Persisted oracle: No task or due date changes; G-T1/T2 remain open.

## TOD-004 — Daily summary uses saved samples

Priority: **P1** · Status: **NOT RUN** · Setup: **G**.

1. Today D → expand Daily summary → 2 mood samples with score average 3/5; optional energy/stress average 3/5 if displayed.
2. Change date to D−7 and expand summary → score 5/5 from 1 sample; missing energy/stress remain unavailable.
3. Return D → numbers refresh to D, without a temporary previous-day number presented as current data.

Persisted oracle: Records unchanged. Optional integrations off: no invented activity/usage totals.

## TOD-005 — Saved edit refreshes all entry consumers

Priority: **P0** · Status: **NOT RUN** · Setup: **G**.

1. Open G-M1 from Today; change score 2 to 3; Save → same event returns, not a second mood.
2. Reopen G-M1 → score 3. Expand summary → mean score 3.5/5 (or localized rounded format consistent with app formatter).
3. Open Timeline D and Review current week → score/source and sample counts reflect 3 and 4; still 2 mood samples.

Persisted oracle: Still 5 events/3 Mood details; D mood score sum 7, sample count 2. Reset G after case.

## TOD-006 — Failure is separate from empty

Priority: **P1** · Status: **NOT RUN** · Setup: **existing TodayScreenTest harness**.

1. Run the harness case for Today error state with its failing LifeEventRepository (see automated-test instructions); capture error and Retry.
2. Restore successful repository emission/re-run success fixture → retry loads matching saved records; empty message is not used for a load failure.

Persisted oracle: Read failure makes no writes; G data unchanged.

Level: Compose UI/harness. For a full-app injected transient failure, no toggle is provided; mark that extension BLOCKED until a test build supplies the repository seam. Existing error rendering alone does not prove end-to-end recovery.

## TL-001 — Mixed feed counts and exact targets

Priority: **P0** · Status: **NOT RUN** · Setup: **G**.

1. Timeline D, app usage hidden → summary 3 entries + 2 habit logs; five feed items.
2. Open G-E1 and G-M1 → exact Memory/Mood record, correct timestamp; return.
3. Open each QA water log row → habit detail/history targets that particular log ID, not a new log or another date.

Persisted oracle: No new logs or events. Record displayed log times/IDs to distinguish identical +1 rows.

## TL-002 — Day boundary and stale result prevention

Priority: **P1** · Status: **NOT RUN** · Setup: **G**.

1. Select D−7 → only G-M3/G-E2.
2. Quickly select D, D−7, D+1 → final heading D+1 and empty feed; no D rows beneath that heading.
3. Return D → original five feed items, correct counts.

Persisted oracle: No writes; no duplicated or lost rows from cancellation.

## TL-003 — View options are functional disclosure

Priority: **P1** · Status: **NOT RUN** · Setup: **G**.

1. Open Timeline View options → real controls and tracking-zone context expand; no label-only fake compact mode.
2. Close/reopen disclosure → no data/date change.
3. Toggle app usage on then off; background/reopen → chosen usage visibility persists. With no usage imported, user event/log counts stay unchanged.

Persisted oracle: 5 events/2 logs; display preferences only are mutated. Reset usage visibility off.

## TL-004 — Small list has no false paging

Priority: **P1** · Status: **NOT RUN** · Setup: **B50**.

1. Timeline D with usage hidden → exactly 50 Memory entries; no infinite spinner.
2. Scroll to first and final record → every QA memory 01..50 appears once, newest timestamp first.
3. At end inspect paging → no Earlier page action for nonexistent rows; switch dates away/back → same total 50.

Persisted oracle: 50 Memory events only; no paging duplicates. This is first-window coverage, not >150 pagination coverage.

## TL-005 — Deletion refresh and empty day

Priority: **P1** · Status: **NOT RUN** · Setup: **G**.

1. Timeline → G-E1 → delete direct Memory and confirm → return without opening a deleted record.
2. Timeline D now has 2 entries + 2 logs; Today D also has 2 entries.
3. Calendar D → no memory row/marker for G-E1; Search its unique title → no hit.

Persisted oracle: 4 total events, 1 Memory detail remains G-E2; 3 Mood details unchanged. Reset G.

## TL-006 — Read error and observer lifecycle

Priority: **P1** · Status: **NOT RUN** · Setup: **TimelineFlowTest / TimelinePagingRouteTest harness**.

1. Use the failing-repository/late-day-result scenarios in the existing Timeline harness → error/Retry replaces endless loading, and old-day completion does not override new selection.
2. Background and resume the harness/activity → observation restarts once and successful data replaces error without duplicate keys.

Persisted oracle: Read-only; state sequence and exact record keys match source fixture.

Level: Compose UI/harness. Run only matching existing test methods whose fixture respects the cap. Larger paging/window tests are excluded; full-app fault injection needs a test build and is BLOCKED without it.

