# UI/UX acceptance checklist — 18 October 2026

Scope: UX-03–11 and UX-13–19. Excluded: UX-01, UX-02, UX-12 as new work; use existing UX-01/02 tests for shared-component regressions.
[Handoff/specs/plans](UI_UX_IMPLEMENTATION_READY_2026-10-18.md).

**Current result:** UX-03 and UX-04 are implemented in the working tree. Their focused automated checks pass; see [the UX-03/04 validation record](VALIDATION.md#ux-03-and-ux-04-habit-correction-and-detail-hierarchy--2026-10-18). The checkboxes below remain open for broader acceptance because manual device, RTL, font-size, keyboard and TalkBack checks have not been completed. Other scoped UX work remains unexecuted. UX-11 has existing implementation and historical automated results in [VALIDATION.md](VALIDATION.md#ux-11--search-initial-and-empty-states--2026-10-18); it is not newly accepted by this document.

Record per run: commit, date, command, device/API, application ID, locale, width/font/theme, fixture, result and evidence path. Status vocabulary: PASS, FAIL, NOT RUN, BLOCKED (with actual reason). Keep production data untouched; follow the handoff's isolated install procedure.

## Shared fixtures and environment

- [ ] Empty install; entries only; habits only; mixed journal + two habit logs beyond target; ten habits with long English/Persian/Latin names.
- [ ] Habit targets 1 and 10, counts 0/1/2/12, no scheduled day, skip remaining, future scheduled day, paused, archived, future-start and pending schedule changes.
- [ ] Precise log instant with .276 seconds, date-only log, DST overlap second offset (America/New_York 2026-11-01 01:30), DST gap (2026-03-08 02:30), device/tracking-zone date mismatch and deleted log.
- [ ] Week beginning 2026-10-12; partial-through 2026-10-14 with matching prior weekdays; full week; previous-only habit; zero scheduled days in either period; observer failure and live deletion.
- [ ] Access granted/required/locked/update-needed/unavailable/probe-failed crossed with imports on/off; saved absent/zero/nonzero; saved read failure; import failure; manual activity independent of Health.
- [ ] Backup prior-success/unknown metadata, failing open/write/flush/close, failing history commit, picker unavailable/cancel, valid and invalid restore candidates.
- [ ] Widths 360 dp and approximately 411 dp, fonts 1.0/1.3/2.0, en/fa/fr, light/dark. Use pairwise coverage across all flows; correction, capture and progress get explicit 360 dp/font 2.0/Persian coverage.
- [ ] API 28 or 32 and API 33+ locale/time settings where available; record unavailable device images as BLOCKED rather than certification. Real docked software keyboard, landscape/gesture navigation and actual TalkBack need explicit manual evidence.

## UX-03 — Correction

- [ ] Untouched open/cancel, Back and outside dismissal close once; no discard prompt and no route pop.
- [ ] Same displayed minute and canceled picker retain full precise time. Unchanged Save closes with zero repository write.
- [ ] Date-only edit preserves seconds/nanos; explicit different time zeros seconds; No time produces null. ID, recordedAt and original zone unchanged.
- [ ] Dirty leave asks once; Keep editing retains draft; restored dirty/clean states correct after recreation. Actual process-death restoration recorded separately from simulated SavedStateHandle tests.
- [ ] Date/time-specific error semantics, future rejection, gap rejection, original valid overlap offset, missing-log recovery, failed-save retry and duplicate-save guard.

## UX-04 — Habit detail

- [ ] History follows progress/log/secondary skip and transient action feedback; management is below.
- [ ] Labeled Manage habit exposes all eligible actions; Delete remains destructive/confirmed.
- [ ] Actual pending effective date/Cancel pending remain; future-start and archived restrictions unchanged; log retry/undo and history pagination still work.

## UX-05 — Weekly habits

- [ ] Habits-only overview shows available metric without changing entry/log scope or adding duplicate week queries.
- [ ] Short collapsed habit card ≤280 dp at 411 dp/font 1.0; one default View history, optional expanded real-day destinations.
- [ ] Seven semantic dates/states/counts, upcoming not missed, neutral off/paused/archived states, meaningful skip behavior; wrapping at large text.
- [ ] Partial/prior matching unchanged; zero scheduled denominator says No comparable scheduled days. Previous-only history routes correctly. Live delete updates both count and card; failure is not zero.

## UX-06 — Missing metrics

- [ ] Loading/saved read failure/unknown configuration/no check-ins/missing optional energy or stress ratings/imports-off/access-needed/locked/update/unavailable/probe failure/import failure/no saved period all distinct; no generic metric “Unavailable” remains in summary/Review cells.
- [ ] Zero retained as zero and historical values retained with off/denied status; current and previous periods independent; no fabricated delta.
- [ ] Manual activity is never hidden or mislabeled as Health permission failure.
- [ ] Check in/Integration settings/Retry lead to working destinations/actions; requery never silently imports provider records.

## UX-07 — Editor content

- [ ] Mood, Journal and Memory main input is in first viewport; selected date visible before save; timezone explanation available.
- [ ] Change dialog cancel is clean; Done calls validation once; rejected edit remains visible; same-minute/date-only precision retained.
- [ ] Promoted memory timestamp stays read-only. Real docked keyboard keeps focused input/Save accessible; drafts/discard/photo cancel remain correct.

## UX-08 — Capture/footer

- [ ] Only navigation in outer bottomBar; no reserved capture row. Today uses existing shortcuts; Timeline/Review labeled FAB; Habits/More/secondary no global FAB.
- [ ] Final Timeline/Review/fallback item and its action scroll above measured FAB clearance; insets applied once; RTL logical-end placement; font 1.3/2.0 wrapping.
- [ ] Existing Today shortcuts/density and entry/habit-count regressions pass; five tabs/selection/date/scroll restoration intact.

## UX-09 — Habit rows/setup

- [ ] Name/open and +1 are distinct named ≥48 dp targets; count remains exact above target, indicator clamps and state is not color-only.
- [ ] Ten long names scan/wrap without reduced font; pending errors/retry/undo stay reachable.
- [ ] Counting label/examples/tap explanation translated; Daily/Mon–Fri/Custom set exact weekdays with no schema change.
- [ ] Limits 100/30 code points, 1..999 target, required weekdays and schedule effective dates unchanged; timezone mismatch visible while routine explanation collapses.

## UX-10 — Other habits

- [ ] Scheduled classification uses reviewTarget, not logging eligibility. Future/skip/completed scheduled rows stay Scheduled.
- [ ] Zero scheduled concise state/Today tracking-date action; collapsed counted Other habits browses off-day/paused/archived/future-start history.
- [ ] Other rows have no disabled log button; domain still blocks forbidden logs. Pending/failed hidden rows force expansion with Retry.

## UX-11 — Existing Search implementation

- [ ] Unfiltered empty library shows saved-entries explanation, populated library shows prompt/results; no false failure/reset request at rest.
- [ ] Whitespace follows empty-query policy; query-only/filter-only no-match states offer relevant enabled resets.
- [ ] Reset filters retains text; Clear all clears text/filters; named active chips remain removable.
- [ ] Character counter hidden at 179, visible at 180 and 200; 201st input follows existing limit behavior. Loading/error/live deletion remain meaningful.
- [ ] After UX-13 header migration, keyboard/focus, RTL/large text and actual TalkBack initial/no-match announcements checked. No UX-12 scope/copy changes.

## UX-13 — Fixed bars

- [ ] Every secondary inventory route has fixed Back/title through scrolling, loading/missing/error; no double system inset.
- [ ] System/icon Back share exact guards: correction/picker/discard first, busy reset guarded, ordinary route pops once.
- [ ] Full long title accessible, RTL arrow mirrored; catalog create/tab actions retained; editor Close/Cancel unchanged.

## UX-14 — Privacy

- [ ] Three summary facts and four discoverable disclosure boundaries; expansion/recreation and TalkBack state.
- [ ] Every fact in privacy spec retention matrix survives in en/fa/fr, including in-flight Usage, Health seven-date reconciliation/old snapshots and external files.
- [ ] Deletion removes/does-not-remove visible near reset; red action/explicit confirmation/busy/failure/accepted-request wording preserved.
- [ ] Native rationale opens without database/import/permission action; unavailable settings intent recoverable.

## UX-15 — Integrations

- [ ] Android access and imports independent while off and on; each state exposes the specified primary action.
- [ ] Explicit grant explanation, cancel and return do not enable/import; Health grant while off allowed only after explicit action.
- [ ] Usage locked/update/unavailable/probe error never reported as zero/denial incorrectly; correct recovery.
- [ ] App usage described as foreground total; Health imports described truthfully as manual foreground. Last success retained on failure; unknown saved bounds not invented.
- [ ] Opening settings performs zero source.query/import calls. Disabled in-flight imports retain existing commit/revision safety and saved history.

## UX-16 — Localization

- [ ] 0/1/2/10 repetitions/scheduled-day/default-unit forms correct en/fa/fr, including fixed Persian/French quantity lint errors.
- [ ] Shared digit/time policy across Today/Timeline/history/Review; both 12/24-hour modes and resume change; Gregorian dates unchanged.
- [ ] User-authored names unmodified; zone/offset/package fragments readable in RTL; stored ISO/Instant untouched.
- [ ] Small positive durations remain nonzero; editor selected date/draft survives locale/time changes.

## UX-17 — Review discovery

- [ ] Sections lists Overview, Entry counts, Mood, Memories, People & places, App usage, Activity, optional Habits without horizontal-gesture knowledge.
- [ ] Each jump/Overview action works, including empty sections/end-clamping; existing chip selection/auto-scroll intact.
- [ ] Sticky selected range/in-progress context meaningful; week changes clear stale target, same-week detail return preserves scroll.

## UX-18 — Backup status

- [ ] Initial unknown state, successful completed-close timestamp, relaunch persistence and qualification about moved/deleted file.
- [ ] Picker cancel/unavailable, failed open/export/flush/close and canceled operation leave prior timestamp. History-write failure reports successful archive plus unsaved-date warning.
- [ ] Restore prepare/cancel/confirm retain local timestamp; archive excludes preference; preview counts/replacement warning/confirmation intact.
- [ ] Warning adjacent to Export backup file; no protection/file-existence claim. Privacy shortcut opens Backup only, absent if unavailable; destructive reset on disposable data clears history.

## UX-19 — Chooser

- [ ] Sheet fully expanded initially. All three choices/Close visible at 360/411 dp/fonts 1.0/1.3; extreme sizes scroll clearly.
- [ ] Default Use current time versus deliberate selected date honored for all types; no unadvertised expansion needed.
- [ ] Backdrop/Back/Close safely dismiss; correct system padding, single navigation and saved-date return.

## Targeted automated suites

All existing device class names are rooted at com.luma.lifeos unless a subpackage is shown; new classes are introduced by the owning plan.

| Unit | JVM focus | Device classes |
| --- | --- | --- |
| A | language.LocalizedFormatsTest, TranslationCatalogTest, DurationDisplayTest | language.LanguageFlowTest, language.TimePreferenceFlowTest (new), habits.HabitReviewFlowTest |
| B | ui.habits.HabitsViewModelTest, HabitEditorViewModelTest, HabitPresentationTest (new) | habits.HabitCaptureFlowTest, habits.HabitsNavigationTest, TodayScreenTest, habits.EntryHabitCountsFlowTest |
| C | Existing ui.mood/journal/memory editor suites | CaptureAccessibilityTest, MoodCaptureTest, JournalCaptureTest, MemoryCaptureTest, TodayPersistenceTest, AppNavigationTest, TimelinePagingRouteTest, review.WeeklyReviewFlowTest, PhotoCaptureTest |
| D | domain usage/health syncer suites; ui.integrations availability/metric tests (new); usage/health settings ViewModels | usage.UsageSettingsFlowTest, health.HealthSettingsFlowTest, summary.DailySummaryFlowTest, review.WeeklyReviewFlowTest |
| E | Existing Search/route state suites | DetailNavigationTest, AppNavigationTest, settings.SettingsHubFlowTest, privacy.PrivacySettingsFlowTest, habits.HabitsNavigationTest, PeopleCaptureTest, PlaceCatalogFlowTest, usage.ReviewUsageFlowTest, review.WeeklyReviewFlowTest |
| F | ui.review.HabitWeekPresentationTest (new), ReviewOverviewTest, WeeklyReviewStateTest | habits.HabitReviewFlowTest, habits.EntryHabitCountsFlowTest, review.WeeklyReviewFlowTest |
| G | privacy.PrivacyViewModelTest, language.TranslationCatalogTest | privacy.PrivacySettingsFlowTest, health.HealthSettingsFlowTest |
| H | backup.BackupViewModelTest | backup.BackupExportHistoryTest (new), backup.BackupSettingsFlowTest, privacy.PrivacySettingsFlowTest, AppNavigationTest |
| V / UX-11 | ui.search.SearchViewModelTest, ActiveSearchFiltersTest | SearchStatesTest, SearchFlowTest, ActiveSearchFiltersTest |

## Evidence log

Append real results during execution. No implementation/runtime tests were run for this documentation-only preparation. Keep unchecked manual criteria open; screenshots do not certify speech, keyboard behavior, persistence or provider recovery.
