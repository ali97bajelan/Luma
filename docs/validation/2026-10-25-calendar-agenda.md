# Calendar & Agenda implementation handoff

Package date: 2026-10-25

Handoff review: 2026-10-25T14:32:20+03:30 (Asia/Tehran)

Status: **Implementation source complete; QA not started; acceptance pending.**

This record closes CAL-I06 and opens the QA phase described in [CALENDAR_TASKS.md](../CALENDAR_TASKS.md), [the implementation plan](../superpowers/plans/2026-10-25-calendar-agenda.md), and [CALENDAR_QA.md](../CALENDAR_QA.md). “Source complete” means ready for QA. It is not evidence of compilation or runtime behavior.

## Revision and patch identity

| Item | Value |
| --- | --- |
| Calendar source revision reviewed | `bf97528eb74f2d6c120745b36863e29d72c50df1` |
| Implementation base revision | `df58ff4` |
| SHA-256 of `git diff --binary df58ff4..bf97528` at handoff | `a5078b285e8ea582487678ed3c4710908359e2aef150ac1369ba06a6befe5832` |
| Pre-existing dirty patch SHA-256 | `e5c6fd7269885f1fbea92184efbdf84033fc64944fb5ecc96f4df84e63f634ee` |
| Dirty patch scope | `app/build.gradle.kts`, `app/release/app-release.apk`, `app/release/output-metadata.json`; present before CAL-I06 documentation edits and excluded from this handoff |

The dirty patch changes release version metadata and the release APK. It was preserved and is not part of Calendar source evidence. QA must identify the exact source patch and built APK it exercises. The Calendar source diff check `git diff --check df58ff4..bf97528` completed with no whitespace errors.

## Implementation source coverage

| Task | Source status | Static review coverage |
| --- | --- | --- |
| CAL-I01 | Source complete; QA pending | Civil-date bounds, month/week range rules, date membership, ordering, marker/read models and repository contracts reviewed. |
| CAL-I02 | Source complete; QA pending | Room projections, repository mappings, task counts/paging, habit schedule projection and journal day boundaries reviewed. Database version remains 6; only Calendar DAO registration was added, with no entity, migration, schema or archive change. |
| CAL-I03 | Source complete; QA pending | Saved date/mode/filter defaults and invalid-date fallback reviewed. Per-source request keys include applicable date/today/range/filter/zone/restore generation; `flatMapLatest` and current-key checks guard stale responses. Initial task page is 50 rows. |
| CAL-I04 | Source complete; QA pending | Month/week grid, marker types, filters/counts, loading/error/retry states, task recovery wiring, habit/reflection sections and accessible labels reviewed. Visual and interaction behavior remains unverified. |
| CAL-I05 | Source complete; QA pending | Five primary destinations retain the specified order; Calendar replaces Habits in slot three. More opens the retained Habits route with Back support. Existing task editor and Quick Capture call paths retain nullable/defaulted compatibility; Calendar supplies an explicit task due date. |
| CAL-I06 | Source complete | This source review, handoff, route-ownership update and signed implementation gate. |

Reviewed source files:

- Domain: `core/domain/src/main/kotlin/com/luma/lifeos/domain/calendar/CalendarModels.kt`, `CalendarRepository.kt`, `CalendarRules.kt`.
- Data: `core/data/src/main/kotlin/com/luma/lifeos/data/calendar/CalendarDao.kt`, `CalendarRows.kt`, `core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomCalendarRepository.kt`, `core/data/src/main/kotlin/com/luma/lifeos/data/LumaStorage.kt`, `core/data/src/main/kotlin/com/luma/lifeos/data/db/LumaDatabase.kt`.
- Calendar UI: `app/src/main/java/com/luma/lifeos/ui/calendar/CalendarUiState.kt`, `CalendarViewModel.kt`, `CalendarRoute.kt`, `CalendarScreen.kt`, `CalendarGrid.kt`, `CalendarAgenda.kt`.
- Integration: `app/src/main/java/com/luma/lifeos/MainActivity.kt`, `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt`, `app/src/main/java/com/luma/lifeos/ui/QuickCaptureSheet.kt`, `app/src/main/java/com/luma/lifeos/ui/more/MoreRoute.kt`, `app/src/main/java/com/luma/lifeos/ui/navigation/AppDestinations.kt`, `app/src/main/java/com/luma/lifeos/ui/todo/TodayTasksCard.kt`, `app/src/main/java/com/luma/lifeos/ui/todo/TodoEditorRoute.kt`.
- Resources: `app/src/main/res/drawable/ic_calendar_schedule.xml`, `ic_more_vertical.xml`, `ic_nav_calendar.xml`, and `app/src/main/res/values/strings.xml`, `values-fr/strings.xml`, `values-fa/strings.xml`.

Static review confirmed the 56 `calendar_` resource names and the integration keys `nav_calendar_compact`, `more_habits_description` and `capture_task_due_date` have matching English, French and Persian entries. The feature scope adds no task tags, event CRUD, reminders, runtime permissions, dependencies, database entities, schema version, migration or archive format. Reference sample counts and sample journal prose are not used as application data.

Known validation limits: no runtime, compiler, layout, accessibility, navigation, Room execution or visual result was established in this phase. No source defect was confirmed by the static review; this is not a substitute for the listed QA scenarios. Route ownership is recorded in [route-coverage.md](../luma-redesign/route-coverage.md). Existing release blockers remain separate from this Calendar gate.

## Static implementation checks

| Check | Result | Evidence |
| --- | --- | --- |
| Spec-to-source and changed-file review | PASS | CAL-01–07 traced to I01–I06 source and the plan's file/interface map. |
| Public signatures and existing call-site compatibility | PASS | Nullable/defaulted provider and More callback, existing task editor routes, and trailing nullable Quick Capture scheduling parameters reviewed in source. |
| Saved state, request keys, null handling and date bounds | PASS | `CalendarViewModel`, domain guards/rules and grid date construction reviewed. |
| Locale resource key parity | PASS | Static XML name comparison: 56 `calendar_` keys plus three Calendar integration keys in each of `values`, `values-fr` and `values-fa`; no missing or extra names. |
| Schema, archive, permission and dependency scope | PASS | Implementation diff review; database version remains 6 and no new schema/archive/permission/dependency change was found. |
| Calendar implementation whitespace check | PASS | `git diff --check df58ff4..bf97528` exited 0. |

## QA checks — all NOT RUN

Implementation-phase rule: **Gradle/build/tests/emulator NOT RUN during implementation.** No test files were authored or executed, and no app was installed. Every Calendar QA scenario and required matrix cell remains NOT RUN; Calendar acceptance remains PENDING.

| Scenario | Result |
| --- | --- |
| CQA-01 — Main-bar Calendar and retained tab state | NOT RUN |
| CQA-02 — More → Habits, legacy links and fallback state | NOT RUN |
| CQA-03 — Navigation to existing records | NOT RUN |
| CQA-04 — Month/week behavior | NOT RUN |
| CQA-05 — Month arithmetic and leap years | NOT RUN |
| CQA-06 — Overflow, 4/5/6 weeks and year boundary | NOT RUN |
| CQA-07 — Minimum/maximum supported dates | NOT RUN |
| CQA-08 — Locale week alignment | NOT RUN |
| CQA-09 — Today carry-in and exact-date counts | NOT RUN |
| CQA-10 — Historical/future and global Overdue scope | NOT RUN |
| CQA-11 — Completed and undated exclusion | NOT RUN |
| CQA-12 — Markers and accessible date descriptions | NOT RUN |
| CQA-13 — Ordering and >50-row paging | NOT RUN |
| CQA-14 — Civil due times and date-based overdue | NOT RUN |
| CQA-15 — Midnight/foreground refresh | NOT RUN |
| CQA-16 — Journal DST and habit tracking zone | NOT RUN |
| CQA-17 — Add task defaults and save/cancel | NOT RUN |
| CQA-18 — Dated Schedule versus existing Quick Capture | NOT RUN |
| CQA-19 — Completion, repeated tap and Undo | NOT RUN |
| CQA-20 — Retry, stale revision and missing task | NOT RUN |
| CQA-21 — Edit, reschedule, menu and confirmed delete | NOT RUN |
| CQA-22 — Recreation, process restoration and language | NOT RUN |
| CQA-23 — Back, sheet/editor focus and IME | NOT RUN |
| CQA-24 — Restore invalidation | NOT RUN |
| CQA-25 — Rapid switching and late responses | NOT RUN |
| CQA-26 — Room observation and cross-surface consistency | NOT RUN |
| CQA-27 — Effective habit schedules and real state | NOT RUN |
| CQA-28 — Reflection saved-data fidelity | NOT RUN |
| CQA-29 — Empty, partial failure and Retry | NOT RUN |
| CQA-30 — Light/dark visual parity | NOT RUN |
| CQA-31 — Narrow/large text/RTL/landscape/expanded | NOT RUN |
| CQA-32 — TalkBack, keyboard and regression gate | NOT RUN |

| Required QA matrix cell | Result |
| --- | --- |
| EN/FR/FA × Light/Dark at 411 × 891 dp, font scale 1.0 | NOT RUN |
| System theme and light→dark→recreate | NOT RUN |
| 320 dp × font scale 2.0 × EN/FR/FA × Light/Dark | NOT RUN |
| 411 dp landscape × font scale 2.0 × Persian RTL × Light/Dark | NOT RUN |
| 840 dp width × EN and FA × Light/Dark | NOT RUN |
| Android 12/24-hour preference × EN/FR/FA | NOT RUN |
| API 28 and API 36 | NOT RUN |
| Matching and different device/habit zones; DST gap/overlap | NOT RUN |
| TalkBack and hardware keyboard | NOT RUN |
| Offline airplane-mode smoke | NOT RUN |
| Host tests, production compile, APK assembly, Android test source compile and lint | NOT RUN |
| Room and Compose/instrumentation suites and existing regressions | NOT RUN |
| Manual screenshots, contrast and target-bound measurements | NOT RUN |

### QA run fields

Fill these fields for each actual run; the values below are pending, not inferred from this handoff.

| Field | Value |
| --- | --- |
| QA execution timestamp | NOT RUN |
| Tested source revision and dirty patch | Record per run |
| Build variant, APK path and APK SHA-256 | NOT RUN |
| Device/emulator, API, Android version and install state | NOT RUN |
| Locale, app/system theme, width, orientation, font scale and IME | NOT RUN per matrix cell |
| Device zone, habit tracking zone and 12/24-hour preference | NOT RUN per matrix cell |
| Fixture/date/record IDs and executed steps | NOT RUN per scenario |
| Expected result, actual result and defect ID | NOT RUN per scenario |
| Command/test counts/logs and screenshot/accessibility evidence | NOT RUN |
| Planned visual evidence directory | `docs/luma-redesign/evidence/<actual-run-date>/calendar/` |

## Design decisions and QA entry gate

- Calendar uses the user's selected five-tab order and replaces Habits in the third slot. The existing Habits destination remains under More and preserves old route callers.
- Stored task due dates/times remain floating civil values. Calendar is a read-only projection for tasks, scheduled habits and saved manual journals; all writes use existing task/editor/capture paths.
- The UI uses real saved counts and reflection text. It omits mockup-only task tags, sample values, sample quotes, external events, recurrence and reminders.
- Database version 6, task and journal storage formats, archive format and permissions remain unchanged by Calendar source work.
- QA should use the isolated environment defined in [CALENDAR_QA.md](../CALENDAR_QA.md), record actual execution timestamps and exact build/source patch, and leave unavailable checks NOT RUN or BLOCKED.

**Implementation gate: SIGNED — CAL-I01–I06 source complete; QA may start. Calendar runtime and release acceptance remain PENDING.**
