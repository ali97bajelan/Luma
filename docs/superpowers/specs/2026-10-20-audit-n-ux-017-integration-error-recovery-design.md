# UX-017 — Successful integration refresh leaves obsolete error text

**Status:** Implementation complete; regression and runtime verification remain pending at the user's request.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-017](../../UI_UX_AUDIT-20261020N.md#ux-017--successful-integration-refresh-leaves-obsolete-error-text)
**Priority / effort / confidence:** P2 — Medium / Low / High (copied from the static audit; not a delivery estimate).
**Affected surface:** Usage access and Health Connect settings
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-017-integration-error-recovery.md)
**Delivery phase:** 2 — recovery and truthful feedback

## Intent and success

Clear stale integration feedback only when the operation that failed has recovered. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

The audit is source evidence, not reproduced runtime acceptance. The scoped implementation follows this specification; tests and runtime exercises remain future work.

## Dependencies

None. Coordinate overlapping files using the index's integration order.

## Global constraints

- Keep Kotlin/JVM 17, Android minSdk 28 and compileSdk/targetSdk 35; use existing dependencies unless a task explicitly requires a reviewed exception.
- All changed user-facing copy must exist in EN/FR/FA with matching resource names and numbered placeholder signatures; preserve RTL and intended Gregorian display/storage behavior.
- Keep semantic light/dark palettes, at least 48 dp interactive targets, and reachability at 100%/130%/200% font, narrow portrait and short landscape.
- Preserve offline/private storage, existing consent boundaries, stable record/command IDs, original retry payloads and Undo protections.
- Never truncate accepted personal content, infer write failure means no commit, or treat an unavailable privacy mode as known.
- Only implement this task and its declared prerequisites; preserve unrelated working-tree changes and existing navigation/draft state.

## Behavior and policy

1. A successful state emission clears OBSERVE only; successful refreshAccess clears ACCESS_REFRESH only. A successful import/setEnabled clears only its own failure.
2. Use per-operation request generations so an older success cannot erase a newer failure. Cancellation is propagated and not presented as a user-facing error.
3. Refresh UI invokes the corresponding ViewModel refresh path as well as shared availability refresh. Provide relevant Retry per failed operation.
4. Disabling import while a request is busy remains allowed where currently supported; access grants never silently enable importing and retained history is not deleted.

## Design and contract

Give Usage and Health settings separate observation, access-refresh, import and preference error channels. Render and retry each channel independently while preserving access/import consent boundaries.

enum class IntegrationSettingsOperation { OBSERVE, ACCESS_REFRESH, IMPORT, PREFERENCE }; data class IntegrationSettingsError(val operation: IntegrationSettingsOperation, val requestId: Long). Both settings states replace actionError: Boolean with errors: Map<IntegrationSettingsOperation, IntegrationSettingsError>. Existing retryConfiguration(), refresh(), importNow(), setEnabled(Boolean) remain public.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `observationRetryClearsOwnError` | observeState fails, then emits after retryConfiguration. | OBSERVE error clears and current status is shown. |
| AC-2 | `accessRetryClearsRefreshError` | refreshAccess fails once then succeeds. | ACCESS_REFRESH error disappears in Usage and Health cards. |
| AC-3 | `importErrorSurvivesUnrelatedEmission` | Import fails while state observation succeeds. | IMPORT failure remains actionable until its own successful retry. |
| AC-4 | `lateSuccessCannotEraseNewFailure` | An older refresh succeeds after a newer refresh fails. | The newest ACCESS_REFRESH failure remains visible. |
| AC-5 | `consentAndCancellationRemainCorrect` | Disable import while busy or cancel a coroutine. | Preference boundaries remain; cancellation creates no false failure and access alone never enables import. |

## Scope boundary

No integration provider rewrite, automatic permission grants, import-on-access enablement or historical data deletion.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.
