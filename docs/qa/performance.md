# Lightweight performance QA

Status for every measurement: **NOT RUN**. This plan checks visible responsiveness with bounded fixtures; it is not a load/stress test.

## Environment and method

- Use productionDebug on a representative disposable emulator, API 35 or currently available stable API; record exact emulator/device model, API, CPU/RAM, resolution/dp, app commit/build, JDK status, theme, locale and fixture.
- No debugger attached; close unrelated apps, keep battery/power mode fixed. Record whether emulator is cold-booted. Do not compare emulator timings with physical-device budgets.
- Use E for launch/navigation, G for normal aggregate screens, B50 for bounded lists only. Never exceed 50 records per entity type.
- Use Android Studio System Trace/CPU profiler only if a visible stall repeats. First gather video or timestamped interaction evidence. Avoid profiler overhead in the primary timing sample.
- For each action: perform 1 warm-up, then 5 measured repetitions. Record individual values and median, not only the mean. Cold launch: force-stop before each of 3 measured starts, clear no data; report separately from warm launch.
- Measure wall time from tap/launch to stable expected content, using screen recording/frame timestamps or a repeatable UI automation timestamp. Record visible loading duration separately. Do not count Gradle build/test execution or test runner delay as app jank.
- Proposed exploratory budgets below are triage thresholds, not product requirements. Any exceedance requires repeat on same device and evidence before filing a performance defect. No target is claimed from source unless labeled documented.

## Scenarios

### PERF-001 — Cold and warm launch · P1 · E

1. With E installed, force-stop app; measure launcher tap until Today heading and stable empty state are visible; repeat 3 times.
2. Leave app in background 10 seconds; reopen from Recents; repeat 5 warm returns.
3. Record median and range for cold and warm separately, plus crash/log errors.

Oracle: stable Today content with no indefinite spinner. Proposed triage: cold median ≤3.0 s; warm median ≤1.0 s.

### PERF-002 — Primary tab switching · P1 · G

1. Warm up Today, Timeline D, Calendar D, Review current partial week, More.
2. Perform Today→Timeline→Calendar→Review→More→Today once, then repeat the full cycle 5 times. Measure tap to screen heading and first stable data.
3. Record each destination separately; count visible blank frames, loading indication and stale-date flashes.

Oracle: correct screen and selected tab every time. Proposed triage: median ≤700 ms to first usable content; local Room-backed data should not remain blank >2 s.

### PERF-003 — G list scrolling · P1 · G

1. Scroll Timeline D from top to bottom and back once as warm-up; measure 5 top-to-bottom passes at comfortable fixed speed.
2. Repeat 5 slow scrolls through Tasks list and Habits list.
3. Record dropped/janky frames from Android Studio frame profiler if available, plus blank or duplicate rows.

Oracle: stable keys/rows and responsive controls. Proposed triage: no repeatable multi-frame hitch during ordinary scroll; frame timing over 16.7 ms may be recorded as evidence, not an automatic defect threshold across all devices.

### PERF-004 — B50 task and Memory lists · P1 · B50

1. Build B50. Open Tasks, scroll to bottom and back 5 times; measure from first row visible to final row visible.
2. Open Memory Library, repeat 5 passes. Verify row count stays 50 and no row duplicates while scrolling.
3. Open Calendar D; scroll each section with 50 records. Repeat 5 times.

Oracle: all rows reachable, no ANR, input remains responsive. Proposed triage: median open-to-first-row ≤1 s after warm-up; no single user action freezes visible input for >2 s. This does not test second paging windows.

### PERF-005 — Search typing/filter · P1 · B50

1. Open Search on B50 and wait for initial state.
2. Enter QA memory 25 character by character 5 times, clearing query between runs; measure last keystroke to stable result.
3. Apply one available filter, clear filters, then repeat with task title query.
4. Record query restoration and keyboard responsiveness.

Oracle: matching results follow the documented indexed fields; no stale result shown as current after query changes. Proposed triage: median query-to-stable result ≤700 ms; typing has no visible input lag.

### PERF-006 — Editor open/save/reopen · P1 · G

1. Open G-T1, edit title and Save; repeat the open/save cycle 5 times, restoring original title each run.
2. Open G-E1, enter draft and Cancel 5 times; no write on cancel.
3. Measure tap-to-editor and Save-to-confirmation/reopened list separately.

Oracle: task identity/count remains stable; canceled Memory drafts do not persist. Proposed triage: editor usable within 500 ms median; local save feedback within 1 s median. Save correctness overrides speed.

### PERF-007 — Date switch, Review refresh and photo load · P2 · feature fixture

1. On Timeline switch D↔D−7 five times; measure stable correct feed each time.
2. In Review switch current↔previous 5 cycles; measure metrics become consistent with period.
3. When valid local photo fixture is available, open the same Memory thumbnail/gallery 5 times after one warm-up; record thumbnail and full image load separately.
4. Never measure using network-hosted photos or include time to transfer an external asset.

Oracle: no previous-date data under new heading; photo decode errors are reported with retry. Proposed triage: date switch median ≤1 s; local thumbnail median ≤1 s and full image median ≤2 s on recorded device.

### PERF-008 — Background/foreground and widget refresh · P2 · FW or F1

1. Create one task/habit or use existing fixture. Background app for 30 seconds; reopen 5 times and record stale period/content if any.
2. If launcher supported, place exactly one Tasks and one Habits widget; perform one item completion/log through each; observe refresh up to 5 seconds.
3. Force-stop/relaunch and repeat once; record OS/launcher and battery restrictions.

Oracle: correct identity and state; no duplicate command when refresh occurs. Proposed triage: visible widget update ≤5 s in normal conditions. Android launcher scheduling can vary; record delayed update without concluding app lag unless reproducible.

## Result sheet

For each PERF ID record: device/API/build, fixture counts, repetitions and raw milliseconds, median, visible frame/jank evidence, profiler configuration, first vs repeat behavior, and pass/fail reason. Proposed triage limits require product agreement before they become acceptance requirements.
