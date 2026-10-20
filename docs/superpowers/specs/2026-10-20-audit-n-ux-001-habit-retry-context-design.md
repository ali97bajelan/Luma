# UX-001 — Habit retries conceal the date they will affect

**Status:** Draft specification; implementation has not started. Proposed decisions below are reviewable defaults, not previously approved product policy.
**Date:** 20 October 2026
**Source:** [20 October N audit, UX-001](../../UI_UX_AUDIT-20261020N.md#ux-001--habit-retries-conceal-the-date-they-will-affect)
**Priority / effort / confidence:** P1 — High / Medium / High (copied from the static audit; not a delivery estimate).
**Affected surface:** Habits, Today habit preview, Habit detail
**Plan:** [Implementation plan](../plans/2026-10-20-audit-n-ux-001-habit-retry-context.md)
**Delivery phase:** 1 — P1 corrections

## Intent and success

Make every retained habit action identify the original habit, civil date and tracking zone before Retry. The affected user is a person using the audited surface, including assistive technology users. Success means the acceptance scenarios below hold without weakening existing data, privacy or recovery guarantees.

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

1. Show the habit name, localized input.date and input.zone.id in saving and failed states. Snapshot and persist the name when enqueuing; a later rename must not hide action identity. Restore older pending payloads without changing their ID/date/zone, resolving a current name when available or showing a localized unavailable-name label.
2. Associate inline feedback by both habit ID and original date. Cross-date actions remain reachable in a separate lazy/scrollable section with Open date and Retry; an action must appear once per surface.
3. Changing dates or device zones never rewrites a queued command. Open date changes navigation only. Existing Undo and stale-generation guards remain active.

## Design and contract

Extend pending-action presentation while preserving PendingHabitLog.id and HabitLogInput. Separate actions whose original date differs from the selected day into a retained-actions section.

PendingHabitLog gains habitName: String; its existing id: UUID and input: HabitLogInput remain unchanged. PendingHabitFeedback(pending: PendingHabitLog, onRetry: () -> Unit, onOpenDate: (LocalDate) -> Unit): Unit is @Composable. Date navigation calls the existing selected-day owner; Retry calls retryLog(pending.id).

Types, properties and files described as additions are proposed interfaces. Existing APIs are named explicitly where they are reused; follow the implementation plan's file inventory to distinguish new from current code.

## Acceptance scenarios

| ID | Regression case | Input / action | Required result |
|---|---|---|---|
| AC-1 | `retryPreservesOriginalPayload` | Fail on 2026-10-18 in Asia/Tehran; select 2026-10-19 and Retry. | The repository receives the same UUID, 2026-10-18 date, Asia/Tehran zone and original occurredAt. |
| AC-2 | `crossDateFeedbackIsSeparate` | The same habit is visible on B while an A action failed. | B has no inline Retry for A; retained feedback names A and offers Open date. |
| AC-3 | `openDateDoesNotWrite` | Activate Open date on a pending action. | Selected day becomes A; repository.log call count does not increase. |
| AC-4 | `multipleCommandsRemainDistinct` | Two commands share a habit ID but have different dates. | Both command IDs stay reachable with their own date and recovery. |
| AC-5 | `renamedOrMissingHabitKeepsIdentity` | Rename/delete a habit or change device zone while its action is retained. | Original name/date/zone stay readable; a rejected retry remains an honest failure. |

## Scope boundary

No new habit scheduling, log generation algorithm, Undo persistence rewrite or widget redesign.

## Validation and completion

Use the matching plan's targeted commands and evidence checklist. Semantics-tree assertions establish structure; actual TalkBack speech/focus and layout claims require device evidence. Record any unavailable runtime checks as pending rather than passed. A corrective task closes only after its behavior and regression checks pass; UX-028 closes only if separately selected for implementation.

