# T33 — Accessibility, regression and visual acceptance specification

Date: 2026-10-22  
Status: Proposed / pending implementation. This document is a planning artifact, not release acceptance.  
Depends on: T31 and T32 (all four children)

## Intent and sources

Produce auditable acceptance evidence for the required redesign and separately record any opted-in feature results.

References: Original six app-screen PNGs plus four new editor light PNG/HTML pairs; T34 separately owns launcher widget acceptance; reference-resolution brief. Read the [shared reference brief](../../luma-redesign/README.md) and [task index](../../UI_REDESIGN_TASKS.md) with this specification. The brief resolves inconsistencies and provides common tokens; it is normative for every task.

## Editor-reference acceptance extension — 23 October 2026

T33 final acceptance now also depends on T35–T38. Add H35-1–5, M36-1–5, T37-1–5 and R38-1–5 from their specs to the acceptance ledger, with both-theme en/fr/fa/RTL/window/font/IME and TalkBack evidence for each affected editor. T36 shared timestamp changes also require Journal/Memory compatibility checks. Record new evidence under `docs/luma-redesign/evidence/<actual-run-date>/t35/` through `t38/` and link the route ledger. Unselected O09–O12 are intentional differences; selected options require their own regression results. Existing T33 and T34 blockers and gates remain unchanged; no new acceptance was run by this documentation pass.

## Global constraints

- Native Android, Kotlin, Compose Material 3 and Navigation Compose; keep minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- Offline operation; no account, cloud sync, AI service, telemetry, new permissions or schema migration in required visual work.
- System/Light/Dark selection and English/French/Persian support remain available offline.
- Preserve existing route IDs, callbacks, saved state, data semantics, loading/error/retry/undo and accessibility tags unless an optional task explicitly changes its own contract.
- Use actual saved data; never ship reference people, dates, percentages, measurements, quotes or charts as user records.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL and system animation scale.
- Optional tasks default to deferred and are not required for redesign acceptance. No optional feature is authorized for implementation by this planning document.

## Screen and behavior contract

- Create `docs/luma-redesign/acceptance-2026-10-22.md` and `docs/luma-redesign/evidence/<actual-run-date>/`. Record actual run date/build SHA/device/API, locale, theme, window dp and font scale. The filename is the planning baseline, not a claim tests ran on that date.
- Capture Today, Timeline and Review in light/dark English at 411 × 891 dp/font scale 1.0 using equivalent controlled records. Compare composition, type roles, surfaces and hierarchy rather than raw pixels against narrow 1600px-tall references.
- For every route family, inspect English/French/Persian in both themes at 320 dp/2.0 and 840 dp width. Capture representative screenshots; the route ledger must record each route result, even when sharing family evidence.
- Exercise empty/loading/error/retry/undo, unavailable/partial/zero integrations, historical date and partial week, long names/body/units, restored drafts and off-preview recovery. Check TalkBack order/names/states, keyboard focus, contrast, system bars and animation-disabled progress.
- Include the corrected Timeline light date/count ribbon and the existing Quick Capture sheet in visual inspection; verify View options expands its actual controls, and capture dismissal writes nothing. Optional habit action O08 is absent unless selected.
- Optional tasks appear in a separate acceptance section with their decision status. Four-tab mismatch, absent type filters/photo previews/streaks/charts/quotes are intentional in the required base.
- Use PASS, FAIL or NOT RUN per check; missing device/tool/data is NOT RUN with a reason and follow-up. Do not mark release acceptance complete while required checks are unrun or failing. Never clear real user data for fixtures.

## Implementation boundaries

- Acceptance evidence and route ledger only; no production behavior change.

Existing public/internal composable signatures remain compatible except additions explicitly named above. Existing repositories/view models own data and mutations; decorative components receive values and callbacks. New files named in the contract are proposed implementation files, not files already created by this documentation change. Consult the paired plan for test ownership.

## Acceptance scenarios

1. Evidence includes six primary-screen captures, the route ledger, measured color-pair results and a reproducible environment/fixture description.
2. Relevant existing unit/instrumented regression suites pass; any blocked build/device checks have explicit NOT RUN entries.
3. No unresolved clipping, inaccessible action, false metric/privacy statement, obscured final row or broken state/navigation remains in required scope.

## Verification and completion

Use focused existing regression coverage plus visual/manual checks for the changed surfaces. Add tests for changed behavior and meaningful state/semantics risks; do not add tests that merely assert padding or duplicate token assignments. Required font/contrast/RTL/inset checks are acceptance work, not a reason to add fake data. Record PASS/FAIL/NOT RUN and evidence in the T33 ledger. No checks are claimed to have run in this specification.

[Implementation plan](../plans/2026-10-22-t33-redesign-acceptance.md)
