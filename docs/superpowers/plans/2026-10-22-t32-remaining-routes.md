# T32 — Remaining routes rollout coordinator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan does not request subagent delegation.

**Goal:** Replace the old catch-all remaining-screen task with four independently reviewable screen-family tasks and a route coverage ledger.

**Architecture:** Existing repositories/view models retain data ownership and LifeOsApp retains navigation. Shared T30 roles/components provide presentation; the spec defines any optional new interface. Keep current product behavior.

**Tech Stack:** Kotlin, Compose Material 3, Navigation Compose, JUnit/Compose instrumentation, Gradle and JDK 17.

**Spec:** [Corresponding specification](../specs/2026-10-22-t32-remaining-routes-design.md).

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

Unregistered conditional routes; shared-card callback drift; unsupported mockup controls; leftover palette overrides; missing family evidence.

## Files and interfaces

- Create: `docs/luma-redesign/route-coverage.md` during implementation; one row per registered route and significant sheet/dialog.
- Inspect: `app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt` and `ui/navigation/AppDestinations.kt`. No application changes in this coordination task.

Consumes: paired child specifications, current route registrations and actual implementation evidence. Produces: a complete coverage ledger for T33.

### Task 1: Coverage inventory — complete (static inventory)

- [x] Enumerate registered routes and dialogs into the new route ledger and assign each to T32-A/B/C/D. Include shared card consumers and conditional integrations/widgets.
- [x] For each route include route ID, owning task, direct-reference/extrapolated classification, providers required, light/dark evidence paths, locale/width/font-scale result, blocker and status. Include every sheet/dialog and conditional integration/widget configuration route.
- [x] Check owners against T32-A/B/C/D and assign shared EventCard changes to T32-A; mark launcher RemoteViews out of scope with links to their existing acceptance docs.

Inventory result: [route-coverage.md](../../luma-redesign/route-coverage.md). The ownership ruling for Today is recorded in the ledger: T31 owns the route, while T32-A owns its shared EventCard consumer check.

### Task 2: Cross-family integration

- [x] Reconcile the route ledger against current route declarations and T32-A/B/C/D source evidence; no conflicting ownership or shared-component treatment was found. Forward the ledger and its unresolved provider-absent route gaps to T33. Rendered/runtime acceptance remains open; see [coordinator evidence](../../luma-redesign/evidence/2026-10-22/t32-coordinator-review.md).
- [ ] Verify every child behavior and rendered acceptance scenario against runtime evidence. Child source records exist, but the required Gradle tests and emulator checks were not run at the user's request; no child acceptance is signed off by this source review.
- [x] Check the complete route set against current registrations again and hand off uncovered/blocked rows explicitly to T33.
- [ ] Commit the reviewed ledger/evidence documents. Suggested message for this documentation update: `docs(ui): reconcile T32 route coverage and status`. No Gradle tests or emulator checks were run, per the user's instruction.

## Planned verification

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Expected: BUILD SUCCESSFUL, selected tests actually executed, zero failing tests/lint errors. Device/environment failures are NOT RUN with reasons; never treat SKIPPED as acceptance. T32 records child evidence and may consume already-current checks rather than repeat unchanged tests. These commands were not run by the documentation task.

## Handoff

Depends on: T30; complete T32-A through T32-D before T33. Hand the reviewed ledger to T33 after all four children pass. This document does not begin app implementation.
