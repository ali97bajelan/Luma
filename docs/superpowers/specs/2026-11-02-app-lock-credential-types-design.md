# App lock credential types

Date: 2 November 2026. Status: source implementation complete; QA pending. See the [implementation handoff](../../feature-roadmap/app-lock-credential-types-implementation-handoff.md) for source checks and deferred acceptance.

## Intent and authority

Let users choose the form of their independent Luma app-lock credential: digit-only PIN, character password, or pattern. Exactly one method is active at a time. The method applies to every app-lock authentication surface, including security settings and sensitive operations.

This package contains the specification and an [implementation-only plan](../plans/2026-11-02-app-lock-credential-types.md). “Implementation only” omits QA tasks from the plan, consistent with the existing [workflow](../../feature-roadmap/WORKFLOW.md). Test authoring, Gradle/sync/build/lint, test execution, and device work remain deferred. Source completion does not claim runtime acceptance; that remains pending subsequent QA.

The three choices are user requirements. Lengths, default selection, pattern rules, accessibility interaction, and migration details below are proposed engineering/product defaults, not separately approved user decisions. This specification supersedes the password-only portions of LOCK-R01/R02/R12 and their shared service contracts in the [31 October specification](2026-10-31-app-lock.md); other lock behavior remains governed by that specification and current source.

## Source baseline when this specification was written

- `LockCredentialService` accepts owned `CharArray` secrets for enable, verify, changePassword, and recover. `DefaultLockCredentialService` enforces 4–32 Unicode code points and serializes mutations through its process mutex.
- `AndroidLockCrypto` derives verifiers with PBKDF2-HMAC-SHA256 (600,000 iterations, 256 bits), a random 16-byte salt, and a Keystore HMAC pepper. Recovery has its own verifier format.
- `AndroidLocalLockStore` writes an authenticated version-1 payload through `AtomicFile` in `noBackupFilesDir`. `PersistedCredential` has no credential type.
- Password inputs are repeated across `AppLockSettingsRoute`, `AppLockUnlockDialog` in `AppLockGate.kt`, `AppLockRecoveryRoute`, and `LockSensitiveActionDialog`. `LockSensitiveOperationGuard` owns fresh sensitive-operation proofs.
- Settings already opens App lock from its top-level row. `AppLockViewModel` is a thin adapter. Coverage, relocking, restricted capture, and pending-operation proof binding already exist.

## Approaches

1. **Persist a credential type and extend the existing service (recommended).** Reuse retry accounting, verifier derivation, proofs, recovery, and atomic writes. Introduce one shared input component and a pure pattern helper. This changes the relevant flows without adding competing authorities.
2. **Infer the method from the secret.** This cannot distinguish a numeric legacy password from a PIN or a pattern encoding, and cannot choose the correct input before authentication. Rejected.
3. **Maintain separate PIN/password/pattern services or simultaneous credentials.** This introduces multiple recovery and retry paths and requires a product decision about fallback credentials. Outside this request.

The plan uses approach 1 as a proposed implementation choice. No claim of prior approval is made.

## Requirements

| ID | Required behavior |
| --- | --- |
| ACT-01 | Setup offers PIN, Password, and Pattern; enter the selected credential once and provide one matching confirmation before enabling lock. |
| ACT-02 | PIN accepts 4–32 digits. ASCII, Persian U+06F0–U+06F9, and Arabic-Indic U+0660–U+0669 digits canonicalize to ASCII. Leading zeros are significant. Reject other characters; do not silently strip them. |
| ACT-03 | Password preserves existing semantics: 4–32 well-formed Unicode code points, exact case and whitespace, no trimming or normalization. Letters, digits, symbols, and spaces are allowed; no composition rule. |
| ACT-04 | Pattern uses a fixed physical 3×3 grid, 4–9 unique nodes, ordered input, skipped-midpoint insertion, and no repeated nodes. |
| ACT-05 | Unlock, private-draft resume, settings entry, policy changes, disable, recovery-code rotation, and sensitive operations render the stored form automatically; there is no method selector on authentication prompts. |
| ACT-06 | Changing method or secret requires fresh authentication with the current credential and confirmation of the replacement. Commit type/verifier/generation atomically; revoke grants and return locked. |
| ACT-07 | Recovery may choose any of the three replacement methods, requires first entry plus one matching confirmation, and atomically replaces both credential and recovery code using the existing recovery ceremony. |
| ACT-08 | Version-1 credentials remain PASSWORD, even when numeric. Unknown/corrupt type or format fails closed. |
| ACT-09 | All methods share the existing persisted failure counter and cooldown with recovery. Preserve proof purpose, generation, pending UUID, at-most-once use, and cancellation rules. |
| ACT-10 | Credential input is process-local and excluded from saved state, navigation, logs, analytics, clipboard, archives, and backup. Clear visible input on submission; owned pending setup/recovery arrays may remain only until the final commit. Clear all secret holders on stop/disposal/cancel/method change/failure/completion. Generated recovery-code copying retains its existing explicit action and cleanup. |
| ACT-11 | EN/FR/FA, physical RTL-stable pattern positions, TalkBack, hardware keyboard, large text, compact screens, light/dark, and secure-window behavior apply to every changed surface. |

## Credential entry and flows

New setup defaults to Password to retain the current experience. A labeled single-choice selector displays PIN, Password, Pattern; helper text explains the selected rule. Switching form clears both new and confirmation input, visibility state, errors, and any uncommitted recovery preparation. Never relabel existing secret content as another method.

PIN uses `KeyboardType.NumberPassword`, masked input, and explicit submission; Password uses `KeyboardType.Password` and the current reveal toggle. PIN may also be revealed explicitly. Neither auto-submits by length. UI may temporarily hold incomplete input but marks an invalid character and prevents continuation. Service validation remains authoritative. PIN confirmation compares canonical digits; password confirmation compares exact input.

Pattern enrollment displays Draw pattern, then Draw pattern again. A mismatch clears confirmation and requests another confirmation; Retry starts a fresh first drawing. Unlock and sensitive operations require an explicit Continue after completing a valid drawing, matching text entry and avoiding double submissions. Starting another gesture replaces the unfinished/submitted local drawing. Authentication prompts offer Clear and Forgot lock credential? without exposing another authentication method.

An enabled settings screen shows the current method and a Change lock method action. Replacement defaults to the current method and supports replacing the secret without changing method. Existing `AuthPurpose.CHANGE_PASSWORD` may retain its internal name; it authorizes precisely one credential replacement. Current input always uses the stored method while replacement/confirmation use the newly selected method.

Recovery uses its existing recovery-code field separately from the replacement credential selector/input. Freeze the selected replacement method together with its confirmed secret while showing the new recovery code. Cancel, stop, or stale-generation failure discards the pending replacement and preparation; successful recovery rotates the code and returns locked. An ordinary credential change retains the recovery code.

Load nonsecret method metadata before displaying authentication input. Show a loading state with submission disabled; read failure displays the existing unavailable path and never defaults to Password. Disabled metadata on an authentication prompt cancels the obsolete request. Callers observe `coordinator.snapshot`: a generation change or departure from READY clears secret input and cancels the prompt; the caller may start a new request. Service-side expectedGeneration checks also close the race after a UI snapshot. Metadata loading itself never grants access.

## Pattern contract

Node IDs are `0 1 2 / 3 4 5 / 6 7 8`, physically left-to-right, top-to-bottom in every locale. Do not mirror node IDs or the gesture grid in Persian. The surrounding layout and copy follow locale direction normally.

- A gesture starts when the primary pointer enters a node target. It adds subsequent entered nodes in movement order, including nodes crossed between sampled positions. Process historical pointer samples and segment intersections in traversal order; do not rely only on the latest pointer coordinate.
- Each node has a circular 48dp minimum hit target, radius 24dp at the minimum size, with centers spaced at least 64dp apart. Enlarge the grid when space permits, keeping disjoint targets. Ignore off-grid movement, additional pointers, and already selected nodes. Pointer cancellation discards the drawing. Starting outside a node may begin when the pointer first enters a target.
- For each new node, if both row/column deltas have even values and the midpoint is a distinct unvisited grid node, insert it first. Thus `0→2` becomes `0,1,2`, `0→8` becomes `0,4,8`, and `0→5` has no insertion. If the midpoint was already visited, do not insert it again. Preserve drawing direction.
- Completed sequences must have 4–9 unique nodes. Serialize each node as ASCII `'0' + id`, with no delimiter. Validate stored-input sequences for uniqueness, range, length, and canonical midpoint order; never accept arbitrary short digit text as a pattern.
- Keep current selection feedback while entering, then clear the trace on completion; retain the pending secret only for explicit submission/confirmation. Do not display the previously completed pattern or announce its sequence. Provide a generic Pattern ready status, Clear, and a retry message after failure.
- TalkBack and hardware keyboard can select focusable node controls in the same physical grid, append by activate/Enter/Space, Clear, and finish via a dedicated Done drawing action. This uses the identical midpoint helper, length rules, confirmation, and verifier. Labels describe positions (Top left through Bottom right), never selected sequence indexes. Exclude secret selection/order from semantics, state descriptions, and live announcements; expose only generic progress/completion. No drag-only requirement.

Clear, method change, cancellation, stop/disposal, and ownership transfer on submission reset every pattern holder: active pointer, node-list references, trace, completion flag, and encoded arrays. Enrollment/recovery may retain only the explicitly owned pending array until their final commit or cancellation. Dropping an immutable list/string reference is not a guarantee of physical memory erasure; avoid additional copies and wipe mutable buffers where owned.

Pattern canonicalization is an input encoding for the existing credential derivation, not a new crypto algorithm. Keep the existing derivation, verifier domain, salt size, and Keystore alias for all three methods. The authenticated stored type selects validation; one stored active verifier prevents cross-method fallback. Never store a sequence, PIN, or password in reversible form. Pattern and short PIN offer fewer combinations; existing persistent throttling must not be weakened.

## Source contracts

All new types below live in app's `com.luma.lifeos.lock`; domain lock policy and access decisions do not need credential details.

```kotlin
enum class LockCredentialType { PIN, PASSWORD, PATTERN }
data class LockCredentialDescriptor(val type: LockCredentialType, val generation: Long)
sealed interface CredentialDescriptorResult {
    data class Ready(val descriptor: LockCredentialDescriptor) : CredentialDescriptorResult
    data object Disabled : CredentialDescriptorResult
    data object Unavailable : CredentialDescriptorResult
}
```

`LockCredentialInputPolicy` owns `canonicalize(type: LockCredentialType, input: CharArray): CharArray?`, returning a new owned canonical array, or null for invalid input, without retaining or mutating the caller's array. Shared validation eliminates local `codePointCount` duplicates. `LockPatternRules.appendNode(current: List<Int>, next: Int): List<Int>` handles midpoint insertion; `encode(nodes: List<Int>): CharArray?` validates and serializes. Pattern service input must be canonical already; replaying its nodes through appendNode must reproduce it exactly.

Extend existing service methods with required explicit types on mutations and an optional generation binding for verification. Update production callers together; test fake/interface adapters are deferred to QA.

```kotlin
suspend fun credentialDescriptor(): CredentialDescriptorResult
suspend fun enable(secret: CharArray, type: LockCredentialType,
    policy: LockPolicy, recoveryConfirmation: RecoveryConfirmation): CredentialResult
suspend fun verify(secret: CharArray, purpose: AuthPurpose,
    expectedGeneration: Long? = null): CredentialResult
suspend fun changePassword(proof: OperationProof, replacement: CharArray,
    replacementType: LockCredentialType): CredentialResult
suspend fun recover(code: CharArray, replacement: CharArray,
    replacementType: LockCredentialType, confirmation: RecoveryConfirmation): CredentialResult
```

The service reads the persisted type under its existing mutex; callers never select a verification type. UI callers pass descriptor.generation. Before hashing or updating failures, a generation mismatch returns Cancelled and clears owned input. Existing internal callers without a prompt may omit expectedGeneration. `LockSensitiveOperationGuard` exposes `suspend fun credentialDescriptor(pending: PendingSensitiveOperation): CredentialDescriptorResult`, returning Ready only while the pending operation and descriptor generation remain current, and read-only `val lockSnapshot: StateFlow<LockSnapshot>` delegating to coordinator.snapshot for prompt invalidation observation. Its existing `verify(pending, secret)` passes pending.policyGeneration to the service; no raw service access is added to the dialog. Observing lockSnapshot confers no authority.

Descriptor reads run on IO under the service mutex, use authenticated storage, and do not rewrite state. Existing CredentialResult variants remain sufficient. Invalid local format produces InvalidInput without counting an attempt; a valid but incorrect secret counts once; cooldown is checked before validation/derivation and prevents verification. Clear original and canonical arrays in finally on every exit, including cancellation and exceptions. Do derivation on Dispatchers.Default and persistence on Dispatchers.IO.

## Persistence and compatibility

Extend `PersistedCredential` with required `type`. Payload version 2 inserts the enum name immediately before passwordSalt in the credential block; all other fields retain their version-1 ordering and bounds. Decode versions 1 and 2 explicitly: v1 synthesizes PASSWORD; v2 requires a recognized type when a credential exists. Disabled states have no credential/type. Preserve the envelope MAGIC, tag and authenticated-payload process, file path, key alias, salt/verifier bytes, policy generation, recovery metadata, and retry data.

Reads of v1 are read-only. The next normal successful state write emits v2 without rehashing or inferring type. Interrupted writes recover the prior complete record through AtomicFile. Invalid/truncated/unknown records stay unavailable and cannot reset to disabled or prompt for fresh setup. A version-2 record is intentionally unreadable by older app versions; downgrades remain fail-closed rather than removing the lock. No Room/archive version change is needed, and credentials remain excluded from exports/backups.

Type and verifier are committed in the same authenticated record. Failed changes/recovery leave the prior complete record active; successful changes use the existing generation increment/invalidation callback. Keep retry counts across version migration; only existing successful authentication/mutation rules may reset them.

## Constraints and implementation boundary

- Minimum API 28; compile/target SDK 35; Java 17. Reuse Kotlin/Compose and existing platform crypto; add no dependency, permission, network, or telemetry.
- Keep `core:domain` Android-free. Coverage, relock timing, restricted capture, Settings placement, and proof authority are outside this change.
- Use localized EN/FR/FA string resources and shared Luma controls. Replace password-only instructions/errors with credential-neutral or method-specific text where used by multiple methods. Keep recovery-code input/copy distinct.
- Do not author/edit tests or invoke Gradle, sync, builds, lint, tests, emulators, or devices during implementation. Source/diff review, XML parsing, link checks, and `git diff --check` are allowed.
- Source completion must be labeled `SOURCE COMPLETE — QA PENDING`; planning completion does not claim feature completion.

## Acceptance contract for later QA

Acceptance must cover ACT-01–ACT-11: setup/unlock/current-plus-replacement across all nine method transitions; leading-zero and localized-digit PINs; exact Unicode passwords including malformed surrogates; all midpoint/reverse/revisit/crossed-node patterns; confirmation mismatch and abandoned enrollment; recovery to each method; cooldown/restart/reboot persistence; v1 numeric-password compatibility and v2 corruption/interrupted writes; concurrent generation changes and stale operation proofs; every authentication surface; stop/recreation/process death; secure windows and absence of secrets from saved/public state; EN/FR/FA RTL, compact/large-text layouts, TalkBack and hardware keyboard. These are acceptance obligations, not execution tasks or recorded results in this package.
