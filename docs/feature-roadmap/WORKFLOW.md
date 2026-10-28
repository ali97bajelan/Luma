# Feature roadmap — shared implementation and QA workflow

Date: 26 October 2026. Status: documentation only; no feature implementation, Gradle invocation, test authoring/execution, emulator launch or device action is performed by creating these documents.

## Exactly two phases per task

1. **Implementation:** read existing source and predecessor specs, settle and document source-design choices, write production/configuration/resource/documentation changes, and review source/diffs. Do not run Gradle at all: no sync, dependency resolution, schema export, compilation, assembly, lint, unit tests or connected tests. Do not start/control an emulator or device. Test authoring and execution are deferred to QA. XML parsing, link/file checks and `git diff --check` are allowed source-only checks; they do not prove compilation or behavior.
2. **QA:** begin only after that task's implementation handoff is recorded. Author tests/fixtures, generate Room schema JSON where necessary, run Gradle/build/lint/tests, use isolated emulators/devices, perform manual scenarios, fix discovered defects and rerun affected checks. Defect corrections are part of QA, not a third named phase. A substantial new requirement revises spec/implementation scope and reopens the relevant handoff before QA resumes.

The boundary applies to **each task**: finish task 01 implementation, then task 01 QA; dependencies may then proceed. It does not require all implementations before any QA. Phase 1 status is `SOURCE COMPLETE — QA PENDING`, never `verified`, `passing`, `release ready` or task complete. Do not merge/release schema-changing work until phase 2 exports, inspects and validates the schema and migration chain.

## Baseline and authority

The proposal ranks the original 14 tasks by importance; F15 was added on 28 October for requested memory/mood sharing documentation. Neither list authorizes implementing all tasks now. This workflow applies to every task listed in the roadmap, including later additions. Specs/plans are draft execution documents. The user's explicit two-phase instruction overrides older plans that mix implementation and tests. Product defaults introduced here are **proposed defaults**, not prior user decisions. A future implementation request should identify selected tasks and settle any material default changes; generating this package requires no further confirmation.

Preserve existing user edits, personal installation/data and release artifacts. Inspect `git status` and the execution baseline before edits. Do not allocate fixed next Room/archive version numbers in parallel documents: inspect current versions and assign the next unused version during the owning task, with explicit legacy adapters. No destructive migration, account, network permission, telemetry, mandatory AI or automatic upload. Keep core:domain free of Android/Room dependencies. Preserve minimum API 28, compile/target 35 and JDK17 unless task14 has an explicit justified compatibility change; minimum API increase needs a separate decision.

Current Calendar remains scheduled tasks plus saved memories only; main bar Today → Timeline → Calendar → Review → More; Habits under More. Existing draft recovery, revision-safe writes, photo ownership, optional integration behavior, EN/FR/FA, RTL and light/dark remain requirements. New preferences/runtime/auth state stay device-local unless a spec explicitly makes user content portable. Never put secrets in logs, artifacts or saved navigation state.

## Phase 1 exit checklist

- All implementation steps checked and source changes reviewed against requirement IDs.
- Existing/new files and interfaces reconciled with actual execution baseline; removed/renamed files documented.
- Schema/archive changes have source migrations/adapters and stated backward behavior; schema generation explicitly pending QA.
- EN/FR/FA copy and empty/loading/error/retry/disabled/draft behavior accounted for where relevant.
- Handoff contains Git SHA, dirty patch hash, touched paths, static checks, known uncertainty and QA scenario/test mapping.
- No Gradle/emulator/device/test result claimed. Every QA scenario begins `NOT RUN`.

## Phase 2 environment, evidence and result policy

Use only isolated application/test package IDs, disposable databases/photos and dedicated AVD/device fixtures. Verify isolation before connected tests because runners may reinstall/uninstall their target. Never reset, restore over, grant new integration access to or install over the user's personal app for validation. Inspect actual target identifiers; a debug build type by itself does not guarantee a distinct package.

Record actual date/time, source SHA and dirty patch hash, package/variant/version/APK SHA, command exit code, JDK/Gradle/SDK/dependency versions, device/API, fixture seed/hash, locale, zone, 12/24-hour mode, theme, window dp, orientation and font scale. Store evidence at `docs/feature-roadmap/evidence/<actual-run-date>/Fnn/`; create this directory during QA, not as fabricated evidence now. Use test logs, JUnit XML, screenshots, accessibility notes and raw performance samples as appropriate. Synthetic passwords and health fixtures only.

Results are `NOT RUN`, `PASS`, `FAIL`, `BLOCKED`, or `N/A` with a recorded scope rationale. BLOCKED needs the exact prerequisite failure and next step; elapsed time is not approval or a pass. Subset/host/compile success never certifies complete native or manual cases. Fix → rerun owning cases plus affected integration regressions; identify which earlier evidence the change invalidated. Critical data/privacy/mutation defects block acceptance. Any release waiver needs explicit owner, reason, expiry and remaining risk; do not invent waivers. All required cases/matrix cells must pass for a feature-complete claim.

## Required UI/platform matrix

For affected app screens, execute EN/FR/FA × System-light/dark × (320dp portrait/font2.0, 411dp portrait/font1.0, 411dp landscape/font2.0, >=840dp/font1.0). Verify actual Persian RTL, IME, system/gesture insets and final action reachability in each relevant cell. Include one system-theme transition. If a cell is technically unsupported, record N/A with source-supported reason; do not silently remove it.

Exercise minimum supported API28 and API36 on affected core flows; add API33+ notification permission and API28/API30+ authentication paths where applicable. Native Health Connect cases run on supported provider devices and unsupported-device fallback is separately verified. Run TalkBack human observation and hardware keyboard on each changed interaction family; semantics tests alone do not certify spoken behavior. Widget work requires an actual launcher; synthetic renderer passes alone are insufficient.

For persistence changes: old populated database upgrade, exact retained data/photo hashes, current archive round-trip, every supported legacy archive adapter, malformed input, replacement invalidation and concurrent stale revision. For UI: loading/empty/error/retry, offline/permission-denied, recreation/process restart, rapid navigation and stale responses. For security: direct entry paths, cancelled auth/permission, logs/public previews and replayed commands.

## QA-only Gradle command catalogue

Commands below are templates for the QA engineer after source handoff and isolation setup. Do not execute these during implementation or assume the current environment can run them. Capture actual output/exit codes; dependency/network/SDK failure is BLOCKED. Use the repository wrapper rather than silently substituting a toolchain. Run focused suites first, then required affected regression gates; avoid redundant full reruns without changes.

```sh
# Host/domain suite (works without Android SDK in domainOnly mode)
bash gradlew -PdomainOnly=true :core:domain:test
# Affected host suites; select actual owning modules from each task plan
bash gradlew :app:testDebugUnitTest :core:data:testDebugUnitTest :integration:usage:testDebugUnitTest :integration:health:testDebugUnitTest
# Compilation, schema/KSP generation, lint, app and instrumentation assembly
bash gradlew :app:assembleDebug :app:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest :app:lintDebug :core:data:lintDebug :integration:usage:lintDebug :integration:health:lintDebug
# ONLY after the QA target is proven isolated; adapt variant/test runner if needed
bash gradlew :app:connectedDebugAndroidTest :core:data:connectedDebugAndroidTest
# Minified release artifact; arrange an isolated signed QA release variant for install
bash gradlew :app:assembleRelease
```

Expected automated gate: exit 0, required tests executed with zero failures/errors, skips individually justified, lint errors/fatals zero with each new warning classified. Successful assembly is not successful instrumentation. Export generated Room schemas in QA, inspect against source migration and commit as part of the validated task; never overwrite old schema JSON to hide drift.

## Performance procedure

Use named representative hardware and deterministic synthetic datasets. Record baseline and candidate source/build identity, power/thermal conditions, variant, cold first-open separately, 5 warmups then 30 measured repetitions per query/period and raw samples. Compute nearest-rank p95 as sorted sample at ceil(0.95*N). Include repository query/count/mapping and exclude only documented debounce/UI time. Frame probes use actual frame deadlines at the device refresh rate; navigation latency is not scroll smoothness. Emulator figures must be labelled and cannot alone establish representative phone acceptance.

## Acceptance record template

Copy into the actual execution record during QA: candidate identity; implementation handoff; environment/isolation; requirement→case→test mapping; scenario matrix with actual outcomes/artifacts; automated command results; manual/runtime results; defects/fixes/retests; performance raw data; remaining blockers/waivers; final acceptance decision and exact validated scope. Do not prefill pass counts.
