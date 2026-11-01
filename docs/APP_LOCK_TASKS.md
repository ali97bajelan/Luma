# App lock task board

Date: 1 November 2026 · Status: **T01–T08 source complete; consolidated QA in progress with partial evidence**

[Product requirements](superpowers/specs/2026-10-31-app-lock-product-requirements.md) · [Execution spec](superpowers/specs/2026-10-31-app-lock.md) · [Implementation plan](superpowers/plans/2026-10-31-app-lock.md) · [QA scenarios](feature-roadmap/app-lock-qa.md) · [QA test data](feature-roadmap/app-lock-test-data.md) · [Fixture JSON](feature-roadmap/fixtures/app-lock-v1.json)

## Scheduling rule

Phase one completes all implementation tasks (T01–T08) and their source handoffs. Phase two starts one consolidated QA task for the integrated implementation. Dependencies refer exclusively to implementation source handoffs, never QA PASS. Do not start a dependent source change against an undefined contract. Source handoffs record signatures, paths, patch identity, static checks and uncertainty. Use `SOURCE COMPLETE — QA PENDING` for implementation-only work.

This is the user's app-lock exception to the ordinary per-task ordering in [WORKFLOW.md](feature-roadmap/WORKFLOW.md). QA failure affects acceptance and relevant evidence, not implementation scheduling.

## Cards

| Card | Deliverable | Implementation prerequisites (source only) | Implementation |
| --- | --- | --- | --- |
| ALOCK-T01 | Domain authorization, classification and timing contracts | None | SOURCE COMPLETE — QA PENDING |
| ALOCK-T02 | Password/recovery stores, proofs and cooldown | T01 source handoff | SOURCE COMPLETE — QA PENDING |
| ALOCK-T03 | Lifecycle coordinator, shell gate, pending navigation | T01, T02 source handoffs | SOURCE COMPLETE — QA PENDING |
| ALOCK-T04 | Secured content projections, readers and writers | T01, T03 source handoffs | SOURCE COMPLETE — QA PENDING |
| ALOCK-T05 | Restricted creation, multi-drafts and media isolation | T01–T04 source handoffs | SOURCE COMPLETE — QA PENDING |
| ALOCK-T06 | Setup, unlock, lock settings and localized recovery UI | T02, T03, T05 source handoffs | SOURCE COMPLETE — QA PENDING |
| ALOCK-T07 | Widgets/notifications, action authorization and screenshots | T03–T05 source handoffs | SOURCE COMPLETE — QA PENDING |
| ALOCK-T08 | Backup/share/reset, Focus integration and feature acceptance | T01–T07 source handoffs | SOURCE COMPLETE — QA PENDING |
| ALOCK-QA | Consolidated QA for T01–T08 | All T01–T08 source handoffs and integrated candidate | PENDING — partial evidence available |

## Consolidated QA task

After all implementation handoffs, execute AL-Q01–AL-Q22 from the linked QA scenarios against the integrated candidate, including the required device/API matrix and security review. Record each scenario and matrix cell once, covering shared boundaries in the same execution. Reuse applicable evidence from [the 31 October F06-v2 run](feature-roadmap/evidence/2026-10-31/F06-v2/summary.md) and the QA ledger when it remains valid for the candidate; complete only missing or invalidated checks. Current-source gates after the gate-semantics fix: 169 C1 passes; C2 passes 340/340 (app 310, core:data 30); C3 debug/instrumentation assembly and lint succeed (app lint 156 warnings + 11 informational; core:data none); C5 minified unsigned release assembly succeeds with Kotlin-metadata R8 warnings. A current-source debug-signed API36 release copy starts and reaches App Lock setup, but its locked-state behavior remains open. The latest full app C4 JUnit has 279 cases: 256 passed, 22 failed, 1 skipped; Gradle console reports 280. The gate Unlock/semantics/settings regression passes 1/1. App projection/lifecycle integration, locked-state release checks, API28, the remaining device matrix and security review still need evidence.

QA status: **IN PROGRESS — Q02 manual API36 setup, process cold relaunch to the locked gate, and Luma-password unlock passed; the focused gate test also confirms covered Today semantics are hidden and settings navigation works. Current-source C2 passes 340/340, C3 debug/instrumentation assembly and lint complete, and C5 minified release assembly completes. Current full API36 app C4 has 279 JUnit cases: 256 passed, 22 failed, 1 skipped (Gradle console reports 280). Remaining C4 failures, locked-state release checks, API28/device/security obligations and the C4 count discrepancy remain open.** Partial existing evidence does not imply a pass. N/A applies only to individual scenarios or matrix cells with a documented rationale. If a defect or source change invalidates evidence, fix it and retest only affected checks. Record the candidate SHA/patch/APK/fixture hashes, package and device identity, results, command exits, observations, defects, fixes, retests and invalidated evidence. Do not invent evidence.

## Board lanes and update rules

Implementation lanes: **TODO → IN PROGRESS → SOURCE COMPLETE — QA PENDING**, or **BLOCKED** with a concrete missing source/input. Each source handoff records start/end, base SHA, owned paths, patch hash including new files, consumed/produced signatures, requirement mapping, source checks and unresolved uncertainty.

The sequence is T01.I → T02.I → T03.I → T04.I → T05.I → T06.I / T07.I → T08.I → ALOCK-QA. T06.I and T07.I may be scheduled in either order after their source prerequisites. This does not authorize delegation or concurrent edits to shared files. Acceptance requires all implementation handoffs and consolidated QA PASS on the current source, including required matrix and security review. A new requirement revises the source handoff; do not hide changed scope in a test expectation.

## Current scope decisions

The spec's working baseline includes recovery codes; original password/system-auth conflicts are resolved for planning in favor of the user's independent password and password-free capture. No app implementation, test adapter, build, device mutation, commit or release is performed by creating this board.
