# Focus UX redesign task tracker

Source: [audit](../FOCUS_UX_REDESIGN.md).
Behavior: [spec](superpowers/specs/2026-11-08-focus-ux-redesign.md).
Execution: [plan](superpowers/plans/2026-11-08-focus-ux-redesign.md).
Device verification: [QA protocol](feature-roadmap/focus-ux-redesign-qa.md).

## Phase rule

**I01–I10 all finish first. Then QA01 runs one consolidated emulator QA campaign across every change (matrix cases Q01–Q12).**

During implementation: edit code/test sources, inspect code, compile, run local JVM tests and static checks. Do not deploy or run emulator/manual/connected tests. Existing unrelated androidTest runs initiated by the user are not redesign QA evidence.

Statuses: [ ] pending; [x] implemented/local-verified (I tasks) or QA-passed with evidence (Q task). Do not infer runtime success from an implementation checkbox.

## Phase 1 — Implementation

| Status | ID | Deliverable | Priority | Depends on | Spec | Device cases |
|---|---|---|---|---|---|---|
| [x] | I01 | Non-overlapping consequence-labeled leave dialog | P0 | — | S03 | Q04,Q12 |
| [x] | I02 | Required-input guidance, IME and localized copy | P1 | — | S02,S08,S09 | Q01,Q02,Q12 |
| [ ] | I03 | Preparation hierarchy, timer presets, optional task/tag/recent work | P1 | I02 | S02 | Q01–Q03,Q09 |
| [ ] | I04 | State-aware Today and History/Settings header wiring in both hosts | P1 | I01,I03 | S01 | Q01,Q04,Q09,Q10 |
| [ ] | I05 | Running/paused hierarchy, shortcut/help and failure feedback | P1 | I01,I04 | S03,S08 | Q04,Q08,Q10,Q11 |
| [ ] | I06 | Single-primary completion, reflection and explicit task actions | P1 | I02,I05 | S04 | Q05,Q09,Q11 |
| [ ] | I07 | Active/expired break hierarchy and custom input | P1 | I06 | S05 | Q06,Q11 |
| [ ] | I08 | Inline progress, compact history and disclosed run details | P2 | I03,I04,I06 | S06 | Q07,Q09,Q11 |
| [ ] | I09 | Grouped settings, goal/reminder/tag/shortcut status and recovery | P2 | I04 | S07,S08 | Q08–Q11 |
| [ ] | I10 | Cross-surface accessibility/localization integration and local verification | P1 | I01–I09 | S01–S09 | Q01–Q12 |

### Implementation gate G1

- [ ] Every I task is checked and maps to its acceptance criteria.
- [ ] No deferred background-Back experiment, persistence redesign or new dependency entered scope.
- [ ] Both navigation hosts are wired and every new string has English/Persian/French resources.
- [ ] Changed production/test sources compile; applicable JVM tests/static checks pass.
- [ ] Connected test sources and any failure-injection harness compile; no emulator tests executed as part of Phase 1.
- [ ] Record branch/commit, variant/application ID, check commands/results and known limitations in QA report.
- [ ] Mark handoff “Implementation complete; consolidated emulator QA pending.”

## Phase 2 — Consolidated emulator QA

- [ ] QA01: Execute every Q01–Q12 case in [QA protocol](feature-roadmap/focus-ux-redesign-qa.md) against the completed implementation.
- [ ] Record results/screenshots, failures and blocked cases in docs/feature-roadmap/focus-ux-redesign-qa-report.md.
- [ ] Fix discovered defects; rerun affected cases and neighboring state/navigation flows within the same campaign.
- [ ] If a fix changes shared timing, privacy or navigation, rerun all affected matrix rows, not just the original failing tap.
- [ ] G2: no open blocker/high-impact regression; every required case passed with evidence. Blocked cases mean conditional/incomplete sign-off.

## Deferred items

Background-safe ordinary Back requires a separate product decision and plan. Participant research and retention measurement are optional follow-up work. Neither is silently included in implementation or falsely reported as emulator QA.
