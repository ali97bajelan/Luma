# Calendar and Review

All cases **NOT RUN**. Manual/device except explicitly marked harness. Start each case from [G or E](../fixtures.md), English with en-GB region/Monday week start, Gregorian, app lock off. Restore fixture and environment after each case; do not chain mutations. Capture each failed checkpoint, displayed period, actual selected ISO date and logs.

Sources: [Calendar rules](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/calendar/CalendarRules.kt), [projection](../../../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomCalendarRepository.kt), [Calendar state](../../../app/src/main/java/com/luma/lifeos/ui/calendar/CalendarViewModel.kt), [agenda](../../../app/src/main/java/com/luma/lifeos/ui/calendar/CalendarAgenda.kt), [Review calculation](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/review/WeeklyReview.kt), [Review route](../../../app/src/main/java/com/luma/lifeos/ui/review/WeeklyReviewRoute.kt), [source route](../../../app/src/main/java/com/luma/lifeos/ui/review/ReviewSourcesRoute.kt), [state](../../../app/src/main/java/com/luma/lifeos/ui/review/WeeklyReviewState.kt).
Existing references: [CalendarFlowTest](../../../app/src/androidTest/java/com/luma/lifeos/calendar/CalendarFlowTest.kt), [CalendarViewModelTest](../../../app/src/test/java/com/luma/lifeos/ui/calendar/CalendarViewModelTest.kt), [CalendarRulesTest](../../../core/domain/src/test/kotlin/com/luma/lifeos/domain/calendar/CalendarRulesTest.kt), [WeeklyReviewFlowTest](../../../app/src/androidTest/java/com/luma/lifeos/review/WeeklyReviewFlowTest.kt), [WeeklyReviewTest](../../../core/domain/src/test/kotlin/com/luma/lifeos/domain/review/WeeklyReviewTest.kt).

## CAL-001 — Today's agenda versus exact-day markers

Priority: **P0** · Status: **NOT RUN** · Setup: **G**.

1. Open Calendar, tap Today and All → agenda lists G-T2 overdue and G-T1 due today, plus G-E1 in Memories.
2. Read date ribbon/grid → D has 1 open task due exactly D and 1 Memory; D−1 has 1 overdue task marker. Today's agenda includes 1 carried overdue task.
3. Confirm G-T4 undated/G-T5 completed/G-T3 future are absent from today's task section; Mood and habit rows are absent from Calendar.

Persisted oracle: 5 tasks and 5 events unchanged. Agenda has 2 task rows although today's exact-date task marker count is 1.

## CAL-002 — Historical and future dates have exact tasks

Priority: **P0** · Status: **NOT RUN** · Setup: **G**.

1. Select D−1 in Calendar → task section contains only QA overdue; no carry-in from other days.
2. Select D+1 → only QA tomorrow; select D−7 → QA last week Memory, no tasks.
3. Select D+2 → empty task and memory sections with add actions; no fake zero-valued content rows.

Persisted oracle: No writes; marker count and task count are not imported habit counts.

## CAL-003 — All, Tasks and global Overdue filters

Priority: **P1** · Status: **NOT RUN** · Setup: **G**.

1. Select D, All → 2 task rows + G-E1.
2. Tap Tasks → same 2 tasks, Memory section hidden. Tap Overdue → only G-T2 and explanation 'as of' D.
3. While Overdue selected, move selection to D+1 → G-T2 still appears because scope is global relative to today.
4. Tap All → G-T3 and no memories; exact date context remains D+1.

Persisted oracle: Filter switches create no record; overdue total stays 1.

## CAL-004 — Complete and reopen from Calendar

Priority: **P0** · Status: **NOT RUN** · Setup: **G**.

1. Calendar D All → complete QA due today → its row disappears; D exact open-task marker becomes 0, overdue row remains.
2. More → Tasks → Completed → reopen QA due today by clearing completed state.
3. Return Calendar D All → QA due today reappears once.

Persisted oracle: Still 5 tasks; final 4 open/1 completed as original. No new task ID.

## CAL-005 — Add task inherits selected due date

Priority: **P0** · Status: **NOT RUN** · Setup: **G**.

1. Calendar select D+1 → Add task → due date is D+1.
2. Enter title QA calendar task; Save → calendar selected date remains D+1 and new row appears once.
3. Reopen row → correct title/date; More → Tasks search/find same item.

Persisted oracle: 6 tasks, new task open/due D+1; no event created. Reset G.

## CAL-006 — Add Memory inherits selected capture date

Priority: **P0** · Status: **NOT RUN** · Setup: **G**.

1. Calendar select D−7 → Add memory → timestamp is on D−7.
2. Enter title QA dated memory and description QA calendar capture; Save → same Calendar date and Memory row.
3. Open it from Calendar and Timeline D−7 → same title/content/ID; timestamp date stays D−7.

Persisted oracle: 6 events, 3 Memory details; D−7 now 3 events/2 memories. Reset G.

## CAL-007 — Schedule sheet preserves selected date and cancel

Priority: **P1** · Status: **NOT RUN** · Setup: **G**.

1. Calendar select D+1 → Schedule FAB → capture sheet. Dismiss → no record.
2. Reopen Schedule → Task → task due date D+1; cancel/discard.
3. Reopen Schedule → Memory → timestamp D+1; cancel/discard.
4. Return Today and open normal capture → calendar's D+1 context does not leak into a new current-day capture.

Persisted oracle: Original counts unchanged; no ghost task/event/draft falsely shown as saved.

## CAL-008 — Month and week movement clamps correctly

Priority: **P1** · Status: **NOT RUN** · Setup: **E**.

1. Use Calendar date selection to reach Gregorian 2027-01-31; Month mode, Next period → 2027-02-28 selected.
2. Previous period → 2027-01-28, preserving current selected day after clamping (do not expect Jan 31).
3. Select 2028-01-31 → Next month → 2028-02-29. Switch Week → Next → 2028-03-07.

Persisted oracle: No writes. Highlight and agenda must agree with selected civil date.

## CAL-009 — Dual-calendar view preserves ISO selection

Priority: **P1** · Status: **NOT RUN** · Setup: **G**.

1. Calendar select Gregorian 2026-11-16 → switch Calendar view to Persian → selected day is 1405-08-25; same G-T1/G-T2/G-E1 agenda.
2. Switch Week → Persian week starts Saturday. Switch back Gregorian → ISO selected date still 2026-11-16.
3. Leave/reenter Calendar using tabs → view override remains for that back-stack entry; app-wide Calendar preference is unchanged.

Persisted oracle: No stored due date or timestamp rewritten. Fixed conversion case uses original D, not shifted fixture dates.

## CAL-010 — Default-calendar changes invalidate view override

Priority: **P1** · Status: **NOT RUN** · Setup: **G**.

1. Set app default Gregorian in Settings; open Calendar and choose Persian local view.
2. Settings → Calendar → choose Persian as app default; return Calendar → Persian display with original ISO selection.
3. Set app default Gregorian → Calendar returns to Gregorian default, without stale local Persian override or changed agenda IDs.

Persisted oracle: Only calendar preferences change; data unchanged. Restore Gregorian.

## CAL-011 — Fifty-item first-page boundary

Priority: **P1** · Status: **NOT RUN** · Setup: **B50**.

1. Calendar Today All → 50 task rows total, 50 Memory rows total (scroll each section).
2. Verify QA task 01..50 and QA memory 01..50 are reachable exactly once, no Load more when no remaining records.
3. Complete QA task 01 → 49 open Calendar tasks, all 50 memories remain.

Persisted oracle: 50 task records/1 completed and 50 Memory events; UI does not manufacture a 51st row.

## CAL-012 — Independent source failures and retry

Priority: **P1** · Status: **NOT RUN** · Setup: **CalendarViewModelTest harness**.

1. Use existing fake CalendarRepository to fail tasks while emitting one Memory → task error/unknown count, Memory remains available.
2. Retry TASKS with successful original fixture → task rows recover; Memory IDs/count unchanged.
3. Repeat with only Memory source failing and retry MEMORIES → task rows remain available; unknown count is not presented as measured zero.

Persisted oracle: No repository writes. Ready rows match last successful source snapshot.

Level: ViewModel/harness. Existing class gives source fakes; creating a new transient-failure variant is a prerequisite, not an existing full-app toggle. Mark extended steps BLOCKED until supplied; existing tests can be run as documented in automation.

## CAL-013 — Midnight boundaries and zone change

Priority: **P1** · Status: **NOT RUN** · Setup: **E plus 2 direct memories**.

1. Create QA midnight before at 2026-11-15 23:59 Tehran and QA midnight at 2026-11-16 00:00 Tehran → Calendar/Timeline count 1 on each day.
2. Switch device zone to UTC and return to app → both events belong to UTC 2026-11-15 (20:29 and 20:30); Calendar selected ISO day does not auto-shift.
3. Open both → stored instants unchanged; restore Tehran → original day split returns.

Persisted oracle: 2 events/2 Memory details only; date groupings depend on viewing zone, timestamps are not rewritten. Reset E and automatic zone state.

## CAL-014 — Rapid selection does not flash wrong agenda

Priority: **P1** · Status: **NOT RUN** · Setup: **G**.

1. Select D, D−7, D+1 rapidly while observing heading and rows.
2. Wait until loading finishes → selected date D+1 shows QA tomorrow only, no QA sunrise or QA last week.
3. Rotate/recreate activity → selected D+1, current mode/filter remain coherent.

Persisted oracle: Read-only; final record IDs match final selection, not a late previous request.

## REV-001 — Partial current week uses aligned comparison

Priority: **P0** · Status: **NOT RUN** · Setup: **G**.

1. Open Review on Monday D with Monday week start → current range D..D, marked in progress.
2. Read comparison → D−7..D−7, not the entire previous seven days.
3. Next week action is disabled; entries 3 vs 2, memories 1 vs 1, mood samples 2 vs 1.

Persisted oracle: No writes. Current score 3/5, previous 5/5; arithmetic based on 3 and 2 distinct events.

## REV-002 — Overview and optional scales have exact sample counts

Priority: **P0** · Status: **NOT RUN** · Setup: **G**.

1. Review current week → Mood average 3/5 from 2 samples.
2. Open mood/energy/stress evidence → current scores [2,4], energy [1,5], stress [5,1], each mean 3/5.
3. Open previous energy/stress evidence if available → unavailable/no samples, not score 0. Previous mood remains score 5/5.

Persisted oracle: No writes; each metric denominator includes only samples actually carrying that scale.

## REV-003 — Current and previous source actions target exact records

Priority: **P0** · Status: **NOT RUN** · Setup: **G**.

1. Open Mood current sources via its source menu → only G-M1 then G-M2, oldest first, period D..D and Asia/Tehran shown.
2. Open first source → score 2 and 09:00, Back to same source list.
3. Back to Review, open previous Mood sources → only G-M3 score 5, not current moods.

Persisted oracle: No duplicate event or promotion from opening; source total 2 current/1 previous.

## REV-004 — Catalog frequencies and detail links

Priority: **P1** · Status: **NOT RUN** · Setup: **G**.

1. Review People/Places → QA Alex linked to 2 current records, QA Park linked to 2.
2. Open each catalog detail → correct name, linked G-M1/G-E1 (not all five events).
3. Return Review → same selected week; frequency remains 2, not inflated by multiple detail joins.

Persisted oracle: One person/one place, associations unchanged; current records 3.

## REV-005 — Period navigation and restoration

Priority: **P1** · Status: **NOT RUN** · Setup: **G**.

1. Review Previous → full week D−7..D−1, not marked in progress; 2 entries, one memory, one mood.
2. Open section chooser, choose Mood; open sources and Back → previous week and chosen section preserved.
3. Rotate → same period; tap This week → D partial period; Next disabled again.

Persisted oracle: No writes; current/previous metrics never shown under mismatching date headings.

## REV-006 — Picker cancel and future-week guard

Priority: **P1** · Status: **NOT RUN** · Setup: **G**.

1. Open Review period picker → choose date D−7 and confirm → previous full week.
2. Reopen picker, change draft selection, press Back/cancel → previous full week unchanged.
3. Try choosing a date in week D+7 → unavailable/disabled; no future report or crash. Select current week and confirm → D partial report.

Persisted oracle: No saved data changes. If picker allows a future date within the current week it must still clamp through D; only future weeks are prohibited.

## REV-007 — All section links and About close preserve context

Priority: **P1** · Status: **NOT RUN** · Setup: **G**.

1. Open section chooser → activate every offered destination one at a time (record labels in run evidence) → corresponding heading becomes reachable.
2. Select previous week then open About → text explains saved-source/missing-data/comparison period.
3. Dismiss by Close then by Back → same selected week/section; Overview action returns to overview. Final section actions scroll clear of capture FAB.

Persisted oracle: No writes; no invented metrics or extra navigation stack.

## REV-008 — Missing signals versus recorded zero

Priority: **P1** · Status: **NOT RUN** · Setup: **E then existing overview harness**.

1. With integrations off and no records open Review → no mood samples, memories or invented health/usage values; unavailable is distinct from recorded zero.
2. Run existing method overviewKeepsMissingAndRecordedZeroDistinct → explicit recorded zero usage stays 0 while missing mood/activity remains unavailable.
3. Open any offered usage evidence → displayed coverage/methodology explains completeness, not a claim of all-day measurement.

Persisted oracle: No writes; zero is only asserted for a fixture with known measurement.

Level: first step manual, second/third Compose harness. Use exact method selector in automation; historical test results are not current evidence.

## REV-009 — Source data mutation refreshes totals

Priority: **P1** · Status: **NOT RUN** · Setup: **G**.

1. From current Mood sources open G-M1, change score to 1 and Save → sources still exactly 2.
2. Return Review → current score sum 5 from 2 samples (mean 2.5); no duplicate entry.
3. Delete G-M2 and confirm → Review current entries 2, mood samples 1 average 1, memories 1; previous Monday remains untouched.

Persisted oracle: 4 events remain, 2 Mood details (G-M1/G-M3), 2 Memory details. Reset G.

## REV-010 — Provider-absent routes stay navigable

Priority: **P1** · Status: **NOT RUN** · Setup: **ReviewProviderFallbackTest harness**.

1. Run absent-repository Review sources and usage cases → localized unavailable state and Back, no blank trap/crash.
2. Activate Back → parent context returns without fabricating source rows or requesting protected content.

Persisted oracle: No writes; unavailable provider does not erase saved history.

Level: Compose UI/harness; unavailable dependency configuration cannot be produced by disabling OS permission alone. See existing ReviewProviderFallbackTest.

## REV-011 — Exact source first-page boundary

Priority: **P1** · Status: **NOT RUN** · Setup: **B50**.

1. Review current week → Entries sources → count 50 and oldest-first order QA memory 01..50.
2. Scroll final source → QA memory 50 visible; Previous and Next page actions absent at exactly one full page.
3. Open final Memory and Back → still same source period/count.

Persisted oracle: 50 distinct Memory events. Second-page navigation is outside this single-type 50-record fixture and remains a gap.

## DATE-001 — DST repeated hour retains two identities

Priority: **P1** · Status: **NOT RUN** · Setup: **E; device zone America/New_York**.

1. Use developer repository fixture/harness to create two Memory events at 2026-11-01T05:30:00Z and 2026-11-01T06:30:00Z, titles QA DST first/second. Both display 01:30 on Nov 1, with distinct offsets/instants available in recorded fixture.
2. Open Calendar and Timeline Nov 1 → two records, not one deduplicated by display time.
3. Open each → correct unique title/ID. Restore Tehran → both still Nov 1, at 09:00 and 10:00.

Persisted oracle: 2 separate event IDs remain; UTC instants unchanged.

Level: repository/integration plus device. A UI time picker cannot unambiguously choose both repeated-hour offsets. Fixture injection is required; BLOCKED until harness fixture is supplied. Do not claim this covered by ordinary clock changes.

## DATE-002 — Leap day and Persian Nowruz round trip

Priority: **P1** · Status: **NOT RUN** · Setup: **E**.

1. Gregorian Calendar/date picker select 2024-02-29 → valid; select February 2025 → no Feb 29.
2. Select Gregorian 2024-03-20 then switch Persian → 1403-01-01 selected.
3. Return Gregorian → 2024-03-20, same selection; dismiss without saving → no event/task created.

Persisted oracle: Only view state changes. Fixed dates are never shifted with D.

