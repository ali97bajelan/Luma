# T19 — Reliability, Performance, Accessibility

Status: design approved. Accessibility/language implementation and API 36
instrumentation began on 2026-10-17; legacy API 28/32 and manual acceptance remain
open, as do the T19 performance targets.

## Intent and scope

Close the recorded T19 follow-ups by improving the measured Timeline, Search,
and Weekly Review performance cases; completing the broad TalkBack and
screen-size acceptance; correcting the French primary-navigation label layout;
and recording native app-language behavior on legacy Android API 28–32.

This work follows the open items in `REMAINING_TASKS.md`, the measured results
in `TASKS.md` and `docs/VALIDATION.md`, and the manual steps in
`docs/UX_ACCEPTANCE_2026-10-14.md`. Existing user data must not be reset or
overwritten for validation. Device checks use a disposable install and generated
fixtures. A check that cannot run is recorded as BLOCKED or NOT RUN with its
reason; it is never inferred from compilation or automated semantics.

## Current evidence

- T11: large-day navigation measured 116–150 ms. A prior native-input probe
  captured 1,112 frames with p95 21.96 ms and 52.43% missed platform deadlines.
  Smooth scrolling has not been established on representative hardware.
- T15: on a 50,000-entry, five-year fixture, title-substring and no-match pages
  measured 2.0–2.2 seconds on API 36; broad and date-filtered pages measured
  about 10–25 ms. The T15 design sets a repository-page p95 target below
  300 ms, including relation mapping.
- T16: on a 50,000-entry, five-year fixture with 2,500 selected mood entries,
  repository timing measured a 330 ms median and 364 ms p95. Its target is p95
  below 300 ms after initialization.
- T21: French bottom-navigation labels wrap awkwardly at enlarged text. The
  API 28–32 preference/recreation branch has code review and JVM coverage but
  no native execution recorded.
- The UX acceptance checklist still has TalkBack, keyboard, narrow/large text,
  landscape, and several route walkthroughs pending.

## Design decisions

### Performance

Profile the existing implementations against their recorded fixtures before
changing them. Make focused query, aggregation, mapping, or rendering changes
only where the profile identifies the cost. Keep the existing domain results,
ordering, filter semantics, source identities, and saved state stable.

Search must retain the approved T15 contract: literal substring matches across
event titles, journal titles, linked tags, people and places; structured filters;
body exclusion; and current pagination and visibility rules. The initial T19
optimization stays within the current schema and search behavior. If the 300 ms
target cannot be met within those constraints, document the measured alternatives
and migration/write-path costs for a separate design review before adding a
search index, cache, synchronization trigger, or schema migration.

Weekly Review must continue to calculate the same distinct counts, weighted
averages, missing-versus-zero states, source attribution, and saved usage
behavior. Reduce the measured repository cost without changing the metric
contract or introducing a persistent cache without evidence.

For Timeline, use a representative-device scrolling profile to identify work
that can be removed from the hot path. Preserve chronological stability,
window behavior, restoration, cancellation, and typed row content. Record frame
timings, missed-frame rate, device, display mode, build variant, fixture, and
profile method. Do not claim smooth scrolling from day-navigation timing alone.

### Accessibility and language behavior

Use `docs/UX_ACCEPTANCE_2026-10-14.md` as the runbook for TalkBack and screen
size checks. Close every outstanding checklist item that exercises TalkBack,
keyboard, screen width, text scale, landscape, insets, or route-control
reachability. Test actual English, French, and Persian resource selection and
Persian RTL. Cover 320 dp and normal phone widths, portrait and landscape, font
scales 1.0, 1.3, and 2.0, keyboard navigation where applicable, and TalkBack
names, roles, states, heading traversal, status feedback, focus stability, and
action reachability. Use generated data in a disposable install and record each
result and evidence path in the checklist. Backup, reset, Health Connect, and
Usage Access acceptance stays with T13, T17, and T18 unless a specific
accessibility or layout check also exercises that surface.

Inspect French primary navigation at the same widths and font scales. Visible
labels must remain readable without awkward word breaks or clipping, and each
item must retain its full localized accessible name. Text must continue to
respect the user's font scale.

Exercise the existing app-language preference and activity-context path at API
28 and API 32. Verify selection, activity recreation, process/force-stop relaunch,
resource language, layout direction, and independence from the system language.
Use generated state to confirm selected dates and an in-progress draft survive
the language change and relaunch. Do not change device-wide language settings
outside the disposable emulator; restore any temporary emulator settings after
the run.

## Acceptance and evidence

- T15 Search repository-page p95 is below 300 ms for the 50,000-entry,
  five-year fixture, including relation mapping, on a recorded representative
  device/build. Include title-substring and no-match queries as well as broad
  and date-filtered controls; report median and p95 after initialization.
- T16 Weekly Review repository p95 is below 300 ms on the 50,000-entry,
  five-year fixture with 2,500 selected mood entries. Record device/build,
  warm-up method, median, and p95.
- T11 scrolling is exercised on representative hardware with the multi-year and
  large-day fixture. Pass when p95 frame time is within the active display
  deadline, no more than 5% of frames miss that deadline, and no visible stalls
  occur during the controlled scroll. Record the same frame metrics as the
  existing probe. If representative hardware is unavailable, retain T11 as
  pending.
- Complete the outstanding TalkBack, keyboard, screen-size, text-scale,
  landscape, inset, and route-control walkthroughs in
  `docs/UX_ACCEPTANCE_2026-10-14.md`; record PASS, FAIL, BLOCKED, or NOT RUN and
  evidence for each. Automated semantics or forced Compose layout alone do not
  certify TalkBack speech or native layout.
- French navigation labels satisfy the readability and accessible-name checks
  at the specified widths and scales.
- API 28 and API 32 language preference/recreation behavior is recorded from
  native runs, or remains explicitly BLOCKED with the missing system-image or
  device stated.
- Update `TASKS.md`, `REMAINING_TASKS.md`, `docs/VALIDATION.md`, and the T11,
  T15, T16, or T21 source records only with evidence obtained in this work.
  Leave any task whose required device or performance acceptance remains open.

Existing focused tests and fixture coverage should be reused. Add targeted
regressions when an implementation change affects query results, aggregation,
ordering, navigation semantics, or language state. Re-run only the checks needed
to validate the changed code, plus the agreed performance and manual acceptance
work. No dependency, permission, network, or product-scope changes are part of
this design.

## Current execution constraint

At design time an API 36.1 AVD is listed, but the ADB server cannot start in the
current sandbox because socket access is denied. No native test has been run as
part of this design. Reassess emulator access at implementation time; do not
change the existing AVD's stored data to bypass this constraint.
