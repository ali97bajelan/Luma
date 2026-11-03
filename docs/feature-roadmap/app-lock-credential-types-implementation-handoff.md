# App Lock Credential Types — implementation handoff

**Status: SOURCE COMPLETE — QA PENDING**

Implementation follows the [design specification](../superpowers/specs/2026-11-02-app-lock-credential-types-design.md) and [source plan](../superpowers/plans/2026-11-02-app-lock-credential-types.md). Runtime acceptance and release certification remain pending.

## Revision and workspace record

- Implementation-base `HEAD`: `03a440a06552f1fb47ff0f3ae0fae69149515f7e`.
- The feature was later committed by the user as `6ac86bfd6dd2167bb362bd787692de58eee9eff3`. The follow-up JVM setter-name fix is an unstaged working-tree change; no commit was created for that fix.
- Feature source/resource unified-diff SHA-256 against the implementation-base commit (18 files, sorted by path): `c895107885f5ee4abf103482e3cd516f267ac4ef407e2045701e98d8cf5b2d65` (148,357 bytes). This hash covers only the production Kotlin and localized string resources listed below, not this handoff or the plan/spec documents.
- At initial implementation, existing staged plan/spec files and unrelated workspace changes were preserved. Current unrelated edits include `app/build.gradle.kts`, `scripts/generate_widget_previews.py`, and untracked `app/production/` artifacts.

## Implemented behavior

- Added `PIN`, `PASSWORD`, and `PATTERN` credential types, method descriptors, and unavailable/disabled results.
- Added canonicalization for ASCII, Persian, and Arabic-Indic PIN digits; exact well-formed Unicode password code points; and canonical 3×3 pattern sequences with midpoint insertion.
- Upgraded the authenticated local payload to version 2, storing the type before the existing password salt. Version 1 credentials decode as `PASSWORD` without a read-time rewrite or verifier change.
- Extended the credential service and sensitive-operation guard with stored-method descriptors, typed mutations, and generation-bound verification. Existing derivation, retry accounting, recovery confirmation, proof purpose, and atomic write paths remain in use.
- Added shared transient Compose editors, method selection and enrollment, a physical-LTR pattern board with gesture and accessible node controls, and EN/FR/FA copy.
- Named the text editor mutators `updateEntry` and `updateConfirmation` so they do not clash with JVM setters generated for the corresponding Kotlin properties.
- Integrated stored-method prompts into app unlock, security settings, recovery replacement, and sensitive operations. Method changes and recovery freeze their selected type with the owned replacement secret until the existing commit ceremony completes.
- Added lifecycle/generation invalidation for pending authentication and recovery preparation, plus scrolling for credential dialogs.

## Requirement mapping

| Requirement | Source implementation | Deferred acceptance work |
| --- | --- | --- |
| ACT-01 | [Settings setup](../../app/src/main/java/com/luma/lifeos/ui/privacy/AppLockSettingsRoute.kt), [shared selector/enrollment](../../app/src/main/java/com/luma/lifeos/ui/privacy/LockCredentialInput.kt) | Exercise each setup form, confirmation, method switching, and recovery acknowledgment. |
| ACT-02 | [Input policy](../../app/src/main/java/com/luma/lifeos/lock/LockCredentialInputPolicy.kt), [PIN instructions](../../app/src/main/res/values/strings.xml) | Verify digit ranges, leading zeros, Persian and Arabic-Indic digits, and rejection of mixed input. |
| ACT-03 | [Input policy](../../app/src/main/java/com/luma/lifeos/lock/LockCredentialInputPolicy.kt) | Verify exact whitespace/case, Unicode code-point limits, and malformed surrogate rejection. |
| ACT-04 | [Pattern rules](../../app/src/main/java/com/luma/lifeos/lock/LockPatternRules.kt), [pattern UI](../../app/src/main/java/com/luma/lifeos/ui/privacy/LockPatternInput.kt) | Exercise all midpoint directions, revisits, fast movement, cancellation, keyboard, TalkBack, and RTL. |
| ACT-05 | [Unlock](../../app/src/main/java/com/luma/lifeos/ui/privacy/AppLockGate.kt), [settings](../../app/src/main/java/com/luma/lifeos/ui/privacy/AppLockSettingsRoute.kt), [sensitive prompt](../../app/src/main/java/com/luma/lifeos/ui/privacy/LockSensitiveActionDialog.kt), [view-model adapter](../../app/src/main/java/com/luma/lifeos/ui/privacy/AppLockViewModel.kt) | Verify every authentication surface selects the persisted method and never offers a method selector. |
| ACT-06 | [Typed service](../../app/src/main/java/com/luma/lifeos/lock/DefaultLockCredentialService.kt), [settings flow](../../app/src/main/java/com/luma/lifeos/ui/privacy/AppLockSettingsRoute.kt) | Exercise all nine current-to-replacement transitions, stale prompts, proof reuse, and atomic failure behavior. |
| ACT-07 | [Recovery flow](../../app/src/main/java/com/luma/lifeos/ui/privacy/AppLockRecoveryRoute.kt), [typed recovery service](../../app/src/main/java/com/luma/lifeos/lock/DefaultLockCredentialService.kt) | Verify replacement to each method, wrong/cooldown codes, cancellation, stale confirmation, and return-to-locked behavior. |
| ACT-08 | [Versioned store](../../app/src/main/java/com/luma/lifeos/lock/LocalLockStore.kt) | Verify v1 numeric-password compatibility, v2 round trips, corruption, truncation, interrupted writes, and retained retry fields. |
| ACT-09 | [Credential service](../../app/src/main/java/com/luma/lifeos/lock/DefaultLockCredentialService.kt), [guard](../../app/src/main/java/com/luma/lifeos/lock/LockSensitiveOperationGuard.kt), [retry policy](../../app/src/main/java/com/luma/lifeos/lock/LockRetryPolicy.kt) | Exercise cooldown/restart behavior, concurrent generation changes, pending UUID binding, and every sensitive-operation proof gate. |
| ACT-10 | [Transient editor](../../app/src/main/java/com/luma/lifeos/ui/privacy/LockCredentialInput.kt), [settings lifecycle](../../app/src/main/java/com/luma/lifeos/ui/privacy/AppLockSettingsRoute.kt), [recovery lifecycle](../../app/src/main/java/com/luma/lifeos/ui/privacy/AppLockRecoveryRoute.kt) | Inspect saved state, navigation, process death, stop/disposal, clipboard cleanup, and all secret-holder paths. |
| ACT-11 | [EN](../../app/src/main/res/values/strings.xml), [FR](../../app/src/main/res/values-fr/strings.xml), [FA](../../app/src/main/res/values-fa/strings.xml), [secure window and dialog layout](../../app/src/main/java/com/luma/lifeos/ui/privacy/LockSensitiveActionDialog.kt) | Render all locales/themes, compact and large-text layouts; test TalkBack, hardware keyboard, focus order, and secure-window behavior. |

## Final service signatures

```kotlin
suspend fun credentialDescriptor(): CredentialDescriptorResult
suspend fun enable(secret: CharArray, type: LockCredentialType, policy: LockPolicy,
    recoveryConfirmation: RecoveryConfirmation): CredentialResult
suspend fun verify(secret: CharArray, purpose: AuthPurpose,
    expectedGeneration: Long? = null): CredentialResult
suspend fun changePassword(proof: OperationProof, replacement: CharArray,
    replacementType: LockCredentialType): CredentialResult
suspend fun recover(code: CharArray, replacement: CharArray,
    replacementType: LockCredentialType, confirmation: RecoveryConfirmation): CredentialResult
```

`LockSensitiveOperationGuard` also exposes `credentialDescriptor(pending)`, a read-only `lockSnapshot`, and generation-bound `verify(pending, secret)`. The corresponding `AppLockViewModel` calls delegate to the service without moving proof authority into UI components.

## Source reasoning cases

- PIN `0012` remains `0012`; Persian `۰۰۱۲` canonicalizes to the same four ASCII digits. No trimming occurs, and a nondigit fails canonicalization.
- A four-code-point password containing spaces is retained byte-for-code-unit as entered; an isolated high or low surrogate returns invalid input.
- Pattern `0→2` inserts node `1`; `0→8` inserts `4`; `0→5` inserts none. Reverse traversal `8→0` inserts `4` in reverse order. For a previously visited midpoint, `1→0→2` stays `1,0,2`; the final `0→2` does not insert node `1` again. Serialized duplicates fail `encode` before replay.
- A v1 credential reads as `PASSWORD`; its existing salt/verifier and retry fields follow the original read order. A disabled v1 record has no credential block or type. An unknown v2 enum name reaches the existing corrupt/unavailable path, and trailing payload data is rejected.
- These are source traces, not executed migration, verifier, or gesture tests.

## Touched files

Production Kotlin and resources (the hash scope above):

- `app/src/main/java/com/luma/lifeos/lock/DefaultLockCredentialService.kt`
- `app/src/main/java/com/luma/lifeos/lock/LocalLockStore.kt`
- `app/src/main/java/com/luma/lifeos/lock/LockCredentialService.kt`
- `app/src/main/java/com/luma/lifeos/lock/LockRetryPolicy.kt`
- `app/src/main/java/com/luma/lifeos/lock/LockSensitiveOperationGuard.kt`
- `app/src/main/java/com/luma/lifeos/lock/LockCredentialInputPolicy.kt` (new)
- `app/src/main/java/com/luma/lifeos/lock/LockCredentialType.kt` (new)
- `app/src/main/java/com/luma/lifeos/lock/LockPatternRules.kt` (new)
- `app/src/main/java/com/luma/lifeos/ui/privacy/AppLockGate.kt`
- `app/src/main/java/com/luma/lifeos/ui/privacy/AppLockRecoveryRoute.kt`
- `app/src/main/java/com/luma/lifeos/ui/privacy/AppLockSettingsRoute.kt`
- `app/src/main/java/com/luma/lifeos/ui/privacy/AppLockViewModel.kt`
- `app/src/main/java/com/luma/lifeos/ui/privacy/LockSensitiveActionDialog.kt`
- `app/src/main/java/com/luma/lifeos/ui/privacy/LockCredentialInput.kt` (new)
- `app/src/main/java/com/luma/lifeos/ui/privacy/LockPatternInput.kt` (new)
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-fr/strings.xml`
- `app/src/main/res/values-fa/strings.xml`

Implementation status documents:

- `docs/superpowers/plans/2026-11-02-app-lock-credential-types.md`
- `docs/superpowers/specs/2026-11-02-app-lock-credential-types-design.md`
- `docs/feature-roadmap/app-lock-credential-types-implementation-handoff.md` (this file)

The test fake/interface adapters and acceptance tests are intentionally deferred by the plan. The unrelated modified `scripts/generate_widget_previews.py` and untracked `app/production/` content were not changed by this feature work.

## Source checks and review

- `git diff --check`: passed (exit code 0).
- `git diff --cached --check`: passed (exit code 0).
- Python `xml.etree.ElementTree` parsing of `values/strings.xml`, `values-fr/strings.xml`, and `values-fa/strings.xml`: passed; no duplicate string IDs and all three ID sets match.
- Source path and resource reference scan: passed for all 18 feature-owned Kotlin/resource paths; referenced `R.string` IDs resolve.
- Source-level clash check: explicit text mutators are named `updateEntry`/`updateConfirmation`, avoiding the generated `setEntry(String)`/`setConfirmation(String)` JVM signatures.
- Follow-up `git diff --check` passed, and a source search found no remaining `setEntry`/`setConfirmation` editor mutators.
- Production caller scan: service verification/setup/change/recovery callers use the typed signatures and production `PersistedCredential` constructors provide a credential type. The remaining old test adapters are listed above as deferred QA work.
- A fresh read-only review found Compose import/coordinate issues, an LTR board placement issue, drag consumption, a sensitive-dialog cancellation lifetime bug, stale-authentication races on stop, and recovery reveal-state cleanup. These were corrected in the source. Static checks were rerun after those corrections; no second review or runtime verification was performed.
- No Gradle, build, lint, test, emulator, or device command was run, as required by the source plan. This record does not claim compilation or runtime behavior.

## Remaining acceptance obligations

The separate QA pass must cover all ACT-01–ACT-11 behaviors, including v1/v2 persistence and write failure, the nine method transitions, malformed Unicode, localized digits, pattern gesture/accessibility behavior, lifecycle/process death, generation races, retry/cooldown persistence, and EN/FR/FA large-text/RTL/secure-window rendering. Test adapters must be updated before test compilation. No release or migration-readiness claim is made here.

## Suggested commit message

```text
feat(app-lock): support PIN, password, and pattern credentials

Persist credential type with v1 password compatibility and share typed
entry across setup, unlock, recovery, and sensitive-operation prompts.
```
