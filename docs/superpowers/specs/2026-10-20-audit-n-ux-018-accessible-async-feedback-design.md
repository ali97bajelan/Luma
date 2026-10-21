# UX-018 — Several important asynchronous results lack announcement semantics

**Status:** Implementation complete; regression and runtime verification remain pending at the user's request.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-018](../../UI_UX_AUDIT-20261020N.md#ux-018--several-important-asynchronous-results-lack-announcement-semantics)
**Priority / effort / confidence:** P2 — Medium / Low / High (copied from the static audit; not a delivery estimate).
**Affected surface:** Backup, Language, Today, Timeline, Daily summary, Event detail
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-018-accessible-async-feedback.md)
**Delivery phase:** 2 — recovery and truthful feedback

## Intent and success

Make important asynchronous results and errors discoverable through consistent live feedback semantics. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

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

1. Cover backup completion/failure, failed language changes, feed load errors, summary errors and event detail failures. Preserve ordinary content text as non-live.
2. Success is polite feedback without error semantics; failure is polite feedback with error semantics and a nearby named Retry where supported.
3. Do not steal focus for an announcement, duplicate a message in parent/child live regions or announce unchanged values on periodic refresh.
4. If a result cannot yet be described accurately, fix its owning state contract first; semantics must not turn an unknown outcome into success.

## Design and contract

Use existing AccessibleFeedback at scoped asynchronous result sites. Classify success versus error and keep Retry adjacent without making entire pages live regions.

Reuse AccessibleFeedback(message: String, modifier: Modifier = Modifier, isError: Boolean = true) and its existing polite/error semantics. Call sites explicitly set isError=false for successful completion. Feedback identities follow operation/status transitions rather than each recomposition.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `backupSuccessIsNotError` | Finish export successfully. | Completion has polite live-region semantics and no error semantic. |
| AC-2 | `failureHasRetryAndSemantics` | Fail Today, Timeline, Summary or Event detail loading. | Scoped feedback has error semantics and available Retry stays adjacent. |
| AC-3 | `languageFailureIsAnnounceable` | Language change fails. | The failure is exposed as scoped live feedback without resetting unrelated focus. |
| AC-4 | `unchangedRefreshIsQuiet` | Recompose or refresh with the same result repeatedly. | No new app-driven announcement event is emitted for unchanged text. |
| AC-5 | `talkBackReadsOnceWithoutFocusJump` | Exercise success/error transitions in EN/FR/FA. | TalkBack announces the new outcome once and preserves usable focus; runtime evidence is recorded. |

## Scope boundary

No page-wide live regions, assertive interruptions or assertion that semantics-tree tests certify spoken output.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.
