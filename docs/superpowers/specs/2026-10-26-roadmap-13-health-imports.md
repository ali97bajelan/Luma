# F13 — Optional sleep and workout imports specification

Date: 26 October 2026 · Priority: **P3** · Status: **DRAFT — NOT IMPLEMENTED / NOT VALIDATED**

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](2026-10-26-roadmap-13-health-imports.md) · [Plan](../plans/2026-10-26-roadmap-13-health-imports.md) · [QA scenarios](../../feature-roadmap/13-health-imports-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Intent and dependencies

Investigate and deliver separately opt-in sleep/workout summaries only where native provider support and user value are established.

Dependencies: Task 02 real-step-provider acceptance first; task 14 compatibility work only if needed for selected APIs.

Rank and scope come from [the feature proposal](../../FEATURE_IMPROVEMENT_PROPOSAL_2026-10-26.md). Specific new defaults in this document are proposed implementation choices, not claims of user approval. Read the existing implementation at execution time; this document does not certify its current runtime behavior.

## Architecture and alternatives

Recommended approach: Preserve the steps adapter and permission boundary. Extend health contracts by record kind with independent access/enable state; source-normalize sleep/workout summaries before idempotent persistence. The capability investigation is a source-design deliverable inside implementation, not a third phase.

Considered alternatives: retain the current behavior and document its limitations, or introduce an independent subsystem. Retaining behavior is acceptable only for an explicitly documented investigation/deferred outcome; otherwise it does not meet the intent above. An independent replacement would duplicate existing persistence/navigation and recovery paths; extend the current module contracts unless a specific requirement makes reuse impossible. The recommendation is proposed, not a recorded user selection.

## Existing source and handoff material

- [integration/health/src/main/kotlin/com/luma/lifeos/integration/health/HealthConnectPlatform.kt](../../../integration/health/src/main/kotlin/com/luma/lifeos/integration/health/HealthConnectPlatform.kt)
- [integration/health/src/main/kotlin/com/luma/lifeos/integration/health/AndroidHealthSource.kt](../../../integration/health/src/main/kotlin/com/luma/lifeos/integration/health/AndroidHealthSource.kt)
- [core/domain/src/main/kotlin/com/luma/lifeos/domain/health](../../../core/domain/src/main/kotlin/com/luma/lifeos/domain/health)
- [core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomHealthRepository.kt](../../../core/data/src/main/kotlin/com/luma/lifeos/data/repository/RoomHealthRepository.kt)
- [app/src/main/java/com/luma/lifeos/ui/health/HealthSettingsViewModel.kt](../../../app/src/main/java/com/luma/lifeos/ui/health/HealthSettingsViewModel.kt)
- [app/src/main/java/com/luma/lifeos/ui/health/HealthSettingsCard.kt](../../../app/src/main/java/com/luma/lifeos/ui/health/HealthSettingsCard.kt)
- [integration/health/src/main/AndroidManifest.xml](../../../integration/health/src/main/AndroidManifest.xml)
- [gradle/libs.versions.toml](../../../gradle/libs.versions.toml)

Platform reference checked 26 October 2026: [Health Connect read-data guidance](https://developer.android.com/health-and-fitness/health-connect/read-data) distinguishes foreground/background reads and recommends aggregate reads for cumulative step data to avoid source double counting. Session-level origin/overlap behavior in this spec is a proposed conservative presentation policy; confirm native API support in I01.

## Contracts and ownership

Names below are proposed interfaces to create or adapt, not claims that these declarations already exist. Keep existing public callers working or migrate every caller explicitly in the owning change. Resolve exact current types while preserving these behaviors.

- Proposed `HealthImportKind` STEPS, SLEEP, WORKOUT; state distinguishes unavailable/update-required/access-required/enabled/error independently by kind.
- Proposed `ImportedHealthSession(kind, providerRecordId, dataOrigin, start: Instant, end: Instant, updatedAt: Instant?, attributes)` uses kind+origin+provider ID identity; end must be >=start.
- Proposed `importSessions(kind, window, revision)` atomically publishes records and checkpoint only while enable/access revision remains current.

## Requirements

### F13-R01 — Capability gate

Document demand from concrete workflows, provider/API support, required read scopes, SDK compatibility and available fixture tooling before coding integrations. If unsupported or demand is unconfirmed, deliver a documented deferred recommendation and keep rollout disabled; do not claim imported functionality shipped.

### F13-R02 — Granular opt-in

Steps, sleep and workouts have independent toggles/read permissions, all new kinds off by default. Denial of one kind does not disable another or core capture. Revoke/disable stops reads and retains labelled saved history. Foreground import only in v1.

### F13-R03 — Identity and corrections

Refresh last 7 calendar days in a saved import zone. Upsert by provider identity; reconcile corrections/deletions only for successfully read complete windows. Provider failure/permission loss never means a successful empty window. Commit checkpoint atomically and reject stale in-flight revisions.

### F13-R04 — Time and overlap

Retain original start/end instants and origin; do not add durations across overlapping providers as if distinct activity. V1 presents source-labelled sessions separately and aggregate duration only for an explicitly selected single source, unioning intervals. Sleep belongs to its end-date in saved import zone; workout belongs to start-date. State boundary truncation and partial coverage.

### F13-R05 — Presentation and restore

Typed read-only details and Review show recorded session/time/unit/source, not diagnosis/calories inferred from duration. Calendar remains tasks/memories only. Persist/archive source summaries and import zone with migration; restored imports stay off and native access must be re-granted as needed.

## Two-phase delivery contract

**Phase 1 — Implementation:** implement the source/configuration/resources/documentation described in the plan, review the source and diff, and record the handoff. **No Gradle command, sync, dependency resolution, build, schema generation, lint, automated test execution, emulator launch or device test. Test authoring is deferred to QA.** Source checks do not prove compilation or correctness.

**Phase 2 — QA:** author tests/fixtures, generate and inspect schemas where relevant, run Gradle/build/lint/automated suites, execute native/manual cases, correct discovered defects and record evidence. Begin only after this task’s implementation source handoff; all initial outcomes are NOT RUN. Follow the [shared workflow](../../feature-roadmap/WORKFLOW.md) for isolation, matrix, commands, evidence and completion gates.

## Scope boundaries

No background health reads, write scopes, cross-provider guessed totals, diagnosis or automatic medical advice. Deferred capability outcome closes investigation only, not feature QA.

## Acceptance and traceability

All five requirements must map to implementation steps and QA outcomes. [The QA document](../../feature-roadmap/13-health-imports-qa.md) provides executable scenarios with fixtures/actions/oracles; [the plan](../plans/2026-10-26-roadmap-13-health-imports.md) owns concrete files and implementation/QA checklists. Both phases start pending. A source-complete task is not feature-complete. For assessment/deferred branches, report the delivered investigation separately from feature/runtime acceptance.
