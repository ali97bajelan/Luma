# F14 — Build and dependency modernization assessment specification

Date: 26 October 2026 · Priority: **P3** · Status: **DRAFT — NOT IMPLEMENTED / NOT VALIDATED**

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](2026-10-26-roadmap-14-build-modernization.md) · [Plan](../plans/2026-10-26-roadmap-14-build-modernization.md) · [QA scenarios](../../feature-roadmap/14-build-modernization-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Intent and dependencies

Select and validate only justified compatible build/dependency changes with reproducible release evidence.

Dependencies: Task 02 provides current reproducible baseline; can be promoted for a verified release/security blocker.

Rank and scope come from [the feature proposal](../../FEATURE_IMPROVEMENT_PROPOSAL_2026-10-26.md). Specific new defaults in this document are proposed implementation choices, not claims of user approval. Read the existing implementation at execution time; this document does not certify its current runtime behavior.

## Architecture and alternatives

Recommended approach: Create a baseline and staged compatibility decision before editing toolchain versions. Separate SDK/plugin/Kotlin/KSP/Compose/Room/Health changes into reviewable groups and preserve existing module boundaries and minimum supported device policy.

Considered alternatives: retain the current behavior and document its limitations, or introduce an independent subsystem. Retaining behavior is acceptable only for an explicitly documented investigation/deferred outcome; otherwise it does not meet the intent above. An independent replacement would duplicate existing persistence/navigation and recovery paths; extend the current module contracts unless a specific requirement makes reuse impossible. The recommendation is proposed, not a recorded user selection.

## Existing source and handoff material

- [app/build.gradle.kts](../../../app/build.gradle.kts)
- [build.gradle.kts](../../../build.gradle.kts)
- [settings.gradle.kts](../../../settings.gradle.kts)
- [gradle/libs.versions.toml](../../../gradle/libs.versions.toml)
- [gradle/wrapper/gradle-wrapper.properties](../../../gradle/wrapper/gradle-wrapper.properties)
- [core/data/build.gradle.kts](../../../core/data/build.gradle.kts)
- [integration/health/build.gradle.kts](../../../integration/health/build.gradle.kts)
- [.github/workflows/android.yml](../../../.github/workflows/android.yml)
- [app/proguard-rules.pro](../../../app/proguard-rules.pro)



## Contracts and ownership

Names below are proposed interfaces to create or adapt, not claims that these declarations already exist. Keep existing public callers working or migrate every caller explicitly in the owning change. Resolve exact current types while preserving these behaviors.

- Assessment table rows: component/current value/candidate pinned value/official compatibility source and access date/reason/affected modules/rollback boundary/QA cases.
- Do not invent current latest versions in this package. The implementing engineer chooses pinned candidates from then-current official release notes in phase 1; actual resolution/build is exclusively phase 2.
- CI artifact identity includes Git/patch ID, dependency graph, JDK/SDK/Gradle versions, variant, APK hash, lint/test reports and R8 warnings.

## Requirements

### F14-R01 — Justified scope

Every selected change maps to a verified compatibility defect, security advisory, supported-device benefit or distribution requirement. Assessment may recommend retaining versions; a no-change outcome needs evidence and still receives QA baseline validation.

### F14-R02 — Compatibility matrix

Record AGP/Gradle/JDK, Kotlin/KSP/Compose compiler, Room/schema tooling and Health Connect SDK constraints together using official documentation. Preserve minSdk 28 unless explicitly approved separately; no claim of store eligibility from targetSdk alone.

### F14-R03 — Staged changes

Freeze a reversible baseline then change one coherent compatible group at a time. No broad namespace/refactor/navigation change mixed into version upgrades. Avoid destructive migrations and blanket lint/R8 warning suppression.

### F14-R04 — Reproducibility and artifacts

CI explicitly installs selected compile platform/build tools and records exact versions. Collect unit/instrumentation/lint/release artifacts even on failure where possible. Compare signed-equivalent minified variants with matching fixtures; do not expose signing secrets.

### F14-R05 — Release evidence

Validate startup/capture, multilingual layouts, migrations/restores, widgets, optional imports and minified reflection paths. Report APK size, cold-start/frame/search/review changes with exact device/run setup and resolved/unresolved warnings; unchanged performance is a valid result, unmeasured claims are not.

## Two-phase delivery contract

**Phase 1 — Implementation:** implement the source/configuration/resources/documentation described in the plan, review the source and diff, and record the handoff. **No Gradle command, sync, dependency resolution, build, schema generation, lint, automated test execution, emulator launch or device test. Test authoring is deferred to QA.** Source checks do not prove compilation or correctness.

**Phase 2 — QA:** author tests/fixtures, generate and inspect schemas where relevant, run Gradle/build/lint/automated suites, execute native/manual cases, correct discovered defects and record evidence. Begin only after this task’s implementation source handoff; all initial outcomes are NOT RUN. Follow the [shared workflow](../../feature-roadmap/WORKFLOW.md) for isolation, matrix, commands, evidence and completion gates.

## Scope boundaries

No automatic latest-version sweep, AGP9 migration assumption, minSdk increase, signing-secret change or unrelated app refactor.

## Acceptance and traceability

All five requirements must map to implementation steps and QA outcomes. [The QA document](../../feature-roadmap/14-build-modernization-qa.md) provides executable scenarios with fixtures/actions/oracles; [the plan](../plans/2026-10-26-roadmap-14-build-modernization.md) owns concrete files and implementation/QA checklists. Both phases start pending. A source-complete task is not feature-complete. For assessment/deferred branches, report the delivered investigation separately from feature/runtime acceptance.
