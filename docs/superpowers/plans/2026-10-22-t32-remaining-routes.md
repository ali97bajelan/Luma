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

### Task 1: Coverage inventory

- [ ] Enumerate registered routes and dialogs into the new route ledger and assign each to T32-A/B/C/D. Include shared card consumers and conditional integrations/widgets.
- [ ] For each route include route ID, owning task, direct-reference/extrapolated classification, providers required, light/dark evidence paths, locale/width/font-scale result, blocker and status. Include every sheet/dialog and conditional integration/widget configuration route.
- [ ] Check owners against T32-A/B/C/D and assign shared EventCard changes to T32-A; mark launcher RemoteViews out of scope with links to their existing acceptance docs.

### Task 2: Cross-family integration

- [ ] After child tasks, verify each ledger entry against implementation and evidence; resolve incompatible shared-component changes and forward the completed ledger to T33.
- [ ] Verify every child acceptance scenario against its evidence; resolve duplicate ownership or inconsistent component treatments. Keep task status pending/validation pending until checks actually pass.
- [ ] Check the complete route set against current registrations again and hand off uncovered/blocked rows explicitly to T33.
- [ ] Review and commit only the resulting ledger/evidence documents when executing; do not call a future implementation or device check complete from this plan.

## Planned verification

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Expected: BUILD SUCCESSFUL, selected tests actually executed, zero failing tests/lint errors. Device/environment failures are NOT RUN with reasons; never treat SKIPPED as acceptance. T32 records child evidence and may consume already-current checks rather than repeat unchanged tests. These commands were not run by the documentation task.

## Handoff

Depends on: T30; complete T32-A through T32-D before T33. Hand the reviewed ledger to T33 after all four children pass. This document does not begin app implementation.
