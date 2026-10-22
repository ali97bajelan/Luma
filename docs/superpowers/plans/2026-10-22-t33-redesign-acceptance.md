# T33 — Accessibility, regression and visual acceptance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan does not request subagent delegation.

**Goal:** Produce auditable acceptance evidence for the required redesign and separately record any opted-in feature results.

**Architecture:** Existing repositories/view models retain data ownership and LifeOsApp retains navigation. Shared T30 roles/components provide presentation; the spec defines any optional new interface. Keep current product behavior.

**Tech Stack:** Kotlin, Compose Material 3, Navigation Compose, JUnit/Compose instrumentation, Gradle and JDK 17.

**Spec:** [Corresponding specification](../specs/2026-10-22-t33-redesign-acceptance-design.md).

## Global constraints

- Native Android, Kotlin, Compose Material 3 and Navigation Compose; keep minSdk 28, compileSdk 35, targetSdk 35 and JDK 17.
- Offline operation; no account, cloud sync, AI service, telemetry, new permissions or schema migration in required visual work.
- System/Light/Dark selection and English/French/Persian support remain available offline.
- Preserve existing route IDs, callbacks, saved state, data semantics, loading/error/retry/undo and accessibility tags unless an optional task explicitly changes its own contract.
- Use actual saved data; never ship reference people, dates, percentages, measurements, quotes or charts as user records.
- Minimum interactive target 48 dp; normal text contrast at least 4.5:1, large text and essential non-text indicators at least 3:1.
- Validate at 320 dp/font scale 2.0, 411 × 891 dp/font scale 1.0 and 840 dp width; support RTL and system animation scale.
- Optional tasks default to deferred and are not required for redesign acceptance. No optional feature is authorized for implementation by this planning document.

## Review focus

Restored/historical dates; missing/zero/partial measurements; long RTL/IME layouts; repeated actions/recovery; system bars/focus/contrast.

## Files and interfaces

- Create: `docs/luma-redesign/acceptance-2026-10-22.md` and evidence under `docs/luma-redesign/evidence/<actual-run-date>/`.
- Update: `docs/luma-redesign/route-coverage.md` with actual results. Do not alter real user data.

Consumes: paired child specifications, current route registrations and actual implementation evidence. Produces: a complete coverage ledger and recorded acceptance decision.

### Task 1: Controlled fixtures and visual matrix

- [ ] Use a dedicated test install/emulator and existing fixtures to seed equivalent current/historical dates, counted habits, tasks and records. Capture the prescribed matrix and fill route-family outcomes; restore settings afterwards.
- [ ] Create isolated fixture data with two counted habits, three due/overdue tasks, one undated task for full-list preservation, a journal, real local attachment, date-only habit log and imported/absent/zero/partial signal variants. Use equivalent records for comparison screenshots; do not hard-code fixtures into production.
- [ ] Capture the six main screen/theme combinations and family matrix at the spec widths/locales/font scales. Inspect against the matching PNG and record intentional optional-feature omissions.

### Task 2: Regression and sign-off evidence

- [ ] Run the scoped regression suites plus full app unit/lint/build checks. Record exact commands/results, inspect TalkBack/keyboard/contrast and compare screenshots. Resolve failures and record optional decisions before acceptance.
- [ ] Run the commands below plus relevant focused classes from each implemented plan. Check TalkBack, keyboard, target size, contrast, system bars, animation-disabled progress and final-row/FAB clearance manually; record measured results.
- [ ] Record PASS/FAIL/NOT RUN per scenario, actual environment/build/date and exact artifact paths. Restore test device locale/theme/font/window settings and rerun failed checks after fixes. Sign off only with no required FAIL or NOT RUN.
- [ ] Review and commit only the resulting ledger/evidence documents when executing; do not call a future implementation or device check complete from this plan.

## Planned verification

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Expected: BUILD SUCCESSFUL, selected tests actually executed, zero failing tests/lint errors. Device/environment failures are NOT RUN with reasons; never treat SKIPPED as acceptance. Inspect every required manual/visual scenario in addition to automated results. These commands were not run by the documentation task.

## Handoff

Depends on: T31 and T32 (all four children). Report the evidence-backed acceptance decision and remaining blockers. This document does not begin app implementation.
