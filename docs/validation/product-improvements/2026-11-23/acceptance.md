# IP-Q01 — QA-01 follow-up execution record

**Run date:** 2026-11-23  
**QA-01 status:** BLOCKED — partial execution; release acceptance remains pending

[QA plan and scenario matrix](../../../superpowers/plans/2026-11-20-product-improvements-q01-emulator-qa.md#qa-01) · [QA specification](../../../superpowers/specs/2026-11-20-product-improvements-q01-emulator-qa-design.md) · [2026-11-22 integrated QA record](../2026-11-22/acceptance.md)

## Candidate and environment

- **Candidate revision:** `c5419e2ad45fcd0902284e73f3c5bffe7665d3e3`
- **QA source patch SHA-256:** `35474e83f78dffbdd8b2161b156af62cab2c09126b1314dfd2862103831184a9`, over diffs in `app/src/test` and `app/src/androidTest`. The patch adds 57-row state/UI coverage, task bucket coverage, parent-family cancel/confirm/Undo coverage, and viewport-aware hierarchy assertions. A production-source test tag was added to the existing editor scroll container; it has no runtime behavior change. No build configuration changed. The unrelated untracked `app/production/` directory was preserved and excluded.
- **App variant:** `productionDebug`; QA application ID `com.luma.lifeos.qa`, instrumentation ID `com.luma.lifeos.qa.test`; versionCode 21, versionName 1.6.7.
- **APK SHA-256:** app `5347fff55ca337b13edc46b15d46679d749b448f7093c7d989ac36060b8a8370`; instrumentation `497be9f742a29117ac8479fdc273208afe9efc3d009daf1549adf8235d9c4cd9`.
- **Connected device:** `Medium_Phone_API_36.1(AVD) - 16`, Android 16 / API 36, `sdk_gphone64_x86_64`, 1080×2400 px at 420 dpi, portrait. Locale `en-GB-u-fw-mon`, timezone `Asia/Tehran`, font scale 1.0 and system light mode; same AVD profile as the 2026-11-22 record.
- **Fixtures:** synthetic Compose test data and controlled repositories. The paging test covers 57 due/overdue matches against the 50-row page; the full real-data Today → Tasks → Habits → Focus sequence was not seeded or run. The production package was not used.
- **Scope limit:** only API 36 ran in this follow-up. API 28, EN/FR/FA and RTL, dark mode, large text, viewport variations, TalkBack, keyboard, IME/insets and protected-detail direct-entry/relock checks remain outstanding.

## Focused checks

| Check | Result | Evidence |
| --- | --- | --- |
| `bash gradlew :app:testProductionDebugUnitTest --tests com.luma.lifeos.ui.todo.TodayTasksStateTest --rerun-tasks --console=plain` | PASS — 8 tests, 0 failures | [Gradle log](/home/bajelan/.android/studio/agent/conversations/Luma-fc9658513f7c6e6667848c164f3350/cfdfe55b-2a14-4993-9b65-b0b7573d2431/session/agent/shell-logs/shell-23.log) · [JUnit XML](../../../../app/build/test-results/testProductionDebugUnitTest/TEST-com.luma.lifeos.ui.todo.TodayTasksStateTest.xml) |
| `TodayTasksSemanticsTest#fiftySevenMatchingRowsShowSevenUnloadedThenRemainScrollableAfterLoading` | PASS — 1 test | [Gradle log](/home/bajelan/.android/studio/agent/conversations/Luma-fc9658513f7c6e6667848c164f3350/cfdfe55b-2a14-4993-9b65-b0b7573d2431/session/agent/shell-logs/shell-24.log) |
| `TodoAccessibilitySemanticsTest#openTaskBucketsRemainDistinctAndCompletedRowsRemainInCompletedView` | PASS — 1 test | [Gradle log](/home/bajelan/.android/studio/agent/conversations/Luma-fc9658513f7c6e6667848c164f3350/cfdfe55b-2a14-4993-9b65-b0b7573d2431/session/agent/shell-logs/shell-25.log) |
| `TodayTasksFlowTest` | PASS — 2 tests | [Gradle log](/home/bajelan/.android/studio/agent/conversations/Luma-fc9658513f7c6e6667848c164f3350/cfdfe55b-2a14-4993-9b65-b0b7573d2431/session/agent/shell-logs/shell-27.log). The shared per-variant JUnit XML was overwritten by the later hierarchy run. |
| `TodoHierarchyFlowTest#parentFamilyCompletionCanBeCancelledConfirmedAndUndoneWithoutLosingItsChild` | PASS — 1 test | [Final Gradle log](/home/bajelan/.android/studio/agent/conversations/Luma-fc9658513f7c6e6667848c164f3350/cfdfe55b-2a14-4993-9b65-b0b7573d2431/session/agent/shell-logs/shell-46.log) |
| `HabitDateSelectionFlowTest#todayRejectsStaleClicksDuringDelayFailureAndReturnToSameDate` | PASS — 1 test | [Gradle log](/home/bajelan/.android/studio/agent/conversations/Luma-fc9658513f7c6e6667848c164f3350/cfdfe55b-2a14-4993-9b65-b0b7573d2431/session/agent/shell-logs/shell-30.log) |
| `FocusLifecycleTest#recreationSystemBackPauseResumeAndStopKeepOneFocusSession` | PASS — 1 test | [Gradle log](/home/bajelan/.android/studio/agent/conversations/Luma-fc9658513f7c6e6667848c164f3350/cfdfe55b-2a14-4993-9b65-b0b7573d2431/session/agent/shell-logs/shell-32.log) |
| `TodoHierarchyFlowTest#savedParentCreatesIndependentChildAndReturnsThroughParentContext` | PASS — 1 test | The original failure came from tapping the High importance option while it was below the editor viewport; the later delete action had the same offscreen issue. The editor now exposes its existing scroll viewport with a stable test tag, and the test waits for each target to be displayed before acting. Final API 36 rerun passed through child creation, priority persistence, parent navigation, and delete-confirmation cancellation. [Final Gradle log](/home/bajelan/.android/studio/agent/conversations/Luma-fc9658513f7c6e6667848c164f3350/cfdfe55b-2a14-4993-9b65-b0b7573d2431/session/agent/shell-logs/shell-53.log) |

`git diff --check` passed. The broader app suite and the full host/build/lint gates were not rerun in this follow-up. No screenshot was needed for these semantic checks.

## QA-01 acceptance

| Requirement or scenario | Result | Evidence and limit |
| --- | --- | --- |
| **IP-I01-R1 — bounded Today count, load more and scroll** | PASS | State test covers the 50-row page and 57 matching total (7 remaining), then all 57 loaded and zero remaining. Compose test verifies the initial count, Load more, disappearance of the remaining count, and access to the last row by scrolling. |
| **IP-I01-R2 — full Tasks date buckets and completion** | BLOCKED — partial | Focused semantics test distinguishes overdue, today, upcoming, no-date and completed rows. Today flow covers the historical-day task-card state, return to current day, and completion Undo. The Today “View all” route and the invariant that navigation leaves due dates unchanged were not exercised. |
| **IP-I01-R3 — history, Habits, Focus and task-family safeguards** | BLOCKED — partial | Historical-day return and habit date selection/return pass. A direct family test verifies confirmation cancel, confirm and Undo while preserving parent/child identity. Focus recreation/back/pause/resume/stop keeps one session. Hidden-provider behavior, protected task/habit/Focus details, direct-entry/relock, and the complete Today-to-destination flow remain unverified. |
| **Actual emulator midnight crossing and device-zone change** | NOT RUN | A unit test covers historical/future visibility across simulated midnight and zone changes; no connected run crossed midnight. |
| **IP-I01 product validation** | NOT RUN | No unassisted-use observation or large-library usability measure was collected. |

## Integrated QA status

- **IP-Q01-R1 — BLOCKED:** focused JVM and Compose checks ran, but the complete host, lint, connected suite, API 28 and UI/accessibility matrix have not been completed.
- **IP-Q01-R2 — BLOCKED:** this record maps QA-01 evidence; QA-01 itself is partial and QA-02–QA-09 remain open.
- **IP-Q01-R3 — PARTIAL:** the QA-01 paging test, parent hierarchy flow, and family cancel/confirm/Undo coverage pass. The hierarchy test now scrolls its editor viewport until offscreen controls are displayed before interacting. Hidden-provider behavior, protected-detail gates, direct-entry/relock, and the full Today-to-destination flow remain unverified.
- **IP-Q01-R4 — BLOCKED:** this dated record is saved, but the required platform and scenario matrix is incomplete.

**Decision:** Keep QA-01 **BLOCKED — partial execution**, IP-I01 **SOURCE COMPLETE — QA PENDING**, and IP-Q01 **BLOCKED — PARTIAL EXECUTION**. Continue with the missing protected-detail/provider cases, an actual midnight boundary, and the required UI/platform matrix.
