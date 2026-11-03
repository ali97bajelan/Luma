# App Lock Credential Types Implementation Plan

**Status:** SOURCE COMPLETE — QA PENDING. Implementation and allowed source checks are recorded in [the handoff](../../feature-roadmap/app-lock-credential-types-implementation-handoff.md). Runtime acceptance remains deferred.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking. This is an implementation-only source plan; QA is deferred.

**Goal:** Let users choose a digit-only PIN, character password, or 3×3 pattern for every Luma app-lock authentication flow.

**Architecture:** Add explicit credential type metadata to the existing authenticated local store, and extend its serialized credential service. Use one input-policy helper and shared Compose inputs across setup, unlock, changes, recovery, and sensitive operations. Preserve the existing verifier derivation, recovery ceremony, retry counter, and operation proofs.

**Tech Stack:** Existing Kotlin, Jetpack Compose, Android AtomicFile/Keystore, coroutines, EN/FR/FA Android string resources.

**Spec:** [App lock credential types](../specs/2026-11-02-app-lock-credential-types-design.md).

## Global Constraints

- Minimum API 28; compile/target SDK 35; Java 17. Reuse Kotlin/Compose and existing platform crypto; add no dependency, permission, network, or telemetry.
- Keep `core:domain` Android-free. Coverage, relock timing, restricted capture, Settings placement, and proof authority are outside this change.
- Use localized EN/FR/FA string resources and shared Luma controls. Replace password-only instructions/errors with credential-neutral or method-specific text where used by multiple methods. Keep recovery-code input/copy distinct.
- Do not author/edit tests or invoke Gradle, sync, builds, lint, tests, emulators, or devices during implementation. Source/diff review, XML parsing, link checks, and `git diff --check` are allowed.
- Source completion must be labeled `SOURCE COMPLETE — QA PENDING`; planning completion does not claim feature completion.
- Implement ACT-01–ACT-11 exactly as specified. Proposed defaults: Password for new setup; PIN 4–32 canonical digits; Password 4–32 exact Unicode code points; Pattern 4–9 unique nodes in a physical, RTL-stable 3×3 grid.
- Preserve unrelated workspace changes and release artifacts. Do not stage or commit unrelated files. This document does not authorize product implementation by itself.

## Review Focus

- A v1 numeric password must stay PASSWORD with identical salt/verifier and retry metadata; review Task 1's branch ordering and round-trip layout.
- A credential change while a prompt is open must cancel old input and cannot authenticate a new generation or a different pending operation; review Tasks 2, 4, and 5.
- Fast pattern movement, midpoint insertion, node revisits, and RTL must produce one consistent sequence for gestures, keyboard, TalkBack, and service validation; review Tasks 1 and 3.
- A cancelled or failed recovery must preserve the old credential/code pair and clear its staged replacement type/secret; review Task 5.
- Compact or large-text layouts and assistive input must reach Clear, Done drawing, Continue, and recovery controls without exposing the selected secret; review Tasks 3–5.

Review checks below are source checks. The spec's later-QA acceptance contract remains pending; this plan intentionally contains no test-authoring or runtime-validation tasks.

## File structure and dependency order

New lock files live under `app/src/main/java/com/luma/lifeos/lock/`: `LockCredentialType.kt` owns type and descriptor/result models; `LockCredentialInputPolicy.kt` owns canonicalization/validation; `LockPatternRules.kt` owns pure node ordering and encoding. Existing `LocalLockStore.kt` owns v1/v2 format compatibility; `DefaultLockCredentialService.kt` remains the sole credential authority.

New shared UI files live under `app/src/main/java/com/luma/lifeos/ui/privacy/`: `LockCredentialInput.kt` owns method selection, entry, confirmation, and lifecycle-safe transient state; `LockPatternInput.kt` owns gesture and accessible grid interaction. Route composables retain workflow orchestration. Do not move proof consumption into input components.

Order: Task 1 → Task 2 → Task 3 → Task 4 → Task 5 → Task 6. Dependencies are source handoffs, never predecessor QA results.

---

### Task 1: Define credential formats and compatible local persistence

**Files:** Create `lock/LockCredentialType.kt`, `lock/LockCredentialInputPolicy.kt`, `lock/LockPatternRules.kt`; modify `lock/LocalLockStore.kt`, all under the app package path above.

**Interfaces:**
- Produce `LockCredentialType { PIN, PASSWORD, PATTERN }`, `LockCredentialDescriptor(type: LockCredentialType, generation: Long)`, and sealed `CredentialDescriptorResult.Ready(descriptor)`, `.Disabled`, `.Unavailable`.
- Produce `LockCredentialInputPolicy.canonicalize(type: LockCredentialType, input: CharArray): CharArray?`; caller owns its returned array. It neither retains nor modifies input.
- Produce `LockPatternRules.appendNode(current: List<Int>, next: Int): List<Int>` and `encode(nodes: List<Int>): CharArray?`.
- Add required `type: LockCredentialType` to `PersistedCredential`. Preserve its four existing salt/verifier property names for minimal adapter changes.

- [x] Add the type/descriptor models without importing Android or adding them to domain lock policy.
- [x] Implement canonicalization: PIN maps only ASCII, Persian, and Arabic-Indic digit ranges to ASCII; retain leading zeros and require 4–32 digits. Password counts well-formed Unicode code points, requires 4–32, preserves exact input, and rejects isolated surrogates. Pattern requires canonical ASCII node IDs, unique 4–9 nodes, and exact replay through appendNode. Return independent arrays; clear invalid intermediate buffers.
- [x] Implement appendNode/encode with the spec's midpoint rule, repeat handling, fixed physical IDs, and forward/reverse direction. Out-of-range nodes are rejected; revisiting a selected valid node leaves the current sequence unchanged. Encode validates rather than silently fixing a noncanonical supplied sequence.
- [x] Emit payload version 2 with credential type immediately before passwordSalt. Branch decoding by version 1/2; synthesize PASSWORD for v1 without rehashing or writing on read. Reject unknown types/versions, bad lengths, and trailing bytes through the existing corrupt-store path. Preserve no-backup path, MAGIC/envelope, keyed authentication, AtomicFile recovery, enum/policy mappings, retry counters, and disabled-state representation.
- [x] Review ACT-02/03/04/08 against the source. Trace examples `0012`, Persian `۰۰۱۲`, exact spaced passwords, isolated surrogate, `0→2`, `0→8`, `0→5`, reverse traversal, revisited midpoint, duplicate serialized nodes, numeric v1 password, disabled v1 state, malformed v2 type, and retained retry fields. Record these as source reasoning, not executed test outcomes.

**Source handoff:** Canonicalization contract and v1/v2 byte-layout decisions, touched paths, and remaining runtime uncertainty. Do not claim legacy upgrade verification.

### Task 2: Extend the credential authority and generation-bound metadata

**Files:** Modify `lock/LockCredentialService.kt`, `lock/DefaultLockCredentialService.kt`, `lock/LockSensitiveOperationGuard.kt`, and `ui/privacy/AppLockViewModel.kt`.

**Interfaces:** Consume Task 1's types/policy; produce the five service signatures specified in the design: `credentialDescriptor()`, typed `enable`, generation-bound `verify`, typed `changePassword`, and typed `recover`. Existing preparation/confirmation/policy/disable methods and CredentialResult variants retain their contracts. Expose `LockSensitiveOperationGuard.credentialDescriptor(pending)`, read-only `val lockSnapshot: StateFlow<LockSnapshot>` delegating to coordinator.snapshot, and corresponding ViewModel method delegation.

- [x] Add service signatures exactly as declared in the spec. Keep `AuthPurpose.CHANGE_PASSWORD` as the existing purpose for credential replacement; do not mint a broader settings authority. Delegate typed methods through AppLockViewModel.
- [x] Implement descriptor reads through authenticated IO under PROCESS_MUTEX. Return Disabled only for a genuinely disabled/absent state; exceptions produce Unavailable. No storage rewrite or access grant is performed.
- [x] Replace password-only validation with canonicalization in enable, verify, changePassword, and recover. In verification use only the stored type, check expectedGeneration before cooldown/derivation/failure mutation, then preserve the existing cooldown-before-validation ordering. Hash canonical arrays using existing AndroidLockCrypto methods. Keep the existing crypto/key domain/iteration constants.
- [x] Build and commit type/verifier/recovery/policy metadata atomically through the existing write path. Preserve fresh proof consumption, generation increments, recovery acknowledgment, grant invalidation, and callback ordering. Ordinary credential changes retain the recovery verifier; recovery replaces both pairs.
- [x] Clear original and canonical secret arrays in finally, including cancellation and initial-key rollback branches. Preserve Default dispatcher derivation and IO persistence. Remove redundant private password-length logic after all consumers use the shared helper.
- [x] Add guard metadata access that rechecks pending-operation currency before and after descriptor loading, and compares descriptor.generation with pending.policyGeneration. Pass pending.policyGeneration into guard.verify's service call. No metadata result may bind/consume a proof.
- [x] Review ACT-05/06/07/09/10: stale generations return Cancelled without failure-accounting changes; unavailable stores never mean disabled; cooldown prevents derivation; incorrect valid input increments once; failed writes preserve the old record; all mutation branches retain existing one-operation proof rules.

**Source handoff:** Final signatures, ownership/clearing rules, proof and generation ordering, failure behavior, plus the downstream production-caller inventory. Existing test doubles/adapters require later QA updates; no tests are edited now.

### Task 3: Build shared credential entry and pattern interaction

**Files:** Create `ui/privacy/LockCredentialInput.kt`, `ui/privacy/LockPatternInput.kt`; modify `app/src/main/res/values/strings.xml`, `values-fr/strings.xml`, and `values-fa/strings.xml`.

**Interfaces:**
- Produce `LockCredentialEditorState(type: LockCredentialType)` with transient entry/confirmation state, `clear()`, `isComplete: Boolean`, and `takeSecret(): CharArray?`. takeSecret clears editor content and returns an owned canonical array only when complete; no state enters SavedStateHandle or rememberSaveable.
- Produce `rememberLockCredentialEditorState(type: LockCredentialType): LockCredentialEditorState`, resetting on type changes and clearing on ON_STOP/disposal.
- Produce `LockCredentialTypeSelector(selected: LockCredentialType, enabled: Boolean, onSelected: (LockCredentialType) -> Unit)` and `LockCredentialInput(state: LockCredentialEditorState, enabled: Boolean, label: String, modifier: Modifier = Modifier)`.
- Produce `LockCredentialEnrollment(state: LockCredentialEditorState, enabled: Boolean, modifier: Modifier = Modifier)`, which renders first/confirmation entry and only makes state.isComplete true after canonical equality. Separate enrollment state from the editor used for current authentication.
- Produce internal `LockPatternInput(enabled: Boolean, onCompleted: (List<Int>) -> Unit, modifier: Modifier = Modifier)`; pointer and assistive interaction share Task 1's ordering helper. Parent editor owns the pending sequence after completion.

- [x] Implement process-local text/pattern state and ownership transfer. Clear reveal flags, errors, confirmation, traces, active pointer, completion flags, node-list references, and owned arrays together on clear/type change. Clear visible input when transferring a secret, retaining only an explicitly owned pending setup/recovery array until final commit/cancel/stop/failure. Keep incomplete text available for correction; block continuation for invalid characters or lengths.
- [x] Implement PIN/Password fields with NumberPassword/Password keyboards, masked defaults and reveal controls, and explicit submission. Reuse Luma components. Centralize confirmation through Task 1's canonicalization; dispose temporary comparison arrays.
- [x] Implement fixed-ID pattern layout with disjoint circular node targets of at least 48dp diameter, center spacing at least 64dp, physical LTR placement inside surrounding locale-aware UI, sampled-segment crossing order, midpoint insertion, primary-pointer ownership, pointer cancellation, and trace clearing on completion.
- [x] Implement the same node actions through focusable position-labeled controls for TalkBack, Enter/Space, Clear, and Done drawing. Do not publish sequence, selected nodes, or indexes through semantics/live announcements. Show only generic Pattern ready and localized validation errors. Parent Continue remains explicit.
- [x] Add EN/FR/FA method labels, length instructions, current/new/confirm method copy, generic unlock/recovery/errors, pattern-position/action labels, and Change lock method. Replace password-only copy on shared flows; keep recovery-code strings/field distinct.
- [x] Review ACT-01–04/10/11 in source: exact digit ranges and Unicode rules, canonical confirmation, clear/retry semantics, disabled/busy controls, large-text reflow and scrolling, touch target geometry, RTL coordinates, accessible completion, and absence of secret state in persistence or semantics. Parse the three XML files as a source check; defer rendering/runtime claims.

**Source handoff:** Shared component signatures, state ownership/lifecycle behavior, localized resource list, and gesture/accessibility limitations pending QA.

### Task 4: Integrate setup, credential changes, and unlock

**Files:** Modify `ui/privacy/AppLockSettingsRoute.kt`, `ui/privacy/AppLockGate.kt`, and `ui/privacy/AppLockViewModel.kt` where route adapters require metadata.

**Interfaces:** Consume Tasks 2/3. Retain public route and AppLockUnlockDialog callbacks and existing coordinator browse binding. No navigation changes are required.

- [x] Load descriptor before enabled-settings authentication and unlock input. Key transient editors to descriptor generation/type; display loading/unavailable states with submission disabled. Disabled metadata cancels obsolete authentication. Observe coordinator.snapshot for policy-generation changes or departure from READY and cancel/clear stale prompts before a new request can be made.
- [x] Replace settings-entry and unlock password fields with shared method-specific input; pass descriptor.generation to verify. Preserve browse request capture/binding, proofOnly handling, cancellation, private-draft resume, secure window, and existing coordinator grant rules.
- [x] Add setup method selector defaulting to Password and shared enrollment. At Continue freeze selected type with its canonical owned secret for the existing recovery-code acknowledgment ceremony. Pass that frozen pair into typed enable. Clear both on cancel/stop/failure; keep setup disabled until recovery acknowledgment succeeds.
- [x] Render current method and Change lock method. Use separate editor states for current authentication and replacement enrollment; default replacement type to current, allow all three. Verify current with CHANGE_PASSWORD and expected generation, bind/validate the pending UUID, then invoke typed changePassword exactly once. Preserve independent fresh auth for policy, disable, and recovery-code rotation.
- [x] Remove route-local password-only confirmation/counting and SecretField duplication once shared components cover consumers. Preserve stable outer navigation/actions and existing test tags where semantically compatible; document new type/pattern tags for later QA adapters.
- [x] Review ACT-01/05/06/10/11 and every old password-field call site. Trace each current→replacement combination, no-selector authentication prompts, pending recovery type freeze, repeated taps, method switches, ON_STOP/cancel, unavailable initialization, and generation changes while a request is in flight.

**Source handoff:** Setup/unlock/change call graph, tag/resource changes, nine-transition coverage map for later QA, and unchanged proof authority boundaries.

### Task 5: Integrate recovery and sensitive-operation prompts

**Files:** Modify `ui/privacy/AppLockRecoveryRoute.kt`, `ui/privacy/LockSensitiveActionDialog.kt`, and `lock/LockSensitiveOperationGuard.kt` only if the Task 2 adapter needs reconciliation.

**Interfaces:** Consume typed recover, guard.credentialDescriptor(pending), guard.verify(pending, secret), and Task 3's components. Preserve guard.execute/executeSensitive and recovery/reset route callbacks.

- [x] Replace replacement-password fields in recovery with selector defaulting to Password and shared enrollment. Keep the recovery-code field independent. Freeze replacement type/canonical secret with pending recovery code/confirmation; pass them to typed recover after acknowledgment of the replacement recovery code.
- [x] Clear recovery editor and frozen state on stop, disposal, cancellation, method switch, failure, and completion; cancel abandoned preparations and preserve existing clipboard cleanup for the recovery code. Keep old credentials active until the atomic successful commit, then return locked. Preserve the existing forgotten-credential erase confirmation flow and erase-only authority.
- [x] Load sensitive-prompt metadata only through the pending-operation guard; compare with the pending generation. Observe guard.lockSnapshot and cancel/clear when load status departs from READY or policy generation changes from the descriptor's generation. Render shared input for the stored type and submit through guard.verify. Preserve cancellation, proof binding/consumption, purpose-specific copy, secure window, and guard execution boundaries.
- [x] Review ACT-05/07/09/10/11: recovery to each method, wrong/cooldown code, stale generation between code display and commit, cancellation retaining old pairs, pending operation cancellation while descriptor loads, unchanged export/share/backup/restore/reset proof gates, accessible reachable actions, and no secret sequence in semantics.

**Source handoff:** Recovery replacement lifecycle, sensitive-operation integration inventory, remaining QA obligations, and any unavailable-state copy changes.

### Task 6: Reconcile source and record implementation handoff

**Files:** Update this plan's completed checkboxes and add `docs/feature-roadmap/app-lock-credential-types-implementation-handoff.md`. Reconcile only feature-owned production/resource files from Tasks 1–5.

**Interfaces:** Produce a requirement→source→deferred-acceptance mapping for ACT-01–ACT-11 and a source-only completion record.

- [x] Search all production references to LockCredentialService, verify/enable/changePassword/recover, PersistedCredential constructors, old password-only labels, and route-local password validation. Reconcile actual execution-baseline paths and remove stale production callers; list deferred test adapters explicitly.
- [x] Inspect the complete feature diff and check ACT-01–ACT-11 against source. Inspect version branches and unchanged salt/key constants, secret ownership and exception cleanup, shared retry accounting, recovery atomicity, generation/proof binding, and all authentication-surface coverage. Resolve source defects within their owning task.
- [x] Run only allowed source checks: parse affected resource XML; check document links and source paths; run `git diff --check`. Record real commands/results. Do not run Gradle, build/test tools, or device work, and do not equate source checks with compilation or runtime verification.
- [x] Record Git SHA, dirty feature patch SHA-256, touched files, final signatures, completed requirement mapping, exact source-check results, known limitations, and deferred acceptance obligations. Mark status `SOURCE COMPLETE — QA PENDING` only after source work is finished. Preserve all unrelated edits and release artifacts.

**Exit:** All implementation handoffs exist; no preceding QA PASS is required to reach the next source task. The separate acceptance contract remains pending QA and release is not certified by this plan.
