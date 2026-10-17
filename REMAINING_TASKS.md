# Remaining work

Status reconciled on 17 October 2026 from [`TASKS.md`](TASKS.md), the UX review,
its acceptance checklist, validation notes, and the implementation plans under
[`docs/superpowers/plans/`](docs/superpowers/plans/). “Open” means the source
documents still record an acceptance result, performance target, or implementation
step as incomplete. Historical test results are not treated as new results.

## Open implementation and release tasks

| Task | Tracker status | Work that remains | Source |
| --- | --- | --- | --- |
| T11 — Life Timeline | Implemented; validation pending | Establish smooth scrolling on representative hardware. Existing debug-emulator frame probe missed 52.43% of platform deadlines; day navigation measured 116–150 ms. Profile/optimize if needed, then record the device result. | [`TASKS.md`](TASKS.md), [`VALIDATION.md`](docs/VALIDATION.md) |
| T13 — Health/activity | Implemented; validation pending | On a supported device with known step data, grant native Health Connect access and verify that useful steps import and appear correctly. Current emulator probe returned `GRANTED / NO_DATA`; no known-data fixture has been imported. | [`TASKS.md`](TASKS.md), [`VALIDATION.md`](docs/VALIDATION.md) |
| T15 — Search | Implemented; validation pending | Optimize title-substring and no-match searches on the 50,000-row fixture, which measured 2.0–2.2 seconds. Retest on representative hardware and record the result. Broad and date-filtered pages measured about 10–25 ms. | [`TASKS.md`](TASKS.md), [`VALIDATION.md`](docs/VALIDATION.md) |
| T16 — Weekly Review | Implemented; validation pending | Optimize the 50,000-row/five-year repository case: p95 was 364 ms against the documented 300 ms target. Retest and record results. | [`TASKS.md`](TASKS.md), [`VALIDATION.md`](docs/VALIDATION.md) |
| T17 — Backup, export, restore | Implemented; validation pending | Complete process-lifecycle, concurrent cleanup, and power-loss acceptance. Room round-trip/replacement/rollback cases and the native document-picker flow have passed on the isolated validation install. | [`TASKS.md`](TASKS.md), [`BACKUP_FORMAT.md`](docs/BACKUP_FORMAT.md) |
| T18 — Privacy, permissions, settings | Implemented; validation pending | Verify Android app-data reset through termination and reopen, native provider permission/revocation flows, lifecycle behavior, and preservation of exported backup copies. The reset boundary is currently mocked; no real reset or provider grant was performed. | [`TASKS.md`](TASKS.md), [`PRIVACY.md`](docs/PRIVACY.md) |
| T19 — Reliability, performance, accessibility | Pending | Close Timeline/Search/Review performance work and broad manual TalkBack/screen-size acceptance; record API 28/32 language behavior (BLOCKED: only API 36.1 system image is installed). French navigation wrapping is implemented and automated layout/semantics coverage passes. | [`TASKS.md`](TASKS.md), [`VALIDATION.md`](docs/VALIDATION.md) |
| T20 — MVP release readiness | Pending | Run and record the complete fresh-install journey. It depends on the Health Connect known-data check, backup and reset acceptance, outstanding performance work, and known Android/API limitations being documented. | [`TASKS.md`](TASKS.md), [`VALIDATION.md`](docs/VALIDATION.md) |

## UX review acceptance still open

All 18 findings in [`UX_REVIEW_2026-10-13.md`](docs/UX_REVIEW_2026-10-13.md)
have implementation changes. The review and
[`UX_ACCEPTANCE_2026-10-14.md`](docs/UX_ACCEPTANCE_2026-10-14.md) keep manual
acceptance open; automated tests do not close TalkBack, native keyboard, or
physical-device checks.

The acceptance checklist marks findings **01–12 and 15** as NOT RUN for their
listed walkthroughs. These cover past-day capture, import-switch accessibility,
filtered Timeline updates, Persian date navigation, compact and large-text
layouts, reading/editing entries from different routes, Review evidence, Settings
navigation, mood-scale anchors, rejected-tag recovery, dense-day paging, and
People/Places updates.

Findings **13, 14, 16, 17 and 18** have implementation changes and automated
coverage, with manual acceptance still pending. Remaining examples recorded in
the checklist include Search lifecycle/provider walkthroughs; TalkBack speech and
navigation; language switching and disclosure state; Review duration-sign and
51-plus-source paging checks; the long direct-memory route; and detail-route
keyboard, landscape, inset, retry, loading, and error checks.

The shared manual matrix also remains open for actual English/French/Persian
switching, Persian RTL, 320 dp and normal widths, portrait/landscape, text scales
1.0/1.3/2.0, light/dark where relevant, conventional keyboard operation,
TalkBack speech, draft restoration, and injected failure/retry behavior. The
checklist has the exact steps and evidence fields; record each result there.

T19 accessibility/language update (17 October): compact French primary-tab labels
now keep their full localized accessible names. The automated Compose matrix passes
for English/French/Persian at 320 dp and 411 dp and font scales 1.0, 1.3 and 2.0.
Native API 36 language flows also pass, including Persian RTL, selected-day
restoration and journal-draft retention. API 28/32 native execution is BLOCKED
because no corresponding system image or AVD is installed. Manual TalkBack speech,
keyboard and route walkthroughs remain NOT RUN; T19 stays open for those checks and
the performance targets.

## Additional plan acceptance

The automatic usage/steps-on-open implementation plan says implementation is
complete, but Android instrumentation runtime and native-provider acceptance are
pending. On a test install with generated or known data, verify launch/return
imports, last-sync updates, Usage Access return, Health Connect permission result,
revocation, retained history, independent source updates, and cancellation when
the app leaves RESUMED. The plan says no test device was attached for this work.
See [`2026-10-14-auto-update-on-open.md`](docs/superpowers/plans/2026-10-14-auto-update-on-open.md).

## Plan and status conflicts to keep in mind

Use the current task tracker, dated UX handoff, acceptance checklist, and recorded
validation results together. Several plan checkboxes and status lines were not
updated after later work:

- The UX handoff says findings 13, 14, and 16 are implemented, but their plans
  still say implementation has not started and retain unchecked implementation
  steps. Findings 17 and 18 plans say implementation is complete.
- UX plans for findings 08, 09, and 15 retain unchecked implementation steps,
  while the UX review marks those findings implemented. Their manual acceptance
  still belongs to the open checklist above.
- The older T15 and T16 plans retain unchecked device/performance steps. Device
  flows have later passing evidence; the unresolved query performance is captured
  under T15/T16 above.
- The auto-update design spec still says “implementation has not started,” while
  its plan records implementation complete and native acceptance pending.

Treat an unchecked historical plan box as a prompt to compare evidence, not as
proof by itself that its entire feature remains unimplemented. Reconcile these
markers when updating the original plans; do not replace their dated evidence
with assumptions.

## Completed or outside the current MVP remainder

The task tracker marks T01–T10, T12, T14, and T21 complete. The original
[`audit.md`](audit.md) findings are historical; the later UX review and handoff
record their implementation follow-ups and current acceptance gaps. The future
backlog in `TASKS.md`—voice notes, habits, conversational retrieval, semantic
search, AI narratives, and encrypted cloud sync—is explicitly outside the MVP and
is not counted as remaining release work here.
