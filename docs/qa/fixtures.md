# QA fixtures

All scenarios start NOT RUN. Use only a disposable QA install. Never reset or restore over personal data. Baseline: productionDebug variant (`com.luma.lifeos.qa`), no debugger, English, Light, Gregorian, Asia/Tehran, font scale 1.0, app lock off, integrations off. Use a standard phone approximately 411 × 891 dp.

## Clock and isolation

D = 2026-11-16, Monday, at 12:00 Asia/Tehran (UTC+03:30). Set the disposable emulator clock in Android Settings > System > Date & time, disabling automatic time/time zone. Record actual device time before every date-sensitive case. If changing date is unavailable, use the actual Monday as D and shift every fixture date by the same whole-week offset; do not shift fixed leap/DST/conversion cases.

An unshifted baseline gives current Review Monday 2026-11-16 through 2026-11-16, compared with Monday 2026-11-09 only. Device language/region en-GB gives Monday as first weekday. Select English explicitly in Luma. Restore automatic time after QA. Keep the clock fixed only for static cases; timer/reminder/performance tests need a running clock.

**E (empty):** dedicated fresh app storage; launch normally. Decline optional integrations and leave app lock off. No saved tasks, habits/logs, events, photos, people, places, or focus sessions. System-owned predefined emotion catalog is not user data. Empty means no user records, not a physically empty database.

**G (aggregate baseline):** reset to E, then create the following through UI. Names below are unique search keys, not generated database UUIDs. Record actual UUIDs if captured by the harness. Every case using G must start from the original data below, not the previous case's mutated state.

## G catalog and task setup

1. More > People & places > People > Add: name `QA Alex`; leave optional fields empty; Save.
2. In the Places tab: Add `QA Park`; leave optional fields empty; Save.
3. More > Tasks > Add task: create each row below, with empty note and default importance. Set exact date using the due-date picker, then Save. Open G-T5 and mark completed.

| ID | Title | Due | Status |
| --- | --- | --- | --- |
| G-T1 | QA due today | D | open |
| G-T2 | QA overdue | D−1 (2026-11-15) | open |
| G-T3 | QA tomorrow | D+1 (2026-11-17) | open |
| G-T4 | QA undated | none | open |
| G-T5 | QA completed | D | completed |

Oracle: 5 tasks, 4 open, 1 completed; exactly 1 open overdue task. No parent/child relationships or reminders.

## G capture setup

Today > Mood opens new Mood; set timestamp with its date/time control, set score, expand optional details for energy/stress/context, Save. More > Memories > Record memory opens a direct Memory; set timestamp, title/body, optional context; Save. After each save reopen and verify fields.

| ID | Type/title | Local timestamp | Values | Context |
| --- | --- | --- | --- | --- |
| G-M1 | Mood | D 09:00 | score 2, energy 1, stress 5; note `QA low` | QA Alex; QA Park |
| G-M2 | Mood | D 10:00 | score 4, energy 5, stress 1; note `QA high` | none |
| G-M3 | Mood | D−7 09:00 | score 5; energy/stress unset; note `QA previous` | none |
| G-E1 | QA sunrise (direct Memory) | D 11:00 | description `QA morning at the park`; importance unset | QA Alex; QA Park |
| G-E2 | QA last week (direct Memory) | D−7 11:00 | description `QA previous Monday`; importance unset | none |

No emotion selections, tags or photos in G. Oracle: 5 distinct life events, 3 Mood detail records, 2 Memory detail records; no Journal details or promotions. At D: 3 events (2 Mood + 1 Memory), mean score 3/5 from 2 samples, energy 3/5, stress 3/5. At D−7: 2 events (1 Mood + 1 Memory), score 5/5 from 1 sample; energy/stress unavailable. Current and previous Review: memories 1 each, entries 3 vs 2; current people/place frequencies QA Alex=2, QA Park=2. Counts overlap because one event may supply multiple metrics.

## G habit setup

More > Habits > Add habit: name `QA water`, unit `glass`, target 3, daily schedule, start D−7, tracking zone Asia/Tehran. Save. On D log +1 twice, allowing each write to settle. Do not log other days. Oracle: 1 active habit, 2 separate repetitions/logs on D, count 2/3; no completed daily target. If source UI gives preset unit text, enter custom `glass` through its unit field.

D Timeline with app usage hidden: **3 entries + 2 habit logs**, five feed rows, with logs separated from entries in the count. Habit logs are not life-event rows or memories. Their occurrence times are the actual setup time; capture them in the fixture manifest. Do not assert their order relative to G-E1 unless the setup clock is after 11:00.

## Reset and fixture manifest

For each module, use its local fixture recipe rather than combining every module dataset. Reset E/G by clearing only disposable QA app storage through Android app settings and reseeding, or by restoring an explicitly verified QA-only snapshot when available. Backup restore can disable imports/change security settings; reapply the baseline after every restore.

Record: build/commit, package, device/API, D, zone, locale/region, calendar, theme, fixture name, actual IDs/timestamps, record counts and optional-provider state. Preserve this alongside run results. No existing seed script or backup binary is supplied by this documentation; UI recipes are the baseline executable setup.

## B50 (bounded list/performance dataset)

Start from E, not G. For i=01..50 create tasks titled `QA task i`, default importance, note `QA body i`, due D, all open. For i=01..50 create direct memories titled `QA memory i`, description `QA body i`, dated D at 08:00+i minutes (08:01..08:50). No photos, moods, habits, sessions or catalog links. Verify counts 50 tasks + 50 memories, not 100 tasks or 50 records per parent.

The exact 50 Memory rows test the first-page boundary. Calendar pages are 50 and Timeline initially reads 150. This dataset cannot exercise a second Calendar page or Timeline window eviction. Those remain documented limit-related gaps; never add a 51st record of a type to bypass the agreed cap.

## Strings and assets

Use `QA Café é`, `QA سلام 123`, `QA 😀`, `QA Alex` twice where duplicate-name behavior is explicitly tested, a single ASCII space, and a newline as separate values. Do not assume notes participate in full-text Search.

For a long display string, use `QA سلام Café 😀 ` repeated 8 times, only in fields whose validation permits that length. Boundary tests in the feature files give their actual field limits.

Photo cases require local QA-only assets: one valid JPEG, one valid PNG, and a deliberately invalid image fixture for harness-only decode failure. The capture module defines preparation and assertions. External provider/launcher/credential requirements are in the system module.

## Fixture index

- Aggregate/navigation/Calendar/Review: G and E above.
- [Tasks/Habits/Focus](scenarios/productivity.md): local T/H/F recipes.
- [Capture/catalog/Search](scenarios/capture-and-catalog.md): local C recipes.
- [Settings/integrations/widgets/lock](scenarios/settings-integrations-widgets.md): local recipes and external prerequisites.
