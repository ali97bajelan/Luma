# App lock task board

Date: 31 October 2026 · Status: **T01–T04 SOURCE COMPLETE — QA PENDING; T05–T08 IMPLEMENTATION NOT STARTED; QA NOT RUN**

[Product requirements](superpowers/specs/2026-10-31-app-lock-product-requirements.md) · [Execution spec](superpowers/specs/2026-10-31-app-lock.md) · [Implementation plan](superpowers/plans/2026-10-31-app-lock.md) · [QA scenarios](feature-roadmap/app-lock-qa.md) · [QA test data](feature-roadmap/app-lock-test-data.md) · [Fixture JSON](feature-roadmap/fixtures/app-lock-v1.json)

## Scheduling rule

Each card has exactly **two phases: Implementation → QA**. Complete the implementation wave first (T01–T08 source handoffs), then the QA wave. Dependencies in this board refer exclusively to **implementation source handoffs**, never QA PASS. In particular, T02 implementation can start after T01 source handoff while T01 QA remains NOT RUN. The same rule applies transitively to every card.

This is the user's app-lock exception to the ordinary per-task ordering in [WORKFLOW.md](feature-roadmap/WORKFLOW.md). Source dependencies are real: do not start a dependent source change against an undefined contract. Source handoff records signatures, paths, patch identity, static checks and uncertainty. Use `SOURCE COMPLETE — QA PENDING`, not “done,” for an implementation-only card. QA failure affects acceptance and dependent evidence; it is not an automatic scheduling edge from every earlier QA to every later implementation.

## Cards

| Card | Deliverable | Implementation prerequisites (source only) | Implementation | QA | QA scenarios | Overall |
| --- | --- | --- | --- | --- | --- | --- |
| ALOCK-T01 | Domain authorization, classification and timing contracts | None | SOURCE COMPLETE — QA PENDING | NOT RUN | Q03–Q08, Q22 | TODO |
| ALOCK-T02 | Password/recovery stores, proofs and cooldown | T01 source handoff | SOURCE COMPLETE — QA PENDING | NOT RUN | Q01–Q02, Q13–Q15, Q22 | TODO |
| ALOCK-T03 | Lifecycle coordinator, shell gate, pending navigation | T01, T02 source handoffs | SOURCE COMPLETE — QA PENDING | NOT RUN | Q05–Q08, Q20, Q22 | TODO |
| ALOCK-T04 | Secured content projections, readers and writers | T01, T03 source handoffs | SOURCE COMPLETE — QA PENDING | NOT RUN | Q03–Q04, Q05, Q10, Q12, Q22 | TODO |
| ALOCK-T05 | Restricted creation, multi-drafts and media isolation | T01–T04 source handoffs | TODO | NOT RUN | Q08–Q12 | TODO |
| ALOCK-T06 | Setup, unlock, lock settings and localized recovery UI | T02, T03, T05 source handoffs | TODO | NOT RUN | Q01–Q02, Q13–Q15, Q19, Q21 | TODO |
| ALOCK-T07 | Widgets/notifications, action authorization and screenshots | T03–T05 source handoffs | TODO | NOT RUN | Q05, Q16–Q17, Q21 | TODO |
| ALOCK-T08 | Backup/share/reset, Focus integration and feature acceptance | T01–T07 source handoffs | TODO | NOT RUN | Q18–Q22; integrated Q01–Q22 | TODO |

`Qnn` means `AL-Qnn` in the linked QA document. Scenario ownership is shared where a boundary crosses tasks; T08 owns the final combined execution record. No rows report execution results yet.

## Board lanes and update rules

Implementation lanes: **TODO → IN PROGRESS → SOURCE COMPLETE — QA PENDING**, or **BLOCKED** with a concrete missing source/input. QA lanes: **NOT RUN → IN PROGRESS → PASS / FAIL / BLOCKED**. N/A applies to individual scenarios/matrix cells only with a documented rationale, not as a substitute for an entire undelivered feature.

Overall card COMPLETE requires its implementation handoff and applicable QA PASS on the current source. During the implementation wave no card is overall COMPLETE. Release requires all cards complete, cross-feature acceptance passed and required matrix/security review finished.

Allowed sequence: T01.I → T02.I → T03.I → T04.I → T05.I → T06.I → T07.I → T08.I → QA wave. T06.I and T07.I have no mutual dependency; they may be scheduled in either order after their source prerequisites. This does not authorize delegation or concurrent edits to shared files.

At the QA wave, each card requires its own handoff and the integrated candidate's source identity, not PASS from a different card. A discovered shared-contract defect is fixed in QA, affected cards/evidence are reopened and retested. A new requirement revises the source handoff; do not hide changed scope in a test expectation.

## Per-card handoff record

Copy beneath the owning card in the plan at execution time:

- Implementation: start/end, base SHA, exact owned paths, patch hash including new files, consumed/produced signatures, requirement mapping, source checks, unresolved uncertainty.
- QA: candidate SHA/patch/APK/fixture hashes, isolated package/device identity, case/matrix results, actual command exits, logs/manual observations, defects/fixes/retests and invalidated prior evidence.
- Acceptance: exact validated scope, blockers and remaining work. Never prefill PASS or invent build/device evidence.

## Current scope decisions

The spec's working baseline includes recovery codes; original password/system-auth conflicts are resolved for planning in favor of the user's independent password and password-free capture. No app implementation, test adapter, build, device mutation, commit or release is performed by creating this board.
