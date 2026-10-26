# F06 — Optional app lock and private previews specification

Date: 26 October 2026 · Priority: **P1** · Status: **DRAFT — NOT IMPLEMENTED / NOT VALIDATED**

[Roadmap](../../FEATURE_ROADMAP_2026-10-26.md) · [Spec](2026-10-26-roadmap-06-app-lock.md) · [Plan](../plans/2026-10-26-roadmap-06-app-lock.md) · [QA scenarios](../../feature-roadmap/06-app-lock-qa.md) · [Shared workflow](../../feature-roadmap/WORKFLOW.md)

## Intent and dependencies

Gate private app UI with system authentication while preserving drafts and intended destinations.

Dependencies: Task 02; integrate task 04 notification destinations if available.

Rank and scope come from [the feature proposal](../../FEATURE_IMPROVEMENT_PROPOSAL_2026-10-26.md). Specific new defaults in this document are proposed implementation choices, not claims of user approval. Read the existing implementation at execution time; this document does not certify its current runtime behavior.

## Architecture and alternatives

Recommended approach: Place a single lock coordinator above private Compose navigation. Use system biometric/device-credential authentication with API-appropriate fallback; enforce the same gate on activity, widget and notification entry paths. Device lock settings remain device-local.

Considered alternatives: retain the current behavior and document its limitations, or introduce an independent subsystem. Retaining behavior is acceptable only for an explicitly documented investigation/deferred outcome; otherwise it does not meet the intent above. An independent replacement would duplicate existing persistence/navigation and recovery paths; extend the current module contracts unless a specific requirement makes reuse impossible. The recommendation is proposed, not a recorded user selection.

## Existing source and handoff material

- [app/src/main/java/com/luma/lifeos/MainActivity.kt](../../../app/src/main/java/com/luma/lifeos/MainActivity.kt)
- [app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt](../../../app/src/main/java/com/luma/lifeos/ui/LifeOsApp.kt)
- [app/src/main/java/com/luma/lifeos/widget/WidgetLaunchViewModel.kt](../../../app/src/main/java/com/luma/lifeos/widget/WidgetLaunchViewModel.kt)
- [app/src/main/java/com/luma/lifeos/widget/WidgetActionReceiver.kt](../../../app/src/main/java/com/luma/lifeos/widget/WidgetActionReceiver.kt)
- [app/src/main/java/com/luma/lifeos/widget/WidgetActionWorker.kt](../../../app/src/main/java/com/luma/lifeos/widget/WidgetActionWorker.kt)
- [app/src/main/java/com/luma/lifeos/ui/privacy/PrivacySettingsRoute.kt](../../../app/src/main/java/com/luma/lifeos/ui/privacy/PrivacySettingsRoute.kt)
- [app/src/main/java/com/luma/lifeos/ui/privacy/AppDataReset.kt](../../../app/src/main/java/com/luma/lifeos/ui/privacy/AppDataReset.kt)
- [app/build.gradle.kts](../../../app/build.gradle.kts)
- [gradle/libs.versions.toml](../../../gradle/libs.versions.toml)

Platform reference checked 26 October 2026: [AndroidX BiometricManager](https://developer.android.com/reference/androidx/biometric/BiometricManager) documents authenticator combinations unsupported before API30, including DEVICE_CREDENTIAL alone and BIOMETRIC_STRONG combined with DEVICE_CREDENTIAL on API28–29. Use the documented API-appropriate credential fallback rather than assuming one modern authenticator mask works on every supported device.

## Contracts and ownership

Names below are proposed interfaces to create or adapt, not claims that these declarations already exist. Keep existing public callers working or migrate every caller explicitly in the owning change. Resolve exact current types while preserving these behaviors.

- Proposed `AppLockState` is Disabled, Locked, Authenticating or Unlocked; `AppLockCoordinator.onForeground(nowElapsed: Long)`, `onBackground(nowElapsed: Long)` and `onAuthenticationResult(success: Boolean)` define transitions.
- Use monotonic elapsed time for same-process timeout, and always lock on cold process start when enabled. Proposed timeout choices: immediately, 1 minute (default), 5 minutes.
- Queue typed destinations and opaque pending-command tokens rather than arbitrary route text. Successful unlock opens the destination once; pending mutation is revalidated for revision/date/expiry and consumed at most once, never replayed blindly.

## Requirements

### F06-R01 — Enable and recovery

Off by default; enabling requires successful system authentication and a configured secure device credential. If no credential exists, explain setup and do not enable. Credential removal leaves a gated recovery screen with Set device lock and confirmed local reset, never an automatic unlock bypass.

### F06-R02 — Lifecycle and entry points

Lock every cold start; background >= chosen timeout locks next foreground. Exclude the in-progress system authentication prompt from self-triggered background lock loops. Block private rendering before auth on normal launch, widget and notification navigation. While locked, widget/notification mutation actions request unlock instead of writing; after auth revalidate and consume the opaque command once. Cancelled/expired authentication leaves records unchanged.

### F06-R03 — Draft preservation

Keep unsaved drafts through auth cancellation, rotation and foreground locking using existing draft stores. Do not save authentication success across process death. Unlock resumes the intended route once without double saves or lost editor results.

### F06-R04 — Preview privacy

Hide private content in recent-app snapshots whenever lock is enabled. A separate screenshot-protection switch applies secure-window protection to unlocked private screens too. Both behaviors are accurately described; widget cached views cannot be remotely guaranteed erased.

### F06-R05 — External surfaces and restore

While locked, new notifications are generic. Existing detailed widgets retain their explicit device-local preference unless the user chooses to hide them; explain their independent visibility and offer a hide-details action. Backup/restore never transfers authentication state, biometric material or timeout settings; reset disables and removes local lock preferences.

## Two-phase delivery contract

**Phase 1 — Implementation:** implement the source/configuration/resources/documentation described in the plan, review the source and diff, and record the handoff. **No Gradle command, sync, dependency resolution, build, schema generation, lint, automated test execution, emulator launch or device test. Test authoring is deferred to QA.** Source checks do not prove compilation or correctness.

**Phase 2 — QA:** author tests/fixtures, generate and inspect schemas where relevant, run Gradle/build/lint/automated suites, execute native/manual cases, correct discovered defects and record evidence. Begin only after this task’s implementation source handoff; all initial outcomes are NOT RUN. Follow the [shared workflow](../../feature-roadmap/WORKFLOW.md) for isolation, matrix, commands, evidence and completion gates.

## Scope boundaries

No app-specific PIN, custom biometric database, at-rest file encryption or remote wipe.

## Acceptance and traceability

All five requirements must map to implementation steps and QA outcomes. [The QA document](../../feature-roadmap/06-app-lock-qa.md) provides executable scenarios with fixtures/actions/oracles; [the plan](../plans/2026-10-26-roadmap-06-app-lock.md) owns concrete files and implementation/QA checklists. Both phases start pending. A source-complete task is not feature-complete. For assessment/deferred branches, report the delivered investigation separately from feature/runtime acceptance.
