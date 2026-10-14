# Remaining UX work: development handoff

Status: findings 13, 14, 16, 17 and 18 have implementation changes; manual acceptance remains pending.

Baseline for the handoff: `391e7a4e5f1b87164f797c41eb776810a2935692`, inspected 14 October 2026. Source of scope: [UX review](UX_REVIEW_2026-10-13.md), current task tracker. Findings **13, 14, 16, 17 and 18** have implementation changes. All 18 findings have implementation changes; manual acceptance remains open across affected findings.

## Intent and success

Prepare all remaining UX findings for development in the existing offline Android app. A developer should be able to begin a unit using its spec and plan without inventing filter behavior, validation semantics, duration precision, navigation behavior, copy, or acceptance tests.

This handoff supplied the specs and plans used for findings 13 and 14/16. Their implementation does not close manual acceptance or authorize a release. Assumptions for remaining work: preserve the current architecture and search scope; make targeted changes; retain Review's chronological source order and label it.

## Brainstormed approaches

| Approach | Benefit | Cost / weakness | Decision |
| --- | --- | --- | --- |
| Targeted changes within current routes, with small shared presentation helpers | All five findings addressed; existing data and navigation contracts remain stable; independently testable units | Some route wiring remains repetitive | Recommended |
| Rebuild all capture/detail screens around a new generic screen framework | Common structure everywhere | Broad refactoring, more regression risk, and unnecessary abstractions for this scope | Reject |
| Copy and semantics patches only | Smallest initial change | Does not provide named removable filters, field association or persistent detail controls | Insufficient |

For 17, preserving ascending Review sources with visible order labels is recommended over changing SQL to descending. `ReviewOverview.LatestMemory` requests the last ascending memory row; reversing SQL requires changing that algorithm and its tests. A sort selector is unnecessary for this finding.

## Development units and sequence

| Sequence | Findings | Spec | Implementation plan | Depends on | Status |
| --- | --- | --- | --- | --- | --- |
| 1 | 13 | [Search filters](superpowers/specs/2026-10-14-ux-13-search-design.md) | [Search plan](superpowers/plans/2026-10-14-ux-13-search.md) | Current search behavior | Implemented; manual acceptance pending |
| 2 | 14, 16 | [Feedback and capture](superpowers/specs/2026-10-14-ux-14-16-feedback-capture-design.md) | [Feedback/capture plan](superpowers/plans/2026-10-14-ux-14-16-feedback-capture.md) | Unit 1 only for final Search status wiring | Implemented; manual acceptance pending |
| 3 | 17 | [Duration and order](superpowers/specs/2026-10-14-ux-17-display-design.md) | [Display plan](superpowers/plans/2026-10-14-ux-17-display.md) | Existing usage calculations and source order | Implemented; manual acceptance pending |
| 4 | 18 | [Detail navigation](superpowers/specs/2026-10-14-ux-18-detail-navigation-design.md) | [Navigation plan](superpowers/plans/2026-10-14-ux-18-detail-navigation.md) | Existing detail routes | Implemented; manual acceptance pending |
| 5 | All 18 | [Acceptance checklist](UX_ACCEPTANCE_2026-10-14.md) | Execute targeted and shared checks | Implemented units | Automated checks recorded; manual acceptance pending |

Units 1–4 have been implemented. Finding 16 was folded into unit 2 because it shares capture disclosures and accessibility states. The implementation sequence is complete; finish the outstanding manual acceptance checks recorded in the checklist.

## Project-wide constraints

- Keep `minSdk = 28`, `compileSdk = 35`, `targetSdk = 35`, JDK 17, AGP 8.7.3, Kotlin 2.2.10 and Compose BOM 2025.08.01.
- Add no product dependency, database migration, permission, network access, telemetry or AI service.
- Keep English, Persian and French available offline; localize all new labels, dates, numbers and semantic descriptions.
- Preserve user-authored text, stable IDs, raw durations, occurrence timestamps and timezone rules.
- Preserve unsaved-change confirmations, rejected pending-tag save blocking, photo retention, read-only imported records and retry behavior.
- Missing observations stay unavailable; recorded zero stays zero; positive durations never display as zero.
- Every interactive control has an accessible name and at least a 48 dp touch target. Text wraps or ellipsizes without reducing the configured font scale.
- Automated semantics checks do not certify TalkBack speech. Manual acceptance remains pending until evidence is recorded.

## Shared decisions and exact boundaries

Search continues to match titles and linked tag/person/place names; bodies and notes are outside scope. Categories combine with AND, selected tags/people with OR. Removing a chip removes one structured filter. Reset filters retains text; Clear all clears text and filters.

Editor text-size feedback identifies the rejected field and retains the last accepted draft. A same-value TextField callback must not erase a rejection. This work does not change existing save eligibility for accepted text; the existing pending-tag failure still blocks Save.

Summary duration precision is whole seconds below one minute and whole minutes at or above one minute, with an explicit less-than-one-second state. Exact milliseconds remain available in existing full usage details. Timeline and Search are labeled newest first; Review sources and People/Places histories are labeled oldest first.

Detail bars use Back, not the editor's Cancel/close icon. Edit remains conditional on the current record's capability. Memory actions remain in detail content. Back must work in loading, missing and error states.

## Development preflight

1. Read the selected spec, its plan and the acceptance checklist. Confirm the baseline has not gained an implementation of that finding; adapt only affected file anchors if it has.
2. Inspect `git status --short` and relevant changes. Existing untracked `app/release/`, `core/data/schemas/com.luma.lifeos.data.db.LumaDatabase/` and `core/data/src/test/resources/` are unrelated; preserve them.
3. Use the repository's current toolchain and Gradle wrapper. Check JDK/SDK and a disposable device before running builds/device checks. No toolchain upgrades are required.
4. Use an isolated development branch/worktree at execution time if needed. Do not create or overwrite a branch as part of planning.
5. Run device tests only in a disposable validation application ID with generated records. Never run mutating fixture tests against the installed personal app.
6. Record actual command results, failed/skipped checks, screenshots and TalkBack observations. Do not turn the review's historical results into new passing results.

## Completion and handoff rules

Each plan supplies file responsibilities, interfaces, tests and commit boundaries. After a unit's required checks pass, update its review finding to implemented and record remaining manual checks. Mark a finding accepted only after its acceptance checks pass. Keep the master checklist's result/evidence fields honest.

For units 1 and 2, implementation status and automated results are recorded in the UX review and acceptance checklist. Do not treat automated semantics checks as TalkBack acceptance.

Independent release/reliability backlog in `TASKS.md` is outside this UX planning scope. Backup/provider/process-lifecycle checks mentioned by the UX review are included as acceptance checks, not as new feature development.

## Preparation verification — before implementation

On 14 October 2026, document checks verified 11 Markdown files for local links, fenced blocks, unresolved placeholders and trailing whitespace; 72 Modify/Test file references resolve to current source files or files explicitly created by an earlier planned task. All nine implementation tasks have interfaces and five checkable steps. `git diff --check` passes. Specs were checked against the five remaining findings and the acceptance matrices cover all 18 finding IDs.

These were document checks only. At that preparation checkpoint, application code, dependencies and database schemas were not changed; application builds, automated behavior tests and device/TalkBack acceptance were not run. The review's existing untracked artifacts were preserved.

## Execution update — 14 October 2026 (tasks 17–18)

Implemented both remaining units in the existing checkout based on HEAD `b52b9a7`. The normal debug app and Android test APKs build; `:app:testDebugUnitTest` and `:app:lintDebug` pass. Forty-five affected AndroidJUnitRunner cases pass across isolated runs on API 36 (`emulator-5554`) using `com.luma.lifeos.uxvalidation`; see the [acceptance checklist](UX_ACCEPTANCE_2026-10-14.md) for classes and coverage. `git diff --check` passes. Manual TalkBack, physical layout/keyboard, and route-level extreme-text acceptance remain pending. No commit was created because the implementation request did not ask for one.
