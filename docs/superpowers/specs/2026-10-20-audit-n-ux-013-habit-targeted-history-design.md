# UX-013 — A historical habit link does not locate the requested repetition

**Status:** Draft specification; implementation has not started. Proposed decisions below are reviewable defaults, not previously approved product policy.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-013](../../UI_UX_AUDIT-20261020N.md#ux-013--a-historical-habit-link-does-not-locate-the-requested-repetition)
**Priority / effort / confidence:** P2 — Medium / Medium / High (copied from the static audit; not a delivery estimate).
**Affected surface:** Timeline → Habit detail/history
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-013-habit-targeted-history.md)
**Delivery phase:** 3 — navigation, scale and consistency

## Intent and success

Reveal the exact historical repetition selected from Timeline even when it is older than the first 50 history rows. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

The audit is source evidence, not reproduced runtime acceptance. Its recommendations are input to this draft, not executable instructions. All tests, runtime exercises and commits in the plan are future work.

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

1. Use the audit's explicit-target-above-history option to avoid inventing a new paging contract. Preserve the newest-50 regular history and existing load-more semantics.
2. Once resolved, scroll to the stable selected target key, show Selected repetition and selected semantics, and announce arrival once per target navigation.
3. A missing or wrong-habit log gets a named unavailable-target state. A read error gets Retry target; neither silently selects the newest log.
4. Guard asynchronous target loads by current habit/target generation. Correction/deletion refreshes target state; removing the selected record exposes unavailable rather than a stale selected card.

## Design and contract

Resolve selectedLogId independently using existing HabitRepository.getLog. Show the target in a dedicated arrival section before bounded paged history and omit its duplicate from regular rows.

HabitDetailUiState gains selectedLogId: UUID?, selectedLog: HabitLog?, selectedLogStatus: HabitTargetStatus where status is NONE/LOADING/FOUND/MISSING/ERROR. detail target loading uses HabitRepository.getLog(id) and validates log.habitId. No new repository history-window API is required.

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `oldTargetIsShown` | Open a target older than 50 lifetime logs. | The exact UUID appears in the selected section and is brought into view. |
| AC-2 | `targetIsNotDuplicated` | The selected target also belongs to a loaded history page. | It is rendered once while history offset/total remain based on repository records. |
| AC-3 | `missingOrWrongHabitIsHonest` | getLog returns null or a log owned by another habit. | Target unavailable appears; no unrelated repetition is selected. |
| AC-4 | `lateTargetCannotReplaceNewNavigation` | Navigate to another habit/target before an old lookup finishes. | Late results are ignored and arrival/focus names the current target only. |
| AC-5 | `correctionAndDeleteRefreshTarget` | Correct then delete the selected repetition. | The selected card updates by ID; deletion becomes unavailable; announcement is not repeated on unrelated refresh. |

## Scope boundary

No all-history eager loading, new repository paging/window interface or changes to repetition identities.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.

